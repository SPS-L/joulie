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
    val ACTIVE_CAR_ID = intPreferencesKey("activeCarId")
    val DRIVE_ENABLED = booleanPreferencesKey("driveEnabled")
    val THEME = stringPreferencesKey("theme")
    val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt")

    /** Durable interrupted-reset flag. See `ResetAllDataUseCase` + `MainActivity`. */
    val RESET_IN_PROGRESS = booleanPreferencesKey("resetInProgress")

    /** Streak counter incremented on each Drive backup failure; reset to 0 on success. */
    val CONSECUTIVE_BACKUP_FAILURES = intPreferencesKey("consecutiveBackupFailures")

    /** Sticky once true: the user has explicitly denied `POST_NOTIFICATIONS`; never re-prompt. */
    val NOTIFICATION_PERMISSION_DENIED = booleanPreferencesKey("notificationPermissionDenied")

    /**
     * ISO-8601 `exported_at` of the remote Drive backup most recently offered
     * to the user (Skip) or restored. Empty string (`""`) = never seen.
     *
     * Used by `SettingsViewModel.onDriveAuthGranted` to silently enable Drive
     * when the remote snapshot identity matches the marker, so the destructive
     * restore prompt is not shown twice for the same backup. Cleared on
     * `WipeRemoteBackupUseCase` success.
     */
    val LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT =
        stringPreferencesKey("lastSeenRemoteBackupExportedAt")

    /**
     * Persisted IETF BCP-47 language tag for the in-app locale picker. Empty
     * string (`""`) means "follow system" — `AppCompatDelegate.setApplicationLocales`
     * resolves to an empty `LocaleListCompat` and Android falls back to the
     * device locale. Otherwise: `"en"` / `"el"` / `"tr"` / `"ru"`. Read once
     * on app start in `EVTrackerApp.onCreate` and applied via `LocaleApplier`.
     */
    val LANGUAGE_TAG = stringPreferencesKey("languageTag")

    /**
     * ICE petrol baseline in litres per 100 km, used as the counterfactual
     * for CO₂ savings. Default 7.0 represents the EU real-world fleet
     * average. User-editable in Settings → CO₂.
     */
    val ICE_BASELINE_L_PER_100KM = doublePreferencesKey("iceBaselineLPer100km")

    /**
     * Opt-in master switch for CO₂ tracking. Default `false` on fresh
     * installs so the dashboard CO₂ card + chart CO₂ series are hidden
     * until the user explicitly enables them. When false the Electricity
     * Maps fetch is short-circuited and `SaveChargeEventUseCase` stores
     * `null` on the entity's per-event intensity column.
     *
     * **No manual fallback.** When the toggle is on but the API key is
     * blank or the live fetch returns null, the entity column stays null
     * and the dashboard / charts CO₂ surfaces stay hidden. There is no
     * static grid-intensity preference — guessing CO₂ from a typed number
     * was misleading and is gone (issue #1 follow-up).
     */
    val CO2_ENABLED = booleanPreferencesKey("co2Enabled")

    /**
     * Electricity Maps API key. Empty string = unset; when blank with
     * [CO2_ENABLED] true, no CO₂ is computed (no manual fallback). Stored
     * verbatim; no salting because the value is a per-account read token,
     * not a credential.
     */
    val ELECTRICITY_MAPS_API_KEY = stringPreferencesKey("electricityMapsApiKey")

    /**
     * Electricity Maps grid-zone code (uppercase IETF/ISO 3166 region
     * code, e.g. `CY`, `DE`, `FR`). Default `CY`. Bound by the live-data
     * subscription — the free tier scopes calls to one zone per request.
     */
    val ELECTRICITY_MAPS_ZONE = stringPreferencesKey("electricityMapsZone")

    /**
     * Persistent 1-hour throttle for the Electricity Maps API call. Three
     * keys written atomically by `SettingsWriter.setElectricityMapsCache`:
     *   - [ELECTRICITY_MAPS_CACHE_ZONE]: the zone string this cache entry
     *     was fetched for. A zone change makes the cache irrelevant.
     *   - [ELECTRICITY_MAPS_CACHE_INTENSITY]: the cached value in gCO₂/kWh.
     *   - [ELECTRICITY_MAPS_CACHE_FETCHED_AT_MS]: wall-clock epoch-ms of
     *     the successful fetch.
     *
     * The repository consults this BEFORE making any HTTP call. If the
     * cached `(zone, fetchedAt)` is still within the 1-hour TTL, the
     * cached value is returned and no network call is made — the
     * guarantee survives process restarts and DataStore-cleared in-memory
     * caches. Cleared on `ResetAllDataUseCase` and on `clearCache`.
     */
    val ELECTRICITY_MAPS_CACHE_ZONE = stringPreferencesKey("electricityMapsCacheZone")
    val ELECTRICITY_MAPS_CACHE_INTENSITY = doublePreferencesKey("electricityMapsCacheIntensity")
    val ELECTRICITY_MAPS_CACHE_FETCHED_AT_MS = longPreferencesKey("electricityMapsCacheFetchedAtMs")
}
