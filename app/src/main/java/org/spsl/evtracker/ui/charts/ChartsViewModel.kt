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
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.ObserveChartsModelsUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val observeChartsModels: ObserveChartsModelsUseCase,
    settingsReader: SettingsReader,
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

    // Render inputs (distance unit, primary metric) join at the outer combine so flipping
    // either rebuilds the screen state without tearing down the Room subscription.
    val uiState: StateFlow<ChartsScreenState> =
        combine(
            chartsFlow,
            period,
            settingsReader.distanceUnit,
            settingsReader.primaryMetric,
        ) { ui, p, du, pm ->
            ChartsScreenState(period = p, distanceUnit = du, primaryMetric = pm, charts = ui)
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
}
