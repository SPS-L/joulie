// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.FetchOutcome
import org.spsl.evtracker.domain.repository.SettingsReader
import javax.inject.Inject

/**
 * Triggers an Electricity Maps refresh against the user's configured zone
 * (TASK-82). Returns the underlying [FetchOutcome] so the dashboard pill
 * can render a specific Error reason (auth / network / rate-limited /
 * server) instead of a generic "Tap to retry".
 *
 * The actual rate-limiting lives in `ElectricityMapsRepository`: this
 * use case unconditionally delegates; the repo's persistent throttle
 * decides whether to make an HTTP call.
 */
class RefreshCarbonIntensityUseCase @Inject constructor(
    private val settingsReader: SettingsReader,
    private val carbonIntensitySource: CarbonIntensitySource,
) {
    suspend operator fun invoke(): FetchOutcome {
        if (!settingsReader.co2Enabled.first()) return FetchOutcome.Disabled
        val apiKey = settingsReader.electricityMapsApiKey.first()
        if (apiKey.isBlank()) return FetchOutcome.Disabled
        val zone = settingsReader.electricityMapsZone.first()
        return carbonIntensitySource.fetchCarbonIntensity(zone, apiKey)
    }
}
