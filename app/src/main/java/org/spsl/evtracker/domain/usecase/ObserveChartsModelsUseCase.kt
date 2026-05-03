// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.core.coroutines.AggregationDispatcher
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.CapacityEstimator
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class ObserveChartsModelsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val capacityEstimator: CapacityEstimator,
    private val dateRangeResolver: DateRangeResolver,
    private val now: NowProvider,
    @AggregationDispatcher private val aggregationContext: CoroutineContext,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: ChartsPeriod): Flow<ChartsUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { active, cars ->
            active to cars
        }.flatMapLatest { (active, cars) ->
            when {
                cars.isEmpty() || active == -1L -> flowOf<ChartsUiState>(ChartsUiState.NoCar)
                else -> {
                    val activeCar = cars.firstOrNull { it.id == active }
                    chargeEventQueries.observeForCar(active).map { all ->
                        if (all.isEmpty()) {
                            ChartsUiState.NoEvents
                        } else {
                            build(all, period, activeCar)
                        }
                    }
                }
            }
        }.flowOn(aggregationContext)
    }

    private fun build(
        allEvents: List<ChargeEventEntity>,
        period: ChartsPeriod,
        activeCar: CarEntity?,
    ): ChartsUiState.Loaded {
        val range = dateRangeResolver.resolveCharts(period, now.nowMillis())
        val periodEvents = allEvents.filter { it.eventDate in range.startMillis..range.endMillis }
        val mixed = statsCalculator.detectMixedCurrency(periodEvents)
        val monthly = statsCalculator.computeMonthlyBuckets(periodEvents)
        val costBuckets = if (mixed) emptyList() else monthly.filter { it.totalCost != null }
        val resolvedCurrency = if (mixed) null else costBuckets.firstNotNullOfOrNull { it.currency }
        // Capacity uses the FULL per-car history (filtered by `period`) — degradation
        // is a long-term property, but the user still picks a period to scope what's
        // visible on the chart.
        val nominalBatteryKwh = activeCar?.batteryKwh
        val capacityPoints = capacityEstimator
            .estimate(periodEvents, nominalBatteryKwh)
            .takeIf { it.size >= CapacityEstimator.MIN_POINTS_FOR_CHART && nominalBatteryKwh != null }
            .orEmpty()
        // TASK-43: count derived events in the visible period so the chart
        // can surface a banner explaining the exclusion. Counted regardless
        // of whether the chart is rendered (banner can also fire when the
        // remaining measured-event count is below the 3-point threshold).
        val derivedExcluded = capacityEstimator.countDerivedEvents(periodEvents)
        return ChartsUiState.Loaded(
            periodHasEvents = periodEvents.isNotEmpty(),
            mixedCurrency = mixed,
            periodCurrency = resolvedCurrency,
            periodStartMillis = range.startMillis,
            trend = statsCalculator.computeEfficiencyTrend(periodEvents),
            monthlyKwh = monthly,
            monthlyCost = costBuckets,
            acDc = statsCalculator.computeAcDcSplit(periodEvents),
            locations = statsCalculator.computeLocationDistribution(periodEvents),
            capacity = capacityPoints,
            nominalBatteryKwh = nominalBatteryKwh,
            derivedExcludedCount = derivedExcluded,
        )
    }
}
