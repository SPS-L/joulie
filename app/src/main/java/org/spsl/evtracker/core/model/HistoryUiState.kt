// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

data class HistoryUiState(
    val rows: List<HistoryRow> = emptyList(),
    val filter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val distanceUnit: String = "km",
    val activeCarId: Long = -1L,
    /**
     * TASK-82: gates the per-row "X kg CO₂ · Y g/kWh" line. When the user
     * toggles CO₂ tracking off, the line disappears across the list
     * even though the data is still on the entity — re-enabling brings
     * it back without re-saving.
     */
    val co2Enabled: Boolean = false,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class HistoryRow(
    val event: ChargeEventEntity,
    val displayOdometer: Double,
    val showCost: Boolean,
    val isPendingDelete: Boolean,
)

sealed class HistoryEvent {
    data class ShowUndoSnackbar(val eventId: Long) : HistoryEvent()
    data class NavigateToEdit(val eventId: Long) : HistoryEvent()
}
