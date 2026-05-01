package org.spsl.evtracker.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT = stringPreferencesKey("distanceUnit")
    val CURRENCY = stringPreferencesKey("currency")

    /** Sentinel value `-1` means no car selected (DataStore default when key absent). */
    val ACTIVE_CAR_ID = intPreferencesKey("activeCarId") // consumed by Sub-project B
    val DRIVE_ENABLED = booleanPreferencesKey("driveEnabled") // consumed by Sub-project E
    val THEME = stringPreferencesKey("theme")
    val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt") // consumed by Sub-project E

    /** F1: durable interrupted-reset flag. See ResetAllDataUseCase + MainActivity. */
    val RESET_IN_PROGRESS = booleanPreferencesKey("resetInProgress")

    /** TASK-19: streak counter incremented on each Drive backup failure, reset on success. */
    val CONSECUTIVE_BACKUP_FAILURES = intPreferencesKey("consecutiveBackupFailures")

    /** TASK-19: true once the user has explicitly denied POST_NOTIFICATIONS. Sticky — never re-prompt. */
    val NOTIFICATION_PERMISSION_DENIED = booleanPreferencesKey("notificationPermissionDenied")
}
