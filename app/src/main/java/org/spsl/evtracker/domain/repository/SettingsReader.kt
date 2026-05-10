// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    /**
     * Active car id, or `-1L` when none. Storage backing is `intPreferencesKey`
     * for backward-compat with v1.0.x DataStore values; the repository widens
     * to `Long` at the boundary so callers see the same type as the entity PK.
     * One user with thousands of cars over decades won't exceed `Int.MAX_VALUE`.
     */
    val activeCarId: Flow<Long>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
    val driveEnabled: Flow<Boolean>

    /** Null when no successful backup has been recorded yet. */
    val lastBackupAt: Flow<Long?>

    /** Theme is "system" | "light" | "dark". */
    val theme: Flow<String>

    /**
     * Durable flag set true at start of `ResetAllDataUseCase`, cleared at the end.
     * `MainActivity` reads this at startup; if true, the use case re-runs to completion
     * before any UI mounts so the user never reaches an inconsistent state.
     */
    val resetInProgress: Flow<Boolean>

    /**
     * Wizard gate. False until the wizard's finish step writes primaryMetric,
     * distanceUnit, currency together with this flag. Settings → Reset preferences
     * sets it back to false.
     */
    val setupComplete: Flow<Boolean>

    /**
     * Streak counter incremented on each Drive backup failure
     * (Auth-required or generic) and reset to 0 on success. `MainActivity`
     * reads this to decide when to prompt for `POST_NOTIFICATIONS`.
     */
    val consecutiveBackupFailures: Flow<Int>

    /**
     * Sticky once true. Set by `MainActivity` when the user denies
     * `POST_NOTIFICATIONS`. Once true, the rationale dialog never re-fires.
     */
    val notificationPermissionDenied: Flow<Boolean>

    /**
     * ISO-8601 `exported_at` string of the Drive snapshot most recently
     * offered to (and Skipped or Restored by) the user. Empty string = none.
     * Compared verbatim against the `exported_at` field of incoming remote
     * backups in `SettingsViewModel.onDriveAuthGranted` to suppress the
     * destructive restore-prompt loop. Reset on `WipeRemoteBackupUseCase` success.
     */
    val lastSeenRemoteBackupExportedAt: Flow<String>

    /**
     * Persisted IETF BCP-47 language tag. Empty string = "follow
     * system" (the default; `LocaleApplier.apply("")` passes an empty
     * `LocaleListCompat` to AppCompat and the device locale wins). Other
     * values: `"en"` / `"el"` / `"tr"` / `"ru"`.
     */
    val languageTag: Flow<String>

    /**
     * ICE petrol baseline in L/100km (default 7.0). Used as the
     * counterfactual for CO₂ savings in [CO2Calculator].
     */
    val iceBaselineLPer100km: Flow<Double>

    /**
     * Grid carbon intensity in gCO₂/kWh (default 577 — Cyprus
     * 2025 grid average per cyprusgrid.com). Used by [CO2Calculator] to
     * compute EV-side emissions per charge event.
     */
    val gridIntensityGCo2PerKwh: Flow<Double>

    /**
     * Opt-in master flag for CO₂ tracking. False by default on fresh
     * installs: the Dashboard CO₂ card and the Charts CO₂ tab gate
     * themselves on this value, so a user who never visits the new
     * Settings section sees no CO₂ surfaces at all.
     */
    val co2Enabled: Flow<Boolean>

    /**
     * Electricity Maps API key. Empty string = unset; when blank with
     * [co2Enabled] true, [SaveChargeEventUseCase] falls back to the static
     * [gridIntensityGCo2PerKwh] preference.
     */
    val electricityMapsApiKey: Flow<String>

    /**
     * Electricity Maps grid-zone code (uppercase IETF/ISO 3166 region
     * code, e.g. `"CY"`). Default `"CY"` matches the Cyprus-focused
     * static-intensity default. Free-tier authorisation is scoped to a
     * single zone per API key.
     */
    val electricityMapsZone: Flow<String>
}
