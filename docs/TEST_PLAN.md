# Test Plan — EV Efficiency Tracker

> **Complete test specification** covering all phases: data layer, wizard, location chips, cost handling, charts, Drive backup.

---

## 1. Unit Tests (JVM, no Android dependencies)

### 1.1 UnitConverterTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `kmToMiles_positive` | 100.0 km | 62.1371 mi (±0.001) |
| `milesToKm_positive` | 62.1371 mi | 100.0 km (±0.001) |
| `kmToMiles_zero` | 0.0 | 0.0 |
| `efficiency_kmPerKwh_to_miPerKwh` | 5.0 km/kWh | 3.107 mi/kWh |

### 1.2 StatsCalculatorTest.kt

| Test | Scenario | Expected |
|------|----------|----------|
| `emptyEvents_returnsZeroStats` | empty list | totalKwh=0, chargeCount=0, all efficiency/cost stats null |
| `singleEvent_totalsButNoEfficiency` | 1 event, kwh=42 | totalKwh=42.0, chargeCount=1, avgEfficiency=null, costPerKm=null |
| `twoEvents_correctEfficiency` | odo 0→100km, 20kWh | 5.0 km/kWh, chargeCount=2, totalKwh=Σ |
| `multipleEvents_sumCorrect` | 3 events, mixed | sum kWh and dist correct |
| `negativeOdometerDelta_skipped` | odo goes backwards | skipped; no contribution to dist/efficiency |
| `monthlyAggregation_correctBuckets` | 4 events across 3 calendar months | 3 MonthBuckets, kWh sums match, sorted ascending |

### 1.3 CostParserTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `costZero_returnsNull` | value=0.0, kwh=10 | (null, null) |
| `costBlank_returnsNull` | value=null, kwh=10 | (null, null) |
| `costNegative_returnsNull` | value=-5.0, kwh=10 | (null, null) |
| `costTotal_derivesPerKwh` | value=3.0, kwh=10, TOTAL | (3.0, 0.30) |
| `costPerKwh_derivesTotal` | value=0.30, kwh=10, PER_KWH | (3.0, 0.30) |
| `kwhZero_returnsNull` | value=5.0, kwh=0 | (null, null) |
| `bothEntered_totalWins` | total=4.0, perKwh=0.20, kwh=10, TOTAL | (4.0, 0.40) — total takes precedence per DESIGN §4.1 |

### 1.4 StatsCalculatorCostTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `allCostNull_costStatsNull` | 3 events, all cost=NULL | costPerKm=null |
| `mixedCost_sumNonNullOnly` | 5 events, 3 with cost, 2 NULL | sum over 3 events only |
| `singleCostEvent_correct` | 1 event, cost=5.0, 50km | costPerKm=0.10 |
| `multipleCurrencies_costStatsNull` | 4 events, 2 EUR + 2 USD | costPerKm=null, costPer100km=null (multi-currency guard) |
| `singleCurrencyAcrossPeriod_costStatsComputed` | 4 events all EUR | costPerKm=Σcost/Σdist |

### 1.5 CapacityEstimatorTest.kt

Locks in the TASK-14 battery-capacity estimator. SoC fields are stored as fractions in `0.0..1.0`. The heuristic boundary is `kwhAdded ≥ 0.8 × nominalBatteryKwh`. `batteryHealthPercent` is **not clamped** — over-estimation by the heuristic surfaces as values above 100%.

| Test | Description |
|------|-------------|
| `exactCapacity_fromSocFields` | Both SoC fields present → `kwhAdded / (after − before)`; result has `isExact = true` |
| `exactCapacity_ignoresHeuristicOnSameEvent` | Exact path takes priority even when the heuristic would also qualify |
| `exactCapacity_withNullNominal` | Exact path doesn't need `nominalBatteryKwh` at all |
| `exactCapacity_socAfterEqualBefore_skipped` | Strict `after > before` — equal values skipped (no division-by-zero) |
| `exactCapacity_socAfterBelowBefore_skipped` | Reversed SoC values skipped (treated as invalid input, not flipped) |
| `heuristic_kwhAtLeast80PctOfNominal_qualifies` | `50 / 60 ≈ 0.833` qualifies → `effectiveCapacity = kwhAdded` with `isExact = false` |
| `heuristic_belowEightyPct_skipped` | `47 / 60 ≈ 0.783` does NOT qualify |
| `heuristic_exactlyEightyPct_qualifies` | `48 / 60 = 0.80` boundary value qualifies (≥ comparison) |
| `heuristic_withNullNominal_skipped` | No nominal capacity → no heuristic possible → skipped |
| `mixed_exactAndHeuristic_bothEmittedSortedByDate` | Mixed events emitted, sorted ascending by `eventDate` regardless of source path |
| `zeroOrNegativeKwhAdded_skipped` | `kwhAdded ≤ 0` always skipped, even with valid SoC |
| `emptyEvents_returnsEmpty` | Empty input → empty output |
| `batteryHealth_latestPointVsNominal` | Uses the chronologically latest point ÷ nominal × 100 |
| `batteryHealth_nullWhenNoPoints` | Empty points → `null` |
| `batteryHealth_nullWhenNoNominal` | Null nominal → `null` |
| `batteryHealth_doesNotClampAbove100` | Heuristic over-estimation surfaces as `> 100%` rather than being clamped |

### 1.6 DateUtilsTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `startOf30DaysAgo` | today | epoch of 30 days ago at 00:00 |
| `startOfYear` | today | Jan 1 of current year |
| `customRangeInclusive` | from/to | both endpoints included |

### 1.7 BackupOutcomeReporterTest.kt

Locks in the TASK-19 orchestration: `BackupOutcomeReporter` translates each `BackupResult` into a counter update and a notifier call. Threshold is `CHRONIC_FAILURE_THRESHOLD = 3`. Uses an in-test `LinkedSettings` paired SettingsReader+Writer so the reporter's read-then-write sequence sees the value it just wrote (the shared `Fakes.kt` reader/writer are independent stores).

| Test | Description |
|------|-------------|
| `success_resetsCounter_andClearsNotifications` | `Success` writes counter = 0 and calls `notifier.clearAll()` once |
| `firstFailure_incrementsCounter_doesNotFireChronic` | Counter 0 → 1; chronic does **not** fire |
| `secondFailure_stillBelowThreshold_doesNotFireChronic` | Counter 1 → 2; chronic does **not** fire |
| `thirdFailure_firesChronic` | Counter 2 → 3; `notifier.notifyChronicFailure()` fires once; clearAll **not** called |
| `fourthFailure_keepsFiringChronic` | Counter 3 → 4; chronic fires every time past threshold (sticky semantics) |
| `authRequired_firesAuthEveryTime_andIncrementsCounter` | Two `AuthRequired` calls → counter 0 → 2; authCount = 2; chronicCount = 0 |
| `successAfterStreak_resetsCounter_andClearsBothChannels` | Counter 5 → 0; clearAll fires once |
| `authRequired_doesNotFireChronicEvenAtThreshold` | `AuthRequired` increments counter past threshold but only fires the auth surface, never chronic |

### 1.8 PushBackupNowUseCaseTest.kt

Locks in the TASK-31 manual-push contract: `PushBackupNowUseCase` calls `BackupRepository.backupCurrentData()` and only updates `lastBackupAt` to the `NowProvider` value on `BackupResult.Success`. Failure paths leave the timestamp untouched so the UI's "Last backup at …" hint stays truthful.

| Test | Description |
|------|-------------|
| `success_callsBackupOnce_andUpdatesLastBackupAt` | `Success` → backup called exactly once + `lastBackupAt = now` |
| `authRequired_propagates_andDoesNotUpdateLastBackupAt` | `AuthRequired` returned + `lastBackupAt` stays null |
| `failure_propagates_andDoesNotUpdateLastBackupAt` | `Failure("Drive storage full")` returned + `lastBackupAt` stays null |
| `success_atDifferentNow_writesThatExactValue` | The exact `NowProvider.nowMillis()` value lands in `lastBackupAt` |

### 1.9 WipeRemoteBackupUseCaseTest.kt

Locks in the TASK-31 manual-wipe contract: `WipeRemoteBackupUseCase` calls `BackupRepository.deleteRemoteBackup()` and only clears `lastBackupAt` to `0L` on `BackupResult.Success`. Failure paths leave the timestamp at its previous value.

| Test | Description |
|------|-------------|
| `success_callsDeleteOnce_andClearsLastBackupAt` | `Success` → delete called exactly once + `lastBackupAt = 0L` |
| `authRequired_propagates_andDoesNotClearLastBackupAt` | `AuthRequired` returned + previous `lastBackupAt` preserved |
| `failure_propagates_andDoesNotClearLastBackupAt` | `Failure("HTTP 500")` returned + previous `lastBackupAt` preserved |
| `success_whenNoPriorTimestamp_writesZero` | Even with a null pre-state, `Success` normalises to `0L` so the empty-state hint reverts |

### 1.10 LastChargeWidgetSnapshotTest.kt

Locks in the TASK-12 widget snapshot helper. The helper picks the latest event by `eventDate` (sorts the input — caller may pass unsorted), computes efficiency from the latest event's odometer-delta vs the previous one (consistent with `StatsCalculator`'s convention from `docs/DESIGN.md §7`), converts to the user's primary metric, and buckets the relative date.

| Test | Description |
|------|-------------|
| `nullCar_returnsEmpty` | `activeCar = null` → `Empty` regardless of events |
| `emptyEvents_returnsEmpty` | non-null car + empty list → `Empty` |
| `singleEvent_loadedButEfficiencyNull` | 1 event renders, but efficiency is null (need ≥ 2 events for an odometer-delta) |
| `twoEvents_kmPerKwh_correct` | 6.2 km/kWh from a 310 km / 50 kWh delta |
| `twoEvents_kwhPer100km_convertsCorrectly` | 6.2 km/kWh → 16.13 kWh/100 km |
| `twoEvents_miPerKwh_convertsCorrectly` | 6.2 km/kWh → 3.85 mi/kWh via `UnitConverter` |
| `twoEvents_negativeOdometerDelta_efficiencyNull` | Odometer goes backwards → efficiency null |
| `twoEvents_zeroKwhAdded_efficiencyNull` | Latest event with `kwhAdded = 0` → efficiency null (no division) |
| `unsortedEvents_picksLatestByEventDate` | Caller may pass events in any order; helper sorts internally |
| `cost_passesThrough_whenSet` | `costTotal` + `currency` both round-trip into the snapshot |
| `cost_null_whenNotSet` | Missing cost → null in snapshot, widget hides the row |
| `relativeDate_today` | Same calendar day → "Today" |
| `relativeDate_yesterday` | 1 day delta → "Yesterday" |
| `relativeDate_threeDaysAgo` | 3 days → "3 days ago" |
| `relativeDate_oneWeekAgo_singular` | Exactly 7 days → "1 week ago" (singular) |
| `relativeDate_threeWeeksAgo` | 21 days → "3 weeks ago" |
| `relativeDate_overFourWeeks_fallsBackToAbsoluteDate` | > 28 days → absolute "MMM d, yyyy" via locale formatter |
| `chargeType_AC_propagates`, `chargeType_DC_FAST_propagates` | Latest event's `ChargeType` round-trips through |

---

## 2. Room Integration Tests (in-memory DB)

### 2.1 ChargeEventDaoTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `insertAndRetrieve` | insert 3 events, query by carId | returns 3 events |
| `cascadeDelete` | delete car → events deleted | query returns empty |
| `rangeQuery` | insert events across 3 months | range query returns correct subset |
| `orderByDate` | insert in random date order | result sorted ascending |
| `insertDuplicate_replaces` | insert same id twice | only latest retained |

### 2.2 CarDaoTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `insertCar` | insert car | id > 0 |
| `updateCar` | update name | new name returned |
| `deleteCar` | delete | no longer in list |

### 2.3 CustomLocationDaoTest.kt

| Test | Description |
|------|-------------|
| `insert_newLabel_stored` | Insert label; query returns it |
| `insert_duplicate_ignored` | Insert same label twice; count = 1 |
| `increment_existingLabel` | Insert + increment; useCount = 2 |
| `getTopLocations_maxFive` | Insert 8 labels; top query returns 5 |
| `getTopLocations_sortedByUseCount` | 5 labels with varying counts; highest first |
| `delete_removesEntry` | Insert + delete; query returns empty |

### 2.4 MigrationTest.kt

| Test | Description |
|------|-------------|
| `migrate_1_to_2` | Adds `chargeType` column |
| `migrate_2_to_3` | Creates `custom_locations`; adds cost/location/note columns (camelCase) |
| `migrate_3_to_4_rewritesLegacyDcRows` | Seeds a v3 DB with a `chargeType = 'DC'` row, runs `MIGRATION_3_4`, asserts the row is rewritten to `'DC_FAST'`. Column type stays TEXT — `@TypeConverters(ChargeTypeConverter)` does the enum round-trip. |
| `migrate_4_to_5_isNoOp_widenIntPksToLong` | Runs `MIGRATION_3_4` then `MIGRATION_4_5` against a v3 fixture and asserts existing rows survive untouched with PKs round-tripping as 64-bit `Long`. SQLite `INTEGER` columns already hold 64 bits, so the migration is a deliberate no-op (TASK-26). |
| `migrate_5_to_6_addsSocColumns` | Runs the full v3 → v4 → v5 → v6 chain against a fixture and asserts the new `socBefore` and `socAfter` REAL columns exist and default to `NULL` on legacy rows (TASK-14). |
| `migrate_1_to_6_validatesSchema` | Full chain v1 → v6; opens via `Room.databaseBuilder(...).build().openHelper.writableDatabase` to force schema validation against the entity declarations (catches column-name casing drift), asserts the migrated `ChargeType` decodes to `ChargeType.AC`, asserts entity PKs round-trip as `Long`, and asserts both SoC columns are `null` on rows persisted before TASK-14. |

---

## 3. ViewModel Tests

### 3.1 DashboardViewModelTest.kt

Uses `TestCoroutineDispatcher` + in-memory Room.

| Test | Scenario | Expected |
|------|----------|----------|
| `noEvents_allStatsNull` | empty DB | all StateFlows emit null/zero Stats |
| `last30Days_correctAgg` | 5 events in last 20 days | Stats matches manual calculation |
| `customPeriod_filtered` | setCustomPeriod(from, to) | only events in range counted |
| `activeCarChange_statsRefresh` | switch car | stats update to new car |

### 3.2 WizardViewModelTest.kt

| Test | Description |
|------|-------------|
| `finish_writesSetupComplete_true` | Call `finish()`; DataStore has `setupComplete=true` |
| `finish_writesAllPrefs` | Call `finish()` with custom values; verify metric, unit, currency all written |
| `defaultValues_correct` | Fresh VM: metric=`km_per_kwh`, unit=`km`, currency=`EUR` |

### 3.3 ChargeEditViewModelTest.kt

| Test | Description |
|------|-------------|
| `saveWithCostZero_storesNull` | Submit form with cost=0; DB entry has `costTotal=null` |
| `saveWithCost_storesBoth` | Submit form with cost=5.0, kwh=10; `costTotal=5.0`, `costPerKwh=0.5` |
| `saveLocation_recordsUsage` | Submit with location="Home"; `custom_locations` has "Home" with count ≥ 1 |

### 3.4 DriveBackupRepositoryTest.kt

Locks in the `BackupResult` contract introduced in TASK-07: `Success` / `AuthRequired` / `Failure(reason, cause?)`. `FakeDriveRemoteSource` exposes `failNext: Throwable?` + `failTimes: Int` + `failuresRaised: Int` so tests drive multi-failure scenarios deterministically. Retry budget under test: `DriveBackupRepository.MAX_ATTEMPTS = 3`, exponential backoff `250 ms × 2^attempt` (virtualised by `runTest`'s test scheduler).

| Test | Description |
|------|-------------|
| `backup_noExistingFile_callsCreate_andReturnsSuccess` | First-time backup creates the App Data file; result = `Success` |
| `backup_existingFile_callsUpdate_andReturnsSuccess` | Subsequent backup overwrites in place; same `fileId` retained |
| `backup_serializerRoundTripPreservesAllFields` | Car / charge event / location entities survive a `toJson` → `fromJson` round trip with full field fidelity |
| `backup_silentTokenFailed_returnsAuthRequired` | `DriveAuthManager.silentToken()` returns `Failed` → `AuthRequired` |
| `backup_drive401_returnsAuthRequired` | HTTP 401 from Drive → `AuthRequired` |
| `backup_drive403UnknownReason_returnsAuthRequired` | HTTP 403 with unknown JSON `reason` → conservative `AuthRequired` |
| `backup_drive403UnparseableBody_returnsAuthRequired` | HTTP 403 with non-JSON body → conservative `AuthRequired` |
| `backup_drive403StorageFull_returnsFailureNotAuthRequired` | HTTP 403 `storageQuotaExceeded` → `Failure("Drive storage full", cause)` (NOT `AuthRequired`) |
| `backup_drive403StorageFull_doesNotRetry` | Storage-full failure raises exactly once; `failuresRaised == 1` even with `failTimes = 99` |
| `backup_drive429_retriesThenSucceeds` | First call HTTP 429, second succeeds → `Success`; `failuresRaised == 1` |
| `backup_drive500_retriesThenSucceeds` | First call HTTP 500, second succeeds → `Success`; `failuresRaised == 1` |
| `backup_drive403Quota_retriesThenSucceeds` | First call HTTP 403 `quotaExceeded`, second succeeds → `Success`; `failuresRaised == 1` |
| `backup_unknownHostException_retriesThenSucceeds` | First call `UnknownHostException`, second succeeds → `Success`; `failuresRaised == 1` |
| `backup_transient429_threeFailures_returnsFailure` | All three attempts return HTTP 429 → `Failure("HTTP 429", cause)`; `failuresRaised == MAX_ATTEMPTS` |
| `backup_transientNetworkException_threeFailures_returnsFailure` | All three attempts throw `IOException("socket reset")` → `Failure("Network failure: IOException", cause)`; `failuresRaised == MAX_ATTEMPTS` |
| `backup_authError_doesNotRetry` | HTTP 401 raises exactly once; `failuresRaised == 1` even with `failTimes = 99` |
| `read_noFileId_returnsNull` | No remote file → `null` |
| `read_existingFile_returnsBody` | Remote file present → body string |
| `read_drive401_throwsDriveAuthRequired` | Read path keeps its exception contract — 401 → `DriveAuthRequiredException` (not `BackupResult`) |
| `read_drive429_retriesThenSucceeds` | Read path also retries transient — 429 then success → returns body |

---

## 4. UI / Instrumented Tests (Espresso)

### 4.1 WizardFlowTest.kt

| Test | Steps | Expected |
|------|-------|----------|
| `firstLaunch_showsWizard` | Clear DataStore; launch app | WizardFragment visible |
| `secondLaunch_skipsWizard` | Set `setupComplete=true`; launch app | DashboardFragment visible |
| `getStarted_advancesToPage2` | Tap Get Started | Page 2 (metric) visible |
| `next_from_page2_to_page3` | Tap Next on page 2 | Page 3 (currency) visible |
| `finish_navigatesToDashboard` | Complete all pages; tap Finish | DashboardFragment visible |
| `back_navigatesToPreviousPage` | On page 2, tap Back | Page 1 visible |
| `resetPrefs_reshowsWizard` | Settings → Reset preferences | WizardFragment shown |

### 4.2 DashboardFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `statsCards_displayedOnLaunch` | launch app | 3+ stat cards visible |
| `fabClick_opensChargeEdit` | tap FAB | ChargeEditFragment in back stack |
| `emptyState_showsMessage` | no events | empty state visible |
| `carSpinner_switchesCar` | select different car | stats cards update |
| `filterChip_dc_filters` | tap DC chip | only DC events in stats |

### 4.3 ChargeEditFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `saveValid_dismisses` | fill valid data, tap Save | returns to Dashboard |
| `saveEmptyOdometer_showsError` | blank odometer, tap Save | error hint visible |
| `saveLowerOdometer_showsError` | odo < previous, tap Save | error "must be higher" |
| `saveZeroKwh_showsError` | kwh=0, tap Save | error visible |
| `homeChip_visible` | open fragment | Home chip present |
| `tapHomeChip_fillsTextField` | tap Home chip | location field = "Home" |
| `addChip_focusesTextField` | tap + Add chip | location field focused |
| `savedCustomLocation_appearsAsChip` | save with location "Supercharger"; reopen | chip visible |
| `customChips_showMax5` | save 6 different locations; open form | at most 5 custom chips shown |

### 4.4 SettingsFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `unitToggle_km_to_miles` | toggle unit | dashboard shows mi/kWh |
| `resetCarData_confirmDialog` | tap Reset Car | confirmation dialog shown |
| `resetCarData_confirm_deletesData` | confirm reset | events gone from log |
| `driveEnable_remoteBackupPromptsReplaceOrSkip` | enable Drive when fake remote backup exists | replace/skip dialog shown; no merge option |
| `driveEnable_noRemoteBackupQueuesInitialBackup` | enable Drive when fake remote backup is absent | `driveEnabled=true`; initial backup queued |

### 4.5 ChartsFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `tabSwitch_showsCorrectChart` | tap "Monthly Energy" tab | bar chart visible |
| `noData_emptyState` | no events | chart shows "No data" |

### 4.6 Cost UI Tests

| Test | Steps | Expected |
|------|-------|----------|
| `costZero_notInStatsCard` | all events have cost=0; open Dashboard | cost row not visible |
| `costPresent_showsStatsCard` | at least 1 event has cost; open Dashboard | cost row visible |
| `multipleCurrenciesInPeriod_showsBanner` | 2 EUR + 1 USD events in selected period | cost cards hidden, "Multi-currency period — cost stats hidden" banner visible |

### 4.7 WorkManager / Drive / Empty-state / CSV

Uses `androidx.work:work-testing` for WorkManager plus fakes for `BackupRepository`, `BackupScheduler`, and Drive auth.

For real-device Drive auth and backup verification, complete the external OAuth setup in [`GOOGLE_CLOUD_SETUP.md`](GOOGLE_CLOUD_SETUP.md) first. That is a test prerequisite, not a product-design issue.

| Test | Description |
|------|-------------|
| `backupWorker_enqueuedAfterChargeSave` | Save a ChargeEvent; assert exactly one unique work `drive_backup` is enqueued with `NetworkType.CONNECTED` and exponential backoff |
| `backupWorker_enqueuedAfterCarMutation` | Create, rename, or delete a car; assert a unique `drive_backup` work item is enqueued because cars are part of the snapshot |
| `backupWorker_enqueuedAfterCommittedLocationDelete` | Delete a custom location and let the undo window expire; assert a unique `drive_backup` work item is enqueued |
| `backupWorker_notEnqueuedBeforeDeleteCommit` | Swipe-delete a charge event or custom location but trigger undo before expiry; assert no backup is enqueued for the transient delete |
| `backupWorker_rapidSavesDebounced` | Save 5 events in quick succession; only the **last** enqueued worker remains pending (REPLACE policy) |
| `restoreFlow_replaceClearsAndImports` | Pre-populate DB with 1 car + 2 events; trigger replace with a fake backup containing 3 cars + 5 events + 2 custom_locations; DB now contains exactly the backup contents and `cacheDir/last_overwritten_backup.json` exists with the prior contents |
| `restoreFlow_replaceDoesNotMerge` | Local DB and remote backup contain overlapping-but-different rows; choose Replace; resulting DB matches the remote snapshot exactly, with no merged local rows retained |
| `restoreFlow_skipKeepsLocal` | Same setup; user picks "Skip"; local data unchanged, `driveEnabled=true`, and future backups use the existing local state |
| `restoreFlow_requiresExplicitConfirmation` | fake remote backup exists; do not confirm replace | local data remains unchanged until user explicitly chooses Replace |
| `restoreFlow_successQueuesFollowupBackup` | complete a successful restore | backup scheduler receives a follow-up enqueue request for the restored local state |
| `emptyStates_noCarVsNoEvents` | (a) `cars` empty → "Add a car" CTA visible, "Log charge" CTA absent. (b) ≥ 1 car, no events → "Log charge" CTA visible, "Add a car" CTA absent |
| `csv_firstEventEfficiencyBlank` | Export CSV for car with 3 events; first event row has empty Efficiency column, subsequent rows have numeric values; header reads `Efficiency (km/kWh)` or `Efficiency (mi/kWh)` per unit pref |

---

## 5. Manual Smoke Tests

1. Fresh install → wizard appears → complete → dashboard shown with empty state
2. Rotate screen mid-wizard → state preserved on page 2/3
3. Kill app on page 2 → relaunch → wizard starts from page 1
4. Add car → add 3 charge events → verify stats cards
5. Switch units km↔miles → verify all values convert
6. Add charge with no cost → Dashboard cost row absent
7. Add charge with cost → Dashboard cost row present with correct value
8. Add 3 charges with same location → chip count increments; chip visible on next open
9. Add 8 unique locations → only 5 custom chips in form; all 8 in Manage Locations
10. Reset preferences → wizard shown; previous car data intact
11. Enable Drive backup with no remote snapshot → complete auth → verify initial backup file created in App Data folder
12. Enable Drive backup with an existing remote snapshot → verify Replace / Skip dialog appears and no merge option is offered
13. Choose Skip on the existing-remote-snapshot flow → verify local data is unchanged and subsequent edits back up normally
14. Uninstall & reinstall → enable Drive → choose Replace → verify remote snapshot fully replaces local data and location chips are restored
15. Reset car data → confirm events gone, car still present, and backup re-queued
16. Export CSV → open in spreadsheet app → verify columns and values
17. Custom period picker → select last 60 days → stats card updates
18. Dark mode → launch app → verify no illegible text or invisible elements
19. Add second car → switch → logs and stats are independent
20. Swipe-to-delete charge event → tap Undo before timeout → row restored and no backup triggered for the transient delete
21. Swipe-to-delete charge event → let timeout expire → row removed, stats update, and backup triggered

---

## 5b. Release-APK smoke test (run before every tagged release)

The release APK runs through R8 with the keep rules in `app/proguard-rules.pro`.
Pre-merge gates and instrumented Espresso tests use the **debug** APK, which
does **not** exercise minification — Drive sign-in, MPAndroidChart renderers,
Gson reflection on backup DTOs, and Room's generated DAOs all need to be
verified against the minified release APK before publishing the GitHub Release.

Run this matrix on a physical device or API-26+ emulator after every `v*` tag
push **before** advancing the release from draft to published. Tester emails
must be allow-listed on the Google Cloud OAuth consent screen
(`docs/GOOGLE_CLOUD_SETUP.md` Step 4).

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

| # | Step | Pass criterion |
|---|------|----------------|
| 1 | Cold-launch the app from the launcher | Wizard renders, no crash, all 4 pages reachable |
| 2 | Complete the wizard | Lands on Dashboard with the "no car" empty state; `setupComplete=true` (verify by relaunching — wizard must NOT reappear) |
| 3 | Settings → Cars → add a car | Car appears in list; Dashboard now shows the active car |
| 4 | Dashboard FAB → log a charge with cost (e.g. 30 kWh / €12.50) | Save returns to Dashboard; total kWh + cost rows render correct values |
| 5 | Add a second event so efficiency can compute | Efficiency stat is no longer "—"; the configured primary metric (default `kwh_per_100km`) renders a number |
| 6 | Bottom nav → Charts; cycle every tab (Trend / Monthly kWh / Monthly cost / AC vs DC / Locations / Degradation) | Each tab renders without crash; MPAndroidChart canvases are non-empty (this exercises the new keep rule). The Degradation tab shows an empty-state for cars with no nominal `battery_kwh` set or fewer than 3 qualifying charges — that's expected, not a failure. |
| 6b | **Switch system to Dark mode** (Settings → Display → Dark theme) **and re-cycle all six Charts tabs** | Axis labels, legend text, AC vs DC center text, and gridlines are all readable against the dark surface. AC vs DC + Locations + Degradation tabs render without crash. Two regressions were caught here in the past: (a) `c677a2b` introduced theme-aware text but accidentally crashed the PieChart paths by touching `chart.xAxis` on a renderer chain with no XAxisRenderer — fixed by `5a99335`; (b) Degradation was missing `LayoutParams.MATCH_PARENT` and crashed during measure — same fix. This step is the first line of defence for similar future drift. |
| 7 | Settings → enable Drive backup → sign in (allow-listed Google account) → wait for first backup | Snackbar reports success; verify `evtracker_backup.json` lands in the App Data folder via `files.list?spaces=appDataFolder` |
| 8 | Add another charge event after Drive is enabled | WorkManager fires a follow-up backup; remote `modifiedTime` advances |
| 9 | Settings → Reset preferences → confirm | App relaunches into the wizard; existing charge data intact (TASK-23 startup auto-recovery path) |
| 10 | Re-complete the wizard (page 4 disclaimer must be re-accepted) | Dashboard returns; previously logged events still visible |
| 11 | Settings → Export CSV | Share-sheet opens; chosen target receives a non-empty `.csv` with the correct header for the active distance unit |
| 12 | Settings → About | About screen renders with version (e.g. `1.0.1`), SPS-Lab card with tappable links, MIT license, and the open-source-libraries card |
| 13 | **TASK-19 backup notifications.** Settings → enable Drive backup, then put the device into airplane mode and trigger 3 charge-event saves to drive 3 backup failures in a row | On the 3rd failure, when the app is opened, the rationale dialog appears (Android 13+). Tap **Allow**, then **Allow** on the system prompt → channel `backup_status` shows the sticky "Drive backup failed — Tap to open Settings" notification. Tap it → app opens to Settings. Disable airplane mode and trigger another save → next backup succeeds → the chronic notification is cancelled. To verify the **Not now** path, repeat with a fresh install / re-grant cycle and decline; the rationale must never re-fire and notifications stay silent. Pre-13 devices skip the rationale (channel exists, permission implicit). |
| 14 | **TASK-31 manual Drive controls.** Settings → with Drive enabled, observe two buttons under "Last backup": "Back up now" (primary) and "Wipe remote backup" (destructive outlined). Tap **Back up now** | Snackbar "Backup uploaded" appears within a few seconds; the "Last backup" timestamp advances. Verifying via Drive `files.list?spaces=appDataFolder` shows the file's `modifiedTime` updated. While the upload is in flight, the wipe button is disabled (mutual exclusion). |
| 15 | **TASK-31 wipe.** Tap **Wipe remote backup** → confirmation dialog appears with title "Delete remote backup?" and the body explaining local data is unaffected. Tap **Delete** | Snackbar "Remote backup deleted" appears. Verifying via Drive `files.list?spaces=appDataFolder` returns no `evtracker_backup.json`. The "Last backup" timestamp reverts to its empty state ("Never"). Trigger any new charge save → the auto-backup worker re-creates the remote file (regression check). With Drive disabled (toggle off), both buttons disappear (View.GONE, not just disabled). |
| 16 | **TASK-12 widget.** Long-press the home screen → Widgets → search "EV Tracker" → drag the **Last charge** 2×2 widget to the home screen. With no charge events: empty state renders ("No charges logged yet."). Save a charge event in the app → switch back to the home screen | The widget refreshes within a second showing the active car name, "Today", kWh, efficiency in the configured primary metric, and (if cost was entered) the formatted currency line. Tap the widget → the app opens to the dashboard. Add a second charge event with the odometer advanced → efficiency value updates to a real number. Wipe all data via **Settings → Reset all** → the widget reverts to the empty state. Switch the primary metric in Settings → the widget's efficiency unit follows on the next refresh. |

**On any failure:** capture `adb logcat *:E` from the moment of the crash,
file an issue, and **do not publish the GitHub Release** — keep it in draft
until the regression is fixed and the matrix re-runs cleanly. R8 regressions
typically manifest as `NoSuchMethodError`, `NoSuchFieldError`, or
`ClassNotFoundException` from inside Drive / Gson / MPAndroidChart frames.

---

## 6. Coverage Targets

| Layer | Target |
|-------|--------|
| Data models + utils | ≥ 90% line coverage |
| DAOs (Room) | ≥ 85% |
| ViewModels | ≥ 80% |
| UI fragments | smoke tests (≥ 18 scenarios) |
