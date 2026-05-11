// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.spsl.evtracker.core.model.CarbonIntensityBucket
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.data.repository.ElectricityMapsRepository
import javax.inject.Inject

/**
 * Pure-domain mapper from the persistent Electricity Maps cache + the
 * live Settings flags to a [CarbonIntensityUiState] (TASK-82).
 *
 * Inputs come from `SettingsReader` flows (cache zone / intensity /
 * fetchedAtMs, the user-configurable zone + apiKey + co2Enabled), plus
 * a `now` clock and the ViewModel's `isRefreshing` flag. Output is one
 * of the four sealed states defined on [CarbonIntensityUiState].
 *
 * No Android types touched — JVM-testable with one row per transition.
 */
class CarbonIntensityFormatter @Inject constructor() {

    fun format(
        co2Enabled: Boolean,
        apiKey: String,
        currentZone: String,
        cacheZone: String,
        cacheIntensityGCo2PerKwh: Double,
        cacheFetchedAtMs: Long,
        nowMs: Long,
        isRefreshing: Boolean,
    ): CarbonIntensityUiState {
        if (!co2Enabled) return CarbonIntensityUiState.Hidden
        if (apiKey.isBlank()) return CarbonIntensityUiState.Hidden

        val cacheFresh = cacheZone == currentZone &&
            cacheFetchedAtMs > 0L &&
            cacheIntensityGCo2PerKwh > 0.0 &&
            nowMs - cacheFetchedAtMs < ElectricityMapsRepository.CACHE_TTL_MS

        if (cacheFresh) {
            return CarbonIntensityUiState.Ready(
                intensityGCo2PerKwh = cacheIntensityGCo2PerKwh,
                bucket = CarbonIntensityBucket.forValue(cacheIntensityGCo2PerKwh),
                fetchedAtMs = cacheFetchedAtMs,
            )
        }
        if (isRefreshing) return CarbonIntensityUiState.Loading
        return CarbonIntensityUiState.Error
    }
}
