package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorLocationDistTest {

    private val calc = StatsCalculator()

    private fun ev(location: String?) = ChargeEventEntity(
        id = 0, carId = 1, eventDate = 0L, odometerKm = 0.0, kwhAdded = 1.0,
        chargeType = ChargeType.AC, costTotal = null, costPerKwh = null,
        currency = null, location = location, note = "", createdAt = 0L,
    )

    @Test fun emptyEvents_returnsEmpty() {
        assertTrue(calc.computeLocationDistribution(emptyList()).isEmpty())
    }

    @Test fun nullAndBlankLocations_excluded() {
        assertTrue(
            calc.computeLocationDistribution(
                listOf(
                    ev(null),
                    ev(""),
                    ev("   "),
                    ev("\t"),
                ),
            ).isEmpty(),
        )
    }

    @Test fun singleLocation_oneSlice() {
        val r = calc.computeLocationDistribution(listOf(ev("Home"), ev("Home"), ev("Home")))
        assertEquals(1, r.size)
        assertEquals("Home", r[0].label)
        assertEquals(3, r[0].count)
    }

    @Test fun nineLocations_collapsesToTopEightPlusOther() {
        val events = (1..9).flatMap { i ->
            // i appearances of "L<i>" so the ranking is L9, L8, ..., L1
            List(i) { ev("L$i") }
        }
        val r = calc.computeLocationDistribution(events)
        assertEquals(9, r.size) // top 8 + Other
        assertEquals("L9", r[0].label)
        assertEquals(9, r[0].count)
        assertEquals("L2", r[7].label)
        assertEquals(2, r[7].count)
        assertTrue(r[8].isOther)
        assertEquals(1, r[8].count) // tail = just L1 (count 1)
    }

    @Test fun tieBreaking_byInsertionOrder() {
        // groupingBy preserves first-seen order on ties; the spec leaves tie
        // ordering implementation-defined. This test pins current behaviour
        // so a future refactor that changes it surfaces here.
        val r = calc.computeLocationDistribution(
            listOf(
                ev("First"),
                ev("Second"),
                ev("First"),
                ev("Second"),
            ),
        )
        assertEquals(2, r.size)
        assertEquals("First", r[0].label)
        assertEquals(2, r[0].count)
        assertEquals("Second", r[1].label)
    }

    @Test fun trim_caseSensitive() {
        // "Home" and "home" are different labels (case-sensitive grouping).
        // Leading/trailing whitespace is stripped before grouping.
        val r = calc.computeLocationDistribution(
            listOf(
                ev("Home"),
                ev(" Home "),
                ev("home"),
            ),
        )
        assertEquals(2, r.size)
        assertEquals("Home", r[0].label)
        assertEquals(2, r[0].count) // "Home" + " Home " merged
        assertEquals("home", r[1].label)
        assertEquals(1, r[1].count)
    }
}
