package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsReader, SettingsWriter {
    val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }

    override val primaryMetric: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "km_per_kwh" }

    override val distanceUnit: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.DISTANCE_UNIT] ?: "km" }

    override val currency: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.CURRENCY] ?: "EUR" }

    val theme: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    override val activeCarId: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT]  = unit
            prefs[PreferenceKeys.CURRENCY]       = currency
            prefs[PreferenceKeys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[PreferenceKeys.THEME] = theme }
    }

    override suspend fun setActiveCarId(id: Int) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id }
    }

    override suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DRIVE_ENABLED] = enabled }
    }

    /** Used by the future Settings → Reset preferences action (Sub-project F). */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = false }
    }
}
