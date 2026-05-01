package org.spsl.evtracker.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeDataResetTransactionRunner
import org.spsl.evtracker.testing.FakeLocationWriter
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Before fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val vm: MainViewModel,
        val reader: FakeSettingsReader,
        val writer: FakeSettingsWriter,
        val runner: FakeDataResetTransactionRunner,
        val scheduler: FakeBackupScheduler,
    )

    private fun build(
        resetInProgress: Boolean = false,
        setupComplete: Boolean = true,
        failNext: Throwable? = null,
    ): Rig {
        val reader = FakeSettingsReader(
            resetInProgressInit = resetInProgress,
            setupCompleteInit = setupComplete,
        )
        val writer = FakeSettingsWriter()
        val eventStore = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val locStore = MutableStateFlow<List<CustomLocationEntity>>(emptyList())
        FakeChargeEventWriter(eventStore)
        FakeLocationWriter(locStore)
        val carRepo = FakeCarRepository()
        val runner = FakeDataResetTransactionRunner {
            eventStore.value = emptyList()
            locStore.value = emptyList()
            carRepo.seed(emptyList())
        }
        runner.failNext = failNext
        val scheduler = FakeBackupScheduler()
        val useCase = ResetAllDataUseCase(runner, writer, scheduler, org.spsl.evtracker.testing.FakeWidgetRefresher())
        val vm = MainViewModel(reader, writer, useCase)
        return Rig(vm, reader, writer, runner, scheduler)
    }

    @Test fun startup_resetInProgressFalse_setupComplete_emitsReadyTrue_doesNotRunUseCase() = runTest {
        val rig = build(resetInProgress = false, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        assertEquals(0, rig.runner.clearCallCount)
        val terminal = collected.last()
        assertTrue("expected Ready but was $terminal", terminal is MainViewModel.StartupState.Ready)
        assertEquals(true, (terminal as MainViewModel.StartupState.Ready).setupComplete)
    }

    @Test fun startup_resetInProgressFalse_setupIncomplete_emitsReadyFalse() = runTest {
        val rig = build(resetInProgress = false, setupComplete = false)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        val terminal = collected.last()
        assertTrue("expected Ready but was $terminal", terminal is MainViewModel.StartupState.Ready)
        assertEquals(false, (terminal as MainViewModel.StartupState.Ready).setupComplete)
    }

    @Test fun startup_resetInProgressTrue_recoverySuccess_emitsReady_andClearsFlag() = runTest {
        val rig = build(resetInProgress = true, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        assertEquals(1, rig.runner.clearCallCount)
        assertFalse(rig.writer.resetInProgress)
        val terminal = collected.last()
        assertTrue("expected Ready but was $terminal", terminal is MainViewModel.StartupState.Ready)
    }

    @Test fun startup_recoveryThrows_emitsRecoveryFailed_andLeavesFlag() = runTest {
        val rig = build(
            resetInProgress = true,
            failNext = IllegalStateException("rooms exploded"),
        )
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        val terminal = collected.last()
        assertTrue(
            "expected RecoveryFailed but was $terminal",
            terminal is MainViewModel.StartupState.RecoveryFailed,
        )
        assertTrue(
            "resetInProgress must remain true so the next launch re-runs recovery",
            rig.writer.resetInProgress,
        )
    }

    @Test fun retry_afterFailure_clearsFailure_andReachesReady() = runTest {
        val rig = build(
            resetInProgress = true,
            failNext = IllegalStateException("first attempt"),
        )
        // First pass should have failed.
        assertTrue(rig.vm.startupState.value is MainViewModel.StartupState.RecoveryFailed)
        // Recovery left the flag on; reader still reflects true. Retry: failNext is
        // already cleared by the failed call (FakeDataResetTransactionRunner consumes it).
        rig.vm.runStartupSequence()
        val terminal = rig.vm.startupState.value
        assertTrue("expected Ready but was $terminal", terminal is MainViewModel.StartupState.Ready)
        // FakeDataResetTransactionRunner only increments on success, so 1 success after 1 fail = 1.
        assertEquals(1, rig.runner.clearCallCount)
        assertFalse(rig.writer.resetInProgress)
    }

    @Test fun runStartupSequence_secondPassAfterReady_emitsAnotherReady() = runTest {
        val rig = build(resetInProgress = false, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        rig.vm.runStartupSequence()
        job.cancel()
        // We do not assert exact emission order — only that the terminal state is Ready
        // and the in-flight guard did not deadlock.
        val terminal = collected.last()
        assertTrue("expected Ready but was $terminal", terminal is MainViewModel.StartupState.Ready)
    }
}
