// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.chargeedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeEditEvent
import org.spsl.evtracker.core.model.ChargeEditUiState
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.CostInput
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.domain.service.KwhFromSocCalculator
import org.spsl.evtracker.domain.service.UnitConverter
import org.spsl.evtracker.domain.usecase.NowProvider
import org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase
import javax.inject.Inject

@HiltViewModel
class ChargeEditViewModel @Inject constructor(
    private val saveChargeEvent: SaveChargeEventUseCase,
    locationReader: LocationReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val carReader: CarReader,
    private val settingsReader: SettingsReader,
    private val now: NowProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargeEditUiState(eventDateMillis = now.nowMillis()))
    val uiState: StateFlow<ChargeEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChargeEditEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ChargeEditEvent> = _events.asSharedFlow()

    init {
        val rawId = savedStateHandle.get<Long>("eventId") ?: -1L
        viewModelScope.launch {
            val activeCarId = settingsReader.activeCarId.first()
            val unit = settingsReader.distanceUnit.first()
            val ccy = settingsReader.currency.first()
            if (rawId == -1L) {
                buildCreateState(activeCarId, unit, ccy)
            } else {
                val event = chargeEventQueries.getById(rawId)
                if (event == null) {
                    buildCreateState(activeCarId, unit, ccy)
                } else {
                    buildEditState(event, unit, ccy)
                }
            }
        }
        viewModelScope.launch {
            locationReader.observeTop5()
                .map { entities -> entities.map { it.label } }
                .collect { labels ->
                    _uiState.update { it.copy(locationChips = it.locationChips.copy(custom = labels)) }
                }
        }
    }

    private suspend fun buildCreateState(activeCarId: Long, unit: String, ccy: String) {
        val sorted = chargeEventQueries.getAllForCarSorted(activeCarId)
        val prevKm = sorted.lastOrNull()?.odometerKm
        val prefilled = prevKm?.let { (it + 1.0).toDisplayUnit(unit).toString() } ?: ""
        val nominal = carReader.getById(activeCarId)?.batteryKwh
        _uiState.value = ChargeEditUiState(
            mode = ChargeEditUiState.Mode.Create,
            carId = activeCarId,
            eventDateMillis = now.nowMillis(),
            odometer = prefilled,
            distanceUnit = unit,
            currency = ccy,
            previousOdometerKm = prevKm,
            nominalBatteryKwh = nominal,
        )
    }

    private suspend fun buildEditState(event: org.spsl.evtracker.data.local.entity.ChargeEventEntity, unit: String, ccy: String) {
        val sorted = chargeEventQueries.getAllForCarSorted(event.carId)
        val idx = sorted.indexOfFirst { it.id == event.id }
        val prevKm = sorted.getOrNull(idx - 1)?.odometerKm
        val nextKm = sorted.getOrNull(idx + 1)?.odometerKm
        val displayOdo = event.odometerKm.toDisplayUnit(unit)
        val costExpanded = event.costTotal != null
        val costValue = event.costTotal?.toString() ?: ""
        val nominal = carReader.getById(event.carId)?.batteryKwh
        _uiState.value = ChargeEditUiState(
            mode = ChargeEditUiState.Mode.Edit(event.id),
            carId = event.carId,
            eventDateMillis = event.eventDate,
            odometer = displayOdo.toString(),
            kwh = event.kwhAdded.toString(),
            chargeType = event.chargeType,
            location = event.location ?: "",
            costExpanded = costExpanded,
            costMode = CostMode.TOTAL,
            costValue = costValue,
            note = event.note,
            distanceUnit = unit,
            currency = event.currency ?: ccy,
            previousOdometerKm = prevKm,
            nextOdometerKm = nextKm,
            odometerBelowPrevious = prevKm != null && event.odometerKm <= prevKm,
            odometerAboveNext = nextKm != null && event.odometerKm >= nextKm,
            socExpanded = event.socBefore != null || event.socAfter != null,
            socBeforeText = event.socBefore?.let { (it * 100.0).toPercentText() } ?: "",
            socAfterText = event.socAfter?.let { (it * 100.0).toPercentText() } ?: "",
            kwhSource = event.kwhSource,
            nominalBatteryKwh = nominal,
        )
    }

    private fun Double.toPercentText(): String {
        val rounded = kotlin.math.round(this * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private fun Double.toDisplayUnit(unit: String): Double =
        if (unit == "miles") UnitConverter.kmToMiles(this) else this

    fun setEventDate(millis: Long) = _uiState.update { it.copy(eventDateMillis = millis) }
    fun setOdometer(text: String) = _uiState.update { st ->
        val km = text.trim().toDoubleOrNull()?.let { typed ->
            if (st.distanceUnit == "miles") UnitConverter.milesToKm(typed) else typed
        }
        st.copy(
            odometer = text,
            odometerError = null,
            odometerBelowPrevious = km != null && st.previousOdometerKm != null &&
                km <= st.previousOdometerKm,
            odometerAboveNext = km != null && st.nextOdometerKm != null &&
                km >= st.nextOdometerKm,
        )
    }

    /**
     * TASK-43 override semantics: when the typed text differs from current
     * state.kwh, the user is genuinely editing — flip provenance to
     * MEASURED and deactivate the SoC calculator. When the text matches
     * (echo from a programmatic setText() — initial render, or our own
     * calculator update), preserve the existing provenance.
     */
    fun setKwh(text: String) = _uiState.update { st ->
        if (text == st.kwh) {
            st.copy(kwhError = null)
        } else {
            st.copy(
                kwh = text,
                kwhError = null,
                kwhSource = ChargeKwhSource.MEASURED,
                kwhCalculatorActive = false,
            )
        }
    }
    fun setChargeType(type: ChargeType) = _uiState.update { it.copy(chargeType = type) }
    fun selectLocationChip(label: String) = _uiState.update { it.copy(location = label) }
    fun setLocation(text: String) = _uiState.update { it.copy(location = text) }
    fun toggleCostExpanded() = _uiState.update { it.copy(costExpanded = !it.costExpanded) }
    fun setCostMode(mode: CostMode) = _uiState.update { it.copy(costMode = mode) }
    fun setCostValue(text: String) = _uiState.update { it.copy(costValue = text) }
    fun setNote(text: String) = _uiState.update { it.copy(note = text) }
    fun toggleSocExpanded() = _uiState.update { it.copy(socExpanded = !it.socExpanded, socError = null) }
    fun setSocBefore(text: String) = _uiState.update { st ->
        val updated = st.copy(socBeforeText = text, socError = null)
        when {
            updated.kwhCalculatorActive -> recomputeKwhFromSoc(updated)
            else -> tryAutoActivateCalculator(updated)
        }
    }
    fun setSocAfter(text: String) = _uiState.update { st ->
        val updated = st.copy(socAfterText = text, socError = null)
        when {
            updated.kwhCalculatorActive -> recomputeKwhFromSoc(updated)
            else -> tryAutoActivateCalculator(updated)
        }
    }

    /**
     * TASK-43: opt-in entry point for the SoC-based kWh calculator.
     * Expands the SoC card if collapsed, marks the calculator active, and
     * pre-derives the kWh field if both SoC values are already present.
     * The calculator stays active until the user manually edits the kWh
     * field (handled in [setKwh]).
     *
     * Note: post-auto-derive (2026-05-03), filling both SoC fields with kWh
     * blank already activates the calculator implicitly via
     * [tryAutoActivateCalculator]. This explicit entry point survives so
     * users can still trigger the calculator by tapping the form's
     * "Calculate from SoC %" link — useful when they want to overwrite an
     * already-filled kWh value with the SoC-derived one.
     */
    fun onCalculateKwhFromSoc() = _uiState.update { st ->
        if (st.nominalBatteryKwh == null) return@update st // safety net
        val activated = st.copy(
            socExpanded = true,
            kwhCalculatorActive = true,
            kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
            kwhError = null,
        )
        recomputeKwhFromSoc(activated)
    }

    /**
     * Auto-activate the SoC calculator when the user has supplied both SoC
     * fields with a valid range and the kWh field is blank. The auto-fill
     * is visible (kWh appears live as the user types SoC), so unlike the
     * silent save-time auto-derive considered (and rejected) in TASK-43,
     * this never surprises the user — manual edits to kWh after the fact
     * still flip provenance back to `MEASURED` via [setKwh]'s existing
     * override semantic.
     *
     * Returns the input state unchanged when any precondition fails so a
     * non-blank kWh is never overwritten silently and partial / invalid
     * SoC entries don't churn the kWh field mid-keystroke.
     */
    private fun tryAutoActivateCalculator(state: ChargeEditUiState): ChargeEditUiState {
        if (state.kwh.isNotBlank()) return state
        if (state.nominalBatteryKwh == null) return state
        val before = state.socBeforeText.trim().toDoubleOrNull() ?: return state
        val after = state.socAfterText.trim().toDoubleOrNull() ?: return state
        if (before !in 0.0..100.0 || after !in 0.0..100.0) return state
        if (after <= before) return state
        return recomputeKwhFromSoc(
            state.copy(
                kwhCalculatorActive = true,
                kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
                kwhError = null,
            ),
        )
    }

    /**
     * Re-derives the kWh field from the current SoC inputs and the active
     * car's nominal capacity. Returns the original state unchanged when
     * SoC inputs are incomplete or unparseable so the user can keep typing
     * without partial values overwriting the kWh field mid-keystroke.
     */
    private fun recomputeKwhFromSoc(state: ChargeEditUiState): ChargeEditUiState {
        val nominal = state.nominalBatteryKwh ?: return state
        val before = state.socBeforeText.trim().toDoubleOrNull()
        val after = state.socAfterText.trim().toDoubleOrNull()
        if (before == null || after == null) return state
        if (before !in 0.0..100.0 || after !in 0.0..100.0) return state
        val derived = KwhFromSocCalculator.compute(
            socBefore = before / 100.0,
            socAfter = after / 100.0,
            nominalBatteryKwh = nominal,
        )
        return state.copy(kwh = formatKwh(derived))
    }

    /**
     * Format the derived kWh for display in the kWh `TextInputEditText`.
     * Strips the trailing `.0` for whole-number values so the field reads
     * `36` rather than `36.0`. Mirrors [Double.toPercentText].
     */
    private fun formatKwh(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }

    private sealed class SocParseResult {
        object None : SocParseResult()
        data class Both(val before: Double, val after: Double) : SocParseResult()
        data class Error(val messageRes: Int) : SocParseResult()
    }

    /**
     * SoC inputs are valid if (a) both blank — drop them — or (b) both
     * non-blank, parseable, in `[0, 100]`, with `after > before`. Any other
     * combination is an error. Saved values convert from `0..100` percent to
     * the entity's `0.0..1.0` fraction.
     */
    private fun parseSoc(state: ChargeEditUiState): SocParseResult {
        if (!state.socExpanded) return SocParseResult.None
        val beforeText = state.socBeforeText.trim()
        val afterText = state.socAfterText.trim()
        if (beforeText.isEmpty() && afterText.isEmpty()) return SocParseResult.None
        if (beforeText.isEmpty() || afterText.isEmpty()) {
            return SocParseResult.Error(R.string.error_soc_both_required)
        }
        val before = beforeText.toDoubleOrNull()
        val after = afterText.toDoubleOrNull()
        if (before == null || after == null || before !in 0.0..100.0 || after !in 0.0..100.0) {
            return SocParseResult.Error(R.string.error_soc_range)
        }
        if (after <= before) return SocParseResult.Error(R.string.error_soc_after_must_exceed_before)
        return SocParseResult.Both(before = before / 100.0, after = after / 100.0)
    }

    fun save() {
        val state = _uiState.value
        val odoText = state.odometer.trim()
        val odoDouble = odoText.toDoubleOrNull()
        if (odoText.isBlank() || odoDouble == null || odoDouble <= 0.0) {
            _uiState.update { it.copy(odometerError = R.string.error_odometer_required, saving = false) }
            return
        }
        val kwhText = state.kwh.trim()
        val kwhDouble = kwhText.toDoubleOrNull()
        if (kwhText.isBlank() || kwhDouble == null || kwhDouble <= 0.0) {
            _uiState.update { it.copy(kwhError = R.string.error_kwh_required, saving = false) }
            return
        }
        val odoKm = if (state.distanceUnit == "miles") UnitConverter.milesToKm(odoDouble) else odoDouble
        val costInput: CostInput? = if (state.costExpanded) {
            val v = state.costValue.trim().toDoubleOrNull()
            if (v != null && v > 0.0) CostInput(value = v, mode = state.costMode, currency = state.currency) else null
        } else {
            null
        }
        val socResult = parseSoc(state)
        if (socResult is SocParseResult.Error) {
            _uiState.update { it.copy(saving = false, socError = socResult.messageRes) }
            return
        }
        val (socBeforeFraction, socAfterFraction) = when (socResult) {
            is SocParseResult.Both -> socResult.before to socResult.after
            else -> null to null
        }
        val input = SaveChargeEventInput(
            eventId = (state.mode as? ChargeEditUiState.Mode.Edit)?.eventId,
            carId = state.carId,
            eventDate = state.eventDateMillis,
            odometerKm = odoKm,
            kwhAdded = kwhDouble,
            chargeType = state.chargeType,
            costInput = costInput,
            location = state.location.ifBlank { null },
            note = state.note,
            socBefore = socBeforeFraction,
            socAfter = socAfterFraction,
            kwhSource = state.kwhSource,
        )
        _uiState.update { it.copy(saving = true, odometerError = null, kwhError = null, socError = null) }
        viewModelScope.launch {
            when (saveChargeEvent(input)) {
                is SaveChargeEventResult.Success -> {
                    _uiState.update { it.copy(saving = false) }
                    _events.tryEmit(ChargeEditEvent.SavedAndExit)
                }
                SaveChargeEventResult.OdometerNotIncreasing -> {
                    _uiState.update { it.copy(saving = false, odometerError = R.string.error_odometer_must_be_higher) }
                }
            }
        }
    }
}
