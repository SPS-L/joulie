package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupRepository

@Singleton
class NoOpBackupRepository @Inject constructor() : BackupRepository {
    override suspend fun backupCurrentData() {
        // No-op until E lands.
    }

    override suspend fun readRemoteBackup(): String? = null
}
