// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

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

    companion object {
        /** Unique name for the WorkManager backup work. UI consumers cancel by this name on toggle-off. */
        const val UNIQUE_WORK_NAME = "drive_backup"
    }
}
