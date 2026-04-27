package org.spsl.evtracker.domain.service

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.DashboardPeriod

class DateRangeResolverTest {

    private val resolver = DateRangeResolver()
    private val MS_PER_DAY = 24L * 60 * 60 * 1000

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 26, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun last7Days_returnsNowMinus7DaysToNow() {
        val r = resolver.resolve(DashboardPeriod.Last7Days, now)
        assertEquals(now - 7 * MS_PER_DAY, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun last30Days_returnsNowMinus30DaysToNow() {
        val r = resolver.resolve(DashboardPeriod.Last30Days, now)
        assertEquals(now - 30 * MS_PER_DAY, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun year_isJanuary1OfCurrentYearAtMidnight() {
        val r = resolver.resolve(DashboardPeriod.Year, now)
        val expectedStart = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedStart, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun custom_returnsItsInputs() {
        val r = resolver.resolve(DashboardPeriod.Custom(fromMillis = 1000L, toMillis = 2000L), now)
        assertEquals(1000L, r.startMillis)
        assertEquals(2000L, r.endMillis)
    }

    @Test
    fun sincePreviousCharge_isFromZeroToNow() {
        val r = resolver.resolve(DashboardPeriod.SincePreviousCharge, now)
        assertEquals(0L, r.startMillis)
        assertEquals(now, r.endMillis)
    }
}
