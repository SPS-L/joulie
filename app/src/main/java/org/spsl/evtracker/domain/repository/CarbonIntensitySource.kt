// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

/**
 * Narrow domain interface over the Electricity Maps live carbon-intensity
 * feed. Implementations cache aggressively (1 h TTL matches the free tier's
 * per-zone rate limit) so that two charges saved within the same hour share
 * one network call.
 *
 * Returns `null` on any failure path — blank API key, network error, non-200
 * response, malformed JSON. Callers fall back to the static
 * `gridIntensityGCo2PerKwh` preference when this returns `null`.
 */
interface CarbonIntensitySource {
    /**
     * Returns gCO₂/kWh for [zone] (e.g. `"CY"`, `"DE"`, `"FR"`). Pass a
     * non-blank [apiKey] to authenticate the call. Returns `null` when the
     * lookup fails for any reason (silent fallback to manual intensity).
     */
    suspend fun fetchCarbonIntensity(zone: String, apiKey: String): Double?

    /**
     * Drop all cached intensity values. Called by `ResetAllDataUseCase` so a
     * post-reset save event re-fetches against the live API instead of
     * resurrecting a stale value from before the reset.
     */
    fun clearCache()
}
