package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostParserTest {

    private val parser = CostParser()

    @Test
    fun costZero_returnsNull() {
        val (total, perKwh) = parser.parse(0.0, 10.0, CostMode.TOTAL)
        assertNull(total)
        assertNull(perKwh)
    }

    @Test
    fun costBlank_returnsNull() {
        val (total, perKwh) = parser.parse(null, 10.0, CostMode.TOTAL)
        assertNull(total)
        assertNull(perKwh)
    }

    @Test
    fun costNegative_returnsNull() {
        val (total, perKwh) = parser.parse(-5.0, 10.0, CostMode.TOTAL)
        assertNull(total)
        assertNull(perKwh)
    }

    @Test
    fun costTotal_derivesPerKwh() {
        val (total, perKwh) = parser.parse(3.0, 10.0, CostMode.TOTAL)
        assertEquals(3.0, total!!, 0.0001)
        assertEquals(0.30, perKwh!!, 0.0001)
    }

    @Test
    fun costPerKwh_derivesTotal() {
        val (total, perKwh) = parser.parse(0.30, 10.0, CostMode.PER_KWH)
        assertEquals(3.0, total!!, 0.0001)
        assertEquals(0.30, perKwh!!, 0.0001)
    }

    @Test
    fun kwhZero_returnsNull() {
        val (total, perKwh) = parser.parse(5.0, 0.0, CostMode.TOTAL)
        assertNull(total)
        assertNull(perKwh)
    }

    @Test
    fun bothEntered_totalWins() {
        val (total, perKwh) = parser.parse(4.0, 10.0, CostMode.TOTAL)
        assertEquals(4.0, total!!, 0.0001)
        assertEquals(0.40, perKwh!!, 0.0001)
    }
}
