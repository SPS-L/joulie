package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorAcDcSplitTest {

    private val calc = StatsCalculator()

    private fun ev(type: ChargeType, kwh: Double) = ChargeEventEntity(
        id = 0L, carId = 1L, eventDate = 0L, odometerKm = 0.0, kwhAdded = kwh,
        chargeType = type, costTotal = null, costPerKwh = null,
        currency = null, location = null, note = "", createdAt = 0L,
    )

    @Test fun emptyEvents_zeroSplit() {
        val s = calc.computeAcDcSplit(emptyList())
        assertEquals(0, s.acCount)
        assertEquals(0, s.dcCount)
        assertEquals(0.0, s.acKwh, 0.0001)
        assertEquals(0.0, s.dcKwh, 0.0001)
    }

    @Test fun onlyAc_returnsZeroDc() {
        val s = calc.computeAcDcSplit(listOf(ev(ChargeType.AC, 5.0), ev(ChargeType.AC, 7.0)))
        assertEquals(2, s.acCount)
        assertEquals(0, s.dcCount)
        assertEquals(12.0, s.acKwh, 0.0001)
        assertEquals(0.0, s.dcKwh, 0.0001)
    }

    @Test fun mixed_correctTotals() {
        val s = calc.computeAcDcSplit(
            listOf(
                ev(ChargeType.AC, 5.0),
                ev(ChargeType.DC_FAST, 30.0),
                ev(ChargeType.AC, 7.0),
                ev(ChargeType.DC_FAST, 50.0),
            ),
        )
        assertEquals(2, s.acCount)
        assertEquals(2, s.dcCount)
        assertEquals(12.0, s.acKwh, 0.0001)
        assertEquals(80.0, s.dcKwh, 0.0001)
    }

    @Test fun kwhSumsCorrect_evenWithFractional() {
        val s = calc.computeAcDcSplit(
            listOf(
                ev(ChargeType.AC, 0.5),
                ev(ChargeType.AC, 0.25),
                ev(ChargeType.DC_FAST, 0.125),
            ),
        )
        assertEquals(0.75, s.acKwh, 0.0001)
        assertEquals(0.125, s.dcKwh, 0.0001)
    }
}
