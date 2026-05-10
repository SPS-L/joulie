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
 * Verifies the four CO₂ branches of [SaveChargeEventUseCase] (TASK-80):
 *
 *  - off entirely → entity column stays `null`, no fetch call.
 *  - on + valid API key + live value → live value persisted.
 *  - on + blank API key → static manual preference persisted.
 *  - on + fetch returns `null` → static manual fallback persisted.
 *
 * The "cache hit within the hour" assertion lives at the repository level
 * — exercising it through the use case would just retest the fake's
 * pass-through behaviour. The real cache is in `ElectricityMapsRepository`
 * and is JVM-testable directly (see `ElectricityMapsRepositoryTest`).
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
        gw.settingsReader.setGridIntensityGCo2PerKwh(577.0)
        gw.carbonIntensitySource.nextValue = 412.0

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertEquals(412.0, saved.gridIntensityGCo2PerKwh!!, 0.0)
        assertEquals(1, gw.carbonIntensitySource.callCount)
        assertEquals("CY", gw.carbonIntensitySource.lastZone)
        assertEquals("key", gw.carbonIntensitySource.lastApiKey)
    }

    @Test
    fun co2_enabled_withBlankApiKey_skipsFetch_andFallsBackToManual() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("")
        gw.settingsReader.setGridIntensityGCo2PerKwh(577.0)

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertEquals(577.0, saved.gridIntensityGCo2PerKwh!!, 0.0)
        assertEquals("blank key must skip the network call", 0, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun co2_enabled_fetchReturnsNull_fallsBackToManual() = runTest {
        val gw = FakeSaveChargeEventGateway()
        gw.settingsReader.setCo2Enabled(true)
        gw.settingsReader.setElectricityMapsApiKey("key")
        gw.settingsReader.setGridIntensityGCo2PerKwh(577.0)
        gw.carbonIntensitySource.nextValue = null

        gw.useCase(input())

        val saved = gw.queries.getAllForCarSorted(1L).single()
        assertEquals(577.0, saved.gridIntensityGCo2PerKwh!!, 0.0)
        assertEquals(1, gw.carbonIntensitySource.callCount)
    }

    @Test
    fun twoSaves_inSameSession_callFetchOncePerSave_cacheLivesInRepository() = runTest {
        // The fake source is a thin pass-through; the production cache lives
        // in ElectricityMapsRepository. Verify the use case does call the source
        // both times — caching is the repository's responsibility, not the
        // use case's. (If the use case ever absorbed caching itself, this test
        // would catch that drift.)
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
