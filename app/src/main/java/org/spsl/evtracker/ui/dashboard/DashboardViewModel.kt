// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardEvent
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DashboardScreenState
import org.spsl.evtracker.core.model.DashboardUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.service.CarbonIntensityFormatter
import org.spsl.evtracker.domain.usecase.NowProvider
import org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCase
import org.spsl.evtracker.domain.usecase.RefreshCarbonIntensityUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeDashboardStats: ObserveDashboardStatsUseCase,
    carReader: CarReader,
    settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val refreshCarbonIntensity: RefreshCarbonIntensityUseCase,
    private val carbonIntensityFormatter: CarbonIntensityFormatter,
    private val carbonIntensitySource: CarbonIntensitySource,
    private val now: NowProvider,
) : ViewModel() {

    private val period = MutableStateFlow<DashboardPeriod>(DashboardPeriod.Last30Days)
    private val filter = MutableStateFlow(ChargeTypeFilter.ALL)

    private val _events = MutableSharedFlow<DashboardEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<DashboardEvent> = _events.asSharedFlow()

    private val dashboardFlow: Flow<DashboardUiState> =
        combine(period, filter) { p, f -> p to f }
            .flatMapLatest { (p, f) -> observeDashboardStats.observe(p, f) }

    private data class Inputs(
        val cars: List<CarEntity>,
        val activeCarId: Long,
        val primaryMetric: String,
        val distanceUnit: String,
        val currency: String,
    )

    private val inputsFlow: Flow<Inputs> = combine(
        carReader.observeAll(),
        settingsReader.activeCarId,
        settingsReader.primaryMetric,
        settingsReader.distanceUnit,
        settingsReader.currency,
    ) { cars, active, metric, unit, ccy -> Inputs(cars, active, metric, unit, ccy) }

    val uiState: StateFlow<DashboardScreenState> =
        combine(inputsFlow, dashboardFlow, period, filter) { inputs, dashboard, p, f ->
            DashboardScreenState(
                cars = inputs.cars,
                activeCarId = inputs.activeCarId,
                period = p,
                filter = f,
                primaryMetric = inputs.primaryMetric,
                distanceUnit = inputs.distanceUnit,
                currency = inputs.currency,
                dashboard = dashboard,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardScreenState())

    fun selectPeriod(newPeriod: DashboardPeriod) {
        period.value = newPeriod
    }

    fun selectCustomRange(from: Long, to: Long) {
        period.value = DashboardPeriod.Custom(fromMillis = from, toMillis = to)
    }

    fun selectFilter(newFilter: ChargeTypeFilter) {
        filter.value = newFilter
    }

    fun selectCar(carId: Long) {
        viewModelScope.launch { settingsWriter.setActiveCarId(carId) }
    }

    fun onFabClick() {
        if (uiState.value.activeCarId != -1L) {
            _events.tryEmit(DashboardEvent.NavigateToChargeEdit)
        }
    }

    fun onAddCarCtaClick() {
        _events.tryEmit(DashboardEvent.NavigateToCars)
    }

    fun onLogChargeCtaClick() {
        _events.tryEmit(DashboardEvent.NavigateToChargeEdit)
    }

    fun onManageCarsClick() {
        _events.tryEmit(DashboardEvent.NavigateToManageCars)
    }

    // ── TASK-82: Carbon-intensity pill ───────────────────────────────────

    private val isRefreshingCarbon = MutableStateFlow(false)

    /**
     * Bundle of inputs the carbon-intensity formatter folds into one
     * [CarbonIntensityUiState]. Bundled to keep the `combine` arity
     * manageable (6 sources is at the edge of what Kotlin Flow's
     * built-in combine variants support).
     */
    private data class CarbonInputs(
        val co2Enabled: Boolean,
        val apiKey: String,
        val zone: String,
        val cacheZone: String,
        val cacheIntensity: Double,
        val cacheFetchedAtMs: Long,
    )

    private val carbonInputs: Flow<CarbonInputs> = combine(
        settingsReader.co2Enabled,
        settingsReader.electricityMapsApiKey,
        settingsReader.electricityMapsZone,
        settingsReader.electricityMapsCacheZone,
        combine(
            settingsReader.electricityMapsCacheIntensity,
            settingsReader.electricityMapsCacheFetchedAtMs,
        ) { intensity, fetchedAt -> intensity to fetchedAt },
    ) { co2On, key, zone, cacheZone, (intensity, fetchedAt) ->
        CarbonInputs(co2On, key, zone, cacheZone, intensity, fetchedAt)
    }

    val carbonIntensity: StateFlow<CarbonIntensityUiState> =
        combine(carbonInputs, isRefreshingCarbon, carbonIntensitySource.lastError) { inputs, refreshing, lastError ->
            carbonIntensityFormatter.format(
                co2Enabled = inputs.co2Enabled,
                apiKey = inputs.apiKey,
                currentZone = inputs.zone,
                cacheZone = inputs.cacheZone,
                cacheIntensityGCo2PerKwh = inputs.cacheIntensity,
                cacheFetchedAtMs = inputs.cacheFetchedAtMs,
                nowMs = now.nowMillis(),
                isRefreshing = refreshing,
                lastError = lastError,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarbonIntensityUiState.Hidden)

    init {
        // Belt-and-suspenders with MainViewModel.init's boot fetch:
        // serves users who land on the Dashboard after the boot fetch failed
        // (e.g. flaky network at startup). Repo's Mutex + persistent cache
        // make the duplicate call essentially free.
        viewModelScope.launch {
            isRefreshingCarbon.value = true
            try {
                runCatching { refreshCarbonIntensity() }
            } finally {
                isRefreshingCarbon.value = false
            }
        }
    }

    private var periodicRefreshJob: Job? = null

    /**
     * TASK-84: start the periodic foreground refresh loop. Called by the
     * Fragment from `onStart`. The loop ticks every
     * [PERIODIC_REFRESH_INTERVAL_MS]: each tick is a cheap cache read
     * unless the persistent 1-hour cache has expired, at which point the
     * next tick flips the pill from "stale Ready" to "fresh Ready"
     * without the user needing to tap. The refresh use case short-circuits
     * when CO₂ is off or the API key is blank, so the loop is a no-op
     * when the feature is disabled.
     *
     * Idempotent: calling twice while the job is alive is a no-op.
     */
    fun startPeriodicRefresh() {
        if (periodicRefreshJob?.isActive == true) return
        periodicRefreshJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(PERIODIC_REFRESH_INTERVAL_MS)
                if (isRefreshingCarbon.value) continue
                isRefreshingCarbon.value = true
                try {
                    runCatching { refreshCarbonIntensity() }
                } finally {
                    isRefreshingCarbon.value = false
                }
            }
        }
    }

    /**
     * Stop the TASK-84 periodic-refresh loop. Called by the Fragment
     * from `onStop` so the loop doesn't keep ticking while the user is
     * looking at another screen. Safe to call when the loop isn't
     * running.
     */
    fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    fun onRefreshCarbonIntensity() {
        if (isRefreshingCarbon.value) return
        viewModelScope.launch {
            isRefreshingCarbon.value = true
            try {
                runCatching { refreshCarbonIntensity() }
            } finally {
                isRefreshingCarbon.value = false
            }
        }
    }

    companion object {
        /** TASK-84 periodic-refresh tick. Set just under the cache TTL so a
         *  user who's been on the dashboard for more than an hour reliably
         *  picks up the next fetch without manual interaction; the repo's
         *  throttle ensures we don't actually hit the network until the
         *  cached value expires. */
        internal const val PERIODIC_REFRESH_INTERVAL_MS: Long = 15L * 60L * 1_000L
    }
}
