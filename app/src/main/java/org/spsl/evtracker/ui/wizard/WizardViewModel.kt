package org.spsl.evtracker.ui.wizard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.spsl.evtracker.data.repository.SettingsRepository

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    data class UiState(
        val page: Int = 0,
        val metric: String = "km_per_kwh",
        val unit: String = "km",
        val currency: String = "EUR"
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun goNext() {
        _state.update { it.copy(page = (it.page + 1).coerceAtMost(2)) }
    }

    fun goBack() {
        _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
    }

    fun selectMetric(metric: String) {
        _state.update { current ->
            val forcedUnit = when (metric) {
                "mi_per_kwh" -> "miles"
                "km_per_kwh", "kwh_per_100km" -> "km"
                else -> current.unit
            }
            current.copy(metric = metric, unit = forcedUnit)
        }
    }

    fun selectUnit(unit: String) {
        _state.update { current ->
            val coupledMetric = when {
                unit == "miles" && current.metric != "kwh_per_100km" -> "mi_per_kwh"
                unit == "km" && current.metric == "mi_per_kwh"        -> "km_per_kwh"
                else -> current.metric
            }
            current.copy(unit = unit, metric = coupledMetric)
        }
    }

    fun selectCurrency(currency: String) {
        _state.update { it.copy(currency = currency) }
    }

    suspend fun finish() {
        val s = state.value
        settingsRepository.completeSetup(
            metric = s.metric,
            unit = s.unit,
            currency = s.currency
        )
    }
}
