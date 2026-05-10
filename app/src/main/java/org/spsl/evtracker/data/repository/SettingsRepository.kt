// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsReader, SettingsWriter {
    override val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }

    override val primaryMetric: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "kwh_per_100km" }

    override val distanceUnit: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.DISTANCE_UNIT] ?: "km" }

    override val currency: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.CURRENCY] ?: "EUR" }

    override val driveEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.DRIVE_ENABLED] ?: false }

    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[PreferenceKeys.LAST_BACKUP_AT] }

    override val theme: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    override val resetInProgress: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.RESET_IN_PROGRESS] ?: false }

    override val consecutiveBackupFailures: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.CONSECUTIVE_BACKUP_FAILURES] ?: 0 }

    override val notificationPermissionDenied: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.NOTIFICATION_PERMISSION_DENIED] ?: false }

    override val lastSeenRemoteBackupExportedAt: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT] ?: "" }

    override val languageTag: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.LANGUAGE_TAG] ?: "" }

    override val iceBaselineLPer100km: Flow<Double> =
        dataStore.data.map { it[PreferenceKeys.ICE_BASELINE_L_PER_100KM] ?: 7.0 }

    override val gridIntensityGCo2PerKwh: Flow<Double> =
        dataStore.data.map { it[PreferenceKeys.GRID_INTENSITY_G_CO2_PER_KWH] ?: 577.0 }

    override val co2Enabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.CO2_ENABLED] ?: false }

    override val electricityMapsApiKey: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.ELECTRICITY_MAPS_API_KEY] ?: "" }

    override val electricityMapsZone: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.ELECTRICITY_MAPS_ZONE] ?: "CY" }

    // ACTIVE_CAR_ID stays an `intPreferencesKey` (didn't touch DataStore
    // backing types — switching `intPreferencesKey` to `longPreferencesKey` with
    // the same key name would silently lose the existing Int value). We widen
    // to Long at the boundary so callers see the same type as the entity PK.
    override val activeCarId: Flow<Long> =
        dataStore.data.map { (it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1).toLong() }

    override suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT] = unit
            prefs[PreferenceKeys.CURRENCY] = currency
            prefs[PreferenceKeys.SETUP_COMPLETE] = true
        }
    }

    override suspend fun setTheme(value: String) {
        dataStore.edit { it[PreferenceKeys.THEME] = value }
    }

    override suspend fun setPrimaryMetric(metric: String) {
        dataStore.edit { it[PreferenceKeys.PRIMARY_METRIC] = metric }
    }

    override suspend fun setDistanceUnit(unit: String) {
        dataStore.edit { it[PreferenceKeys.DISTANCE_UNIT] = unit }
    }

    override suspend fun setCurrency(code: String) {
        dataStore.edit { it[PreferenceKeys.CURRENCY] = code }
    }

    override suspend fun setSetupComplete(value: Boolean) {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = value }
    }

    override suspend fun setResetInProgress(value: Boolean) {
        dataStore.edit { it[PreferenceKeys.RESET_IN_PROGRESS] = value }
    }

    override suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT] = unit
        }
    }

    override suspend fun markGlobalResetInProgress() {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SETUP_COMPLETE] = false
            prefs[PreferenceKeys.ACTIVE_CAR_ID] = -1
            prefs[PreferenceKeys.RESET_IN_PROGRESS] = true
        }
    }

    override suspend fun setActiveCarId(id: Long) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id.toInt() }
    }

    override suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DRIVE_ENABLED] = enabled }
    }

    override suspend fun setLastBackupAt(epochMs: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_BACKUP_AT] = epochMs }
    }

    override suspend fun setConsecutiveBackupFailures(value: Int) {
        dataStore.edit { it[PreferenceKeys.CONSECUTIVE_BACKUP_FAILURES] = value }
    }

    override suspend fun setNotificationPermissionDenied(value: Boolean) {
        dataStore.edit { it[PreferenceKeys.NOTIFICATION_PERMISSION_DENIED] = value }
    }

    override suspend fun setLastSeenRemoteBackupExportedAt(value: String) {
        dataStore.edit { it[PreferenceKeys.LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT] = value }
    }

    override suspend fun setLanguageTag(value: String) {
        dataStore.edit { it[PreferenceKeys.LANGUAGE_TAG] = value }
    }

    override suspend fun setIceBaselineLPer100km(value: Double) {
        dataStore.edit { it[PreferenceKeys.ICE_BASELINE_L_PER_100KM] = value }
    }

    override suspend fun setGridIntensityGCo2PerKwh(value: Double) {
        dataStore.edit { it[PreferenceKeys.GRID_INTENSITY_G_CO2_PER_KWH] = value }
    }

    override suspend fun setCo2Enabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.CO2_ENABLED] = enabled }
    }

    override suspend fun setElectricityMapsApiKey(value: String) {
        dataStore.edit { it[PreferenceKeys.ELECTRICITY_MAPS_API_KEY] = value }
    }

    override suspend fun setElectricityMapsZone(value: String) {
        dataStore.edit { it[PreferenceKeys.ELECTRICITY_MAPS_ZONE] = value }
    }

    /** Flips `setupComplete` back to `false` so the wizard re-fires on next launch. */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = false }
    }
}
