package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventQueries {
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>>
    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity>
    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity>
    suspend fun getById(id: Int): ChargeEventEntity?
}
