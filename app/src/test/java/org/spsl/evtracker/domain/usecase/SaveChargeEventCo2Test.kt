// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.testing.FakeCarbonIntensitySource
import org.spsl.evtracker.testing.FakeSaveChargeEventGateway

/**
 * Per-event grid-intensity behaviour for [SaveChargeEventUseCase] (TASK-80,
 * TASK-81). The contract is strict — there is NO manual fallback:
 *
 *  - co2Enabled=false → null, no fetch.
 *  - co2Enabled=true + valid key + live value → live value stored.
 *  - co2Enabled=true + blank key → null (no fetch, no fallback).
 *  - co2Enabled=true + fetch returns null → null (no fallback).
 *
 * The "cache hit within the hour" assertion lives at the repository level
 * — exercising it through the use case would retest the fake's
 * pass-through. The real cache (in-memory + persistent) is JVM-testable
 * directly via `ElectricityMapsRepositoryTest`.
 */
class SaveChargeEventCo2Test {

    private fun input(eventDate: Long = 1_000L, odo: Double = 100.0, kwh: Double = 10.0) =
        SaveChargeEventInput(
            carId = 1L,
            eventDate = eventDate,
            odometerKm = odo,
            kwhAdded = kwh,
            chargeType = ChargeType.AC,
        )

    @Test
    fun co2_disabled_storesNull_andSkipsFetch() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(false)
        gw.settingsReader.setElectricityMapsApiKey("key")
        gw.carbonIntensitySource.nextValue = 444.0

        val result = gw.useCase(input())

        assert(result is SaveChargeEventResult.Success)
        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertNull("CO₂ off should not persist intensity", saved.gridIntensityGCo2PerKwh)
        assertEquals("fetch must be skipped when CO₂ is off", 0, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun co2_enabled_withApiKey_andLiveValue_storesLiveValue() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("key")
        gw.settingsReader.setElectricityMapsZone("CY")
        gw.carbonIntensitySource.nextValue = 412.0

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertEquals(412.0, saved.gridIntensityGCo2PerKwh!!, 0.0)
        assertEquals(1, gw.carbonIntensitySource.callCount)
        assertEquals("CY", gw.carbonIntensitySource.lastZone)
        assertEquals("key", gw.carbonIntensitySource.lastApiKey)
    }

    @Test
    fun co2_enabled_blankApiKey_storesNull_noFetch_noFallback() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("")

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertNull("blank key with no fallback must yield null intensity", saved.gridIntensityGCo2PerKwh)
        assertEquals("blank key must skip the network call", 0, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun co2_enabled_fetchReturnsNull_storesNull_noFallback() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("key")
        gw.carbonIntensitySource.nextValue = null

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertNull("network failure with no fallback must yield null intensity", saved.gridIntensityGCo2PerKwh)
        assertEquals(1, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun twoSaves_inSameSession_callFetchOncePerSave_cacheLivesInRepository() = runTest {
        // The fake source is a pass-through; the real cache (in-memory +
        // persistent) lives in ElectricityMapsRepository. The use case
        // delegates fully — verify it calls the source both times so
        // future drift (e.g. accidental use-case-side caching) is caught.
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("key")
        gw.carbonIntensitySource.nextValue = 412.0

        gw.useCase(input(eventDate = 1_000L, odo = 100.0))
        gw.useCase(input(eventDate = 2_000L, odo = 200.0))

        assertEquals(2, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun fakeCarbonIntensitySource_passesThroughAndCounts() = runTest {
        val src = FakeCarbonIntensitySource(nextValue = 250.0)
        val first = src.fetchCarbonIntensity("CY", "k")
        val second = src.fetchCarbonIntensity("DE", "k2")
        assertEquals(250.0, first!!, 0.0)
        assertEquals(250.0, second!!, 0.0)
        assertEquals(2, src.callCount)
        assertEquals("DE", src.lastZone)
        assertEquals("k2", src.lastApiKey)
    }
}
