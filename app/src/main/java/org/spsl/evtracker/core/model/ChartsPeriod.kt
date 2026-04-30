package org.spsl.evtracker.core.model

sealed class ChartsPeriod {
    object Last6Months : ChartsPeriod()
    object Last12Months : ChartsPeriod()
    object AllTime : ChartsPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : ChartsPeriod()
}
