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

Note (TASK-44, 2026-05-03): the existing fixtures in cases 1–5 below all start with an "anchor event" (`event(1, 0.0, 0.0)`) that has *no* cost, so the pre-TASK-44 bug (first event's cost silently dropped) didn't trip them. The four new cases at the bottom (`firstAndSecondEventBothCosted_…`, `firstAndThirdEventCosted_…`, `singleCostedEvent_…`, `mixedCurrencyWithFirstEventCosted_…`) lock in the corrected `Σ cost` semantic for first-event-costed shapes.

| Test | Input | Expected |
|------|-------|----------|
| `allCostNull_costStatsNull` | 3 events, all cost=NULL | costPerKm=null |
| `mixedCost_sumNonNullOnly` | 5 events, 3 with cost, 2 NULL | sum over 3 events only |
| `singleCostEvent_correct` | 1 event, cost=5.0, 50km | costPerKm=0.10 |
| `multipleCurrencies_costStatsNull` | 4 events, 2 EUR + 2 USD | costPerKm=null, costPer100km=null (multi-currency guard) |
| `singleCurrencyAcrossPeriod_costStatsComputed` | 4 events all EUR | costPerKm=Σcost/Σdist |
| `firstAndSecondEventBothCosted_totalCostSumsBoth` | 2 events both costed, EUR | totalCost=15 (sum); costPerKm=15/Δkm (TASK-44 — pre-fix dropped event 0's €5) |
| `firstAndThirdEventCosted_middleNotCosted_totalIncludesBoth` | 3 events, only 1st & 3rd costed | totalCost=sum of both costed events; currency=EUR (TASK-44) |
| `singleCostedEvent_reportsTotalCost_costPerKmStillNull` | 1 event, costed | totalCost=that cost; currency=EUR; costPerKm=null (delta distance undefined for single event) (TASK-44) |
| `mixedCurrencyWithFirstEventCosted_totalCostStillNull` | 2 events: EUR + USD, both costed, first event costed | totalCost=null; currency=null; mixedCurrency=true (mixed-currency rule still wins after TASK-44) |

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

Locks in the TASK-31 manual-wipe contract: `WipeRemoteBackupUseCase` calls `BackupRepository.deleteRemoteBackup()` and only clears `lastBackupAt` to `0L` on `BackupResult.Success`. Failure paths leave the timestamp at its previous value. TASK-54 (2026-05-03) extends the `Success` side-effect to **also** clear the durable last-seen-snapshot marker — `setLastSeenRemoteBackupExportedAt("")` runs in the same branch, ordered AFTER `setLastBackupAt(0L)` (the test pins call ordering so a future reorder is visible in code review).

| Test | Description |
|------|-------------|
| `success_callsDeleteOnce_andClearsLastBackupAt` | `Success` → delete called exactly once + `lastBackupAt = 0L` |
| `authRequired_propagates_andDoesNotClearLastBackupAt` | `AuthRequired` returned + previous `lastBackupAt` preserved |
| `failure_propagates_andDoesNotClearLastBackupAt` | `Failure("HTTP 500")` returned + previous `lastBackupAt` preserved |
| `success_whenNoPriorTimestamp_writesZero` | Even with a null pre-state, `Success` normalises to `0L` so the empty-state hint reverts |
| `success_clearsLastSeenRemoteBackupExportedAtMarker` | Pre-seeded marker `"2025-01-01T00:00:00Z"` → after `Success`, marker resets to `""` AND call recorder shows `setLastBackupAt(0)` then `setLastSeenRemoteBackupExportedAt()` in that order (TASK-54) |
| `authRequired_doesNotClearLastSeenMarker` | Pre-seeded marker → after `AuthRequired`, marker is preserved (the wipe didn't happen, so the remote may still exist and the marker is still meaningful) (TASK-54) |

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

### 1.11 KwhFromSocCalculatorTest.kt

Locks in the TASK-43 pure helper `KwhFromSocCalculator.compute(socBefore, socAfter, nominalBatteryKwh)`. Returns `max(0, (socAfter − socBefore) × nominalBatteryKwh)`. Negative deltas clamp to zero (rather than throw) so the caller can treat zero as "calculator could not produce a value". Battery-side kWh only — charging-loss caveat documented inline on the helper's KDoc.

| Test | Description |
|------|-------------|
| `typicalCharge_returnsDeltaTimesNominal` | 60 kWh × (0.80 − 0.20) → 36 |
| `fullCharge_zeroToOne_returnsNominal` | 75 kWh × (1.0 − 0.0) → 75 |
| `zeroDelta_returnsZero` | Δsoc = 0 → 0 |
| `negativeDelta_clampsToZero` | User enters socBefore > socAfter → clamps to 0 (no throw) |

### 1.12 ChargeKwhSourceTest.kt + ChargeKwhSourceConverterTest.kt

Locks in the TASK-43 enum + Room TypeConverter pair, mirroring the TASK-25 `ChargeType` pattern. Defensive-fallback contract: unknown / corrupted strings decode to `MEASURED` so a stale row never crashes Room reads (worst-case is a single derived event over-counts in the degradation chart).

| Test | Description |
|------|-------------|
| `parseLegacy_knownValues_roundTrip` | `"MEASURED"` and `"DERIVED_FROM_SOC"` decode cleanly |
| `parseLegacy_unknownValues_fallBackToMeasured` | Empty / garbage strings decode to `MEASURED` |
| `roundTrip_allValues_preserved` | Converter `fromChargeKwhSource` → `toChargeKwhSource` is identity for every enum value |
| `unknownString_fallsBackToMeasured` | Converter side: same defensive fallback as the enum |

### 1.13 BackupSerializerTest.kt — TASK-43 additions

| Test | Description |
|------|-------------|
| `roundTrip_preservesKwhSourceFlag` | A v7 backup carrying `"kwh_source": "DERIVED_FROM_SOC"` round-trips through Gson preserving the enum value |
| `fromJson_legacyV6Backup_kwhSourceDefaultsToMeasured` | A v6 backup file (no `kwh_source` key) restores cleanly; `ChargeEventDto.toEntity()` coalesces null → `MEASURED`. The DTO field is **nullable** because Gson uses `Unsafe.allocateInstance` and bypasses Kotlin primary-constructor defaults for absent JSON keys |
| `toJson_serializesKwhSourceAsName` | `DERIVED_FROM_SOC` writes as the canonical enum name on the wire |
| `currentVersion_isSeven` | Sanity tripwire: catches a future bump that forgets to update the constant |

### 1.14 CapacityEstimatorTest.kt — TASK-43 additions

| Test | Description |
|------|-------------|
| `derivedEvent_excludedFromExactPath` | An event with both SoC fields populated AND `kwhSource = DERIVED_FROM_SOC` produces no capacity point — the exact path early-returns before the divide |
| `derivedEvent_excludedFromHeuristicPath` | An event with no SoC fields, `kwhAdded ≥ 0.8 × nominal`, AND `kwhSource = DERIVED_FROM_SOC` produces no capacity point — the heuristic path also early-returns |
| `mixedDataset_excludesDerivedRowsOnly` | Mixed measured + derived events: only the measured ones produce points, sorted ascending by date |
| `countDerivedEvents_returnsDerivedRowCount` | Counts events flagged `DERIVED_FROM_SOC`; unflagged events do not contribute |
| `countDerivedEvents_emptyListReturnsZero` | Empty input → 0 |

### 1.15 SaveChargeEventUseCaseTest.kt — TASK-43 additions

| Test | Description |
|------|-------------|
| `kwhSource_defaultsToMeasured_whenInputOmitsField` | Existing call sites that pre-date TASK-43 stay correct: `SaveChargeEventInput.kwhSource` default is `MEASURED`, persisted entity is never silently flipped to `DERIVED` |
| `kwhSource_persistsDerivedFromSoc_whenInputOptsIn` | Explicit `DERIVED_FROM_SOC` round-trips through to the persisted entity unchanged |

### 1.16 ObserveChartsModelsUseCaseTest.kt — TASK-43 addition

| Test | Description |
|------|-------------|
| `derivedExcludedCount_reflectsKwhSourceFlag_inPeriod` | Mixed measured + derived events in the period: derived ones do not produce capacity points, but the count is exposed via `ChartsUiState.Loaded.derivedExcludedCount` so the chart can render the banner. With 1 measured + 2 derived events, capacity is empty (below `MIN_POINTS_FOR_CHART = 3`) and `derivedExcludedCount == 2` |

### 1.17 ExportCsvUseCaseTest.kt

Locks in two related contracts: the TASK-52 (2026-05-03) CSV-injection / RFC 4180 escape rules, and the TASK-09 (2026-05-03) canonical 14-column schema with date-ranged-export support. The file has two baseline cases (`header_isCanonicalFourteenColumnSchema`, `rowCountMatchesEventCount`) plus 10 TASK-52 hardening cases plus 11 TASK-09 schema/range cases. Two shared helpers: `rowFor(location, note, currency)` slices on the first `\n` (header terminator) and `trimEnd('\n')` to capture a single full data row (used by the TASK-52 single-event hardening cases); `writeAndGetLines(events, carName)` walks the output character-by-character honouring CSV quoting state so embedded `\r` / `\n` inside a quoted field don't split a logical record across multiple "lines" (used by the TASK-09 column-index assertions). The previous `headerLineUsesKmOrMilesPerFlag` test was retired when TASK-09 dropped the `useKm` parameter.

**TASK-52 hardening cases:**

| Test | Description |
|------|-------------|
| `note_containingCarriageReturn_isQuoted` | RFC 4180 mandates CR-bearing fields to be quoted; pre-TASK-52 only `\n` triggered quoting. Note `"line1\rline2"` → `"line1\rline2"` |
| `note_containingTab_isQuoted` | Excel auto-detects TSV when a tab appears in the first data row; quoting forces CSV interpretation. Note `"col1\tcol2"` → `"col1\tcol2"` |
| `note_startingWithEquals_getsFormulaPrefix` | Canonical OWASP CSV-injection. Note `"=SUM(A1:A10)"` → `"'=SUM(A1:A10)"` (apostrophe inside outer quotes neutralises Excel's formula mode) |
| `location_startingWithPlus_getsFormulaPrefix` | International phone numbers (`"+44 7000"`) are legitimate user data that triggers Excel's formula mode. Apostrophe-prefix protects without losing the data |
| `note_startingWithMinus_getsFormulaPrefix` | Note `"-Some negative note"` → `"'-Some negative note"` (the same trigger that, on a numeric column, would be benign — but `note` is text) |
| `note_startingWithAt_getsFormulaPrefix` | `@` is the fourth canonical trigger — covers e.g. `"@everyone"` |
| `note_withFormulaPrefixAndEmbeddedQuote_doublesQuotesInsideField` | Compound case: leading `=` triggers the apostrophe prefix AND an embedded `"` requires standard CSV doubling. `=HYPERLINK("http://evil")` → `"'=HYPERLINK(""http://evil"")"`. Regression guard for the prefix + escape interaction |
| `note_plainText_isNotQuoted` | Over-quoting regression guard: `"Charged at home"` is benign and stays unquoted. The full row ends with `,Charged at home` (no trailing record terminator after `trimEnd('\n')`) |
| `note_existingCommaAndQuoteCases_stayGreen` | RFC 4180 baseline — `,` → quoted; embedded `"` → doubled and quoted; `\n` → quoted |
| `currency_isAlsoEscaped` | TASK-52 expanded escape coverage to the `currency` column (free-form letter code stored verbatim from the wizard). A malicious `currency = "=USD"` emits `"'=USD"` instead of `=USD` |

**TASK-09 schema + range cases:**

| Test | Description |
|------|-------------|
| `header_isCanonicalFourteenColumnSchema` | Asserts the exact 14-column header literal. Replaces the retired `headerLineUsesKmOrMilesPerFlag` since the `useKm` flip is gone |
| `emptyEvents_emitsHeaderOnly` | Range filters can produce zero rows; the header must still be present so consumers can introspect column names |
| `singleEvent_efficiencyCellIsBlank` | First (and only) event in the slice has no previous row to delta against; `km_per_kwh` (column 10) is blank |
| `multiEvent_efficiencyDerivedFromDeltaOdometer` | 100 km gained, 12 kWh charged → `8.333333333333334` km/kWh on row 2; row 1 stays blank |
| `negativeOdometerDelta_efficiencyBlank` | Odometer rolls back: that row's efficiency is blank, but the chain continues — the next row computes its delta against the rolled-back row (mirrors `StatsCalculator`'s pairwise convention) |
| `zeroKwh_efficiencyBlank` | 0-kwh rows would divide-by-zero; defensive blank, not NaN |
| `kwhSource_emitsEnumName` | TASK-43 column 4 — `MEASURED` / `DERIVED_FROM_SOC` enum names emit verbatim |
| `socFields_emitFractionsOrBlank` | TASK-14 columns 11 / 12 — `0.2` and `0.8` emit as `"0.2"` / `"0.8"`; null fields emit blank |
| `costPerKwh_emitsDoubleOrBlank_independentOfCostTotal` | Both `costTotal` (col 7) and `costPerKwh` (col 8) are independently nullable per the CostParser contract; each renders Double or blank without coupling |
| `carName_appearsInEveryRow_andIsEscapedWhenContainsComma` | Car name is column 1 on every row (not just header). A name containing `,` (e.g., `"Tesla, Model 3"`) forces RFC 4180 quoting via the hardened escape |
| `mixedCurrencyRows_eachRowEmitsItsOwnCurrency` | The exporter does NOT apply mixed-currency aggregation rules (those live in `StatsCalculator` for the dashboard). EUR row + USD row each emit their own currency verbatim — researchers handle aggregation downstream |
| `rangeFilter_omitsEventsOutsideRange` | Builds a 3-event store (epoch-ms 1000 / 2000 / 3000), calls `export(carId, 1500..2500)`, asserts only the 2000ms event survives the filter. Uses the suspend `export(...)` overload directly via a custom `CsvFileSink` that captures the writer body — exercises the range filter end-to-end rather than going through `writeCsv` |

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
| `migrate_6_to_7_addsKwhSourceColumn` | Runs v3 → v4 → v5 → v6 → v7 against a fixture and asserts the new `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` column exists and pre-existing rows backfill to `'MEASURED'` (TASK-43). |
| `migrate_1_to_7_validatesSchema` | Full chain v1 → v7 (renamed from `migrate_1_to_6_validatesSchema`); opens via `Room.databaseBuilder(...).build().openHelper.writableDatabase` to force schema validation against the entity declarations (catches column-name casing drift), asserts the migrated `ChargeType` decodes to `ChargeType.AC`, asserts entity PKs round-trip as `Long`, asserts both SoC columns are `null` on rows persisted before TASK-14, and asserts the new `kwhSource` column resolves to `ChargeKwhSource.MEASURED` via `@TypeConverters(ChargeKwhSourceConverter)`. |

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

`Dispatchers.setMain` / `resetMain` setup is required because the VM's `init` block now launches a `viewModelScope` collector for `SettingsReader.languageTag` (TASK-55).

| Test | Description |
|------|-------------|
| `finish_writesSetupComplete_true` | Call `finish()`; DataStore has `setupComplete=true` |
| `finish_writesAllPrefs` | Call `finish()` with custom values; verify metric, unit, currency all written |
| `defaultValues_correct` | Fresh VM: metric=`km_per_kwh`, unit=`km`, currency=`EUR` |

**TASK-55 additions:**

| Test | Description |
|------|-------------|
| `onLanguageSelected_persistsTagAndAppliesLocale` | `vm.onLanguageSelected("el")` writes `"el"` to `SettingsRepository.languageTag` AND fires `LocaleApplier.apply("el")` exactly once |
| `onLanguageSelected_followSystem_writesEmptyString` | After picking `"ru"` then `""`, both DataStore and the LocaleApplier reflect the empty-string "follow system" semantic |
| `onLanguageSelected_persistsTag_evenBeforeFinish` | Mid-wizard kill survival: the language tag is persisted to DataStore the moment the user picks, not on `finish()`. Asserts `setupComplete` is still `false` after the language write completes |

### 3.3 ChargeEditViewModelTest.kt

| Test | Description |
|------|-------------|
| `saveWithCostZero_storesNull` | Submit form with cost=0; DB entry has `costTotal=null` |
| `saveWithCost_storesBoth` | Submit form with cost=5.0, kwh=10; `costTotal=5.0`, `costPerKwh=0.5` |
| `saveLocation_recordsUsage` | Submit with location="Home"; `custom_locations` has "Home" with count ≥ 1 |
| `createMode_loadsNominalBatteryKwhFromActiveCar` | Active car with `batteryKwh = 60.0` → `state.nominalBatteryKwh == 60.0`; defaults to `kwhSource = MEASURED` and `kwhCalculatorActive = false` (TASK-43) |
| `calculator_withSocFieldsPrefilled_derivesKwhAndFlagsDerived` | User enters SoC 20% → 80%, taps the calculator link → kWh fills to "36" (60 × 0.6), `kwhSource = DERIVED_FROM_SOC`, SoC card expanded (TASK-43) |
| `calculator_thenSocChange_recomputesKwh` | Calculator active, then SoC changes → kWh re-derives in real time (TASK-43) |
| `calculator_userManuallyEditsKwh_revertsToMeasured` | After the calculator filled kWh, manually editing the field flips `kwhSource` back to `MEASURED` and deactivates the calculator (TASK-43) |
| `setKwh_echoesCurrentText_preservesProvenance` | The fragment's `doAfterTextChanged` listener echoes the calculator's own `setText()` with the unchanged value — the echo guard preserves provenance (TASK-43) |
| `calculator_recalculate_afterUserEdit_reactivates` | User edits → MEASURED, then re-tap link → DERIVED_FROM_SOC again, kWh re-derived (TASK-43) |
| `editMode_loadsExistingKwhSourceFromEntity` | Loading a persisted event with `kwhSource = DERIVED_FROM_SOC` preserves the flag in UiState (TASK-43) |
| `save_threadsKwhSourceToInput` | After calculator-driven save, the persisted entity carries `kwhSource = DERIVED_FROM_SOC` and the battery-side derived kWh value (TASK-43) |
| `socFieldsFilledWithBlankKwh_autoActivatesCalculator` | User types SoC 20 → 80 with kWh blank; auto-activation fires *without* tapping the calculator link → kWh = "36", `kwhSource = DERIVED_FROM_SOC` (TASK-43 follow-up, 2026-05-03) |
| `socFieldsFilledWithKwhAlreadyPresent_doesNotOverwriteKwh` | User types kWh = "42" first, then SoC 20 → 80; kWh stays "42", calculator stays inactive, `kwhSource = MEASURED` — manual values are never overwritten silently (TASK-43 follow-up) |
| `socAfterLessThanBefore_doesNotAutoActivate` | SoC 80 → 20 (invalid range) with blank kWh; auto-activation declines, kWh stays "" (TASK-43 follow-up) |
| `nominalBatteryKwhMissing_doesNotAutoActivate` | Active car has `batteryKwh = null`; SoC fields filled with blank kWh; auto-activation declines (TASK-43 follow-up) |
| `userClearsKwhAfterAutoFill_thenChangesSoc_reActivates` | After auto-fill, user clears kWh, then changes SoC; auto-activation fires again, derives new kWh from updated SoC (TASK-43 follow-up) |

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

### 3.5 SettingsViewModelTest.kt — TASK-54 additions (durable last-seen marker)

`SettingsViewModelTest.kt` covers the full Drive (E) + F1 + TASK-31 surface (~39 cases total). The TASK-54 (2026-05-03) additions lock in the durable last-seen-snapshot marker contract: the destructive restore prompt is shown at most once per remote snapshot identity, where identity = the JSON `exported_at` ISO-8601 string. Backed by the new `lastSeenRemoteBackupExportedAt` DataStore key on `SettingsReader` / `SettingsWriter`. All cases use `BackupData.fromEntities(..., now = X)` to seed a remote JSON with a deterministic `exported_at` derived from `Instant.ofEpochMilli(X).toString()`. The TASK-55 (2026-05-04) additions exercise the language-picker contract via a new `FakeLocaleApplier` recorded in the test `Setup` data class.

| Test | Description |
|------|-------------|
| `onDriveAuthGranted_remoteExists_firstTime_emitsPrompt` | Marker default `""`; remote `exported_at = "2023-11-14T22:13:20Z"` → `ShowRestorePrompt` emitted, `pendingRestoreExportedAt` populated, `driveEnabled` stays false until user decides |
| `onDriveAuthGranted_remoteExists_alreadySeen_skipsSilently` | Pre-seed marker = remote `exported_at` → no `ShowRestorePrompt` emitted; `driveEnabled = true`, `enqueueBackup` called once |
| `onDriveAuthGranted_remoteWithDifferentExportedAt_promptsAgain` | Marker holds an older `exported_at`, remote has newer `exported_at` → prompt re-fires (covers the wipe + new upload path) |
| `onSkipRestore_persistsLastSeenExportedAt` | After Skip, `writer.lastSeenRemoteBackupExportedAt` reads back the snapshot's `exported_at`; both `pendingRestoreLabel` and `pendingRestoreExportedAt` are cleared |
| `onConfirmRestore_success_persistsLastSeenExportedAt` | After successful Restore, marker is written so the same snapshot is never re-prompted post-restore (the local DB already equals it) |
| `onDriveAuthGranted_noRemote_keepsExistingMarker` | The no-remote path enables Drive without touching the marker (a previous Skip / Restore decision is still meaningful — regression guard) |

**TASK-55 additions (language picker):**

| Test | Description |
|------|-------------|
| `onLanguageSelected_persistsTagAndAppliesLocale` | `vm.onLanguageSelected("el")` writes `"el"` to `FakeSettingsWriter.languageTag` AND fires `FakeLocaleApplier.apply("el")` exactly once |
| `onLanguageSelected_followSystem_writesEmptyString` | After picking `"ru"` then `""`, both writer and applier reflect the empty-string "follow system" semantic |
| `languageTag_collectedFromSettingsReader_intoUiState` | Pre-seed `reader.setLanguageTag("tr")` → `vm.uiState.value.languageTag == "tr"`. Confirms the init-time collector surfaces the persisted tag for the dialog's selected-option highlight |

**Deferred instrumented coverage:** the BACKLOG TASK-55 spec called for `SettingsLanguagePickerTest` + `WizardLanguagePickerTest` instrumented cases. Both deferred at merge time — the JVM tests cover the VM contract end-to-end (DataStore + LocaleApplier + UiState round-trip) and the dialog wiring is mechanical (`MaterialAlertDialog.Builder.setSingleChoiceItems`). File a follow-up if instrumented coverage of the `setApplicationLocales`-triggered Activity recreation becomes load-bearing.

---

## 4. UI / Instrumented Tests (Espresso)

> **Test runner — `org.spsl.evtracker.HiltTestRunner`** (subclass of `AndroidJUnitRunner`):
>
> - **`callApplicationOnCreate(app)`** — calls `WorkManagerTestInitHelper.initializeTestWorkManager(app)` once per test process so the first WorkManager-touching test doesn't crash. The production manifest removes `androidx.work.WorkManagerInitializer` from `androidx.startup` because `EVTrackerApp` implements `Configuration.Provider`; under instrumentation the application is `HiltTestApplication`, which doesn't, so without this hook `WorkManager.getInstance(context)` would throw `IllegalStateException` and take the whole suite down (regression that surfaced in the 2026-05-03 nightly when `MainActivityBottomNavTest` first pulled WorkManager into a Hilt graph).
> - **`onStart()`** — calls `AccessibilityChecks.enable().setRunChecksFromRootView(true)` (TASK-18 Step 6, 2026-05-03) so every Espresso `ViewAction` (click, type, scrollTo, …) runs the WCAG 2.1 AA rule set against the targeted view AND the surrounding root. No suppression matchers configured — pre-existing violations surface as nightly test failures (informational only; does not block PRs) and feed the TASK-18 follow-up scope.
>
> **Fragment host activity** — `androidx.fragment:fragment-testing-manifest:1.6.2` is on `debugImplementation` (TASK-50 sub-fix A, 2026-05-03), so the merged debug app manifest declares `androidx.fragment.app.testing.EmptyFragmentActivity`. Without that, every test that calls `launchFragmentInContainer` fails with "Unable to resolve activity" because the test APK's manifest entry isn't visible to the runtime app package (`org.spsl.evtracker.debug`).

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

### 4.8 DriveBackupWorkerTest.kt (instrumented)

Locks in the worker-level translation of `BackupResult` → `ListenableWorker.Result` per TASK-07's contract. The instrumented `FakeDriveRemoteSource` mirrors the JVM-side budget design (`failNext: Throwable?` + `failTimes: Int` + `failuresRaised: Int`) so both halves of the retry contract are testable on the same fixtures (TASK-50 sub-fix B, 2026-05-03).

| Test | Description |
|------|-------------|
| `happyPath_returnsSuccess` | No failures seeded → `BackupResult.Success` → `Result.success()` |
| `authRevoked_returnsFailure` | `FakeDriveAuthManager.nextResult = Failed("revoked")` → `BackupResult.AuthRequired` → `Result.failure()` (worker never emits `Result.retry()` per TASK-36's invariant) |
| `ioError_recoversAfterTransientRetry_returnsSuccess` | `failTimes = 1` → repo absorbs the transient via its 3-attempt budget → `Result.success()`, with `failuresRaised == 1` proving the retry actually happened (TASK-50 sub-fix B; replaced the stale `ioError_returnsRetry` case that asserted the pre-TASK-07 contract) |
| `ioError_exceedsRetryBudget_returnsFailure` | `failTimes = 4` exceeds `MAX_ATTEMPTS = 3` → `BackupResult.Failure` → `Result.failure()` (TASK-50 sub-fix B) |

### 4.9 MainActivityResetRecoveryTest.kt (instrumented)

Locks in the F1 startup auto-recovery flow. Uses `@UninstallModules(DataResetModule::class)` + a local `TestResetModule` to swap the production `RoomDataResetTransactionRunner` for a `TestableResetRunner` spy. The `DataResetModule` was extracted from `DomainModule` in TASK-50 sub-fix C specifically so this single binding can be uninstalled without dragging in unrelated dependencies (mirrors the `BackupModule` pattern used by `DriveBackupWorkerTest`).

| Test | Description |
|------|-------------|
| `startup_resetInProgressTrue_runsUseCase_clearsFlag_beforeUiVisible` | Seed `resetInProgress=true` + a Test car in DB; launch `MainActivity`; await `resetInProgress=false`; assert `clearCalls == 1` and the Test car is gone |
| `startup_resetInProgressFalse_doesNotRunUseCase` | `resetInProgress=false` at launch; assert `clearCalls == 0` and the seeded Test car still exists |
| `startup_resetRecoveryThrows_showsRetryDialog_doesNotMountNavGraph` | Set `testRunner.failNext = IllegalStateException(…)`; launch; assert the recovery-failure dialog is displayed, the nav graph is not mounted, and `resetInProgress` remains `true` so the next launch retries |

### 4.10 SettingsDriveSwitchEntryTest.kt (instrumented) — TASK-54 Step 0 regression

Covers the user-reported reproduction: every Settings entry the Drive switch visibly flipped OFF→ON on its own and the "Restore from Drive?" dialog appeared. Root cause was that `SettingsFragment.onViewCreated` attached the switch's `OnCheckedChangeListener` synchronously, and Android's view-state restoration called `setChecked(true)` between `onCreateView` and `onStart` to restore the saved checked state — which fired the listener → `onUserToggledOn()` → `auth.authorize()`. Fix is the lazy listener attach inside the StateFlow collector (TASK-54 Step 0, Option A).

Uses the same `@UninstallModules(BackupModule::class)` + local `TestBackupModule` pattern as `DriveBackupWorkerTest` to wire `FakeDriveAuthManager` (which exposes a new `authorizeCallCount: Int` field — incremented on every `authorize()` invocation) and `FakeDriveRemoteSource`. Pre-seeds DataStore with `DRIVE_ENABLED = true` so the StateFlow collector has a real reason to flip `binding.switchDrive.isChecked` from the XML default `false` → `true`.

| Test | Description |
|------|-------------|
| `firstEntry_withDriveEnabled_doesNotCallAuthorize` | Launch fragment, move to RESUMED, settle one frame; assert `fakeAuth.authorizeCallCount` did not increment. Pre-fix this fails because the listener attached at line 69 fires when the StateFlow collector's first emission flips `isChecked` |
| `reEntry_viaActivityRecreation_doesNotCallAuthorize` | Launch + RESUMED, capture `authorizeCallCount`, then `FragmentScenario.recreate()` (full activity recreation — the canonical "navigate away and back" simulation that exercises view-state save/restore), settle one frame, assert the counter did not move. This is the exact reproduction trigger for the user-reported bug |

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
