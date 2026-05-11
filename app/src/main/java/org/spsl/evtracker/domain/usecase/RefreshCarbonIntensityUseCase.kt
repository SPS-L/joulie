// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.SettingsReader
import javax.inject.Inject

/**
 * Triggers an Electricity Maps refresh against the user's configured zone
 * (TASK-82). Returns `true` on a successful fetch (or persistent-cache
 * hit within the 1-hour TTL — both look identical to callers).
 *
 * The actual rate-limiting lives in `ElectricityMapsRepository`: this
 * use case unconditionally delegates; the repo's persistent throttle
 * decides whether to make an HTTP call.
 *
 * Fire-and-forget callers (MainViewModel.init,
 * DashboardViewModel.init) don't read the result. The retry-tap path
 * (DashboardViewModel.onRefreshTapped) uses the result for its
 * `isRefreshing` flag.
 */
class RefreshCarbonIntensityUseCase @Inject constructor(
    private val settingsReader: SettingsReader,
    private val carbonIntensitySource: CarbonIntensitySource,
) {
    suspend operator fun invoke(): Boolean {
        if (!settingsReader.co2Enabled.first()) return false
        val apiKey = settingsReader.electricityMapsApiKey.first()
        if (apiKey.isBlank()) return false
        val zone = settingsReader.electricityMapsZone.first()
        return carbonIntensitySource.fetchCarbonIntensity(zone, apiKey) != null
    }
}
