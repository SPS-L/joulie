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
import org.spsl.evtracker.domain.service.CO2Calculator
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
    private val co2Calculator: CO2Calculator,
    private val dateRangeResolver: DateRangeResolver,
    private val now: NowProvider,
) {
    /**
     * Bundle of settings flows the use case needs to fold together with the
     * event stream. Pulled into a data class so the outer `combine` is
     * legible and adding a new pref doesn't reshuffle the lambda arity.
     */
    private data class CombinedSettings(
        val activeCarId: Long,
        val cars: List<CarEntity>,
        val iceBaselineLPer100km: Double,
        val gridIntensityGCo2PerKwh: Double,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState> {
        return combine(
            settingsReader.activeCarId,
            carReader.observeAll(),
            settingsReader.iceBaselineLPer100km,
            settingsReader.gridIntensityGCo2PerKwh,
        ) { activeCarId, cars, iceL, gridG ->
            CombinedSettings(activeCarId, cars, iceL, gridG)
        }.flatMapLatest { settings ->
            when {
                settings.cars.isEmpty() || settings.activeCarId == -1L ->
                    flowOf(DashboardUiState(emptyState = EmptyState.NoCar))
                else -> {
                    val activeCar = settings.cars.firstOrNull { it.id == settings.activeCarId }
                    chargeEventQueries.observeForCar(settings.activeCarId).map { events ->
                        buildUiState(events, period, filter, activeCar, settings)
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
        settings: CombinedSettings,
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
        // The "no events" empty state reflects the car as a whole, not the
        // current filter+period. Gating on `allEventsForCar.isEmpty()` keeps
        // the empty state honest (a brand-new car) and lets a zero-match
        // filter fall through to a normal Stats render with placeholder
        // values, so the filter chips (which live inside `dashboardContent`)
        // stay visible and tappable.
        return if (allEventsForCar.isEmpty()) {
            DashboardUiState(emptyState = EmptyState.NoEvents)
        } else {
            // Battery-health is computed from the FULL per-car history, not the
            // filtered/period subset — degradation is a long-term property and
            // hiding it when the user picks a tight period would be misleading.
            val capacityPoints = capacityEstimator.estimate(allEventsForCar, activeCar?.batteryKwh)
            val healthPct = capacityEstimator.batteryHealthPercent(capacityPoints, activeCar?.batteryKwh)
            // surface heuristic provenance to the Stats consumer so
            // the Dashboard can render the "Estimated — heuristic may
            // overestimate" warning chip when the latest point came from the
            // unclamped 80%-of-nominal heuristic AND the percentage crosses
            // the 105% guard.
            val isHeuristic = capacityEstimator.latestIsExact(capacityPoints) == false
            val isOverestimated = isHeuristic &&
                healthPct != null &&
                healthPct >= CapacityEstimator.HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT
            val baseStats = statsCalculator.computeStats(
                events = filtered,
                label = period.toString(),
                batteryHealthPercent = healthPct,
                batteryHealthIsHeuristic = isHeuristic,
                batteryHealthIsOverestimated = isOverestimated,
            )
            // CO₂ stats. Both numbers are null when the user hasn't
            // configured the corresponding preference (zero or unset).
            // Dashboard hides the entire CO₂ card if either is null.
            val evCo2 = if (settings.gridIntensityGCo2PerKwh > 0.0) {
                co2Calculator.evCo2Kg(filtered, settings.gridIntensityGCo2PerKwh)
            } else {
                null
            }
            val iceCo2 = if (settings.iceBaselineLPer100km > 0.0 && baseStats.totalDistanceKm > 0.0) {
                co2Calculator.iceCounterfactualCo2Kg(
                    distanceKm = baseStats.totalDistanceKm,
                    iceBaselineLPer100km = settings.iceBaselineLPer100km,
                )
            } else {
                null
            }
            val stats = baseStats.copy(evCo2Kg = evCo2, iceCo2Kg = iceCo2)
            DashboardUiState(stats = stats, showMultiCurrencyBanner = stats.mixedCurrency)
        }
    }
}
