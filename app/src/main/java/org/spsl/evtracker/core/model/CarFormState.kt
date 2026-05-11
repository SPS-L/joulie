// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class CarFormState(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val batteryKwh: String = "",
    /**
     * Manufacturer WLTP reference in kWh/100 km, populated when the user
     * picks a vehicle from the EV-database autocomplete (TASK-91). Stays
     * `null` when the user types fields manually or when the picked
     * vehicle has no upstream WLTP figure. Persisted to
     * [org.spsl.evtracker.data.local.entity.CarEntity.wltpKwhPer100km]
     * on save.
     */
    val wltpKwhPer100km: Double? = null,
)
