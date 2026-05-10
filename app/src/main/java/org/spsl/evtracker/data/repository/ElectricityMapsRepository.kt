// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches real-time carbon intensity (gCO₂/kWh) from the Electricity Maps
 * v3 API. Cached for [CACHE_TTL_MS] per zone so a second charge logged within
 * the same hour does not consume a second free-tier request.
 *
 * No new transitive deps: `HttpURLConnection` + `org.json.JSONObject` are
 * already on the Android classpath. Returns `null` on every failure path so
 * callers can fall back to the static `gridIntensityGCo2PerKwh` preference.
 *
 * `@Singleton` keeps the cache process-scoped — without it, Hilt would
 * construct a fresh repository per `SaveChargeEventUseCase` invocation and
 * each save would hit the network.
 */
@Singleton
class ElectricityMapsRepository @Inject constructor() : CarbonIntensitySource {

    private data class CacheEntry(val intensityGCo2PerKwh: Double, val fetchedAtMs: Long)

    private val cache = mutableMapOf<String, CacheEntry>()

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

    override suspend fun fetchCarbonIntensity(zone: String, apiKey: String): Double? =
        withContext(Dispatchers.IO) {
            val now = clock()
            cache[zone]?.let { entry ->
                if (now - entry.fetchedAtMs < CACHE_TTL_MS) {
                    return@withContext entry.intensityGCo2PerKwh
                }
            }
            if (apiKey.isBlank()) return@withContext null
            if (zone.isBlank()) return@withContext null
            val body = httpFetcher(zone, apiKey) ?: return@withContext null
            // Single-field extraction. We deliberately do NOT use
            // `org.json.JSONObject` here because that class is a stub on the
            // JVM unit-test classpath — every getter returns 0/null, which
            // would silently dilute production behaviour to a no-op under
            // test. A 30-char regex is enough for one field; if Electricity
            // Maps ever returns multiple useful fields we can revisit.
            val match = CARBON_INTENSITY_REGEX.find(body) ?: return@withContext null
            val intensity = match.groupValues[1].toDoubleOrNull() ?: return@withContext null
            cache[zone] = CacheEntry(intensity, now)
            intensity
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

    override fun clearCache() {
        cache.clear()
    }

    companion object {
        const val CACHE_TTL_MS: Long = 60L * 60L * 1_000L
        private const val HTTP_TIMEOUT_MS: Int = 8_000
        private const val HTTP_OK: Int = 200
        private val CARBON_INTENSITY_REGEX =
            Regex("\"carbonIntensity\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
    }
}
