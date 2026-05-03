// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.CarEntity

data class CarsUiState(
    val cars: List<CarRow> = emptyList(),
    val activeCarId: Long = -1L,
) {
    val empty: Boolean get() = cars.isEmpty()

    data class CarRow(val car: CarEntity, val isActive: Boolean)
}

sealed class CarsEvent {
    object ShowAddDialog : CarsEvent()
    data class ShowEditDialog(val car: CarEntity) : CarsEvent()
    data class ShowDeleteConfirm(val car: CarEntity) : CarsEvent()
    data class ShowError(val messageRes: Int) : CarsEvent()
}
