package org.spsl.evtracker.core.model

data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgKmPerKwh: Double?,
    val avgKwhPer100Km: Double?,
    val avgMiPerKwh: Double?,
    val chargeCount: Int,
    val costPerKm: Double?,
    val costPer100Km: Double?,
    val mixedCurrency: Boolean
)
