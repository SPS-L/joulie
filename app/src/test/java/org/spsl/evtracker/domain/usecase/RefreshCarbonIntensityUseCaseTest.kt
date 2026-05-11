// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.domain.repository.FetchOutcome
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
    fun co2Disabled_returnsDisabled_doesNotFetch() = runTest {
        val (useCase, _, source) = build()
        val result = useCase()
        assertEquals(FetchOutcome.Disabled, result)
        assertEquals(0, source.callCount)
    }

    @Test
    fun apiKeyBlank_returnsDisabled_doesNotFetch() = runTest {
        val (useCase, reader, source) = build()
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("")
        val result = useCase()
        assertEquals(FetchOutcome.Disabled, result)
        assertEquals(0, source.callCount)
    }

    @Test
    fun co2OnAndKeySet_andFetchReturnsValue_returnsSuccess() = runTest {
        val (useCase, reader, source) = build(source = FakeCarbonIntensitySource().apply { nextValue = 412.0 })
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("k")
        reader.setElectricityMapsZone("CY")
        val result = useCase()
        assertTrue(result is FetchOutcome.Success)
        assertEquals(412.0, (result as FetchOutcome.Success).intensityGCo2PerKwh, 0.0)
        assertEquals(1, source.callCount)
        assertEquals("CY", source.lastZone)
        assertEquals("k", source.lastApiKey)
    }

    @Test
    fun co2OnAndKeySet_andFetchReturnsAuthError_propagates() = runTest {
        val (useCase, reader, source) = build(
            source = FakeCarbonIntensitySource(initialOutcome = FetchOutcome.AuthError),
        )
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("k")
        val result = useCase()
        assertEquals(FetchOutcome.AuthError, result)
        assertEquals("fetch was called", 1, source.callCount)
    }

    @Test
    fun co2OnAndKeySet_andFetchReturnsNetworkError_propagates() = runTest {
        val (useCase, reader, source) = build(
            source = FakeCarbonIntensitySource(initialOutcome = FetchOutcome.NetworkError),
        )
        reader.setCo2Enabled(true)
        reader.setElectricityMapsApiKey("k")
        val result = useCase()
        assertEquals(FetchOutcome.NetworkError, result)
        assertEquals(1, source.callCount)
    }
}
