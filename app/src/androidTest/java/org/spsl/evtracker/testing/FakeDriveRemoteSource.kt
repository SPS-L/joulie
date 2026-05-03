package org.spsl.evtracker.testing

import org.spsl.evtracker.domain.backup.DriveRemoteSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDriveRemoteSource @Inject constructor() : DriveRemoteSource {
    private var fileId: String? = null
    private var body: ByteArray? = null

    /**
     * Throwable to raise from the next [failTimes] calls. After it has been
     * raised that many times, both fields auto-reset and subsequent calls
     * succeed normally. `failTimes = 1` is the legacy single-shot behaviour.
     *
     * Mirrors the JVM-side fake at `app/src/test/.../testing/Fakes.kt` so
     * instrumented tests can exercise the same `DriveBackupRepository`
     * retry-budget paths the JVM `DriveBackupRepositoryTest` already covers.
     */
    var failNext: Throwable? = null
    var failTimes: Int = 1

    /** Total method invocations on this fake (find / create / update / download / delete), including ones that succeed. */
    var attemptCount: Int = 0
        private set

    /** Number of times [failNext] was actually raised as an exception. */
    var failuresRaised: Int = 0
        private set

    override suspend fun findBackupFileId(accessToken: String): String? {
        consumeFailure()
        return fileId
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String {
        consumeFailure()
        fileId = "fake-file-id"
        body = jsonBytes
        return fileId!!
    }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        consumeFailure()
        check(this.fileId == fileId) { "fileId mismatch: had=${this.fileId} got=$fileId" }
        body = jsonBytes
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        consumeFailure()
        return body ?: throw IOException("no body for $fileId")
    }

    override suspend fun deleteBackup(accessToken: String, fileId: String) {
        consumeFailure()
        check(this.fileId == fileId) { "fileId mismatch: had=${this.fileId} got=$fileId" }
        this.fileId = null
        this.body = null
    }

    fun seed(jsonBytes: ByteArray) {
        fileId = "fake-file-id"
        body = jsonBytes
    }
    fun lastUploadedBytes(): ByteArray? = body
    fun seededFileId(): String? = fileId

    private fun consumeFailure() {
        attemptCount++
        val e = failNext ?: return
        failTimes--
        if (failTimes <= 0) {
            failNext = null
            failTimes = 1
        }
        failuresRaised++
        throw e
    }
}
