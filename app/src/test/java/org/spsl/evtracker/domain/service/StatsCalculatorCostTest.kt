package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorCostTest {

    private val calc = StatsCalculator()

    private fun event(
        eventDate: Long,
        odometerKm: Double,
        kwhAdded: Double,
        costTotal: Double? = null,
        currency: String? = null,
    ) = ChargeEventEntity(
        carId = 1,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        costTotal = costTotal,
        currency = currency,
        createdAt = 0L,
    )

    @Test
    fun allCostNull_costStatsNull() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0),
                event(3, 100.0, 10.0),
            ),
            "label",
        )
        assertNull(s.costPerKm)
        assertNull(s.costPer100Km)
        assertEquals(false, s.mixedCurrency)
    }

    @Test
    fun mixedCost_sumNonNullOnly() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR"),
                event(3, 100.0, 10.0),
                event(4, 200.0, 10.0, costTotal = 10.0, currency = "EUR"),
                event(5, 250.0, 10.0),
            ),
            "label",
        )
        assertEquals(15.0 / 250.0, s.costPerKm!!, 0.0001)
    }

    @Test
    fun singleCostEvent_correct() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR"),
            ),
            "label",
        )
        assertEquals(5.0 / 50.0, s.costPerKm!!, 0.0001)
        assertEquals(s.costPerKm!! * 100.0, s.costPer100Km!!, 0.0001)
    }

    @Test
    fun multipleCurrencies_costStatsNull() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR"),
                event(3, 100.0, 10.0, costTotal = 6.0, currency = "EUR"),
                event(4, 150.0, 10.0, costTotal = 7.0, currency = "USD"),
                event(5, 200.0, 10.0, costTotal = 8.0, currency = "USD"),
            ),
            "label",
        )
        assertNull(s.costPerKm)
        assertNull(s.costPer100Km)
        assertTrue(s.mixedCurrency)
    }

    @Test
    fun singleCurrencyAcrossPeriod_costStatsComputed() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR"),
                event(3, 100.0, 10.0, costTotal = 6.0, currency = "EUR"),
                event(4, 200.0, 10.0, costTotal = 12.0, currency = "EUR"),
            ),
            "label",
        )
        assertEquals(23.0 / 200.0, s.costPerKm!!, 0.0001)
        assertEquals(23.0, s.totalCost!!, 0.0001)
        assertEquals("EUR", s.currency)
        assertEquals(false, s.mixedCurrency)
    }

    @Test
    fun multipleCurrencies_totalCostAndCurrencyAreNull() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR"),
                event(3, 100.0, 10.0, costTotal = 7.0, currency = "USD"),
            ),
            "label",
        )
        assertNull(s.totalCost)
        assertNull(s.currency)
        assertTrue(s.mixedCurrency)
    }

    @Test
    fun allCostNull_totalCostAndCurrencyAreNull() {
        val s = calc.computeStats(
            listOf(
                event(1, 0.0, 0.0),
                event(2, 50.0, 10.0),
                event(3, 100.0, 10.0),
            ),
            "label",
        )
        assertNull(s.totalCost)
        assertNull(s.currency)
    }
}
