package org.spsl.evtracker.domain.backup

interface BackupRepository {
    /** Serialize current local state and upload to Drive. */
    suspend fun backupCurrentData()

    /** Download evtracker_backup.json from the App Data folder. Returns null if no remote file. */
    suspend fun readRemoteBackup(): String?
}
