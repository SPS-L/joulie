// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.dashboard

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
import kotlinx.coroutines.launch
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardEvent
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DashboardScreenState
import org.spsl.evtracker.core.model.DashboardUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeDashboardStats: ObserveDashboardStatsUseCase,
    carReader: CarReader,
    settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
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
}
