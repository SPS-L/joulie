package org.spsl.evtracker.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardEvent
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCase
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        activeCarId: Long = -1L,
        primaryMetric: String = "kwh_per_100km",
        distanceUnit: String = "km",
        currency: String = "EUR",
    ): VmFixture {
        val carReader = FakeCarReader(initial = cars)
        val queries = FakeChargeEventQueries().also { it.seed(events) }
        val settingsReader = FakeSettingsReader(
            activeCarIdInit = activeCarId,
            primaryMetricInit = primaryMetric,
            distanceUnitInit = distanceUnit,
            currencyInit = currency,
        )
        val settingsWriter = FakeSettingsWriter()
        val useCase = ObserveDashboardStatsUseCase(
            carReader = carReader,
            chargeEventQueries = queries,
            settingsReader = settingsReader,
            statsCalculator = StatsCalculator(),
            capacityEstimator = org.spsl.evtracker.domain.service.CapacityEstimator(),
            co2Calculator = org.spsl.evtracker.domain.service.CO2Calculator(),
            dateRangeResolver = DateRangeResolver(),
            now = FakeNowProvider(System.currentTimeMillis()),
        )
        val vm = DashboardViewModel(
            observeDashboardStats = useCase,
            carReader = carReader,
            settingsReader = settingsReader,
            settingsWriter = settingsWriter,
            refreshCarbonIntensity = org.spsl.evtracker.domain.usecase.RefreshCarbonIntensityUseCase(
                settingsReader = settingsReader,
                carbonIntensitySource = org.spsl.evtracker.testing.FakeCarbonIntensitySource(),
            ),
            carbonIntensityFormatter = org.spsl.evtracker.domain.service.CarbonIntensityFormatter(),
            now = FakeNowProvider(System.currentTimeMillis()),
        )
        return VmFixture(vm, settingsWriter)
    }

    private data class VmFixture(val vm: DashboardViewModel, val settingsWriter: FakeSettingsWriter)

    @Test
    fun noCar_emitsNoCarEmptyState() = runTest {
        val (vm, _) = build()
        val state = vm.uiState.first { it.dashboard.emptyState != null }
        assertEquals(EmptyState.NoCar, state.dashboard.emptyState)
    }

    @Test
    fun hasCarButNoEvents_emitsNoEventsEmptyState() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val (vm, _) = build(cars = listOf(car), activeCarId = 1L)
        val state = vm.uiState.first { it.dashboard.emptyState == EmptyState.NoEvents }
        assertEquals(EmptyState.NoEvents, state.dashboard.emptyState)
    }

    @Test
    fun eventsLoaded_propagatesStatsFromUseCase() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val now = System.currentTimeMillis()
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = now - 86_400_000, odometerKm = 100.0, kwhAdded = 20.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = now, odometerKm = 200.0, kwhAdded = 25.0, chargeType = ChargeType.AC, createdAt = 0L),
        )
        val (vm, _) = build(cars = listOf(car), events = events, activeCarId = 1L)
        val state = vm.uiState.first { it.dashboard.stats != null }
        assertNotNull(state.dashboard.stats)
        assertEquals(45.0, state.dashboard.stats!!.totalKwh, 0.001)
        assertEquals(100.0, state.dashboard.stats!!.totalDistanceKm, 0.001)
    }

    @Test
    fun selectFilter_filtersDcOut() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val now = System.currentTimeMillis()
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = now - 86_400_000, odometerKm = 100.0, kwhAdded = 20.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = now, odometerKm = 200.0, kwhAdded = 25.0, chargeType = ChargeType.DC_FAST, createdAt = 0L),
        )
        val (vm, _) = build(cars = listOf(car), events = events, activeCarId = 1L)
        vm.selectFilter(ChargeTypeFilter.AC)
        val state = vm.uiState.first { it.filter == ChargeTypeFilter.AC && it.dashboard.stats?.chargeCount == 1 }
        assertEquals(1, state.dashboard.stats?.chargeCount)
    }

    @Test
    fun selectPeriod_recomputesFromUseCase() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val now = System.currentTimeMillis()
        val daysAgo = { d: Int -> now - d * 86_400_000L }
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = daysAgo(20), odometerKm = 100.0, kwhAdded = 20.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = daysAgo(2), odometerKm = 200.0, kwhAdded = 25.0, chargeType = ChargeType.AC, createdAt = 0L),
        )
        val (vm, _) = build(cars = listOf(car), events = events, activeCarId = 1L)
        vm.uiState.first { it.dashboard.stats?.chargeCount == 2 }
        vm.selectPeriod(DashboardPeriod.Last7Days)
        val state = vm.uiState.first { it.period == DashboardPeriod.Last7Days && it.dashboard.stats?.chargeCount == 1 }
        assertEquals(1, state.dashboard.stats?.chargeCount)
    }

    @Test
    fun multiCurrency_propagatesShowBanner() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val now = System.currentTimeMillis()
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = now - 86_400_000, odometerKm = 100.0, kwhAdded = 20.0, chargeType = ChargeType.AC, costTotal = 5.0, costPerKwh = 0.25, currency = "EUR", createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = now, odometerKm = 200.0, kwhAdded = 25.0, chargeType = ChargeType.AC, costTotal = 7.0, costPerKwh = 0.28, currency = "USD", createdAt = 0L),
        )
        val (vm, _) = build(cars = listOf(car), events = events, activeCarId = 1L)
        val state = vm.uiState.first { it.dashboard.showMultiCurrencyBanner }
        assertTrue(state.dashboard.showMultiCurrencyBanner)
    }

    @Test
    fun selectCar_writesToSettingsWriter() = runTest {
        val (vm, settingsWriter) = build()
        vm.selectCar(7)
        assertEquals(7, settingsWriter.activeCarId)
    }

    @Test
    fun customRange_emitsCustomPeriod() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val (vm, _) = build(cars = listOf(car), activeCarId = 1L)
        vm.selectCustomRange(from = 1_000L, to = 2_000L)
        val state = vm.uiState.first { it.period is DashboardPeriod.Custom }
        val custom = state.period as DashboardPeriod.Custom
        assertEquals(1_000L, custom.fromMillis)
        assertEquals(2_000L, custom.toMillis)
    }

    @Test
    fun onFabClick_emitsNavigateEvent_whenCarActive() = runTest {
        val car = CarEntity(id = 1L, name = "Tesla", createdAt = 0L)
        val (vm, _) = build(cars = listOf(car), activeCarId = 1L)
        vm.uiState.first { it.activeCarId == 1L }
        val received = mutableListOf<DashboardEvent>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onFabClick()
        testScheduler.advanceUntilIdle()
        job.cancel()
        assertTrue("got $received", received.isNotEmpty() && received.first() is DashboardEvent.NavigateToChargeEdit)
    }

    @Test
    fun onFabClick_emitsNothing_whenNoCar() = runTest {
        val (vm, _) = build(activeCarId = -1L)
        vm.uiState.first { it.activeCarId == -1L }
        vm.onFabClick()
        assertNull(vm.events.replayCache.firstOrNull())
    }
}
