package org.spsl.evtracker.data.backup

import com.google.api.client.http.HttpResponseException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource
import org.spsl.evtracker.testing.FakeLocationReader
import java.io.IOException

class DriveBackupRepositoryTest {

    private val serializer = BackupSerializer()

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        locations: List<CustomLocationEntity> = emptyList(),
        auth: FakeDriveAuthManager = FakeDriveAuthManager(),
        remote: FakeDriveRemoteSource = FakeDriveRemoteSource(),
    ): Setup {
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries().also { it.seed(events) }
        val locReader = FakeLocationReader(locations)
        val repo = DriveBackupRepository(
            auth,
            remote,
            serializer,
            carReader,
            queries,
            locReader,
            org.spsl.evtracker.testing.FakeNowProvider(),
        )
        return Setup(repo, auth, remote)
    }

    private data class Setup(
        val repo: DriveBackupRepository,
        val auth: FakeDriveAuthManager,
        val remote: FakeDriveRemoteSource,
    )

    @Test
    fun backup_noExistingFile_callsCreate() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.repo.backupCurrentData()
        assertNotNull(s.remote.lastUploadedBytes())
        assertEquals("fake-file-id", s.remote.seededFileId())
    }

    @Test
    fun backup_existingFile_callsUpdate() = runTest {
        val seed = serializer.toJson(BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L))
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.seed(seed.toByteArray(Charsets.UTF_8))
        s.repo.backupCurrentData()
        // Same fileId retained — update path.
        assertEquals("fake-file-id", s.remote.seededFileId())
        // The body was overwritten with current state (one car).
        val parsed = serializer.fromJson(s.remote.lastUploadedBytes()!!.toString(Charsets.UTF_8))
        assertEquals(1, parsed.cars.size)
    }

    @Test
    fun backup_serializerRoundTripPreservesAllFields() = runTest {
        val car = CarEntity(id = 1, name = "T", createdAt = 5L)
        val event = ChargeEventEntity(
            id = 7, carId = 1, eventDate = 1L,
            odometerKm = 100.0, kwhAdded = 10.0, chargeType = ChargeType.DC_FAST,
            costTotal = 5.0, costPerKwh = 0.5, currency = "EUR",
            location = "Home", note = "n", createdAt = 0L,
        )
        val loc = CustomLocationEntity(id = 1, label = "Home", useCount = 1, lastUsed = 9L)
        val s = build(cars = listOf(car), events = listOf(event), locations = listOf(loc))
        s.repo.backupCurrentData()
        val parsed = serializer.fromJson(s.remote.lastUploadedBytes()!!.toString(Charsets.UTF_8))
        assertEquals(listOf(car), parsed.cars.map { it.toEntity() })
        assertEquals(listOf(event), parsed.chargeEvents.map { it.toEntity() })
        assertEquals(listOf(loc), parsed.customLocations.map { it.toEntity() })
    }

    @Test
    fun backup_silentTokenFailed_throwsDriveAuthRequired() = runTest {
        val auth = FakeDriveAuthManager(nextResult = DriveAuthManager.AuthResult.Failed("revoked"))
        val s = build(auth = auth)
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }

    @Test
    fun read_noFileId_returnsNull() = runTest {
        val s = build()
        assertNull(s.repo.readRemoteBackup())
    }

    @Test
    fun read_existingFile_returnsBody() = runTest {
        val s = build()
        s.remote.seed("hello".toByteArray(Charsets.UTF_8))
        assertEquals("hello", s.repo.readRemoteBackup())
    }

    @Test
    fun backup_drive401_translatesToDriveAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = HttpResponseException
            .Builder(401, "Unauthorized", com.google.api.client.http.HttpHeaders())
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }

    @Test
    fun backup_drive403QuotaExceeded_propagatesAsIOException() = runTest {
        val s = build()
        val body = """{"error":{"errors":[{"reason":"quotaExceeded"}],"code":403}}"""
        s.remote.failNext = HttpResponseException
            .Builder(403, "Forbidden", com.google.api.client.http.HttpHeaders())
            .setContent(body)
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected IOException, not DriveAuthRequiredException")
        } catch (e: DriveAuthRequiredException) {
            fail("403 quotaExceeded must NOT translate to auth: $e")
        } catch (_: IOException) {
            // ok — Worker will retry
        }
    }

    @Test
    fun backup_drive403UnknownReason_treatedAsAuthRequired() = runTest {
        // Spec §6.1: "Drive 403 with unknown reason / unparseable body" → conservative auth.
        // Locks in the implementation's `return reason !in QUOTA_REASONS` branch so a
        // future refactor doesn't accidentally let unknown 403 reasons through as
        // retryable IOException.
        val s = build()
        val body = """{"error":{"errors":[{"reason":"someNewReason"}],"code":403}}"""
        s.remote.failNext = HttpResponseException
            .Builder(403, "Forbidden", com.google.api.client.http.HttpHeaders())
            .setContent(body)
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException for unknown 403 reason")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }

    @Test
    fun backup_drive403UnparseableBody_treatedAsAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = HttpResponseException
            .Builder(403, "Forbidden", com.google.api.client.http.HttpHeaders())
            .setContent("not json at all")
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException for unparseable 403 body")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }
}
