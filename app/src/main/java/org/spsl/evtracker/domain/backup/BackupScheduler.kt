package org.spsl.evtracker.domain.backup

/**
 * Requests a backup of current local state.
 *
 * **Contract:** implementations own the `driveEnabled` gate. If Drive backup is disabled,
 * the implementation MUST no-op rather than schedule a Worker. Use cases (SaveChargeEvent,
 * DeleteChargeEvent, RestoreBackup) call [enqueueBackup] unconditionally after every
 * persisted state change — they do NOT read `driveEnabled` themselves.
 *
 * Bindings:
 * - C ships [org.spsl.evtracker.data.backup.NoOpBackupScheduler] which always no-ops.
 * - E swaps in a WorkManager-backed implementation that reads `driveEnabled` from
 *   SettingsRepository and either schedules a `OneTimeWorkRequest` or no-ops.
 */
interface BackupScheduler {
    fun enqueueBackup()
}
