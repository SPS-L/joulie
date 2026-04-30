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

### 1.5 DateUtilsTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `startOf30DaysAgo` | today | epoch of 30 days ago at 00:00 |
| `startOfYear` | today | Jan 1 of current year |
| `customRangeInclusive` | from/to | both endpoints included |

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
| `migrate_1_to_3_validatesSchema` | Full chain; opens via `Room.databaseBuilder(...).build().openHelper.writableDatabase` to force schema validation against the entity declarations (catches column-name casing drift) |

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

For real-device Drive auth and backup verification, complete the external OAuth setup in [`../GOOGLE_CLOUD_SETUP.md`](../GOOGLE_CLOUD_SETUP.md) first. That is a test prerequisite, not a product-design issue.

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

## 6. Coverage Targets

| Layer | Target |
|-------|--------|
| Data models + utils | ≥ 90% line coverage |
| DAOs (Room) | ≥ 85% |
| ViewModels | ≥ 80% |
| UI fragments | smoke tests (≥ 18 scenarios) |
