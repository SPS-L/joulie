package org.spsl.evtracker.domain.service

import java.util.Calendar
import javax.inject.Inject
import org.spsl.evtracker.core.model.AcDcSplit
import org.spsl.evtracker.core.model.EfficiencyPoint
import org.spsl.evtracker.core.model.EfficiencySeries
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.core.model.Stats
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculator @Inject constructor() {

    fun computeStats(events: List<ChargeEventEntity>, label: String): Stats {
        val totalKwhAll = events.sumOf { it.kwhAdded }
        val chargeCount = events.size

        if (events.size < 2) {
            return Stats(
                label = label,
                totalKwh = totalKwhAll,
                totalDistanceKm = 0.0,
                avgKmPerKwh = null,
                avgKwhPer100Km = null,
                avgMiPerKwh = null,
                chargeCount = chargeCount,
                totalCost = null,
                currency = null,
                costPerKm = null,
                costPer100Km = null,
                mixedCurrency = false
            )
        }

        val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
        val mixedCurrency = costedCurrencies.size > 1

        val sorted = events.sortedBy { it.eventDate }
        var pairKwh = 0.0
        var totalDist = 0.0
        var totalCost = 0.0
        var costCount = 0

        for (i in 1 until sorted.size) {
            val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
            if (dist > 0) {
                pairKwh += sorted[i].kwhAdded
                totalDist += dist
                if (!mixedCurrency) {
                    sorted[i].costTotal?.let { totalCost += it; costCount++ }
                }
            }
        }

        val avgKmPerKwh    = if (pairKwh > 0)  totalDist / pairKwh else null
        val avgKwhPer100Km = avgKmPerKwh?.let { 100.0 / it }
        val avgMiPerKwh    = avgKmPerKwh?.let { UnitConverter.kmPerKwhToMiPerKwh(it) }
        val resolvedTotalCost = if (costCount > 0) totalCost else null
        val resolvedCurrency  = if (costCount > 0) costedCurrencies.singleOrNull() else null
        val costPerKm      = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
        val costPer100Km   = costPerKm?.times(100.0)

        return Stats(
            label = label,
            totalKwh = totalKwhAll,
            totalDistanceKm = totalDist,
            avgKmPerKwh = avgKmPerKwh,
            avgKwhPer100Km = avgKwhPer100Km,
            avgMiPerKwh = avgMiPerKwh,
            chargeCount = chargeCount,
            totalCost = resolvedTotalCost,
            currency = resolvedCurrency,
            costPerKm = costPerKm,
            costPer100Km = costPer100Km,
            mixedCurrency = mixedCurrency
        )
    }

    fun computeMonthlyBuckets(events: List<ChargeEventEntity>): List<MonthBucket> {
        val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
        val singleCurrency = costedCurrencies.singleOrNull()
        val groups = events.groupBy { ev ->
            val cal = Calendar.getInstance().apply { timeInMillis = ev.eventDate }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        return groups.map { (yearMonth, bucketEvents) ->
            val (year, month) = yearMonth
            val totalKwh = bucketEvents.sumOf { it.kwhAdded }
            val totalCost = if (singleCurrency != null) {
                val sum = bucketEvents.mapNotNull { it.costTotal }.sum()
                if (sum > 0) sum else null
            } else null
            MonthBucket(
                year = year,
                month = month,
                totalKwh = totalKwh,
                totalCost = totalCost,
                currency = if (totalCost != null) singleCurrency else null
            )
        }.sortedWith(compareBy({ it.year }, { it.month }))
    }

    fun detectMixedCurrency(events: List<ChargeEventEntity>): Boolean =
        events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct().size > 1

    fun computeEfficiencyTrend(events: List<ChargeEventEntity>): EfficiencySeries {
        fun seriesFor(type: String): List<EfficiencyPoint> {
            val sorted = events.filter { it.chargeType == type }.sortedBy { it.eventDate }
            val out = ArrayList<EfficiencyPoint>(sorted.size)
            for (i in 1 until sorted.size) {
                val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
                if (dist > 0 && sorted[i].kwhAdded > 0.0) {
                    out += EfficiencyPoint(
                        eventTimeMillis = sorted[i].eventDate,
                        kmPerKwh = dist / sorted[i].kwhAdded
                    )
                }
            }
            return out
        }
        return EfficiencySeries(
            acPoints = seriesFor("AC"),
            dcPoints = seriesFor("DC")
        )
    }

    fun computeAcDcSplit(events: List<ChargeEventEntity>): AcDcSplit {
        val ac = events.filter { it.chargeType == "AC" }
        val dc = events.filter { it.chargeType == "DC" }
        return AcDcSplit(
            acCount = ac.size,
            dcCount = dc.size,
            acKwh   = ac.sumOf { it.kwhAdded },
            dcKwh   = dc.sumOf { it.kwhAdded }
        )
    }
}
