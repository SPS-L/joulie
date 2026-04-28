# Sub-project F1 — Settings remainder & ManageLocations

> **Status:** spec, ready for review. Predecessors A/B/C/D/E shipped (see `CLAUDE.md`). This is the first half of "Sub-project F" per `CLAUDE.md`'s placeholder list. The Charts screen is intentionally out of scope and will be a separate sub-project (**F2**).

## 1. Goal

Wire every still-placeholder Settings row, add the `ManageLocations` screen, and ship two new "reset data" use cases. After F1 lands, the only remaining placeholder fragment in the app is `ChartsFragment`.

## 2. Scope

### In

- **Settings rows (replacing the disabled placeholders in `fragment_settings.xml:49-110`):**
  - Primary metric picker
  - Distance unit picker (with auto-flip coupling to primary metric)
  - Currency picker (curated dropdown, same source as wizard)
  - Theme picker (System / Light / Dark, applied immediately)
  - Manage custom locations entry → navigates to ManageLocations
  - Reset preferences (re-runs wizard)
  - Reset data for active car (clears that car's `charge_events` only)
  - Reset all data (clears cars, charge events, custom locations, then wizard)
  - Export CSV (active car, single share intent)
- **ManageLocations screen** — RecyclerView with empty state, swipe-to-delete + 5-second Snackbar Undo, observes `LocationReader.observeAll()`.
- **Two new use cases** — `ResetActiveCarDataUseCase`, `ResetAllDataUseCase`.
- **Narrow-interface extensions** — additions to `SettingsReader/Writer`, `ChargeEventWriter`, `LocationWriter`, `CarWriter`. One new DAO method (`ChargeEventDao.deleteForCar`).
- **Tests** — JVM unit tests for both ViewModels and both new use cases; instrumented tests for `SettingsFragment` (theme / CSV / reset-all) and `ManageLocationsFragment` (swipe + undo).

### Out (deferred)

- ChartsFragment / ChartsViewModel / chart rendering — sub-project **F2**.
- Renaming custom locations — DESIGN.md doesn't require it; `charge_events.location` is a free-text snapshot, not an FK reference, so rename has awkward semantics. YAGNI.
- "Export all cars CSV" — current `ExportCsvUseCase` is per-car; there is no UX request for an all-cars bundle.
- Settings search, settings categories beyond simple section headers, animations on theme switch.

## 3. Confirmed product decisions (Q&A round, 2026-04-28)

| # | Decision | Implication |
|---|---|---|
| Q1 | F split into F1 (this spec) + F2 (Charts, later) | Smaller blast radius per branch |
| Q2 | Reset = two separate Settings rows (active-car / global) | Two distinct use cases, two distinct confirms |
| Q3 | "Reset all data" wipes cars too, then re-routes through wizard | After the wizard finishes, Dashboard shows empty state until user adds a car via Cars |
| Q4 | CSV export is active-car only | Reuses `ExportCsvUseCase(activeCarId, useKm)` unchanged; row disabled when `activeCarId == -1` |
| Q5 | Primary metric + distance unit are two rows but coupled with auto-flip | Atomic DataStore write, Snackbar surfaces the auto-change |
| Q6 | ManageLocations is delete-only | No rename; past `charge_events.location` strings stay intact |
| Q7 | Currency picker is curated dropdown from `R.array.currencies` (same source as wizard) | If stored value isn't in the list, the dropdown still shows it as the current value |

## 4. Architecture

The 4-layer architecture from CLAUDE.md is preserved. F1 only **adds** (no rewires):

```
UI:       SettingsFragment + SettingsViewModel              (extended, retains all E wiring)
          ManageLocationsFragment + ManageLocationsViewModel (full implementation)
          ui/common/                                         (reuse existing helpers)
          core/model/  SettingsUiState (extended) · SettingsEvent (extended)
                       ManageLocationsUiState · ManageLocationsEvent (new)
Domain:   Use cases    ResetActiveCarDataUseCase · ResetAllDataUseCase   (new)
                       ExportCsvUseCase                                  (existing, unchanged)
          Narrow IFs   SettingsReader (+ theme) · SettingsWriter (+ setTheme/setPrimaryMetric/
                       setDistanceUnit/setCurrency/setSetupComplete/setPrimaryMetricAndDistanceUnit)
                       ChargeEventWriter (+ deleteForCar / deleteAll)
                       LocationWriter (+ deleteAll)
                       CarWriter (+ deleteAll)
Repo:     SettingsRepository · ChargeEventRepository · LocationRepository · CarRepository
          (each implements the new methods on its narrow interface)
Data:     Room DAOs    ChargeEventDao (+ deleteForCar)        — only new DAO method
                       CarDao.deleteAll · CustomLocationDao.deleteAll · ChargeEventDao.deleteAll
                       (already exist; spec verified by reading the files)
          DataStore    PreferenceKeys.THEME (already exists)
```

Single-Activity + Navigation Component is unchanged. ManageLocations destination already exists in `nav_graph.xml` and is already in MainActivity's BottomNav-hide set.

## 5. Settings — row-by-row behaviour

### 5.1 General row UI

All rows follow E's pattern (a clickable LinearLayout containing a title `TextView` and an optional summary `TextView`). On tap, the row fires a ViewModel action; for rows that need a choice, the Fragment shows an `AlertDialog` (single-choice or text-input). Dialogs cancel without writing; confirm calls a single VM method that performs the write atomically.

After every successful write, the relevant row's summary updates because the VM's `uiState` re-emits from the DataStore Flow.

### 5.2 Primary metric

- **Title:** `R.string.settings_primary_metric` ("Primary metric").
- **Summary:** human-readable label of current value (e.g. "kWh per 100 km").
- **Tap:** `AlertDialog.Builder.setSingleChoiceItems` with three options:
  - "km / kWh" → `km_per_kwh`
  - "kWh / 100 km" → `kwh_per_100km`
  - "mi / kWh" → `mi_per_kwh`
- **Confirm:** VM call `onPrimaryMetricSelected(metric)`.
- **Auto-flip rule:** if the new metric's required unit (per DESIGN §3.4) differs from `distanceUnit`, the VM writes both keys atomically (see §6.1) and emits `SettingsEvent.AutoFlippedDistanceUnit(newUnit)`. The Fragment shows a Snackbar: "Distance unit also changed to miles" / "…to km".

### 5.3 Distance unit

- **Title:** `R.string.settings_distance_unit` ("Distance unit").
- **Summary:** "km" or "miles" (localized).
- **Tap:** AlertDialog single-choice with two items.
- **Confirm:** VM call `onDistanceUnitSelected(unit)`.
- **Auto-flip rule:** mirror image — if the new unit conflicts with current metric, atomically flip metric to its compatible default:
  - new unit = miles, current metric ∈ {km_per_kwh, kwh_per_100km} → metric becomes `mi_per_kwh`
  - new unit = km, current metric = mi_per_kwh → metric becomes `km_per_kwh` (deterministic default; user can change to `kwh_per_100km` afterward)

  VM emits `SettingsEvent.AutoFlippedPrimaryMetric(newMetric)`; Fragment shows Snackbar.

### 5.4 Currency

- **Title:** `R.string.settings_currency` ("Currency").
- **Summary:** current 3-letter code (e.g. "EUR").
- **Tap:** AlertDialog containing a `TextInputLayout` with a `MaterialAutoCompleteTextView` (`exposed_dropdown_menu` style), adapter from `R.array.currencies` (same array the wizard uses).
- **Confirm:** VM call `onCurrencySelected(code)`. No validation — DataStore stores whatever string the user picked. If the stored value isn't in the array (e.g. legacy state), the dropdown text simply shows the stored value verbatim.

### 5.5 Theme

- **Title:** `R.string.settings_theme` ("Theme").
- **Summary:** localized label of current value.
- **Tap:** AlertDialog single-choice with three options:
  - "System default" → `system`
  - "Light" → `light`
  - "Dark" → `dark`
- **Confirm:** VM call `onThemeSelected(theme)` — writes to DataStore. The Fragment ALSO calls `AppCompatDelegate.setDefaultNightMode(...)` immediately so the change is visible without an app restart. (`EVTrackerApp.onCreate` already applies the saved theme on cold start; this Fragment-level call covers the in-session change.)

### 5.6 Manage custom locations

- **Title:** `R.string.settings_manage_locations` ("Manage custom locations").
- **Summary:** "{n} saved" — count from `LocationReader.observeAll().map { it.size }`. Hide the summary when n == 0.
- **Tap:** `findNavController().navigate(R.id.action_settings_to_manage_locations)`.

### 5.7 Reset preferences

- **Title:** `R.string.settings_reset_preferences` ("Reset preferences").
- **Summary:** "You'll go through setup again."
- **Tap:** Confirm dialog ("Reset all preferences and re-run setup?"). On confirm:
  1. VM calls `SettingsWriter.setSetupComplete(false)`.
  2. VM emits `SettingsEvent.NavigateToWizard`.
  3. Fragment navigates with `popUpTo(R.id.nav_graph)` + `popUpToInclusive=true` so back from the wizard exits the app.

  This **does not** clear stored values for primaryMetric/distanceUnit/currency — re-running the wizard will overwrite them. Cars and charge events are untouched.

### 5.8 Reset data for {activeCar.name}

- **Title:** `R.string.settings_reset_active_car` formatted with the active car's name (e.g. "Reset data for Tesla Model 3"). When `activeCarId == -1`, title is "Reset data for active car" and the row is disabled (alpha 0.5, click ignored).
- **Summary:** "Delete all charge events for this car. Cars and locations are kept."
- **Tap:** Confirm dialog ("Delete all charge events for {name}? This cannot be undone."). On confirm:
  1. VM calls `ResetActiveCarDataUseCase(activeCarId)` (see §6.2).
  2. Snackbar "Charge events deleted."

### 5.9 Reset all data

- **Title:** `R.string.settings_reset_all` ("Reset all data").
- **Summary:** "Delete all cars, charge events, and custom locations."
- **Tap:** Confirm dialog ("Delete everything? This cannot be undone.") On confirm:
  1. VM calls `ResetAllDataUseCase()` (see §6.3).
  2. VM emits `SettingsEvent.NavigateToWizard`.

  Post-wizard, Dashboard shows the empty-state ("Add a car to begin") because `activeCarId == -1`.

### 5.10 Export CSV

- **Title:** `R.string.settings_export_csv` ("Export CSV").
- **Summary:** "Share charge events as a CSV file."
- **Disabled:** when `activeCarId == -1` (alpha 0.5, click ignored).
- **Tap:** VM call `onExportCsv()`:
  1. Calls `ExportCsvUseCase.export(activeCarId, useKm = (distanceUnit == "km"))` on `Dispatchers.IO`.
  2. On success, emits `SettingsEvent.LaunchCsvShareIntent(uri)`.
  3. Fragment builds `Intent.ACTION_SEND` with `type="text/csv"`, `EXTRA_STREAM = uri`, `FLAG_GRANT_READ_URI_PERMISSION`, wraps in `Intent.createChooser`, starts.
  4. On `IOException` or `IllegalArgumentException` (unknown carId), VM emits `SettingsEvent.ShowError(R.string.settings_export_csv_failed)`.

## 6. Domain layer — new code

### 6.1 SettingsWriter additions

```kotlin
interface SettingsWriter {
    // Existing E methods retained:
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)

    // New (F1):
    suspend fun setTheme(value: String)
    suspend fun setPrimaryMetric(metric: String)
    suspend fun setDistanceUnit(unit: String)
    suspend fun setCurrency(code: String)
    suspend fun setSetupComplete(value: Boolean)

    /** Writes both keys in a single dataStore.edit { ... } block. */
    suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String)
}
```

`SettingsRepository` already has `setTheme(theme)` and a private equivalent of `setSetupComplete(false)` named `resetSetupComplete()`; F1 lifts both onto the interface and renames `resetSetupComplete` to `setSetupComplete(false)` for symmetry. The atomic combined writer is the only one with new logic — it's a single `edit { prefs -> prefs[METRIC] = ...; prefs[UNIT] = ... }` block.

`SettingsReader` gains `val theme: Flow<String>` (already implemented as a `val` on the repo, just lifted to the interface).

### 6.2 ResetActiveCarDataUseCase

```kotlin
class ResetActiveCarDataUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(carId: Int) {
        require(carId != -1) { "ResetActiveCarDataUseCase called with carId=-1" }
        chargeEventWriter.deleteForCar(carId)
        backupScheduler.enqueueBackup()
    }
}
```

`ChargeEventWriter` gains `suspend fun deleteForCar(carId: Int)` backed by a new DAO query `@Query("DELETE FROM charge_events WHERE carId = :carId") suspend fun deleteForCar(carId: Int)`.

Custom locations are NOT cleared by this use case — a per-car reset shouldn't wipe shared learned labels.

### 6.3 ResetAllDataUseCase

```kotlin
class ResetAllDataUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val locationWriter: LocationWriter,
    private val carWriter: CarWriter,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke() {
        // ChargeEvents first; cars last would also work because of FK ON DELETE CASCADE,
        // but explicit ordering matches the order tests assert and avoids relying on cascade.
        chargeEventWriter.deleteAll()
        locationWriter.deleteAll()
        carWriter.deleteAll()
        settingsWriter.setActiveCarId(-1)
        settingsWriter.setSetupComplete(false)
        backupScheduler.enqueueBackup()
    }
}
```

`ChargeEventWriter`, `LocationWriter`, and `CarWriter` each gain a `suspend fun deleteAll()` that delegates to their existing DAO `deleteAll()`. The DAO methods are already in place (verified by reading `ChargeEventDao.kt:39-40`, `CarDao.kt:40-41`, `CustomLocationDao.kt:30-31`).

The use case does **not** wrap its writes in a single transaction. The writes are sequenced through narrow interfaces (Room DAOs called from the data layer), and an interrupted reset just leaves the app on the wizard at next launch (which is also where `setSetupComplete(false)` would have taken it). Acceptable per DESIGN.md's "best-effort, no rollback" reset model.

The post-reset `enqueueBackup()` causes WorkManager to upload an effectively-empty snapshot (no cars ⇒ no events) to Drive. That is the intended behaviour — if the user wants to abandon the cloud copy too, they should toggle Drive off **before** the global reset.

### 6.4 What about Drive backup pre-reset?

A user might want a "save current state to Drive" prompt before destroying everything. The existing pattern (E shipped) doesn't offer pre-action snapshots; the Drive flow only re-uploads after committed changes. F1 keeps the same model — no pre-reset upload — but the confirm dialog text spells out "Cannot be undone" so the user knows.

## 7. ManageLocations screen

### 7.1 Layout (`fragment_manage_locations.xml`)

`CoordinatorLayout` containing:
- `RecyclerView` (`androidx.recyclerview.widget.RecyclerView`) — `MATCH_PARENT`, vertical `LinearLayoutManager`, `MaterialDividerItemDecoration`.
- Empty-state `TextView` — center-of-parent, `gone` by default, text `R.string.manage_locations_empty` ("Locations you save on charge events will appear here.").

The Fragment toggles the empty-state visibility based on `uiState.locations.isEmpty()`.

### 7.2 Row layout (`item_custom_location.xml`)

- Title `TextView` — `label`, e.g. "Office garage".
- Subtitle `TextView` — "Used N time(s) · last on {relative date}". Relative date via `DateUtils.getRelativeTimeSpanString(lastUsed, now, DAY_IN_MILLIS, FORMAT_ABBREV_RELATIVE)`. When `useCount == 0`, subtitle is just "Last on {date}".

### 7.3 RecyclerView + ItemTouchHelper

- Adapter is a `ListAdapter<CustomLocationEntity, _>` with a `DiffUtil.ItemCallback` keyed on `label`.
- `ItemTouchHelper` callback:
  - `getMovementFlags`: 0 for drag, `LEFT or RIGHT` for swipe.
  - `onSwiped(viewHolder, direction)` → fragment calls `vm.onSwipeDelete(label)`.

### 7.4 ViewModel (`ManageLocationsViewModel`)

Injects: `LocationReader`, `LocationWriter`, `BackupScheduler`.

State:

```kotlin
data class ManageLocationsUiState(
    val locations: List<CustomLocationEntity> = emptyList(),
    /** Labels currently in their 5-second cancel window. Filtered out of the visible list. */
    val pendingDeletions: Set<String> = emptySet()
)

sealed class ManageLocationsEvent {
    data class ShowUndoSnackbar(val label: String) : ManageLocationsEvent()
}
```

The visible list shown to the Fragment is `locations.filter { it.label !in pendingDeletions }`.

`onSwipeDelete(label)`:
1. Adds `label` to `pendingDeletions` so the row visually disappears.
2. Stores a 5-second cancellable `Job` in a `Map<String, Job>` (one per label, mirroring History's pattern). Inside the job: `delay(5_000); commitDelete(label)`.
3. Emits `ShowUndoSnackbar(label)`.

`onUndoDelete(label)`:
1. Cancels the `Map[label]` job.
2. Removes `label` from `pendingDeletions`.

`commitDelete(label)`:
1. Looks up the entity in `uiState.locations` by label. If absent (race with `observeAll` emission), no-op.
2. Calls `LocationWriter.delete(entity)` (the existing `delete(CustomLocationEntity)` signature).
3. `BackupScheduler.enqueueBackup()`.
4. Removes the label from `pendingDeletions`.

`onCleared()`: cancels every job in the map (process death / fragment destroyed → in-memory pending deletions are dropped, DB row stays).

### 7.5 Empty / loading

- Empty list ⇒ empty-state TextView visible, RecyclerView gone.
- No explicit loading state — `observeAll` emits the current snapshot immediately and the fragment binds when STARTED.

### 7.6 Drive enqueue

`enqueueBackup()` is suspending and respects the `driveEnabled` gate (BackupScheduler contract — see `BackupScheduler.kt:6-8`). When Drive is off, the call is a no-op.

## 8. SettingsViewModel — extended state

```kotlin
data class SettingsUiState(
    // Drive (E):
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    val pendingRestoreLabel: String? = null,

    // F1:
    val primaryMetric: String = "km_per_kwh",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val theme: String = "system",
    val activeCarId: Int = -1,
    val activeCarName: String? = null,
    val customLocationCount: Int = 0
)

sealed class SettingsEvent {
    // E:
    data class ShowRestorePrompt(val label: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msg: Int) : SettingsEvent()

    // F1:
    data class AutoFlippedDistanceUnit(val newUnit: String) : SettingsEvent()
    data class AutoFlippedPrimaryMetric(val newMetric: String) : SettingsEvent()
    data class LaunchCsvShareIntent(val uri: Uri) : SettingsEvent()
    object NavigateToWizard : SettingsEvent()
}
```

The `init { ... }` block in the ViewModel adds new flow collectors for `primaryMetric`, `distanceUnit`, `currency`, `theme`, `activeCarId` (then derives `activeCarName` via `CarReader.getById`), and the count from `LocationReader.observeAll().map { it.size }`. Each `update` mutates the corresponding `uiState` field.

### 8.1 Auto-flip helper (in ViewModel)

```kotlin
private fun unitFor(metric: String): String = when (metric) {
    "mi_per_kwh" -> "miles"
    "km_per_kwh", "kwh_per_100km" -> "km"
    else -> "km"
}

private fun defaultMetricFor(unit: String, currentMetric: String): String =
    when (unit) {
        "miles" -> "mi_per_kwh"
        "km"    -> if (currentMetric == "mi_per_kwh") "km_per_kwh" else currentMetric
        else    -> currentMetric
    }
```

`onPrimaryMetricSelected(metric)`:
- `requiredUnit = unitFor(metric)`
- if `requiredUnit != current distanceUnit` → `setPrimaryMetricAndDistanceUnit(metric, requiredUnit)` + emit `AutoFlippedDistanceUnit(requiredUnit)`
- else → `setPrimaryMetric(metric)`

`onDistanceUnitSelected(unit)`:
- `newMetric = defaultMetricFor(unit, current primaryMetric)`
- if `newMetric != current primaryMetric` → `setPrimaryMetricAndDistanceUnit(newMetric, unit)` + emit `AutoFlippedPrimaryMetric(newMetric)`
- else → `setDistanceUnit(unit)`

## 9. Navigation graph

Add to `nav_graph.xml`:

```xml
<fragment android:id="@+id/settingsFragment" ... >
    <action android:id="@+id/action_settings_to_manage_locations"
            app:destination="@id/manageLocationsFragment"/>
    <action android:id="@+id/action_settings_to_wizard"
            app:destination="@id/wizardFragment"
            app:popUpTo="@id/nav_graph"
            app:popUpToInclusive="true"/>
</fragment>
```

`manageLocationsFragment` destination already exists. `MainActivity.kt:39-46` already lists it in the BottomNav-hide set.

## 10. Strings (`res/values/strings.xml`)

New keys (English):

```
settings_primary_metric              Primary metric
settings_distance_unit               Distance unit
settings_currency                    Currency
settings_theme                       Theme
settings_manage_locations            Manage custom locations
settings_manage_locations_summary    %d saved
settings_reset_preferences           Reset preferences
settings_reset_preferences_summary   You'll go through setup again.
settings_reset_active_car            Reset data for %1$s
settings_reset_active_car_default    Reset data for active car
settings_reset_active_car_summary    Delete all charge events for this car. Cars and locations are kept.
settings_reset_active_car_confirm    Delete all charge events for %1$s? This cannot be undone.
settings_reset_active_car_done       Charge events deleted.
settings_reset_all                   Reset all data
settings_reset_all_summary           Delete all cars, charge events, and custom locations.
settings_reset_all_confirm           Delete everything? This cannot be undone.
settings_export_csv                  Export CSV
settings_export_csv_summary          Share charge events as a CSV file.
settings_export_csv_failed           CSV export failed.
settings_theme_system                System default
settings_theme_light                 Light
settings_theme_dark                  Dark
settings_unit_flipped_to_km          Distance unit also changed to km.
settings_unit_flipped_to_miles       Distance unit also changed to miles.
settings_metric_flipped              Primary metric also changed to %1$s.   <!-- %1$s = the localized metric label, not the raw token (e.g. "kWh per 100 km", not "kwh_per_100km"). The VM passes the localized string from R.string.metric_label_* -->
metric_label_km_per_kwh              km / kWh
metric_label_kwh_per_100km           kWh / 100 km
metric_label_mi_per_kwh              mi / kWh
manage_locations_title               Manage custom locations
manage_locations_empty               Locations you save on charge events will appear here.
manage_locations_row_count           Used %1$d time(s) · last %2$s
manage_locations_row_count_zero      Last %1$s
manage_locations_undo_snackbar       Deleted "%1$s"
common_undo                          Undo
common_cancel                        Cancel
common_confirm                       Confirm
```

`R.array.currencies` is reused from the wizard.

## 11. Tests

### 11.1 JVM (`app/src/test/java/...`)

#### `SettingsViewModelTest` — extends existing E tests with F1 coverage:
- `primaryMetric_select_compatibleUnit_writesOnlyMetric`
- `primaryMetric_select_incompatibleUnit_writesBoth_emitsAutoFlipUnit`
- `distanceUnit_select_compatibleMetric_writesOnlyUnit`
- `distanceUnit_select_incompatibleMetric_writesBoth_emitsAutoFlipMetric`
- `currency_select_writesValue`
- `theme_select_writesValue`
- `resetActiveCar_disabled_whenNoActiveCar` (asserts row state via uiState; VM rejects the call)
- `resetActiveCar_callsUseCase_withActiveCarId`
- `resetAllData_callsUseCase_emitsNavigateToWizard`
- `resetPreferences_setsSetupCompleteFalse_emitsNavigateToWizard`
- `exportCsv_disabled_whenNoActiveCar`
- `exportCsv_success_emitsLaunchIntent`
- `exportCsv_ioException_emitsShowError`
- `customLocationCount_reflectsLocationReaderEmission`

#### `ResetActiveCarDataUseCaseTest`:
- `invoke_deletesEventsForGivenCarOnly`
- `invoke_doesNotTouchOtherCars`
- `invoke_enqueuesBackup`
- `invoke_throwsForCarIdMinusOne`

#### `ResetAllDataUseCaseTest`:
- `invoke_clearsAllFourCollections` (events, locations, cars, settings flags)
- `invoke_setsActiveCarIdToMinusOne`
- `invoke_setsSetupCompleteFalse`
- `invoke_enqueuesBackup`
- `invoke_callsWritersInOrder` (events → locations → cars → settings)

#### `ManageLocationsViewModelTest`:
- `observe_emitsSortedList` (uses Fakes; sort order = useCount DESC, lastUsed DESC)
- `swipe_addsToPendingDeletions_emitsSnackbar`
- `swipe_then_undo_cancelsJob_removesFromPending`
- `swipe_then_5sElapses_callsLocationWriterDelete_andEnqueueBackup`
- `swipe_multipleLabels_each_has_independent_job`
- `swipe_then_clearVm_cancelsAllJobs_doesNotCallDelete`
- `emptyList_uiState_isEmpty`

All swipe tests use `StandardTestDispatcher` + `advanceTimeBy(5_001)`, matching D's History tests.

### 11.2 Instrumented (`app/src/androidTest/java/...`)

#### `SettingsFragmentTest` (Hilt + Espresso):
- `themeRow_tap_opensDialog_select_dark_updatesSummary`
- `exportCsv_disabled_whenNoActiveCar`
- `resetAll_confirm_navigatesToWizard`

#### `ManageLocationsFragmentTest`:
- `swipe_showsSnackbar_undo_restoresRow`
- `swipe_no_undo_after_5s_rowIsGoneAfterReopen` (asserts via DB state)

These follow D's existing Espresso patterns (Hilt test runner, `IntentsTestRule` not needed — CSV share goes through ACTION_SEND chooser which we don't intercept here).

### 11.3 Coverage targets

- F1 raises the JVM test count from ~152 to ~182 (~30 new JVM tests: 14 in `SettingsViewModelTest`, 4 in `ResetActiveCarDataUseCaseTest`, 5 in `ResetAllDataUseCaseTest`, 7 in `ManageLocationsViewModelTest`).
- F1 adds 5 instrumented tests (3 + 2); the instrumented suite still compiles via `:app:assembleDebugAndroidTest`. Running them needs an emulator (sandbox-incompatible).

## 12. Edge cases

| Scenario | Behaviour |
|---|---|
| Tap "Reset data for active car" then immediately switch active car via Cars screen | The confirm dialog already captured the carId at tap; if the user confirms after the switch, the use case operates on the originally-tapped car. (Optional refinement: dismiss the dialog when activeCarId changes — not required for F1.) |
| Tap "Export CSV" when active car has zero events | `ExportCsvUseCase` writes a CSV with only the header line; share sheet opens with that empty file. Acceptable. |
| Theme change with `EVTrackerApp` already initialized | Fragment calls `AppCompatDelegate.setDefaultNightMode` on the calling thread; the recreate cascade is handled by AppCompat. No extra Application work needed. |
| Currency stored value is not in `R.array.currencies` (legacy, e.g. "ZWL") | Dropdown displays "ZWL" as current text. User can pick a curated value or close the dialog and keep it. We don't validate. |
| Reset all data while a Drive backup worker is mid-flight | `BackupScheduler.enqueueUniqueWork(REPLACE)` cancels the in-flight worker and queues a new one with the post-reset (empty) snapshot. No data race because the use case completes its writes before enqueue. |
| Manage locations swipe + process death | `Job` cancelled on `onCleared`; in-memory `pendingDeletions` lost; DB row remains. On rebirth, `observeAll` re-emits, row is back. Acceptable per History's pattern. |
| Two rapid swipes on the same row | Second swipe is impossible — the row is hidden after the first because it's in `pendingDeletions`. |
| Settings opened with `driveEnabled=true` and then user resets all data | Drive section's switch stays at "on" (we don't touch driveEnabled in `ResetAllDataUseCase`). The post-reset wizard completes, the user lands on the empty Dashboard, and the next charge event will trigger a Drive upload of the new (single-event) snapshot. Drive tokens persist across the reset — DataStore key `driveEnabled` is preserved. |
| User has zero custom locations and taps "Manage custom locations" | Screen shows the empty-state TextView. Swipe does nothing because there's nothing to swipe. |

## 13. File inventory

**Create:**

- `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCase.kt`
- `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCase.kt`
- `app/src/main/java/org/spsl/evtracker/core/model/ManageLocationsUiState.kt`
- `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsAdapter.kt`
- `app/src/main/res/layout/item_custom_location.xml`
- Tests: `ResetActiveCarDataUseCaseTest`, `ResetAllDataUseCaseTest`, `ManageLocationsViewModelTest`, `ManageLocationsFragmentTest`

**Modify:**

- `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt` (extend state + events)
- `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt` (+ `theme: Flow<String>`)
- `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt` (+ 6 setters)
- `app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventWriter.kt` (+ `deleteForCar`, `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt` (+ `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/domain/repository/CarWriter.kt` (+ `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` (interface impls + atomic combined writer)
- `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt` (+ `deleteForCar`, `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt` (+ `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt` (+ `deleteAll`)
- `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt` (+ `deleteForCar`)
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` (wire all rows)
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt` (extend init + add F1 actions)
- `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt`
- `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModel.kt`
- `app/src/main/res/layout/fragment_settings.xml` (replace 6 disabled placeholders)
- `app/src/main/res/layout/fragment_manage_locations.xml` (real RecyclerView + empty state)
- `app/src/main/res/navigation/nav_graph.xml` (+ 2 actions on settingsFragment)
- `app/src/main/res/values/strings.xml` (~30 new keys)
- `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` (new methods on `FakeChargeEventWriter`, `FakeLocationWriter`, `FakeCarWriter`, `FakeSettingsReader`/`Writer`)
- `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt` (extended)

**Delete:** none.

**Migration:** none. No Room schema bump — only one new query (`@Query DELETE FROM charge_events WHERE carId = :carId`) which doesn't change the schema.

## 14. Acceptance gates

JVM tests: `:app:testDebugUnitTest` ⇒ all green, count ≈ 182.
Instrumented compile: `:app:assembleDebugAndroidTest` ⇒ green.
Manual smoke (on device): theme picker flips theme without restart; CSV row triggers the system share sheet; reset-all routes through wizard back to an empty Dashboard.

## 15. Out of scope for F1, queued for F2

- ChartsFragment + ChartsViewModel + chart rendering (line / bar / pie via MPAndroidChart)
- Charts period selector and multi-currency banner reuse
- Charts no-data and AC/DC empty states

F2 will be brainstormed and specced separately after F1 ships.
