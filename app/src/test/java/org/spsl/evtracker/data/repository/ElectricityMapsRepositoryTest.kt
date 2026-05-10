// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM-side cache + fallback behaviour for [ElectricityMapsRepository] (TASK-80).
 *
 * The repo exposes two `internal` seams for tests:
 *  - `clock`: replaces `System.currentTimeMillis()` so the 1-hour TTL is
 *    deterministic without sleeping.
 *  - `httpFetcher`: replaces the real `HttpURLConnection` call so the test
 *    runs without a network.
 *
 * Coverage:
 *  - second call within TTL hits cache (no second fetch).
 *  - second call past TTL re-fetches.
 *  - blank API key returns null without fetching.
 *  - non-200 / IO error returns null and does not poison the cache.
 *  - `clearCache` forces the next call to re-fetch.
 *  - independent zones cache independently.
 */
class ElectricityMapsRepositoryTest {

    private fun mkRepo(initialNow: Long = 1_000_000L): Pair<ElectricityMapsRepository, MutableMap<String, String>> {
        val repo = ElectricityMapsRepository()
        var nowMs = initialNow
        repo.clock = { nowMs }
        // Each test mutates `now` by reassigning a fresh lambda; the inner Map
        // tracks how many times each (zone,key) was queried.
        val callLog = mutableMapOf<String, String>()
        repo.httpFetcher = { zone, key ->
            val n = (callLog[zone]?.toIntOrNull() ?: 0) + 1
            callLog[zone] = n.toString()
            // Force a tiny gCO₂/kWh value uniquely per call so the test can
            // tell "served from cache" (same value as the first hit) apart from
            // "served from network" (a new value).
            "{\"carbonIntensity\": ${100 + n}, \"zone\": \"$zone\", \"key\": \"$key\"}"
        }
        return repo to callLog
    }

    @Test
    fun secondFetchWithinHour_servesFromCache_noSecondNetworkCall() = runTest {
        val (repo, callLog) = mkRepo()
        val first = repo.fetchCarbonIntensity("CY", "k")
        val second = repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first!!, 0.0)
        assertEquals("cache must serve same value", 101.0, second!!, 0.0)
        assertEquals("network must be called exactly once", "1", callLog["CY"])
    }

    @Test
    fun fetchPastOneHourTtl_reFetchesFromNetwork() = runTest {
        val repo = ElectricityMapsRepository()
        var nowMs = 0L
        repo.clock = { nowMs }
        var calls = 0
        repo.httpFetcher = { _, _ ->
            calls++
            "{\"carbonIntensity\": ${100 + calls}}"
        }
        val first = repo.fetchCarbonIntensity("CY", "k")
        nowMs = ElectricityMapsRepository.CACHE_TTL_MS // exactly at TTL boundary → cache expired (strict `<`).
        val second = repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first!!, 0.0)
        assertEquals(102.0, second!!, 0.0)
        assertEquals(2, calls)
    }

    @Test
    fun blankApiKey_returnsNull_andDoesNotCallNetwork() = runTest {
        val repo = ElectricityMapsRepository()
        var calls = 0
        repo.httpFetcher = { _, _ ->
            calls++
            "{\"carbonIntensity\": 412}"
        }
        assertNull(repo.fetchCarbonIntensity("CY", ""))
        assertEquals(0, calls)
    }

    @Test
    fun networkFailure_returnsNull_andDoesNotCacheFailure() = runTest {
        val repo = ElectricityMapsRepository()
        var nowMs = 0L
        repo.clock = { nowMs }
        var calls = 0
        var failNext = true
        repo.httpFetcher = { _, _ ->
            calls++
            if (failNext) null else "{\"carbonIntensity\": 412}"
        }
        val first = repo.fetchCarbonIntensity("CY", "k")
        assertNull(first)
        // Next call within the would-be-TTL window still attempts the network
        // because failures must not be cached.
        failNext = false
        val second = repo.fetchCarbonIntensity("CY", "k")
        assertEquals(412.0, second!!, 0.0)
        assertEquals(2, calls)
    }

    @Test
    fun clearCache_forcesReFetch_onNextCall() = runTest {
        val repo = ElectricityMapsRepository()
        repo.clock = { 0L }
        var calls = 0
        repo.httpFetcher = { _, _ ->
            calls++
            "{\"carbonIntensity\": ${100 + calls}}"
        }

        repo.fetchCarbonIntensity("CY", "k")
        repo.fetchCarbonIntensity("CY", "k") // cached
        assertEquals(1, calls)

        repo.clearCache()
        repo.fetchCarbonIntensity("CY", "k")
        assertEquals("clearCache must invalidate", 2, calls)
    }

    @Test
    fun independentZones_cacheIndependently() = runTest {
        val (repo, callLog) = mkRepo()
        val cy = repo.fetchCarbonIntensity("CY", "k")
        val de = repo.fetchCarbonIntensity("DE", "k")
        assertEquals(101.0, cy!!, 0.0)
        assertEquals(101.0, de!!, 0.0) // 101 because first call to DE
        assertEquals("1", callLog["CY"])
        assertEquals("1", callLog["DE"])
    }

    @Test
    fun unparseableJson_returnsNull() = runTest {
        val repo = ElectricityMapsRepository()
        repo.clock = { 0L }
        repo.httpFetcher = { _, _ -> "not-json" }
        assertNull(repo.fetchCarbonIntensity("CY", "k"))
    }

    @Test
    fun missingCarbonIntensityField_returnsNull() = runTest {
        val repo = ElectricityMapsRepository()
        repo.clock = { 0L }
        repo.httpFetcher = { _, _ -> "{\"zone\": \"CY\"}" }
        assertNull(repo.fetchCarbonIntensity("CY", "k"))
    }
}
