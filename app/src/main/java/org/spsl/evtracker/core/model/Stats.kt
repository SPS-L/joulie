package org.spsl.evtracker.core.model

data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgKmPerKwh: Double?,
    val avgKwhPer100Km: Double?,
    val avgMiPerKwh: Double?,
    val chargeCount: Int,
    // sum of costTotal across costed events; null when no cost data or mixedCurrency
    val totalCost: Double?,
    // the single currency of the costed events; null when no cost data or mixedCurrency
    val currency: String?,
    val costPerKm: Double?,
    val costPer100Km: Double?,
    val mixedCurrency: Boolean,
)
