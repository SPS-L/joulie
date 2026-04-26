# Test Plan — EV Efficiency Tracker

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
| `emptyEvents_returnsZeroStats` | empty list | Stats with all zeros |
| `singleEvent_noEfficiency` | 1 event only | chargeCount=0, avgEfficiency=0 |
| `twoEvents_correctEfficiency` | odo 0→100km, 20kWh | 5.0 km/kWh |
| `multipleEvents_sumCorrect` | 3 events, mixed | sum kWh and dist correct |
| `negativeOdometerDelta_skipped` | odo goes backwards | skipped; no contribution |

### 1.3 DateUtilsTest.kt

| Test | Input | Expected |
|------|-------|----------|
| `startOf30DaysAgo` | today | epoch of 30 days ago at 00:00 |
| `startOfYear` | today | Jan 1 of current year |
| `customRangeInclusive` | from/to | both endpoints included |

---

## 2. Integration Tests (Room in-memory)

### 2.1 ChargeEventDaoTest.kt

Uses `Room.inMemoryDatabaseBuilder`.

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

---

## 4. UI / Instrumented Tests (Espresso + Navigation Testing)

### 4.1 DashboardFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `statsCards_displayedOnLaunch` | launch app | 4+ stat cards visible |
| `fabClick_opensChargeEdit` | tap FAB | ChargeEditFragment in back stack |
| `emptyState_showsMessage` | no events | "No charges recorded" text visible |
| `carSpinner_switchesCar` | select different car | stats cards update |

### 4.2 ChargeEditFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `saveValid_dismisses` | fill valid data, tap Save | returns to Dashboard |
| `saveEmptyOdometer_showsError` | blank odometer, tap Save | error hint visible |
| `saveLowerOdometer_showsError` | odo < previous, tap Save | error "must be higher" |
| `saveZeroKwh_showsError` | kwh=0, tap Save | error visible |
| `datePicker_updatesButton` | tap date button | picker opens, selection shown |

### 4.3 SettingsFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `unitToggle_km_to_miles` | toggle unit | dashboard shows mi/kWh |
| `resetCarData_confirmDialog` | tap Reset Car | confirmation dialog shown |
| `resetCarData_confirm_deletesData` | confirm reset | events gone from log |

### 4.4 ChartsFragmentTest.kt

| Test | Action | Assertion |
|------|--------|-----------|
| `tabSwitch_showsCorrectChart` | tap "Monthly Energy" tab | bar chart visible |
| `noData_emptyState` | no events | chart shows "No data" |

---

## 5. Manual Smoke Tests (pre-APK release)

1. Fresh install → add car → add 3 charge events → verify stats cards
2. Switch units km→miles → verify all values convert
3. Enable Drive backup → complete auth → verify backup file in Drive App Folder via Drive API explorer
4. Uninstall & reinstall → enable Drive → restore → verify data present
5. Reset car data → confirm events gone, car still present
6. Export CSV → open in spreadsheet app → verify columns and values
7. Custom period picker → select last 60 days → stats card updates
8. Dark mode → launch app → verify no illegible text or invisible elements
9. Add second car → switch → logs and stats are independent
10. Swipe-to-delete charge event → verify removed from list and stats update

---

## 6. Coverage Targets

| Layer | Target |
|-------|--------|
| Data models + utils | ≥ 90% line coverage |
| DAOs (Room) | ≥ 85% |
| ViewModels | ≥ 80% |
| UI fragments | smoke tests (≥ 10 scenarios) |
