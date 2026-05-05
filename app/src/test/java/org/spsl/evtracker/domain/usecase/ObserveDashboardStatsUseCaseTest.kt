package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStatsUseCaseTest {

    companion object {
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        activeCarId: Long = -1L,
    ): Triple<ObserveDashboardStatsUseCase, FakeChargeEventQueries, FakeSettingsReader> {
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries()
        queries.seed(events)
        val settings = FakeSettingsReader(activeCarIdInit = activeCarId)
        val useCase = ObserveDashboardStatsUseCase(
            carReader = carReader,
            chargeEventQueries = queries,
            settingsReader = settings,
            statsCalculator = StatsCalculator(),
            capacityEstimator = org.spsl.evtracker.domain.service.CapacityEstimator(),
            co2Calculator = org.spsl.evtracker.domain.service.CO2Calculator(),
            dateRangeResolver = DateRangeResolver(),
            now = FakeNowProvider(System.currentTimeMillis()),
        )
        return Triple(useCase, queries, settings)
    }

    @Test
    fun noCars_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(cars = emptyList(), activeCarId = -1L)
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun activeCarMinusOne_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1L, name = "T", createdAt = 0L)),
            activeCarId = -1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun noEventsForActiveCar_emitsNoEventsEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1L, name = "T", createdAt = 0L)),
            events = emptyList(),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoEvents, state.emptyState)
    }

    @Test
    fun filterMatchesNothing_butCarHasEvents_doesNotEmitNoEventsEmptyState() = runTest {
        // Regression: a car with only AC events + the user picking the DC chip
        // used to flip emptyState=NoEvents because filtered.isEmpty(). The
        // Fragment then hid `dashboardContent` (the NestedScrollView), which
        // takes the filter chips down with it because the chips live inside
        // that container in fragment_dashboard.xml. With chips gone, the user
        // could not tap All to escape and had to leave the Dashboard via the
        // bottom nav. The contract is now: NoEvents fires only when the active
        // car has zero events overall; a zero-match filter falls through to a
        // normal Stats render with placeholder values, so the chips stay
        // tappable.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1L, name = "T", createdAt = 0L)),
            events = listOf(
                ChargeEventEntity(
                    carId = 1L,
                    eventDate = now - 3 * MS_PER_DAY,
                    odometerKm = 100.0,
                    kwhAdded = 20.0,
                    chargeType = org.spsl.evtracker.core.model.ChargeType.AC,
                    createdAt = 0L,
                ),
                ChargeEventEntity(
                    carId = 1L,
                    eventDate = now - 1 * MS_PER_DAY,
                    odometerKm = 200.0,
                    kwhAdded = 20.0,
                    chargeType = org.spsl.evtracker.core.model.ChargeType.AC,
                    createdAt = 0L,
                ),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.DC).first()
        // The contract: NOT NoEvents (so the Fragment keeps `dashboardContent`
        // visible and the chips stay tappable). A non-null Stats with zero
        // counts is the right thing to render in the cards.
        assertTrue("NoEvents must NOT fire when only the filter is empty", state.emptyState == null)
        assertTrue("Stats must be non-null so cards render placeholders", state.stats != null)
        assertEquals(0, state.stats!!.chargeCount)
    }

    @Test
    fun eventsPresent_emitsStatsAndMultiCurrencyFlag() = runTest {
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1L, name = "T", createdAt = 0L)),
            events = listOf(
                ChargeEventEntity(carId = 1L, eventDate = now - 5 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 0.0, costTotal = 5.0, currency = "EUR", createdAt = 0L),
                ChargeEventEntity(carId = 1L, eventDate = now - 3 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 20.0, costTotal = 6.0, currency = "USD", createdAt = 0L),
                ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 200.0, kwhAdded = 20.0, costTotal = 7.0, currency = "EUR", createdAt = 0L),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertTrue(state.stats != null)
        assertTrue(state.showMultiCurrencyBanner)
    }

    // -------------------------------------------------------------------------
    // TASK-46 — battery-health heuristic / overestimate flags on Stats
    // -------------------------------------------------------------------------

    private fun nominal60Car() = CarEntity(id = 1L, name = "T", batteryKwh = 60.0, createdAt = 0L)

    @Test
    fun heuristicLatestPoint_overThreshold_setsBothFlags() = runTest {
        // kwhAdded = 70 against nominal = 60: heuristic qualifies (70 >= 48),
        // capacity = 70, pct = 70/60 * 100 = 116.67% — well above the 105%
        // threshold, so BOTH flags fire.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(nominal60Car()),
            events = listOf(
                ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 70.0, createdAt = 0L),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        val stats = state.stats!!
        assertTrue("heuristic flag should fire", stats.batteryHealthIsHeuristic)
        assertTrue("overestimated flag should fire above 105%", stats.batteryHealthIsOverestimated)
    }

    @Test
    fun heuristicLatestPoint_underThreshold_setsHeuristicButNotOverestimated() = runTest {
        // kwhAdded = 50 against nominal = 60: heuristic qualifies (50 >= 48),
        // capacity = 50, pct = 83.3% — under the 105% threshold, so the
        // overestimation flag stays clear even though the heuristic flag fires.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(nominal60Car()),
            events = listOf(
                ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 50.0, createdAt = 0L),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        val stats = state.stats!!
        assertTrue("heuristic flag should fire", stats.batteryHealthIsHeuristic)
        assertEquals(false, stats.batteryHealthIsOverestimated)
    }

    @Test
    fun heuristicAtExactly105Percent_setsOverestimated_boundaryGuard() = runTest {
        // kwhAdded = 63 against nominal = 60: capacity = 63, pct = 105.0%
        // exactly. The threshold uses `>=`, so the boundary value triggers.
        // Regression guard for the BACKLOG-mandated transition point.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(nominal60Car()),
            events = listOf(
                ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 63.0, createdAt = 0L),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        val stats = state.stats!!
        assertTrue(stats.batteryHealthIsHeuristic)
        assertTrue("105.0% must trigger via >= comparison", stats.batteryHealthIsOverestimated)
    }

    @Test
    fun exactLatestPoint_evenAboveThreshold_setsNeitherFlag() = runTest {
        // SoC delta 0.2 → 0.7 over 33 kWh = 66 kWh effective capacity.
        // Against nominal = 60, that's 110% — above the 105% guard, but
        // the EXACT path is trusted: no warning chip should fire because
        // exact readings reflect real battery state, not an inferred
        // upper bound.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(nominal60Car()),
            events = listOf(
                ChargeEventEntity(
                    carId = 1L,
                    eventDate = now - 1 * MS_PER_DAY,
                    odometerKm = 100.0,
                    kwhAdded = 33.0,
                    socBefore = 0.2,
                    socAfter = 0.7,
                    createdAt = 0L,
                ),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        val stats = state.stats!!
        assertEquals(false, stats.batteryHealthIsHeuristic)
        assertEquals(
            "exact path must never trip the overestimation chip even above 105%",
            false,
            stats.batteryHealthIsOverestimated,
        )
    }

    @Test
    fun nullNominal_setsNeitherFlag() = runTest {
        // Car has no nominal battery capacity — health pct is null and both
        // flags should default to false. Regression guard for the case where
        // the user added a car without setting batteryKwh.
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1L, name = "T", batteryKwh = null, createdAt = 0L)),
            events = listOf(
                ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 70.0, createdAt = 0L),
            ),
            activeCarId = 1L,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        val stats = state.stats!!
        assertEquals(false, stats.batteryHealthIsHeuristic)
        assertEquals(false, stats.batteryHealthIsOverestimated)
    }

    @Test
    fun reEmitsWhenEventInsertedForActiveCar() = runTest(UnconfinedTestDispatcher()) {
        val carReader = FakeCarReader(listOf(CarEntity(id = 1L, name = "T", createdAt = 0L)))
        val queries = FakeChargeEventQueries()
        val writer = FakeChargeEventWriter(queries.shareStore())
        val settings = FakeSettingsReader(activeCarIdInit = 1)
        val useCase = ObserveDashboardStatsUseCase(
            carReader,
            queries,
            settings,
            StatsCalculator(),
            org.spsl.evtracker.domain.service.CapacityEstimator(),
            org.spsl.evtracker.domain.service.CO2Calculator(),
            DateRangeResolver(),
            FakeNowProvider(System.currentTimeMillis()),
        )

        val emissions = mutableListOf<org.spsl.evtracker.core.model.DashboardUiState>()
        val job = launch {
            useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).collect { emissions += it }
        }
        advanceUntilIdle()
        // Initial emission: NoEvents.
        assertEquals(EmptyState.NoEvents, emissions.last().emptyState)

        // Insert an event for the active car within the period window.
        val now = System.currentTimeMillis()
        writer.insert(ChargeEventEntity(carId = 1L, eventDate = now - 1 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 10.0, createdAt = 0L))
        advanceUntilIdle()
        writer.insert(ChargeEventEntity(carId = 1L, eventDate = now, odometerKm = 100.0, kwhAdded = 20.0, createdAt = 0L))
        advanceUntilIdle()
        // Latest emission should now have stats (two events => delta-odo computable).
        assertTrue("expected stats after insert; emissions=$emissions", emissions.last().stats != null)
        job.cancel()
    }
}
