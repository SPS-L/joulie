// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.CarbonIntensityBucket
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.data.repository.ElectricityMapsRepository

class CarbonIntensityFormatterTest {

    private val fmt = CarbonIntensityFormatter()

    // ── State machine ────────────────────────────────────────────────────

    @Test
    fun co2Disabled_returnsHidden() {
        val state = fmt.format(
            co2Enabled = false,
            apiKey = "k",
            currentZone = "CY",
            cacheZone = "CY",
            cacheIntensityGCo2PerKwh = 412.0,
            cacheFetchedAtMs = 1_000L,
            nowMs = 2_000L,
            isRefreshing = false,
        )
        assertEquals(CarbonIntensityUiState.Hidden, state)
    }

    @Test
    fun apiKeyBlank_returnsHidden() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "",
            currentZone = "CY",
            cacheZone = "CY",
            cacheIntensityGCo2PerKwh = 412.0,
            cacheFetchedAtMs = 1_000L,
            nowMs = 2_000L,
            isRefreshing = false,
        )
        assertEquals(CarbonIntensityUiState.Hidden, state)
    }

    @Test
    fun freshCacheSameZone_returnsReady() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "k",
            currentZone = "CY",
            cacheZone = "CY",
            cacheIntensityGCo2PerKwh = 412.0,
            cacheFetchedAtMs = 1_000L,
            nowMs = 1_000L + 30L * 60 * 1_000L,
            isRefreshing = false,
        )
        assertEquals(
            CarbonIntensityUiState.Ready(
                intensityGCo2PerKwh = 412.0,
                bucket = CarbonIntensityBucket.MODERATE,
                fetchedAtMs = 1_000L,
            ),
            state,
        )
    }

    @Test
    fun zoneMismatch_andNotRefreshing_returnsError() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "k",
            currentZone = "DE",
            cacheZone = "CY",
            cacheIntensityGCo2PerKwh = 412.0,
            cacheFetchedAtMs = 1_000L,
            nowMs = 1_000L + 30L * 60 * 1_000L,
            isRefreshing = false,
        )
        assertEquals(CarbonIntensityUiState.Error, state)
    }

    @Test
    fun expiredCache_andNotRefreshing_returnsError() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "k",
            currentZone = "CY",
            cacheZone = "CY",
            cacheIntensityGCo2PerKwh = 412.0,
            cacheFetchedAtMs = 0L,
            nowMs = ElectricityMapsRepository.CACHE_TTL_MS + 1L,
            isRefreshing = false,
        )
        assertEquals(CarbonIntensityUiState.Error, state)
    }

    @Test
    fun missingCache_andRefreshing_returnsLoading() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "k",
            currentZone = "CY",
            cacheZone = "",
            cacheIntensityGCo2PerKwh = 0.0,
            cacheFetchedAtMs = 0L,
            nowMs = 1_000L,
            isRefreshing = true,
        )
        assertEquals(CarbonIntensityUiState.Loading, state)
    }

    @Test
    fun missingCache_andNotRefreshing_returnsError() {
        val state = fmt.format(
            co2Enabled = true,
            apiKey = "k",
            currentZone = "CY",
            cacheZone = "",
            cacheIntensityGCo2PerKwh = 0.0,
            cacheFetchedAtMs = 0L,
            nowMs = 1_000L,
            isRefreshing = false,
        )
        assertEquals(CarbonIntensityUiState.Error, state)
    }

    // ── Bucket boundaries ────────────────────────────────────────────────

    @Test
    fun bucketBoundaries_lowerEdges() {
        assertEquals(CarbonIntensityBucket.VERY_LOW, CarbonIntensityBucket.forValue(0.0))
        assertEquals(CarbonIntensityBucket.VERY_LOW, CarbonIntensityBucket.forValue(149.99))
        assertEquals(CarbonIntensityBucket.LOW, CarbonIntensityBucket.forValue(150.0))
        assertEquals(CarbonIntensityBucket.LOW, CarbonIntensityBucket.forValue(399.99))
        assertEquals(CarbonIntensityBucket.MODERATE, CarbonIntensityBucket.forValue(400.0))
        assertEquals(CarbonIntensityBucket.MODERATE, CarbonIntensityBucket.forValue(649.99))
        assertEquals(CarbonIntensityBucket.HIGH, CarbonIntensityBucket.forValue(650.0))
        assertEquals(CarbonIntensityBucket.HIGH, CarbonIntensityBucket.forValue(899.99))
        assertEquals(CarbonIntensityBucket.VERY_HIGH, CarbonIntensityBucket.forValue(900.0))
        assertEquals(CarbonIntensityBucket.VERY_HIGH, CarbonIntensityBucket.forValue(1_500.0))
    }

    @Test
    fun cyprusAverageLandsInModerate() {
        // 577 g/kWh — TASK-20 default before TASK-81 dropped the static pref.
        assertEquals(CarbonIntensityBucket.MODERATE, CarbonIntensityBucket.forValue(577.0))
    }
}
