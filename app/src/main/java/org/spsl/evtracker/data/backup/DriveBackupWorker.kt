// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.notification.BackupOutcomeReporter
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.NowProvider

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val now: NowProvider,
    private val outcomeReporter: BackupOutcomeReporter,
) : CoroutineWorker(appContext, params) {

    /**
     * The repository owns transient retry (network / 429 / 5xx / quota-403)
     * via its own bounded loop. The worker only translates terminal results
     * to the WorkManager [Result] surface — no `Result.retry()` needed,
     * which keeps WorkManager's outer retry from amplifying the inner one.
     *
     * TASK-19: side-channels the result through [BackupOutcomeReporter]
     * before translating, so the failure-streak counter and notifications
     * fire whether the user opens the app or not.
     */
    override suspend fun doWork(): Result {
        val result = backupRepository.backupCurrentData()
        outcomeReporter.onResult(result)
        return when (result) {
            BackupResult.Success -> {
                settingsWriter.setLastBackupAt(now.nowMillis())
                Result.success()
            }
            // Repository already exhausted its retry budget (TASK-07);
            // returning Result.retry() would let WorkManager re-amplify it.
            BackupResult.AuthRequired -> Result.failure()
            // Same invariant — see KDoc above and TASK-07 / TASK-36.
            is BackupResult.Failure -> Result.failure()
        }
    }
}
