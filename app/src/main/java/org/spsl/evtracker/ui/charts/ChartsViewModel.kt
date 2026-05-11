// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.spsl.evtracker.core.model.CarbonIntensityBucket
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.data.repository.ElectricityMapsRepository
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.NowProvider
import org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val observeChartsModels: ObserveChartsModelsUseCase,
    private val settingsReader: SettingsReader,
    private val now: NowProvider,
) : ViewModel() {

    private val period = MutableStateFlow<ChartsPeriod>(ChartsPeriod.Last12Months)

    private val _events = MutableSharedFlow<ChartsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ChartsEvent> = _events.asSharedFlow()

    // Behaviour-driving flow: only `period` triggers re-subscription / re-aggregation.
    private val chartsFlow: Flow<ChartsUiState> =
        period.flatMapLatest { p -> observeChartsModels.observe(p) }

    /**
     * Snapshot of the current "right now" grid carbon intensity for the CO₂
     * tab's banner (TASK-87). Emits non-null only when CO₂ is on, an API key
     * is set, and the persistent cache is fresh for the current zone.
     *
     * Built from 6 settings flows via the same nested-combine pattern as
     * `DashboardViewModel.carbonInputs` because the built-in combine arity
     * caps at 5. The mapping is intentionally a tighter version of
     * `CarbonIntensityFormatter.format` — we only care about the Ready state
     * (the banner has no Loading/Error counterparts on the Charts tab).
     */
    private val currentCarbonReadyFlow: Flow<CarbonIntensityUiState.Ready?> = combine(
        settingsReader.co2Enabled,
        settingsReader.electricityMapsApiKey,
        settingsReader.electricityMapsZone,
        settingsReader.electricityMapsCacheZone,
        combine(
            settingsReader.electricityMapsCacheIntensity,
            settingsReader.electricityMapsCacheFetchedAtMs,
        ) { intensity, fetchedAt -> intensity to fetchedAt },
    ) { co2On, apiKey, zone, cacheZone, (cacheIntensity, cacheFetchedAt) ->
        currentReadyIfFresh(co2On, apiKey, zone, cacheZone, cacheIntensity, cacheFetchedAt, now.nowMillis())
    }

    val uiState: StateFlow<ChartsScreenState> =
        combine(
            chartsFlow,
            period,
            settingsReader.distanceUnit,
            settingsReader.primaryMetric,
            currentCarbonReadyFlow,
        ) { ui, p, du, pm, ready ->
            ChartsScreenState(
                period = p,
                distanceUnit = du,
                primaryMetric = pm,
                charts = ui,
                currentCarbonReady = ready,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsScreenState())

    fun selectPeriod(p: ChartsPeriod) {
        period.value = p
    }
    fun selectCustomRange(from: Long, to: Long) {
        period.value = ChartsPeriod.Custom(from, to)
    }
    fun onCustomChipClicked() {
        _events.tryEmit(ChartsEvent.OpenCustomRangePicker)
    }
    fun onAddCarCta() {
        _events.tryEmit(ChartsEvent.NavigateToCars)
    }
    fun onLogChargeCta() {
        _events.tryEmit(ChartsEvent.NavigateToChargeEdit)
    }

    private fun currentReadyIfFresh(
        co2Enabled: Boolean,
        apiKey: String,
        currentZone: String,
        cacheZone: String,
        cacheIntensity: Double,
        cacheFetchedAt: Long,
        nowMs: Long,
    ): CarbonIntensityUiState.Ready? {
        if (!co2Enabled || apiKey.isBlank()) return null
        val cacheFresh = cacheZone == currentZone &&
            cacheFetchedAt > 0L &&
            cacheIntensity > 0.0 &&
            nowMs - cacheFetchedAt < ElectricityMapsRepository.CACHE_TTL_MS
        if (!cacheFresh) return null
        return CarbonIntensityUiState.Ready(
            intensityGCo2PerKwh = cacheIntensity,
            bucket = CarbonIntensityBucket.forValue(cacheIntensity),
            fetchedAtMs = cacheFetchedAt,
        )
    }
}
