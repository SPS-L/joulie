package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.repository.CarReader

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) : CarReader {
    override fun observeAll(): Flow<List<CarEntity>> = carDao.observeAll()
    override suspend fun getById(id: Int): CarEntity? = carDao.getById(id)
    suspend fun insert(car: CarEntity): Long = carDao.insert(car)
    suspend fun update(car: CarEntity) = carDao.update(car)
    suspend fun delete(car: CarEntity) = carDao.delete(car)
}
