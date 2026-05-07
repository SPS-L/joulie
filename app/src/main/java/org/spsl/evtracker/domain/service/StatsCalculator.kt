// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.spsl.evtracker.core.model.AcDcSplit
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.EfficiencyPoint
import org.spsl.evtracker.core.model.EfficiencySeries
import org.spsl.evtracker.core.model.LocationSlice
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.core.model.Stats
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import java.util.Calendar
import javax.inject.Inject

class StatsCalculator @Inject constructor() {

    /**
     * Defense-in-depth guard for the single-car invariant. Every
     * aggregation in this class except [detectMixedCurrency] assumes its
     * input shares a single `carId` — the odometer-delta loops in
     * [computeStats] / [computeEfficiencyTrend] produce nonsense numbers
     * across a car-switch boundary if two cars happen to have similar
     * odometer readings, and [computeMonthlyBuckets] / [computeAcDcSplit]
     * / [computeLocationDistribution] would silently roll two cars'
     * histories into one indistinguishable summary.
     *
     * Today every call site (`ObserveDashboardStatsUseCase`,
     * `ObserveChartsModelsUseCase`) passes a single-car list, but the
     * invariant is unenforced. A future cross-car comparison view would
     * trip this require with a clear failure rather than silently
     * producing wrong numbers; if cross-car aggregation ever becomes a
     * legitimate use case, add a parallel method (`computeStatsAcrossCars`,
     * etc.) rather than relaxing the existing contract.
     *
     * Empty input passes the guard — `distinct().size == 0` is `<= 1`.
     */
    private fun requireSingleCar(events: List<ChargeEventEntity>) {
        val distinctCars = events.map { it.carId }.distinct()
        require(distinctCars.size <= 1) {
            "expects a single-car event list; got carIds=$distinctCars"
        }
    }

    fun computeStats(
        events: List<ChargeEventEntity>,
        label: String,
        batteryHealthPercent: Double? = null,
        batteryHealthIsHeuristic: Boolean = false,
        batteryHealthIsOverestimated: Boolean = false,
    ): Stats {
        requireSingleCar(events)
        val totalKwhAll = events.sumOf { it.kwhAdded }
        val chargeCount = events.size

        // Cost over the period is the sum of every costed event (DESIGN.md
        // §7: `Σ cost / Σ d_km`). Accumulating cost outside the delta-pair
        // odometer loop ensures the first event's cost contributes — matches
        // `computeMonthlyBuckets` which already sums every event.
        val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
        val mixedCurrency = costedCurrencies.size > 1
        val totalCost = if (mixedCurrency) 0.0 else events.mapNotNull { it.costTotal }.sum()
        val costCount = if (mixedCurrency) 0 else events.count { it.costTotal != null }
        val resolvedTotalCost = if (costCount > 0) totalCost else null
        val resolvedCurrency = if (costCount > 0) costedCurrencies.singleOrNull() else null

        if (events.size < 2) {
            return Stats(
                label = label,
                totalKwh = totalKwhAll,
                totalDistanceKm = 0.0,
                avgKmPerKwh = null,
                avgKwhPer100Km = null,
                avgMiPerKwh = null,
                chargeCount = chargeCount,
                totalCost = resolvedTotalCost,
                currency = resolvedCurrency,
                costPerKm = null,
                costPer100Km = null,
                mixedCurrency = mixedCurrency,
                batteryHealthPercent = batteryHealthPercent,
                batteryHealthIsHeuristic = batteryHealthIsHeuristic,
                batteryHealthIsOverestimated = batteryHealthIsOverestimated,
            )
        }

        val sorted = events.sortedBy { it.eventDate }
        var pairKwh = 0.0
        var totalDist = 0.0

        for (i in 1 until sorted.size) {
            val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
            if (dist > 0) {
                pairKwh += sorted[i].kwhAdded
                totalDist += dist
            }
        }

        val avgKmPerKwh = if (pairKwh > 0) totalDist / pairKwh else null
        val avgKwhPer100Km = avgKmPerKwh?.let { 100.0 / it }
        val avgMiPerKwh = avgKmPerKwh?.let { UnitConverter.kmPerKwhToMiPerKwh(it) }
        val costPerKm = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
        val costPer100Km = costPerKm?.times(100.0)

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
            mixedCurrency = mixedCurrency,
            batteryHealthPercent = batteryHealthPercent,
            batteryHealthIsHeuristic = batteryHealthIsHeuristic,
            batteryHealthIsOverestimated = batteryHealthIsOverestimated,
        )
    }

    fun computeMonthlyBuckets(events: List<ChargeEventEntity>): List<MonthBucket> {
        requireSingleCar(events)
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
            } else {
                null
            }
            MonthBucket(
                year = year,
                month = month,
                totalKwh = totalKwh,
                totalCost = totalCost,
                currency = if (totalCost != null) singleCurrency else null,
            )
        }.sortedWith(compareBy({ it.year }, { it.month }))
    }

    /**
     * Exemption: this function semantically does NOT depend on
     * car identity — it is asking "are there ≥ 2 distinct currencies
     * among costed events". A future cross-car aggregator (e.g. a
     * fleet-level summary view) might legitimately call this on a
     * multi-car list to check currency homogeneity, so the
     * single-car guard is intentionally NOT applied here. Every other
     * aggregation in this class enforces the guard via [requireSingleCar].
     */
    fun detectMixedCurrency(events: List<ChargeEventEntity>): Boolean =
        events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct().size > 1

    fun computeEfficiencyTrend(events: List<ChargeEventEntity>): EfficiencySeries {
        requireSingleCar(events)
        fun seriesFor(predicate: (ChargeEventEntity) -> Boolean): List<EfficiencyPoint> {
            val sorted = events.filter(predicate).sortedBy { it.eventDate }
            val out = ArrayList<EfficiencyPoint>(sorted.size)
            for (i in 1 until sorted.size) {
                val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
                if (dist > 0 && sorted[i].kwhAdded > 0.0) {
                    out += EfficiencyPoint(
                        eventTimeMillis = sorted[i].eventDate,
                        kmPerKwh = dist / sorted[i].kwhAdded,
                    )
                }
            }
            return out
        }
        return EfficiencySeries(
            acPoints = seriesFor { it.chargeType == ChargeType.AC },
            dcPoints = seriesFor { it.chargeType.isDc },
        )
    }

    fun computeAcDcSplit(events: List<ChargeEventEntity>): AcDcSplit {
        requireSingleCar(events)
        val ac = events.filter { it.chargeType == ChargeType.AC }
        val dc = events.filter { it.chargeType.isDc }
        return AcDcSplit(
            acCount = ac.size,
            dcCount = dc.size,
            acKwh = ac.sumOf { it.kwhAdded },
            dcKwh = dc.sumOf { it.kwhAdded },
        )
    }

    fun computeLocationDistribution(events: List<ChargeEventEntity>): List<LocationSlice> {
        requireSingleCar(events)
        val counts = events
            .mapNotNull { it.location?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        if (counts.isEmpty()) return emptyList()
        val ranked = counts.entries.sortedByDescending { it.value }
        val top = ranked.take(MAX_LOCATION_SLICES)
            .map { LocationSlice(it.key, it.value) }
        val tail = ranked.drop(MAX_LOCATION_SLICES)
        return if (tail.isEmpty()) {
            top
        } else {
            top + LocationSlice(LocationSlice.OTHER_KEY, tail.sumOf { it.value })
        }
    }

    companion object {
        // Cap chosen so the pie chart stays legible on phone widths.
        // Tweaking is a code change, not a localization change.
        const val MAX_LOCATION_SLICES = 8
    }
}
