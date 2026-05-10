// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeSettingsReader

/**
 * CO₂ gate for [ObserveDashboardStatsUseCase] (TASK-80).
 *
 * Asserts:
 *  - `co2Enabled=false` → both EV and ICE CO₂ stats are null even when
 *    grid intensity / baseline are populated.
 *  - `co2Enabled=true` + per-event intensity present → that value is used
 *    in preference to the static manual preference.
 *  - `co2Enabled=true` + no per-event intensity → the manual preference
 *    is used as the fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStatsCo2GateTest {

    private fun build(
        events: List<ChargeEventEntity>,
        co2Enabled: Boolean,
        gridIntensity: Double = 577.0,
    ): ObserveDashboardStatsUseCase {
        val car = CarEntity(id = 1L, name = "T", createdAt = 0L)
        val queries = FakeChargeEventQueries()
        queries.seed(events)
        val settings = FakeSettingsReader(
            activeCarIdInit = 1L,
            co2EnabledInit = co2Enabled,
            gridIntensityGCo2PerKwhInit = gridIntensity,
            iceBaselineLPer100kmInit = 7.0,
        )
        return ObserveDashboardStatsUseCase(
            carReader = FakeCarReader(listOf(car)),
            chargeEventQueries = queries,
            settingsReader = settings,
            statsCalculator = StatsCalculator(),
            capacityEstimator = org.spsl.evtracker.domain.service.CapacityEstimator(),
            co2Calculator = org.spsl.evtracker.domain.service.CO2Calculator(),
            dateRangeResolver = DateRangeResolver(),
            now = FakeNowProvider(System.currentTimeMillis()),
        )
    }

    private fun event(
        eventDate: Long,
        kwh: Double = 10.0,
        odo: Double = 100.0,
        intensity: Double? = null,
    ) = ChargeEventEntity(
        id = 0L,
        carId = 1L,
        eventDate = eventDate,
        odometerKm = odo,
        kwhAdded = kwh,
        chargeType = ChargeType.AC,
        gridIntensityGCo2PerKwh = intensity,
        createdAt = 0L,
    )

    @Test
    fun co2Disabled_blocksBothStats() = runTest {
        val now = System.currentTimeMillis()
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = 400.0),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = 400.0),
            ),
            co2Enabled = false,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertNull("CO₂ off must zero out evCo2Kg", state.stats?.evCo2Kg)
        assertNull("CO₂ off must zero out iceCo2Kg", state.stats?.iceCo2Kg)
    }

    @Test
    fun co2Enabled_perEventIntensity_preferredOverManual() = runTest {
        val now = System.currentTimeMillis()
        // Two events. Per-event intensity 200 g/kWh on one, 300 on the other.
        // Manual preference is 999 g/kWh — should NOT be used because every
        // row already carries its own intensity.
        // Expected EV kg: (10 * 200 + 10 * 300) / 1000 = 5.0 kg
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = 200.0),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = 300.0),
            ),
            co2Enabled = true,
            gridIntensity = 999.0,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(5.0, state.stats!!.evCo2Kg!!, 1e-9)
    }

    @Test
    fun co2Enabled_noPerEventIntensity_fallsBackToManual() = runTest {
        val now = System.currentTimeMillis()
        // 20 kWh total at manual 500 g/kWh → 10 kg
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = null),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = null),
            ),
            co2Enabled = true,
            gridIntensity = 500.0,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(10.0, state.stats!!.evCo2Kg!!, 1e-9)
    }

    @Test
    fun co2Enabled_mixedPerEventAndNull_blendsPerEventWithManualFallback() = runTest {
        val now = System.currentTimeMillis()
        // First event: per-event 200 g/kWh × 10 kWh = 2 kg
        // Second event: null intensity → falls back to manual 500 g/kWh × 10 kWh = 5 kg
        // Total: 7 kg
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = 200.0),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = null),
            ),
            co2Enabled = true,
            gridIntensity = 500.0,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(7.0, state.stats!!.evCo2Kg!!, 1e-9)
    }
}
