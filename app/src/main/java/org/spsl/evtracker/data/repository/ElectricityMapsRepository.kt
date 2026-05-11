// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.FetchOutcome
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches real-time carbon intensity (gCO₂/kWh) from the Electricity Maps
 * v3 API.
 *
 * **Hard 1-hour rate limit.** The repository GUARANTEES the API is called
 * at most once per zone per hour. Two layers enforce it:
 *
 * 1. **In-memory cache** — fast path, scoped to a single process run.
 * 2. **Persistent cache** in DataStore (`SettingsReader` /
 *    `SettingsWriter.setElectricityMapsCache`) — survives process restart.
 *    On every call, the repo reads `(cacheZone, cacheIntensity,
 *    cacheFetchedAtMs)` from DataStore BEFORE deciding to fetch. If the
 *    cached entry matches the requested zone and is younger than
 *    [CACHE_TTL_MS] (1 h), the cached value is returned — no HTTP call.
 *
 * A `Mutex` serialises concurrent callers within a single process so
 * two saves logged within the same millisecond cannot race past the
 * cache check together.
 *
 * **Threading.** The HTTP call runs on [Dispatchers.IO] regardless of
 * the calling dispatcher. Earlier the call ran on whatever dispatcher
 * `viewModelScope.launch` lands on (Main), which threw
 * NetworkOnMainThreadException, got swallowed by the bare catch, and
 * surfaced as a permanently-stuck "Tap to retry" pill on the dashboard.
 *
 * **Failure modes are typed.** Every fetch returns a [FetchOutcome] so
 * the UI can render a specific reason. [lastError] tracks the most
 * recent non-success outcome for observers (dashboard pill) that need
 * to surface it.
 *
 * No new transitive deps: `HttpURLConnection` + a tiny regex JSON parse
 * (the `org.json.JSONObject` stub on the JVM test classpath returns
 * 0/null for every getter, so this code path stays off it).
 */
@Singleton
class ElectricityMapsRepository @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
) : CarbonIntensitySource {

    private data class CacheEntry(val intensityGCo2PerKwh: Double, val fetchedAtMs: Long)

    private val inMemoryCache = mutableMapOf<String, CacheEntry>()
    private val fetchMutex = Mutex()

    private val _lastError = MutableStateFlow<FetchOutcome?>(null)
    override val lastError: StateFlow<FetchOutcome?> = _lastError.asStateFlow()

    /**
     * Clock seam, internal to the package so JVM tests can advance time
     * without booting Android. Defaults to wall-clock. Production code
     * should never set this.
     */
    @JvmField
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Network seam, internal to the package. Returns the raw HTTP outcome
     * for a `(zone, apiKey)` pair. Defaults to a real HTTPS call to the
     * Electricity Maps v3 endpoint. Tests override this to assert the
     * cache-hit / fallback paths without making a network call.
     */
    @JvmField
    internal var httpFetcher: suspend (String, String) -> HttpResult = { z, k ->
        defaultHttpFetch(z, k)
    }

    /** Discriminated http outcome, used internally + by the test seam. */
    internal sealed class HttpResult {
        data class Body(val text: String) : HttpResult()
        data object Auth : HttpResult()
        data object RateLimited : HttpResult()
        data object Server : HttpResult()
        data object Network : HttpResult()
    }

    override suspend fun fetchCarbonIntensity(zone: String, apiKey: String): FetchOutcome {
        if (zone.isBlank()) return FetchOutcome.Disabled
        // Serialise so two concurrent saves can't both miss the cache.
        return fetchMutex.withLock {
            val now = clock()

            // Fast path: in-memory cache.
            inMemoryCache[zone]?.let { entry ->
                if (now - entry.fetchedAtMs < CACHE_TTL_MS) {
                    return@withLock FetchOutcome.Success(entry.intensityGCo2PerKwh)
                }
            }

            // Durable path: persistent cache survives process restart.
            val persistedZone = settingsReader.electricityMapsCacheZone.first()
            val persistedIntensity = settingsReader.electricityMapsCacheIntensity.first()
            val persistedFetchedAt = settingsReader.electricityMapsCacheFetchedAtMs.first()
            if (persistedZone == zone &&
                persistedFetchedAt > 0L &&
                now - persistedFetchedAt < CACHE_TTL_MS &&
                persistedIntensity > 0.0
            ) {
                inMemoryCache[zone] = CacheEntry(persistedIntensity, persistedFetchedAt)
                return@withLock FetchOutcome.Success(persistedIntensity)
            }

            // Cache miss in both layers → fetch.
            if (apiKey.isBlank()) return@withLock FetchOutcome.Disabled

            val outcome = doFetch(zone, apiKey)
            if (outcome is FetchOutcome.Success) {
                _lastError.value = null
                inMemoryCache[zone] = CacheEntry(outcome.intensityGCo2PerKwh, now)
                settingsWriter.setElectricityMapsCache(zone, outcome.intensityGCo2PerKwh, now)
            } else {
                _lastError.value = outcome
            }
            outcome
        }
    }

    override suspend fun probeApiKey(zone: String, apiKey: String): FetchOutcome {
        if (zone.isBlank()) return FetchOutcome.Disabled
        if (apiKey.isBlank()) return FetchOutcome.Disabled
        // Bypass cache + lastError on purpose: this is the Settings dialog's
        // "Test connection" call, so the user wants a fresh probe against
        // the candidate (zone, key) pair without polluting global state.
        return doFetch(zone, apiKey)
    }

    override suspend fun clearCache() {
        fetchMutex.withLock {
            inMemoryCache.clear()
            settingsWriter.clearElectricityMapsCache()
            _lastError.value = null
        }
    }

    private suspend fun doFetch(zone: String, apiKey: String): FetchOutcome {
        return when (val r = httpFetcher(zone, apiKey)) {
            is HttpResult.Body -> {
                val match = CARBON_INTENSITY_REGEX.find(r.text)
                    ?: run {
                        Log.w(TAG, "Electricity Maps response missing carbonIntensity field")
                        return FetchOutcome.ServerError
                    }
                val intensity = match.groupValues[1].toDoubleOrNull()
                    ?: run {
                        Log.w(TAG, "Electricity Maps response had non-numeric carbonIntensity")
                        return FetchOutcome.ServerError
                    }
                FetchOutcome.Success(intensity)
            }
            HttpResult.Auth -> FetchOutcome.AuthError
            HttpResult.RateLimited -> FetchOutcome.RateLimited
            HttpResult.Server -> FetchOutcome.ServerError
            HttpResult.Network -> FetchOutcome.NetworkError
        }
    }

    private suspend fun defaultHttpFetch(zone: String, apiKey: String): HttpResult =
        withContext(Dispatchers.IO) {
            val url = URL("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=$zone")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.setRequestProperty("auth-token", apiKey)
                conn.connectTimeout = HTTP_TIMEOUT_MS
                conn.readTimeout = HTTP_TIMEOUT_MS
                val code = conn.responseCode
                when {
                    code == HTTP_OK -> {
                        HttpResult.Body(conn.inputStream.bufferedReader().readText())
                    }
                    code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> {
                        Log.w(TAG, "Electricity Maps auth failed (HTTP $code) for zone=$zone")
                        HttpResult.Auth
                    }
                    code == HTTP_TOO_MANY_REQUESTS -> {
                        Log.w(TAG, "Electricity Maps rate-limited (HTTP 429) for zone=$zone")
                        HttpResult.RateLimited
                    }
                    code in HTTP_SERVER_ERROR_RANGE -> {
                        Log.w(TAG, "Electricity Maps server error (HTTP $code) for zone=$zone")
                        HttpResult.Server
                    }
                    else -> {
                        Log.w(TAG, "Electricity Maps unexpected status (HTTP $code) for zone=$zone")
                        HttpResult.Server
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "Electricity Maps transport error for zone=$zone: ${e.message}")
                HttpResult.Network
            } catch (e: Exception) {
                Log.w(TAG, "Electricity Maps unexpected exception for zone=$zone: ${e.message}")
                HttpResult.Network
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        const val CACHE_TTL_MS: Long = 60L * 60L * 1_000L
        private const val HTTP_TIMEOUT_MS: Int = 8_000
        private const val HTTP_OK: Int = 200
        private const val HTTP_UNAUTHORIZED: Int = 401
        private const val HTTP_FORBIDDEN: Int = 403
        private const val HTTP_TOO_MANY_REQUESTS: Int = 429
        private val HTTP_SERVER_ERROR_RANGE = 500..599
        private const val TAG = "ElectricityMaps"
        private val CARBON_INTENSITY_REGEX =
            Regex("\"carbonIntensity\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    }
}
