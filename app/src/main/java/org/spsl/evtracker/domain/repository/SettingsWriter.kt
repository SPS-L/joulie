// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Long)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)

    // Settings rows backing the Reset / Preferences flows.
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

    /** Set to 0 on backup success, or `previous + 1` after a backup failure. */
    suspend fun setConsecutiveBackupFailures(value: Int)

    /** Set to true once when the user denies POST_NOTIFICATIONS — never reverted. */
    suspend fun setNotificationPermissionDenied(value: Boolean)

    /**
     * Persist the ISO-8601 `exported_at` of the most recently
     * Skipped or Restored remote snapshot. Pass empty string to clear
     * (used by `WipeRemoteBackupUseCase` on successful delete).
     */
    suspend fun setLastSeenRemoteBackupExportedAt(value: String)

    /**
     * Persist the IETF BCP-47 language tag chosen by the user.
     * Pass empty string for "follow system". Caller is responsible for
     * also invoking `LocaleApplier.apply(tag)` to surface the change to
     * the running process.
     */
    suspend fun setLanguageTag(value: String)

    /** Persist the user-edited ICE petrol baseline in L/100km. */
    suspend fun setIceBaselineLPer100km(value: Double)

    /** Toggle the opt-in CO₂ master switch. */
    suspend fun setCo2Enabled(enabled: Boolean)

    /** Persist the Electricity Maps API key (empty string = unset). */
    suspend fun setElectricityMapsApiKey(value: String)

    /** Persist the Electricity Maps grid-zone code (uppercase, e.g. `"CY"`). */
    suspend fun setElectricityMapsZone(value: String)

    /**
     * Atomic 3-key write of the persistent 1-hour throttle. Caller
     * passes the wall-clock epoch-ms of the successful fetch. The
     * repository reads these together — partial state would let the
     * throttle silently degrade.
     */
    suspend fun setElectricityMapsCache(zone: String, intensityGCo2PerKwh: Double, fetchedAtMs: Long)

    /** Wipe the persistent throttle cache. Called on reset-all-data. */
    suspend fun clearElectricityMapsCache()
}
