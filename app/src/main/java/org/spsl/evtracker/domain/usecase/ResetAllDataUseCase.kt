package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject

class ResetAllDataUseCase @Inject constructor(
    private val resetRunner: DataResetTransactionRunner,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler,
) {
    suspend operator fun invoke() {
        // Step 1 — atomic flag flip; if we crash between here and Step 3, the next
        // launch's MainActivity auto-recovery finishes the reset before any UI mounts.
        settingsWriter.markGlobalResetInProgress()

        // Step 2 — atomic Room transaction across the three tables.
        resetRunner.clearAllTables()

        // Step 3 — clear the flag. AFTER this commits, the reset is committed.
        settingsWriter.setResetInProgress(false)

        // Step 4 — best-effort post-reset Drive enqueue. runCatching ensures a
        // scheduler/DataStore/WorkManager failure cannot stick the flag (already cleared
        // in Step 3). Next snapshot-triggering action will re-enqueue if Drive is on.
        runCatching { backupScheduler.enqueueBackup() }
    }
}
