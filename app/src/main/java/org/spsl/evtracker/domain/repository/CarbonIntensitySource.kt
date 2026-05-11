// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

/**
 * Narrow domain interface over the Electricity Maps live carbon-intensity
 * feed. Implementations MUST enforce a hard 1-hour throttle: the API is
 * called at most once per zone per hour, and the throttle survives
 * process restart (the production impl persists `(zone, intensity,
 * fetchedAtMs)` in DataStore — see `ElectricityMapsRepository`).
 *
 * Returns `null` on any failure path — blank API key, network error,
 * non-200 response, malformed JSON. There is no manual fallback by
 * design (issue #1 follow-up): when the live feed is unavailable, the
 * dashboard / charts CO₂ surfaces stay hidden.
 */
interface CarbonIntensitySource {
    /**
     * Returns gCO₂/kWh for [zone] (e.g. `"CY"`, `"DE"`, `"FR"`). Pass a
     * non-blank [apiKey] to authenticate the call. Returns `null` when
     * the lookup fails for any reason — callers must NOT supply a
     * fallback value, just leave the per-event column null.
     */
    suspend fun fetchCarbonIntensity(zone: String, apiKey: String): Double?

    /**
     * Drop all cached intensity values (in-memory + persistent). Called
     * by `ResetAllDataUseCase` so a post-reset save re-fetches against
     * the live API instead of resurrecting a stale value from before
     * the reset. Suspending because the persistent layer is DataStore.
     */
    suspend fun clearCache()
}
