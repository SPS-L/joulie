package org.spsl.evtracker.ui.settings

import androidx.work.WorkManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }

    private fun build(
        remoteJson: String? = null,
        readThrows: Throwable? = null
    ): Setup {
        val reader = FakeSettingsReader()
        val writer = FakeSettingsWriter()
        val backupRepo = ThrowingBackupRepository(remoteJson, readThrows)
        val scheduler = FakeBackupScheduler()
        val workManager = mock<WorkManager>()
        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = backupRepo,
            backupSerializer = org.spsl.evtracker.domain.service.BackupSerializer(),
            transactionRunner = FakeRestoreTransactionRunner(),
            snapshotWriter = FakeRestoreSnapshotWriter(),
            carReader = FakeCarReader(),
            chargeEventQueries = FakeChargeEventQueries(),
            locationReader = FakeLocationReader(),
            settingsWriter = writer,
            backupScheduler = scheduler
        )
        val vm = SettingsViewModel(reader, writer, backupRepo, scheduler, workManager, restoreUseCase)
        return Setup(vm, reader, writer, backupRepo, scheduler, workManager)
    }

    private data class Setup(
        val vm: SettingsViewModel,
        val reader: FakeSettingsReader,
        val writer: FakeSettingsWriter,
        val backupRepo: ThrowingBackupRepository,
        val scheduler: FakeBackupScheduler,
        val workManager: WorkManager
    )

    /** Like FakeBackupRepository but lets a test throw from readRemoteBackup. */
    private class ThrowingBackupRepository(
        var remoteJson: String? = null,
        var throwOnRead: Throwable? = null
    ) : org.spsl.evtracker.domain.backup.BackupRepository {
        override suspend fun backupCurrentData() {}
        override suspend fun readRemoteBackup(): String? {
            throwOnRead?.let { throw it }
            return remoteJson
        }
    }

    @Test
    fun onDriveAuthGranted_noRemote_setsDriveEnabledAndClearsAuthFlight() = runTest {
        val s = build(remoteJson = null)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        val state = s.vm.uiState.first()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertFalse(state.isAuthInFlight)
        assertNull(state.pendingRestoreLabel)
    }

    @Test
    fun onDriveAuthGranted_remoteFound_emitsRestorePromptAndClearsAuthFlight() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()

        val prompt = received.filterIsInstance<SettingsEvent.ShowRestorePrompt>().firstOrNull()
        assertNotNull("expected ShowRestorePrompt; got $received", prompt)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        assertEquals(prompt!!.backupDateLabel, state.pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsAuthRequired_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = DriveAuthRequiredException())
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_auth_failed, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsIOException_emitsNetworkErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = IOException("offline"))
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_network_error, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthFailed_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthFailed(R.string.drive_consent_cancelled)
        advanceUntilIdle()
        assertEquals(R.string.drive_consent_cancelled, (received.single() as SettingsEvent.ShowError).msgRes)
        assertFalse(s.vm.uiState.first().isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onConfirmRestore_invokesRestoreAndEmitsSuccess() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()                  // primes pendingRestoreLabel
        advanceUntilIdle()

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onConfirmRestore()
        advanceUntilIdle()

        assertTrue(received.any { it is SettingsEvent.RestoreSucceeded })
        // RestoreBackupUseCase sets driveEnabled = true on Success.
        assertTrue(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onSkipRestore_setsDriveEnabledAndClearsPending() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onSkipRestore()
        advanceUntilIdle()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onRestorePromptDismissed_leavesDriveOff() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onRestorePromptDismissed()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onToggleDriveOff_cancelsWorkAndDisables() = runTest {
        val s = build()
        s.vm.onToggleDriveOff()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        verify(s.workManager).cancelUniqueWork(any())
    }
}
