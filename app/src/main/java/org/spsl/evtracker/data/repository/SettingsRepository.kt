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
    val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }

    override val primaryMetric: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "km_per_kwh" }

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

    override val activeCarId: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
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

    override suspend fun setActiveCarId(id: Int) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id }
    }

    override suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DRIVE_ENABLED] = enabled }
    }

    override suspend fun setLastBackupAt(epochMs: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_BACKUP_AT] = epochMs }
    }

    /** Used by the future Settings → Reset preferences action (Sub-project F). */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = false }
    }
}
