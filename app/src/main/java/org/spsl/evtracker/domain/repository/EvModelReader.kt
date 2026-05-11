// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.evdb.EvModel

/**
 * Narrow domain-facing surface for the curated EV reference dataset
 * shipped as `app/src/main/assets/ev_models.json` and refreshable from
 * the `ev-db-latest` GitHub release (TASK-91).
 *
 * Implementations layer two sources: a user-replaceable file at
 * `context.filesDir/ev_models.json`, written by [updateFromRemote], and
 * a bundled fallback at `assets/ev_models.json`. The first-load is
 * lazy, cached in-memory across the process lifetime, and serialised
 * by a `Mutex` so concurrent first-callers don't double-parse the JSON.
 *
 * Per CLAUDE.md's narrow-IF rule, ViewModels and use cases depend on
 * this interface; the concrete `EvModelRepository` is only referenced
 * from `di/`.
 */
interface EvModelReader {
    /**
     * Distinct make names, sorted by locale-aware case-insensitive
     * comparison. Drives the "Make" `AutoCompleteTextView` adapter.
     */
    suspend fun makes(): List<String>

    /**
     * All curated rows whose `make` matches [make] (case-insensitive,
     * trimmed), sorted by `model` then `year` (nulls last). Drives the
     * "Model" `AutoCompleteTextView` adapter once the user has picked a
     * make.
     */
    suspend fun modelsForMake(make: String): List<EvModel>

    /**
     * Refresh the local cache from the GitHub release asset. The
     * implementation downloads, parses, validates (at least
     * [VALIDATION_FLOOR] vehicles), atomically writes the JSON to
     * `context.filesDir/ev_models.json`, invalidates the in-memory
     * cache, and persists `(lastUpdatedAt, version, vehicleCount)`
     * through [SettingsWriter.setEvDbCache] in a single DataStore
     * transaction. Returns a typed [UpdateResult] so the Settings UI
     * can render specific reasons per failure class.
     */
    suspend fun updateFromRemote(): UpdateResult

    companion object {
        /**
         * Validation floor for a remote refresh. A successful upstream
         * dataset ships hundreds of rows; anything below this is a
         * sanity check that something has gone catastrophically wrong
         * upstream, and we'd rather keep the working local cache than
         * replace it with a degraded one.
         */
        const val VALIDATION_FLOOR = 50
    }
}

/** Typed outcome of [EvModelReader.updateFromRemote]. */
sealed class UpdateResult {
    /** New cache file written. */
    data class Success(val version: String, val vehicleCount: Int) : UpdateResult()

    /** Remote returned fewer than [EvModelReader.VALIDATION_FLOOR] rows. */
    data class ValidationFailed(val vehicleCount: Int) : UpdateResult()

    /** Could not reach the GitHub release asset (DNS, no internet, 4xx, etc.). */
    data class NetworkError(val cause: Throwable? = null) : UpdateResult()

    /** Response body was not valid JSON or didn't match the expected shape. */
    data class ParseError(val cause: Throwable? = null) : UpdateResult()
}
