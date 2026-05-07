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
        carId = 1L,
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

    // Cost-accumulation invariant: the first event's cost must contribute to
    // the period total. `computeStats.totalCost` sums every costed event so it
    // agrees with `computeMonthlyBuckets`.

    @Test
    fun firstAndSecondEventBothCosted_totalCostSumsBoth() {
        val s = calc.computeStats(
            listOf(
                event(1000L, 100.0, 30.0, costTotal = 5.0, currency = "EUR"),
                event(2000L, 250.0, 25.0, costTotal = 10.0, currency = "EUR"),
            ),
            "label",
        )
        assertEquals(15.0, s.totalCost!!, 1e-6)
        assertEquals("EUR", s.currency)
        // costPerKm uses delta distance (250 - 100 = 150) → 15/150
        assertEquals(15.0 / 150.0, s.costPerKm!!, 1e-6)
    }

    @Test
    fun firstAndThirdEventCosted_middleNotCosted_totalIncludesBoth() {
        val s = calc.computeStats(
            listOf(
                event(1000L, 100.0, 30.0, costTotal = 4.0, currency = "EUR"),
                event(2000L, 200.0, 25.0, costTotal = null, currency = null),
                event(3000L, 300.0, 30.0, costTotal = 6.0, currency = "EUR"),
            ),
            "label",
        )
        assertEquals(10.0, s.totalCost!!, 1e-6)
        assertEquals("EUR", s.currency)
    }

    @Test
    fun singleCostedEvent_reportsTotalCost_costPerKmStillNull() {
        val s = calc.computeStats(
            listOf(
                event(1000L, 100.0, 30.0, costTotal = 5.0, currency = "EUR"),
            ),
            "label",
        )
        assertEquals(5.0, s.totalCost!!, 1e-6)
        assertEquals("EUR", s.currency)
        // Delta-distance is undefined for a single event — efficiency-rate stats stay null.
        assertNull(s.costPerKm)
        assertNull(s.costPer100Km)
    }

    @Test
    fun mixedCurrencyWithFirstEventCosted_totalCostStillNull() {
        val s = calc.computeStats(
            listOf(
                event(1000L, 100.0, 30.0, costTotal = 5.0, currency = "EUR"),
                event(2000L, 250.0, 25.0, costTotal = 10.0, currency = "USD"),
            ),
            "label",
        )
        assertNull(s.totalCost)
        assertNull(s.currency)
        assertNull(s.costPerKm)
        assertTrue(s.mixedCurrency)
    }
}
