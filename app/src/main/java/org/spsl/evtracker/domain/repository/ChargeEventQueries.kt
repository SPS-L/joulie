// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventQueries {
    fun observeForCar(carId: Long): Flow<List<ChargeEventEntity>>
    suspend fun getInRange(carId: Long, from: Long, to: Long): List<ChargeEventEntity>
    suspend fun getAllForCarSorted(carId: Long): List<ChargeEventEntity>
    suspend fun getById(id: Long): ChargeEventEntity?
}
