package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    /**
     * Active car id, or `-1L` when none. Storage backing is `intPreferencesKey`
     * for backward-compat with v1.0.x DataStore values; the repository widens
     * to `Long` at the boundary so callers see the same type as the entity PK.
     * One user with thousands of cars over decades won't exceed `Int.MAX_VALUE`.
     */
    val activeCarId: Flow<Long>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
    val driveEnabled: Flow<Boolean>

    /** Null when no successful backup has been recorded yet. */
    val lastBackupAt: Flow<Long?>

    /** F1: theme is "system" | "light" | "dark". */
    val theme: Flow<String>

    /**
     * F1: durable flag set true at start of `ResetAllDataUseCase`, cleared at the end.
     * `MainActivity` reads this at startup; if true, the use case re-runs to completion
     * before any UI mounts so the user never reaches an inconsistent state.
     */
    val resetInProgress: Flow<Boolean>

    /**
     * F1 wizard gate. False until the wizard's finish step writes primaryMetric,
     * distanceUnit, currency together with this flag. Settings → Reset preferences
     * sets it back to false.
     */
    val setupComplete: Flow<Boolean>

    /**
     * TASK-19: streak counter incremented on each Drive backup failure
     * (Auth-required or generic) and reset to 0 on success. `MainActivity`
     * reads this to decide when to prompt for `POST_NOTIFICATIONS`.
     */
    val consecutiveBackupFailures: Flow<Int>

    /**
     * TASK-19: sticky once true. Set by `MainActivity` when the user denies
     * `POST_NOTIFICATIONS`. Once true, the rationale dialog never re-fires.
     */
    val notificationPermissionDenied: Flow<Boolean>

    /**
     * TASK-54: ISO-8601 `exported_at` string of the Drive snapshot most recently
     * offered to (and Skipped or Restored by) the user. Empty string = none.
     * Compared verbatim against the `exported_at` field of incoming remote
     * backups in `SettingsViewModel.onDriveAuthGranted` to suppress the
     * destructive restore-prompt loop. Reset on `WipeRemoteBackupUseCase` success.
     */
    val lastSeenRemoteBackupExportedAt: Flow<String>
}
