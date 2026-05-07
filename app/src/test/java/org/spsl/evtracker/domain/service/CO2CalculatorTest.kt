// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class CO2CalculatorTest {

    private val calc = CO2Calculator()

    private fun event(
        id: Long = 1L,
        eventDate: Long = 1_000L,
        odometerKm: Double = 0.0,
        kwhAdded: Double = 0.0,
    ) = ChargeEventEntity(
        id = id,
        carId = 1L,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        chargeType = ChargeType.AC,
        createdAt = 0L,
    )

    // -------------------------------------------------------------------------
    // evCo2Kg
    // -------------------------------------------------------------------------

    @Test
    fun evCo2_emptyEvents_returnsZero() {
        assertEquals(0.0, calc.evCo2Kg(emptyList(), 577.0), 1e-9)
    }

    @Test
    fun evCo2_singleEvent_correct() {
        // 50 kWh × 577 gCO₂/kWh / 1000 = 28.85 kg
        val e = event(kwhAdded = 50.0)
        assertEquals(28.85, calc.evCo2Kg(listOf(e), 577.0), 1e-9)
    }

    @Test
    fun evCo2_multipleEvents_sumsTotalKwh() {
        // (10 + 20 + 30) kWh × 400 gCO₂/kWh / 1000 = 24.0 kg
        val events = listOf(
            event(id = 1, kwhAdded = 10.0),
            event(id = 2, kwhAdded = 20.0),
            event(id = 3, kwhAdded = 30.0),
        )
        assertEquals(24.0, calc.evCo2Kg(events, 400.0), 1e-9)
    }

    @Test
    fun evCo2_negativeKwh_treatedAsZero() {
        // Defensive: a negative kwhAdded shouldn't subtract from the
        // running total. Real-world this would be a data-entry error.
        val events = listOf(
            event(id = 1, kwhAdded = 10.0),
            event(id = 2, kwhAdded = -5.0),
        )
        assertEquals(10.0 * 577.0 / 1000.0, calc.evCo2Kg(events, 577.0), 1e-9)
    }

    @Test
    fun evCo2_zeroIntensity_returnsZero() {
        // Hidden-card semantic: when the grid pref is unset / 0, the
        // EV-side number is 0 (not "free electricity").
        val e = event(kwhAdded = 50.0)
        assertEquals(0.0, calc.evCo2Kg(listOf(e), 0.0), 1e-9)
    }

    // -------------------------------------------------------------------------
    // iceCounterfactualCo2Kg
    // -------------------------------------------------------------------------

    @Test
    fun iceCounterfactual_zeroDistance_returnsZero() {
        assertEquals(0.0, calc.iceCounterfactualCo2Kg(0.0, 7.0), 1e-9)
        assertEquals(0.0, calc.iceCounterfactualCo2Kg(-50.0, 7.0), 1e-9)
    }

    @Test
    fun iceCounterfactual_zeroBaseline_returnsZero() {
        // Hidden-card semantic: unset ICE baseline → 0.
        assertEquals(0.0, calc.iceCounterfactualCo2Kg(1000.0, 0.0), 1e-9)
    }

    @Test
    fun iceCounterfactual_typicalCase() {
        // 1000 km / 100 × 7.0 L/100km × 2.31 kg/L = 161.7 kg
        assertEquals(161.7, calc.iceCounterfactualCo2Kg(1000.0, 7.0), 1e-9)
    }

    @Test
    fun iceCounterfactual_smallDistance() {
        // 50 km / 100 × 5.5 L/100km × 2.31 kg/L = 6.3525 kg
        assertEquals(6.3525, calc.iceCounterfactualCo2Kg(50.0, 5.5), 1e-9)
    }

    // -------------------------------------------------------------------------
    // savedCo2Kg
    // -------------------------------------------------------------------------

    @Test
    fun savedCo2_positiveSavings_typicalCypriotEv() {
        // 1000 km / 50 kWh charged. ICE counterfactual = 161.7 kg.
        // EV emissions = 50 × 577 / 1000 = 28.85 kg. Saved = 132.85 kg.
        val events = listOf(event(kwhAdded = 50.0))
        val saved = calc.savedCo2Kg(
            events = events,
            distanceKm = 1000.0,
            iceBaselineLPer100km = 7.0,
            gridIntensityGCo2PerKwh = 577.0,
        )
        assertEquals(132.85, saved, 1e-9)
    }

    @Test
    fun savedCo2_canGoNegative_onDirtyGridShortDistance() {
        // 100 km on 50 kWh on an extreme-dirty grid: EV emits 50 kg,
        // ICE counterfactual is only 16.17 kg → saved is negative.
        // The card surfaces this honestly (both numbers shown
        // side-by-side rather than only the savings).
        val events = listOf(event(kwhAdded = 50.0))
        val saved = calc.savedCo2Kg(
            events = events,
            distanceKm = 100.0,
            iceBaselineLPer100km = 7.0,
            gridIntensityGCo2PerKwh = 1000.0,
        )
        assertEquals(16.17 - 50.0, saved, 1e-9)
    }

    // -------------------------------------------------------------------------
    // cumulativeTrend
    // -------------------------------------------------------------------------

    @Test
    fun cumulativeTrend_emptyEvents_returnsEmpty() {
        assertEquals(emptyList<CO2Calculator.CumulativePoint>(), calc.cumulativeTrend(emptyList(), 7.0, 577.0))
    }

    @Test
    fun cumulativeTrend_firstEventHasZeroIceRunning() {
        // First event has no prior odometer — ICE counterfactual is 0
        // for that point. EV side accrues immediately.
        val events = listOf(event(eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0))
        val points = calc.cumulativeTrend(events, 7.0, 577.0)
        assertEquals(1, points.size)
        assertEquals(10.0 * 577.0 / 1000.0, points[0].cumulativeEvCo2Kg, 1e-9)
        assertEquals(0.0, points[0].cumulativeIceCo2Kg, 1e-9)
    }

    @Test
    fun cumulativeTrend_threeEvents_runningTotalsAdvance() {
        // Event 1: odo=1000, kWh=10 → EV 5.77 kg, ICE 0
        // Event 2: odo=1100 (+100 km), kWh=10 → EV 11.54 kg, ICE 16.17 kg
        // Event 3: odo=1300 (+200 km), kWh=20 → EV 23.08 kg, ICE 48.51 kg
        val events = listOf(
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0),
            event(id = 2, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0),
            event(id = 3, eventDate = 3L, odometerKm = 1300.0, kwhAdded = 20.0),
        )
        val points = calc.cumulativeTrend(events, 7.0, 577.0)
        assertEquals(3, points.size)
        assertEquals(5.77, points[0].cumulativeEvCo2Kg, 1e-9)
        assertEquals(0.0, points[0].cumulativeIceCo2Kg, 1e-9)
        assertEquals(11.54, points[1].cumulativeEvCo2Kg, 1e-9)
        assertEquals(16.17, points[1].cumulativeIceCo2Kg, 1e-9)
        assertEquals(23.08, points[2].cumulativeEvCo2Kg, 1e-9)
        // 16.17 + (200/100 × 7.0 × 2.31) = 16.17 + 32.34 = 48.51
        assertEquals(48.51, points[2].cumulativeIceCo2Kg, 1e-9)
    }

    @Test
    fun cumulativeTrend_negativeOdometerDelta_skipsIceContribution_butChainAdvances() {
        // Mirrors StatsCalculator's pairwise convention from DESIGN.md §7:
        // a rolled-back odometer reading contributes 0 to ICE running but
        // the chain still advances so the NEXT valid delta is computed
        // against the rolled-back value.
        val events = listOf(
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0),
            // Event 2 rolls the odometer back from 1000 → 900 (-100).
            event(id = 2, eventDate = 2L, odometerKm = 900.0, kwhAdded = 10.0),
            // Event 3 reaches 1000 again, +100 vs the rolled-back event 2.
            event(id = 3, eventDate = 3L, odometerKm = 1000.0, kwhAdded = 10.0),
        )
        val points = calc.cumulativeTrend(events, 7.0, 577.0)
        assertEquals(0.0, points[0].cumulativeIceCo2Kg, 1e-9) // first event
        assertEquals(0.0, points[1].cumulativeIceCo2Kg, 1e-9) // dist = -100, skipped
        assertEquals(16.17, points[2].cumulativeIceCo2Kg, 1e-9) // dist = 100 from rolled-back
    }

    @Test
    fun cumulativeTrend_unsortedInput_sortsByDate() {
        // Caller may pass events in any order; cumulativeTrend sorts
        // internally so the running total is monotonic in event date.
        val events = listOf(
            event(id = 3, eventDate = 3L, odometerKm = 1300.0, kwhAdded = 20.0),
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0),
            event(id = 2, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0),
        )
        val points = calc.cumulativeTrend(events, 7.0, 577.0)
        assertEquals(1L, points[0].eventTimeMillis)
        assertEquals(2L, points[1].eventTimeMillis)
        assertEquals(3L, points[2].eventTimeMillis)
    }
}
