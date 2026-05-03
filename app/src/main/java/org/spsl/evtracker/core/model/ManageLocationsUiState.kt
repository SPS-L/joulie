// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

data class ManageLocationsUiState(
    val locations: List<CustomLocationEntity> = emptyList(),
    /** Labels currently in their 5-second cancel window. Filtered out of the visible list. */
    val pendingDeletions: Set<String> = emptySet(),
) {
    /**
     * The list the Fragment renders, AND the source of truth for the empty-state.
     * If the last row was just swiped, this is empty during the 5s undo window so
     * the empty-state shows immediately rather than leaving a blank RecyclerView.
     */
    val visibleLocations: List<CustomLocationEntity>
        get() = locations.filter { it.label !in pendingDeletions }
}

sealed class ManageLocationsEvent {
    data class ShowUndoSnackbar(val label: String) : ManageLocationsEvent()
}
