package org.spsl.evtracker.ui.chargeedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import org.spsl.evtracker.core.model.CostInput
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.domain.service.UnitConverter
import org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase

@HiltViewModel
class ChargeEditViewModel @Inject constructor(
    private val saveChargeEvent: SaveChargeEventUseCase,
    locationReader: LocationReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargeEditUiState())
    val uiState: StateFlow<ChargeEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChargeEditEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ChargeEditEvent> = _events.asSharedFlow()

    init {
        val rawId = savedStateHandle.get<Int>("eventId") ?: -1
        viewModelScope.launch {
            val activeCarId = settingsReader.activeCarId.first()
            val unit = settingsReader.distanceUnit.first()
            val ccy = settingsReader.currency.first()
            if (rawId == -1) {
                _uiState.value = ChargeEditUiState(
                    mode = ChargeEditUiState.Mode.Create,
                    carId = activeCarId,
                    distanceUnit = unit,
                    currency = ccy
                )
            } else {
                val event = chargeEventQueries.getById(rawId)
                if (event == null) {
                    _uiState.value = ChargeEditUiState(
                        mode = ChargeEditUiState.Mode.Create,
                        carId = activeCarId,
                        distanceUnit = unit,
                        currency = ccy
                    )
                } else {
                    val displayOdo = if (unit == "miles")
                        UnitConverter.kmToMiles(event.odometerKm)
                    else event.odometerKm
                    val costExpanded = event.costTotal != null
                    val costValue = event.costTotal?.toString() ?: ""
                    _uiState.value = ChargeEditUiState(
                        mode = ChargeEditUiState.Mode.Edit(rawId),
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
                        currency = event.currency ?: ccy
                    )
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

    fun setEventDate(millis: Long) = _uiState.update { it.copy(eventDateMillis = millis) }
    fun setOdometer(text: String) = _uiState.update { it.copy(odometer = text, odometerError = null) }
    fun setKwh(text: String) = _uiState.update { it.copy(kwh = text, kwhError = null) }
    fun setChargeType(type: String) = _uiState.update { it.copy(chargeType = type) }
    fun selectLocationChip(label: String) = _uiState.update { it.copy(location = label) }
    fun setLocation(text: String) = _uiState.update { it.copy(location = text) }
    fun toggleCostExpanded() = _uiState.update { it.copy(costExpanded = !it.costExpanded) }
    fun setCostMode(mode: CostMode) = _uiState.update { it.copy(costMode = mode) }
    fun setCostValue(text: String) = _uiState.update { it.copy(costValue = text) }
    fun setNote(text: String) = _uiState.update { it.copy(note = text) }

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
        } else null
        val input = SaveChargeEventInput(
            eventId = (state.mode as? ChargeEditUiState.Mode.Edit)?.eventId,
            carId = state.carId,
            eventDate = state.eventDateMillis,
            odometerKm = odoKm,
            kwhAdded = kwhDouble,
            chargeType = state.chargeType,
            costInput = costInput,
            location = state.location.ifBlank { null },
            note = state.note
        )
        _uiState.update { it.copy(saving = true, odometerError = null, kwhError = null) }
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
