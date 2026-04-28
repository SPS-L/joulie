package org.spsl.evtracker.data.backup

import com.google.api.client.http.HttpResponseException
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.service.BackupSerializer

@Singleton
class DriveBackupRepository @Inject constructor(
    private val auth: DriveAuthManager,
    private val remote: DriveRemoteSource,
    private val serializer: BackupSerializer,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader
) : BackupRepository {

    override suspend fun backupCurrentData(): Unit = runTranslating {
        val token = requireToken()
        val cars = carReader.observeAll().first()
        val events = cars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val locations = locationReader.observeAll().first()
        val json = serializer.toJson(BackupData.fromEntities(cars, events, locations))
        val bytes = json.toByteArray(Charsets.UTF_8)
        val existing = remote.findBackupFileId(token)
        if (existing == null) remote.createBackup(token, bytes)
        else remote.updateBackup(token, existing, bytes)
    }

    override suspend fun readRemoteBackup(): String? = runTranslating {
        val token = requireToken()
        val fileId = remote.findBackupFileId(token) ?: return@runTranslating null
        remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }

    private suspend fun requireToken(): String =
        when (val r = auth.silentToken()) {
            is DriveAuthManager.AuthResult.Success -> r.accessToken
            else -> throw DriveAuthRequiredException()
        }

    /**
     * Translate Drive HTTP errors at the boundary:
     * - 401 → always auth (token expired/invalid).
     * - 403 with auth reason (`appNotAuthorized`, `insufficientFilePermissions`,
     *   `insufficientPermissions`, `forbidden`) → auth.
     * - 403 with quota/rate reason or unparseable body but not in AUTH_REASONS → IOException
     *   so the Worker retries with exponential backoff.
     * - Unknown reason on 403 → conservative: treat as auth.
     * - Anything else (5xx, transport IOException) propagates unchanged.
     *
     * Result: Worker error rule stays two-branch — DriveAuthRequiredException → failure;
     * anything else IOException → retry.
     */
    private inline fun <T> runTranslating(block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        when {
            e.statusCode == 401 -> throw DriveAuthRequiredException(cause = e)
            e.statusCode == 403 && isAuthReason(e) -> throw DriveAuthRequiredException(cause = e)
            else -> throw e
        }
    }

    private fun isAuthReason(e: HttpResponseException): Boolean {
        val reason = parseFirstErrorReason(e.content)
        // Quota/rate-limit reasons are explicitly NOT auth — they retry.
        if (reason != null && reason in QUOTA_REASONS) return false
        if (reason != null && reason in AUTH_REASONS) return true
        // Unknown / unparseable → conservative auth treatment.
        return reason !in QUOTA_REASONS
    }

    private fun parseFirstErrorReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonObject("error")
                ?.getAsJsonArray("errors")
                ?.firstOrNull()?.asJsonObject
                ?.get("reason")?.asString
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val AUTH_REASONS = setOf(
            "appNotAuthorized",
            "insufficientFilePermissions",
            "insufficientPermissions",
            "forbidden"
        )
        private val QUOTA_REASONS = setOf(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "quotaExceeded"
        )
    }
}
