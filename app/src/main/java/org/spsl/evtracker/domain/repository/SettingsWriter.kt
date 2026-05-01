package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Long)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)

    // F1:
    suspend fun setTheme(value: String)
    suspend fun setPrimaryMetric(metric: String)
    suspend fun setDistanceUnit(unit: String)
    suspend fun setCurrency(code: String)
    suspend fun setSetupComplete(value: Boolean)
    suspend fun setResetInProgress(value: Boolean)

    /** Writes both keys in a single dataStore.edit { ... } block. */
    suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String)

    /**
     * Wizard finish: writes primaryMetric, distanceUnit, currency, and
     * setupComplete=true together inside a single dataStore.edit { ... } block.
     * Atomicity is required by the wizard gate invariant — a partially-applied
     * finish must never leave setupComplete=true while the other three keys are unset.
     */
    suspend fun completeSetup(metric: String, unit: String, currency: String)

    /**
     * Atomic Step 1 of ResetAllDataUseCase: writes setupComplete=false, activeCarId=-1,
     * AND resetInProgress=true inside a single dataStore.edit { ... } block.
     */
    suspend fun markGlobalResetInProgress()
}
