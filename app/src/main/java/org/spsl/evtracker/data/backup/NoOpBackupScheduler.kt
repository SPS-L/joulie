package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupScheduler

@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override fun enqueueBackup() {
        // No-op until E lands. See BackupScheduler KDoc for the contract.
    }
}
