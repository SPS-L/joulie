# Sub-project F2 — Charts Screen — Design Spec

**Goal**

Replace the placeholder `ChartsFragment` with the full Charts screen described in DESIGN.md F8 / §6: efficiency line chart (AC vs DC), monthly kWh bar chart, monthly cost bar chart, AC/DC pie, and location-distribution pie. All charts are read-only views built from the existing event data; no new persistence, no new domain mutations.

**Architecture in one sentence**

A new `ObserveChartsModelsUseCase` combines `ChargeEventQueries`, `SettingsReader`, `StatsCalculator` (extended with two new pure aggregators), and `DateRangeResolver` into a single `ChartsScreenState` that the Fragment renders into MPAndroidChart views inside a `ViewPager2` of five fixed tabs.

**Tech stack additions**

- No new Gradle dependencies. MPAndroidChart is already wired (`libs.mpandroidchart`).
- No new database tables, migrations, DAOs, DataStore keys, or backup-payload fields.
- DB version stays at 3.

---

## 1. Source-of-truth alignment

This spec implements:

- **DESIGN.md §2 F8** — *"Line (efficiency trend AC vs DC), Bar (monthly kWh), Bar (monthly cost), Pie (AC/DC split + location split)"*
- **DESIGN.md §5 Key ViewModels** — *`ChartsViewModel` owns "Chart models for the selected period built from the same stats/query rules used by the dashboard"*
- **DESIGN.md §6 Charts** — *"All charts use MPAndroidChart; support pinch-zoom and value markers"*, *"Bar chart: monthly cost (hidden if no cost data)"*
- **AGENT_INSTRUCTIONS.md §6.3 Charts** — *"chart models should come from the same stats/query rules as dashboard state"*, *"keep MPAndroidChart setup in UI, but keep aggregation logic outside the Fragment"*
- **TEST_PLAN.md §4.5 ChartsFragmentTest** — `tabSwitch_showsCorrectChart`, `noData_emptyState`

Multi-currency cost-hiding rule (DESIGN §8 / CLAUDE.md "Invariants") is honoured: when a period contains > 1 distinct `currency` value across costed events, the **Monthly cost** tab is replaced by a "Multi-currency period — cost stats hidden" banner identical to the dashboard string.

This spec does **not** modify any existing screen. Dashboard, History, Settings, etc. are untouched.

---

## 2. Scope

### In scope

| # | Item | Notes |
|---|------|-------|
| 1 | Five chart tabs in a `ViewPager2` + `TabLayout` | *Trend · Monthly kWh · Monthly cost · AC/DC split · Locations* |
| 2 | Period control above the tabs | `Last 6 mo · Last 12 mo · All time · Custom` |
| 3 | `ChartsViewModel` + `ChartsScreenState` + `ChartsEvent` | Mirrors D-era ViewModel pattern |
| 4 | `ObserveChartsModelsUseCase` | Domain orchestration, pure |
| 5 | `StatsCalculator` extensions | `computeAcDcSplit`, `computeLocationDistribution`, `computeEfficiencyTrend` |
| 6 | New core models | `ChartsUiState`, `ChartsScreenState`, `ChartsEvent`, `ChartsPeriod`, `EfficiencyPoint`, `EfficiencySeries`, `AcDcSplit`, `LocationSlice`, `EmptyState` (reused) |
| 7 | `ui/charts/ChartStyling.kt` | Pure object that configures axes, markers, animations, theme colours |
| 8 | `MarkerView` for tap-to-inspect | Single shared marker for line + bar charts |
| 9 | Three-tier empty-state ladder | NoCar / NoEvents / NoEventsForPeriod (tab-internal) |
| 10 | Multi-currency banner over the tabs | Hides only the *Monthly cost* tab; other tabs render normally |
| 11 | JVM unit tests | New aggregators, new use case, new ViewModel — counts called out in §11 |
| 12 | Espresso instrumented tests | `tabSwitch_showsCorrectChart`, `noData_emptyState` (TEST_PLAN §4.5) |

### Explicitly out of scope (YAGNI)

- All / AC / DC filter chips on the Charts screen. The line chart already separates AC/DC via two series; the pies are deliberately whole-period splits. Adding filter chips would either contradict the AC-vs-DC split tab (filtering DC away) or duplicate existing affordances. Defer.
- Tap-on-bar drill-into-History navigation.
- Comparison overlays ("this car vs all cars").
- Animated transitions between periods.
- CSV export of chart data (CSV already exports events; users can re-aggregate).
- Saved/last-selected period across app restarts. The screen always opens at the default period (*Last 12 mo*) — preventing a "the bars all disappeared" surprise after the user returns months later.
- Custom car-spinner inside Charts. The active car is read from `SettingsReader.activeCarId`; the user switches cars on the Dashboard.
- New DAO methods. `ChargeEventQueries.observeForCar(carId)` already streams the full per-car list and the use case filters in memory — same pattern Dashboard uses today.

---

## 3. Architecture

### 3.1 Layer mapping

```
UI         ChartsFragment (binding + MPAndroidChart wiring)
           ChartsViewModel (StateFlow<ChartsScreenState> + SharedFlow<ChartsEvent>)
           ui/charts/ChartStyling.kt (axes, colors, markers — pure helpers)
           ui/charts/ChartsMarkerView.kt (XML + class extending MarkerView)
Core       ChartsUiState · ChartsScreenState · ChartsEvent · ChartsPeriod
           EfficiencyPoint · EfficiencySeries · AcDcSplit · LocationSlice
Domain     ObserveChartsModelsUseCase
           StatsCalculator extensions (3 new pure functions)
           DateRangeResolver — already supports `Year` and `Custom`; we add a
             second resolver method `resolveCharts(period: ChartsPeriod): DateRange`
             rather than overloading DashboardPeriod
Data       (none — read path only)
```

No new repositories, no DI changes other than the new use case auto-injecting via constructor.

### 3.2 Why not reuse `DashboardPeriod`?

`DashboardPeriod` contains `SincePreviousCharge`, `Last7Days`, `Last30Days`, `Year`, `Custom`. The Charts screen needs `Last6Months`, `Last12Months`, `AllTime`, `Custom`. Sharing the type would either force Charts to expose nonsensical options (a 7-day monthly bar chart) or create an enum where some values are valid in Dashboard, some in Charts, and some in both. A separate `ChartsPeriod` sealed class keeps each screen's state space explicit and unsurprising.

`DateRangeResolver` is extended (one new method, eight new lines) rather than duplicated, because the resolution of a date range *given a period* is the same conceptual operation.

### 3.3 New file layout

```
core/model/
  ChartsUiState.kt          — sealed/data classes for the five chart payloads + EmptyState
  ChartsScreenState.kt      — top-level Fragment-render state + ChartsEvent
  ChartsPeriod.kt           — sealed class: Last6Months · Last12Months · AllTime · Custom
  EfficiencyPoint.kt        — (eventTimeMillis: Long, kmPerKwh: Double)
  EfficiencySeries.kt       — (acPoints: List<EfficiencyPoint>, dcPoints: List<EfficiencyPoint>)
  AcDcSplit.kt              — (acCount: Int, dcCount: Int, acKwh: Double, dcKwh: Double)
  LocationSlice.kt          — (label: String, count: Int)

domain/usecase/
  ObserveChartsModelsUseCase.kt
  NowProvider.kt            — fun interface { fun nowMillis(): Long } — clock seam (see §9, §11.2)

domain/service/
  StatsCalculator.kt        — extended (3 new functions)
  DateRangeResolver.kt      — extended (1 new method)

ui/charts/
  ChartsFragment.kt         — replaced (was placeholder)
  ChartsViewModel.kt        — replaced (was empty)
  ChartsPagerAdapter.kt     — FragmentStateAdapter holding 5 tab fragments
  ChartsTabFragment.kt      — sealed-tagged tab Fragments (TrendTab · MonthlyKwhTab · MonthlyCostTab · AcDcTab · LocationsTab)
  ChartStyling.kt           — pure helpers (axis config, color resolution, formatter)
  ChartsMarkerView.kt       — MPAndroidChart MarkerView impl

res/layout/
  fragment_charts.xml       — replaced — toolbar-less host (period chips + tab + ViewPager2)
  fragment_charts_tab.xml   — generic tab host (chart container + empty-state TextView)
  view_chart_marker.xml     — MarkerView layout (date label + value label)

res/values/
  strings.xml               — new keys (~25)
  colors.xml                — `chart_ac_fallback`, `chart_dc_fallback` (literal blue/orange used only when theme attrs are missing — safety net)

di/
  AggregationDispatcher.kt  — qualifier annotation (§9)
  DispatcherModule.kt       — Hilt @Provides for @AggregationDispatcher CoroutineContext + NowProvider (§9)
  (AppModule.kt, DomainModule.kt, etc. — unchanged)
```

`ChartsTabFragment` is a single class with a `TabKind` arg; the five tabs differ only in which sub-state they bind to and which chart view they inflate. A single class avoids five near-identical files. Each tab still owns its own MPAndroidChart instance to make state restoration on rotation behave correctly.

**ViewModel scoping.** All five tab fragments share the parent `ChartsFragment`'s `ChartsViewModel` instance via `by viewModels({ requireParentFragment() })`. The use case must run exactly once for the screen and emit one state — five independent ViewModel instances would issue five overlapping DAO subscriptions and produce five identical re-aggregations on every event change. Hilt supports parent-fragment-scoped ViewModel injection out of the box; this is the same pattern used by the Material Component samples for `ViewPager2` + tabs.

### 3.4 Why a `ChartsTabFragment` per tab and not five Views in one Fragment

`ViewPager2` recycles its hosted Fragments and keeps the inactive ones detached from the view tree. Putting all five charts in one giant scrollable `NestedScrollView` would force MPAndroidChart to lay out (and animate) all charts at once, which is both heavier and visually noisier. The Fragment-per-tab structure also matches TEST_PLAN's `tabSwitch_showsCorrectChart` expectation: switching tabs is the affordance under test.

### 3.5 Data flow

```
SettingsReader.activeCarId ─┐
SettingsReader.distanceUnit ┤  (for trend Y-axis label + km↔mi rendering)
ChargeEventQueries          ├─►  ObserveChartsModelsUseCase  ─►  ChartsScreenState
  .observeForCar(activeId)  │       (filters by period, derives
period: MutableStateFlow ───┘        periodCurrency from event data,
  <ChartsPeriod>                     delegates aggregation to
                                     StatsCalculator helpers)
```

**Currency source for the cost tab.** The cost-tab currency label is derived from the *event data* in the selected period (via `MonthBucket.currency`), not from `SettingsReader.currency`. This matches the existing dashboard rule (`Stats.currency` is the resolved single currency of costed events in `StatsCalculator.computeStats` — see `StatsCalculator.kt:56` and `DashboardFragment.kt:206`/`:211`). If a user logged events in EUR last year and switched the preference to USD this morning, the cost bars must keep saying EUR — that is what actually happened — and the new USD setting only affects newly-entered events going forward. Reading `SettingsReader.currency` for chart labelling would silently relabel historical data.

Period changes are driven by the Fragment calling `viewModel.selectPeriod(...)`. The use case re-runs because `period` is one of the inputs into `combine(...).flatMapLatest`, exactly like Dashboard does today.

`flatMapLatest` is required so a fast tap through `Last 6 mo → All time` cancels the in-flight emission for the previous period rather than racing it to the UI thread.

---

## 4. Period control

Sealed class:

```kotlin
sealed class ChartsPeriod {
    object Last6Months  : ChartsPeriod()
    object Last12Months : ChartsPeriod()
    object AllTime      : ChartsPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : ChartsPeriod()
}
```

Default on first entry: **`Last12Months`**.

UI: a `ChipGroup` (single-select) with four chips. Tapping *Custom* opens a `MaterialDatePicker.Builder.dateRangePicker()`; the previously-selected chip is restored on cancel/dismiss (matches `DashboardFragment.showCustomDatePicker()`).

When the current period is `ChartsPeriod.Custom(from, to)`, the Fragment formats the chip's content description directly from those millis via `DateUtils.formatDateRange(context, from, to, FORMAT_SHOW_DATE)` — no extra UI-state field is needed because both the source millis and the formatter are deterministic given the period.

`DateRangeResolver.resolveCharts`:

```kotlin
fun resolveCharts(period: ChartsPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange =
    when (period) {
        ChartsPeriod.Last6Months  -> DateRange(nowMillis - 182L * MILLIS_PER_DAY, nowMillis)
        ChartsPeriod.Last12Months -> DateRange(nowMillis - 365L * MILLIS_PER_DAY, nowMillis)
        ChartsPeriod.AllTime      -> DateRange(0L, nowMillis)
        is ChartsPeriod.Custom    -> DateRange(period.fromMillis, period.toMillis)
    }
```

The 6-month / 12-month windows use rolling-day math (182 / 365 days) rather than calendar-month math because rolling windows match the user-visible chip labels exactly — *"Last 12 months"* should mean *12 × 30.4 days back from now*, not *Jan-of-last-year onwards*. The bar charts then bucket the window into calendar months via `StatsCalculator.computeMonthlyBuckets`, so users still see properly-aligned month labels.

`AllTime` uses `0L` as the lower bound, matching the existing `DashboardPeriod.SincePreviousCharge` resolver. Realistic event dates are post-2020; `0L` is safe.

---

## 5. UI state model

```kotlin
data class ChartsScreenState(
    val period: ChartsPeriod = ChartsPeriod.Last12Months,
    val distanceUnit: String = "km",       // "km" | "miles"; drives trend Y-axis label + value conversion
    val charts: ChartsUiState = ChartsUiState.Loading
)

sealed class ChartsUiState {
    object Loading : ChartsUiState()
    object NoCar : ChartsUiState()                // no rows in cars OR activeCarId == -1
    object NoEvents : ChartsUiState()             // active car has 0 events at all
    data class Loaded(
        val periodHasEvents: Boolean,             // false → all five tabs show "No data for this period"
        val mixedCurrency: Boolean,               // true → Monthly cost tab replaced with banner
        val periodCurrency: String?,              // single currency of costed events in the period; null when no costed events or mixedCurrency
        val trend: EfficiencySeries,              // possibly empty lists when not enough data
        val monthlyKwh: List<MonthBucket>,
        val monthlyCost: List<MonthBucket>,       // empty when mixedCurrency or no costed events
        val acDc: AcDcSplit,
        val locations: List<LocationSlice>
    ) : ChartsUiState()
}

sealed class ChartsEvent {
    object OpenCustomRangePicker : ChartsEvent()
    object NavigateToCars : ChartsEvent()         // from NoCar empty state CTA
    object NavigateToChargeEdit : ChartsEvent()   // from NoEvents empty state CTA
}
```

`ChartsEvent` uses the project-standard `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` so navigation events never re-fire on rotation (CLAUDE.md "ViewModel + event pattern").

---

## 6. Per-chart specifications

### 6.1 Trend (Line chart, MPAndroidChart `LineChart`)

**Data source.** All events in the period, sorted ascending by `eventDate`, partitioned by `chargeType`. Each event after the first contributes a point; the very first event of each series contributes none (no prior odometer to delta against).

**Per-point math.** For event `i` (after sorting **per series**): `kmPerKwh = (events[i].odometerKm - events[i-1].odometerKm) / events[i].kwhAdded`. Skip the point if the delta is `<= 0` (matches `StatsCalculator.computeStats`). Y value is converted at render-time: when `state.distanceUnit == "miles"` we render `mi/kWh` via `UnitConverter.kmPerKwhToMiPerKwh`. The model in `EfficiencyPoint` always stores km/kWh — display conversion stays at the UI boundary, identical to the rule that "Odometer is always stored in km" (CLAUDE.md Invariants).

> **Design note — partition before delta.** Computing the delta against the *previous event of the same charge type* (not the previous event globally) is what lets the AC and DC series each tell a coherent efficiency story. If we delta'd against the most recent event of any type, an AC point following a DC charge would be measuring "km driven since the last DC charge" rather than "km since the last AC charge". The trend lines then either smear or show artificial jumps; the choice to partition is therefore part of the spec, not an implementation detail.

**X axis.** Time, in epoch millis. Formatter renders `"d MMM"` for windows ≤ 12 months and `"MMM yy"` for `AllTime`.

**Y axis.** Primary metric value. Label suffix follows `state.distanceUnit`: `km/kWh` or `mi/kWh`. (We do **not** plot `kWh/100km` here — that metric inverts and would visually confuse "higher = better"; trend always uses *distance per kWh*.)

**Colors.** AC series = theme attr `?attr/colorPrimary`; DC series = `?attr/colorTertiary`. Resolved once via `ChartStyling.resolveSeriesColors(context)` and cached on the chart. Fallback constants `chart_ac_fallback = #1E88E5` (blue), `chart_dc_fallback = #FB8C00` (orange) — matches DESIGN §6 "AC blue, DC orange".

**Empty within tab.** When the period has events but no series has ≥ 2 points after delta-skipping: render the chart container `gone` and show `"Need at least 2 charges to plot a trend"` TextView.

**Pinch-zoom.** Enabled both axes. `setScaleEnabled(true)`, `setPinchZoom(true)`.

**Markers.** Tap-to-inspect via shared `ChartsMarkerView` showing `"<formatted date>\n<value> <unit>"`.

**Legend.** Top, AC and DC swatches with localized strings.

### 6.2 Monthly kWh (Bar chart, `BarChart`)

**Data source.** `StatsCalculator.computeMonthlyBuckets(periodEvents)` — already exists.

**Bars.** One bar per `MonthBucket`, value = `totalKwh`. Bar color = `?attr/colorPrimary`.

**X axis.** "MMM yy" labels per bucket index.

**Empty within tab.** When `monthlyKwh.isEmpty()`: show *"No data for this period"*.

### 6.3 Monthly cost (Bar chart, `BarChart`)

**Data source.** Same `MonthBucket` list filtered to entries with non-null `totalCost`. Already excluded from cost stats by `StatsCalculator.computeMonthlyBuckets` when `mixedCurrency` is true.

**Visibility rules** (in priority order):
1. `mixedCurrency == true` → entire tab body replaced with banner *"Multi-currency period — cost stats hidden"* (string already exists from F1; reuse `R.string.multi_currency_banner` from `strings.xml:179`).
2. `monthlyCost.isEmpty()` (no costed events at all) → tab-internal *"No cost data for this period"* message.
3. Otherwise render bars labeled with the period's **event-derived** currency, taken from `state.charts.periodCurrency` (which the use case computed from `MonthBucket.currency`). Never read `SettingsReader.currency` here — see §3.5.

**Bar color.** `?attr/colorTertiary` (visually distinct from kWh tab, no hard-coded green).

### 6.4 AC/DC split (Pie chart, `PieChart`)

**Data source.** New `StatsCalculator.computeAcDcSplit(periodEvents) → AcDcSplit`. Returns counts and totals; the pie shows **count proportions** with a sub-label *"24.3 kWh AC / 12.0 kWh DC"* under the pie.

**Slices.** Two slices, AC = `?attr/colorPrimary`, DC = `?attr/colorTertiary`. Centered hole text: total event count.

**Empty within tab.** When `acDc.acCount + acDc.dcCount == 0`: *"No data for this period"*.

**Pinch-zoom.** Disabled (pies don't benefit and rotation gestures conflict).

### 6.5 Locations (Pie chart, `PieChart`)

**Data source.** New `StatsCalculator.computeLocationDistribution(periodEvents) → List<LocationSlice>`. Group by non-null, non-blank `location` value (case-sensitive, trimmed); count events per label; sort descending by count; cap at top **8** labels and merge the rest into a single *"Other"* slice.

**Why 8?** A pie with > 8 slices becomes unreadable on phone-width screens. 8 + Other matches the existing top-5 quick-chip rationale (top-N by use, rest collapsed).

**Slice colours.** Material 3 distinct-color palette generated by `ChartStyling.locationPalette(slot)`. Same palette for the whole period; deterministic by sort order so the same label gets the same colour as long as the ranking is stable.

**Empty within tab.** When `locations.isEmpty()` (no events have any location set): *"No location data for this period"*.

**Pinch-zoom.** Disabled (same reason as AC/DC).

---

## 7. Empty-state ladder

Three levels, evaluated in this order in `ObserveChartsModelsUseCase`:

| Level | Trigger | Render |
|-------|---------|--------|
| **NoCar** | `cars.isEmpty()` OR `activeCarId == -1` | Full-screen card. Headline `"Add a car to see charts"`, CTA button `"Add car"` → emits `ChartsEvent.NavigateToCars`. Period chips and TabLayout are `gone`. |
| **NoEvents** | Active car selected, but `chargeEventQueries.observeForCar(id)` is empty | Full-screen card. Headline `"Log a charge to see charts"`, CTA `"Log charge"` → `ChartsEvent.NavigateToChargeEdit`. Period chips and TabLayout `gone`. |
| **NoEventsForPeriod** (per-tab) | Active car has events, but the selected period contains zero events | Period chips + TabLayout still visible. Each tab shows its own empty message (the strings listed in §6). |

Strings reuse Dashboard's keys where the copy matches exactly (`empty_no_car_headline`, `empty_no_car_cta`, `empty_no_events_headline`, `empty_no_events_cta`).

The CTA navigation actions are added to `nav_graph.xml`:

```xml
<fragment android:id="@+id/chartsFragment" ...>
    <action android:id="@+id/action_charts_to_cars"       app:destination="@id/carsFragment"/>
    <action android:id="@+id/action_charts_to_chargeEdit" app:destination="@id/chargeEditFragment"/>
</fragment>
```

---

## 8. ViewModel contract

```kotlin
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val observeChartsModels: ObserveChartsModelsUseCase,
    settingsReader: SettingsReader,
) : ViewModel() {

    private val period = MutableStateFlow<ChartsPeriod>(ChartsPeriod.Last12Months)

    private val _events = MutableSharedFlow<ChartsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChartsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ChartsScreenState> =
        combine(period, settingsReader.distanceUnit) { p, du -> p to du }
            .flatMapLatest { (p, du) ->
                observeChartsModels.observe(p).map { ui ->
                    ChartsScreenState(period = p, distanceUnit = du, charts = ui)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsScreenState())

    fun selectPeriod(p: ChartsPeriod) { period.value = p }
    fun selectCustomRange(from: Long, to: Long) {
        period.value = ChartsPeriod.Custom(from, to)
    }
    fun onCustomChipClicked() { _events.tryEmit(ChartsEvent.OpenCustomRangePicker) }
    fun onAddCarCta()         { _events.tryEmit(ChartsEvent.NavigateToCars) }
    fun onLogChargeCta()      { _events.tryEmit(ChartsEvent.NavigateToChargeEdit) }
}
```

The use case reads `activeCarId` and the events stream (it owns the empty-state decisions and derives `periodCurrency` from event data). The ViewModel observes `distanceUnit` to drive the trend tab's km↔miles rendering — same threading pattern Dashboard uses today (`DashboardViewModel.kt:65–66`). `currency` is *not* a ViewModel input because chart cost labels must reflect the events, not the current user preference (see §3.5).

---

## 9. `ObserveChartsModelsUseCase` contract

`NowProvider` is a one-method `fun interface` declared in its own file `domain/usecase/NowProvider.kt`:

```kotlin
fun interface NowProvider { fun nowMillis(): Long }
```

The use case takes it as a constructor dependency:

```kotlin
class ObserveChartsModelsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val dateRangeResolver: DateRangeResolver,
    private val now: NowProvider,
    @AggregationDispatcher private val aggregationContext: CoroutineContext
) {
    fun observe(period: ChartsPeriod): Flow<ChartsUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { active, cars ->
            active to cars
        }.flatMapLatest { (active, cars) ->
            when {
                cars.isEmpty() || active == -1 -> flowOf(ChartsUiState.NoCar)
                else -> chargeEventQueries.observeForCar(active).map { all ->
                    if (all.isEmpty()) ChartsUiState.NoEvents
                    else build(all, period)
                }
            }
        }.flowOn(aggregationContext)   // §15: aggregation off main in production
    }

    private fun build(allEvents: List<ChargeEventEntity>, period: ChartsPeriod): ChartsUiState.Loaded {
        val range = dateRangeResolver.resolveCharts(period, now.nowMillis())
        val periodEvents = allEvents.filter { it.eventDate in range.startMillis..range.endMillis }
        val mixed = statsCalculator.detectMixedCurrency(periodEvents)
        val monthly = statsCalculator.computeMonthlyBuckets(periodEvents)
        val costBuckets = if (mixed) emptyList() else monthly.filter { it.totalCost != null }
        // periodCurrency: the single non-null bucket currency when not mixed; null otherwise.
        // computeMonthlyBuckets already nulls bucket.currency when mixedCurrency is true,
        // so this also yields null in the mixed case.
        val resolvedCurrency = if (mixed) null else costBuckets.firstNotNullOfOrNull { it.currency }
        return ChartsUiState.Loaded(
            periodHasEvents = periodEvents.isNotEmpty(),
            mixedCurrency = mixed,
            periodCurrency = resolvedCurrency,
            trend = statsCalculator.computeEfficiencyTrend(periodEvents),
            monthlyKwh = monthly,
            monthlyCost = costBuckets,
            acDc = statsCalculator.computeAcDcSplit(periodEvents),
            locations = statsCalculator.computeLocationDistribution(periodEvents)
        )
    }
}
```

**Helper sharing.** `detectMixedCurrency(events)` is extracted from the existing inline logic in `StatsCalculator.computeStats` so it can be shared by the use case without re-running the full stats computation.

**Off-main aggregation.** `flowOn(aggregationContext)` is applied at the use-case boundary, not in the ViewModel. This shifts both the upstream Room observer's emissions and the per-emission `build(...)` aggregation to whichever dispatcher the qualifier supplies. Room flows are dispatcher-agnostic on the producer side, so this is safe. The ViewModel's `combine(...).flatMapLatest { ... }.stateIn(...)` chain consumes already-aggregated values, leaving the main thread responsible only for state delivery and rendering.

**Dispatcher injection.** Two new files under `di/`:

- `di/AggregationDispatcher.kt` — qualifier annotation only:

  ```kotlin
  @Qualifier
  @Retention(AnnotationRetention.BINARY)
  annotation class AggregationDispatcher
  ```

- `di/DispatcherModule.kt` — Hilt module that supplies the qualified context and the production `NowProvider`:

  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  object DispatcherModule {
      @Provides @AggregationDispatcher
      fun provideAggregationContext(): CoroutineContext = Dispatchers.Default

      @Provides
      fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }
  }
  ```

The existing `AppModule.kt` and `DomainModule.kt` are unchanged — adding dispatcher/clock concerns to either would mix unrelated bindings. The qualifier lives in its own file so other future use cases can reuse it without forcing a dependency on `DispatcherModule`'s contents.

JVM tests that exercise the use case directly construct it with `aggregationContext = EmptyCoroutineContext` and a fixed `NowProvider`, keeping the flow on the test scheduler and avoiding the well-known `runTest` + real `Dispatchers.Default` race.

---

## 10. `StatsCalculator` extensions

Three new pure functions appended to `StatsCalculator`. All operate on `List<ChargeEventEntity>` and return data classes from `core/model/`. None of them allocate Android types or coroutines.

```kotlin
fun detectMixedCurrency(events: List<ChargeEventEntity>): Boolean =
    events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct().size > 1

fun computeEfficiencyTrend(events: List<ChargeEventEntity>): EfficiencySeries {
    fun seriesFor(type: String): List<EfficiencyPoint> {
        val sorted = events.filter { it.chargeType == type }.sortedBy { it.eventDate }
        val out = ArrayList<EfficiencyPoint>(sorted.size)
        for (i in 1 until sorted.size) {
            val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
            if (dist > 0 && sorted[i].kwhAdded > 0.0) {
                out += EfficiencyPoint(sorted[i].eventDate, dist / sorted[i].kwhAdded)
            }
        }
        return out
    }
    return EfficiencySeries(acPoints = seriesFor("AC"), dcPoints = seriesFor("DC"))
}

fun computeAcDcSplit(events: List<ChargeEventEntity>): AcDcSplit {
    val ac = events.filter { it.chargeType == "AC" }
    val dc = events.filter { it.chargeType == "DC" }
    return AcDcSplit(
        acCount = ac.size,
        dcCount = dc.size,
        acKwh   = ac.sumOf { it.kwhAdded },
        dcKwh   = dc.sumOf { it.kwhAdded }
    )
}

fun computeLocationDistribution(events: List<ChargeEventEntity>): List<LocationSlice> {
    val counts = events
        .mapNotNull { it.location?.trim()?.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
    if (counts.isEmpty()) return emptyList()
    val ranked = counts.entries.sortedByDescending { it.value }
    val top = ranked.take(MAX_LOCATION_SLICES).map { LocationSlice(it.key, it.value) }
    val tail = ranked.drop(MAX_LOCATION_SLICES)
    return if (tail.isEmpty()) top
           else top + LocationSlice(LocationSlice.OTHER_KEY, tail.sumOf { it.value })
}

private companion object { const val MAX_LOCATION_SLICES = 8 }
```

The collapsed-tail slice uses a sentinel key, not a hard-coded English string. `LocationSlice` declares:

```kotlin
data class LocationSlice(val label: String, val count: Int) {
    val isOther: Boolean get() = label == OTHER_KEY
    companion object { const val OTHER_KEY = " __other__" }
}
```

The leading ` ` makes accidental collision with a user-typed location label impossible (no Android text input can contain a NUL). The Fragment's `LocationsTab` translates the sentinel at render time via `if (slice.isOther) getString(R.string.charts_locations_other) else slice.label`. JVM tests assert the sentinel directly without depending on Android resources. The cap stays as a code constant (tweaking it is a code change, not a localization change).

---

## 11. Testing

### 11.1 New JVM unit tests

| File | Tests |
|------|-------|
| `StatsCalculatorTrendTest.kt` | `emptyEvents_returnsEmptySeries`, `singleAcEvent_emptySeries`, `acAndDcEvents_partitionedCorrectly`, `negativeOdometerDelta_skipped`, `zeroKwh_skipped`, `mixedTypeOrder_eachSeriesSortedIndependently` |
| `StatsCalculatorAcDcSplitTest.kt` | `emptyEvents_zeroSplit`, `onlyAc_returnsZeroDc`, `mixed_correctTotals`, `kwhSumsCorrect` |
| `StatsCalculatorLocationDistTest.kt` | `emptyEvents_returnsEmpty`, `nullAndBlankLocations_excluded`, `singleLocation_oneSlice`, `nineLocations_collapsesToTopEightPlusOther`, `tieBreaking_byInsertionOrder`, `trim_caseSensitive` |
| `DateRangeResolverChartsTest.kt` | `last6Months_182Days`, `last12Months_365Days`, `allTime_lowerBoundZero`, `custom_passthrough` |
| `ObserveChartsModelsUseCaseTest.kt` | `noCar_emitsNoCar`, `activeCarMinusOne_emitsNoCar`, `noEvents_emitsNoEvents`, `eventsOutsidePeriod_emitsLoadedWithPeriodHasEventsFalse`, `eventsInPeriod_singleCurrency_emitsAllSeriesAndPeriodCurrency`, `eventsInPeriod_mixedCurrency_zeroesMonthlyCostAndPeriodCurrencyNull`, `differentPeriodArg_producesDifferentBuild`, `carSwitch_resetsState` |
| `ChartsViewModelTest.kt` | `defaultPeriod_isLast12Months`, `selectPeriod_emitsNewState`, `selectCustomRange_wrapsInCustom`, `periodChange_recomputesViaFlatMapLatest`, `distanceUnitChange_propagatesToScreenState`, `costLabelDoesNotReadSettingsCurrency`, `onCustomChipClicked_emitsOpenCustomRangePicker`, `onAddCarCta_emitsNavigateToCars`, `onLogChargeCta_emitsNavigateToChargeEdit`, `events_replayIsZero_noReplayOnLateCollector` |

The `events_replayIsZero_noReplayOnLateCollector` test is essential because it would catch a future "let me just use `replay = 1` here" mistake that has bitten the codebase before (CLAUDE.md "ViewModel + event pattern").

### 11.2 New `Fakes.kt` additions

A Charts-specific fake is not needed; the existing `FakeChargeEventQueries`, `FakeCarReader`, `FakeSettingsReader` cover the use case. Tests construct a real `StatsCalculator()` and `DateRangeResolver()` directly.

**Deterministic clock.** `ObserveChartsModelsUseCaseTest` and `ChartsViewModelTest` construct the use case with a fixed `NowProvider`:

```kotlin
val now = NowProvider { 1_714_032_000_000L }   // 2026-04-25T08:00Z, anchor for the period math
val useCase = ObserveChartsModelsUseCase(
    carReader, chargeEventQueries, settingsReader,
    StatsCalculator(), DateRangeResolver(),
    now = now,
    aggregationContext = EmptyCoroutineContext
)
```

The use case threads `now.nowMillis()` into `dateRangeResolver.resolveCharts(...)` on every emission, so rolling-window tests are time-stable. Production-code Hilt binding is provided once via the same `DispatcherModule` (see §9): `@Provides fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }`.

### 11.3 New instrumented tests

`ChartsFragmentTest.kt` (Hilt + `launchFragmentInContainer` + `TestNavHostController` — same harness as `SettingsFragmentTest` from F1):

| Test | Steps | Expected |
|------|-------|----------|
| `tabSwitch_showsCorrectChart` | Seed DAO with ≥ 2 events spanning two months. Launch fragment. Click each tab in turn. | After each click, the corresponding chart container is `displayed`; the others are `not(isDisplayed())`. |
| `noData_emptyState_perPeriod` | Seed DAO with 1 event from 2 years ago. Default period is *Last 12 months*. | Period chips visible; *every* tab body shows the relevant per-tab empty message (no chart). |
| `noCar_showsAddCarCta` | Empty `cars` table. | Period chips and TabLayout `gone`; Add-car CTA visible; clicking it navigates to `R.id.carsFragment`. |
| `noEvents_showsLogChargeCta` | One car, zero events. | Period chips and TabLayout `gone`; Log-charge CTA visible; click navigates to `R.id.chargeEditFragment`. |
| `multiCurrencyPeriod_costTabShowsBanner` | Two costed events, EUR + USD, same month. Click *Monthly cost* tab. | Banner visible with multi-currency string; chart not rendered. |

Each test asserts navigation via `TestNavHostController.currentDestination?.id` — same pattern as `SettingsFragmentTest` post-rev3 in F1.

### 11.4 Coverage targets (carried forward from F1)

- `StatsCalculator` extensions: ≥ 90% line coverage (pure code, achievable trivially).
- `ObserveChartsModelsUseCase`: ≥ 90%.
- `ChartsViewModel`: ≥ 80%.

### 11.5 No flakiness affordances

- No `Thread.sleep`. The use case is observable; instrumented tests use `IdlingResource`-equivalent waits via DAO Flow polling (the F1 pattern).
- No `runCurrent`-vs-`advanceUntilIdle` gotchas: the ViewModel never uses time-based delays. Tests use `runTest` and `advanceUntilIdle` for the standard-dispatcher convergence.
- Chart rendering is not asserted at the pixel level; only container visibility, tab labels, and CTA navigation. MPAndroidChart's own internals are not the system under test.

---

## 12. MPAndroidChart configuration (centralized in `ChartStyling`)

Pure Kotlin object so JVM tests can poke at it (color resolution requires a context, isolated to one method that takes `Context` explicitly):

```kotlin
object ChartStyling {
    fun resolveSeriesColors(context: Context): Pair<Int, Int> {
        // colorPrimary, colorTertiary; fall back to chart_ac_fallback / chart_dc_fallback
    }
    fun configureLineChart(chart: LineChart, context: Context, distanceUnit: String): Unit
    fun configureBarChart(chart: BarChart, context: Context, yLabel: String): Unit
    fun configurePieChart(chart: PieChart, context: Context): Unit
    fun monthLabelFormatter(): IAxisValueFormatter
    fun dateLabelFormatter(window: ChartsPeriod): IAxisValueFormatter
    fun locationPalette(slot: Int): Int
}
```

Configuration applied to all charts:

- `description.isEnabled = false`
- `legend.isEnabled = true` (line + pies); `legend.isEnabled = false` for bar charts (single series)
- `setNoDataText("")` — empty states are handled by us, not by MPAndroidChart's default text
- `axisRight.isEnabled = false` on Line + Bar
- `xAxis.position = BOTTOM`, granularity = 1
- Touch enabled; pinch-zoom on Line + Bar; off on Pies
- Animations: `animateY(400)` on first load; suppressed on subsequent state emissions to avoid flicker. (Implemented by tracking a `firstRenderConsumed` boolean per tab fragment.)

`ChartsMarkerView`:

- Inflates `view_chart_marker.xml` (white card with two `TextView`s — date and value)
- Updates content from `Entry` data via `refreshContent`
- Returns `MPPointF.getInstance(-(width / 2f), -height.toFloat())` from `getOffset()` so the marker hovers above the tap point

---

## 13. Bottom-nav and back-stack

The bottom navigation already includes Charts (`res/menu/bottom_nav.xml`). `MainActivity` already binds `bottomNav.setupWithNavController(navController)`. F2 does not add or remove bottom-nav items.

Back-stack behaviour: navigating away from Charts via Dashboard's CTAs uses standard `findNavController().navigate(actionId)`. There is no need to pop the Charts back-stack entry; the bottom nav handles re-selection naturally.

The `BottomNavigationView` is hidden when the user is in `wizardFragment`, `chargeEditFragment`, `carsFragment`, or `manageLocationsFragment` (per CLAUDE.md). Charts is *not* hidden — it stays visible like Dashboard / History / Settings.

---

## 14. Strings (new keys)

```xml
<!-- F2 — Charts -->
<string name="charts_title">Charts</string>
<string name="charts_period_last_6_months">Last 6 months</string>
<string name="charts_period_last_12_months">Last 12 months</string>
<string name="charts_period_all_time">All time</string>
<string name="charts_period_custom">Custom…</string>
<string name="charts_tab_trend">Trend</string>
<string name="charts_tab_monthly_kwh">Monthly energy</string>
<string name="charts_tab_monthly_cost">Monthly cost</string>
<string name="charts_tab_ac_dc">AC vs DC</string>
<string name="charts_tab_locations">Locations</string>
<string name="charts_trend_legend_ac">AC</string>
<string name="charts_trend_legend_dc">DC</string>
<string name="charts_trend_y_kmh">Efficiency (km/kWh)</string>
<string name="charts_trend_y_mi">Efficiency (mi/kWh)</string>
<string name="charts_trend_need_two">Need at least 2 charges to plot a trend</string>
<string name="charts_no_data_period">No data for this period</string>
<string name="charts_no_cost_period">No cost data for this period</string>
<string name="charts_no_locations_period">No location data for this period</string>
<string name="charts_locations_other">Other</string>
<string name="charts_acdc_kwh_subtitle">%1$.1f kWh AC · %2$.1f kWh DC</string>
```

Reused (no new keys):
- `R.string.empty_no_car_headline`, `R.string.empty_no_car_cta`
- `R.string.empty_no_events_headline`, `R.string.empty_no_events_cta`
- `R.string.multi_currency_banner` (from `strings.xml:179` — note: `dashboard_multi_currency_banner` is the *view id* in `fragment_dashboard.xml`, not a string id)
- `R.string.period_custom`

---

## 15. Risks and mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| MPAndroidChart's `MarkerView` API is awkward and easy to mis-anchor | Medium | All anchor math goes through `ChartStyling.markerOffset`; tested in instrumented test by tapping a known data point. |
| ViewPager2 + Fragment recycling causes chart state loss on rotation | Medium | Each tab is a Fragment; MPAndroidChart's own `setSavedState` is not used — instead we rebuild from `uiState.value` on `onViewCreated`, which is the StateFlow's most recent value (free thanks to `stateIn(WhileSubscribed)`). |
| Theme attr resolution silently falls back to neutral colors when run inside `launchFragmentInContainer` without a Material 3 theme | Low | Test harness uses `R.style.Theme_EVTracker` exactly like `SettingsFragmentTest`; verified once in `tabSwitch_showsCorrectChart`. |
| Large all-time datasets (e.g. 5 000 events) cause ANR during pie/bar aggregation | Low | All aggregators are O(n) over per-car events. The use case applies `.flowOn(aggregationContext)` (provided as `Dispatchers.Default` in production via the `@AggregationDispatcher` qualifier — see §9) so both the upstream Room observer and the per-emission `build(...)` call run off the main thread. The ViewModel's downstream `combine`/`flatMapLatest`/`stateIn` sees already-aggregated values and the main thread is responsible only for state delivery. Tests pass `EmptyCoroutineContext` to keep the flow on the test dispatcher. |
| New `MAX_LOCATION_SLICES = 8` is a bare constant without rationale in the codebase | Low | Comment in code references this spec section. |
| Future spec drift: someone adds a new chart and forgets to wire it into the empty-state ladder | Low | `ChartsUiState.Loaded` is a single data class; adding a field forces a compile error in the rendering Fragment. The empty-state ladder is at the use-case boundary and does not multiply per chart. |

---

## 16. Acceptance criteria

- [ ] All five charts render against a seeded DB with ≥ 2 AC and ≥ 2 DC events spanning two calendar months, in two currencies (one mono-currency tab visible, one banner-only tab).
- [ ] Period chips switch between *Last 6 mo / Last 12 mo / All time / Custom*. Custom opens a date-range picker and the previously-selected chip restores on cancel.
- [ ] No-car state shows the *Add car* CTA and navigates to `carsFragment`. No-events state shows the *Log charge* CTA and navigates to `chargeEditFragment`. No-events-for-period state shows tab-internal messages without hiding the period chips.
- [ ] Multi-currency periods render the banner exclusively on the cost tab; the other four tabs render normally.
- [ ] Switching `activeCarId` from another screen (Dashboard) repaints Charts within one frame after returning to it.
- [ ] Distance-unit preference change (km ↔ miles) updates the trend Y axis label and re-formats values *without* mutating any stored data.
- [ ] All new JVM tests pass; `:app:testDebugUnitTest` is green; total JVM unit-test count rises by **≥ 35** (~6 trend + ~4 split + ~6 location + ~4 resolver + ~8 use case + ~10 VM = 38 expected).
- [ ] All new instrumented tests pass; `:app:assembleDebugAndroidTest` compiles cleanly.
- [ ] `./gradlew :app:assembleDebug` produces an APK that opens to the Charts tab without crashing on a fresh install (NoCar state visible).
- [ ] CLAUDE.md *Status* section is updated in the merge commit to reflect F2 shipped and Charts is now wired (✓), and JVM test count bumped.

---

## 17. Implementation hand-off

The next skill (writing-plans) will decompose this spec into bite-sized tasks. The plan should:

- Land **StatsCalculator extensions + DateRangeResolver extension first**, in their own commits, with full JVM tests, before any UI work. They are pure and gate everything else.
- Land **`ObserveChartsModelsUseCase` second**, with its tests. It can use the new aggregators on top of the existing fakes.
- Land **`ChartsViewModel` third**, with its tests.
- Land **layout XML + ChartStyling + MarkerView fourth**, before the Fragment, so the Fragment task is purely wiring.
- Land **`ChartsFragment` + `ChartsPagerAdapter` + `ChartsTabFragment` fifth**.
- Land **instrumented tests sixth**, including the multi-currency banner case.
- Land **CLAUDE.md status update + final commit** seventh.

Each task ends with a working build. The plan should produce a single feat branch (e.g. `feat/sub-project-f2`), merged to main with `--no-ff` exactly as F1 was.
