# Sub-project F2 — Charts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder `ChartsFragment` with the full five-tab Charts screen described in `docs/superpowers/specs/2026-04-29-sub-project-f2-design.md` — efficiency line trend (AC vs DC), monthly kWh bar, monthly cost bar (with multi-currency banner), AC/DC pie, location-distribution pie — driven by a period control (Last 6 mo / 12 mo / All time / Custom) and a three-tier empty-state ladder.

**Architecture:** A new `ObserveChartsModelsUseCase` (off-main via `@AggregationDispatcher`) combines `ChargeEventQueries`, `SettingsReader`, an extended `StatsCalculator` (three new pure aggregators), and an extended `DateRangeResolver` into a `ChartsScreenState`. A new `ChartsViewModel` keeps rendering inputs separate from the behaviour-driving flow (matches `DashboardViewModel`): `period` is the only thing that triggers re-subscription/re-aggregation; `distanceUnit` joins at the outer `combine`. The Fragment hosts a `ViewPager2` of five `ChartsTabFragment` instances that share the parent ViewModel; MPAndroidChart configuration is centralised in `ChartStyling`. Clock seam via `NowProvider`.

**Tech Stack:** Kotlin · Hilt 2.50 (KSP) · MPAndroidChart (already wired) · Coroutines + StateFlow/SharedFlow · ViewPager2 + TabLayout · MaterialDatePicker · JUnit 4 + mockito-kotlin + kotlinx-coroutines-test for JVM · Hilt + Espresso + TestNavHostController for instrumented.

**Sandbox build commands** (CLAUDE.md note — `~/.gradle` is read-only in this sandbox):

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest    # compile-only; running needs an emulator
```

**Branch:** `feat/sub-project-f2` (already created). Plan and spec already committed there. Final merge to `main` via `--no-ff`, then `git branch -d feat/sub-project-f2` (matches A/B/C/D/E/F1 flow per CLAUDE.md). Per CLAUDE.md global rules: never compound git commands with `&&`; run each git command separately.

---

## File Structure

### New files

```
core/model/
  ChartsPeriod.kt              — sealed class (4 variants)
  EfficiencyPoint.kt           — data class (Long, Double)
  EfficiencySeries.kt          — data class (acPoints, dcPoints)
  AcDcSplit.kt                 — data class (counts + kWh totals)
  LocationSlice.kt             — data class + OTHER_KEY sentinel
  ChartsUiState.kt             — sealed class (Loading/NoCar/NoEvents/Loaded)
  ChartsScreenState.kt         — top-level render state + ChartsEvent

domain/usecase/
  NowProvider.kt               — fun interface { fun nowMillis(): Long }
  ObserveChartsModelsUseCase.kt

di/
  AggregationDispatcher.kt     — qualifier annotation
  DispatcherModule.kt          — @Provides for @AggregationDispatcher CoroutineContext + NowProvider

ui/charts/
  ChartsViewModel.kt           — replaced (was empty stub)
  ChartsFragment.kt            — replaced (was placeholder)
  ChartsPagerAdapter.kt        — FragmentStateAdapter
  ChartsTabFragment.kt         — single Fragment class with TabKind enum
  ChartStyling.kt              — pure helpers (axes, colors, formatters)
  ChartsMarkerView.kt          — MPAndroidChart MarkerView impl

res/layout/
  fragment_charts.xml          — replaced (period chips + TabLayout + ViewPager2)
  fragment_charts_tab.xml      — generic tab host (chart container + empty TextView)
  view_chart_marker.xml        — date label + value label

res/values/
  colors.xml                   — chart_ac_fallback / chart_dc_fallback (additions)
  strings.xml                  — ~22 new keys (additions)

JVM tests:
app/src/test/java/org/spsl/evtracker/
  domain/service/StatsCalculatorTrendTest.kt
  domain/service/StatsCalculatorAcDcSplitTest.kt
  domain/service/StatsCalculatorLocationDistTest.kt
  domain/service/StatsCalculatorMixedCurrencyTest.kt
  domain/service/DateRangeResolverChartsTest.kt
  domain/usecase/ObserveChartsModelsUseCaseTest.kt
  ui/charts/ChartsViewModelTest.kt

Instrumented tests:
app/src/androidTest/java/org/spsl/evtracker/
  ui/charts/ChartsFragmentTest.kt
```

### Modified files

```
app/src/main/java/org/spsl/evtracker/
  domain/service/StatsCalculator.kt        — append 4 new functions + companion const
  domain/service/DateRangeResolver.kt      — append resolveCharts(...)
  ui/charts/ChartsFragment.kt              — replaced
  ui/charts/ChartsViewModel.kt             — replaced

app/src/main/res/navigation/nav_graph.xml  — add 2 actions on chartsFragment
app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
                                           — add observeForCarCallCount to FakeChargeEventQueries
CLAUDE.md                                  — Status section bumped at end (Task 20)
```

No DAO changes, no Room migrations, no `PreferenceKeys` additions, no `AppDatabase` version bump.

---

## Task Map

| # | Task | Layer | Tests added |
|---|------|-------|-------------|
| 1 | `ChartsPeriod` + extend `DateRangeResolver` | core/domain | 4 JVM |
| 2 | Pure data classes (`EfficiencyPoint`, `EfficiencySeries`, `AcDcSplit`, `LocationSlice`) | core | smoke |
| 3 | `StatsCalculator.detectMixedCurrency` | domain | 3 JVM |
| 4 | `StatsCalculator.computeEfficiencyTrend` | domain | 6 JVM |
| 5 | `StatsCalculator.computeAcDcSplit` | domain | 4 JVM |
| 6 | `StatsCalculator.computeLocationDistribution` | domain | 6 JVM |
| 7 | Extend `FakeChargeEventQueries` with subscribe-count hook | testing | — |
| 8 | `NowProvider` + `AggregationDispatcher` + `DispatcherModule` | di | — |
| 9 | `ChartsUiState` / `ChartsScreenState` / `ChartsEvent` | core | smoke |
| 10 | `ObserveChartsModelsUseCase` | domain | 8 JVM |
| 11 | `ChartsViewModel` | ui | 11 JVM |
| 12 | Strings + colors | res | — |
| 13 | Layouts | res | — |
| 14 | `ChartStyling` | ui | — |
| 15 | `ChartsMarkerView` | ui | — |
| 16 | `ChartsTabFragment` | ui | — |
| 17 | `ChartsPagerAdapter` | ui | — |
| 18 | `ChartsFragment` + nav graph actions | ui | — |
| 19 | `ChartsFragmentTest` | androidTest | 5 instrumented |
| 20 | CLAUDE.md status update + final commit | docs | — |

JVM new-test rollup target: 6 + 4 + 6 + 4 + 8 + 11 = 39 new tests (matches spec §16).

---

## Task 1: `ChartsPeriod` sealed class + extend `DateRangeResolver`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/ChartsPeriod.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverChartsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverChartsTest.kt`:

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChartsPeriod

class DateRangeResolverChartsTest {

    private val resolver = DateRangeResolver()
    private val now = 1_714_032_000_000L  // 2024-04-25T08:00Z; deterministic anchor

    @Test fun last6Months_182Days() {
        val r = resolver.resolveCharts(ChartsPeriod.Last6Months, now)
        assertEquals(now - 182L * 24 * 60 * 60 * 1000, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun last12Months_365Days() {
        val r = resolver.resolveCharts(ChartsPeriod.Last12Months, now)
        assertEquals(now - 365L * 24 * 60 * 60 * 1000, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun allTime_lowerBoundZero() {
        val r = resolver.resolveCharts(ChartsPeriod.AllTime, now)
        assertEquals(0L, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun custom_passthrough() {
        val r = resolver.resolveCharts(ChartsPeriod.Custom(100L, 200L), now)
        assertEquals(100L, r.startMillis)
        assertEquals(200L, r.endMillis)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.DateRangeResolverChartsTest"
```

Expected: FAIL with "unresolved reference: ChartsPeriod" / "unresolved reference: resolveCharts".

- [ ] **Step 3: Add `ChartsPeriod` and `resolveCharts`**

Create `app/src/main/java/org/spsl/evtracker/core/model/ChartsPeriod.kt`:

```kotlin
package org.spsl.evtracker.core.model

sealed class ChartsPeriod {
    object Last6Months  : ChartsPeriod()
    object Last12Months : ChartsPeriod()
    object AllTime      : ChartsPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : ChartsPeriod()
}
```

Append to `app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt` — inside the existing `class DateRangeResolver`, after the existing `resolve(...)` method:

```kotlin
    fun resolveCharts(period: org.spsl.evtracker.core.model.ChartsPeriod, nowMillis: Long = System.currentTimeMillis()): org.spsl.evtracker.core.model.DateRange =
        when (period) {
            org.spsl.evtracker.core.model.ChartsPeriod.Last6Months  -> org.spsl.evtracker.core.model.DateRange(nowMillis - 182L * MILLIS_PER_DAY, nowMillis)
            org.spsl.evtracker.core.model.ChartsPeriod.Last12Months -> org.spsl.evtracker.core.model.DateRange(nowMillis - 365L * MILLIS_PER_DAY, nowMillis)
            org.spsl.evtracker.core.model.ChartsPeriod.AllTime      -> org.spsl.evtracker.core.model.DateRange(0L, nowMillis)
            is org.spsl.evtracker.core.model.ChartsPeriod.Custom    -> org.spsl.evtracker.core.model.DateRange(period.fromMillis, period.toMillis)
        }
```

(Use FQNs to avoid editing the import block; an implementer may collapse them to imports if the file's import style allows.)

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.DateRangeResolverChartsTest"
```

Expected: 4 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/ChartsPeriod.kt
git add app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt
git add app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverChartsTest.kt
git commit -m "feat(F2): ChartsPeriod sealed class + DateRangeResolver.resolveCharts"
```

---

## Task 2: Pure data classes — `EfficiencyPoint`, `EfficiencySeries`, `AcDcSplit`, `LocationSlice`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/EfficiencyPoint.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/EfficiencySeries.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/AcDcSplit.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/LocationSlice.kt`
- Test:   inside `StatsCalculatorLocationDistTest.kt` (added in Task 6) — but also a tiny smoke test bundled here for `LocationSlice.isOther` since it's the only non-trivial data class

- [ ] **Step 1: Write the failing smoke test**

Create `app/src/test/java/org/spsl/evtracker/core/model/LocationSliceTest.kt`:

```kotlin
package org.spsl.evtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSliceTest {

    @Test fun otherKey_setsIsOther() {
        val slice = LocationSlice(LocationSlice.OTHER_KEY, 7)
        assertTrue(slice.isOther)
        assertEquals(7, slice.count)
    }

    @Test fun normalLabel_isNotOther() {
        val slice = LocationSlice("Home", 3)
        assertFalse(slice.isOther)
    }

    @Test fun otherKey_startsWithNul_collisionProofAgainstAnyAndroidInput() {
        // Android EditText strips the NUL char from IME input, so no
        // user-typed location label can ever start with it. The sentinel
        // is therefore collision-proof regardless of any trim() invariant.
        assertEquals(Char(0), LocationSlice.OTHER_KEY[0])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.core.model.LocationSliceTest"
```

Expected: FAIL with "unresolved reference: LocationSlice".

- [ ] **Step 3: Create the four data classes**

`app/src/main/java/org/spsl/evtracker/core/model/EfficiencyPoint.kt`:

```kotlin
package org.spsl.evtracker.core.model

data class EfficiencyPoint(
    val eventTimeMillis: Long,
    val kmPerKwh: Double
)
```

`app/src/main/java/org/spsl/evtracker/core/model/EfficiencySeries.kt`:

```kotlin
package org.spsl.evtracker.core.model

data class EfficiencySeries(
    val acPoints: List<EfficiencyPoint> = emptyList(),
    val dcPoints: List<EfficiencyPoint> = emptyList()
)
```

`app/src/main/java/org/spsl/evtracker/core/model/AcDcSplit.kt`:

```kotlin
package org.spsl.evtracker.core.model

data class AcDcSplit(
    val acCount: Int = 0,
    val dcCount: Int = 0,
    val acKwh: Double = 0.0,
    val dcKwh: Double = 0.0
)
```

`app/src/main/java/org/spsl/evtracker/core/model/LocationSlice.kt`:

```kotlin
package org.spsl.evtracker.core.model

data class LocationSlice(
    val label: String,
    val count: Int
) {
    val isOther: Boolean get() = label == OTHER_KEY

    companion object {
        // Construct via Char(0) so the source file is unambiguous in markdown.
        // Embedding a literal NUL char in a Kotlin string literal would render
        // as a blank space in viewers and silently turn into a real space
        // under copy/paste — breaking the collision-proof contract documented
        // below. `@JvmField val` instead of `const val` because Char(0) is
        // not a compile-time constant expression; cost is one allocation
        // per process.
        @JvmField val OTHER_KEY: String = "${Char(0)}__other__"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.core.model.LocationSliceTest"
```

Expected: 3 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/EfficiencyPoint.kt
git add app/src/main/java/org/spsl/evtracker/core/model/EfficiencySeries.kt
git add app/src/main/java/org/spsl/evtracker/core/model/AcDcSplit.kt
git add app/src/main/java/org/spsl/evtracker/core/model/LocationSlice.kt
git add app/src/test/java/org/spsl/evtracker/core/model/LocationSliceTest.kt
git commit -m "feat(F2): EfficiencyPoint/Series, AcDcSplit, LocationSlice core models"
```

---

## Task 3: `StatsCalculator.detectMixedCurrency`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorMixedCurrencyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorMixedCurrencyTest.kt`:

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorMixedCurrencyTest {

    private val calc = StatsCalculator()

    private fun ev(currency: String? = null, costTotal: Double? = null): ChargeEventEntity =
        ChargeEventEntity(
            id = 0, carId = 1, eventDate = 0L, odometerKm = 0.0, kwhAdded = 1.0,
            chargeType = "AC", costTotal = costTotal, costPerKwh = null,
            currency = currency, location = null, note = "", createdAt = 0L
        )

    @Test fun emptyEvents_notMixed() {
        assertFalse(calc.detectMixedCurrency(emptyList()))
    }

    @Test fun singleCurrency_notMixed() {
        assertFalse(calc.detectMixedCurrency(listOf(
            ev("EUR", 5.0),
            ev("EUR", 7.0),
            ev(null, null)              // uncosted events ignored
        )))
    }

    @Test fun twoCurrencies_isMixed() {
        assertTrue(calc.detectMixedCurrency(listOf(
            ev("EUR", 5.0),
            ev("USD", 7.0)
        )))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorMixedCurrencyTest"
```

Expected: FAIL with "unresolved reference: detectMixedCurrency".

- [ ] **Step 3: Add `detectMixedCurrency` to `StatsCalculator`**

Append to the existing `class StatsCalculator @Inject constructor()` body, before the closing brace, in `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`:

```kotlin
    fun detectMixedCurrency(events: List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>): Boolean =
        events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct().size > 1
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorMixedCurrencyTest"
```

Expected: 3 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt
git add app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorMixedCurrencyTest.kt
git commit -m "feat(F2): StatsCalculator.detectMixedCurrency"
```

---

## Task 4: `StatsCalculator.computeEfficiencyTrend`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTrendTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTrendTest.kt`:

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorTrendTest {

    private val calc = StatsCalculator()

    private fun ev(
        date: Long,
        odometerKm: Double,
        kwhAdded: Double = 10.0,
        chargeType: String = "AC"
    ) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = date, odometerKm = odometerKm,
        kwhAdded = kwhAdded, chargeType = chargeType, costTotal = null,
        costPerKwh = null, currency = null, location = null, note = "",
        createdAt = 0L
    )

    @Test fun emptyEvents_returnsEmptySeries() {
        val s = calc.computeEfficiencyTrend(emptyList())
        assertTrue(s.acPoints.isEmpty())
        assertTrue(s.dcPoints.isEmpty())
    }

    @Test fun singleAcEvent_emptySeries() {
        val s = calc.computeEfficiencyTrend(listOf(ev(100L, 0.0, chargeType = "AC")))
        assertTrue(s.acPoints.isEmpty())
        assertTrue(s.dcPoints.isEmpty())
    }

    @Test fun acAndDcEvents_partitionedCorrectly() {
        val s = calc.computeEfficiencyTrend(listOf(
            ev(100L, 0.0,   10.0, "AC"),
            ev(200L, 50.0,  10.0, "AC"),     // 50 km / 10 kWh = 5.0
            ev(150L, 0.0,   10.0, "DC"),
            ev(300L, 80.0,  10.0, "DC")      // 80 km / 10 kWh = 8.0
        ))
        assertEquals(1, s.acPoints.size)
        assertEquals(5.0, s.acPoints[0].kmPerKwh, 0.0001)
        assertEquals(200L, s.acPoints[0].eventTimeMillis)
        assertEquals(1, s.dcPoints.size)
        assertEquals(8.0, s.dcPoints[0].kmPerKwh, 0.0001)
        assertEquals(300L, s.dcPoints[0].eventTimeMillis)
    }

    @Test fun negativeOdometerDelta_skipped() {
        val s = calc.computeEfficiencyTrend(listOf(
            ev(100L, 100.0, 10.0, "AC"),
            ev(200L,  50.0, 10.0, "AC")     // odo went backwards → skip
        ))
        assertTrue(s.acPoints.isEmpty())
    }

    @Test fun zeroKwh_skipped() {
        val s = calc.computeEfficiencyTrend(listOf(
            ev(100L,  0.0, 10.0, "AC"),
            ev(200L, 50.0,  0.0, "AC")      // kwh=0 → skip
        ))
        assertTrue(s.acPoints.isEmpty())
    }

    @Test fun mixedTypeOrder_eachSeriesSortedIndependently() {
        // Inserted out of date order across types; each series must sort by
        // its own dates before computing deltas.
        val s = calc.computeEfficiencyTrend(listOf(
            ev(300L,  60.0, 10.0, "AC"),
            ev(100L,   0.0, 10.0, "AC"),
            ev(200L,  20.0, 10.0, "AC"),
            ev(150L,   0.0, 10.0, "DC"),
            ev(250L,  30.0, 10.0, "DC")
        ))
        assertEquals(2, s.acPoints.size)
        assertEquals(2.0, s.acPoints[0].kmPerKwh, 0.0001)   // 100→200: 20 km
        assertEquals(4.0, s.acPoints[1].kmPerKwh, 0.0001)   // 200→300: 40 km
        assertEquals(1, s.dcPoints.size)
        assertEquals(3.0, s.dcPoints[0].kmPerKwh, 0.0001)   // 150→250: 30 km
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorTrendTest"
```

Expected: FAIL with "unresolved reference: computeEfficiencyTrend".

- [ ] **Step 3: Add `computeEfficiencyTrend` to `StatsCalculator`**

Append inside the same class body in `StatsCalculator.kt`:

```kotlin
    fun computeEfficiencyTrend(events: List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>): org.spsl.evtracker.core.model.EfficiencySeries {
        fun seriesFor(type: String): List<org.spsl.evtracker.core.model.EfficiencyPoint> {
            val sorted = events.filter { it.chargeType == type }.sortedBy { it.eventDate }
            val out = ArrayList<org.spsl.evtracker.core.model.EfficiencyPoint>(sorted.size)
            for (i in 1 until sorted.size) {
                val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
                if (dist > 0 && sorted[i].kwhAdded > 0.0) {
                    out += org.spsl.evtracker.core.model.EfficiencyPoint(
                        eventTimeMillis = sorted[i].eventDate,
                        kmPerKwh = dist / sorted[i].kwhAdded
                    )
                }
            }
            return out
        }
        return org.spsl.evtracker.core.model.EfficiencySeries(
            acPoints = seriesFor("AC"),
            dcPoints = seriesFor("DC")
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorTrendTest"
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt
git add app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTrendTest.kt
git commit -m "feat(F2): StatsCalculator.computeEfficiencyTrend (per-series partition)"
```

---

## Task 5: `StatsCalculator.computeAcDcSplit`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorAcDcSplitTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorAcDcSplitTest.kt`:

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorAcDcSplitTest {

    private val calc = StatsCalculator()

    private fun ev(type: String, kwh: Double) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = 0L, odometerKm = 0.0, kwhAdded = kwh,
        chargeType = type, costTotal = null, costPerKwh = null,
        currency = null, location = null, note = "", createdAt = 0L
    )

    @Test fun emptyEvents_zeroSplit() {
        val s = calc.computeAcDcSplit(emptyList())
        assertEquals(0, s.acCount); assertEquals(0, s.dcCount)
        assertEquals(0.0, s.acKwh, 0.0001); assertEquals(0.0, s.dcKwh, 0.0001)
    }

    @Test fun onlyAc_returnsZeroDc() {
        val s = calc.computeAcDcSplit(listOf(ev("AC", 5.0), ev("AC", 7.0)))
        assertEquals(2, s.acCount); assertEquals(0, s.dcCount)
        assertEquals(12.0, s.acKwh, 0.0001); assertEquals(0.0, s.dcKwh, 0.0001)
    }

    @Test fun mixed_correctTotals() {
        val s = calc.computeAcDcSplit(listOf(
            ev("AC", 5.0), ev("DC", 30.0), ev("AC", 7.0), ev("DC", 50.0)
        ))
        assertEquals(2, s.acCount); assertEquals(2, s.dcCount)
        assertEquals(12.0, s.acKwh, 0.0001); assertEquals(80.0, s.dcKwh, 0.0001)
    }

    @Test fun kwhSumsCorrect_evenWithFractional() {
        val s = calc.computeAcDcSplit(listOf(
            ev("AC", 0.5), ev("AC", 0.25), ev("DC", 0.125)
        ))
        assertEquals(0.75, s.acKwh, 0.0001)
        assertEquals(0.125, s.dcKwh, 0.0001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorAcDcSplitTest"
```

Expected: FAIL with "unresolved reference: computeAcDcSplit".

- [ ] **Step 3: Add `computeAcDcSplit` to `StatsCalculator`**

Append inside the same class body in `StatsCalculator.kt`:

```kotlin
    fun computeAcDcSplit(events: List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>): org.spsl.evtracker.core.model.AcDcSplit {
        val ac = events.filter { it.chargeType == "AC" }
        val dc = events.filter { it.chargeType == "DC" }
        return org.spsl.evtracker.core.model.AcDcSplit(
            acCount = ac.size,
            dcCount = dc.size,
            acKwh   = ac.sumOf { it.kwhAdded },
            dcKwh   = dc.sumOf { it.kwhAdded }
        )
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorAcDcSplitTest"
```

Expected: 4 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt
git add app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorAcDcSplitTest.kt
git commit -m "feat(F2): StatsCalculator.computeAcDcSplit"
```

---

## Task 6: `StatsCalculator.computeLocationDistribution`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorLocationDistTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorLocationDistTest.kt`:

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.LocationSlice
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorLocationDistTest {

    private val calc = StatsCalculator()

    private fun ev(location: String?) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = 0L, odometerKm = 0.0, kwhAdded = 1.0,
        chargeType = "AC", costTotal = null, costPerKwh = null,
        currency = null, location = location, note = "", createdAt = 0L
    )

    @Test fun emptyEvents_returnsEmpty() {
        assertTrue(calc.computeLocationDistribution(emptyList()).isEmpty())
    }

    @Test fun nullAndBlankLocations_excluded() {
        assertTrue(calc.computeLocationDistribution(listOf(
            ev(null), ev(""), ev("   "), ev("\t")
        )).isEmpty())
    }

    @Test fun singleLocation_oneSlice() {
        val r = calc.computeLocationDistribution(listOf(ev("Home"), ev("Home"), ev("Home")))
        assertEquals(1, r.size)
        assertEquals("Home", r[0].label)
        assertEquals(3, r[0].count)
    }

    @Test fun nineLocations_collapsesToTopEightPlusOther() {
        val events = (1..9).flatMap { i ->
            // i appearances of "L<i>" so the ranking is L9, L8, ..., L1
            List(i) { ev("L$i") }
        }
        val r = calc.computeLocationDistribution(events)
        assertEquals(9, r.size)                        // top 8 + Other
        assertEquals("L9", r[0].label); assertEquals(9, r[0].count)
        assertEquals("L2", r[7].label); assertEquals(2, r[7].count)
        assertTrue(r[8].isOther)
        assertEquals(1, r[8].count)                    // tail = just L1 (count 1)
    }

    @Test fun tieBreaking_byInsertionOrder() {
        // groupingBy preserves first-seen order on ties; the spec leaves tie
        // ordering implementation-defined. This test pins current behaviour
        // so a future refactor that changes it surfaces here.
        val r = calc.computeLocationDistribution(listOf(
            ev("First"), ev("Second"), ev("First"), ev("Second")
        ))
        assertEquals(2, r.size)
        assertEquals("First", r[0].label)
        assertEquals(2, r[0].count)
        assertEquals("Second", r[1].label)
    }

    @Test fun trim_caseSensitive() {
        // "Home" and "home" are different labels (case-sensitive grouping).
        // Leading/trailing whitespace is stripped before grouping.
        val r = calc.computeLocationDistribution(listOf(
            ev("Home"), ev(" Home "), ev("home")
        ))
        assertEquals(2, r.size)
        assertEquals("Home", r[0].label)
        assertEquals(2, r[0].count)              // "Home" + " Home " merged
        assertEquals("home", r[1].label)
        assertEquals(1, r[1].count)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorLocationDistTest"
```

Expected: FAIL with "unresolved reference: computeLocationDistribution".

- [ ] **Step 3: Add `computeLocationDistribution` + cap constant**

Append inside the same class body in `StatsCalculator.kt`:

```kotlin
    fun computeLocationDistribution(events: List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>): List<org.spsl.evtracker.core.model.LocationSlice> {
        val counts = events
            .mapNotNull { it.location?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        if (counts.isEmpty()) return emptyList()
        val ranked = counts.entries.sortedByDescending { it.value }
        val top = ranked.take(MAX_LOCATION_SLICES)
            .map { org.spsl.evtracker.core.model.LocationSlice(it.key, it.value) }
        val tail = ranked.drop(MAX_LOCATION_SLICES)
        return if (tail.isEmpty()) top
               else top + org.spsl.evtracker.core.model.LocationSlice(
                   org.spsl.evtracker.core.model.LocationSlice.OTHER_KEY,
                   tail.sumOf { it.value }
               )
    }

    companion object {
        // Cap chosen so the pie chart stays legible on phone widths;
        // see spec §6.5 / §10. Tweaking is a code change, not a localization change.
        const val MAX_LOCATION_SLICES = 8
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculatorLocationDistTest"
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt
git add app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorLocationDistTest.kt
git commit -m "feat(F2): StatsCalculator.computeLocationDistribution + 8-slice cap"
```

---

## Task 7: Extend testing fakes — subscribe-count + currency setter

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

Two tooling changes: (a) `FakeChargeEventQueries.observeForCarCallCount` is consumed by Tasks 10/11; (b) `FakeSettingsReader.setCurrency(...)` is consumed by Task 11's `costLabelDoesNotReadSettingsCurrency` (the existing fake exposes setters for activeCarId/distanceUnit/primaryMetric/theme but not currency). Bundling both into one prep task keeps Tasks 10/11 atomic.

- [ ] **Step 1: Open the file**

Read `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` to confirm the current shapes of `FakeChargeEventQueries` and `FakeSettingsReader`.

- [ ] **Step 2: Add the counter to `FakeChargeEventQueries`**

Replace the existing `class FakeChargeEventQueries` declaration in `Fakes.kt` with:

```kotlin
class FakeChargeEventQueries(
    private val store: MutableStateFlow<List<ChargeEventEntity>> = MutableStateFlow(emptyList())
) : ChargeEventQueries {

    /** Incremented every time observeForCar(...) is called, regardless of carId.
     *  Used by ChartsViewModelTest.distanceUnitChange_doesNotResubscribeEventStream
     *  to assert that flipping rendering inputs does not tear down the inner Room
     *  subscription. */
    @Volatile var observeForCarCallCount: Int = 0
        private set

    override fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>> {
        observeForCarCallCount++
        return store.map { it.filter { e -> e.carId == carId }.sortedBy { e -> e.eventDate } }
    }
    override suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId && it.eventDate in from..to }.sortedBy { it.eventDate }
    override suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId }.sortedBy { it.eventDate }
    override suspend fun getById(id: Int) = store.value.firstOrNull { it.id == id }
    fun seed(events: List<ChargeEventEntity>) { store.value = events }
    fun current(): List<ChargeEventEntity> = store.value
    fun shareStore(): MutableStateFlow<List<ChargeEventEntity>> = store
}
```

- [ ] **Step 3: Add `setCurrency` to `FakeSettingsReader`**

Inside the existing `FakeSettingsReader` class body in the same file, locate the block of setters (`setActiveCarId`, `setDriveEnabled`, `setTheme`, `setPrimaryMetric`, `setDistanceUnit`, `setResetInProgress`) and append:

```kotlin
    fun setCurrency(value: String) { curr.value = value }
```

- [ ] **Step 4: Build the JVM test source set**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`. (No tests run; this is a compile check that existing `FakeChargeEventQueries` and `FakeSettingsReader` consumers still type-check.)

- [ ] **Step 5: Run the existing tests that exercise the fake**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCaseTest"
```

Expected: all existing Dashboard use-case tests still pass — confirms the additions are backward-compatible.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "test(F2): add observeForCarCallCount + setCurrency to fakes"
```

---

## Task 8: `NowProvider` + `AggregationDispatcher` qualifier + `DispatcherModule`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/NowProvider.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt`

No tests for the DI layer itself; correctness is observed through the use-case tests in Task 10. We do verify the assemble step succeeds so KSP/Hilt are happy with the new module.

- [ ] **Step 1: Create `NowProvider`**

`app/src/main/java/org/spsl/evtracker/domain/usecase/NowProvider.kt`:

```kotlin
package org.spsl.evtracker.domain.usecase

/**
 * Clock seam used by use cases that need to compute time-relative ranges (e.g. rolling
 * "Last 12 months"). JVM tests inject a fixed lambda so rolling-window tests are
 * deterministic; production binding (DispatcherModule) returns System.currentTimeMillis().
 */
fun interface NowProvider {
    fun nowMillis(): Long
}
```

- [ ] **Step 2: Create the qualifier annotation**

`app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`:

```kotlin
package org.spsl.evtracker.di

import javax.inject.Qualifier

/**
 * Qualifier for the CoroutineContext used to perform off-main aggregation work
 * inside use cases (e.g. ObserveChartsModelsUseCase). Production binds this to
 * Dispatchers.Default; JVM tests pass EmptyCoroutineContext so flowOn becomes
 * a no-op and the test scheduler stays in control.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AggregationDispatcher
```

- [ ] **Step 3: Create `DispatcherModule`**

`app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt`:

```kotlin
package org.spsl.evtracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import org.spsl.evtracker.domain.usecase.NowProvider

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @AggregationDispatcher
    fun provideAggregationContext(): CoroutineContext = Dispatchers.Default

    @Provides
    @Singleton
    fun provideNowProvider(): NowProvider = NowProvider { System.currentTimeMillis() }
}
```

- [ ] **Step 4: Build to confirm Hilt is happy**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (Hilt + KSP regenerates the component without errors.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/NowProvider.kt
git add app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt
git add app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt
git commit -m "feat(F2): NowProvider + @AggregationDispatcher + DispatcherModule"
```

---

## Task 9: `ChartsUiState` + `ChartsScreenState` + `ChartsEvent`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/ChartsUiState.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/ChartsScreenState.kt`

Smoke test in `ChartsViewModelTest.kt` exercises these via the VM (Task 11). No dedicated test here.

- [ ] **Step 1: Create `ChartsUiState.kt`**

`app/src/main/java/org/spsl/evtracker/core/model/ChartsUiState.kt`:

```kotlin
package org.spsl.evtracker.core.model

sealed class ChartsUiState {

    object Loading : ChartsUiState()

    /** No rows in cars OR activeCarId == -1. Period chips and TabLayout are hidden. */
    object NoCar : ChartsUiState()

    /** Active car exists but the per-car charge_events stream is empty. Period chips
     *  and TabLayout are hidden; full-screen "Log charge" CTA shown. */
    object NoEvents : ChartsUiState()

    data class Loaded(
        val periodHasEvents: Boolean,
        val mixedCurrency: Boolean,
        val periodCurrency: String?,
        /** Start of the resolved period window, used by the trend tab to express the
         *  Line chart's x-axis as a day offset from this anchor. Storing raw
         *  epoch millis as a Float would alias because Float only has ~7 decimal
         *  digits of integer precision while modern timestamps need ~13. */
        val periodStartMillis: Long,
        val trend: EfficiencySeries,
        val monthlyKwh: List<MonthBucket>,
        val monthlyCost: List<MonthBucket>,
        val acDc: AcDcSplit,
        val locations: List<LocationSlice>
    ) : ChartsUiState()
}
```

- [ ] **Step 2: Create `ChartsScreenState.kt`**

`app/src/main/java/org/spsl/evtracker/core/model/ChartsScreenState.kt`:

```kotlin
package org.spsl.evtracker.core.model

data class ChartsScreenState(
    val period: ChartsPeriod = ChartsPeriod.Last12Months,
    /** "km" | "miles". Drives trend Y-axis label and km↔miles render-time conversion. */
    val distanceUnit: String = "km",
    val charts: ChartsUiState = ChartsUiState.Loading
)

sealed class ChartsEvent {
    object OpenCustomRangePicker : ChartsEvent()
    object NavigateToCars : ChartsEvent()
    object NavigateToChargeEdit : ChartsEvent()
}
```

- [ ] **Step 3: Build to confirm**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/ChartsUiState.kt
git add app/src/main/java/org/spsl/evtracker/core/model/ChartsScreenState.kt
git commit -m "feat(F2): ChartsUiState + ChartsScreenState + ChartsEvent"
```

---

## Task 10: `ObserveChartsModelsUseCase`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt`
- Test:   `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCaseTest.kt`:

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveChartsModelsUseCaseTest {

    // 2024-04-25T08:00Z anchor; "Last 12 months" → 2023-04-25 onwards
    private val nowMs = 1_714_032_000_000L
    private val now = NowProvider { nowMs }

    private fun setup(
        cars: List<CarEntity> = listOf(CarEntity(id = 1, name = "C")),
        activeCarId: Int = 1,
        events: List<ChargeEventEntity> = emptyList()
    ): ObserveChartsModelsUseCase {
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries().apply { seed(events) }
        val settings = FakeSettingsReader(activeCarIdInit = activeCarId)
        return ObserveChartsModelsUseCase(
            carReader = carReader,
            chargeEventQueries = queries,
            settingsReader = settings,
            statsCalculator = StatsCalculator(),
            dateRangeResolver = DateRangeResolver(),
            now = now,
            aggregationContext = EmptyCoroutineContext
        )
    }

    private fun ev(
        date: Long, odo: Double, kwh: Double = 10.0, type: String = "AC",
        cost: Double? = null, currency: String? = null, location: String? = null
    ) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = date, odometerKm = odo, kwhAdded = kwh,
        chargeType = type, costTotal = cost, costPerKwh = null,
        currency = currency, location = location, note = "", createdAt = 0L
    )

    @Test fun noCar_emitsNoCar() = runTest {
        val state = setup(cars = emptyList(), activeCarId = -1)
            .observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.NoCar)
    }

    @Test fun activeCarMinusOne_emitsNoCar() = runTest {
        val state = setup(activeCarId = -1).observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.NoCar)
    }

    @Test fun noEvents_emitsNoEvents() = runTest {
        val state = setup(events = emptyList()).observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.NoEvents)
    }

    @Test fun eventsOutsidePeriod_emitsLoadedWithPeriodHasEventsFalse() = runTest {
        // event from ~2 years before nowMs — outside Last12Months
        val twoYearsAgo = nowMs - 2L * 365 * 24 * 60 * 60 * 1000
        val state = setup(events = listOf(ev(twoYearsAgo, 0.0)))
            .observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.Loaded)
        val loaded = state as ChartsUiState.Loaded
        assertFalse(loaded.periodHasEvents)
        assertTrue(loaded.trend.acPoints.isEmpty())
        assertTrue(loaded.monthlyKwh.isEmpty())
        // Pin periodStartMillis to the resolver's Last12Months lower bound
        // (used by the trend tab's day-offset x-axis math).
        assertEquals(nowMs - 365L * 24 * 60 * 60 * 1000, loaded.periodStartMillis)
    }

    @Test fun eventsInPeriod_singleCurrency_emitsAllSeriesAndPeriodCurrency() = runTest {
        // Two AC events 30 days apart, both EUR-costed
        val ms30d = 30L * 24 * 60 * 60 * 1000
        val state = setup(events = listOf(
            ev(nowMs - 2 * ms30d, 0.0,   10.0, "AC", cost = 5.0, currency = "EUR", location = "Home"),
            ev(nowMs - 1 * ms30d, 100.0, 10.0, "AC", cost = 7.5, currency = "EUR", location = "Home")
        )).observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.Loaded)
        val l = state as ChartsUiState.Loaded
        assertTrue(l.periodHasEvents)
        assertFalse(l.mixedCurrency)
        assertEquals("EUR", l.periodCurrency)
        assertEquals(1, l.trend.acPoints.size)
        assertTrue(l.monthlyKwh.isNotEmpty())
        assertTrue(l.monthlyCost.isNotEmpty())
        assertEquals(2, l.acDc.acCount)
        assertEquals(1, l.locations.size)
        assertEquals("Home", l.locations[0].label)
    }

    @Test fun eventsInPeriod_mixedCurrency_zeroesMonthlyCostAndPeriodCurrencyNull() = runTest {
        val ms30d = 30L * 24 * 60 * 60 * 1000
        val state = setup(events = listOf(
            ev(nowMs - 2 * ms30d, 0.0,   10.0, "AC", cost = 5.0, currency = "EUR"),
            ev(nowMs - 1 * ms30d, 100.0, 10.0, "AC", cost = 7.5, currency = "USD")
        )).observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.Loaded)
        val l = state as ChartsUiState.Loaded
        assertTrue(l.mixedCurrency)
        assertNull(l.periodCurrency)
        assertTrue(l.monthlyCost.isEmpty())
        assertTrue(l.monthlyKwh.isNotEmpty())          // kWh series unaffected
    }

    @Test fun differentPeriodArg_producesDifferentBuild() = runTest {
        // event from ~9 months ago — inside Last12Months, outside Last6Months
        val nineMonthsAgo = nowMs - 270L * 24 * 60 * 60 * 1000
        val useCase = setup(events = listOf(ev(nineMonthsAgo, 0.0)))

        val a = useCase.observe(ChartsPeriod.Last6Months).first() as ChartsUiState.Loaded
        val b = useCase.observe(ChartsPeriod.Last12Months).first() as ChartsUiState.Loaded
        assertFalse(a.periodHasEvents)
        assertTrue(b.periodHasEvents)
    }

    @Test fun carSwitch_resetsState() = runTest {
        val carReader = FakeCarReader(listOf(
            CarEntity(id = 1, name = "A"),
            CarEntity(id = 2, name = "B")
        ))
        val queries = FakeChargeEventQueries().apply {
            seed(listOf(ev(nowMs - 100, 0.0).copy(carId = 1)))
        }
        val settings = FakeSettingsReader(activeCarIdInit = 1)
        val useCase = ObserveChartsModelsUseCase(
            carReader, queries, settings, StatsCalculator(), DateRangeResolver(),
            now = now, aggregationContext = EmptyCoroutineContext
        )
        val first = useCase.observe(ChartsPeriod.Last12Months).first()
        assertTrue(first is ChartsUiState.Loaded)

        settings.setActiveCarId(2)
        val second = useCase.observe(ChartsPeriod.Last12Months).first()
        assertTrue(second is ChartsUiState.NoEvents)   // car 2 has no events
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCaseTest"
```

Expected: FAIL with "unresolved reference: ObserveChartsModelsUseCase".

- [ ] **Step 3: Implement the use case**

Create `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt`:

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.di.AggregationDispatcher
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator

class ObserveChartsModelsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val dateRangeResolver: DateRangeResolver,
    private val now: NowProvider,
    @AggregationDispatcher private val aggregationContext: CoroutineContext
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: ChartsPeriod): Flow<ChartsUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { active, cars ->
            active to cars
        }.flatMapLatest { (active, cars) ->
            when {
                cars.isEmpty() || active == -1 -> flowOf<ChartsUiState>(ChartsUiState.NoCar)
                else -> chargeEventQueries.observeForCar(active).map { all ->
                    if (all.isEmpty()) ChartsUiState.NoEvents
                    else build(all, period)
                }
            }
        }.flowOn(aggregationContext)
    }

    private fun build(allEvents: List<ChargeEventEntity>, period: ChartsPeriod): ChartsUiState.Loaded {
        val range = dateRangeResolver.resolveCharts(period, now.nowMillis())
        val periodEvents = allEvents.filter { it.eventDate in range.startMillis..range.endMillis }
        val mixed = statsCalculator.detectMixedCurrency(periodEvents)
        val monthly = statsCalculator.computeMonthlyBuckets(periodEvents)
        val costBuckets = if (mixed) emptyList() else monthly.filter { it.totalCost != null }
        val resolvedCurrency = if (mixed) null else costBuckets.firstNotNullOfOrNull { it.currency }
        return ChartsUiState.Loaded(
            periodHasEvents = periodEvents.isNotEmpty(),
            mixedCurrency = mixed,
            periodCurrency = resolvedCurrency,
            periodStartMillis = range.startMillis,
            trend = statsCalculator.computeEfficiencyTrend(periodEvents),
            monthlyKwh = monthly,
            monthlyCost = costBuckets,
            acDc = statsCalculator.computeAcDcSplit(periodEvents),
            locations = statsCalculator.computeLocationDistribution(periodEvents)
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCaseTest"
```

Expected: 8 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt
git add app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCaseTest.kt
git commit -m "feat(F2): ObserveChartsModelsUseCase with NowProvider clock + flowOn(@AggregationDispatcher)"
```

---

## Task 11: `ChartsViewModel`

**Files:**
- Replace: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsViewModel.kt` (currently a stub)
- Test:    `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt`

This is the biggest unit-test file in the plan. The 11 tests pin: default state, period selection, custom range, flatMapLatest re-aggregation on period change, render-input propagation for distanceUnit, the no-resubscribe contract on distanceUnit (the entire point of separating render inputs from behaviour-driving flow), the cost-label-doesn't-read-settings-currency invariant, the three event emissions (custom-range / nav-cars / nav-chargeEdit), and the SharedFlow `replay = 0` contract.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt`:

```kotlin
package org.spsl.evtracker.ui.charts

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.domain.usecase.NowProvider
import org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCase
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeSettingsReader

/**
 * NOTE on test pattern (matches DashboardViewModelTest.kt:83/134):
 * `ChartsViewModel.uiState` is built with `stateIn(WhileSubscribed(5_000))` so the
 * upstream is only collected while a subscriber is active. Reading `uiState.value`
 * with no subscriber returns the seeded placeholder, NOT what the use case emitted.
 *
 * Tests therefore use one of two patterns:
 *  - `vm.uiState.first { predicate }` for one-shot "wait until state matches" — this
 *    subscribes, waits, and unsubscribes itself.
 *  - A long-running `launch(start = UNDISPATCHED) { vm.uiState.collect(...) }` for
 *    tests that need the upstream to *stay* subscribed across multiple state
 *    transitions (e.g. the no-resubscribe contract). Otherwise WhileSubscribed
 *    can tear down between two `first { }` calls and the second call's resubscription
 *    would inflate `observeForCarCallCount` for the wrong reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartsViewModelTest {

    private val nowMs = 1_714_032_000_000L
    private val now = NowProvider { nowMs }

    private lateinit var carReader: FakeCarReader
    private lateinit var queries: FakeChargeEventQueries
    private lateinit var settings: FakeSettingsReader
    private lateinit var useCase: ObserveChartsModelsUseCase
    private lateinit var vm: ChartsViewModel

    @Before fun setUp() {
        carReader = FakeCarReader(listOf(CarEntity(id = 1, name = "C")))
        queries = FakeChargeEventQueries().apply {
            seed(listOf(ev(nowMs - 100, 0.0)))
        }
        settings = FakeSettingsReader(activeCarIdInit = 1, distanceUnitInit = "km")
        useCase = ObserveChartsModelsUseCase(
            carReader, queries, settings, StatsCalculator(), DateRangeResolver(),
            now = now, aggregationContext = EmptyCoroutineContext
        )
        vm = ChartsViewModel(useCase, settings)
    }

    private fun ev(date: Long, odo: Double) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = date, odometerKm = odo, kwhAdded = 10.0,
        chargeType = "AC", costTotal = null, costPerKwh = null,
        currency = null, location = null, note = "", createdAt = 0L
    )

    /** Helper: keep a permanent subscriber on uiState so WhileSubscribed stays active
     *  across several state transitions in one test. Caller cancels via the returned Job. */
    private suspend fun keepSubscribed(
        scope: kotlinx.coroutines.CoroutineScope,
        sink: MutableList<ChartsScreenState> = mutableListOf()
    ): Pair<Job, MutableList<ChartsScreenState>> {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.uiState.collect { sink += it }
        }
        return job to sink
    }

    @Test fun defaultPeriod_isLast12Months() = runTest {
        // first { it.charts !is Loading } forces the use case to run and emit.
        val state = vm.uiState.first { it.charts !is ChartsUiState.Loading }
        assertEquals(ChartsPeriod.Last12Months, state.period)
    }

    @Test fun selectPeriod_emitsNewState() = runTest {
        // wait for the initial Loaded emission so the next first() definitely sees the
        // post-selectPeriod state, not the seeded placeholder.
        vm.uiState.first { it.charts !is ChartsUiState.Loading }
        vm.selectPeriod(ChartsPeriod.Last6Months)
        val state = vm.uiState.first { it.period == ChartsPeriod.Last6Months }
        assertEquals(ChartsPeriod.Last6Months, state.period)
    }

    @Test fun selectCustomRange_wrapsInCustom() = runTest {
        vm.uiState.first { it.charts !is ChartsUiState.Loading }
        vm.selectCustomRange(100L, 200L)
        val state = vm.uiState.first { it.period is ChartsPeriod.Custom }
        val p = state.period as ChartsPeriod.Custom
        assertEquals(100L, p.fromMillis)
        assertEquals(200L, p.toMillis)
    }

    @Test fun periodChange_recomputesViaFlatMapLatest() = runTest {
        val (job, _) = keepSubscribed(this)
        vm.uiState.first { it.charts !is ChartsUiState.Loading }
        val before = queries.observeForCarCallCount

        vm.selectPeriod(ChartsPeriod.Last6Months)
        // Wait for the new state with the new period to arrive.
        vm.uiState.first { it.period == ChartsPeriod.Last6Months }
        advanceUntilIdle()

        val after = queries.observeForCarCallCount
        job.cancel()
        // Each period change re-runs the flatMapLatest which subscribes anew.
        assertTrue("Expected resubscribe; before=$before after=$after", after > before)
    }

    @Test fun distanceUnitChange_propagatesToScreenState() = runTest {
        vm.uiState.first { it.charts !is ChartsUiState.Loading && it.distanceUnit == "km" }
        settings.setDistanceUnit("miles")
        val state = vm.uiState.first { it.distanceUnit == "miles" }
        assertEquals("miles", state.distanceUnit)
    }

    @Test fun distanceUnitChange_doesNotResubscribeEventStream() = runTest {
        val (job, _) = keepSubscribed(this)
        // Wait for the use case to settle into Loaded (one initial subscription).
        vm.uiState.first { it.charts is ChartsUiState.Loaded }
        val before = queries.observeForCarCallCount

        settings.setDistanceUnit("miles")
        // Wait for the propagation through the outer combine.
        vm.uiState.first { it.distanceUnit == "miles" }
        advanceUntilIdle()

        val after = queries.observeForCarCallCount
        job.cancel()
        // Render-input changes must not tear down the inner Room subscription.
        assertEquals(before, after)
    }

    @Test fun costLabelDoesNotReadSettingsCurrency() = runTest {
        val (job, _) = keepSubscribed(this)
        val firstLoaded = vm.uiState.first { it.charts is ChartsUiState.Loaded }
        val beforeCharts = firstLoaded.charts
        val beforeCount = queries.observeForCarCallCount

        // Flip the preference currency. Because Charts derives its cost label
        // from event data (periodCurrency on ChartsUiState.Loaded), this MUST NOT
        // cause a re-aggregation or change `charts` state identity.
        settings.setCurrency("USD")
        advanceUntilIdle()

        // (a) The Room subscription is not torn down.
        assertEquals(beforeCount, queries.observeForCarCallCount)
        // (b) The aggregated charts payload is the same object — the VM's outer
        // combine never re-emitted ChartsUiState because settings.currency is
        // not in the chain.
        assertSame(beforeCharts, vm.uiState.value.charts)
        job.cancel()
    }

    @Test fun onCustomChipClicked_emitsOpenCustomRangePicker() = runTest {
        val received = mutableListOf<ChartsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onCustomChipClicked()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.first() is ChartsEvent.OpenCustomRangePicker)
    }

    @Test fun onAddCarCta_emitsNavigateToCars() = runTest {
        val received = mutableListOf<ChartsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onAddCarCta()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.first() is ChartsEvent.NavigateToCars)
    }

    @Test fun onLogChargeCta_emitsNavigateToChargeEdit() = runTest {
        val received = mutableListOf<ChartsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onLogChargeCta()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.first() is ChartsEvent.NavigateToChargeEdit)
    }

    @Test fun events_replayIsZero_noReplayOnLateCollector() = runTest {
        // Emit before any collector is attached. With replay = 0, this event
        // must NOT be replayed to a later collector.
        vm.onAddCarCta()
        advanceUntilIdle()

        val received = mutableListOf<ChartsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        advanceUntilIdle()
        job.cancel()
        assertEquals(0, received.size)

        // And ensure subsequent emissions still reach the new collector.
        val received2 = mutableListOf<ChartsEvent>()
        val job2 = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received2 += it }
        }
        vm.onLogChargeCta()
        advanceUntilIdle()
        job2.cancel()
        assertNotEquals(0, received2.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.charts.ChartsViewModelTest"
```

Expected: FAIL with "ChartsViewModel cannot be constructed" / unresolved methods.

- [ ] **Step 3: Implement the ViewModel**

Replace `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsViewModel.kt` with:

```kotlin
package org.spsl.evtracker.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val observeChartsModels: ObserveChartsModelsUseCase,
    settingsReader: SettingsReader
) : ViewModel() {

    private val period = MutableStateFlow<ChartsPeriod>(ChartsPeriod.Last12Months)

    private val _events = MutableSharedFlow<ChartsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChartsEvent> = _events.asSharedFlow()

    // Behaviour-driving flow: only `period` triggers re-subscription / re-aggregation.
    private val chartsFlow: Flow<ChartsUiState> =
        period.flatMapLatest { p -> observeChartsModels.observe(p) }

    // Render inputs (distance unit) join at the outer combine so flipping km/miles
    // rebuilds the screen state without tearing down the Room subscription.
    val uiState: StateFlow<ChartsScreenState> =
        combine(chartsFlow, period, settingsReader.distanceUnit) { ui, p, du ->
            ChartsScreenState(period = p, distanceUnit = du, charts = ui)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsScreenState())

    fun selectPeriod(p: ChartsPeriod) { period.value = p }
    fun selectCustomRange(from: Long, to: Long) {
        period.value = ChartsPeriod.Custom(from, to)
    }
    fun onCustomChipClicked() { _events.tryEmit(ChartsEvent.OpenCustomRangePicker) }
    fun onAddCarCta()         { _events.tryEmit(ChartsEvent.NavigateToCars) }
    fun onLogChargeCta()      { _events.tryEmit(ChartsEvent.NavigateToChargeEdit) }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.charts.ChartsViewModelTest"
```

Expected: 11 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsViewModel.kt
git add app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt
git commit -m "feat(F2): ChartsViewModel — render inputs split from behaviour-driving flow"
```

---

## Task 12: Strings + colors

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Append F2 strings**

Open `app/src/main/res/values/strings.xml` and append (before the closing `</resources>`):

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
    <plurals name="charts_acdc_count_center">
        <item quantity="one">%d charge</item>
        <item quantity="other">%d charges</item>
    </plurals>
```

- [ ] **Step 2: Append F2 colors**

Open `app/src/main/res/values/colors.xml` and append before the closing `</resources>`:

```xml
    <!-- F2 — Chart series fallback colors. Used only when theme attrs (?attr/colorPrimary,
         ?attr/colorTertiary) cannot be resolved. Matches DESIGN §6 "AC blue, DC orange". -->
    <color name="chart_ac_fallback">#1E88E5</color>
    <color name="chart_dc_fallback">#FB8C00</color>
```

- [ ] **Step 3: Build to confirm**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (No string-id collisions.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values/colors.xml
git commit -m "feat(F2): add Charts strings + chart series fallback colors"
```

---

## Task 13: Layout files

**Files:**
- Replace: `app/src/main/res/layout/fragment_charts.xml` (currently a placeholder)
- Create:  `app/src/main/res/layout/fragment_charts_tab.xml`
- Create:  `app/src/main/res/layout/view_chart_marker.xml`

- [ ] **Step 1: Replace `fragment_charts.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:id="@+id/charts_content"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/charts_period_chips"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="8dp"
            app:singleSelection="true"
            app:selectionRequired="true">

            <com.google.android.material.chip.Chip
                android:id="@+id/chip_last_6_months"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/charts_period_last_6_months"/>
            <com.google.android.material.chip.Chip
                android:id="@+id/chip_last_12_months"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:checked="true"
                android:text="@string/charts_period_last_12_months"/>
            <com.google.android.material.chip.Chip
                android:id="@+id/chip_all_time"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/charts_period_all_time"/>
            <com.google.android.material.chip.Chip
                android:id="@+id/chip_custom"
                style="@style/Widget.Material3.Chip.Filter"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/charts_period_custom"/>
        </com.google.android.material.chip.ChipGroup>

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/charts_tab_layout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:tabMode="scrollable"/>

        <androidx.viewpager2.widget.ViewPager2
            android:id="@+id/charts_pager"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"/>
    </LinearLayout>

    <!-- NoCar / NoEvents empty-state container; visible only when ChartsUiState
         is NoCar or NoEvents. Period chips and TabLayout are hidden via
         charts_content visibility in those states. -->
    <LinearLayout
        android:id="@+id/charts_empty_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="32dp"
        android:visibility="gone">

        <TextView
            android:id="@+id/charts_empty_headline"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:gravity="center"/>

        <com.google.android.material.button.MaterialButton
            android:id="@+id/charts_empty_cta"
            style="@style/Widget.Material3.Button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"/>
    </LinearLayout>

    <!-- NOTE: spec §6.3 says the multi-currency banner replaces the body of
         the *Monthly cost* tab only — never globally. The cost tab's empty
         TextView shows R.string.multi_currency_banner when mixedCurrency is
         true; the other four tabs render normally. There is intentionally
         no top-level banner View here. -->
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Create `fragment_charts_tab.xml`**

The tab host is a vertical stack: chart container takes the available height,
optional subtitle TextView sits below (used by the AC/DC tab for the
"X.X kWh AC · X.X kWh DC" sub-label per spec §6.4 — *centered hole text* is the
total event count, *sub-label* is the kWh string). The empty TextView overlays
on top, centered, when `ChartsUiState.Loaded.periodHasEvents` is false or per-tab
data is empty.

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:id="@+id/charts_tab_chart_root"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <FrameLayout
            android:id="@+id/charts_tab_chart_container"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"/>

        <TextView
            android:id="@+id/charts_tab_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="8dp"
            android:gravity="center"
            android:textAppearance="?attr/textAppearanceBodyMedium"
            android:visibility="gone"/>
    </LinearLayout>

    <TextView
        android:id="@+id/charts_tab_empty_message"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:padding="32dp"
        android:gravity="center"
        android:textAppearance="?attr/textAppearanceBodyLarge"
        android:visibility="gone"/>
</FrameLayout>
```

- [ ] **Step 3: Create `view_chart_marker.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:cardElevation="4dp"
    app:cardCornerRadius="6dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="6dp">

        <TextView
            android:id="@+id/marker_date"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelMedium"/>

        <TextView
            android:id="@+id/marker_value"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelLarge"/>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/fragment_charts.xml
git add app/src/main/res/layout/fragment_charts_tab.xml
git add app/src/main/res/layout/view_chart_marker.xml
git commit -m "feat(F2): Charts layouts — host fragment, tab host, marker view"
```

---

## Task 14: `ChartStyling` pure helpers

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartStyling.kt`

- [ ] **Step 1: Create the helper object**

```kotlin
package org.spsl.evtracker.ui.charts

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsPeriod

/**
 * Pure (Context-only) helpers for MPAndroidChart configuration. Keeps Fragment
 * code thin and JVM-testable for everything except color resolution which
 * intrinsically needs a Context.
 */
object ChartStyling {

    /** Used by the trend tab to express the line chart's x-axis as a day offset
     *  from the period start. Storing raw epoch millis as a Float in Entry.x
     *  aliases because Float has only ~7 decimal digits of integer precision
     *  while modern timestamps need ~13. Day offsets stay well within Float
     *  precision (a 20-year window is ~7300 days). */
    const val MILLIS_PER_DAY = 86_400_000L

    private val LOCATION_PALETTE = intArrayOf(
        0xFF1E88E5.toInt(),
        0xFFFB8C00.toInt(),
        0xFF43A047.toInt(),
        0xFF8E24AA.toInt(),
        0xFF00ACC1.toInt(),
        0xFFE53935.toInt(),
        0xFFFDD835.toInt(),
        0xFF6D4C41.toInt(),
        0xFF757575.toInt()      // "Other" slot — neutral grey
    )

    fun resolveSeriesColors(context: Context): Pair<Int, Int> {
        fun resolve(attr: Int, fallback: Int): Int {
            val tv = TypedValue()
            return if (context.theme.resolveAttribute(attr, tv, true)) tv.data
                   else ContextCompat.getColor(context, fallback)
        }
        val ac = resolve(com.google.android.material.R.attr.colorPrimary, R.color.chart_ac_fallback)
        val dc = resolve(com.google.android.material.R.attr.colorTertiary, R.color.chart_dc_fallback)
        return ac to dc
    }

    fun configureLineChart(chart: LineChart, distanceUnit: String) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setAvoidFirstLastClipping(true)
    }

    fun configureBarChart(chart: BarChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.setFitBars(true)
    }

    fun configurePieChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.legend.isEnabled = true
        chart.setUsePercentValues(false)
        chart.setEntryLabelColor(Color.WHITE)
        chart.isRotationEnabled = false
        chart.setHoleColor(Color.TRANSPARENT)
    }

    /** Formatter for monthly bar charts. Bars store the bucket *index* in
     *  Entry.x (values are 0f, 1f, 2f, ... — no Float aliasing). The
     *  formatter looks up the bucket at that index and renders its calendar
     *  month/year. Calling code passes the bucket list once per chart build;
     *  the closure captures it. */
    fun monthBucketFormatter(buckets: List<org.spsl.evtracker.core.model.MonthBucket>): IAxisValueFormatter {
        val fmt = SimpleDateFormat("MMM yy", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        return object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float): String {
                if (buckets.isEmpty()) return ""
                val i = value.toInt().coerceIn(0, buckets.lastIndex)
                val b = buckets[i]
                cal.clear()
                cal.set(b.year, b.month - 1, 1, 0, 0, 0)
                return fmt.format(Date(cal.timeInMillis))
            }
        }
    }

    /** The x value passed in is a *day offset from windowStartMillis*, not an epoch
     *  millis. The formatter reconstructs the absolute date for labelling. */
    fun dateLabelFormatter(windowStartMillis: Long, period: ChartsPeriod): IAxisValueFormatter {
        val pattern = if (period is ChartsPeriod.AllTime) "MMM yy" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float): String {
                val millis = windowStartMillis + (value.toDouble() * MILLIS_PER_DAY).toLong()
                return fmt.format(Date(millis))
            }
        }
    }

    fun locationPalette(slot: Int): Int =
        LOCATION_PALETTE[slot.coerceIn(0, LOCATION_PALETTE.size - 1)]
}
```

- [ ] **Step 2: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartStyling.kt
git commit -m "feat(F2): ChartStyling — axis configs, color resolution, formatters, palette"
```

---

## Task 15: `ChartsMarkerView`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsMarkerView.kt`

- [ ] **Step 1: Create the marker view**

```kotlin
package org.spsl.evtracker.ui.charts

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.spsl.evtracker.R

/**
 * Tap-to-inspect marker for Line and Bar charts. Anchors above the data point.
 * Bar markers receive an Entry whose x is the bucket index — not a millis value
 * — so callers must provide an Entry with `data` set to the millis when needed.
 */
class ChartsMarkerView(context: Context, valueSuffix: String) : MarkerView(context, R.layout.view_chart_marker) {

    private val dateLabel: TextView = findViewById(R.id.marker_date)
    private val valueLabel: TextView = findViewById(R.id.marker_value)
    private val dateFmt = SimpleDateFormat("d MMM yy", Locale.getDefault())
    private val suffix = valueSuffix

    override fun refreshContent(e: Entry, highlight: Highlight) {
        val millis = (e.data as? Long) ?: e.x.toLong()
        dateLabel.text = dateFmt.format(Date(millis))
        valueLabel.text = String.format(Locale.getDefault(), "%.2f %s", e.y, suffix)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF =
        MPPointF.getInstance(-(width / 2f), -height.toFloat())
}
```

- [ ] **Step 2: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsMarkerView.kt
git commit -m "feat(F2): ChartsMarkerView — tap-to-inspect for line + bar charts"
```

---

## Task 16: `ChartsTabFragment` (single class with `TabKind`)

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsTabFragment.kt`

`ChartsTabFragment` shares the parent `ChartsFragment`'s `ChartsViewModel` via `viewModels({ requireParentFragment() })`. Each instance receives a `TabKind` argument and renders only the relevant slice of `ChartsScreenState`. Empty-state messages use the strings from Task 12.

- [ ] **Step 1: Create the file**

```kotlin
package org.spsl.evtracker.ui.charts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.databinding.FragmentChartsTabBinding
import org.spsl.evtracker.domain.service.UnitConverter
import java.util.Calendar

@AndroidEntryPoint
class ChartsTabFragment : Fragment() {

    enum class TabKind { TREND, MONTHLY_KWH, MONTHLY_COST, AC_DC, LOCATIONS }

    private var _binding: FragmentChartsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChartsViewModel by viewModels({ requireParentFragment() })

    private val kind: TabKind by lazy {
        TabKind.valueOf(requireArguments().getString(ARG_KIND)!!)
    }

    private var firstRenderConsumed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: ChartsScreenState) {
        val container = binding.chartsTabChartContainer
        val empty = binding.chartsTabEmptyMessage
        val subtitle = binding.chartsTabSubtitle
        container.removeAllViews()
        empty.isVisible = false
        subtitle.isVisible = false        // reset; only AC/DC turns this on

        val charts = state.charts
        if (charts !is ChartsUiState.Loaded) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        if (!charts.periodHasEvents) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }

        when (kind) {
            TabKind.TREND       -> renderTrend(state, charts, container, empty)
            TabKind.MONTHLY_KWH -> renderMonthlyKwh(charts, container, empty)
            TabKind.MONTHLY_COST -> renderMonthlyCost(charts, container, empty)
            TabKind.AC_DC       -> renderAcDc(charts, container, empty, subtitle)
            TabKind.LOCATIONS   -> renderLocations(charts, container, empty)
        }
    }

    private fun renderTrend(
        state: ChartsScreenState,
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView
    ) {
        val ac = charts.trend.acPoints
        val dc = charts.trend.dcPoints
        if (ac.isEmpty() && dc.isEmpty()) {
            empty.text = getString(R.string.charts_trend_need_two)
            empty.isVisible = true
            return
        }
        val chart = LineChart(requireContext())
        ChartStyling.configureLineChart(chart, state.distanceUnit)
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val unitToMi = state.distanceUnit == "miles"
        val windowStart = charts.periodStartMillis
        // x = day offset from windowStart (Float-safe). Real millis stays in Entry.data
        // so the marker view shows the exact date.
        fun toEntries(points: List<org.spsl.evtracker.core.model.EfficiencyPoint>): List<Entry> =
            points.map {
                val y = if (unitToMi) UnitConverter.kmPerKwhToMiPerKwh(it.kmPerKwh) else it.kmPerKwh
                val xDays = ((it.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY).toFloat()
                Entry(xDays, y.toFloat(), it.eventTimeMillis as Any)
            }
        val sets = mutableListOf<LineDataSet>()
        if (ac.isNotEmpty()) {
            sets += LineDataSet(toEntries(ac), getString(R.string.charts_trend_legend_ac)).apply {
                color = acColor; setCircleColor(acColor); valueTextSize = 0f
            }
        }
        if (dc.isNotEmpty()) {
            sets += LineDataSet(toEntries(dc), getString(R.string.charts_trend_legend_dc)).apply {
                color = dcColor; setCircleColor(dcColor); valueTextSize = 0f
            }
        }
        chart.data = LineData(sets.toList())
        chart.xAxis.valueFormatter = ChartStyling.dateLabelFormatter(windowStart, state.period)
        val unitSuffix = if (unitToMi)
            getString(R.string.charts_trend_y_mi) else getString(R.string.charts_trend_y_kmh)
        chart.marker = ChartsMarkerView(requireContext(), unitSuffix)
        if (!firstRenderConsumed) { chart.animateY(400); firstRenderConsumed = true }
        container.addView(chart, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderMonthlyKwh(
        charts: ChartsUiState.Loaded, container: FrameLayout, empty: TextView
    ) {
        if (charts.monthlyKwh.isEmpty()) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        val chart = BarChart(requireContext())
        ChartStyling.configureBarChart(chart)
        val (primary, _) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = charts.monthlyKwh.mapIndexed { i, b ->
            BarEntry(i.toFloat(), b.totalKwh.toFloat(), bucketMillis(b) as Any)
        }
        val ds = BarDataSet(entries, "kWh").apply { color = primary; valueTextSize = 0f }
        chart.data = BarData(ds)
        chart.xAxis.valueFormatter = ChartStyling.monthBucketFormatter(charts.monthlyKwh)
        chart.marker = ChartsMarkerView(requireContext(), "kWh")
        if (!firstRenderConsumed) { chart.animateY(400); firstRenderConsumed = true }
        container.addView(chart, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderMonthlyCost(
        charts: ChartsUiState.Loaded, container: FrameLayout, empty: TextView
    ) {
        if (charts.mixedCurrency) {
            // Banner already shown by parent ChartsFragment; tab body itself shows
            // a short stub so the tab doesn't appear blank when scrolled to.
            empty.text = getString(R.string.multi_currency_banner)
            empty.isVisible = true
            return
        }
        if (charts.monthlyCost.isEmpty()) {
            empty.text = getString(R.string.charts_no_cost_period)
            empty.isVisible = true
            return
        }
        val chart = BarChart(requireContext())
        ChartStyling.configureBarChart(chart)
        val (_, tertiary) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = charts.monthlyCost.mapIndexed { i, b ->
            BarEntry(i.toFloat(), (b.totalCost ?: 0.0).toFloat(), bucketMillis(b) as Any)
        }
        val currency = charts.periodCurrency ?: ""
        val ds = BarDataSet(entries, currency).apply { color = tertiary; valueTextSize = 0f }
        chart.data = BarData(ds)
        chart.xAxis.valueFormatter = ChartStyling.monthBucketFormatter(charts.monthlyCost)
        chart.marker = ChartsMarkerView(requireContext(), currency)
        if (!firstRenderConsumed) { chart.animateY(400); firstRenderConsumed = true }
        container.addView(chart, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderAcDc(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
        subtitle: TextView
    ) {
        val total = charts.acDc.acCount + charts.acDc.dcCount
        if (total == 0) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        val chart = PieChart(requireContext())
        ChartStyling.configurePieChart(chart)
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = listOf(
            PieEntry(charts.acDc.acCount.toFloat(), getString(R.string.charts_trend_legend_ac)),
            PieEntry(charts.acDc.dcCount.toFloat(), getString(R.string.charts_trend_legend_dc))
        )
        val ds = PieDataSet(entries, "").apply {
            colors = listOf(acColor, dcColor); valueTextSize = 12f
        }
        chart.data = PieData(ds)
        // Spec §6.4: centered hole text = total event count; sub-label below = kWh.
        chart.centerText = resources.getQuantityString(
            R.plurals.charts_acdc_count_center, total, total
        )
        subtitle.text = getString(
            R.string.charts_acdc_kwh_subtitle, charts.acDc.acKwh, charts.acDc.dcKwh
        )
        subtitle.isVisible = true
        if (!firstRenderConsumed) { chart.animateY(400); firstRenderConsumed = true }
        container.addView(chart, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun renderLocations(
        charts: ChartsUiState.Loaded, container: FrameLayout, empty: TextView
    ) {
        if (charts.locations.isEmpty()) {
            empty.text = getString(R.string.charts_no_locations_period)
            empty.isVisible = true
            return
        }
        val chart = PieChart(requireContext())
        ChartStyling.configurePieChart(chart)
        val entries = charts.locations.map { slice ->
            val label = if (slice.isOther) getString(R.string.charts_locations_other) else slice.label
            PieEntry(slice.count.toFloat(), label)
        }
        val ds = PieDataSet(entries, "").apply {
            colors = charts.locations.indices.map { ChartStyling.locationPalette(it) }
            valueTextSize = 12f
        }
        chart.data = PieData(ds)
        if (!firstRenderConsumed) { chart.animateY(400); firstRenderConsumed = true }
        container.addView(chart, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun bucketMillis(b: MonthBucket): Long {
        val cal = Calendar.getInstance()
        cal.set(b.year, b.month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // (Monthly x-axis formatting now lives in ChartStyling.monthBucketFormatter.)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_KIND = "kind"
        fun newInstance(kind: TabKind): ChartsTabFragment = ChartsTabFragment().apply {
            arguments = Bundle().apply { putString(ARG_KIND, kind.name) }
        }
    }
}
```

- [ ] **Step 2: Build to confirm**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (ViewBinding will generate `FragmentChartsTabBinding` from the layout in Task 13; the `R.id.charts_tab_chart_container` and `R.id.charts_tab_empty_message` are both present.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsTabFragment.kt
git commit -m "feat(F2): ChartsTabFragment — single class, TabKind arg, parent VM"
```

---

## Task 17: `ChartsPagerAdapter`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsPagerAdapter.kt`

- [ ] **Step 1: Create the adapter**

```kotlin
package org.spsl.evtracker.ui.charts

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ChartsPagerAdapter(host: Fragment) : FragmentStateAdapter(host) {

    private val tabs = listOf(
        ChartsTabFragment.TabKind.TREND,
        ChartsTabFragment.TabKind.MONTHLY_KWH,
        ChartsTabFragment.TabKind.MONTHLY_COST,
        ChartsTabFragment.TabKind.AC_DC,
        ChartsTabFragment.TabKind.LOCATIONS
    )

    fun tabKindAt(position: Int): ChartsTabFragment.TabKind = tabs[position]

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment =
        ChartsTabFragment.newInstance(tabs[position])
}
```

- [ ] **Step 2: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsPagerAdapter.kt
git commit -m "feat(F2): ChartsPagerAdapter — 5 fixed tabs"
```

---

## Task 18: `ChartsFragment` + nav graph actions

**Files:**
- Replace: `app/src/main/java/org/spsl/evtracker/ui/charts/ChartsFragment.kt`
- Modify:  `app/src/main/res/navigation/nav_graph.xml`

- [ ] **Step 1: Add nav-graph actions**

In `app/src/main/res/navigation/nav_graph.xml`, replace the existing `<fragment android:id="@+id/chartsFragment" .../>` block with:

```xml
    <fragment
        android:id="@+id/chartsFragment"
        android:name="org.spsl.evtracker.ui.charts.ChartsFragment"
        android:label="Charts">
        <action
            android:id="@+id/action_charts_to_cars"
            app:destination="@id/carsFragment"/>
        <action
            android:id="@+id/action_charts_to_chargeEdit"
            app:destination="@id/chargeEditFragment"/>
    </fragment>
```

- [ ] **Step 2: Replace `ChartsFragment.kt`**

```kotlin
package org.spsl.evtracker.ui.charts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.databinding.FragmentChartsBinding

@AndroidEntryPoint
class ChartsFragment : Fragment() {

    private val viewModel: ChartsViewModel by viewModels()

    private var _binding: FragmentChartsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pagerAdapter: ChartsPagerAdapter
    private lateinit var tabMediator: TabLayoutMediator

    /** Period to revert to when the user dismisses the Custom date-range picker
     *  via the negative button or system back. Mirrors DashboardFragment's
     *  selectedTabBeforePicker pattern. Updated only when a *concrete* (non-Custom)
     *  chip is tapped, so cancelling the Custom picker restores the prior choice. */
    private var lastConcretePeriod: ChartsPeriod = ChartsPeriod.Last12Months

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpPager()
        setUpPeriodChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    private fun setUpPager() {
        pagerAdapter = ChartsPagerAdapter(this)
        binding.chartsPager.adapter = pagerAdapter
        tabMediator = TabLayoutMediator(binding.chartsTabLayout, binding.chartsPager) { tab, pos ->
            tab.text = when (pagerAdapter.tabKindAt(pos)) {
                ChartsTabFragment.TabKind.TREND        -> getString(R.string.charts_tab_trend)
                ChartsTabFragment.TabKind.MONTHLY_KWH  -> getString(R.string.charts_tab_monthly_kwh)
                ChartsTabFragment.TabKind.MONTHLY_COST -> getString(R.string.charts_tab_monthly_cost)
                ChartsTabFragment.TabKind.AC_DC        -> getString(R.string.charts_tab_ac_dc)
                ChartsTabFragment.TabKind.LOCATIONS    -> getString(R.string.charts_tab_locations)
            }
        }
        tabMediator.attach()
    }

    private fun setUpPeriodChips() {
        binding.chipLast6Months.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.Last6Months
            viewModel.selectPeriod(ChartsPeriod.Last6Months)
        }
        binding.chipLast12Months.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.Last12Months
            viewModel.selectPeriod(ChartsPeriod.Last12Months)
        }
        binding.chipAllTime.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.AllTime
            viewModel.selectPeriod(ChartsPeriod.AllTime)
        }
        binding.chipCustom.setOnClickListener {
            // Do NOT update lastConcretePeriod — we may need it to restore on cancel.
            viewModel.onCustomChipClicked()
        }
    }

    private fun render(state: ChartsScreenState) {
        when (state.charts) {
            ChartsUiState.Loading -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = false
            }
            ChartsUiState.NoCar -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = true
                binding.chartsEmptyHeadline.setText(R.string.empty_no_car_headline)
                binding.chartsEmptyCta.setText(R.string.empty_no_car_cta)
                binding.chartsEmptyCta.setOnClickListener { viewModel.onAddCarCta() }
            }
            ChartsUiState.NoEvents -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = true
                binding.chartsEmptyHeadline.setText(R.string.empty_no_events_headline)
                binding.chartsEmptyCta.setText(R.string.empty_no_events_cta)
                binding.chartsEmptyCta.setOnClickListener { viewModel.onLogChargeCta() }
            }
            is ChartsUiState.Loaded -> {
                binding.chartsContent.isVisible = true
                binding.chartsEmptyContainer.isVisible = false
                // Multi-currency banner is rendered *inside the cost tab body*
                // (see ChartsTabFragment.renderMonthlyCost), not screen-globally.
            }
        }
        // Reflect the current period selection on the chip group.
        when (val p = state.period) {
            ChartsPeriod.Last6Months  -> binding.chipLast6Months.isChecked = true
            ChartsPeriod.Last12Months -> binding.chipLast12Months.isChecked = true
            ChartsPeriod.AllTime      -> binding.chipAllTime.isChecked = true
            is ChartsPeriod.Custom    -> {
                binding.chipCustom.isChecked = true
                // Spec §4: Custom chip exposes the selected range as its
                // contentDescription so screen readers announce the actual
                // window. DateUtils.formatDateRange chooses a locale-aware
                // representation; we include FORMAT_SHOW_YEAR to disambiguate
                // ranges that span calendar boundaries.
                binding.chipCustom.contentDescription = android.text.format.DateUtils
                    .formatDateRange(
                        requireContext(),
                        p.fromMillis,
                        p.toMillis,
                        android.text.format.DateUtils.FORMAT_SHOW_DATE
                            or android.text.format.DateUtils.FORMAT_SHOW_YEAR
                    )
            }
        }
        // Reset the Custom chip's contentDescription back to its label when the
        // user picks a non-Custom period, so screen readers don't read stale
        // range text.
        if (state.period !is ChartsPeriod.Custom) {
            binding.chipCustom.contentDescription = getString(R.string.charts_period_custom)
        }
    }

    private fun handleEvent(event: ChartsEvent) {
        when (event) {
            ChartsEvent.OpenCustomRangePicker -> showCustomRangePicker()
            ChartsEvent.NavigateToCars ->
                findNavController().navigate(R.id.action_charts_to_cars)
            ChartsEvent.NavigateToChargeEdit ->
                findNavController().navigate(R.id.action_charts_to_chargeEdit)
        }
    }

    private fun showCustomRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.period_custom)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val from = range.first ?: return@addOnPositiveButtonClickListener
            val to = range.second ?: return@addOnPositiveButtonClickListener
            // Custom *is* the new selection; record it so a future Cancel of a
            // re-opened picker would still restore *some* concrete prior choice.
            // We deliberately do NOT update lastConcretePeriod here.
            viewModel.selectCustomRange(from, to)
        }
        picker.addOnNegativeButtonClickListener { restorePreviousPeriodSelection() }
        picker.addOnCancelListener { restorePreviousPeriodSelection() }
        picker.show(parentFragmentManager, "chartsCustomRange")
    }

    private fun restorePreviousPeriodSelection() {
        // Driving via the VM (not via chip.isChecked) preserves single-source-of-truth:
        // the next uiState emission re-renders the chip group from state.period.
        viewModel.selectPeriod(lastConcretePeriod)
    }

    override fun onDestroyView() {
        if (this::tabMediator.isInitialized) tabMediator.detach()
        binding.chartsPager.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (ViewBinding generates `FragmentChartsBinding` with all the IDs from Task 13.)

- [ ] **Step 4: Run all JVM tests to confirm nothing regressed**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. JVM unit-test count up by ≥ 39 vs F1 baseline.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/charts/ChartsFragment.kt
git add app/src/main/res/navigation/nav_graph.xml
git commit -m "feat(F2): ChartsFragment — period chips, ViewPager2 + tabs, empty states, nav actions"
```

---

## Task 19: `ChartsFragmentTest` (instrumented)

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/ui/charts/ChartsFragmentTest.kt`

These tests run on an emulator (or a connected device). The harness mirrors `SettingsFragmentTest.kt` from F1: Hilt + `launchFragmentInContainer<...>(themeResId = R.style.Theme_EVTracker)` for fragment-level tests, and `TestNavHostController` for navigation assertions. We seed Room directly via the injected DAOs to avoid coupling tests to use-case wiring.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/org/spsl/evtracker/ui/charts/ChartsFragmentTest.kt`:

```kotlin
package org.spsl.evtracker.ui.charts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import android.view.View
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.preferences.PreferenceKeys

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChartsFragmentTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var carDao: CarDao
    @Inject lateinit var chargeEventDao: ChargeEventDao

    private fun seedDataStore(activeCarId: Int = 1) = runBlocking {
        dataStore.edit {
            it.clear()
            it[PreferenceKeys.SETUP_COMPLETE] = true
            it[PreferenceKeys.ACTIVE_CAR_ID]  = activeCarId
            it[PreferenceKeys.DISTANCE_UNIT]  = "km"
            it[PreferenceKeys.CURRENCY]       = "EUR"
            it[PreferenceKeys.PRIMARY_METRIC] = "km_per_kwh"
            it[PreferenceKeys.THEME]          = "system"
            it[PreferenceKeys.DRIVE_ENABLED]  = false
        }
    }

    private suspend fun seedDb(events: List<ChargeEventEntity>, withCar: Boolean = true) {
        chargeEventDao.deleteAll()
        carDao.deleteAll()
        if (withCar) carDao.insert(CarEntity(id = 1, name = "Car"))
        events.forEach { chargeEventDao.insert(it) }
    }

    private fun ev(
        date: Long, odo: Double, type: String = "AC",
        cost: Double? = null, currency: String? = null
    ) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = date, odometerKm = odo, kwhAdded = 10.0,
        chargeType = type, costTotal = cost, costPerKwh = null,
        currency = currency, location = null, note = "", createdAt = 0L
    )

    /**
     * Espresso scoping helper for ViewPager2 tab tests.
     *
     * `ViewPager2` keeps neighbouring page Fragments attached for prefetch, so
     * IDs from the shared `fragment_charts_tab.xml` (`charts_tab_empty_message`,
     * `charts_tab_subtitle`, `charts_tab_chart_root`) are NOT unique in the view
     * hierarchy when tests run. A bare `withId(...)` matches every page's copy
     * and Espresso throws AmbiguousViewMatcherException.
     *
     * The active page is the only one whose `charts_tab_chart_root` is
     * `isDisplayed()` (offscreen pages have isDisplayed = false because their
     * fragment view is detached or off-screen). We scope every per-tab assertion
     * to the descendant chain of that displayed root.
     */
    private fun inActivePage(matcher: org.hamcrest.Matcher<View>): org.hamcrest.Matcher<View> =
        org.hamcrest.Matchers.allOf(
            matcher,
            androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.matcher.ViewMatchers.withId(R.id.charts_tab_chart_root),
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()
                )
            )
        )

    @Before fun setUp() {
        hiltRule.inject()
        runBlocking { chargeEventDao.deleteAll(); carDao.deleteAll() }
    }

    @Test fun tabSwitch_showsCorrectChart() = runBlocking {
        // Seed a shape that produces a different visible signal per tab so the
        // assertions can distinguish them via Espresso (MPAndroidChart legends and
        // axis labels are canvas-drawn and are NOT visible to Espresso). We use:
        //  - 2 AC events spanning two months, mono-currency EUR-costed → Trend &
        //    Monthly kWh & Monthly cost have data (chart container populated;
        //    per-tab empty message GONE)
        //  - 1 DC event → AC/DC tab has data → subtitle visible with kWh substring
        //  - All location fields null → Locations tab → empty message visible with
        //    "No location data..."
        seedDataStore()
        val now = System.currentTimeMillis()
        val d = 24L * 60 * 60 * 1000
        seedDb(listOf(
            ev(now - 60 * d, 0.0,   "AC", cost = 5.0, currency = "EUR"),
            ev(now - 30 * d, 100.0, "AC", cost = 7.5, currency = "EUR"),
            ev(now -  5 * d, 200.0, "DC", cost = 4.0, currency = "EUR")
        ))
        launchFragmentInContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Default tab is TREND. Empty message is GONE → chart populated.
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // MONTHLY_KWH: same — chart populated.
                onView(withText(R.string.charts_tab_monthly_kwh)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // MONTHLY_COST: mono-currency EUR data → chart populated.
                onView(withText(R.string.charts_tab_monthly_cost)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // AC_DC: subtitle is visible with a "kWh" substring.
                onView(withText(R.string.charts_tab_ac_dc)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_subtitle)))
                    .check(matches(isDisplayed()))
                    .check(matches(withSubstring("kWh")))

                // LOCATIONS: no location data → empty message shows the locations string.
                onView(withText(R.string.charts_tab_locations)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.charts_no_locations_period)))
            }
    }

    @Test fun noData_emptyState_perPeriod() = runBlocking {
        seedDataStore()
        val twoYearsAgo = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
        seedDb(listOf(ev(twoYearsAgo, 0.0)))
        launchFragmentInContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Period chips and tab layout still visible
                onView(withId(R.id.charts_period_chips)).check(matches(isDisplayed()))
                onView(withId(R.id.charts_tab_layout)).check(matches(isDisplayed()))
                // Tab body shows the per-period empty message. Scope to the
                // active page — every offscreen page also shows this string,
                // so a bare withText would be ambiguous.
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.charts_no_data_period)))
            }
    }

    @Test fun noCar_showsAddCarCta_andNavigates() = runBlocking {
        seedDataStore(activeCarId = -1)
        seedDb(emptyList(), withCar = false)
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setGraph(R.navigation.nav_graph)
            setCurrentDestination(R.id.chartsFragment)
        }
        launchFragmentInContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED)
            .onFragment { Navigation.setViewNavController(it.requireView(), nav) }

        onView(withId(R.id.charts_empty_container)).check(matches(isDisplayed()))
        onView(withId(R.id.charts_empty_cta)).perform(click())

        assertEquals(R.id.carsFragment, nav.currentDestination?.id)
    }

    @Test fun noEvents_showsLogChargeCta_andNavigates() = runBlocking {
        seedDataStore(activeCarId = 1)
        seedDb(emptyList(), withCar = true)
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setGraph(R.navigation.nav_graph)
            setCurrentDestination(R.id.chartsFragment)
        }
        launchFragmentInContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED)
            .onFragment { Navigation.setViewNavController(it.requireView(), nav) }

        onView(withId(R.id.charts_empty_container)).check(matches(isDisplayed()))
        onView(withId(R.id.charts_empty_cta)).perform(click())

        assertEquals(R.id.chargeEditFragment, nav.currentDestination?.id)
    }

    @Test fun multiCurrencyPeriod_costTabShowsBanner_locally() = runBlocking {
        // Spec §6.3: when mixedCurrency is true, the *Monthly cost* tab body is
        // replaced by the multi_currency_banner string. The four other tabs
        // render normally — there is intentionally no screen-global banner.
        seedDataStore()
        val now = System.currentTimeMillis()
        val d = 24L * 60 * 60 * 1000
        seedDb(listOf(
            ev(now - 60 * d, 0.0,   "AC", cost = 5.0, currency = "EUR"),
            ev(now - 30 * d, 100.0, "AC", cost = 7.5, currency = "USD")
        ))
        launchFragmentInContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Default TREND tab does NOT show the multi-currency banner string.
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // Click MONTHLY_COST tab → tab-body empty TextView shows the banner.
                onView(withText(R.string.charts_tab_monthly_cost)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.multi_currency_banner)))
            }
    }
}
```

- [ ] **Step 2: Compile-only build to confirm no syntax / DI gaps**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`. (Running these tests requires an API 26+ emulator; the implementer may run them locally if one is available.)

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/ui/charts/ChartsFragmentTest.kt
git commit -m "test(F2): ChartsFragmentTest — tabs, empty states, multi-currency banner"
```

---

## Task 20: CLAUDE.md status update + final assemble

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update the Status block**

In `CLAUDE.md`, find the line that begins:

```
> **Status:** Sub-projects A (foundation/DI/Room v3) ... and F1 ... are all merged. Wizard, Dashboard, ChargeEdit, Cars, History, Settings, and ManageLocations are fully wired; Charts remains a placeholder fragment until F2. JVM unit-test count: ~188.
```

Replace it with:

```
> **Status:** Sub-projects A (foundation/DI/Room v3), B (repositories), C (domain services + use cases), D (Core UI: Dashboard/ChargeEdit/Cars/History), E (Drive backup), F1 (Settings remainder + ManageLocations + reset use cases + startup auto-recovery), and F2 (Charts) are all merged. Wizard, Dashboard, ChargeEdit, Cars, History, Settings, ManageLocations, and Charts are fully wired. JVM unit-test count: ~227. Instrumented suite compiles via `:app:assembleDebugAndroidTest` (running requires an emulator); Drive backup smoke per `GOOGLE_CLOUD_SETUP.md` requires a Google account allow-listed in the OAuth consent screen.
```

Also update the architecture block: change `Charts ⊘` to `Charts ✓` in the diagram, and add a note next to ChartsViewModel that it's now wired.

In the architecture diagram (`Legend: ✓ = wired in D · ⊘ = placeholder fragment until F.`) replace with:

```
Legend: ✓ = wired
```

(F is fully shipped.)

- [ ] **Step 2: Run the full JVM suite once more**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. Total tests ≥ ~227 (188 baseline + 39 new). If the count differs notably, re-confirm Task 1–11 ran cleanly.

- [ ] **Step 3: Compile instrumented**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(F2): update CLAUDE.md status — Sub-project F2 landed"
```

- [ ] **Step 5: Merge to main**

Run each git command separately (CLAUDE.md global rule — never compound git):

```bash
git checkout main
```

```bash
git pull --ff-only origin main
```

```bash
git merge --no-ff feat/sub-project-f2 -m "Merge Sub-project F2 (Charts) into main"
```

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected post-merge: BUILD SUCCESSFUL.

```bash
git push origin main
```

```bash
git branch -d feat/sub-project-f2
```

---

## Self-review notes (for the implementer)

**1. Spec coverage** — the plan covers every section of `2026-04-29-sub-project-f2-design.md`:

| Spec § | Plan task |
|--------|-----------|
| §1 source-of-truth | All tasks reference DESIGN/TEST_PLAN/CLAUDE invariants in code comments |
| §2 in/out scope | Out-of-scope items not implemented; plan does not introduce filter chips, drill-into-history, etc. |
| §3.1 layer mapping | Tasks 1, 4–6, 8, 10, 11, 14–18 |
| §3.2 separate `ChartsPeriod` | Task 1 |
| §3.3 file layout | Tasks 1, 2, 8, 9, 10, 11, 14, 15, 16, 17, 18 |
| §3.4 ViewModel scoping | Task 16 (`viewModels({ requireParentFragment() })`) |
| §3.5 data flow + currency-source rule | Task 10 (use case derives `periodCurrency`) |
| §4 period control + Custom-chip a11y | Tasks 1, 18 (`MaterialDatePicker`); a11y label is formatted in the Fragment from `state.period as ChartsPeriod.Custom` (no extra state). |
| §5 state model | Task 9 |
| §6.1 trend math + per-series partition | Task 4 |
| §6.2 monthly kWh | Task 16 (`renderMonthlyKwh`) |
| §6.3 monthly cost + visibility rules + reused `R.string.multi_currency_banner` | Tasks 16, 18 |
| §6.4 AC/DC pie | Task 16 (`renderAcDc`) |
| §6.5 location pie + sentinel + cap-8 + palette | Tasks 2, 6, 14, 16 |
| §7 empty-state ladder | Tasks 10, 18, 19 |
| §8 ViewModel contract | Task 11 |
| §9 use case + `NowProvider` + `flowOn(@AggregationDispatcher)` | Tasks 8, 10 |
| §10 stats helpers | Tasks 3–6 |
| §11 testing matrix (39 tests) | Tasks 1, 4, 5, 6, 10, 11, 19 |
| §12 ChartStyling + MarkerView | Tasks 14, 15 |
| §13 bottom-nav + back-stack | No code change required (already wired); Task 18 just uses `findNavController().navigate(actionId)`. |
| §14 strings | Task 12 |
| §15 risks/mitigations | Risks are addressed structurally by the chosen tasks (rotation: Task 16 reads `uiState.value`; theme attrs: Task 14 fallback; ANR: Task 8 + Task 10 `flowOn`). |
| §16 acceptance | Task 20 final builds + ~39 new tests + green merge. |

**2. Placeholder scan** — no "TBD"/"TODO"/"add error handling"/"similar to Task N"/"fill in" anywhere. Every code step shows the full file or full insertion text.

**3. Type consistency** — types/method names referenced in later tasks match earlier definitions:

- `ChartsPeriod` (Task 1) used in Tasks 8, 9, 10, 11, 14, 16, 18
- `EfficiencyPoint`/`EfficiencySeries` (Task 2) used in Tasks 4, 9, 10, 11, 16
- `AcDcSplit` (Task 2) used in Tasks 5, 9, 10, 11, 16
- `LocationSlice.OTHER_KEY` (Task 2) used in Tasks 6, 16
- `StatsCalculator.detectMixedCurrency` / `computeEfficiencyTrend` / `computeAcDcSplit` / `computeLocationDistribution` (Tasks 3–6) all used in Task 10
- `NowProvider` (Task 8) used in Tasks 10, 11; production binding in `DispatcherModule` (Task 8)
- `@AggregationDispatcher` (Task 8) used in Task 10
- `ChartsUiState` / `ChartsScreenState` / `ChartsEvent` (Task 9) used in Tasks 11, 16, 18
- `observeForCarCallCount` field (Task 7) used in Task 11
- `ChartsTabFragment.TabKind` enum (Task 16) used in Task 17
- `ChartsPagerAdapter.tabKindAt(position)` (Task 17) used in Task 18

If the implementer finds a drift (e.g. a parameter renamed mid-plan), they should pick the version in the *first* task that introduces the name and propagate.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-29-sub-project-f2.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Same flow as F1.

**2. Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch with checkpoints.

**Which approach?**
