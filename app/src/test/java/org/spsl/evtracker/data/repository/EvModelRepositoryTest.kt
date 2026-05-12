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
        // Merge semantics (TASK-91 v1.14.0): the 60 fresh vehicles
        // unionise with the 2 bundled rows (Tesla Model 3 LR RWD 2024,
        // renault Zoe R135 2023) because they have disjoint identity
        // keys. Total: 62.
        assertEquals(62, success.vehicleCount)
        assertEquals("2026-06-01", success.version)

        // Persisted via the atomic 3-key setter — count reflects the merge.
        assertEquals(12_345L, writer.evDbLastUpdatedAt)
        assertEquals("2026-06-01", writer.evDbVersion)
        assertEquals(62, writer.evDbVehicleCount)

        // Cache file written atomically.
        val cache = File(filesDir, "ev_models.json")
        assertTrue(cache.exists())
        assertTrue(cache.length() > 0)
    }

    @Test
    fun updateFromRemote_mergesLocalAndRemote() = runTest {
        val repo = newRepo()
        // Prime the in-memory cache via a query against the bundled fallback.
        assertEquals(listOf("renault", "Tesla"), repo.makes())
        // Refresh — merge semantics: remote rows are added but the
        // bundled Tesla / renault rows survive.
        repo.updateFromRemote()
        val makesAfter = repo.makes()
        assertEquals(62, makesAfter.size)
        assertTrue("Tesla" in makesAfter)
        assertTrue("renault" in makesAfter)
        assertTrue(makesAfter.any { it.startsWith("Make") })
    }

    @Test
    fun updateFromRemote_neverShrinks() = runTest {
        // Pre-populate the cache file with 10 vehicles that have no
        // overlap with the 60-row remote payload. The merged DB must
        // therefore have exactly 70 entries.
        val priorRows = (1..10).joinToString(",") { i ->
            """{ "make": "Prior$i", "model": "P$i", "variant": "", "year": 2023, "battery_kwh": 60.0, "wltp_kwh_100km": 15.0 }"""
        }
        File(filesDir, "ev_models.json").writeText(
            """{ "version": "old", "source": "test", "vehicle_count": 10, "vehicles": [ $priorRows ] }""",
        )
        val repo = newRepo()
        val result = repo.updateFromRemote()
        assertTrue(result is UpdateResult.Success)
        assertEquals(70, (result as UpdateResult.Success).vehicleCount)
        val makesAfter = repo.makes()
        assertTrue(makesAfter.any { it == "Prior1" })
        assertTrue(makesAfter.any { it == "Make1" })
    }

    @Test
    fun updateFromRemote_remoteWinsOnConflict() = runTest {
        // Same identity key (make, model, variant, year) in cache as in
        // remote, but with a different battery. The remote value must
        // overwrite the local row.
        File(filesDir, "ev_models.json").writeText(
            """{ "version": "old", "source": "test", "vehicle_count": 1, "vehicles": [
                { "make": "Make1", "model": "Model1", "variant": "v", "year": 2024, "battery_kwh": 999.0, "wltp_kwh_100km": 99.0 }
            ] }""",
        )
        val repo = newRepo()
        repo.updateFromRemote()
        val rows = repo.modelsForMake("Make1")
        assertEquals(1, rows.size)
        // Remote payload uses battery_kwh = 50.0 for Make1.
        assertEquals(50.0, rows[0].batteryKwh, 0.0001)
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
