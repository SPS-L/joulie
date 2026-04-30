package org.spsl.evtracker.core.model

data class MonthBucket(
    val year: Int,
    val month: Int,
    val totalKwh: Double,
    val totalCost: Double?,
    val currency: String?,
)
