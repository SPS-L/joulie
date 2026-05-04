// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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

    /**
     * TASK-54: ISO-8601 `exported_at` of the remote Drive backup most recently
     * offered to the user (Skip) or restored. Empty string ("") = never seen.
     * Used by `SettingsViewModel.onDriveAuthGranted` to silently enable Drive
     * when the remote snapshot identity matches the marker, so the
     * destructive-action restore prompt is not shown twice for the same backup.
     * Cleared on `WipeRemoteBackupUseCase` success.
     */
    val LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT =
        stringPreferencesKey("lastSeenRemoteBackupExportedAt")

    /**
     * TASK-55: persisted IETF BCP-47 language tag for the in-app locale
     * picker. Empty string ("") means "follow system" — the
     * `AppCompatDelegate.setApplicationLocales` call resolves to an empty
     * `LocaleListCompat` and Android falls back to the device locale.
     * Otherwise: `"en"` / `"el"` / `"tr"` / `"ru"` (the locales shipped in
     * TASK-15). Read once on app start in `EVTrackerApp.onCreate` and
     * applied via `LocaleApplier`.
     */
    val LANGUAGE_TAG = stringPreferencesKey("languageTag")

    /**
     * TASK-20: ICE petrol baseline in litres per 100 km, used as the
     * counterfactual for CO₂ savings. Default 7.0 represents the EU
     * real-world fleet average. User-editable in Settings → CO₂.
     */
    val ICE_BASELINE_L_PER_100KM = doublePreferencesKey("iceBaselineLPer100km")

    /**
     * TASK-20: grid carbon intensity in gCO₂/kWh used to compute the
     * EV side of the CO₂ tracker. Default 577 = Cyprus 2025 grid
     * average per cyprusgrid.com. Stays static for this PR; TASK-49
     * will layer per-event live values on top once a free real-time
     * data source is wired up.
     */
    val GRID_INTENSITY_G_CO2_PER_KWH = doublePreferencesKey("gridIntensityGCo2PerKwh")
}
