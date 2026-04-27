package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.testing.FakeBackupRepository
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsWriter

class RestoreBackupUseCaseTest {

    private val serializer = BackupSerializer()

    private fun build(
        remoteJson: String? = null,
        callRecorder: MutableList<String>? = null,
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        locations: List<CustomLocationEntity> = emptyList()
    ): RestoreSetup {
        val backupRepo = FakeBackupRepository(remoteJson = remoteJson)
        val transactionRunner = FakeRestoreTransactionRunner(callRecorder)
        val snapshotWriter = FakeRestoreSnapshotWriter(callRecorder)
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries(); queries.seed(events)
        val locationReader = FakeLocationReader(locations)
        val settingsWriter = FakeSettingsWriter()
        val scheduler = FakeBackupScheduler()
        val useCase = RestoreBackupUseCase(
            backupRepo, serializer, transactionRunner, snapshotWriter,
            carReader, queries, locationReader, settingsWriter, scheduler
        )
        return RestoreSetup(useCase, transactionRunner, snapshotWriter, settingsWriter, scheduler)
    }

    private data class RestoreSetup(
        val useCase: RestoreBackupUseCase,
        val txn: FakeRestoreTransactionRunner,
        val snap: FakeRestoreSnapshotWriter,
        val settings: FakeSettingsWriter,
        val scheduler: FakeBackupScheduler
    )

    @Test
    fun noRemoteBackup_setsDriveEnabledAndQueuesBackup() = runTest {
        val s = build(remoteJson = null)
        val r = s.useCase()
        assertEquals(RestoreResult.NoRemoteBackup, r)
        assertTrue(s.settings.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun versionMismatch_doesNotSetDriveEnabled() = runTest {
        val v2 = """{"backup_version":2,"exported_at":"x","cars":[],"charge_events":[],"custom_locations":[]}"""
        val s = build(remoteJson = v2)
        val r = s.useCase()
        assertTrue(r is RestoreResult.VersionMismatch)
        assertEquals(2, (r as RestoreResult.VersionMismatch).actualVersion)
        assertFalse(s.settings.driveEnabled)
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun success_clearsAndImportsAndEnqueuesBackup() = runTest {
        val data = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = listOf(ChargeEventEntity(id = 7, carId = 1, eventDate = 1L, odometerKm = 100.0, kwhAdded = 10.0)),
            locations = listOf(CustomLocationEntity(id = 1, label = "Home", useCount = 1, lastUsed = 0L)),
            now = 0L
        )
        val s = build(remoteJson = serializer.toJson(data))
        val r = s.useCase()
        assertTrue(r is RestoreResult.Success)
        assertEquals(1, (r as RestoreResult.Success).carCount)
        assertEquals(1, r.eventCount)
        assertEquals(1, r.locationCount)
        assertEquals(listOf(CarEntity(id = 1, name = "T", createdAt = 0L)), s.txn.lastCars)
        assertNotNull(s.txn.lastEvents)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun success_writesCacheSnapshotBeforeDestructive() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val recorder = mutableListOf<String>()
        val s = build(remoteJson = serializer.toJson(data), callRecorder = recorder)
        s.useCase()
        val snapIdx = recorder.indexOf("snapshot")
        val txnIdx  = recorder.indexOf("transaction")
        assertTrue("expected snapshot before transaction; recorder=$recorder", snapIdx in 0 until txnIdx)
    }

    @Test
    fun success_setsDriveEnabledAndQueuesBackup() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val s = build(remoteJson = serializer.toJson(data))
        s.useCase()
        assertTrue(s.settings.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun success_setsDriveEnabledAfterTransactionCompletes() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val recorder = mutableListOf<String>()
        val s = build(remoteJson = serializer.toJson(data), callRecorder = recorder)
        s.useCase()
        assertTrue("snapshot must come before transaction", recorder.indexOf("snapshot") < recorder.indexOf("transaction"))
        assertTrue(s.settings.driveEnabled)
    }
}
