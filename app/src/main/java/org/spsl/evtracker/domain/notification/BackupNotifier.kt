package org.spsl.evtracker.domain.notification

/**
 * Surface for the backup pipeline to inform the user about failures.
 *
 * The implementation is platform-specific (Android `NotificationManager`),
 * but the contract is testable in JVM via fakes that record which methods
 * were called.
 *
 * Contract: callers do not check `POST_NOTIFICATIONS` themselves — the
 * Android implementation no-ops silently when the permission is missing
 * (NotificationManagerCompat behaviour). The permission request flow lives
 * in `MainActivity` and is driven off the consecutive-failure counter, not
 * the notifier directly.
 */
interface BackupNotifier {
    /**
     * Posted (with channel `backup_status`, low importance) once the
     * consecutive-failure counter crosses [BackupOutcomeReporter.CHRONIC_FAILURE_THRESHOLD].
     * Tapping deep-links the user to Settings.
     */
    fun notifyChronicFailure()

    /**
     * Posted (with channel `backup_auth`, default importance) when the
     * worker returns [org.spsl.evtracker.domain.backup.BackupResult.AuthRequired].
     * Higher importance than chronic failure because the user must
     * actively re-consent — auto-backup cannot recover on its own.
     */
    fun notifyAuthRequired()

    /**
     * Cancels both backup notifications. Called on the next successful
     * backup so a recovered failure doesn't leave a stale notification
     * visible.
     */
    fun clearAll()
}
