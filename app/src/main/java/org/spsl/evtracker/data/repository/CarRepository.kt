// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao,
) : CarReader, CarWriter {
    override fun observeAll(): Flow<List<CarEntity>> = carDao.observeAll()
    override suspend fun getById(id: Long): CarEntity? = carDao.getById(id)
    override suspend fun insert(car: CarEntity): Long = carDao.insert(car)
    override suspend fun update(car: CarEntity) = carDao.update(car)
    suspend fun delete(car: CarEntity) = carDao.delete(car)
    override suspend fun rename(carId: Long, newName: String) = carDao.rename(carId, newName)
    override suspend fun deleteById(carId: Long) {
        carDao.deleteById(carId)
    }
    override suspend fun deleteAll() = carDao.deleteAll()
}
