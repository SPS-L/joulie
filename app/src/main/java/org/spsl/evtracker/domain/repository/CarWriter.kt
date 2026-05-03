// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CarEntity

interface CarWriter {
    suspend fun insert(car: CarEntity): Long
    suspend fun rename(carId: Long, newName: String)
    suspend fun deleteById(carId: Long)

    /** F1: global reset. */
    suspend fun deleteAll()
}
