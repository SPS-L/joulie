# Test Plan — v3 Additions

> These tests supplement `TEST_PLAN.md` and `TEST_PLAN_V2.md`.
> Run the full suite after completing all AGENT_INSTRUCTIONS_V3 steps.

---

## 1. Unit Tests

### CostParserTest
| Test | Input | Expected |
|------|-------|----------|
| `costZero_returnsNull` | value=0.0, kwh=10 | (null, null) |
| `costBlank_returnsNull` | value=null, kwh=10 | (null, null) |
| `costNegative_returnsNull` | value=-5.0, kwh=10 | (null, null) |
| `costTotal_derivesPerKwh` | value=3.0, kwh=10, TOTAL | (3.0, 0.30) |
| `costPerKwh_derivesTotal` | value=0.30, kwh=10, PER_KWH | (3.0, 0.30) |
| `kwhZero_returnsNull` | value=5.0, kwh=0 | (null, null) |

### StatsCalculatorTest (cost)
| Test | Input | Expected |
|------|-------|----------|
| `allCostNull_costStatsNull` | 3 events, all cost=NULL | costPerKm=null |
| `mixedCost_sumNonNullOnly` | 5 events, 3 with cost, 2 NULL | sum over 3 events only |
| `singleCostEvent_correct` | 1 event, cost=5.0, 50km | costPerKm=0.10 |

---

## 2. Room Integration Tests

### CustomLocationDaoTest
| Test | Description |
|------|-------------|
| `insert_newLabel_stored` | Insert label; query returns it |
| `insert_duplicate_ignored` | Insert same label twice; count = 1 |
| `increment_existingLabel` | Insert + increment; use_count = 2 |
| `getTopLocations_maxFive` | Insert 8 labels; top query returns 5 |
| `getTopLocations_sortedByUseCount` | 5 labels with varying counts; highest first |
| `delete_removesEntry` | Insert + delete; query returns empty |

### MigrationTest
| Test | Description |
|------|-------------|
| `migrate_1_to_3_complete` | Start at DB v1; run all migrations; verify `custom_locations` table exists and cost columns present |
| `migrate_2_to_3_complete` | Start at DB v2; run migration 2→3; verify same |

---

## 3. ViewModel Tests

### WizardViewModelTest
| Test | Description |
|------|-------------|
| `finish_writesSetupComplete_true` | Call `finish()`; DataStore has `setupComplete=true` |
| `finish_writesAllPrefs` | Call `finish()` with custom values; verify metric, unit, currency all written |
| `defaultValues_correct` | Fresh VM: metric=`km_per_kwh`, unit=`km`, currency=`EUR` |

### ChargeEditViewModelTest
| Test | Description |
|------|-------------|
| `saveWithCostZero_storesNull` | Submit form with cost=0; DB entry has `cost_total=null` |
| `saveWithCost_storesBoth` | Submit form with cost=5.0, kwh=10; `cost_total=5.0`, `cost_per_kwh=0.5` |
| `saveLocation_recordsUsage` | Submit with location="Home"; `custom_locations` has "Home" with count ≥ 1 |

---

## 4. Espresso UI Tests

### WizardFlowTest
| Test | Steps | Expected |
|------|-------|----------|
| `firstLaunch_showsWizard` | Clear DataStore; launch app | WizardFragment visible |
| `secondLaunch_skipsWizard` | Set `setupComplete=true`; launch app | DashboardFragment visible |
| `getStarted_advancesToPage2` | Tap Get Started | Page 2 (metric) visible |
| `next_from_page2_to_page3` | Tap Next on page 2 | Page 3 (currency) visible |
| `finish_navigatesToDashboard` | Complete all pages; tap Finish | DashboardFragment visible |
| `back_navigatesToPreviousPage` | On page 2, tap Back | Page 1 visible |
| `resetPrefs_reshowsWizard` | Settings → Reset preferences | WizardFragment shown |

### LocationChipTest
| Test | Steps | Expected |
|------|-------|----------|
| `homeChip_visible` | Open ChargeEdit | Home chip present |
| `tapHomeChip_fillsTextField` | Tap Home chip | Location field = "Home" |
| `addChip_focusesTextField` | Tap + Add chip | Location field focused |
| `savedCustomLocation_appearsAsChip` | Save event with location "Supercharger"; open new ChargeEdit | "Supercharger" chip visible |
| `customChips_showMax5` | Save 6 different locations; open ChargeEdit | At most 5 custom chips shown |

### CostTest
| Test | Steps | Expected |
|------|-------|----------|
| `costZero_notInStatsCard` | All events have cost=0; open Dashboard | Cost row not visible |
| `costPresent_showsStatsCard` | At least 1 event has cost; open Dashboard | Cost row visible |

---

## 5. Manual Smoke Tests (v3)

1. Fresh install → wizard appears → complete → dashboard shown with empty state
2. Rotate screen mid-wizard → state preserved on page 2/3
3. Kill app on page 2 → relaunch → wizard starts from page 1
4. Add charge with no cost → Dashboard cost row absent
5. Add charge with cost → Dashboard cost row present with correct value
6. Add 3 charges with same location → chip shows after 1st, count increments
7. Add 8 unique locations → only 5 custom chips in form; all 8 in Manage Locations
8. Reset preferences → wizard shown; previous car data intact
9. Drive backup with v3 JSON → restore on fresh install → custom location chips restored
10. Unit switch km↔miles → wizard unit toggle reflects immediately on page 2
