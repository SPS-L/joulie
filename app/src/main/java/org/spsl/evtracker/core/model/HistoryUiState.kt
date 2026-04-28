package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val filter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val distanceUnit: String = "km",
    val activeCarId: Int = -1
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class HistoryRow(
    val event: ChargeEventEntity,
    val displayOdometer: Double,
    val showCost: Boolean,
    val isPendingDelete: Boolean
)

sealed class HistoryEvent {
    data class ShowUndoSnackbar(val eventId: Int) : HistoryEvent()
    data class NavigateToEdit(val eventId: Int) : HistoryEvent()
}
