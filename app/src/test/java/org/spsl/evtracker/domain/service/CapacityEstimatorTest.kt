package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class CapacityEstimatorTest {

    private val estimator = CapacityEstimator()

    private fun event(
        id: Long,
        eventDate: Long,
        kwhAdded: Double,
        socBefore: Double? = null,
        socAfter: Double? = null,
        kwhSource: ChargeKwhSource = ChargeKwhSource.MEASURED,
    ) = ChargeEventEntity(
        id = id,
        carId = 1L,
        eventDate = eventDate,
        odometerKm = 0.0,
        kwhAdded = kwhAdded,
        chargeType = ChargeType.AC,
        socBefore = socBefore,
        socAfter = socAfter,
        kwhSource = kwhSource,
        createdAt = 0L,
    )

    // -- Exact path -----------------------------------------------------------

    @Test fun exactCapacity_fromSocFields() {
        val events = listOf(event(1L, 100L, kwhAdded = 30.0, socBefore = 0.20, socAfter = 0.80))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertEquals(1, points.size)
        // 30 / (0.80 - 0.20) = 50 kWh
        assertEquals(50.0, points.single().effectiveCapacityKwh, 0.001)
        assertTrue(points.single().isExact)
    }

    @Test fun exactCapacity_ignoresHeuristicOnSameEvent() {
        // SoC fields present and valid → exact, even though kWh ≥ 0.8 × nominal would also qualify.
        val events = listOf(event(1L, 100L, kwhAdded = 60.0, socBefore = 0.10, socAfter = 0.90))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.single().isExact)
        // 60 / 0.80 = 75 kWh — would be 60 if heuristic took priority.
        assertEquals(75.0, points.single().effectiveCapacityKwh, 0.001)
    }

    @Test fun exactCapacity_withNullNominal() {
        // Exact path doesn't need nominal capacity at all.
        val events = listOf(event(1L, 100L, kwhAdded = 20.0, socBefore = 0.0, socAfter = 0.5))
        val points = estimator.estimate(events, nominalBatteryKwh = null)
        assertEquals(40.0, points.single().effectiveCapacityKwh, 0.001)
    }

    @Test fun exactCapacity_socAfterEqualBefore_skipped() {
        // No division-by-zero — `after > before` strictly required.
        val events = listOf(event(1L, 100L, kwhAdded = 10.0, socBefore = 0.5, socAfter = 0.5))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    @Test fun exactCapacity_socAfterBelowBefore_skipped() {
        // Reversed SoC values are invalid input — skipped, not flipped.
        val events = listOf(event(1L, 100L, kwhAdded = 10.0, socBefore = 0.8, socAfter = 0.2))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    // -- Heuristic path -------------------------------------------------------

    @Test fun heuristic_kwhAtLeast80PctOfNominal_qualifies() {
        // 50 / 60 = 0.833... ≥ 0.80 — qualifies.
        val events = listOf(event(1L, 100L, kwhAdded = 50.0))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertEquals(1, points.size)
        assertEquals(50.0, points.single().effectiveCapacityKwh, 0.001)
        assertFalse(points.single().isExact)
    }

    @Test fun heuristic_belowEightyPct_skipped() {
        // 47 / 60 = 0.783... < 0.80 — does not qualify.
        val events = listOf(event(1L, 100L, kwhAdded = 47.0))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    @Test fun heuristic_exactlyEightyPct_qualifies() {
        // 48 / 60 = 0.80 — boundary value qualifies (≥ comparison).
        val events = listOf(event(1L, 100L, kwhAdded = 48.0))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertEquals(1, points.size)
    }

    @Test fun heuristic_withNullNominal_skipped() {
        val events = listOf(event(1L, 100L, kwhAdded = 999.0))
        val points = estimator.estimate(events, nominalBatteryKwh = null)
        assertTrue(points.isEmpty())
    }

    // -- Mixed + edge cases ---------------------------------------------------

    @Test fun mixed_exactAndHeuristic_bothEmittedSortedByDate() {
        // Event 1: exact (latest date), Event 2: heuristic (mid), Event 3: skipped (kWh too low).
        val events = listOf(
            event(1L, 200L, kwhAdded = 30.0, socBefore = 0.20, socAfter = 0.80),
            event(2L, 100L, kwhAdded = 50.0),
            event(3L, 50L, kwhAdded = 5.0),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertEquals(2, points.size)
        // Sorted ascending by eventDate.
        assertEquals(100L, points[0].eventDate)
        assertEquals(200L, points[1].eventDate)
        assertFalse(points[0].isExact)
        assertTrue(points[1].isExact)
    }

    @Test fun zeroOrNegativeKwhAdded_skipped() {
        val events = listOf(
            event(1L, 100L, kwhAdded = 0.0, socBefore = 0.0, socAfter = 0.5),
            event(2L, 200L, kwhAdded = -5.0),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    @Test fun emptyEvents_returnsEmpty() {
        assertTrue(estimator.estimate(emptyList(), nominalBatteryKwh = 60.0).isEmpty())
    }

    // -- batteryHealthPercent -------------------------------------------------

    @Test fun batteryHealth_latestPointVsNominal() {
        // Event 1 (older): 56 / 0.80 = 70 kWh exact.
        // Event 2 (newer): 50 / 0.80 = 62.5 kWh exact.
        val events = listOf(
            event(1L, 100L, kwhAdded = 56.0, socBefore = 0.10, socAfter = 0.90),
            event(2L, 200L, kwhAdded = 50.0, socBefore = 0.10, socAfter = 0.90),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        val pct = estimator.batteryHealthPercent(points, nominalBatteryKwh = 60.0)
        // Latest point wins → 62.5 / 60 × 100 ≈ 104.166...
        assertEquals(104.166, pct!!, 0.01)
    }

    @Test fun batteryHealth_nullWhenNoPoints() {
        assertNull(estimator.batteryHealthPercent(emptyList(), nominalBatteryKwh = 60.0))
    }

    @Test fun batteryHealth_nullWhenNoNominal() {
        val events = listOf(event(1L, 100L, kwhAdded = 30.0, socBefore = 0.0, socAfter = 0.5))
        val points = estimator.estimate(events, nominalBatteryKwh = null)
        assertNull(estimator.batteryHealthPercent(points, nominalBatteryKwh = null))
    }

    // -- — derived events excluded from both paths --------------------

    @Test fun derivedEvent_excludedFromExactPath() {
        // SoC fields present and valid AND kWh is non-zero — would normally
        // qualify on the exact path, but kwhSource = DERIVED_FROM_SOC means
        // kwhAdded was computed from `Δsoc × nominal`, so the capacity result
        // would be exactly nominalBatteryKwh (tautological). Skip.
        val events = listOf(
            event(
                1L,
                100L,
                kwhAdded = 36.0,
                socBefore = 0.20,
                socAfter = 0.80,
                kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
            ),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    @Test fun derivedEvent_excludedFromHeuristicPath() {
        // No SoC fields, kWh ≥ 80% of nominal — would qualify on the heuristic
        // path, but kwhSource = DERIVED_FROM_SOC trumps that. The heuristic
        // is just as fooled by a derived value as the exact path is.
        val events = listOf(
            event(1L, 100L, kwhAdded = 50.0, kwhSource = ChargeKwhSource.DERIVED_FROM_SOC),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertTrue(points.isEmpty())
    }

    @Test fun mixedDataset_excludesDerivedRowsOnly() {
        // 4 events: 2 measured (qualify), 2 derived (excluded). The two
        // measured events should produce points; the derived ones drop out.
        val events = listOf(
            event(
                1L,
                100L,
                kwhAdded = 30.0,
                socBefore = 0.20,
                socAfter = 0.80,
                kwhSource = ChargeKwhSource.MEASURED,
            ),
            event(
                2L,
                200L,
                kwhAdded = 36.0,
                socBefore = 0.20,
                socAfter = 0.80,
                kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
            ),
            event(3L, 300L, kwhAdded = 50.0, kwhSource = ChargeKwhSource.MEASURED),
            event(4L, 400L, kwhAdded = 50.0, kwhSource = ChargeKwhSource.DERIVED_FROM_SOC),
        )
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        assertEquals(2, points.size)
        assertEquals(100L, points[0].eventDate)
        assertEquals(300L, points[1].eventDate)
    }

    @Test fun countDerivedEvents_returnsDerivedRowCount() {
        val events = listOf(
            event(1L, 100L, kwhAdded = 30.0, kwhSource = ChargeKwhSource.MEASURED),
            event(2L, 200L, kwhAdded = 36.0, kwhSource = ChargeKwhSource.DERIVED_FROM_SOC),
            event(3L, 300L, kwhAdded = 50.0, kwhSource = ChargeKwhSource.DERIVED_FROM_SOC),
        )
        assertEquals(2, estimator.countDerivedEvents(events))
    }

    @Test fun countDerivedEvents_emptyListReturnsZero() {
        assertEquals(0, estimator.countDerivedEvents(emptyList()))
    }

    @Test fun batteryHealth_doesNotClampAbove100() {
        // Heuristic approximation can exceed nominal; the API does NOT clamp,
        // because clamping would hide diagnostic information.
        val events = listOf(event(1L, 100L, kwhAdded = 65.0, socBefore = 0.05, socAfter = 0.95))
        val points = estimator.estimate(events, nominalBatteryKwh = 60.0)
        // 65 / 0.90 ≈ 72.22 kWh; vs nominal 60 → 120.37 %
        val pct = estimator.batteryHealthPercent(points, nominalBatteryKwh = 60.0)
        assertTrue("expected > 100, got $pct", pct!! > 100.0)
    }
}
