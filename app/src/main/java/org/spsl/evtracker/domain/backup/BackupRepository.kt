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
}
