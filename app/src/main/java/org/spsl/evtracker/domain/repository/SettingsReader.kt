package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    val activeCarId: Flow<Int>
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
}
