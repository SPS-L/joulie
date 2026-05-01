package org.spsl.evtracker.domain.backup

/**
 * Terminal status returned by [BackupRepository.backupCurrentData].
 *
 * The repository performs its own bounded transient-retry loop (network,
 * HTTP 429, HTTP 5xx, quota-rate 403). What surfaces here is the result
 * after that loop has settled — the caller never sees an in-flight retry.
 *
 * - [Success]      — backup uploaded.
 * - [AuthRequired] — OAuth consent revoked / expired or 403 with auth reason.
 *                    UI should prompt the user to re-authenticate.
 *                    No internal retry helps here.
 * - [Failure]      — non-auth, non-success terminal outcome. Either a
 *                    permanent error (Drive storage full, version mismatch)
 *                    or a transient error that didn't recover after the
 *                    retry budget. `reason` is a short human-readable tag
 *                    for logs / notifications; `cause` carries the
 *                    underlying throwable for diagnostics.
 */
sealed class BackupResult {
    object Success : BackupResult()
    object AuthRequired : BackupResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : BackupResult()
}
