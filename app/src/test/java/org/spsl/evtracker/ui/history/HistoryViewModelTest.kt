package org.spsl.evtracker.ui.history

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.HistoryEvent
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.usecase.DeleteChargeEventUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @Before fun setMainDispatcher() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun build(
        events: List<ChargeEventEntity> = emptyList(),
        activeCarId: Int = 1,
        distanceUnit: String = "km",
    ): VmFixture {
        val store = MutableStateFlow(events)
        val queries = FakeChargeEventQueries(store)
        val writer = FakeChargeEventWriter(store)
        val scheduler = FakeBackupScheduler()
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId, distanceUnitInit = distanceUnit)
        val delete = DeleteChargeEventUseCase(writer, scheduler)
        val vm = HistoryViewModel(queries, delete, reader)
        return VmFixture(vm, queries, writer, scheduler, store)
    }

    private data class VmFixture(
        val vm: HistoryViewModel,
        val queries: FakeChargeEventQueries,
        val writer: FakeChargeEventWriter,
        val scheduler: FakeBackupScheduler,
        val store: MutableStateFlow<List<ChargeEventEntity>>,
    )

    private fun event(id: Int, date: Long, type: ChargeType = ChargeType.AC, carId: Int = 1) = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = date,
        odometerKm = id * 100.0,
        kwhAdded = 10.0,
        chargeType = type,
        createdAt = 0L,
    )

    @Test
    fun noActiveCar_emitsEmpty() = runTest {
        val (vm, _, _, _, _) = build(activeCarId = -1)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.rows.isEmpty())
    }

    @Test
    fun eventsLoadedAndSortedNewestFirst() = runTest {
        val (vm, _, _, _, _) = build(
            events = listOf(
                event(1, 100L),
                event(2, 200L),
                event(3, 300L),
            ),
        )
        val state = vm.uiState.first { it.rows.size == 3 }
        assertEquals(listOf(3, 2, 1), state.rows.map { it.event.id })
    }

    @Test
    fun filterAc_filtersDcOut() = runTest {
        val (vm, _, _, _, _) = build(
            events = listOf(
                event(1, 100L, ChargeType.AC),
                event(2, 200L, ChargeType.DC_FAST),
                event(3, 300L, ChargeType.AC),
            ),
        )
        vm.uiState.first { it.rows.size == 3 }
        vm.setFilter(ChargeTypeFilter.AC)
        val state = vm.uiState.first { it.filter == ChargeTypeFilter.AC && it.rows.size == 2 }
        assertTrue(state.rows.all { it.event.chargeType == ChargeType.AC })
    }

    @Test
    fun filterDc_filtersAcOut() = runTest {
        val (vm, _, _, _, _) = build(
            events = listOf(
                event(1, 100L, ChargeType.AC),
                event(2, 200L, ChargeType.DC_FAST),
                event(3, 300L, ChargeType.AC),
            ),
        )
        vm.uiState.first { it.rows.size == 3 }
        vm.setFilter(ChargeTypeFilter.DC)
        val state = vm.uiState.first { it.filter == ChargeTypeFilter.DC && it.rows.size == 1 }
        assertEquals(ChargeType.DC_FAST, state.rows.single().event.chargeType)
    }

    @Test
    fun swipeDelete_after5s_callsDeleteUseCase() = runTest {
        val (vm, _, _, _, store) = build(events = listOf(event(7, 100L)))
        vm.uiState.first { it.rows.size == 1 }
        vm.onSwipeDelete(event(7, 100L))
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, store.value.size)
    }

    @Test
    fun swipeDelete_undo_cancelsTimer() = runTest {
        val (vm, _, _, _, store) = build(events = listOf(event(7, 100L)))
        vm.uiState.first { it.rows.size == 1 }
        vm.onSwipeDelete(event(7, 100L))
        runCurrent()
        vm.onUndoDelete(7)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, store.value.size)
        assertFalse(vm.uiState.value.rows.first { it.event.id == 7 }.isPendingDelete)
    }

    @Test
    fun multipleConcurrentDeletes_eachTracked() = runTest {
        val (vm, _, _, _, store) = build(events = listOf(event(1, 100L), event(2, 200L)))
        vm.uiState.first { it.rows.size == 2 }
        vm.onSwipeDelete(event(1, 100L))
        vm.onSwipeDelete(event(2, 200L))
        runCurrent()
        vm.onUndoDelete(1)
        advanceTimeBy(5_001)
        runCurrent()
        // Only event 2 was committed.
        assertEquals(listOf(1), store.value.map { it.id })
    }

    @Test
    fun milesUnit_displayOdometerConverted() = runTest {
        val (vm, _, _, _, _) = build(events = listOf(event(1, 100L)), distanceUnit = "miles")
        val state = vm.uiState.first { it.rows.size == 1 }
        // event(1, 100L) has odometerKm = 100.0 → 62.137 mi
        assertEquals(62.137, state.rows.single().displayOdometer, 0.005)
    }

    @Test
    fun onRowClick_emitsNavigateEvent() = runTest {
        val (vm, _, _, _, _) = build()
        val received = mutableListOf<HistoryEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onRowClick(11)
        advanceUntilIdle()
        job.cancel()
        assertTrue("got $received", received.isNotEmpty())
        val event = received.first()
        assertTrue(event is HistoryEvent.NavigateToEdit)
        assertEquals(11, (event as HistoryEvent.NavigateToEdit).eventId)
    }

    @Test
    fun swipeDelete_emitsUndoSnackbarEvent() = runTest {
        val (vm, _, _, _, _) = build(events = listOf(event(7, 100L)))
        vm.uiState.first { it.rows.size == 1 }
        val received = mutableListOf<HistoryEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.onSwipeDelete(event(7, 100L))
        runCurrent()
        job.cancel()
        assertTrue("got $received", received.isNotEmpty())
        val event = received.first()
        assertTrue(event is HistoryEvent.ShowUndoSnackbar)
        assertEquals(7, (event as HistoryEvent.ShowUndoSnackbar).eventId)
    }

    @Test
    fun pendingRow_stillPresentForUndoButFlaggedAsPending() = runTest {
        val (vm, _, _, _, _) = build(events = listOf(event(7, 100L)))
        vm.uiState.first { it.rows.size == 1 }
        vm.onSwipeDelete(event(7, 100L))
        runCurrent()
        // BEFORE the 5s timer expires, the row stays in rows with isPendingDelete=true
        assertNotNull(vm.uiState.value.rows.firstOrNull { it.event.id == 7 })
        assertTrue(vm.uiState.value.rows.first { it.event.id == 7 }.isPendingDelete)
    }
}
