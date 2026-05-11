// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.data.local.evdb.EvModelDatabase
import org.spsl.evtracker.domain.repository.EvModelReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.repository.UpdateResult
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete EV reference data source (TASK-91).
 *
 * Load order on first call:
 *   1. `context.filesDir/ev_models.json` (most recent remote refresh)
 *   2. `assets/ev_models.json` (bundled fallback)
 *
 * The parsed dataset is cached in-memory for the rest of the process
 * lifetime. [updateFromRemote] hits [REMOTE_URL], validates the
 * payload has at least [EvModelReader.VALIDATION_FLOOR] rows, writes
 * it atomically (`.tmp` → rename), invalidates the cache, and persists
 * the timestamp + version + count through [SettingsWriter.setEvDbCache].
 *
 * All network and disk I/O runs on [Dispatchers.IO] regardless of
 * the caller's dispatcher (TASK-90 lesson: `HttpURLConnection` on the
 * main dispatcher throws `NetworkOnMainThreadException` which the bare
 * `Exception` catch in a fetch loop will swallow).
 */
@Singleton
class EvModelRepository internal constructor(
    private val context: Context,
    private val settingsWriter: SettingsWriter,
    private val nowProvider: () -> Long,
    private val httpFetch: suspend (String) -> String,
) : EvModelReader {

    // Hilt-driven production constructor. The test-only seams above
    // default to wall-clock time + a real HTTP GET; tests override via
    // the `internal` primary constructor without needing reflection or
    // PowerMock-style instrumentation.
    @Inject constructor(
        @ApplicationContext context: Context,
        settingsWriter: SettingsWriter,
    ) : this(
        context = context,
        settingsWriter = settingsWriter,
        nowProvider = { System.currentTimeMillis() },
        httpFetch = ::defaultHttpFetch,
    )

    private val gson: Gson = Gson()
    private val mutex = Mutex()

    @Volatile private var cached: EvModelDatabase? = null

    private suspend fun load(): EvModelDatabase = mutex.withLock {
        cached?.let { return@withLock it }
        val parsed = withContext(Dispatchers.IO) {
            readCacheFile() ?: readBundledAsset()
        }
        cached = parsed
        parsed
    }

    override suspend fun makes(): List<String> =
        load().vehicles
            .asSequence()
            .map { it.make.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .toList()

    override suspend fun modelsForMake(make: String): List<EvModel> {
        val needle = make.trim()
        if (needle.isEmpty()) return emptyList()
        val byModel = Comparator<EvModel> { a, b ->
            String.CASE_INSENSITIVE_ORDER.compare(a.model, b.model)
        }
        return load().vehicles
            .asSequence()
            .filter { it.make.equals(needle, ignoreCase = true) }
            .sortedWith(byModel.thenBy { it.year ?: Int.MAX_VALUE })
            .toList()
    }

    override suspend fun updateFromRemote(): UpdateResult = withContext(Dispatchers.IO) {
        val body = try {
            httpFetch(REMOTE_URL)
        } catch (e: IOException) {
            return@withContext UpdateResult.NetworkError(e)
        } catch (e: SecurityException) {
            return@withContext UpdateResult.NetworkError(e)
        }
        val parsed = try {
            gson.fromJson(body, EvModelDatabase::class.java)
        } catch (e: JsonSyntaxException) {
            return@withContext UpdateResult.ParseError(e)
        } catch (e: IllegalStateException) {
            return@withContext UpdateResult.ParseError(e)
        }
        if (parsed == null || parsed.vehicles.size < EvModelReader.VALIDATION_FLOOR) {
            return@withContext UpdateResult.ValidationFailed(parsed?.vehicles?.size ?: 0)
        }
        try {
            atomicWriteCache(body)
        } catch (e: IOException) {
            return@withContext UpdateResult.NetworkError(e)
        }
        mutex.withLock { cached = parsed }
        val version = parsed.version.ifBlank { "remote" }
        settingsWriter.setEvDbCache(
            lastUpdatedAtMs = nowProvider(),
            version = version,
            vehicleCount = parsed.vehicles.size,
        )
        UpdateResult.Success(version, parsed.vehicles.size)
    }

    private fun readCacheFile(): EvModelDatabase? {
        val file = cacheFile()
        if (!file.exists() || file.length() == 0L) return null
        return try {
            gson.fromJson(file.readText(Charsets.UTF_8), EvModelDatabase::class.java)
                ?.takeIf { it.vehicles.isNotEmpty() }
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun readBundledAsset(): EvModelDatabase {
        return context.assets.open(ASSET_NAME).use { stream ->
            val text = stream.bufferedReader(Charsets.UTF_8).readText()
            gson.fromJson(text, EvModelDatabase::class.java)
                ?: EvModelDatabase(version = "bundled", source = "bundled")
        }
    }

    private fun atomicWriteCache(body: String) {
        val target = cacheFile()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(body, Charsets.UTF_8)
        // Atomic on POSIX; on Android filesDir we're inside the
        // app sandbox so renameTo lands on the same filesystem.
        if (!tmp.renameTo(target)) {
            // Fallback: copy + delete. renameTo can fail on some
            // emulator images that surface the cache dir via FUSE.
            target.writeText(body, Charsets.UTF_8)
            tmp.delete()
        }
    }

    private fun cacheFile(): File = File(context.filesDir, ASSET_NAME)

    companion object {
        const val REMOTE_URL =
            "https://github.com/SPS-L/joulie/releases/download/ev-db-latest/ev_models.json"
        internal const val ASSET_NAME = "ev_models.json"
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

/**
 * Default network reader. Lives at file scope so the [EvModelRepository]
 * primary constructor can reference it without a `Companion` qualifier;
 * tests pass a stub via the `internal` primary constructor and never
 * call this. Always invoked on [Dispatchers.IO] by the surrounding
 * [EvModelRepository.updateFromRemote] block.
 */
@Suppress("BlockingMethodInNonBlockingContext")
private suspend fun defaultHttpFetch(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("HTTP $code from $url")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
