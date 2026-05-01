package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorTrendTest {

    private val calc = StatsCalculator()

    private fun ev(
        date: Long,
        odometerKm: Double,
        kwhAdded: Double = 10.0,
        chargeType: ChargeType = ChargeType.AC,
    ) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = date, odometerKm = odometerKm,
        kwhAdded = kwhAdded, chargeType = chargeType, costTotal = null,
        costPerKwh = null, currency = null, location = null, note = "",
        createdAt = 0L,
    )

    @Test fun emptyEvents_returnsEmptySeries() {
        val s = calc.computeEfficiencyTrend(emptyList())
        assertTrue(s.acPoints.isEmpty())
        assertTrue(s.dcPoints.isEmpty())
    }

    @Test fun singleAcEvent_emptySeries() {
        val s = calc.computeEfficiencyTrend(listOf(ev(100L, 0.0, chargeType = ChargeType.AC)))
        assertTrue(s.acPoints.isEmpty())
        assertTrue(s.dcPoints.isEmpty())
    }

    @Test fun acAndDcEvents_partitionedCorrectly() {
        val s = calc.computeEfficiencyTrend(
            listOf(
                ev(100L, 0.0, 10.0, ChargeType.AC),
                // 50 km / 10 kWh = 5.0
                ev(200L, 50.0, 10.0, ChargeType.AC),
                ev(150L, 0.0, 10.0, ChargeType.DC_FAST),
                // 80 km / 10 kWh = 8.0
                ev(300L, 80.0, 10.0, ChargeType.DC_FAST),
            ),
        )
        assertEquals(1, s.acPoints.size)
        assertEquals(5.0, s.acPoints[0].kmPerKwh, 0.0001)
        assertEquals(200L, s.acPoints[0].eventTimeMillis)
        assertEquals(1, s.dcPoints.size)
        assertEquals(8.0, s.dcPoints[0].kmPerKwh, 0.0001)
        assertEquals(300L, s.dcPoints[0].eventTimeMillis)
    }

    @Test fun negativeOdometerDelta_skipped() {
        val s = calc.computeEfficiencyTrend(
            listOf(
                ev(100L, 100.0, 10.0, ChargeType.AC),
                // odo went backwards → skip
                ev(200L, 50.0, 10.0, ChargeType.AC),
            ),
        )
        assertTrue(s.acPoints.isEmpty())
    }

    @Test fun zeroKwh_skipped() {
        val s = calc.computeEfficiencyTrend(
            listOf(
                ev(100L, 0.0, 10.0, ChargeType.AC),
                // kwh=0 → skip
                ev(200L, 50.0, 0.0, ChargeType.AC),
            ),
        )
        assertTrue(s.acPoints.isEmpty())
    }

    @Test fun mixedTypeOrder_eachSeriesSortedIndependently() {
        // Inserted out of date order across types; each series must sort by
        // its own dates before computing deltas.
        val s = calc.computeEfficiencyTrend(
            listOf(
                ev(300L, 60.0, 10.0, ChargeType.AC),
                ev(100L, 0.0, 10.0, ChargeType.AC),
                ev(200L, 20.0, 10.0, ChargeType.AC),
                ev(150L, 0.0, 10.0, ChargeType.DC_FAST),
                ev(250L, 30.0, 10.0, ChargeType.DC_FAST),
            ),
        )
        assertEquals(2, s.acPoints.size)
        assertEquals(2.0, s.acPoints[0].kmPerKwh, 0.0001) // 100→200: 20 km
        assertEquals(4.0, s.acPoints[1].kmPerKwh, 0.0001) // 200→300: 40 km
        assertEquals(1, s.dcPoints.size)
        assertEquals(3.0, s.dcPoints[0].kmPerKwh, 0.0001) // 150→250: 30 km
    }
}
