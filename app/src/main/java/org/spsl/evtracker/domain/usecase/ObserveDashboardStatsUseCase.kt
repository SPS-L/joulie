// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DashboardUiState
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.CapacityEstimator
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import javax.inject.Inject

class ObserveDashboardStatsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val capacityEstimator: CapacityEstimator,
    private val dateRangeResolver: DateRangeResolver,
    private val now: NowProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { activeCarId, cars ->
            Pair(activeCarId, cars)
        }.flatMapLatest { (activeCarId, cars) ->
            when {
                cars.isEmpty() || activeCarId == -1L ->
                    flowOf(DashboardUiState(emptyState = EmptyState.NoCar))
                else -> {
                    val activeCar = cars.firstOrNull { it.id == activeCarId }
                    chargeEventQueries.observeForCar(activeCarId).map { events ->
                        buildUiState(events, period, filter, activeCar)
                    }
                }
            }
        }
    }

    private fun buildUiState(
        allEventsForCar: List<ChargeEventEntity>,
        period: DashboardPeriod,
        filter: ChargeTypeFilter,
        activeCar: CarEntity?,
    ): DashboardUiState {
        val periodEvents = when (period) {
            DashboardPeriod.SincePreviousCharge -> allEventsForCar.takeLast(2)
            else -> {
                val range = dateRangeResolver.resolve(period, now.nowMillis())
                allEventsForCar.filter { it.eventDate in range.startMillis..range.endMillis }
            }
        }
        val filtered = when (filter) {
            ChargeTypeFilter.ALL -> periodEvents
            ChargeTypeFilter.AC -> periodEvents.filter { it.chargeType == ChargeType.AC }
            ChargeTypeFilter.DC -> periodEvents.filter { it.chargeType.isDc }
        }
        return if (filtered.isEmpty()) {
            DashboardUiState(emptyState = EmptyState.NoEvents)
        } else {
            // Battery-health is computed from the FULL per-car history, not the
            // filtered/period subset — degradation is a long-term property and
            // hiding it when the user picks a tight period would be misleading.
            val capacityPoints = capacityEstimator.estimate(allEventsForCar, activeCar?.batteryKwh)
            val healthPct = capacityEstimator.batteryHealthPercent(capacityPoints, activeCar?.batteryKwh)
            val stats = statsCalculator.computeStats(
                events = filtered,
                label = period.toString(),
                batteryHealthPercent = healthPct,
            )
            DashboardUiState(stats = stats, showMultiCurrencyBanner = stats.mixedCurrency)
        }
    }
}
