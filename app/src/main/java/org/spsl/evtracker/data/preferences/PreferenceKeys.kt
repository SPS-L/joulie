package org.spsl.evtracker.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT  = stringPreferencesKey("distanceUnit")
    val CURRENCY       = stringPreferencesKey("currency")
    val ACTIVE_CAR_ID  = intPreferencesKey("activeCarId")     // consumed by Sub-project B
    val DRIVE_ENABLED  = booleanPreferencesKey("driveEnabled") // consumed by Sub-project E
    val THEME          = stringPreferencesKey("theme")
}
