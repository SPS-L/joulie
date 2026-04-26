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
| `emptyEvents_returnsZeroStats` | empty list | Stats with all zeros/nulls |
| `singleEvent_noEfficiency` | 1 event only | chargeCount=0, avgEfficiency=null |
| `twoEvents_correctEfficiency` | odo 0→100km, 20kWh | 5.0 km/kWh |
| `multipleEvents_sumCorrect` | 3 events, mixed | sum kWh and dist correct |
| `negativeOdometerDelta_skipped` | odo goes backwards | skipped; no contribution |

### 1.3 CostParserTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `costZero_returnsNull` | value=0.0, kwh=10 | (null, null) |
| `costBlank_returnsNull` | value=null, kwh=10 | (null, null) |
| `costNegative_returnsNull` | value=-5.0, kwh=10 | (null, null) |
| `costTotal_derivesPerKwh` | value=3.0, kwh=10, TOTAL | (3.0, 0.30) |
| `costPerKwh_derivesTotal` | value=0.30, kwh=10, PER_KWH | (3.0, 0.30) |
| `kwhZero_returnsNull` | value=5.0, kwh=0 | (null, null) |

### 1.4 StatsCalculatorCostTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `allCostNull_costStatsNull` | 3 events, all cost=NULL | costPerKm=null |
| `mixedCost_sumNonNullOnly` | 5 events, 3 with cost, 2 NULL | sum over 3 events only |
| `singleCostEvent_correct` | 1 event, cost=5.0, 50km | costPerKm=0.10 |

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
| `increment_existingLabel` | Insert + increment; use_count = 2 |
| `getTopLocations_maxFive` | Insert 8 labels; top query returns 5 |
| `getTopLocations_sortedByUseCount` | 5 labels with varying counts; highest first |
| `delete_removesEntry` | Insert + delete; query returns empty |

### 2.4 MigrationTest.kt

| Test | Description |
|------|-------------|
| `migrate_1_to_2` | Adds `chargeType` column |
| `migrate_2_to_3` | Creates `custom_locations`; adds cost/location/note columns |
| `migrate_1_to_3` | Full chain; all columns and tables present |

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
| `saveWithCostZero_storesNull` | Submit form with cost=0; DB entry has `cost_total=null` |
| `saveWithCost_storesBoth` | Submit form with cost=5.0, kwh=10; `cost_total=5.0`, `cost_per_kwh=0.5` |
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
11. Enable Drive backup → complete auth → verify backup file via Drive API explorer
12. Uninstall & reinstall → enable Drive → restore → verify data + location chips present
13. Reset car data → confirm events gone, car still present
14. Export CSV → open in spreadsheet app → verify columns and values
15. Custom period picker → select last 60 days → stats card updates
16. Dark mode → launch app → verify no illegible text or invisible elements
17. Add second car → switch → logs and stats are independent
18. Swipe-to-delete charge event → removed from list; stats update

---

## 6. Coverage Targets

| Layer | Target |
|-------|--------|
| Data models + utils | ≥ 90% line coverage |
| DAOs (Room) | ≥ 85% |
| ViewModels | ≥ 80% |
| UI fragments | smoke tests (≥ 18 scenarios) |
