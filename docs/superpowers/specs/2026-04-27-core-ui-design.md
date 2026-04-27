# Sub-project D — Core UI Design

> Sub-project of the EV Efficiency Tracker build. Foundation (A), data layer (B), and domain core (C) are merged on `main`. This spec defines the four screens that turn the app from a domain-only library into a usable product: **Dashboard**, **ChargeEdit**, **Cars**, **History**.

## 1. Goal & Scope

**Goal:** ship the four core screens fully wired through Hilt, the existing narrow repository interfaces, and the existing domain use cases, so a user can add a car, log charges, see stats, and edit/delete history.

**Out of scope — C-layer API contracts are frozen:** D does not change any signature in `domain/usecase/`, `domain/service/`, `domain/repository/`, or `core/model/` that already shipped in sub-project C. The only domain-layer additions D introduces are the three new car-management use cases (§6.1) and a brand-new `CarWriter` interface (which doesn't yet exist). Everything else in `domain/` is consumed as-is.

**In scope:**
- `DashboardFragment` + `DashboardViewModel`
- `ChargeEditFragment` + `ChargeEditViewModel` (Create + Edit modes)
- `CarsFragment` + `CarsViewModel` (full CRUD with active-car indicator)
- `HistoryFragment` + `HistoryViewModel` (list + swipe-to-delete with 5s undo)
- BottomNavigationView wiring in `MainActivity` + `nav_graph.xml` updates
- Three new use cases (`AddCarUseCase`, `RenameCarUseCase`, `DeleteCarUseCase`)
- New UI-state models in `core/model/`
- New common helpers in `ui/common/` (money/date formatting)
- JVM ViewModel tests for all four screens
- Small instrumented suite (`DashboardFragmentTest`, `ChargeEditFragmentTest`)

**Out of scope (kept as placeholder fragments; landed in E or F):**
- Charts UI (F)
- Settings UI — preferences, theme, Drive switch, reset flows (F)
- ManageLocations UI (F)
- CSV export trigger (F)
- Drive auth (`DriveAuthManager`) and the real `DriveBackupWorker` / `BackupRepository` Drive client (E)

The placeholder Fragments at `ui/charts/`, `ui/settings/`, `ui/locations/` continue to render their existing TextView until F lands.

## 2. Architecture & module additions

### 2.1 Source tree

```
app/src/main/java/org/spsl/evtracker/
  ui/dashboard/
    DashboardFragment.kt           (rewrite)
    DashboardViewModel.kt          (rewrite)
    DashboardCarSpinnerAdapter.kt  (new)
  ui/chargeedit/
    ChargeEditFragment.kt          (rewrite)
    ChargeEditViewModel.kt         (rewrite)
    LocationChipBinder.kt          (new — pure helper, JVM-testable)
  ui/cars/
    CarsFragment.kt                (rewrite)
    CarsViewModel.kt               (rewrite)
    CarsAdapter.kt                 (new — ListAdapter+DiffUtil)
    CarEditDialog.kt               (new — wraps MaterialAlertDialogBuilder)
  ui/history/
    HistoryFragment.kt             (rewrite)
    HistoryViewModel.kt            (rewrite)
    HistoryAdapter.kt              (new — ListAdapter+DiffUtil)
    SwipeToDeleteCallback.kt       (new — ItemTouchHelper.SimpleCallback)
  ui/common/
    MoneyFormat.kt                 (new — pure helper)
    DateFormat.kt                  (new — pure helper)
    PeriodLabels.kt                (new — string-resource helper)
  core/model/
    CarFormState.kt                (new)
    ChargeEditUiState.kt           (new)
    HistoryUiState.kt              (new)
    CarsUiState.kt                 (new)
    DashboardScreenState.kt        (new — wraps the existing DashboardUiState)
  domain/usecase/
    AddCarUseCase.kt               (new)
    RenameCarUseCase.kt            (new)
    DeleteCarUseCase.kt            (new)
  domain/repository/
    CarWriter.kt                   (new — narrow writer interface; CarRepository will also implement it)
  data/local/dao/CarDao.kt         (modify — add rename(id, name) and deleteById(id) @Query methods)
  data/repository/CarRepository.kt (modify — implement CarWriter; add rename + deleteById methods)
  di/DomainModule.kt               (modify — add @Binds CarWriter ← CarRepository; new use cases use ctor injection so need no binding)

app/src/main/res/
  layout/
    activity_main.xml              (rewrite — add BottomNavigationView in CoordinatorLayout)
    fragment_dashboard.xml         (rewrite — toolbar, tabs, chips, cards, banner, FAB, empty-state)
    fragment_charge_edit.xml       (rewrite — full form)
    fragment_cars.xml              (rewrite — RecyclerView + FAB)
    fragment_history.xml           (rewrite — RecyclerView + filter chips)
    item_car.xml                   (new)
    item_charge_event.xml          (new)
    dialog_edit_car.xml            (new)
  menu/
    bottom_nav.xml                 (new — 4 items: dashboard, history, charts, settings)
    car_row_overflow.xml           (new — Set active / Edit / Delete)
  navigation/
    nav_graph.xml                  (modify — add actions and chargeEditFragment argument)
  values/
    strings.xml                    (modify — add ~30 strings)

app/src/test/java/org/spsl/evtracker/
  testing/
    Fakes.kt                       (extend — add FakeObserveDashboardStatsUseCase, FakeCarRepository writer methods if missing)
  ui/dashboard/DashboardViewModelTest.kt
  ui/chargeedit/ChargeEditViewModelTest.kt
  ui/cars/CarsViewModelTest.kt
  ui/history/HistoryViewModelTest.kt
  domain/usecase/AddCarUseCaseTest.kt
  domain/usecase/RenameCarUseCaseTest.kt
  domain/usecase/DeleteCarUseCaseTest.kt
  ui/common/MoneyFormatTest.kt        (sanity: currency code → symbol/locale)
  ui/common/DateFormatTest.kt         (sanity: epoch ms → display string)

app/src/androidTest/java/org/spsl/evtracker/
  ui/dashboard/DashboardFragmentTest.kt
  ui/chargeedit/ChargeEditFragmentTest.kt
```

### 2.2 Dependencies

No new Gradle dependencies. The bottom navigation comes from `com.google.android.material:material` (already present), and Material dialogs/dropdowns/date/time pickers are also in that AAR. `DiffUtil` and `ListAdapter` are part of `androidx.recyclerview:recyclerview` (transitively pulled via `material`).

> **Build note (pre-D cleanup):** the project now uses a Gradle version catalog at `gradle/libs.versions.toml`. All dependency references in `app/build.gradle.kts` go through `libs.*` aliases. If D ever needs to add a new dependency (it should not), the version belongs in the TOML, not inline.
>
> JitPack is now scoped via `exclusiveContent { … filter { includeGroup("com.github.PhilJay") } }` so a JitPack outage does not gate the whole build. Only `MPAndroidChart` is resolved through it.

### 2.3 Architecture pattern

Per the project's existing convention:

- ViewModels expose **`val uiState: StateFlow<XxxUiState>`** built via `combine`/`flatMapLatest` over the domain interfaces, plus **`val events: SharedFlow<XxxEvent>`** for one-shot effects (Snackbar, navigate, dialog). `SharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`.
- Fragments collect inside `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`. Two coroutines per Fragment: one for state, one for events.
- ViewModels never reference `android.*` (except `SavedStateHandle` and `androidx.lifecycle.ViewModel`).
- Fragments never reference `Dao`, `DataStore`, or any `data/*` class. Fragments call ViewModel methods only.
- All cross-source workflows (add car, rename car, delete car, save charge, delete charge) go through use cases, not directly through repositories.

## 3. Navigation

### 3.1 BottomNavigationView

`menu/bottom_nav.xml` — 4 items, ids:
- `dashboardFragment` — title "Dashboard", icon `ic_dashboard_24`
- `historyFragment` — "History", icon `ic_history_24`
- `chartsFragment` — "Charts", icon `ic_charts_24`
- `settingsFragment` — "Settings", icon `ic_settings_24`

> Vector drawables for the four icons can be the standard Material Symbols set (`@drawable/ic_dashboard_24`, etc.). If they don't already exist in the project they will be added as part of this sub-project, sourced from the Android Studio Asset Studio's Material Symbols catalog.

### 3.2 MainActivity wiring

`MainActivity.onCreate` already runs the Wizard gate inside a `lifecycleScope.launch { ... }` coroutine that is splash-gated by an `isLoading` `MutableStateFlow<Boolean>` (the runBlocking startup pattern was removed in the pre-D cleanup commit). D adds the BottomNavigationView wiring synchronously in `onCreate` after `setContentView` — the listener can be attached before `navController.graph` is set; it simply won't fire until destinations change.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splash = installSplashScreen()
    splash.setKeepOnScreenCondition { isLoading.value }
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val navHost = supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHost.navController
    val graph = navController.navInflater.inflate(R.navigation.nav_graph)

    // Bottom nav setup — D adds this. ViewBinding lookup since activity_main now has a CoordinatorLayout root.
    val binding = ActivityMainBinding.bind(findViewById(R.id.activity_main_root))
    binding.bottomNav.setupWithNavController(navController)
    val hideOn = setOf(
        R.id.wizardFragment,
        R.id.chargeEditFragment,
        R.id.carsFragment,
        R.id.manageLocationsFragment
    )
    navController.addOnDestinationChangedListener { _, dest, _ ->
        binding.bottomNav.isVisible = dest.id !in hideOn
    }

    // Existing splash-gated graph-setting coroutine — unchanged.
    lifecycleScope.launch {
        val complete = settingsRepository.setupComplete.first()
        if (!complete) graph.setStartDestination(R.id.wizardFragment)
        navController.graph = graph
        isLoading.value = false
    }
}
```

> The `activity_main` root LinearLayout becomes a `CoordinatorLayout` with `android:id="@+id/activity_main_root"` so ViewBinding's generated `ActivityMainBinding` provides typed access without `findViewById` for the bottom nav.

### 3.3 nav_graph.xml updates

Add to `chargeEditFragment`:

```xml
<argument
    android:name="eventId"
    app:argType="integer"
    android:defaultValue="-1"/>
```

Add actions:

- `dashboardFragment` → `chargeEditFragment` (id `action_dashboard_to_chargeEdit`)
- `dashboardFragment` → `carsFragment` (id `action_dashboard_to_cars`)
- `historyFragment` → `chargeEditFragment` (id `action_history_to_chargeEdit`)

Top-level destinations stay siblings — `BottomNavigationView` + `setupWithNavController` handles their selection without explicit actions.

### 3.4 Argument flow for ChargeEdit

- Dashboard FAB → `findNavController().navigate(R.id.action_dashboard_to_chargeEdit)` (no bundle ⇒ default `eventId = -1` ⇒ Create mode).
- History row tap → `findNavController().navigate(R.id.action_history_to_chargeEdit, bundleOf("eventId" to event.id))` ⇒ Edit mode.
- `ChargeEditViewModel` reads `savedStateHandle.get<Int>("eventId") ?: -1`. `-1` ⇒ `Mode.Create`; else `Mode.Edit(eventId)`.

## 4. DashboardViewModel + Fragment

### 4.1 State models (new `core/model/DashboardScreenState.kt`)

```kotlin
data class DashboardScreenState(
    val cars: List<CarEntity> = emptyList(),
    val activeCarId: Int = -1,
    val period: DashboardPeriod = DashboardPeriod.Last30Days,
    val filter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val primaryMetric: String = "km_per_kwh",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val dashboard: DashboardUiState = DashboardUiState()
)

sealed class DashboardEvent {
    object NavigateToChargeEdit : DashboardEvent()
    object NavigateToCars : DashboardEvent()
    object NavigateToManageCars : DashboardEvent()
}
```

> `DashboardUiState`, `EmptyState`, `ChargeTypeFilter`, `DashboardPeriod`, and `Stats` already exist in `core/model/` from sub-project C. `Stats.totalCost`, `Stats.currency`, `Stats.costPerKm`, `Stats.costPer100Km`, and `Stats.mixedCurrency` are already populated by `StatsCalculator`. `DashboardPeriod.Custom(fromMillis: Long, toMillis: Long)` already carries the date range — D does not introduce a separate `customRange` field; the picker writes directly into a new `DashboardPeriod.Custom(from, to)` instance.

### 4.2 ViewModel responsibilities

`DashboardViewModel` injects:

- `ObserveDashboardStatsUseCase` (Flow-driven, already exists; signature: `fun observe(period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState>`)
- `CarReader`
- `SettingsReader`
- `SettingsWriter`

It owns two private `MutableStateFlow`s for the user-driven facets:

```kotlin
private val period = MutableStateFlow<DashboardPeriod>(DashboardPeriod.Last30Days)
private val filter = MutableStateFlow(ChargeTypeFilter.ALL)
```

The use case is invoked as a Flow inside `flatMapLatest`, re-subscribed whenever period or filter changes. `activeCarId` is **not** passed to the use case — the use case observes that itself from `SettingsReader`. The Dashboard VM's other inputs (cars, primaryMetric, distanceUnit, currency, activeCarId) are combined with the use case's emissions to build `DashboardScreenState`:

```kotlin
private val dashboardFlow: Flow<DashboardUiState> =
    combine(period, filter) { p, f -> p to f }
        .flatMapLatest { (p, f) -> observeDashboardStats.observe(p, f) }

private data class DashboardInputs(
    val cars: List<CarEntity>,
    val activeCarId: Int,
    val primaryMetric: String,
    val distanceUnit: String,
    val currency: String
)

private val inputsFlow: Flow<DashboardInputs> =
    combine(
        carReader.observeAll(),
        settingsReader.activeCarId,
        settingsReader.primaryMetric,
        settingsReader.distanceUnit,
        settingsReader.currency
    ) { cars, active, metric, unit, ccy -> DashboardInputs(cars, active, metric, unit, ccy) }

val uiState: StateFlow<DashboardScreenState> =
    combine(inputsFlow, dashboardFlow, period, filter) { inputs, dashboard, p, f ->
        DashboardScreenState(
            cars = inputs.cars,
            activeCarId = inputs.activeCarId,
            period = p,
            filter = f,
            primaryMetric = inputs.primaryMetric,
            distanceUnit = inputs.distanceUnit,
            currency = inputs.currency,
            dashboard = dashboard
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardScreenState())
```

> The `combine(inputsFlow, dashboardFlow, period, filter) { ... }` is a 4-arg `combine` — within the stdlib's 5-arg ceiling. Including `period` and `filter` in the outer combine ensures the screen state's `period`/`filter` fields stay synchronized with the use case's emission.

`events: SharedFlow<DashboardEvent>` for one-shot navigations (`MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`).

ViewModel functions:

- `selectPeriod(period: DashboardPeriod)` — updates internal `period` flow. For non-`Custom` variants only.
- `selectCustomRange(from: Long, to: Long)` — sets `period.value = DashboardPeriod.Custom(from, to)`. There is no separate `customRange` state; the picker writes directly into the sealed-class variant.
- `selectFilter(filter: ChargeTypeFilter)` — updates internal flow.
- `selectCar(carId: Int)` — calls `settingsWriter.setActiveCarId`.
- `onFabClick()` — emits `NavigateToChargeEdit` if `activeCarId != -1`; otherwise no-op (the FAB UI is visually disabled in this state).
- `onAddCarCtaClick()` / `onLogChargeCtaClick()` / `onManageCarsClick()` — emit the corresponding event.

### 4.3 Fragment layout (`fragment_dashboard.xml`)

```
CoordinatorLayout
├── AppBarLayout
│   └── MaterialToolbar (title="EV Tracker", end-anchored Spinner)
├── NestedScrollView (visible when state has cars and not NoCar)
│   ├── TabLayout (5 tabs: Since previous · 7d · 30d · Year · Custom)
│   ├── ChipGroup (single-select: All · AC · DC)
│   ├── MaterialCardView (PRIMARY metric — large, ~120dp tall)
│   ├── LinearLayout horizontal
│   │   ├── MaterialCardView (secondary 1)
│   │   └── MaterialCardView (secondary 2)
│   ├── MaterialCardView (cost summary; visibility="gone" when stats.totalCost==null OR mixedCurrency)
│   └── TextView banner ("Multi-currency period — cost stats hidden"; visibility="gone" by default)
├── LinearLayout empty-state container (visibility toggled by VM state)
│   ├── ImageView icon
│   ├── TextView headline ("Add a car to get started" or "Log your first charge to see stats here")
│   └── MaterialButton CTA
└── FloatingActionButton (anchor=AppBarLayout|bottom)
```

### 4.4 Rendering rules

| State | Visible elements |
|-------|------------------|
| `dashboard.emptyState == NoCar` | Empty-state container only (with "Add a car" CTA). Toolbar visible but Spinner hidden. FAB disabled. |
| `dashboard.emptyState == NoEvents` | Empty-state container only ("Log your first charge"). Toolbar with car spinner. FAB enabled. |
| Stats present, normal | Full scrollview. Cost card visible iff `stats.totalCost != null && !dashboard.showMultiCurrencyBanner`. Banner hidden. |
| `dashboard.showMultiCurrencyBanner == true` | Scrollview visible; **banner shown**, cost card hidden. |

> The banner signal is owned exclusively by `DashboardUiState.showMultiCurrencyBanner`, populated by `ObserveDashboardStatsUseCase.buildUiState` from `stats.mixedCurrency`. Dashboard rendering reads the boolean from the use case's UI state, never `stats.mixedCurrency` directly. This keeps the banner contract single-sourced in the domain layer.

The "primary" metric card is determined by `state.primaryMetric`:

| `primaryMetric` | Primary card shows |
|---|---|
| `"km_per_kwh"` | `stats.avgKmPerKwh` with label "km/kWh" |
| `"kwh_per_100km"` | `stats.avgKwhPer100Km` with label "kWh/100km" |
| `"mi_per_kwh"` | `stats.avgMiPerKwh` with label "mi/kWh" |

Secondary cards show the other two efficiency metrics. The primary card has a tinted background (`?attr/colorPrimaryContainer`); secondary cards use `?attr/colorSurfaceVariant`.

When efficiency value is `null` (single event in period): card body shows "—" and a small subtitle "Need 2+ charges".

Cost card layout: total cost (formatted via `MoneyFormat`), separator, "cost/km" + "cost/100km" rows. Shown only when `stats.costPerKm != null`. The currency symbol comes from `stats.currency` (already non-null when costPerKm is non-null).

### 4.5 Period & date handling

- TabLayout's tab indices map to `DashboardPeriod` variants: 0→`SincePreviousCharge`, 1→`Last7Days`, 2→`Last30Days`, 3→`Year`, 4→`Custom(...)`.
- Default selected tab = `Last30Days` (index 2).
- Tapping "Custom" tab opens `MaterialDatePicker.Builder.dateRangePicker()`. On positive button, the Fragment calls `viewModel.selectCustomRange(from, to)` — which writes a fully-formed `DashboardPeriod.Custom(from, to)` into the period flow. If the user dismisses without picking, the previous period stays selected (Fragment reverts the tab visually); the period flow is not touched, so the use case never sees a "custom-without-range" state.

### 4.6 JVM tests (`DashboardViewModelTest.kt`)

| Test | Scenario | Assertion |
|------|----------|-----------|
| `noCar_emitsNoCarEmptyState` | `CarReader` returns empty list | `uiState.value.dashboard.emptyState == EmptyState.NoCar` |
| `hasCarButNoEvents_emitsNoEventsEmptyState` | 1 car, fake use case returns `DashboardUiState(emptyState=NoEvents)` | matches |
| `eventsLoaded_propagatesStatsFromUseCase` | use case emits `DashboardUiState(stats=…)` | `uiState.value.dashboard.stats` equals expected |
| `selectPeriod_invokesUseCaseWithNewPeriod` | call `selectPeriod(Last7Days)` | fake use case records the new period parameter |
| `selectFilter_invokesUseCaseWithNewFilter` | call `selectFilter(DC)` | fake use case records the new filter |
| `selectCar_writesToSettingsWriter` | call `selectCar(7)` | fake `SettingsWriter.setActiveCarId` recorded `7` |
| `customRange_emitsCustomPeriodToUseCase` | call `selectCustomRange(t1, t2)` | use case parameter is `DashboardPeriod.Custom(t1, t2)` |
| `multiCurrency_propagatesShowBanner` | use case emits `DashboardUiState(stats=…, showMultiCurrencyBanner=true)` | `uiState.value.dashboard.showMultiCurrencyBanner == true` |
| `onFabClick_emitsNavigateEvent_whenCarActive` | activeCarId=1 | event is `NavigateToChargeEdit` |
| `onFabClick_emitsNothing_whenNoCar` | activeCarId=-1 | no event emitted (test via `events.replayCache.isEmpty()` after `runCurrent()`) |

## 5. ChargeEditViewModel + Fragment

### 5.1 State model (new `core/model/ChargeEditUiState.kt`)

```kotlin
data class ChargeEditUiState(
    val mode: Mode = Mode.Create,
    val carId: Int = -1,
    val eventDateMillis: Long = System.currentTimeMillis(),
    val odometer: String = "",
    val kwh: String = "",
    val chargeType: String = "AC",
    val location: String = "",
    val locationChips: LocationChips = LocationChips(),
    val costExpanded: Boolean = false,
    val costMode: CostMode = CostMode.TOTAL,
    val costValue: String = "",
    val note: String = "",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val odometerError: Int? = null,    // string-resource id
    val kwhError: Int? = null,
    val saving: Boolean = false
) {
    sealed class Mode {
        object Create : Mode()
        data class Edit(val eventId: Int) : Mode()
    }
}

data class LocationChips(
    val fixed: List<String> = listOf("Home", "Work", "Public"),
    val custom: List<String> = emptyList()
)

sealed class ChargeEditEvent {
    object SavedAndExit : ChargeEditEvent()
}
```

### 5.2 ViewModel responsibilities

Injects: `SaveChargeEventUseCase`, `LocationReader`, `ChargeEventQueries`, `SettingsReader`, `CostParser`, `SavedStateHandle`. `UnitConverter` is a Kotlin `object` and is called statically (e.g. `UnitConverter.milesToKm(value)`); it is **not** a Hilt-injected dependency.

Initialization:

1. Read `eventId` from `SavedStateHandle`. If `-1` → `Mode.Create`; else `Mode.Edit(eventId)`.
2. Read `settingsReader.activeCarId.first()`, `distanceUnit.first()`, `currency.first()`.
3. If Create: `_uiState.value = ChargeEditUiState(mode=Create, carId=activeCarId, distanceUnit=…, currency=…)`.
4. If Edit: `chargeEventQueries.getById(eventId)` (suspend). Convert `event.odometerKm` to display unit. Pre-fill all fields. Set `costExpanded = event.costTotal != null`. Choose `costMode = TOTAL` and `costValue = event.costTotal.toString()` when present.
5. Launch a coroutine that collects `locationReader.observeTop5()` and updates `state.locationChips.custom`. (Initial empty list is fine.)

ViewModel functions: `setEventDate`, `setOdometer`, `setKwh`, `setChargeType`, `selectLocationChip(label)` (sets `location` to the chip text), `setLocation`, `toggleCostExpanded`, `setCostMode`, `setCostValue`, `setNote`, `save()`.

`save()` algorithm:

1. Read current state. Set `state.copy(odometerError=null, kwhError=null, saving=true)`.
2. Parse odometer:
   - Trim. If blank → set `odometerError = R.string.error_odometer_required`. Stop, clear `saving`.
   - Parse to `Double` via `toDoubleOrNull()`. If null or `<=0` → same error.
   - If `state.distanceUnit == "miles"`, convert via `UnitConverter.milesToKm`.
3. Parse kWh:
   - Same blank/non-numeric/`<=0` rules → `kwhError = R.string.error_kwh_required`.
4. Build cost input:
   - If `state.costExpanded` and `state.costValue` parses to a `Double > 0`: build `CostInput(value = costValue.toDouble(), mode = state.costMode, currency = state.currency)`.
   - Else: `null`. (Per DESIGN: cost = 0 or blank ⇒ stored NULL. The use case + `CostParser` already enforce this; passing `null` here is the cleanest expression of intent.)
5. Build `SaveChargeEventInput`:
   ```kotlin
   SaveChargeEventInput(
       eventId = (state.mode as? Mode.Edit)?.eventId,
       carId = state.carId,
       eventDate = state.eventDateMillis,
       odometerKm = odoKm,
       kwhAdded = kwhValue,
       chargeType = state.chargeType,
       costInput = costInput,
       location = state.location.ifBlank { null },
       note = state.note
   )
   ```
6. Launch `viewModelScope.launch { … }`. Call `saveChargeEventUseCase(input)`.
7. On `Success` → emit `ChargeEditEvent.SavedAndExit`. The Fragment pops the back stack.
8. On `OdometerNotIncreasing` → set `odometerError = R.string.error_odometer_must_be_higher`, `saving=false`.

> The use case already (a) calls `LocationRepository.recordUsage(label)` when location is non-null, (b) calls `BackupScheduler.enqueueBackup()` unconditionally on success. No duplication needed in the ViewModel.

### 5.3 Fragment layout (`fragment_charge_edit.xml`)

```
CoordinatorLayout
└── NestedScrollView
    └── LinearLayout vertical, padding=16dp
        ├── MaterialToolbar (title="Add charge"/"Edit charge", end-icon=ic_check_24 → save())
        ├── MaterialButton "Date & time: <formatted>" (full-width outlined)
        ├── TextInputLayout odometer (suffix="km" or "mi")
        ├── TextInputLayout kWh (suffix="kWh")
        ├── MaterialButtonToggleGroup (singleSelection=true) — AC | DC
        ├── TextView "Location"
        ├── ChipGroup — fixed chips (Home, Work, Public) + custom chips + "+ Add" chip
        ├── TextInputLayout location (free-text)
        ├── MaterialButton "Cost (optional) ▼" — toggles cost section visibility
        ├── LinearLayout cost section (visibility="gone" by default)
        │   ├── MaterialButtonToggleGroup — Total | Per-kWh
        │   └── TextInputLayout cost (suffix=currency)
        └── TextInputLayout note (single line)
```

**Date+time button:** click → `MaterialDatePicker.Builder.datePicker()` then `MaterialTimePicker.Builder()` chained; combined epoch ms passed to `viewModel.setEventDate(ms)`. Default = `System.currentTimeMillis()` for Create mode; existing event date for Edit.

**Chip behavior:**
- Each fixed chip: `chip.setOnClickListener { viewModel.selectLocationChip(chip.text.toString()) }`.
- Custom chips rebuilt on `state.locationChips.custom` change; same handler.
- "+ Add" chip: `setOnClickListener { binding.locationField.requestFocus() }`.

**Save button:** in toolbar's end icon (`menu/charge_edit_menu.xml` with single check item) OR as a check `ImageButton`. Click → `viewModel.save()`.

### 5.4 JVM tests (`ChargeEditViewModelTest.kt`)

| Test | Scenario | Assertion |
|------|----------|-----------|
| `createMode_blankInitialState` | savedState eventId=-1, activeCarId=1, distanceUnit=km | `state.mode == Create`, `carId=1`, all inputs blank |
| `editMode_loadsExistingEventAndPreFills` | eventId=7, fake `getById(7)` returns event with odo=100, kwh=20, cost=5.0 | `state.odometer == "100"`, `state.kwh == "20"`, `state.costExpanded == true`, `state.costValue == "5.0"` |
| `editMode_milesUnit_odometerDisplayedInMiles` | eventId=7, event.odometerKm=100, distanceUnit=miles | `state.odometer ≈ "62.137"` (within ±0.001) |
| `save_blankOdometer_setsError` | save() with empty odometer | `state.odometerError == R.string.error_odometer_required`, use case not called |
| `save_blankKwh_setsError` | odometer=100, kwh blank | `state.kwhError == R.string.error_kwh_required` |
| `save_costZero_passesNullCostInput` | costExpanded=true, costValue="0" | use case input.costInput == null |
| `save_costBlank_passesNullCostInput` | costExpanded=true, costValue="" | costInput == null |
| `save_costTotalEntered_passesCostInputWithMode` | costValue="5.5", mode=TOTAL, currency=EUR | costInput == CostInput(5.5, TOTAL, "EUR") |
| `save_useCaseReturnsOdoNotIncreasing_setsError` | fake use case returns OdometerNotIncreasing | `state.odometerError == R.string.error_odometer_must_be_higher`, no event emitted |
| `save_success_emitsSavedAndExitEvent` | use case returns Success(99) | `events` has SavedAndExit |
| `save_milesInput_convertsToKmBeforeUseCaseCall` | distanceUnit=miles, odometer="62.137" | use case input.odometerKm ≈ 100.0 (±0.001) |
| `selectLocationChip_setsLocationField` | call selectLocationChip("Home") | `state.location == "Home"` |
| `customLocationsObserved` | LocationReader emits ["A","B"] | `state.locationChips.custom == ["A","B"]` |
| `editMode_carIdRetained` | event.carId=3 | `state.carId == 3` (not the activeCarId) |

## 6. CarsViewModel + Fragment + 3 use cases

### 6.1 New use cases

**AddCarUseCase** (`domain/usecase/AddCarUseCase.kt`)

```kotlin
class AddCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val carReader: CarReader,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(form: CarFormState): Result {
        if (form.name.isBlank()) return Result.NameBlank
        val entity = CarEntity(
            name = form.name.trim(),
            make = form.make.trim(),
            model = form.model.trim(),
            year = form.year.toIntOrNull(),
            batteryKwh = form.batteryKwh.toDoubleOrNull()
        )
        val rowId = carWriter.insert(entity)
        if (rowId <= 0L) return Result.PersistenceFailed
        val newId = rowId.toInt()
        if (settingsReader.activeCarId.first() == -1) {
            settingsWriter.setActiveCarId(newId)
        }
        backupScheduler.enqueueBackup()
        return Result.Success(newId)
    }
    sealed class Result {
        data class Success(val id: Int) : Result()
        object NameBlank : Result()
        object PersistenceFailed : Result()
    }
}
```

**RenameCarUseCase**

```kotlin
class RenameCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(carId: Int, newName: String): Result {
        if (newName.isBlank()) return Result.NameBlank
        carWriter.rename(carId, newName.trim())
        backupScheduler.enqueueBackup()
        return Result.Success
    }
    sealed class Result { object Success : Result(); object NameBlank : Result() }
}
```

**DeleteCarUseCase**

```kotlin
class DeleteCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val carReader: CarReader,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(carId: Int) {
        carWriter.deleteById(carId)         // cascade-delete handled by FK on charge_events
        if (settingsReader.activeCarId.first() == carId) {
            val remaining = carReader.observeAll().first()
            settingsWriter.setActiveCarId(remaining.firstOrNull()?.id ?: -1)
        }
        backupScheduler.enqueueBackup()
    }
}
```

> **Repository surface additions in D:**
>
> `CarWriter` is a new narrow interface in `domain/repository/`:
>
> ```kotlin
> interface CarWriter {
>     suspend fun insert(car: CarEntity): Long
>     suspend fun rename(carId: Int, newName: String)
>     suspend fun deleteById(carId: Int)
> }
> ```
>
> `CarRepository` (currently implements only `CarReader`) is extended to also implement `CarWriter`. The existing concrete `insert(car)`, `update(car)`, and `delete(car)` methods on `CarRepository` are kept (used internally and by tests). `rename` and `deleteById` are new. The `CarDao` adds two `@Query` methods:
>
> ```kotlin
> @Query("UPDATE cars SET name = :name WHERE id = :id")
> suspend fun rename(id: Int, name: String)
>
> @Query("DELETE FROM cars WHERE id = :id")
> suspend fun deleteById(id: Int): Int
> ```
>
> `CarRepository.rename(id, name)` and `CarRepository.deleteById(id)` simply forward to these DAO methods.

### 6.2 State models (new `core/model/CarsUiState.kt` and `CarFormState.kt`)

```kotlin
data class CarFormState(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val batteryKwh: String = ""
)

data class CarsUiState(
    val cars: List<CarRow> = emptyList(),
    val activeCarId: Int = -1
) {
    val empty: Boolean get() = cars.isEmpty()
    data class CarRow(val car: CarEntity, val isActive: Boolean)
}

sealed class CarsEvent {
    object ShowAddDialog : CarsEvent()
    data class ShowEditDialog(val car: CarEntity) : CarsEvent()
    data class ShowDeleteConfirm(val car: CarEntity) : CarsEvent()
    data class ShowError(val messageRes: Int) : CarsEvent()
}
```

### 6.3 ViewModel responsibilities

Injects: `CarReader`, `SettingsReader`, `SettingsWriter`, `AddCarUseCase`, `RenameCarUseCase`, `DeleteCarUseCase`.

```kotlin
val uiState: StateFlow<CarsUiState> =
    combine(carReader.observeAll(), settingsReader.activeCarId) { cars, activeId ->
        CarsUiState(
            cars = cars.map { CarRow(it, it.id == activeId) },
            activeCarId = activeId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarsUiState())
```

Functions:

- `onFabClick()` → emit `ShowAddDialog`.
- `onRowEditClick(car)` → emit `ShowEditDialog(car)`.
- `onRowDeleteClick(car)` → emit `ShowDeleteConfirm(car)`.
- `onRowSetActiveClick(carId)` → `viewModelScope.launch { settingsWriter.setActiveCarId(carId) }`.
- `submitAdd(form: CarFormState)` → call `AddCarUseCase`. On `NameBlank` emit `ShowError(R.string.error_car_name_required)`. On Success: nothing — the dialog closes itself.
- `submitRename(carId, name)` → call `RenameCarUseCase`. Same error handling.
- `confirmDelete(carId)` → call `DeleteCarUseCase`.

### 6.4 Fragment layout (`fragment_cars.xml`)

```
CoordinatorLayout
├── MaterialToolbar (title="Cars", navigationIcon=ic_arrow_back)
├── RecyclerView (item_car.xml)
└── FloatingActionButton (icon=ic_add)
```

**`item_car.xml`** layout: name (large), make+model+year (subtitle), "Active" chip when active, overflow `ImageButton` (`ic_more_vert`) opening `menu/car_row_overflow.xml` (Set active / Edit / Delete; "Set active" hidden if already active).

**Add/Edit dialog** (`CarEditDialog.kt`): wraps `MaterialAlertDialogBuilder` with `dialog_edit_car.xml` (5 `TextInputLayout`s: name, make, model, year, batteryKwh). Constructor params: `car: CarEntity?` (null = Add). On positive button click, calls back with `CarFormState`.

**Delete confirm dialog:** plain `MaterialAlertDialogBuilder.setTitle("Delete car?").setMessage("All charge events for this car will also be deleted.").setPositiveButton("Delete") { confirmDelete(carId) }.setNegativeButton("Cancel", null).show()`.

### 6.5 Tests

**`AddCarUseCaseTest.kt`:**

| Test | Scenario | Assertion |
|------|----------|-----------|
| `nameBlank_returnsError` | form.name="" | Result.NameBlank, no insert called |
| `firstCar_setsActiveCarId` | activeCarId=-1, insert returns 1L | settingsWriter.setActiveCarId called with 1 |
| `secondCar_doesNotChangeActive` | activeCarId=5, insert returns 8L | settingsWriter.setActiveCarId NOT called |
| `success_enqueuesBackup` | normal flow | scheduler.enqueueBackup called once |
| `nameTrimmed` | form.name=" Tesla " | inserted entity.name == "Tesla" |
| `numericFieldsParseLeniently` | year="abc", batteryKwh="" | inserted entity.year == null, batteryKwh == null |

**`RenameCarUseCaseTest.kt`:**

| Test | Scenario | Assertion |
|------|----------|-----------|
| `nameBlank_returnsError` | name="" | NameBlank, writer.rename not called |
| `success_renamesAndEnqueues` | normal | rename + enqueueBackup called |
| `nameTrimmed` | name=" New " | rename called with "New" |

**`DeleteCarUseCaseTest.kt`:**

| Test | Scenario | Assertion |
|------|----------|-----------|
| `deleteActiveCar_clearsToNextRemaining` | activeCarId=1, after delete observeCars returns [Car(id=2)] | setActiveCarId(2) |
| `deleteOnlyCar_clearsToMinusOne` | only car deleted, observeCars returns [] | setActiveCarId(-1) |
| `deleteInactiveCar_leavesActiveAlone` | activeCarId=5, deleting id=3 | setActiveCarId NOT called |
| `success_enqueuesBackup` | normal | scheduler.enqueueBackup called once |

**`CarsViewModelTest.kt`:**

| Test | Scenario | Assertion |
|------|----------|-----------|
| `noCars_emitsEmpty` | observeCars=[] | state.empty == true |
| `marksActiveCarInList` | observeCars=[Car(1), Car(2)], activeCarId=2 | rows[1].isActive == true, rows[0].isActive == false |
| `addNameBlank_emitsErrorEvent` | call submitAdd(CarFormState(name="")) | events has ShowError |
| `setActive_writesToSettings` | call onRowSetActiveClick(7) | settingsWriter.setActiveCarId == 7 |
| `submitAdd_success_doesNotEmitError` | normal | events.replayCache.isEmpty() (no error) |

## 7. HistoryViewModel + Fragment

### 7.1 State model (new `core/model/HistoryUiState.kt`)

```kotlin
data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val filter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val distanceUnit: String = "km",
    val activeCarId: Int = -1
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class HistoryRow(
    val event: ChargeEventEntity,
    val displayOdometer: Double,
    val showCost: Boolean,
    val isPendingDelete: Boolean
)

sealed class HistoryEvent {
    data class ShowUndoSnackbar(val eventId: Int) : HistoryEvent()
    data class NavigateToEdit(val eventId: Int) : HistoryEvent()
}
```

The adapter filters rows where `isPendingDelete == true` so they appear hidden; the data is still in `rows` for restoration.

### 7.2 ViewModel responsibilities

Injects: `ChargeEventQueries`, `DeleteChargeEventUseCase`, `SettingsReader`. `UnitConverter` is a Kotlin `object` and is called statically — it is **not** injected.

`pendingDeletes` is keyed by event id but stores `(ChargeEventEntity, Job)` so that the cancellable timer has the entity to pass to `DeleteChargeEventUseCase` (which takes a `ChargeEventEntity`, not an id):

```kotlin
private data class PendingDelete(val event: ChargeEventEntity, val job: Job)

private data class HistoryInputs(
    val activeCarId: Int,
    val distanceUnit: String,
    val filter: ChargeTypeFilter,
    val pendingDeletes: Map<Int, PendingDelete>
)

private val pendingDeletes = MutableStateFlow<Map<Int, PendingDelete>>(emptyMap())
private val filter = MutableStateFlow(ChargeTypeFilter.ALL)

val uiState: StateFlow<HistoryUiState> =
    combine(
        settingsReader.activeCarId,
        settingsReader.distanceUnit,
        filter,
        pendingDeletes
    ) { active, unit, f, pending -> HistoryInputs(active, unit, f, pending) }
    .flatMapLatest { inputs ->
        if (inputs.activeCarId == -1) {
            flowOf(HistoryUiState(activeCarId = -1, distanceUnit = inputs.distanceUnit, filter = inputs.filter))
        } else {
            chargeEventQueries.observeForCar(inputs.activeCarId).map { events ->
                val visibleEvents = events
                    .filter { applyFilter(it, inputs.filter) }
                    .sortedByDescending { it.eventDate }
                HistoryUiState(
                    rows = visibleEvents.map { e ->
                        HistoryRow(
                            event = e,
                            displayOdometer = if (inputs.distanceUnit == "miles") UnitConverter.kmToMiles(e.odometerKm) else e.odometerKm,
                            showCost = e.costTotal != null && e.currency != null,
                            isPendingDelete = e.id in inputs.pendingDeletes.keys
                        )
                    },
                    filter = inputs.filter, distanceUnit = inputs.distanceUnit, activeCarId = inputs.activeCarId
                )
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
```

Functions:

- `setFilter(f)` → `filter.value = f`.
- `onSwipeDelete(event: ChargeEventEntity)` — Fragment passes the entity from the row's `HistoryRow.event`:
  ```kotlin
  fun onSwipeDelete(event: ChargeEventEntity) {
      val id = event.id
      val job = viewModelScope.launch {
          delay(5_000)
          deleteChargeEventUseCase(event)              // takes ChargeEventEntity, not Int
          pendingDeletes.update { it - id }
      }
      pendingDeletes.update { it + (id to PendingDelete(event, job)) }
      _events.tryEmit(HistoryEvent.ShowUndoSnackbar(id))
  }
  ```
- `onUndoDelete(eventId: Int)`:
  ```kotlin
  pendingDeletes.value[eventId]?.job?.cancel()
  pendingDeletes.update { it - eventId }
  ```
- `onRowClick(eventId: Int)` → emit `NavigateToEdit(eventId)`.

`applyFilter(event, filter)`: ALL → true; AC → `event.chargeType == "AC"`; DC → `event.chargeType == "DC"`.

### 7.3 Fragment layout (`fragment_history.xml`)

```
CoordinatorLayout
├── MaterialToolbar (title="History")
├── ChipGroup (single-select: All · AC · DC)
├── RecyclerView (item_charge_event.xml; ItemTouchHelper attached)
└── TextView empty (visibility="gone" by default; shown when isEmpty)
```

**`item_charge_event.xml`** rows: date (formatted), odometer with unit suffix, kWh, AC/DC badge, location chip (if non-null), cost text in currency (if showCost), note one-line.

**Swipe-to-delete:** `SwipeToDeleteCallback` extends `ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)`. `onSwiped(viewHolder, direction)` reads the row from the adapter via `adapter.currentList[viewHolder.bindingAdapterPosition].event` and calls `viewModel.onSwipeDelete(event)` (passes `ChargeEventEntity`, not an id, because `DeleteChargeEventUseCase.invoke(event: ChargeEventEntity)`). The Fragment collects `ShowUndoSnackbar(eventId)` and shows `Snackbar.make(rootView, R.string.snackbar_charge_deleted, Snackbar.LENGTH_LONG).setAction(R.string.undo) { viewModel.onUndoDelete(eventId) }.show()`.

**Snackbar duration vs timer:** `LENGTH_LONG` ≈ 3.5s, timer = 5s. Acceptable: the timer is the authoritative window; the Snackbar simply disappears slightly earlier on most devices. Per DESIGN §6 the requirement is "5s undo window", which the timer enforces.

**Empty state:** TextView "No charges yet — tap + on Dashboard to log one" centered when `rows` is empty AND `pendingDeletes` is empty. (When all visible rows are pending-deleted, we still show the rows list to avoid flashing empty state mid-undo-window.)

### 7.4 JVM tests (`HistoryViewModelTest.kt`)

Uses `kotlinx-coroutines-test` `runTest` with `UnconfinedTestDispatcher` for collection and `StandardTestDispatcher` (or the `runTest`'s own dispatcher) for time-controlled `delay`.

| Test | Scenario | Assertion |
|------|----------|-----------|
| `noActiveCar_emitsEmpty` | activeCarId=-1 | `uiState.value.rows.isEmpty()` |
| `eventsLoadedAndSortedNewestFirst` | 3 events with dates 100, 200, 300 | rows order = [300, 200, 100] |
| `filterAc_filtersDcOut` | 2 AC + 1 DC; setFilter(AC) | rows.size == 2, all AC |
| `filterDc_filtersAcOut` | same setup; setFilter(DC) | rows.size == 1, all DC |
| `swipeDelete_addsPendingAndStartsTimer` | onSwipeDelete(event with id=7) | rows[i].isPendingDelete == true for id=7; deleteChargeEventUseCase NOT called yet |
| `swipeDelete_after5s_callsDeleteUseCase` | swipe + advanceTimeBy(5_001) | use case called with the entity (id=7) |
| `swipeDelete_undo_cancelsTimer` | swipe + onUndoDelete(7) + advanceTimeBy(10_000) | use case NOT called; rows[i].isPendingDelete == false |
| `swipeDelete_then_undo_then_swipeAgain_eventuallyDeletes` | sequence | use case eventually called once with the entity |
| `pendingRow_hiddenFromUiList` | swipe; advanceTimeBy(0) | adapter (test-side filter) excludes pending row |
| `milesUnit_displayOdometerConverted` | distanceUnit=miles, event.odometerKm=100 | row.displayOdometer ≈ 62.137 |
| `multipleConcurrentDeletes_eachTracked` | swipe two events; undo one | pendingDeletes contains the other; use case called once with the non-undone entity |
| `onRowClick_emitsNavigateEvent` | onRowClick(11) | events has NavigateToEdit(11) |
| `swipeDelete_emitsUndoSnackbarEvent` | swipe(event id=7) | events has ShowUndoSnackbar(7) |

## 8. Common helpers (`ui/common/`)

### 8.1 `MoneyFormat.kt`

```kotlin
object MoneyFormat {
    fun format(amount: Double, currencyCode: String): String {
        val nf = NumberFormat.getCurrencyInstance().apply {
            try { currency = Currency.getInstance(currencyCode) } catch (_: IllegalArgumentException) { /* keep default */ }
            maximumFractionDigits = 2
        }
        return nf.format(amount)
    }
}
```

### 8.2 `DateFormat.kt`

```kotlin
object DateFormat {
    private val pattern: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

    fun formatEpochMs(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        pattern.withZone(zone).format(Instant.ofEpochMilli(ms))
}
```

> The `zone` parameter defaults to `ZoneId.systemDefault()` for production callers, but the unit test passes `ZoneId.of("UTC")` so output is deterministic across CI machines and local timezones. Do not cache the formatter with a fixed zone — that closes off the test seam.

### 8.3 `PeriodLabels.kt`

`@StringRes`-returning helper that maps `DashboardPeriod` to a label resource (`R.string.period_since_previous`, `period_7d`, `period_30d`, `period_year`, `period_custom`). Used by both Dashboard and Charts (Charts re-uses in F).

### 8.4 Tests

`MoneyFormatTest.kt`: EUR + locale roundtrip; unknown currency code falls back gracefully.
`DateFormatTest.kt`: passes `ZoneId.of("UTC")` to `formatEpochMs(ms, zone)` and asserts the exact formatted string for a fixed epoch — guaranteed stable across timezones and CI machines.

## 9. Hilt wiring

- All three new use cases are `@Inject constructor(...)` — no `@Provides` or `@Binds` needed; Hilt finds them via constructor injection.
- All new ViewModels are `@HiltViewModel class XxxViewModel @Inject constructor(...) : ViewModel()`.
- **New binding in `DomainModule.kt`:** `@Binds @Singleton fun bindCarWriter(impl: CarRepository): CarWriter`. The existing `CarReader` binding stays as-is.
- `MainActivity` and all Fragments stay `@AndroidEntryPoint` (already true from A).

## 10. Strings & resources

`res/values/strings.xml` additions (representative; the implementer fills any gaps as TDD requires):

```
<!-- Dashboard -->
<string name="dashboard_title">EV Tracker</string>
<string name="empty_no_car_headline">Add a car to get started</string>
<string name="empty_no_car_cta">Add car</string>
<string name="empty_no_events_headline">Log your first charge to see stats here</string>
<string name="empty_no_events_cta">Log charge</string>
<string name="multi_currency_banner">Multi-currency period — cost stats hidden</string>
<string name="manage_cars">Manage cars…</string>

<!-- Period tabs -->
<string name="period_since_previous">Since previous</string>
<string name="period_7d">7d</string>
<string name="period_30d">30d</string>
<string name="period_year">Year</string>
<string name="period_custom">Custom</string>

<!-- Filter chips -->
<string name="filter_all">All</string>
<string name="filter_ac">AC</string>
<string name="filter_dc">DC</string>

<!-- Charge edit -->
<string name="charge_edit_create_title">Add charge</string>
<string name="charge_edit_edit_title">Edit charge</string>
<string name="hint_odometer_km">Odometer (km)</string>
<string name="hint_odometer_mi">Odometer (mi)</string>
<string name="hint_kwh">kWh added</string>
<string name="hint_location">Location</string>
<string name="hint_cost">Cost</string>
<string name="hint_note">Note (optional)</string>
<string name="cost_section_header">Cost (optional)</string>
<string name="cost_mode_total">Total</string>
<string name="cost_mode_per_kwh">Per kWh</string>
<string name="error_odometer_required">Enter a valid odometer reading</string>
<string name="error_odometer_must_be_higher">Odometer must be greater than the previous entry</string>
<string name="error_kwh_required">Enter a valid kWh value</string>
<string name="location_home">Home</string>
<string name="location_work">Work</string>
<string name="location_public">Public</string>
<string name="chip_add_location">+ Add</string>

<!-- Cars -->
<string name="cars_title">Cars</string>
<string name="car_active">Active</string>
<string name="car_action_set_active">Set active</string>
<string name="car_action_edit">Edit</string>
<string name="car_action_delete">Delete</string>
<string name="car_delete_title">Delete car?</string>
<string name="car_delete_message">All charge events for this car will also be deleted.</string>
<string name="car_form_name">Name</string>
<string name="car_form_make">Make</string>
<string name="car_form_model">Model</string>
<string name="car_form_year">Year</string>
<string name="car_form_battery_kwh">Battery (kWh)</string>
<string name="error_car_name_required">Name is required</string>

<!-- History -->
<string name="history_title">History</string>
<string name="history_empty">No charges yet — tap + on Dashboard to log one</string>
<string name="snackbar_charge_deleted">Charge deleted</string>
<string name="undo">Undo</string>

<!-- Bottom nav -->
<string name="nav_dashboard">Dashboard</string>
<string name="nav_history">History</string>
<string name="nav_charts">Charts</string>
<string name="nav_settings">Settings</string>
```

## 11. Edge cases summary

| Case | Handling |
|------|----------|
| Active car deleted | `DeleteCarUseCase` sets `activeCarId` to next remaining car or `-1`. Dashboard re-renders with `NoCar` empty state if `-1`. |
| FAB tapped with no cars | FAB visually disabled (alpha 0.5, no-op click) when `activeCarId == -1`. The empty-state CTA "Add a car" is the entry path. |
| Edit a charge whose car was deleted | Cannot reach: FK ON DELETE CASCADE removes the event; Flow re-emits the History list without it. |
| Rotation in ChargeEdit | `ChargeEditUiState` lives in ViewModel — survives. Date/time pickers re-shown only on user action; no extra state. |
| Rotation during undo window | `pendingDeletes` lives in ViewModel — timer continues, row stays hidden. Snackbar dismisses on rotation; user cannot tap Undo after rotating, but the 5s commit still fires. Acceptable. |
| Save tapped twice rapidly | `state.saving = true` disables the Save button until the use case returns. |
| Multi-currency banner | Single source of truth: `DashboardUiState.showMultiCurrencyBanner`, populated by C's `ObserveDashboardStatsUseCase` from `stats.mixedCurrency`. Dashboard renders the banner from the boolean — never the underlying `stats.mixedCurrency` flag — so the contract stays single-sourced. |
| Bottom nav visible on `chargeEdit`/`cars` | Hidden via `addOnDestinationChangedListener` (see §3.2). |
| Save with `costExpanded=true` and `costValue=""` | Treated as no cost (passes `null` `CostInput`). Use case + `CostParser` would handle it identically, but explicit early return keeps the contract clear. |
| Edit mode currency mismatch | If editing a USD event while preferences say EUR, the form shows `state.currency = event.currency ?: prefCurrency`. On save the original currency is preserved. *(Implementer note: pre-fill `state.currency` from the event when in Edit mode.)* |
| Custom period picker dismissed | Tab visually reverts to previously selected period; no state change. |

## 12. Acceptance criteria

- [ ] All four screens fully functional end-to-end with the existing domain layer
- [ ] BottomNavigationView wired in `activity_main.xml`; hides on `wizardFragment`, `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`
- [ ] `nav_graph.xml` has all required actions and `chargeEditFragment.eventId` argument
- [ ] Three new use cases (`AddCarUseCase`, `RenameCarUseCase`, `DeleteCarUseCase`) exist and are tested
- [ ] All new state models in `core/model/` (`DashboardScreenState`, `ChargeEditUiState`, `CarsUiState`, `HistoryUiState`, `CarFormState`)
- [ ] All new ViewModels expose `StateFlow<XxxUiState>` + `SharedFlow<XxxEvent>`
- [ ] All JVM tests pass: `:app:testDebugUnitTest`
- [ ] Instrumented tests compile clean: `:app:assembleDebugAndroidTest` succeeds (running requires an emulator)
- [ ] `assembleDebug` builds clean
- [ ] Wizard, Dashboard NoCar CTA, Dashboard NoEvents CTA, Cars Add/Rename/Delete, ChargeEdit Save, History committed-delete all enqueue backup at the right moments through the existing `BackupScheduler`
- [ ] No direct `Dao` or `DataStore` access in any Fragment or ViewModel — everything goes through the narrow domain interfaces or use cases
- [ ] No Compose; XML + ViewBinding throughout
- [ ] Charts/Settings/ManageLocations remain as their existing placeholder fragments
- [ ] Following each task: `:app:testDebugUnitTest` passes and `assembleDebug` builds

## 13. Test scope clarification

This sub-project lands these JVM unit tests:

- `DashboardViewModelTest`, `ChargeEditViewModelTest`, `CarsViewModelTest`, `HistoryViewModelTest`
- `AddCarUseCaseTest`, `RenameCarUseCaseTest`, `DeleteCarUseCaseTest`
- `MoneyFormatTest`, `DateFormatTest`

This sub-project lands these instrumented tests:

- `DashboardFragmentTest` — minimal coverage of FAB→ChargeEdit nav and the two empty states
- `ChargeEditFragmentTest` — minimal coverage of chip-fills-text-field and save-with-blank-odometer-shows-error

Larger Espresso suites from `TEST_PLAN.md` §4.2/§4.3/§4.4/§4.6 land in F's polish pass once Drive auth (E) and the remaining UI screens are in.
