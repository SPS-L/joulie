package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupScheduler

@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override suspend fun enqueueBackup() {
        // No-op until Task 13 swaps the binding to WorkManagerBackupScheduler.
    }
}
