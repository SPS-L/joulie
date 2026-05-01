package org.spsl.evtracker.data.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao,
) : ChargeEventQueries, ChargeEventWriter {
    override fun observeForCar(carId: Long): Flow<List<ChargeEventEntity>> =
        chargeEventDao.observeForCar(carId)

    override suspend fun getInRange(carId: Long, from: Long, to: Long): List<ChargeEventEntity> =
        chargeEventDao.getInRange(carId, from, to)

    override suspend fun getAllForCarSorted(carId: Long): List<ChargeEventEntity> =
        chargeEventDao.getAllForCarSorted(carId)

    override suspend fun getById(id: Long): ChargeEventEntity? = chargeEventDao.getById(id)
    override suspend fun insert(event: ChargeEventEntity): Long = chargeEventDao.insert(event)
    override suspend fun update(event: ChargeEventEntity) = chargeEventDao.update(event)
    override suspend fun delete(event: ChargeEventEntity) = chargeEventDao.delete(event)
    override suspend fun deleteForCar(carId: Long) = chargeEventDao.deleteForCar(carId)
    override suspend fun deleteAll() = chargeEventDao.deleteAll()
}
