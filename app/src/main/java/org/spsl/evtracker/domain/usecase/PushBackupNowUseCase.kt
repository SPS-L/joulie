// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject

/**
 * TASK-31: forces an immediate Drive snapshot upload on the user's
 * direct request, bypassing [org.spsl.evtracker.domain.backup.BackupScheduler]
 * so the UI gets synchronous-feeling feedback.
 *
 * The auto-backup worker remains the only writer enqueued through
 * WorkManager — manual push is one extra path, not a replacement.
 *
 * [lastBackupAt] is only updated when the upload returns
 * [BackupResult.Success]; failure paths leave the timestamp alone so the
 * UI's "Last backup at …" hint stays truthful.
 */
class PushBackupNowUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val now: NowProvider,
) {
    suspend operator fun invoke(): BackupResult {
        val result = backupRepository.backupCurrentData()
        if (result is BackupResult.Success) {
            settingsWriter.setLastBackupAt(now.nowMillis())
        }
        return result
    }
}
