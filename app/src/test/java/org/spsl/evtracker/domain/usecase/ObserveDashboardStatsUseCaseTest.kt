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
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStatsUseCaseTest {

    companion object {
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        activeCarId: Int = -1,
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
            dateRangeResolver = DateRangeResolver(),
        )
        return Triple(useCase, queries, settings)
    }

    @Test
    fun noCars_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(cars = emptyList(), activeCarId = -1)
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun activeCarMinusOne_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            activeCarId = -1,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun noEventsForActiveCar_emitsNoEventsEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = emptyList(),
            activeCarId = 1,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoEvents, state.emptyState)
    }

    @Test
    fun eventsPresent_emitsStatsAndMultiCurrencyFlag() = runTest {
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = listOf(
                ChargeEventEntity(carId = 1, eventDate = now - 5 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 0.0, costTotal = 5.0, currency = "EUR"),
                ChargeEventEntity(carId = 1, eventDate = now - 3 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 20.0, costTotal = 6.0, currency = "USD"),
                ChargeEventEntity(carId = 1, eventDate = now - 1 * MS_PER_DAY, odometerKm = 200.0, kwhAdded = 20.0, costTotal = 7.0, currency = "EUR"),
            ),
            activeCarId = 1,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertTrue(state.stats != null)
        assertTrue(state.showMultiCurrencyBanner)
    }

    @Test
    fun reEmitsWhenEventInsertedForActiveCar() = runTest(UnconfinedTestDispatcher()) {
        val carReader = FakeCarReader(listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        val queries = FakeChargeEventQueries()
        val writer = FakeChargeEventWriter(queries.shareStore())
        val settings = FakeSettingsReader(activeCarIdInit = 1)
        val useCase = ObserveDashboardStatsUseCase(carReader, queries, settings, StatsCalculator(), DateRangeResolver())

        val emissions = mutableListOf<org.spsl.evtracker.core.model.DashboardUiState>()
        val job = launch {
            useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).collect { emissions += it }
        }
        advanceUntilIdle()
        // Initial emission: NoEvents.
        assertEquals(EmptyState.NoEvents, emissions.last().emptyState)

        // Insert an event for the active car within the period window.
        val now = System.currentTimeMillis()
        writer.insert(ChargeEventEntity(carId = 1, eventDate = now - 1 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 10.0))
        advanceUntilIdle()
        writer.insert(ChargeEventEntity(carId = 1, eventDate = now, odometerKm = 100.0, kwhAdded = 20.0))
        advanceUntilIdle()
        // Latest emission should now have stats (two events => delta-odo computable).
        assertTrue("expected stats after insert; emissions=$emissions", emissions.last().stats != null)
        job.cancel()
    }
}
