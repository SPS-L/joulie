// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

/**
 * JVM-side cache + throttle behaviour for [ElectricityMapsRepository]
 * (TASK-80, TASK-81). The repo guarantees the Electricity Maps API is
 * called at most once per zone per hour. Two layers enforce it:
 *
 *  1. In-memory cache (fast path within a single process).
 *  2. Persistent cache in DataStore (survives process restart).
 *
 * The repo exposes two `internal` seams for tests:
 *  - `clock`: replaces `System.currentTimeMillis()` so the 1-hour TTL is
 *    deterministic without sleeping.
 *  - `httpFetcher`: replaces the real `HttpURLConnection` call so the
 *    test runs without a network.
 *
 * The persistent-cache layer is exercised through a paired
 * `FakeSettingsReader` + `FakeSettingsWriter`: writes record into the
 * writer, and we re-seed the reader from the writer state to simulate
 * a process restart (a fresh repository instance pointing at the same
 * persisted state).
 */
class ElectricityMapsRepositoryTest {

    private class Rig(
        val repo: ElectricityMapsRepository,
        val callLog: MutableMap<String, Int>,
        val nowMs: () -> Long,
        val setNowMs: (Long) -> Unit,
    )

    private fun mkRepo(
        initialNow: Long = 1_000_000L,
        readerInit: FakeSettingsReader = FakeSettingsReader(),
        writer: FakeSettingsWriter = FakeSettingsWriter(),
        responses: MutableMap<String, MutableList<String?>> = mutableMapOf(),
    ): Rig {
        var nowState = initialNow
        val repo = ElectricityMapsRepository(readerInit, writer)
        repo.clock = { nowState }
        val callLog = mutableMapOf<String, Int>()
        repo.httpFetcher = { zone, _ ->
            callLog[zone] = (callLog[zone] ?: 0) + 1
            val q = responses[zone]
            if (q != null && q.isNotEmpty()) {
                // removeAt returns the element (which CAN be null — that's
                // how the test injects a network failure); the size check
                // above disambiguates "empty queue → use default".
                q.removeAt(0)
            } else {
                // Default: unique value per call so cache hits are visible
                // as "same value as the first hit" vs "new value".
                "{\"carbonIntensity\": ${100 + (callLog[zone] ?: 1)}}"
            }
        }
        return Rig(repo, callLog, { nowState }, { nowState = it })
    }

    @Test
    fun secondFetchWithinHour_servesFromMemoryCache_noSecondNetworkCall() = runTest {
        val rig = mkRepo()
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first!!, 0.0)
        assertEquals(101.0, second!!, 0.0)
        assertEquals(1, rig.callLog["CY"])
    }

    @Test
    fun fetchPastOneHourTtl_reFetchesFromNetwork() = runTest {
        val rig = mkRepo(initialNow = 0L)
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        rig.setNowMs(ElectricityMapsRepository.CACHE_TTL_MS) // exact TTL boundary → cache expired (strict `<`).
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first!!, 0.0)
        assertEquals(102.0, second!!, 0.0)
        assertEquals(2, rig.callLog["CY"])
    }

    @Test
    fun blankApiKey_returnsNull_andDoesNotCallNetwork() = runTest {
        val rig = mkRepo()
        assertNull(rig.repo.fetchCarbonIntensity("CY", ""))
        assertEquals(null, rig.callLog["CY"])
    }

    @Test
    fun networkFailure_returnsNull_andDoesNotCacheFailure() = runTest {
        val responses = mutableMapOf("CY" to mutableListOf<String?>(null, "{\"carbonIntensity\": 412}"))
        val rig = mkRepo(initialNow = 0L, responses = responses)
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        assertNull(first)
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(412.0, second!!, 0.0)
        assertEquals(2, rig.callLog["CY"])
    }

    @Test
    fun clearCache_forcesReFetch_andWipesPersistent() = runTest {
        val writer = FakeSettingsWriter()
        val reader = FakeSettingsReader()
        val rig = mkRepo(initialNow = 0L, readerInit = reader, writer = writer)
        rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals("CY", writer.electricityMapsCacheZone)
        assertNotEquals(0.0, writer.electricityMapsCacheIntensity)

        rig.repo.clearCache()
        assertEquals("", writer.electricityMapsCacheZone)
        assertEquals(0.0, writer.electricityMapsCacheIntensity, 0.0)

        rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals("clearCache must force re-fetch", 2, rig.callLog["CY"])
    }

    @Test
    fun unparseableJson_returnsNull() = runTest {
        val rig = mkRepo(responses = mutableMapOf("CY" to mutableListOf<String?>("not-json")))
        assertNull(rig.repo.fetchCarbonIntensity("CY", "k"))
    }

    @Test
    fun missingCarbonIntensityField_returnsNull() = runTest {
        val rig = mkRepo(responses = mutableMapOf("CY" to mutableListOf<String?>("{\"zone\": \"CY\"}")))
        assertNull(rig.repo.fetchCarbonIntensity("CY", "k"))
    }

    /**
     * Persistent cache survives process restart. Simulate: do one fetch
     * with rig A, capture the writer state, build a NEW reader seeded
     * with that state + a NEW repo, advance the clock by less than 1 h,
     * and verify the second fetch hits the persistent cache without
     * calling the network.
     */
    @Test
    fun coldStartWithinOneHourOfPreviousFetch_servesFromPersistent_noNetworkCall() = runTest {
        val writerA = FakeSettingsWriter()
        val readerA = FakeSettingsReader()
        val rigA = mkRepo(initialNow = 1_000_000L, readerInit = readerA, writer = writerA)
        val first = rigA.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first!!, 0.0)
        assertEquals(1, rigA.callLog["CY"])

        // Simulate process restart: new reader pre-loaded with the
        // writer's persisted state; new repo instance.
        val readerB = FakeSettingsReader(
            electricityMapsCacheZoneInit = writerA.electricityMapsCacheZone,
            electricityMapsCacheIntensityInit = writerA.electricityMapsCacheIntensity,
            electricityMapsCacheFetchedAtMsInit = writerA.electricityMapsCacheFetchedAtMs,
        )
        val writerB = FakeSettingsWriter()
        val rigB = mkRepo(
            // Advance by 30 min — still within the 1-hour TTL.
            initialNow = writerA.electricityMapsCacheFetchedAtMs + 30L * 60_000L,
            readerInit = readerB,
            writer = writerB,
        )
        val second = rigB.repo.fetchCarbonIntensity("CY", "k")
        assertEquals("persistent cache must serve same value", 101.0, second!!, 0.0)
        assertEquals("network must not be called after restart", null, rigB.callLog["CY"])
    }

    @Test
    fun coldStartPastOneHour_persistentExpired_reFetches() = runTest {
        val writerA = FakeSettingsWriter()
        val readerA = FakeSettingsReader()
        val rigA = mkRepo(initialNow = 0L, readerInit = readerA, writer = writerA)
        rigA.repo.fetchCarbonIntensity("CY", "k")

        // Simulate restart 2 hours later — persistent entry is expired.
        val readerB = FakeSettingsReader(
            electricityMapsCacheZoneInit = writerA.electricityMapsCacheZone,
            electricityMapsCacheIntensityInit = writerA.electricityMapsCacheIntensity,
            electricityMapsCacheFetchedAtMsInit = writerA.electricityMapsCacheFetchedAtMs,
        )
        val writerB = FakeSettingsWriter()
        val rigB = mkRepo(
            initialNow = 2L * ElectricityMapsRepository.CACHE_TTL_MS,
            readerInit = readerB,
            writer = writerB,
        )
        val second = rigB.repo.fetchCarbonIntensity("CY", "k")
        assertEquals("expired persistent must re-fetch", 101.0, second!!, 0.0)
        assertEquals("network was called on cold-start re-fetch", 1, rigB.callLog["CY"])
    }

    @Test
    fun coldStartZoneMismatch_persistentIgnored_reFetches() = runTest {
        val writerA = FakeSettingsWriter()
        val readerA = FakeSettingsReader()
        val rigA = mkRepo(readerInit = readerA, writer = writerA)
        rigA.repo.fetchCarbonIntensity("CY", "k")

        // User switched zone to DE. The persisted CY cache must not be
        // served for DE.
        val readerB = FakeSettingsReader(
            electricityMapsCacheZoneInit = writerA.electricityMapsCacheZone,
            electricityMapsCacheIntensityInit = writerA.electricityMapsCacheIntensity,
            electricityMapsCacheFetchedAtMsInit = writerA.electricityMapsCacheFetchedAtMs,
        )
        val writerB = FakeSettingsWriter()
        val rigB = mkRepo(readerInit = readerB, writer = writerB)
        val second = rigB.repo.fetchCarbonIntensity("DE", "k")
        assertEquals("zone change must re-fetch", 101.0, second!!, 0.0)
        assertEquals(1, rigB.callLog["DE"])
    }

    @Test
    fun successfulFetch_writesAllThreePersistentKeys() = runTest {
        val writer = FakeSettingsWriter()
        val reader = FakeSettingsReader()
        val rig = mkRepo(initialNow = 42L, readerInit = reader, writer = writer)
        rig.repo.fetchCarbonIntensity("DE", "k")
        assertEquals("DE", writer.electricityMapsCacheZone)
        assertEquals(101.0, writer.electricityMapsCacheIntensity, 0.0)
        assertEquals(42L, writer.electricityMapsCacheFetchedAtMs)
    }
}
