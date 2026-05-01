package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.NowProvider
import java.io.IOException

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val now: NowProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        backupRepository.backupCurrentData()
        settingsWriter.setLastBackupAt(now.nowMillis())
        Result.success()
    } catch (e: DriveAuthRequiredException) {
        Result.failure()
    } catch (e: BackupVersionMismatch) {
        Result.failure()
    } catch (e: IOException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
