package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorTest {

    private val calc = StatsCalculator()

    private fun event(
        id: Int = 0,
        carId: Int = 1,
        eventDate: Long = 0L,
        odometerKm: Double = 0.0,
        kwhAdded: Double = 10.0,
    ) = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
    )

    @Test
    fun emptyEvents_returnsZeroStats() {
        val s = calc.computeStats(emptyList(), "label")
        assertEquals(0.0, s.totalKwh, 0.0)
        assertEquals(0, s.chargeCount)
        assertNull(s.avgKmPerKwh)
        assertNull(s.avgKwhPer100Km)
        assertNull(s.avgMiPerKwh)
        assertNull(s.costPerKm)
        assertNull(s.costPer100Km)
        assertEquals(false, s.mixedCurrency)
    }

    @Test
    fun singleEvent_totalsButNoEfficiency() {
        val s = calc.computeStats(listOf(event(kwhAdded = 42.0)), "label")
        assertEquals(42.0, s.totalKwh, 0.0)
        assertEquals(1, s.chargeCount)
        assertNull(s.avgKmPerKwh)
        assertNull(s.costPerKm)
    }

    @Test
    fun twoEvents_correctEfficiency() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 0.0, kwhAdded = 0.0),
                event(eventDate = 2, odometerKm = 100.0, kwhAdded = 20.0),
            ),
            "label",
        )
        assertEquals(2, s.chargeCount)
        assertEquals(20.0, s.totalKwh, 0.0)
        assertEquals(100.0, s.totalDistanceKm, 0.0)
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)
        assertEquals(20.0, s.avgKwhPer100Km!!, 0.0001)
        assertEquals(3.107, s.avgMiPerKwh!!, 0.001)
    }

    @Test
    fun multipleEvents_sumCorrect() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 0.0, kwhAdded = 0.0),
                event(eventDate = 2, odometerKm = 50.0, kwhAdded = 10.0),
                event(eventDate = 3, odometerKm = 150.0, kwhAdded = 20.0),
            ),
            "label",
        )
        assertEquals(3, s.chargeCount)
        assertEquals(150.0, s.totalDistanceKm, 0.0)
        assertEquals(30.0, s.totalKwh, 0.0)
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)
    }

    @Test
    fun negativeOdometerDelta_skipped() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 100.0, kwhAdded = 0.0),
                event(eventDate = 2, odometerKm = 50.0, kwhAdded = 10.0),
                event(eventDate = 3, odometerKm = 150.0, kwhAdded = 20.0),
            ),
            "label",
        )
        assertEquals(100.0, s.totalDistanceKm, 0.0)
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)
    }

    @Test
    fun monthlyAggregation_correctBuckets() {
        val jan15 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 15, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb1 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.FEBRUARY, 1, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb20 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.FEBRUARY, 20, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val mar5 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.MARCH, 5, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val buckets = calc.computeMonthlyBuckets(
            listOf(
                event(eventDate = jan15, kwhAdded = 10.0),
                event(eventDate = feb1, kwhAdded = 5.0),
                event(eventDate = feb20, kwhAdded = 7.0),
                event(eventDate = mar5, kwhAdded = 12.0),
            ),
        )

        assertEquals(3, buckets.size)
        assertEquals(2026, buckets[0].year)
        assertEquals(1, buckets[0].month)
        assertEquals(10.0, buckets[0].totalKwh, 0.0)
        assertEquals(2026, buckets[1].year)
        assertEquals(2, buckets[1].month)
        assertEquals(12.0, buckets[1].totalKwh, 0.0)
        assertEquals(2026, buckets[2].year)
        assertEquals(3, buckets[2].month)
        assertEquals(12.0, buckets[2].totalKwh, 0.0)
    }
}
