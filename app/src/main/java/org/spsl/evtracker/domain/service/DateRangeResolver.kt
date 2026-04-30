package org.spsl.evtracker.domain.service

import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DateRange
import java.util.Calendar
import javax.inject.Inject

class DateRangeResolver @Inject constructor() {

    fun resolve(period: DashboardPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange =
        when (period) {
            DashboardPeriod.SincePreviousCharge -> DateRange(0L, nowMillis)
            DashboardPeriod.Last7Days -> DateRange(nowMillis - 7 * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Last30Days -> DateRange(nowMillis - 30 * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Year -> DateRange(startOfYear(nowMillis), nowMillis)
            is DashboardPeriod.Custom -> DateRange(period.fromMillis, period.toMillis)
        }

    fun resolveCharts(period: ChartsPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange =
        when (period) {
            ChartsPeriod.Last6Months -> DateRange(nowMillis - 182L * MILLIS_PER_DAY, nowMillis)
            ChartsPeriod.Last12Months -> DateRange(nowMillis - 365L * MILLIS_PER_DAY, nowMillis)
            ChartsPeriod.AllTime -> DateRange(0L, nowMillis)
            is ChartsPeriod.Custom -> DateRange(period.fromMillis, period.toMillis)
        }

    private fun startOfYear(nowMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
