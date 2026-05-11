// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
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
 * Returns `null` on every failure path — blank API key, network error,
 * non-200 response, malformed JSON. Callers must NOT fall back to any
 * other value; no manual grid-intensity preference exists.
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

    /**
     * Clock seam, internal to the package so JVM tests can advance time
     * without booting Android. Defaults to wall-clock. Production code
     * should never set this.
     */
    @JvmField
    internal var clock: () -> Long = { System.currentTimeMillis() }

    /**
     * Network seam, internal to the package. Returns the raw response body
     * for a `(zone, apiKey)` pair, or `null` for any non-200 / IO failure.
     * Defaults to a real HTTPS call to the Electricity Maps v3 endpoint.
     * Tests override this to assert the cache-hit / fallback paths without
     * making a network call.
     */
    @JvmField
    internal var httpFetcher: suspend (String, String) -> String? = { z, k ->
        defaultHttpFetch(z, k)
    }

    override suspend fun fetchCarbonIntensity(zone: String, apiKey: String): Double? {
        if (zone.isBlank()) return null
        // Serialise so two concurrent saves can't both miss the cache.
        return fetchMutex.withLock {
            val now = clock()

            // Fast path: in-memory cache.
            inMemoryCache[zone]?.let { entry ->
                if (now - entry.fetchedAtMs < CACHE_TTL_MS) {
                    return@withLock entry.intensityGCo2PerKwh
                }
            }

            // Durable path: persistent cache survives process restart.
            // Read the three keys together — partial state would let the
            // throttle degrade silently.
            val persistedZone = settingsReader.electricityMapsCacheZone.first()
            val persistedIntensity = settingsReader.electricityMapsCacheIntensity.first()
            val persistedFetchedAt = settingsReader.electricityMapsCacheFetchedAtMs.first()
            if (persistedZone == zone &&
                persistedFetchedAt > 0L &&
                now - persistedFetchedAt < CACHE_TTL_MS &&
                persistedIntensity > 0.0
            ) {
                // Promote into in-memory cache so subsequent calls in this
                // process skip the DataStore read.
                inMemoryCache[zone] = CacheEntry(persistedIntensity, persistedFetchedAt)
                return@withLock persistedIntensity
            }

            // Cache miss in both layers → fetch.
            if (apiKey.isBlank()) return@withLock null
            val body = httpFetcher(zone, apiKey) ?: return@withLock null
            val match = CARBON_INTENSITY_REGEX.find(body) ?: return@withLock null
            val intensity = match.groupValues[1].toDoubleOrNull() ?: return@withLock null

            // Persist BEFORE returning so a process kill immediately after
            // can't lose the throttle anchor.
            inMemoryCache[zone] = CacheEntry(intensity, now)
            settingsWriter.setElectricityMapsCache(zone, intensity, now)
            intensity
        }
    }

    override suspend fun clearCache() {
        fetchMutex.withLock {
            inMemoryCache.clear()
            settingsWriter.clearElectricityMapsCache()
        }
    }

    private fun defaultHttpFetch(zone: String, apiKey: String): String? {
        val url = URL("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=$zone")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("auth-token", apiKey)
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            if (conn.responseCode != HTTP_OK) return null
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val CACHE_TTL_MS: Long = 60L * 60L * 1_000L
        private const val HTTP_TIMEOUT_MS: Int = 8_000
        private const val HTTP_OK: Int = 200
        private val CARBON_INTENSITY_REGEX =
            Regex("\"carbonIntensity\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    }
}
