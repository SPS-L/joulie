# Default `primaryMetric` → `kwh_per_100km` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flip the fresh-install default for `primaryMetric` from `km_per_kwh` to `kwh_per_100km` and extend the Trend chart's Y-axis to follow `primaryMetric` so the default UX is internally consistent.

**Architecture:** Mechanical default flip across 4 production sites + 5 test sites. Chart fix adds `primaryMetric` to `ChartsScreenState`, plumbs it through `ChartsViewModel.combine`, and branches the Trend Y transform on `primaryMetric` (kWh/100km, mi/kWh, km/kWh) instead of `distanceUnit` (km, miles).

**Tech Stack:** Kotlin 1.9.21, AndroidX `lifecycle-viewmodel-ktx` 2.7.0, Hilt 2.50, kotlinx-coroutines 1.7.3, MPAndroidChart, JUnit 4.

---

### Task 0: Spec commit

**Files:**
- New: `docs/superpowers/specs/2026-04-30-default-metric-kwh-per-100km-design.md`

- [ ] **Step 1: Stage and commit the spec**

```bash
git add docs/superpowers/specs/2026-04-30-default-metric-kwh-per-100km-design.md
git commit -m "docs(default-metric): spec for kwh_per_100km default + Trend chart"
```

---

### Task 1: Flip the production defaults (RED for tests using the old token implicitly)

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt:22`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt:19`
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt:15`
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/DashboardScreenState.kt:10`

- [ ] **Step 1: Flip `SettingsRepository` DataStore fallback**

Change the literal:

```kotlin
override val primaryMetric: Flow<String> =
    dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "kwh_per_100km" }
```

- [ ] **Step 2: Flip `WizardViewModel.UiState.metric`**

```kotlin
data class UiState(
    val page: Int = 0,
    val metric: String = "kwh_per_100km",
    val unit: String = "km",
    val currency: String = "EUR",
)
```

- [ ] **Step 3: Flip `SettingsUiState.primaryMetric`**

```kotlin
val primaryMetric: String = "kwh_per_100km",
```

- [ ] **Step 4: Flip `DashboardScreenState.primaryMetric`**

```kotlin
val primaryMetric: String = "kwh_per_100km",
```

- [ ] **Step 5: Run JVM tests to capture the RED set**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```

Expected: tests that compare the *unset / default* value to the literal `"km_per_kwh"` now fail. Per the spec the four sites are `Fakes.kt:114,181`, `SettingsRepositoryTest.kt:38`, `WizardViewModelTest.kt:71,76`, `DashboardViewModelTest.kt:49`. Tests that pass the old token *explicitly* (e.g. `selectMetric("km_per_kwh")`) keep passing because the token is still legal. Note the failing test list — we'll tick each one off in Task 2.

---

### Task 2: Update test fakes and assertions (GREEN)

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt:114,181`
- Modify: `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt:38`
- Modify: `app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt:71,76`
- Modify: `app/src/test/java/org/spsl/evtracker/ui/dashboard/DashboardViewModelTest.kt:49`

- [ ] **Step 1: `FakeSettingsReader` default**

```kotlin
class FakeSettingsReader(
    activeCarIdInit: Int = -1,
    primaryMetricInit: String = "kwh_per_100km",
    distanceUnitInit: String = "km",
    // …rest unchanged
)
```

- [ ] **Step 2: `FakeSettingsWriter.primaryMetric` initial**

```kotlin
var primaryMetric: String = "kwh_per_100km"
    private set
```

- [ ] **Step 3: `SettingsRepositoryTest:38` assertion**

```kotlin
assertEquals("kwh_per_100km", repo.primaryMetric.first())
```

- [ ] **Step 4: `WizardViewModelTest:71,76`**

The old test reads:

```kotlin
assertEquals("km_per_kwh", vm.state.value.metric)
…
// default metric is km_per_kwh
```

becomes:

```kotlin
assertEquals("kwh_per_100km", vm.state.value.metric)
…
// default metric is kwh_per_100km
```

If line 71 is part of a wider test that asserts a specific reachable
state (e.g., the metric remains the default after `selectUnit("km")`),
verify the assertion still tests what it claims — same value, just the
new default token. If the test specifically intended to verify the *old*
default token's behaviour rather than "the default", split it: rename
the test to clarify and adapt the assertion to match the new default
without losing coverage.

- [ ] **Step 5: `DashboardViewModelTest:49` parameter default**

```kotlin
private fun build(
    primaryMetric: String = "kwh_per_100km",
    // …rest unchanged
)
```

- [ ] **Step 6: Run JVM tests — expect GREEN (no failures, count unchanged)**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, 236 tests pass (count unchanged because no new tests yet).

- [ ] **Step 7: Commit Tasks 1 + 2 together**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt
git add app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt
git add app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt
git add app/src/main/java/org/spsl/evtracker/core/model/DashboardScreenState.kt
git add app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git add app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt
git add app/src/test/java/org/spsl/evtracker/ui/wizard/WizardViewModelTest.kt
git add app/src/test/java/org/spsl/evtracker/ui/dashboard/DashboardViewModelTest.kt
git commit -m "refactor(default-metric): flip default primaryMetric to kwh_per_100km"
```

---

### Task 3: Add `primaryMetric` to `ChartsScreenState` and plumb it through `ChartsViewModel` (RED for the new test)

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/ChartsScreenState.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsViewModel.kt:48-51`
- Modify: `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt`

- [ ] **Step 1: Write the failing `ChartsViewModelTest` case first**

Open `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt` and add a new test method that asserts `primaryMetric` flows from `SettingsReader` into `ChartsScreenState`. Match the existing test rig in that file — do not invent a new harness.

```kotlin
@Test fun uiState_reflects_primaryMetric_fromSettingsReader() = runTest {
    val rig = build(primaryMetricInit = "kwh_per_100km")
    val collected = mutableListOf<ChartsScreenState>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) {
        rig.vm.uiState.collect { collected += it }
    }
    job.cancel()
    assertEquals("kwh_per_100km", collected.last().primaryMetric)
}

@Test fun uiState_reflects_primaryMetric_kmPerKwh() = runTest {
    val rig = build(primaryMetricInit = "km_per_kwh")
    val collected = mutableListOf<ChartsScreenState>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) {
        rig.vm.uiState.collect { collected += it }
    }
    job.cancel()
    assertEquals("km_per_kwh", collected.last().primaryMetric)
}
```

If the existing rig's `build(...)` does not accept `primaryMetricInit`, add the parameter with a default of `"kwh_per_100km"` and forward it to the `FakeSettingsReader` constructor it already creates.

- [ ] **Step 2: Run the new test — expect RED**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.charts.ChartsViewModelTest.uiState_reflects_primaryMetric_fromSettingsReader" 2>&1 | tail -15
```

Expected: compile failure (`Unresolved reference: primaryMetric` on `ChartsScreenState`).

- [ ] **Step 3: Add `primaryMetric` to `ChartsScreenState`**

```kotlin
data class ChartsScreenState(
    val period: ChartsPeriod = ChartsPeriod.Last12Months,
    /** "km" | "miles". Drives trend km↔miles render-time conversion. */
    val distanceUnit: String = "km",
    /** "km_per_kwh" | "kwh_per_100km" | "mi_per_kwh". Drives Trend Y-axis mode. */
    val primaryMetric: String = "kwh_per_100km",
    val charts: ChartsUiState = ChartsUiState.Loading,
)
```

- [ ] **Step 4: Plumb `primaryMetric` through `ChartsViewModel`**

In `ChartsViewModel.kt`, replace the 3-flow `combine` with a 4-flow `combine`:

```kotlin
val uiState: StateFlow<ChartsScreenState> =
    combine(
        chartsFlow,
        period,
        settingsReader.distanceUnit,
        settingsReader.primaryMetric,
    ) { ui, p, du, pm ->
        ChartsScreenState(period = p, distanceUnit = du, primaryMetric = pm, charts = ui)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsScreenState())
```

- [ ] **Step 5: Run the new tests — expect GREEN**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.charts.ChartsViewModelTest" 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, all `ChartsViewModelTest` cases pass.

- [ ] **Step 6: Run full JVM tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL, total count = 236 + 2 = **238**.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/ChartsScreenState.kt
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsViewModel.kt
git add app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt
git commit -m "feat(charts): plumb primaryMetric through ChartsScreenState"
```

---

### Task 4: Branch the Trend Y-transform on `primaryMetric` and add the new label string

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsTabFragment.kt:100-161` (`renderTrend`)
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the new string resource**

In `strings.xml`, alongside the existing `charts_trend_y_kmh` and `charts_trend_y_mi`:

```xml
<string name="charts_trend_y_kwh100">kWh/100km</string>
```

- [ ] **Step 2: Replace the Y transform + suffix block in `renderTrend`**

Locate the block currently containing `val unitToMi = state.distanceUnit == "miles"` and the `toEntries` lambda (lines ~116–149). Replace with:

```kotlin
val (yTransform, unitSuffix) = when (state.primaryMetric) {
    "kwh_per_100km" -> Pair<(Double) -> Double?, String>(
        { kmPerKwh -> if (kmPerKwh > 0.0) 100.0 / kmPerKwh else null },
        getString(R.string.charts_trend_y_kwh100),
    )
    "mi_per_kwh" -> Pair<(Double) -> Double?, String>(
        { kmPerKwh -> UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh) },
        getString(R.string.charts_trend_y_mi),
    )
    else -> Pair<(Double) -> Double?, String>(
        { kmPerKwh -> kmPerKwh },
        getString(R.string.charts_trend_y_kmh),
    )
}
val windowStart = charts.periodStartMillis

fun toEntries(points: List<org.spsl.evtracker.core.model.EfficiencyPoint>): List<Entry> =
    points.mapNotNull {
        val y = yTransform(it.kmPerKwh) ?: return@mapNotNull null
        val xDays = ((it.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY).toFloat()
        Entry(xDays, y.toFloat(), it.eventTimeMillis as Any)
    }
```

Then later, replace the now-dead `unitSuffix` derivation block:

```kotlin
val unitSuffix = if (unitToMi) {
    getString(R.string.charts_trend_y_mi)
} else {
    getString(R.string.charts_trend_y_kmh)
}
```

— delete it; `unitSuffix` is already bound by the `when` above. Confirm `chart.marker = ChartsMarkerView(requireContext(), unitSuffix)` still references the same `unitSuffix` symbol.

- [ ] **Step 3: Compile + run JVM tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest 2>&1 | tail -15
```

Expected: BUILD SUCCESSFUL. No new warnings (the `unitToMi` removal is the whole net diff).

- [ ] **Step 4: Compile instrumented bundle**

```bash
./gradlew :app:assembleDebugAndroidTest 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsTabFragment.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(charts): Trend Y-axis follows primaryMetric"
```

---

### Task 5: Doc updates + plan commit + CI gate

**Files:**
- Modify: `docs/DESIGN.md:139` (defaults table) and the Charts section's Trend description
- New: `docs/superpowers/plans/2026-04-30-default-metric-kwh-per-100km.md` (this file)

- [ ] **Step 1: Update `docs/DESIGN.md` defaults table**

In the DataStore key table (around line 139), change the `primaryMetric` row's default cell from `"km_per_kwh"` to `"kwh_per_100km"`.

- [ ] **Step 2: Update `docs/DESIGN.md` Charts → Trend description**

Add a sentence noting the Trend Y-axis now follows `primaryMetric`:

> The Trend Y-axis follows `primaryMetric`: `km_per_kwh` plots raw `kmPerKwh`,
> `kwh_per_100km` plots `100 / kmPerKwh` (skipping rows with `kmPerKwh ≤ 0`),
> and `mi_per_kwh` plots `UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh)`.

(Match the existing prose style; keep it terse.)

- [ ] **Step 3: Run the full CI gate locally**

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease
```

Expected: BUILD SUCCESSFUL on all four. If `ktlintCheck` fails on the new chart code, run `./gradlew :app:ktlintFormat` once and re-run the gate.

- [ ] **Step 4: Verify acceptance criteria 4 and 5**

```bash
git grep '= "km_per_kwh"' app/src/main/java
git grep "kwh_per_100km" app/src/main/java/org/spsl/evtracker/ui/charts
```

Expected:
- First command: zero output.
- Second command: at least one match (in `ChartsScreenState.kt` and/or `ChartsTabFragment.kt`).

- [ ] **Step 5: Commit docs + this plan together**

```bash
git add docs/DESIGN.md
git add docs/superpowers/plans/2026-04-30-default-metric-kwh-per-100km.md
git commit -m "docs(default-metric): DESIGN update + plan"
```

---

### Task 6: Merge, push, cleanup

**Files:** none

- [ ] **Step 1: Merge `--no-ff` into main**

```bash
git checkout main
git merge --no-ff feat/default-metric-kwh-per-100km -m "Merge branch 'feat/default-metric-kwh-per-100km'"
```

- [ ] **Step 2: Push**

```bash
git push origin main
```

- [ ] **Step 3: Delete the feature branch**

```bash
git branch -d feat/default-metric-kwh-per-100km
```

- [ ] **Step 4: Final state check**

```bash
git status
git log --oneline -10
```

Expected: clean tree on `main`; HEAD is the merge commit; the five task commits sit on the merged branch tip.
