// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.domain.locale.LocaleApplier
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val localeApplier: LocaleApplier,
) : ViewModel() {

    data class UiState(
        val page: Int = 0,
        val metric: String = "kwh_per_100km",
        val unit: String = "km",
        val currency: String = "EUR",
        /** Page 3 (About + Disclaimer) Finish gate — true when the user ticks the acceptance checkbox. */
        val disclaimerAccepted: Boolean = false,
        /**
         * TASK-55: persisted language tag. `""` = follow system. The wizard
         * page 0 dropdown surfaces this; selecting an entry persists +
         * applies in one go.
         */
        val languageTag: String = "",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // TASK-55: hydrate the language tag from DataStore so the wizard
        // page 0 dropdown can show the currently-active selection (which
        // may already be non-empty if the user re-runs the wizard via
        // Settings → Reset preferences).
        viewModelScope.launch {
            settingsReader.languageTag.collect { v -> _state.update { it.copy(languageTag = v) } }
        }
    }

    fun goNext() {
        _state.update { it.copy(page = (it.page + 1).coerceAtMost(3)) }
    }

    fun goBack() {
        _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
    }

    fun setDisclaimerAccepted(accepted: Boolean) {
        _state.update { it.copy(disclaimerAccepted = accepted) }
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
                unit == "km" && current.metric == "mi_per_kwh" -> "km_per_kwh"
                else -> current.metric
            }
            current.copy(unit = unit, metric = coupledMetric)
        }
    }

    fun selectCurrency(currency: String) {
        _state.update { it.copy(currency = currency) }
    }

    /**
     * TASK-55: persist the chosen language tag immediately AND apply it
     * to the running process. The DataStore write survives a mid-wizard
     * kill so the user's choice is honoured on relaunch even before
     * `setupComplete = true`.
     */
    fun onLanguageSelected(tag: String) {
        viewModelScope.launch {
            settingsWriter.setLanguageTag(tag)
            localeApplier.apply(tag)
        }
    }

    suspend fun finish() {
        val s = state.value
        settingsWriter.completeSetup(
            metric = s.metric,
            unit = s.unit,
            currency = s.currency,
        )
    }
}
