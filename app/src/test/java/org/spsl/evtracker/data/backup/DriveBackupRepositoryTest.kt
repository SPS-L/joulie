package org.spsl.evtracker.data.backup

import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeNowProvider
import java.io.IOException
import java.net.UnknownHostException

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
            FakeNowProvider(),
        )
        return Setup(repo, auth, remote)
    }

    private data class Setup(
        val repo: DriveBackupRepository,
        val auth: FakeDriveAuthManager,
        val remote: FakeDriveRemoteSource,
    )

    private fun http(status: Int, message: String, body: String? = null): HttpResponseException {
        val builder = HttpResponseException.Builder(status, message, HttpHeaders())
        if (body != null) builder.setContent(body)
        return builder.build()
    }

    // -- Happy path -----------------------------------------------------------

    @Test
    fun backup_noExistingFile_callsCreate_andReturnsSuccess() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        val result = s.repo.backupCurrentData()
        assertEquals(BackupResult.Success, result)
        assertNotNull(s.remote.lastUploadedBytes())
        assertEquals("fake-file-id", s.remote.seededFileId())
    }

    @Test
    fun backup_existingFile_callsUpdate_andReturnsSuccess() = runTest {
        val seed = serializer.toJson(
            BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L),
        )
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.seed(seed.toByteArray(Charsets.UTF_8))
        val result = s.repo.backupCurrentData()
        assertEquals(BackupResult.Success, result)
        assertEquals("fake-file-id", s.remote.seededFileId())
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
        assertEquals(BackupResult.Success, s.repo.backupCurrentData())
        val parsed = serializer.fromJson(s.remote.lastUploadedBytes()!!.toString(Charsets.UTF_8))
        assertEquals(listOf(car), parsed.cars.map { it.toEntity() })
        assertEquals(listOf(event), parsed.chargeEvents.map { it.toEntity() })
        assertEquals(listOf(loc), parsed.customLocations.map { it.toEntity() })
    }

    // -- Auth handling --------------------------------------------------------

    @Test
    fun backup_silentTokenFailed_returnsAuthRequired() = runTest {
        val auth = FakeDriveAuthManager(nextResult = DriveAuthManager.AuthResult.Failed("revoked"))
        val s = build(auth = auth)
        assertEquals(BackupResult.AuthRequired, s.repo.backupCurrentData())
    }

    @Test
    fun backup_drive401_returnsAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = http(401, "Unauthorized")
        assertEquals(BackupResult.AuthRequired, s.repo.backupCurrentData())
    }

    @Test
    fun backup_drive403UnknownReason_returnsAuthRequired() = runTest {
        // Conservative auth path — locks in the implementation's
        // "unknown 403 reason" branch so a future refactor doesn't accidentally
        // let unknown reasons through as retryable IOException.
        val s = build()
        s.remote.failNext = http(
            403,
            "Forbidden",
            """{"error":{"errors":[{"reason":"someNewReason"}],"code":403}}""",
        )
        assertEquals(BackupResult.AuthRequired, s.repo.backupCurrentData())
    }

    @Test
    fun backup_drive403UnparseableBody_returnsAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = http(403, "Forbidden", "not json at all")
        assertEquals(BackupResult.AuthRequired, s.repo.backupCurrentData())
    }

    // -- Storage full (TASK-07: must NOT be conflated with auth) --------------

    @Test
    fun backup_drive403StorageFull_returnsFailureNotAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = http(
            403,
            "Forbidden",
            """{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"code":403}}""",
        )
        val result = s.repo.backupCurrentData()
        assertTrue("expected Failure but got $result", result is BackupResult.Failure)
        assertEquals("Drive storage full", (result as BackupResult.Failure).reason)
    }

    @Test
    fun backup_drive403StorageFull_doesNotRetry() = runTest {
        val s = build()
        s.remote.failNext = http(
            403,
            "Forbidden",
            """{"error":{"errors":[{"reason":"storageQuotaExceeded"}],"code":403}}""",
        )
        s.remote.failTimes = 99 // would happily fail forever if retried
        s.repo.backupCurrentData()
        // Storage full is non-recoverable — exactly one failure raised.
        assertEquals(1, s.remote.failuresRaised)
    }

    // -- Transient retry (TASK-07: 3 attempts with exp backoff) ---------------

    @Test
    fun backup_drive429_retriesThenSucceeds() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = http(429, "Too Many Requests")
        s.remote.failTimes = 1
        val result = s.repo.backupCurrentData()
        assertEquals(BackupResult.Success, result)
        assertEquals(1, s.remote.failuresRaised)
    }

    @Test
    fun backup_drive500_retriesThenSucceeds() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = http(500, "Internal Server Error")
        s.remote.failTimes = 1
        assertEquals(BackupResult.Success, s.repo.backupCurrentData())
        assertEquals(1, s.remote.failuresRaised)
    }

    @Test
    fun backup_drive403Quota_retriesThenSucceeds() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = http(
            403,
            "Forbidden",
            """{"error":{"errors":[{"reason":"quotaExceeded"}],"code":403}}""",
        )
        s.remote.failTimes = 1
        assertEquals(BackupResult.Success, s.repo.backupCurrentData())
        assertEquals(1, s.remote.failuresRaised)
    }

    @Test
    fun backup_unknownHostException_retriesThenSucceeds() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = UnknownHostException("no internet")
        s.remote.failTimes = 1
        assertEquals(BackupResult.Success, s.repo.backupCurrentData())
        assertEquals(1, s.remote.failuresRaised)
    }

    @Test
    fun backup_transient429_threeFailures_returnsFailure() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = http(429, "Too Many Requests")
        s.remote.failTimes = 99 // exceeds MAX_ATTEMPTS
        val result = s.repo.backupCurrentData()
        assertTrue("expected Failure but got $result", result is BackupResult.Failure)
        assertEquals("HTTP 429", (result as BackupResult.Failure).reason)
        // Exactly MAX_ATTEMPTS transient failures raised — bounded retry budget.
        assertEquals(DriveBackupRepository.MAX_ATTEMPTS, s.remote.failuresRaised)
    }

    @Test
    fun backup_transientNetworkException_threeFailures_returnsFailure() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.failNext = IOException("socket reset")
        s.remote.failTimes = 99
        val result = s.repo.backupCurrentData()
        assertTrue("expected Failure but got $result", result is BackupResult.Failure)
        assertTrue(
            "reason should mention network: ${(result as BackupResult.Failure).reason}",
            result.reason.startsWith("Network failure"),
        )
        assertEquals(DriveBackupRepository.MAX_ATTEMPTS, s.remote.failuresRaised)
    }

    @Test
    fun backup_authError_doesNotRetry() = runTest {
        val s = build()
        s.remote.failNext = http(401, "Unauthorized")
        s.remote.failTimes = 99
        s.repo.backupCurrentData()
        assertEquals(1, s.remote.failuresRaised)
    }

    // -- Read path: contract unchanged (still throws) -------------------------

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
    fun read_drive401_throwsDriveAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = http(401, "Unauthorized")
        s.remote.failTimes = 99
        try {
            s.repo.readRemoteBackup()
            fail("expected DriveAuthRequiredException")
        } catch (_: DriveAuthRequiredException) {
            // ok — read path keeps its existing exception contract for
            // SettingsViewModel / RestoreBackupUseCase callers.
        }
    }

    @Test
    fun read_drive429_retriesThenSucceeds() = runTest {
        val s = build()
        s.remote.seed("hello".toByteArray(Charsets.UTF_8))
        s.remote.failNext = http(429, "Too Many Requests")
        s.remote.failTimes = 1
        // findBackupFileId fails once → second attempt finds the file → download succeeds.
        // Expected attempts: 1 (failed find) + 1 (find) + 1 (download) = 3.
        assertEquals("hello", s.repo.readRemoteBackup())
    }
}
