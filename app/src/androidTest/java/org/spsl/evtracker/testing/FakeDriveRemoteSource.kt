package org.spsl.evtracker.testing

import org.spsl.evtracker.domain.backup.DriveRemoteSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDriveRemoteSource @Inject constructor() : DriveRemoteSource {
    private var fileId: String? = null
    private var body: ByteArray? = null
    var failNext: Throwable? = null

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
        val e = failNext ?: return
        failNext = null
        throw e
    }
}
