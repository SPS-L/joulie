// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spsl.evtracker.domain.repository.UpdateResult
import org.spsl.evtracker.testing.FakeSettingsWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * JVM tests for [EvModelRepository] (TASK-91).
 *
 * Builds the repo with the `internal` test constructor that injects
 * a synthetic `httpFetch` lambda and a deterministic `nowProvider`, so
 * we never touch the network or `System.currentTimeMillis`. Disk I/O
 * (cache file + bundled-asset stream) is mocked through Mockito so
 * the test stays a pure JVM unit test (no Robolectric).
 */
class EvModelRepositoryTest {

    private lateinit var context: Context
    private lateinit var assets: AssetManager
    private lateinit var filesDir: File
    private lateinit var writer: FakeSettingsWriter

    private val bundledJson = """
        {
          "version": "bundled",
          "source": "bundled",
          "vehicle_count": 2,
          "vehicles": [
            { "make": "Tesla", "model": "Model 3", "variant": "LR RWD", "year": 2024, "battery_kwh": 75.0, "wltp_kwh_100km": 14.0 },
            { "make": "renault", "model": "Zoe", "variant": "R135", "year": 2023, "battery_kwh": 52.0, "wltp_kwh_100km": 17.2 }
          ]
        }
    """.trimIndent()

    private fun remoteJson(vehicleCount: Int = 60): String {
        val rows = (1..vehicleCount).joinToString(",") { i ->
            """{ "make": "Make$i", "model": "Model$i", "variant": "v", "year": 2024, "battery_kwh": 50.0, "wltp_kwh_100km": 16.0 }"""
        }
        return """{ "version": "2026-06-01", "source": "v1.4.2", "vehicle_count": $vehicleCount, "vehicles": [ $rows ] }"""
    }

    @Before
    fun setUp() {
        filesDir = createTempDir(prefix = "ev-db-test-")
        context = mock()
        assets = mock()
        whenever(context.filesDir).thenReturn(filesDir)
        whenever(context.assets).thenReturn(assets)
        whenever(assets.open(any())).thenAnswer {
            ByteArrayInputStream(bundledJson.toByteArray(Charsets.UTF_8))
        }
        writer = FakeSettingsWriter()
    }

    private fun newRepo(
        nowMs: Long = 1_700_000_000_000L,
        responseBody: String? = remoteJson(),
        failWith: Throwable? = null,
    ): EvModelRepository {
        val fetch: suspend (String) -> String = {
            if (failWith != null) throw failWith
            responseBody ?: error("no response stubbed")
        }
        return EvModelRepository(
            context = context,
            settingsWriter = writer,
            nowProvider = { nowMs },
            httpFetch = fetch,
        )
    }

    @Test
    fun bundledAssetIsLoadedWhenNoCacheFile() = runTest {
        val repo = newRepo()
        val makes = repo.makes()
        assertEquals(listOf("renault", "Tesla"), makes)
    }

    @Test
    fun cacheFileTakesPrecedenceOverBundled() = runTest {
        File(filesDir, "ev_models.json").writeText(
            """{ "version": "remote", "source": "v1", "vehicle_count": 1, "vehicles": [
                { "make": "BMW", "model": "i4", "battery_kwh": 80.0 }
            ] }
            """.trimIndent(),
        )
        val repo = newRepo()
        val makes = repo.makes()
        assertEquals(listOf("BMW"), makes)
    }

    @Test
    fun makesAreDistinctAndSortedCaseInsensitively() = runTest {
        val repo = newRepo()
        val makes = repo.makes()
        // renault (lowercase) and Tesla (uppercase) sort
        // case-insensitively: renault < Tesla.
        assertEquals(listOf("renault", "Tesla"), makes)
    }

    @Test
    fun modelsForMake_isCaseInsensitive() = runTest {
        val repo = newRepo()
        val rows = repo.modelsForMake("tesla")
        assertEquals(1, rows.size)
        assertEquals("Model 3", rows[0].model)
    }

    @Test
    fun updateFromRemote_writesCacheAndPersistsPrefs() = runTest {
        val repo = newRepo(nowMs = 12_345L)
        val result = repo.updateFromRemote()
        assertTrue(result is UpdateResult.Success)
        val success = result as UpdateResult.Success
        assertEquals(60, success.vehicleCount)
        assertEquals("2026-06-01", success.version)

        // Persisted via the atomic 3-key setter.
        assertEquals(12_345L, writer.evDbLastUpdatedAt)
        assertEquals("2026-06-01", writer.evDbVersion)
        assertEquals(60, writer.evDbVehicleCount)

        // Cache file written atomically.
        val cache = File(filesDir, "ev_models.json")
        assertTrue(cache.exists())
        assertTrue(cache.length() > 0)
    }

    @Test
    fun updateFromRemote_invalidatesInMemoryCache() = runTest {
        val repo = newRepo()
        // Prime the in-memory cache via a query against the bundled fallback.
        assertEquals(listOf("renault", "Tesla"), repo.makes())
        // Refresh — the cache file replaces the bundled fallback as
        // the source on the next load() call.
        repo.updateFromRemote()
        val makesAfter = repo.makes()
        assertEquals(60, makesAfter.size)
        assertTrue(makesAfter.first().startsWith("Make"))
    }

    @Test
    fun updateFromRemote_rejectsTooFewRows() = runTest {
        val repo = newRepo(responseBody = remoteJson(vehicleCount = 10))
        val result = repo.updateFromRemote()
        assertTrue(result is UpdateResult.ValidationFailed)
        assertEquals(10, (result as UpdateResult.ValidationFailed).vehicleCount)

        // No cache file written, no prefs touched.
        assertTrue(!File(filesDir, "ev_models.json").exists())
        assertEquals(0L, writer.evDbLastUpdatedAt)
    }

    @Test
    fun updateFromRemote_malformedJson_returnsParseError() = runTest {
        val repo = newRepo(responseBody = "not json")
        val result = repo.updateFromRemote()
        assertTrue(result is UpdateResult.ParseError)
        assertNotNull((result as UpdateResult.ParseError).cause)
    }

    @Test
    fun updateFromRemote_networkFailure_returnsNetworkError() = runTest {
        val repo = newRepo(failWith = IOException("dns"))
        val result = repo.updateFromRemote()
        assertTrue(result is UpdateResult.NetworkError)
        assertEquals("dns", (result as UpdateResult.NetworkError).cause?.message)
    }
}
