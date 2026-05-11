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
 * CO₂ gate for [ObserveDashboardStatsUseCase] (TASK-80 / TASK-81).
 *
 * Asserts the no-fallback contract:
 *  - `co2Enabled=false` → both EV and ICE CO₂ stats are null.
 *  - `co2Enabled=true` + at least one event with live intensity → EV
 *    summed across contributing events; ICE shown over the period's
 *    full distance.
 *  - `co2Enabled=true` + zero events with live intensity → both null
 *    (the period had no live grid data, so the comparison is hidden).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStatsCo2GateTest {

    private fun build(
        events: List<ChargeEventEntity>,
        co2Enabled: Boolean,
    ): ObserveDashboardStatsUseCase {
        val car = CarEntity(id = 1L, name = "T", createdAt = 0L)
        val queries = FakeChargeEventQueries()
        queries.seed(events)
        val settings = FakeSettingsReader(
            activeCarIdInit = 1L,
            co2EnabledInit = co2Enabled,
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
        assertNull("CO₂ off must null out evCo2Kg", state.stats?.evCo2Kg)
        assertNull("CO₂ off must null out iceCo2Kg", state.stats?.iceCo2Kg)
    }

    @Test
    fun co2Enabled_perEventIntensitiesSumOnContributingEvents() = runTest {
        val now = System.currentTimeMillis()
        // Two events, both with live intensity captured at save time.
        // 10 × 200 + 10 × 300 = 5000 g → 5.0 kg
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = 200.0),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = 300.0),
            ),
            co2Enabled = true,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(5.0, state.stats!!.evCo2Kg!!, 1e-9)
        // ICE side present because EV side is computable. Distance is the
        // delta-odometer (200 - 100 = 100 km) × 7 / 100 × 2.31 = 16.17 kg.
        assertEquals(16.17, state.stats!!.iceCo2Kg!!, 1e-9)
    }

    @Test
    fun co2Enabled_noPerEventIntensity_bothNull_noFallback() = runTest {
        val now = System.currentTimeMillis()
        // No event carries live intensity → EV is null → ICE is null too.
        // The dashboard hides the whole card; no asymmetric ICE-only view.
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = null),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = null),
            ),
            co2Enabled = true,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertNull("no per-event intensity must yield null EV", state.stats?.evCo2Kg)
        assertNull("no per-event intensity must yield null ICE too", state.stats?.iceCo2Kg)
    }

    @Test
    fun co2Enabled_partialIntensities_contributingEventsSum_iceShown() = runTest {
        val now = System.currentTimeMillis()
        // One of two events has live intensity. EV = 10 × 200 / 1000 = 2.0 kg.
        // ICE counterfactual is over the full period distance regardless.
        val useCase = build(
            events = listOf(
                event(now - 1000, kwh = 10.0, odo = 100.0, intensity = 200.0),
                event(now - 500, kwh = 10.0, odo = 200.0, intensity = null),
            ),
            co2Enabled = true,
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(2.0, state.stats!!.evCo2Kg!!, 1e-9)
        assertEquals(16.17, state.stats!!.iceCo2Kg!!, 1e-9)
    }
}
