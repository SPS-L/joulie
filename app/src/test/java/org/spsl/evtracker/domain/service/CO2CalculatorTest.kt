// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        intensity: Double? = null,
    ) = ChargeEventEntity(
        id = id,
        carId = 1L,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        chargeType = ChargeType.AC,
        gridIntensityGCo2PerKwh = intensity,
        createdAt = 0L,
    )

    // -------------------------------------------------------------------------
    // evCo2Kg
    // -------------------------------------------------------------------------

    @Test
    fun evCo2_emptyEvents_returnsNull() {
        assertNull(calc.evCo2Kg(emptyList()))
    }

    @Test
    fun evCo2_noEventHasIntensity_returnsNull() {
        // Every event has a null per-event intensity (CO₂ was off at save
        // time, or the Electricity Maps fetch returned null). No CO₂ surface.
        val events = listOf(
            event(id = 1, kwhAdded = 10.0, intensity = null),
            event(id = 2, kwhAdded = 20.0, intensity = null),
        )
        assertNull(calc.evCo2Kg(events))
    }

    @Test
    fun evCo2_singleEvent_correct() {
        // 50 kWh × 577 gCO₂/kWh / 1000 = 28.85 kg
        val e = event(kwhAdded = 50.0, intensity = 577.0)
        assertEquals(28.85, calc.evCo2Kg(listOf(e))!!, 1e-9)
    }

    @Test
    fun evCo2_multipleEvents_perEventIntensities() {
        // 10×200 + 20×300 + 30×400 = 2000 + 6000 + 12000 = 20000 g → 20.0 kg
        val events = listOf(
            event(id = 1, kwhAdded = 10.0, intensity = 200.0),
            event(id = 2, kwhAdded = 20.0, intensity = 300.0),
            event(id = 3, kwhAdded = 30.0, intensity = 400.0),
        )
        assertEquals(20.0, calc.evCo2Kg(events)!!, 1e-9)
    }

    @Test
    fun evCo2_someEventsHaveIntensity_sumsOnlyThose() {
        // Mixed period: 2 of 3 events have intensity. Only those contribute.
        // 10×200 + 20×300 = 8000 g → 8.0 kg. Third event excluded entirely.
        val events = listOf(
            event(id = 1, kwhAdded = 10.0, intensity = 200.0),
            event(id = 2, kwhAdded = 20.0, intensity = 300.0),
            event(id = 3, kwhAdded = 30.0, intensity = null),
        )
        assertEquals(8.0, calc.evCo2Kg(events)!!, 1e-9)
    }

    @Test
    fun evCo2_negativeKwh_treatedAsZero_butStillContributes() {
        // Defensive: a negative kwhAdded shouldn't subtract from the
        // running total. A live-intensity row counts as a "contributing"
        // event row even when kWh is zero, so the aggregate is 0.0
        // (computable) rather than null (no data).
        val events = listOf(
            event(id = 1, kwhAdded = 10.0, intensity = 577.0),
            event(id = 2, kwhAdded = -5.0, intensity = 577.0),
        )
        assertEquals(10.0 * 577.0 / 1000.0, calc.evCo2Kg(events)!!, 1e-9)
    }

    @Test
    fun evCo2_zeroIntensity_skipsThatEvent() {
        // An event with intensity == 0 is treated as no-data on that row.
        val e = event(kwhAdded = 50.0, intensity = 0.0)
        assertNull(calc.evCo2Kg(listOf(e)))
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

    // -------------------------------------------------------------------------
    // savedCo2Kg
    // -------------------------------------------------------------------------

    @Test
    fun savedCo2_positiveSavings_typicalCypriotEv() {
        // 1000 km / 50 kWh charged @ 577 g/kWh. ICE counterfactual = 161.7 kg.
        // EV emissions = 50 × 577 / 1000 = 28.85 kg. Saved = 132.85 kg.
        val events = listOf(event(kwhAdded = 50.0, intensity = 577.0))
        val saved = calc.savedCo2Kg(
            events = events,
            distanceKm = 1000.0,
            iceBaselineLPer100km = 7.0,
        )
        assertEquals(132.85, saved!!, 1e-9)
    }

    @Test
    fun savedCo2_canGoNegative_onDirtyGridShortDistance() {
        // 100 km on 50 kWh on an extreme-dirty grid: EV emits 50 kg,
        // ICE counterfactual is only 16.17 kg → saved is negative.
        val events = listOf(event(kwhAdded = 50.0, intensity = 1000.0))
        val saved = calc.savedCo2Kg(
            events = events,
            distanceKm = 100.0,
            iceBaselineLPer100km = 7.0,
        )
        assertEquals(16.17 - 50.0, saved!!, 1e-9)
    }

    @Test
    fun savedCo2_noLiveIntensity_returnsNull() {
        // Period contains events but none carry live intensity → no CO₂.
        val events = listOf(event(kwhAdded = 50.0, intensity = null))
        val saved = calc.savedCo2Kg(
            events = events,
            distanceKm = 100.0,
            iceBaselineLPer100km = 7.0,
        )
        assertNull(saved)
    }

    // -------------------------------------------------------------------------
    // cumulativeTrend
    // -------------------------------------------------------------------------

    @Test
    fun cumulativeTrend_emptyEvents_returnsEmpty() {
        assertTrue(calc.cumulativeTrend(emptyList(), 7.0).isEmpty())
    }

    @Test
    fun cumulativeTrend_noEventHasIntensity_returnsEmpty() {
        // Without any live grid data, the trend is hidden entirely —
        // returning a "0 EV vs ICE" trend would be misleading.
        val events = listOf(
            event(eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, intensity = null),
            event(eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0, intensity = null),
        )
        assertTrue(calc.cumulativeTrend(events, 7.0).isEmpty())
    }

    @Test
    fun cumulativeTrend_firstEventHasZeroIceRunning() {
        // First event has no prior odometer — ICE counterfactual is 0
        // for that point. EV side accrues immediately.
        val events = listOf(
            event(eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, intensity = 577.0),
        )
        val points = calc.cumulativeTrend(events, 7.0)
        assertEquals(1, points.size)
        assertEquals(10.0 * 577.0 / 1000.0, points[0].cumulativeEvCo2Kg, 1e-9)
        assertEquals(0.0, points[0].cumulativeIceCo2Kg, 1e-9)
    }

    @Test
    fun cumulativeTrend_perEventIntensities_advanceRunningTotals() {
        // Event 1: odo=1000, kWh=10 @ 200 g/kWh → EV 2.0 kg, ICE 0
        // Event 2: odo=1100 (+100 km), kWh=10 @ 300 g/kWh → EV 5.0 kg, ICE 16.17 kg
        // Event 3: odo=1300 (+200 km), kWh=20 @ 400 g/kWh → EV 13.0 kg, ICE 48.51 kg
        val events = listOf(
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, intensity = 200.0),
            event(id = 2, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0, intensity = 300.0),
            event(id = 3, eventDate = 3L, odometerKm = 1300.0, kwhAdded = 20.0, intensity = 400.0),
        )
        val points = calc.cumulativeTrend(events, 7.0)
        assertEquals(3, points.size)
        assertEquals(2.0, points[0].cumulativeEvCo2Kg, 1e-9)
        assertEquals(0.0, points[0].cumulativeIceCo2Kg, 1e-9)
        assertEquals(5.0, points[1].cumulativeEvCo2Kg, 1e-9)
        assertEquals(16.17, points[1].cumulativeIceCo2Kg, 1e-9)
        assertEquals(13.0, points[2].cumulativeEvCo2Kg, 1e-9)
        assertEquals(48.51, points[2].cumulativeIceCo2Kg, 1e-9)
    }

    @Test
    fun cumulativeTrend_missingIntensityRows_doNotAdvanceEv_butIceStillTracks() {
        // Event 2 has no live intensity → EV running stays at event-1 value,
        // but the chain still advances so the ICE delta to event 3 uses
        // event 2's odometer.
        val events = listOf(
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, intensity = 200.0),
            event(id = 2, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0, intensity = null),
            event(id = 3, eventDate = 3L, odometerKm = 1300.0, kwhAdded = 20.0, intensity = 400.0),
        )
        val points = calc.cumulativeTrend(events, 7.0)
        assertEquals(3, points.size)
        assertEquals(2.0, points[0].cumulativeEvCo2Kg, 1e-9)
        // Event 2: EV stays at 2.0 (no live intensity); ICE adds 100 km.
        assertEquals(2.0, points[1].cumulativeEvCo2Kg, 1e-9)
        assertEquals(16.17, points[1].cumulativeIceCo2Kg, 1e-9)
        // Event 3: EV adds 8.0 (20 × 400 / 1000); ICE adds 200 km.
        assertEquals(10.0, points[2].cumulativeEvCo2Kg, 1e-9)
        assertEquals(48.51, points[2].cumulativeIceCo2Kg, 1e-9)
    }

    @Test
    fun cumulativeTrend_unsortedInput_sortsByDate() {
        val events = listOf(
            event(id = 3, eventDate = 3L, odometerKm = 1300.0, kwhAdded = 20.0, intensity = 200.0),
            event(id = 1, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, intensity = 200.0),
            event(id = 2, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0, intensity = 200.0),
        )
        val points = calc.cumulativeTrend(events, 7.0)
        assertEquals(1L, points[0].eventTimeMillis)
        assertEquals(2L, points[1].eventTimeMillis)
        assertEquals(3L, points[2].eventTimeMillis)
    }
}
