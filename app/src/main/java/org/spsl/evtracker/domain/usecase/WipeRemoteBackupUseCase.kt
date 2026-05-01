package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject

/**
 * TASK-31: deletes the remote `evtracker_backup.json` from the App Data
 * folder. Local data is untouched. On success, clears `lastBackupAt` so
 * the UI's "Last backup at …" hint doesn't point at a snapshot that no
 * longer exists.
 *
 * The next committed local change will re-enqueue the auto-backup
 * worker, which will create a fresh remote snapshot — the wipe is a
 * point-in-time delete, not an opt-out from auto-backup.
 */
class WipeRemoteBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
) {
    suspend operator fun invoke(): BackupResult {
        val result = backupRepository.deleteRemoteBackup()
        if (result is BackupResult.Success) {
            settingsWriter.setLastBackupAt(0L)
        }
        return result
    }
}
