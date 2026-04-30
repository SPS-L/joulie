package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChartsPeriod

class DateRangeResolverChartsTest {

    private val resolver = DateRangeResolver()
    private val now = 1_714_032_000_000L // 2024-04-25T08:00Z; deterministic anchor

    @Test fun last6Months_182Days() {
        val r = resolver.resolveCharts(ChartsPeriod.Last6Months, now)
        assertEquals(now - 182L * 24 * 60 * 60 * 1000, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun last12Months_365Days() {
        val r = resolver.resolveCharts(ChartsPeriod.Last12Months, now)
        assertEquals(now - 365L * 24 * 60 * 60 * 1000, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun allTime_lowerBoundZero() {
        val r = resolver.resolveCharts(ChartsPeriod.AllTime, now)
        assertEquals(0L, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test fun custom_passthrough() {
        val r = resolver.resolveCharts(ChartsPeriod.Custom(100L, 200L), now)
        assertEquals(100L, r.startMillis)
        assertEquals(200L, r.endMillis)
    }
}
