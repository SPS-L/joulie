// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.backup

import android.util.Log
import com.google.api.client.http.HttpResponseException
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.domain.usecase.NowProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveBackupRepository @Inject constructor(
    private val auth: DriveAuthManager,
    private val remote: DriveRemoteSource,
    private val serializer: BackupSerializer,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
    private val now: NowProvider,
) : BackupRepository {

    override suspend fun backupCurrentData(): BackupResult = try {
        withRetry {
            val token = requireToken()
            val cars = carReader.observeAll().first()
            val events = cars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
            val locations = locationReader.observeAll().first()
            val json = serializer.toJson(
                BackupData.fromEntities(cars, events, locations, now = now.nowMillis()),
            )
            val bytes = json.toByteArray(Charsets.UTF_8)
            val existing = remote.findBackupFileId(token)
            if (existing == null) {
                remote.createBackup(token, bytes)
            } else {
                remote.updateBackup(token, existing, bytes)
            }
        }
        BackupResult.Success
    } catch (e: DriveAuthRequiredException) {
        Log.e(TAG, "Drive consent required or revoked", e)
        BackupResult.AuthRequired
    } catch (e: HttpResponseException) {
        if (isStorageFull(e)) {
            Log.e(TAG, "Drive storage full (HTTP 403 storageQuotaExceeded)", e)
            BackupResult.Failure("Drive storage full", e)
        } else {
            Log.e(TAG, "Drive HTTP ${e.statusCode} after $MAX_ATTEMPTS attempts", e)
            BackupResult.Failure("HTTP ${e.statusCode}", e)
        }
    } catch (e: IOException) {
        Log.e(TAG, "Drive backup network failure after $MAX_ATTEMPTS attempts", e)
        BackupResult.Failure("Network failure: ${e.javaClass.simpleName}", e)
    }

    override suspend fun readRemoteBackup(): String? = withRetry {
        val token = requireToken()
        val fileId = remote.findBackupFileId(token) ?: return@withRetry null
        remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }

    /**
     * TASK-31: deletes the remote `evtracker_backup.json` from the App Data
     * folder, sharing the same retry + error-translation harness as
     * [backupCurrentData]. Returns [BackupResult.Success] when the desired
     * post-state holds — including the no-file case, since "no remote
     * snapshot" is already true and a noisy error here would mask a clean
     * outcome the user just asked for.
     */
    override suspend fun deleteRemoteBackup(): BackupResult = try {
        withRetry {
            val token = requireToken()
            val fileId = remote.findBackupFileId(token) ?: return@withRetry
            remote.deleteBackup(token, fileId)
        }
        BackupResult.Success
    } catch (e: DriveAuthRequiredException) {
        Log.e(TAG, "Drive consent required or revoked on delete", e)
        BackupResult.AuthRequired
    } catch (e: HttpResponseException) {
        Log.e(TAG, "Drive delete HTTP ${e.statusCode} after $MAX_ATTEMPTS attempts", e)
        BackupResult.Failure("HTTP ${e.statusCode}", e)
    } catch (e: IOException) {
        Log.e(TAG, "Drive delete network failure after $MAX_ATTEMPTS attempts", e)
        BackupResult.Failure("Network failure: ${e.javaClass.simpleName}", e)
    }

    private suspend fun requireToken(): String =
        when (val r = auth.silentToken()) {
            is DriveAuthManager.AuthResult.Success -> r.accessToken
            else -> throw DriveAuthRequiredException()
        }

    /**
     * Run [block] up to [MAX_ATTEMPTS] times, sleeping between transient
     * failures with exponential backoff ([BASE_BACKOFF_MS] × 2^attempt).
     *
     * Transient = network IOException, HTTP 429, HTTP 5xx, HTTP 403 with a
     * quota/rate reason. Non-transient (auth, storage full, version
     * mismatch, anything else) bubbles up immediately so the caller can
     * map it to the right [BackupResult] variant or rethrow.
     */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastTransient: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return runTranslating(block)
            } catch (e: HttpResponseException) {
                if (!isTransientHttp(e)) throw e
                lastTransient = e
            } catch (e: DriveAuthRequiredException) {
                throw e
            } catch (e: IOException) {
                lastTransient = e
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                delay(BASE_BACKOFF_MS shl attempt)
            }
        }
        throw lastTransient ?: IOException("backup failed without specific cause")
    }

    /**
     * Translate Drive HTTP errors at the boundary:
     * - 401 → always auth (token expired/invalid).
     * - 403 with auth reason (`appNotAuthorized`, `insufficientFilePermissions`,
     *   `insufficientPermissions`, `forbidden`) → auth.
     * - 403 with quota/rate reason → propagate raw (handled as transient by `withRetry`).
     * - 403 with `storageQuotaExceeded` → propagate raw (`Failure("Drive storage full")`).
     * - 403 with unknown reason / unparseable body → conservative auth.
     * - Anything else (4xx / 5xx / transport IOException) propagates unchanged.
     */
    private suspend fun <T> runTranslating(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        when {
            e.statusCode == 401 -> throw DriveAuthRequiredException(cause = e)
            e.statusCode == 403 && isAuthReason(e) -> throw DriveAuthRequiredException(cause = e)
            else -> throw e
        }
    }

    private fun isTransientHttp(e: HttpResponseException): Boolean {
        if (isStorageFull(e)) return false
        if (e.statusCode == 429) return true
        if (e.statusCode in 500..599) return true
        if (e.statusCode == 403 && parseFirstErrorReason(e.content) in QUOTA_REASONS) return true
        return false
    }

    private fun isStorageFull(e: HttpResponseException): Boolean =
        e.statusCode == 403 && parseFirstErrorReason(e.content) in STORAGE_FULL_REASONS

    private fun isAuthReason(e: HttpResponseException): Boolean {
        val reason = parseFirstErrorReason(e.content)
        // Quota and storage-full reasons are explicitly NOT auth.
        if (reason != null && reason in QUOTA_REASONS) return false
        if (reason != null && reason in STORAGE_FULL_REASONS) return false
        if (reason != null && reason in AUTH_REASONS) return true
        // Unknown / unparseable → conservative auth treatment.
        return true
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
        private const val TAG = "DriveBackupRepository"
        const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 250L
        private val AUTH_REASONS = setOf(
            "appNotAuthorized",
            "insufficientFilePermissions",
            "insufficientPermissions",
            "forbidden",
        )
        private val QUOTA_REASONS = setOf(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "quotaExceeded",
        )
        private val STORAGE_FULL_REASONS = setOf("storageQuotaExceeded")
    }
}
