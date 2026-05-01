package org.spsl.evtracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.HistoryEvent
import org.spsl.evtracker.core.model.HistoryRow
import org.spsl.evtracker.core.model.HistoryUiState
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.UnitConverter
import org.spsl.evtracker.domain.usecase.DeleteChargeEventUseCase
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val chargeEventQueries: ChargeEventQueries,
    private val deleteChargeEvent: DeleteChargeEventUseCase,
    settingsReader: SettingsReader,
) : ViewModel() {

    private data class PendingDelete(val event: ChargeEventEntity, val job: Job)

    private val pendingDeletes = MutableStateFlow<Map<Int, PendingDelete>>(emptyMap())
    private val filter = MutableStateFlow(ChargeTypeFilter.ALL)

    private val _events = MutableSharedFlow<HistoryEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    private data class Inputs(
        val activeCarId: Int,
        val distanceUnit: String,
        val filter: ChargeTypeFilter,
        val pending: Map<Int, PendingDelete>,
    )

    val uiState: StateFlow<HistoryUiState> = combine(
        settingsReader.activeCarId,
        settingsReader.distanceUnit,
        filter,
        pendingDeletes,
    ) { active, unit, f, pending -> Inputs(active, unit, f, pending) }
        .flatMapLatest { inputs ->
            if (inputs.activeCarId == -1) {
                flowOf(
                    HistoryUiState(
                        activeCarId = -1,
                        distanceUnit = inputs.distanceUnit,
                        filter = inputs.filter,
                    ),
                )
            } else {
                chargeEventQueries.observeForCar(inputs.activeCarId).map { events ->
                    val visible = events
                        .filter { applyFilter(it, inputs.filter) }
                        .sortedByDescending { it.eventDate }
                    HistoryUiState(
                        rows = visible.map { e ->
                            HistoryRow(
                                event = e,
                                displayOdometer = if (inputs.distanceUnit == "miles") {
                                    UnitConverter.kmToMiles(e.odometerKm)
                                } else {
                                    e.odometerKm
                                },
                                showCost = e.costTotal != null && e.currency != null,
                                isPendingDelete = e.id in inputs.pending.keys,
                            )
                        },
                        filter = inputs.filter,
                        distanceUnit = inputs.distanceUnit,
                        activeCarId = inputs.activeCarId,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private fun applyFilter(event: ChargeEventEntity, filter: ChargeTypeFilter): Boolean = when (filter) {
        ChargeTypeFilter.ALL -> true
        ChargeTypeFilter.AC -> event.chargeType == ChargeType.AC
        ChargeTypeFilter.DC -> event.chargeType.isDc
    }

    fun setFilter(newFilter: ChargeTypeFilter) {
        filter.value = newFilter
    }

    fun onSwipeDelete(event: ChargeEventEntity) {
        val id = event.id
        val job = viewModelScope.launch {
            delay(5_000)
            deleteChargeEvent(event)
            pendingDeletes.update { it - id }
        }
        pendingDeletes.update { it + (id to PendingDelete(event, job)) }
        _events.tryEmit(HistoryEvent.ShowUndoSnackbar(id))
    }

    fun onUndoDelete(eventId: Int) {
        pendingDeletes.value[eventId]?.job?.cancel()
        pendingDeletes.update { it - eventId }
    }

    fun onRowClick(eventId: Int) {
        _events.tryEmit(HistoryEvent.NavigateToEdit(eventId))
    }
}
