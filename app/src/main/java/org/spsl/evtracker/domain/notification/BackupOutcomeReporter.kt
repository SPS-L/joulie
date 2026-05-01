package org.spsl.evtracker.domain.notification

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates a [BackupResult] from `DriveBackupRepository` into:
 *  - a counter update on `consecutiveBackupFailures` (DataStore);
 *  - a notification call on [BackupNotifier].
 *
 * Lives in domain so the orchestration is JVM-testable without booting
 * `NotificationManager` or WorkManager. The worker is a thin caller that
 * just forwards its `BackupResult` here before translating to its own
 * `Result.success()` / `Result.failure()`.
 */
@Singleton
class BackupOutcomeReporter @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val notifier: BackupNotifier,
) {
    suspend fun onResult(result: BackupResult) {
        when (result) {
            BackupResult.Success -> {
                settingsWriter.setConsecutiveBackupFailures(0)
                notifier.clearAll()
            }
            BackupResult.AuthRequired -> {
                val next = settingsReader.consecutiveBackupFailures.first() + 1
                settingsWriter.setConsecutiveBackupFailures(next)
                notifier.notifyAuthRequired()
            }
            is BackupResult.Failure -> {
                val next = settingsReader.consecutiveBackupFailures.first() + 1
                settingsWriter.setConsecutiveBackupFailures(next)
                if (next >= CHRONIC_FAILURE_THRESHOLD) notifier.notifyChronicFailure()
            }
        }
    }

    companion object {
        const val CHRONIC_FAILURE_THRESHOLD = 3
    }
}
