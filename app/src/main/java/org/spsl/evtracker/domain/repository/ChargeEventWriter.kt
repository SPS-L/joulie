// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)

    /** Per-active-car reset. */
    suspend fun deleteForCar(carId: Long)

    /** Global reset. */
    suspend fun deleteAll()
}
