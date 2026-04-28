package org.spsl.evtracker.domain.backup

/**
 * Drive REST seam. All methods take an explicit [accessToken] because the auth manager
 * refreshes tokens per-call (tokens expire). Implementations execute on Dispatchers.IO.
 *
 * The "appDataFolder" space is hidden from the regular Drive UI but accessible via
 * `files.list?spaces=appDataFolder`.
 */
interface DriveRemoteSource {
    /** Returns the Drive fileId of the existing `evtracker_backup.json` in App Data, or null. */
    suspend fun findBackupFileId(accessToken: String): String?

    /** Creates a new `evtracker_backup.json` in App Data. Returns the new fileId. */
    suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String

    /** Replaces the body of an existing fileId. */
    suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray)

    /** Downloads the body of fileId. */
    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray
}
