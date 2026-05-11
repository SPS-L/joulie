// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.domain.repository.FetchOutcome
import org.spsl.evtracker.domain.repository.intensityOrNull
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

/**
 * JVM-side cache + throttle behaviour for [ElectricityMapsRepository]
 * (TASK-80, TASK-81, TASK-90).
 *
 * The repo guarantees the Electricity Maps API is called at most once
 * per zone per hour. Two layers enforce it:
 *
 *  1. In-memory cache (fast path within a single process).
 *  2. Persistent cache in DataStore (survives process restart).
 *
 * TASK-90 turned the return type into a sealed [FetchOutcome] so the UI
 * can render specific reasons for failure. The test seam
 * [ElectricityMapsRepository.httpFetcher] now returns [ElectricityMapsRepository.HttpResult]
 * so tests can inject auth / rate-limit / server / network failure modes
 * deterministically.
 */
class ElectricityMapsRepositoryTest {

    private class Rig(
        val repo: ElectricityMapsRepository,
        val callLog: MutableMap<String, Int>,
        val setNowMs: (Long) -> Unit,
    )

    private fun mkRepo(
        initialNow: Long = 1_000_000L,
        readerInit: FakeSettingsReader = FakeSettingsReader(),
        writer: FakeSettingsWriter = FakeSettingsWriter(),
        responses: MutableMap<String, MutableList<ElectricityMapsRepository.HttpResult>> = mutableMapOf(),
    ): Rig {
        var nowState = initialNow
        val repo = ElectricityMapsRepository(readerInit, writer)
        repo.clock = { nowState }
        val callLog = mutableMapOf<String, Int>()
        repo.httpFetcher = { zone, _ ->
            callLog[zone] = (callLog[zone] ?: 0) + 1
            val q = responses[zone]
            if (q != null && q.isNotEmpty()) {
                q.removeAt(0)
            } else {
                // Default: unique value per call so cache hits are visible
                // as "same value as the first hit" vs "new value".
                ElectricityMapsRepository.HttpResult.Body(
                    "{\"carbonIntensity\": ${100 + (callLog[zone] ?: 1)}}",
                )
            }
        }
        return Rig(repo, callLog) { nowState = it }
    }

    @Test
    fun secondFetchWithinHour_servesFromMemoryCache_noSecondNetworkCall() = runTest {
        val rig = mkRepo()
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first.intensityOrNull!!, 0.0)
        assertEquals(101.0, second.intensityOrNull!!, 0.0)
        assertEquals(1, rig.callLog["CY"])
    }

    @Test
    fun fetchPastOneHourTtl_reFetchesFromNetwork() = runTest {
        val rig = mkRepo(initialNow = 0L)
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        rig.setNowMs(ElectricityMapsRepository.CACHE_TTL_MS)
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(101.0, first.intensityOrNull!!, 0.0)
        assertEquals(102.0, second.intensityOrNull!!, 0.0)
        assertEquals(2, rig.callLog["CY"])
    }

    @Test
    fun blankApiKey_returnsDisabled_andDoesNotCallNetwork() = runTest {
        val rig = mkRepo()
        assertEquals(FetchOutcome.Disabled, rig.repo.fetchCarbonIntensity("CY", ""))
        assertEquals(null, rig.callLog["CY"])
    }

    @Test
    fun networkFailure_returnsNetworkError_andDoesNotCacheFailure() = runTest {
        val responses = mutableMapOf(
            "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.Network,
                ElectricityMapsRepository.HttpResult.Body("{\"carbonIntensity\": 412}"),
            ),
        )
        val rig = mkRepo(initialNow = 0L, responses = responses)
        val first = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(FetchOutcome.NetworkError, first)
        assertEquals(FetchOutcome.NetworkError, rig.repo.lastError.value)
        val second = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(412.0, second.intensityOrNull!!, 0.0)
        assertNull("lastError clears on subsequent success", rig.repo.lastError.value)
        assertEquals(2, rig.callLog["CY"])
    }

    @Test
    fun authFailure_returnsAuthError_andUpdatesLastError() = runTest {
        val responses = mutableMapOf(
            "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.Auth,
            ),
        )
        val rig = mkRepo(responses = responses)
        val outcome = rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(FetchOutcome.AuthError, outcome)
        assertEquals(FetchOutcome.AuthError, rig.repo.lastError.value)
    }

    @Test
    fun rateLimitedResponse_returnsRateLimited() = runTest {
        val responses = mutableMapOf(
            "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.RateLimited,
            ),
        )
        val rig = mkRepo(responses = responses)
        assertEquals(FetchOutcome.RateLimited, rig.repo.fetchCarbonIntensity("CY", "k"))
        assertEquals(FetchOutcome.RateLimited, rig.repo.lastError.value)
    }

    @Test
    fun serverErrorResponse_returnsServerError() = runTest {
        val responses = mutableMapOf(
            "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.Server,
            ),
        )
        val rig = mkRepo(responses = responses)
        assertEquals(FetchOutcome.ServerError, rig.repo.fetchCarbonIntensity("CY", "k"))
    }

    @Test
    fun clearCache_resetsLastError() = runTest {
        val responses = mutableMapOf(
            "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.Auth,
            ),
        )
        val rig = mkRepo(responses = responses)
        rig.repo.fetchCarbonIntensity("CY", "k")
        assertEquals(FetchOutcome.AuthError, rig.repo.lastError.value)
        rig.repo.clearCache()
        assertNull(rig.repo.lastError.value)
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
    fun unparseableJson_returnsServerError() = runTest {
        val rig = mkRepo(
            responses = mutableMapOf(
                "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                    ElectricityMapsRepository.HttpResult.Body("not-json"),
                ),
            ),
        )
        assertEquals(FetchOutcome.ServerError, rig.repo.fetchCarbonIntensity("CY", "k"))
    }

    @Test
    fun missingCarbonIntensityField_returnsServerError() = runTest {
        val rig = mkRepo(
            responses = mutableMapOf(
                "CY" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                    ElectricityMapsRepository.HttpResult.Body("{\"zone\": \"CY\"}"),
                ),
            ),
        )
        assertEquals(FetchOutcome.ServerError, rig.repo.fetchCarbonIntensity("CY", "k"))
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
        assertEquals(101.0, first.intensityOrNull!!, 0.0)
        assertEquals(1, rigA.callLog["CY"])

        val readerB = FakeSettingsReader(
            electricityMapsCacheZoneInit = writerA.electricityMapsCacheZone,
            electricityMapsCacheIntensityInit = writerA.electricityMapsCacheIntensity,
            electricityMapsCacheFetchedAtMsInit = writerA.electricityMapsCacheFetchedAtMs,
        )
        val writerB = FakeSettingsWriter()
        val rigB = mkRepo(
            initialNow = writerA.electricityMapsCacheFetchedAtMs + 30L * 60_000L,
            readerInit = readerB,
            writer = writerB,
        )
        val second = rigB.repo.fetchCarbonIntensity("CY", "k")
        assertEquals("persistent cache must serve same value", 101.0, second.intensityOrNull!!, 0.0)
        assertEquals("network must not be called after restart", null, rigB.callLog["CY"])
    }

    @Test
    fun coldStartPastOneHour_persistentExpired_reFetches() = runTest {
        val writerA = FakeSettingsWriter()
        val readerA = FakeSettingsReader()
        val rigA = mkRepo(initialNow = 0L, readerInit = readerA, writer = writerA)
        rigA.repo.fetchCarbonIntensity("CY", "k")

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
        assertEquals("expired persistent must re-fetch", 101.0, second.intensityOrNull!!, 0.0)
        assertEquals("network was called on cold-start re-fetch", 1, rigB.callLog["CY"])
    }

    @Test
    fun coldStartZoneMismatch_persistentIgnored_reFetches() = runTest {
        val writerA = FakeSettingsWriter()
        val readerA = FakeSettingsReader()
        val rigA = mkRepo(readerInit = readerA, writer = writerA)
        rigA.repo.fetchCarbonIntensity("CY", "k")

        val readerB = FakeSettingsReader(
            electricityMapsCacheZoneInit = writerA.electricityMapsCacheZone,
            electricityMapsCacheIntensityInit = writerA.electricityMapsCacheIntensity,
            electricityMapsCacheFetchedAtMsInit = writerA.electricityMapsCacheFetchedAtMs,
        )
        val writerB = FakeSettingsWriter()
        val rigB = mkRepo(readerInit = readerB, writer = writerB)
        val second = rigB.repo.fetchCarbonIntensity("DE", "k")
        assertEquals("zone change must re-fetch", 101.0, second.intensityOrNull!!, 0.0)
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

    @Test
    fun probeApiKey_bypassesCache_anddoesNotPollutePersistent() = runTest {
        val writer = FakeSettingsWriter()
        val rig = mkRepo(writer = writer)
        // Pre-warm a cache entry for CY so we know probe doesn't disturb it.
        rig.repo.fetchCarbonIntensity("CY", "k")
        val zoneBefore = writer.electricityMapsCacheZone
        val intensityBefore = writer.electricityMapsCacheIntensity
        val fetchedAtBefore = writer.electricityMapsCacheFetchedAtMs

        rig.repo.probeApiKey("DE", "candidate")

        assertEquals(zoneBefore, writer.electricityMapsCacheZone)
        assertEquals(intensityBefore, writer.electricityMapsCacheIntensity, 0.0)
        assertEquals(fetchedAtBefore, writer.electricityMapsCacheFetchedAtMs)
    }

    @Test
    fun probeApiKey_doesNotUpdateLastError_evenOnFailure() = runTest {
        val responses = mutableMapOf(
            "DE" to mutableListOf<ElectricityMapsRepository.HttpResult>(
                ElectricityMapsRepository.HttpResult.Auth,
            ),
        )
        val rig = mkRepo(responses = responses)
        val outcome = rig.repo.probeApiKey("DE", "bad-key")
        assertEquals(FetchOutcome.AuthError, outcome)
        assertNull(
            "probeApiKey must not touch lastError so the dashboard pill stays unaffected",
            rig.repo.lastError.value,
        )
    }

    @Test
    fun probeApiKey_blankInputs_returnsDisabled() = runTest {
        val rig = mkRepo()
        assertEquals(FetchOutcome.Disabled, rig.repo.probeApiKey("", "k"))
        assertEquals(FetchOutcome.Disabled, rig.repo.probeApiKey("CY", ""))
        assertTrue("no HTTP call when inputs are blank", rig.callLog.isEmpty())
    }
}
