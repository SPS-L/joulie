package org.spsl.evtracker.domain.backup

/**
 * Requests a backup of current local state.
 *
 * **Contract:** implementations own the `driveEnabled` gate. If Drive backup is disabled,
 * the implementation MUST no-op rather than schedule a Worker. Use cases call [enqueueBackup]
 * unconditionally after every persisted state change — they do NOT read `driveEnabled` themselves.
 *
 * Suspending so implementations can read DataStore for the gate without blocking.
 *
 * Bindings (E):
 * - [org.spsl.evtracker.data.backup.WorkManagerBackupScheduler] reads
 *   `SettingsReader.driveEnabled` and either enqueues a `OneTimeWorkRequest` or no-ops.
 */
interface BackupScheduler {
    suspend fun enqueueBackup()
}
