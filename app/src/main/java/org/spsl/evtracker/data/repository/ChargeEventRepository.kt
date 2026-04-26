package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao
) {
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>> =
        chargeEventDao.observeForCar(carId)

    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity> =
        chargeEventDao.getInRange(carId, from, to)

    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity> =
        chargeEventDao.getAllForCarSorted(carId)

    suspend fun getById(id: Int): ChargeEventEntity? = chargeEventDao.getById(id)
    suspend fun insert(event: ChargeEventEntity): Long = chargeEventDao.insert(event)
    suspend fun update(event: ChargeEventEntity) = chargeEventDao.update(event)
    suspend fun delete(event: ChargeEventEntity) = chargeEventDao.delete(event)
}
