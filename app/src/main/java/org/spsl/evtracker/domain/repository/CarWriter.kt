// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CarEntity

interface CarWriter {
    suspend fun insert(car: CarEntity): Long
    suspend fun rename(carId: Long, newName: String)

    /**
     * Full-row update for the Edit Car dialog (TASK-91). The caller
     * supplies a [CarEntity] whose `id` matches the existing row, and
     * Room replaces the row in place — name, make, model, year,
     * battery, WLTP, and `createdAt` (typically preserved by the
     * caller).
     */
    suspend fun update(car: CarEntity)
    suspend fun deleteById(carId: Long)

    /** Global reset. */
    suspend fun deleteAll()
}
