package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.NowProvider

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val now: NowProvider,
) : CoroutineWorker(appContext, params) {

    /**
     * The repository owns transient retry (network / 429 / 5xx / quota-403)
     * via its own bounded loop. The worker only translates terminal results
     * to the WorkManager [Result] surface — no `Result.retry()` needed,
     * which keeps WorkManager's outer retry from amplifying the inner one.
     */
    override suspend fun doWork(): Result = when (backupRepository.backupCurrentData()) {
        BackupResult.Success -> {
            settingsWriter.setLastBackupAt(now.nowMillis())
            Result.success()
        }
        BackupResult.AuthRequired -> Result.failure()
        is BackupResult.Failure -> Result.failure()
    }
}
