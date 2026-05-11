// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow domain interface over the Electricity Maps live carbon-intensity
 * feed. Implementations MUST enforce a hard 1-hour throttle: the API is
 * called at most once per zone per hour, and the throttle survives
 * process restart (the production impl persists `(zone, intensity,
 * fetchedAtMs)` in DataStore — see `ElectricityMapsRepository`).
 *
 * Failures fan out into the [FetchOutcome] subtypes so the UI can render
 * a specific reason ("API key rejected", "No internet", "Rate limit
 * reached") instead of a generic "Tap to retry". There is no manual
 * fallback by design — when the live feed is unavailable the dashboard
 * / charts CO₂ surfaces stay hidden and the user sees the reason.
 */
interface CarbonIntensitySource {
    /**
     * Returns the fetch outcome for [zone] (e.g. `"CY"`, `"DE"`, `"FR"`).
     * Pass a non-blank [apiKey] to authenticate the call. A cache hit
     * within the 1-hour TTL returns [FetchOutcome.Success] without
     * making an HTTP call.
     *
     * Side effects: on [FetchOutcome.Success] the result is persisted
     * to the cache. On any other outcome the cache is left untouched
     * AND [lastError] is updated to the corresponding reason so a
     * subsequent UI redraw can surface it.
     */
    suspend fun fetchCarbonIntensity(zone: String, apiKey: String): FetchOutcome

    /**
     * Fire-and-forget probe for the Settings → API key dialog. Same
     * HTTP call as [fetchCarbonIntensity] but **does not** write to the
     * cache and does not touch [lastError]. Lets the user validate a
     * candidate key (or a candidate key + zone pair) before saving.
     */
    suspend fun probeApiKey(zone: String, apiKey: String): FetchOutcome

    /**
     * Drop all cached intensity values (in-memory + persistent). Called
     * by `ResetAllDataUseCase` so a post-reset save re-fetches against
     * the live API instead of resurrecting a stale value from before
     * the reset.
     */
    suspend fun clearCache()

    /**
     * Latest non-success outcome from [fetchCarbonIntensity]. `null`
     * means either no fetch has been attempted, or the most recent
     * attempt succeeded. The dashboard pill observes this so the Error
     * state can render a specific reason string.
     */
    val lastError: StateFlow<FetchOutcome?>
}

/**
 * Outcome of a single fetch attempt. Distinguishing the failure modes
 * lets the dashboard pill show "API key rejected. Zone may not match
 * your key." vs "No internet connection." instead of one generic error.
 */
sealed class FetchOutcome {
    data class Success(val intensityGCo2PerKwh: Double) : FetchOutcome()

    /** HTTP 401 or 403. Most often the API key is wrong, or the key was
     *  issued for a different zone than the one the app is requesting
     *  (free-tier keys are bound to one zone at signup). */
    data object AuthError : FetchOutcome()

    /** HTTP 429. The 1-hour throttle in the repo prevents this for
     *  Joulie's own calls, but the free tier shares the 50 req/h budget
     *  across all Electricity Maps clients using this key. */
    data object RateLimited : FetchOutcome()

    /** HTTP 5xx — Electricity Maps is unavailable. Retry later. */
    data object ServerError : FetchOutcome()

    /** Transport failure: no network, DNS lookup failed, TLS handshake
     *  error, socket timeout. */
    data object NetworkError : FetchOutcome()

    /** CO₂ tracking is off, or API key / zone is blank — no fetch was
     *  attempted. Pill stays Hidden in this case; not surfaced as Error. */
    data object Disabled : FetchOutcome()
}

/** Convenience: the intensity value when this is a Success, else null. */
val FetchOutcome.intensityOrNull: Double?
    get() = (this as? FetchOutcome.Success)?.intensityGCo2PerKwh
