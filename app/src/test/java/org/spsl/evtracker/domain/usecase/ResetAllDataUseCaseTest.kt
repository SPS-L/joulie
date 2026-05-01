package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeDataResetTransactionRunner
import org.spsl.evtracker.testing.FakeLocationWriter
import org.spsl.evtracker.testing.FakeSettingsWriter
import java.io.IOException

class ResetAllDataUseCaseTest {

    private fun build(
        recorder: MutableList<String> = mutableListOf(),
        scheduler: BackupScheduler = FakeBackupScheduler(),
    ): TestRig {
        val eventStore = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val eventWriter = FakeChargeEventWriter(eventStore)
        val locStore = MutableStateFlow<List<CustomLocationEntity>>(emptyList())
        val locWriter = FakeLocationWriter(locStore)
        val carRepo = FakeCarRepository()
        val settings = FakeSettingsWriter(callRecorder = recorder)
        val runner = FakeDataResetTransactionRunner(callRecorder = recorder) {
            eventStore.value = emptyList()
            locStore.value = emptyList()
            carRepo.seed(emptyList())
        }
        val useCase = ResetAllDataUseCase(runner, settings, scheduler)
        return TestRig(useCase, runner, settings, recorder, eventStore, locStore, carRepo, scheduler)
    }

    private class TestRig(
        val useCase: ResetAllDataUseCase,
        val runner: FakeDataResetTransactionRunner,
        val settings: FakeSettingsWriter,
        val recorder: MutableList<String>,
        val eventStore: MutableStateFlow<List<ChargeEventEntity>>,
        val locStore: MutableStateFlow<List<CustomLocationEntity>>,
        val carRepo: FakeCarRepository,
        val scheduler: BackupScheduler,
    )

    @Test fun invoke_callsResetRunner_clearAllTables() = runTest {
        val rig = build()
        rig.useCase()
        assertEquals(1, rig.runner.clearCallCount)
    }

    @Test fun invoke_setsActiveCarIdToMinusOne_andSetupCompleteFalse_andResetInProgressTrue_atStart() = runTest {
        val rig = build()
        rig.eventStore.value = listOf(
            ChargeEventEntity(id = 1, carId = 7, eventDate = 1L, odometerKm = 0.0, kwhAdded = 0.0, chargeType = ChargeType.AC, createdAt = 0L),
        )
        rig.useCase()
        // markGlobalResetInProgress wrote all three keys at start; final state of writer:
        assertEquals(-1, rig.settings.activeCarId)
        assertFalse(rig.settings.setupComplete)
    }

    @Test fun invoke_setsResetInProgressFalse_atEnd() = runTest {
        val rig = build()
        rig.useCase()
        assertFalse(rig.settings.resetInProgress)
    }

    @Test fun invoke_enqueuesBackup() = runTest {
        val scheduler = FakeBackupScheduler()
        val rig = build(scheduler = scheduler)
        rig.useCase()
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test fun invoke_orders_markResetInProgress_then_clearTables_then_clearFlag_then_enqueueBackup() = runTest {
        val recorder = mutableListOf<String>()
        // A scheduler that records itself so we can place it in the ordering:
        val recordingScheduler = object : BackupScheduler {
            override suspend fun enqueueBackup() {
                recorder.add("enqueueBackup")
            }
        }
        val rig = build(recorder = recorder, scheduler = recordingScheduler)
        rig.useCase()
        // Expected sequence: markGlobalResetInProgress, clearAllTables, setResetInProgress(false), enqueueBackup
        val markIdx = recorder.indexOf("markGlobalResetInProgress")
        val clearIdx = recorder.indexOf("clearAllTables")
        val flagOff = recorder.indexOf("setResetInProgress(false)")
        val enqueueIdx = recorder.indexOf("enqueueBackup")
        assertTrue("mark ($markIdx) must precede clear ($clearIdx)", markIdx in 0 until clearIdx)
        assertTrue("clear ($clearIdx) must precede flag-off ($flagOff)", clearIdx < flagOff)
        assertTrue("flag-off ($flagOff) must precede enqueue ($enqueueIdx)", flagOff < enqueueIdx)
    }

    @Test fun invoke_idempotent_secondCallOnEmptyState_completesAndClearsFlag() = runTest {
        val rig = build()
        rig.useCase()
        rig.useCase() // Second call on already-empty state
        assertFalse(rig.settings.resetInProgress)
        assertEquals(2, rig.runner.clearCallCount)
    }

    @Test fun invoke_throwingFromResetRunner_doesNotClearFlag() = runTest {
        val rig = build()
        rig.runner.failNext = IllegalStateException("rooms exploded")
        try {
            rig.useCase()
            error("expected throw")
        } catch (_: IllegalStateException) {
            // expected
        }
        // Flag stays true ⇒ next launch's MainActivity auto-recovery picks up.
        assertTrue(rig.settings.resetInProgress)
    }

    @Test fun invoke_enqueueBackupThrowing_doesNotPropagate_flagIsAlreadyFalse() = runTest {
        val throwingScheduler = object : BackupScheduler {
            override suspend fun enqueueBackup() = throw IOException("WorkManager exploded")
        }
        val rig = build(scheduler = throwingScheduler)
        rig.useCase() // Should not throw — Step 4 swallows.
        assertFalse(rig.settings.resetInProgress)
        assertEquals(1, rig.runner.clearCallCount)
    }
}
