// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots.fixtures

import org.spsl.evtracker.core.model.CarbonIntensityBucket
import org.spsl.evtracker.core.model.CarbonIntensityUiState

/**
 * Canonical [CarbonIntensityUiState] fixtures for the TASK-86 Roborazzi
 * baselines. Values are pinned so the captured pixel output is stable.
 */
object CarbonIntensityFixtures {

    /** Fixed wall-clock for deterministic "Updated X ago" rendering.
     *  2026-01-15T12:00:00Z in epoch millis. */
    const val NOW_MS: Long = 1_768_521_600_000L

    /** Eight minutes before [NOW_MS] yields a stable "8 min. ago" subtitle
     *  via `DateUtils.getRelativeTimeSpanString` at MINUTE_IN_MILLIS
     *  resolution. */
    private const val FETCHED_AT_MS: Long = NOW_MS - 8L * 60L * 1000L

    /** Representative intensity per bucket. Each value sits well inside its
     *  half-open band so boundary jitter in [CarbonIntensityBucket.forValue]
     *  cannot reclassify it. */
    fun ready(bucket: CarbonIntensityBucket): CarbonIntensityUiState.Ready {
        val value = when (bucket) {
            CarbonIntensityBucket.VERY_LOW -> 80.0
            CarbonIntensityBucket.LOW -> 250.0
            CarbonIntensityBucket.MODERATE -> 500.0
            CarbonIntensityBucket.HIGH -> 780.0
            CarbonIntensityBucket.VERY_HIGH -> 1200.0
        }
        return CarbonIntensityUiState.Ready(
            intensityGCo2PerKwh = value,
            bucket = bucket,
            fetchedAtMs = FETCHED_AT_MS,
        )
    }
}
