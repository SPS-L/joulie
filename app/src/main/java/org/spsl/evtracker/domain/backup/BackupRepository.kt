// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.backup

interface BackupRepository {
    /**
     * Serialize current local state and upload to Drive.
     *
     * Implementations retry transient failures internally (network,
     * HTTP 429, HTTP 5xx, quota-rate 403) up to a bounded budget and
     * return a terminal [BackupResult] — the caller never sees a
     * partial / mid-retry state. Non-recoverable errors (auth revoked,
     * Drive storage full, schema mismatch) short-circuit the retry
     * loop and surface as the corresponding [BackupResult] variant.
     */
    suspend fun backupCurrentData(): BackupResult

    /** Download evtracker_backup.json from the App Data folder. Returns null if no remote file. */
    suspend fun readRemoteBackup(): String?

    /**
     * Deletes the remote `evtracker_backup.json` snapshot from the App Data folder.
     * Returns [BackupResult.Success] on completion (including when no file existed —
     * the desired post-state is "no remote file", which is already true), and the
     * usual auth / failure variants per the [backupCurrentData] contract.
     *
     * Drive must be authorised; the caller normally gates on
     * [SettingsReader.driveEnabled] before invoking.
     */
    suspend fun deleteRemoteBackup(): BackupResult
}
