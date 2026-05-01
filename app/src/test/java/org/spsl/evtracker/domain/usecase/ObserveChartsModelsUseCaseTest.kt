package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeSettingsReader
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveChartsModelsUseCaseTest {

    // 2024-04-25T08:00Z anchor; "Last 12 months" → 2023-04-25 onwards
    private val nowMs = 1_714_032_000_000L
    private val now = NowProvider { nowMs }

    private fun setup(
        cars: List<CarEntity> = listOf(CarEntity(id = 1L, name = "C", createdAt = 0L)),
        activeCarId: Long = 1L,
        events: List<ChargeEventEntity> = emptyList(),
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
            aggregationContext = EmptyCoroutineContext,
        )
    }

    private fun ev(
        date: Long,
        odo: Double,
        kwh: Double = 10.0,
        type: ChargeType = ChargeType.AC,
        cost: Double? = null,
        currency: String? = null,
        location: String? = null,
    ) = ChargeEventEntity(
        id = 0L, carId = 1L, eventDate = date, odometerKm = odo, kwhAdded = kwh,
        chargeType = type, costTotal = cost, costPerKwh = null,
        currency = currency, location = location, note = "", createdAt = 0L,
    )

    @Test fun noCar_emitsNoCar() = runTest {
        val state = setup(cars = emptyList(), activeCarId = -1L)
            .observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.NoCar)
    }

    @Test fun activeCarMinusOne_emitsNoCar() = runTest {
        val state = setup(activeCarId = -1L).observe(ChartsPeriod.Last12Months).first()
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
        val state = setup(
            events = listOf(
                ev(nowMs - 2 * ms30d, 0.0, 10.0, ChargeType.AC, cost = 5.0, currency = "EUR", location = "Home"),
                ev(nowMs - 1 * ms30d, 100.0, 10.0, ChargeType.AC, cost = 7.5, currency = "EUR", location = "Home"),
            ),
        ).observe(ChartsPeriod.Last12Months).first()
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
        val state = setup(
            events = listOf(
                ev(nowMs - 2 * ms30d, 0.0, 10.0, ChargeType.AC, cost = 5.0, currency = "EUR"),
                ev(nowMs - 1 * ms30d, 100.0, 10.0, ChargeType.AC, cost = 7.5, currency = "USD"),
            ),
        ).observe(ChartsPeriod.Last12Months).first()
        assertTrue(state is ChartsUiState.Loaded)
        val l = state as ChartsUiState.Loaded
        assertTrue(l.mixedCurrency)
        assertNull(l.periodCurrency)
        assertTrue(l.monthlyCost.isEmpty())
        assertTrue(l.monthlyKwh.isNotEmpty()) // kWh series unaffected
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
        val carReader = FakeCarReader(
            listOf(
                CarEntity(id = 1L, name = "A", createdAt = 0L),
                CarEntity(id = 2L, name = "B", createdAt = 0L),
            ),
        )
        val queries = FakeChargeEventQueries().apply {
            seed(listOf(ev(nowMs - 100, 0.0).copy(carId = 1)))
        }
        val settings = FakeSettingsReader(activeCarIdInit = 1)
        val useCase = ObserveChartsModelsUseCase(
            carReader,
            queries,
            settings,
            StatsCalculator(),
            DateRangeResolver(),
            now = now,
            aggregationContext = EmptyCoroutineContext,
        )
        val first = useCase.observe(ChartsPeriod.Last12Months).first()
        assertTrue(first is ChartsUiState.Loaded)

        settings.setActiveCarId(2)
        val second = useCase.observe(ChartsPeriod.Last12Months).first()
        assertTrue(second is ChartsUiState.NoEvents) // car 2 has no events
    }
}
