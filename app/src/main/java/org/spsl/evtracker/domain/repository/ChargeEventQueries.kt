package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventQueries {
    fun observeForCar(carId: Long): Flow<List<ChargeEventEntity>>
    suspend fun getInRange(carId: Long, from: Long, to: Long): List<ChargeEventEntity>
    suspend fun getAllForCarSorted(carId: Long): List<ChargeEventEntity>
    suspend fun getById(id: Long): ChargeEventEntity?
}
