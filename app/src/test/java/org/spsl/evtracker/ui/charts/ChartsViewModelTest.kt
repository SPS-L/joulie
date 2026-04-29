package org.spsl.evtracker.ui.charts

import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
