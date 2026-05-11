// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.testing.FakeCarbonIntensitySource
import org.spsl.evtracker.testing.FakeSettingsReader

class RefreshCarbonIntensityUseCaseTest {

    private fun build(
        reader: FakeSettingsReader = FakeSettingsReader(),
        source: FakeCarbonIntensitySource = FakeCarbonIntensitySource(),
    ): Triple<RefreshCarbonIntensityUseCase, FakeSettingsReader, FakeCarbonIntensitySource> {
        val useCase = RefreshCarbonIntensityUseCase(reader, source)
        return Triple(useCase, reader, source)
    }

    @Test
    fun co2Disabled_returnsFalse_doesNotFetch() = runTest {
        val (useCase, _, source) = build()
        // Default FakeSettingsReader: co2EnabledInit = false.
        val result = useCase()
        assertFalse(result)
        assertEquals(0, source.callCount)
    }

    @Test
    fun apiKeyBlank_returnsFalse_doesNotFetch() = runTest {
        val (useCase, reader, source) = build()
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("")
        val result = useCase()
        assertFalse(result)
        assertEquals(0, source.callCount)
    }

    @Test
    fun co2OnAndKeySet_andFetchReturnsValue_returnsTrue() = runTest {
        val (useCase, reader, source) = build(source = FakeCarbonIntensitySource(nextValue = 412.0))
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("k")
        reader.setElectricityMapsZone("CY")
        val result = useCase()
        assertTrue(result)
        assertEquals(1, source.callCount)
        assertEquals("CY", source.lastZone)
        assertEquals("k", source.lastApiKey)
    }

    @Test
    fun co2OnAndKeySet_andFetchReturnsNull_returnsFalse() = runTest {
        val (useCase, reader, source) = build(source = FakeCarbonIntensitySource(nextValue = null))
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("k")
        val result = useCase()
        assertFalse(result)
        assertEquals("fetch was called", 1, source.callCount)
    }
}
