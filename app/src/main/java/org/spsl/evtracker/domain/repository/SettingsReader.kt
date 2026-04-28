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
}
