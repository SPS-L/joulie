package org.spsl.evtracker.ui.locations

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.core.model.ManageLocationsEvent
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeLocationWriter

@OptIn(ExperimentalCoroutinesApi::class)
class ManageLocationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var locationReader: FakeLocationReader
    private lateinit var locationWriter: FakeLocationWriter
    private lateinit var backupScheduler: FakeBackupScheduler

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        locationReader = FakeLocationReader()
        locationWriter = FakeLocationWriter()
        backupScheduler = FakeBackupScheduler()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun build(): ManageLocationsViewModel =
        ManageLocationsViewModel(locationReader, locationWriter, backupScheduler)

    private fun loc(id: Long, label: String, useCount: Int = 1, lastUsed: Long = id) =
        CustomLocationEntity(id = id, label = label, useCount = useCount, lastUsed = lastUsed)

    @Test fun observe_emitsSortedList() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A", 1, 10), loc(2L, "B", 5, 5), loc(3L, "C", 5, 15))
        val vm = build()
        advanceUntilIdle()
        // Sort: useCount DESC, lastUsed DESC ⇒ C(5,15), B(5,5), A(1,10)
        assertEquals(listOf("C", "B", "A"), vm.uiState.value.locations.map { it.label })
    }

    @Test fun swipe_addsToPendingDeletions_emitsSnackbar() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"))
        val vm = build()
        advanceUntilIdle()
        val received = mutableListOf<ManageLocationsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
        vm.onSwipeDelete("A")
        runCurrent()
        assertTrue("A" in vm.uiState.value.pendingDeletions)
        assertTrue(received.any { it is ManageLocationsEvent.ShowUndoSnackbar && it.label == "A" })
        job.cancel()
    }

    @Test fun swipe_then_undo_cancelsJob_removesFromPending() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)
        vm.onUndoDelete("A")
        advanceUntilIdle()
        assertFalse("A" in vm.uiState.value.pendingDeletions)
        // The 5s job got cancelled — backup should NOT have been enqueued.
        assertEquals(0, backupScheduler.enqueueCount)
    }

    @Test fun swipe_then_5sElapses_callsLocationWriterDelete_andEnqueueBackup() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(5_001L)
        advanceUntilIdle()
        assertEquals(1, backupScheduler.enqueueCount)
    }

    @Test fun swipe_multipleLabels_each_has_independent_job() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"), loc(2L, "B"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)
        vm.onSwipeDelete("B")
        advanceTimeBy(2_000L)
        // A's window: 1s left; B's window: 3s left.
        vm.onUndoDelete("A")
        advanceTimeBy(2_000L) // total 6s — B should commit, A undone.
        advanceUntilIdle()
        assertFalse("A" in vm.uiState.value.pendingDeletions)
        assertEquals(1, backupScheduler.enqueueCount) // only B
    }

    @Test fun swipe_then_clearVm_cancelsAllJobs_doesNotCallDelete() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)
        // Force VM destruction via reflection (onCleared is protected).
        val onCleared = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(vm)
        advanceTimeBy(10_000L)
        advanceUntilIdle()
        assertEquals(0, backupScheduler.enqueueCount)
    }

    @Test fun emptyList_uiState_visibleLocationsIsEmpty() = runTest(dispatcher) {
        locationReader.state.value = emptyList()
        val vm = build()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.visibleLocations.isEmpty())
    }

    @Test fun swipe_lastRow_visibleLocationsIsEmpty_during_undo_window() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1L, "A"))
        val vm = build()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.visibleLocations.size)
        vm.onSwipeDelete("A")
        runCurrent()
        assertTrue(vm.uiState.value.visibleLocations.isEmpty())
        vm.onUndoDelete("A")
        runCurrent()
        assertEquals(1, vm.uiState.value.visibleLocations.size)
    }
}
