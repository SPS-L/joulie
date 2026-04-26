package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Singleton
class LocationRepository @Inject constructor(
    private val customLocationDao: CustomLocationDao
) {
    fun observeTop5(): Flow<List<CustomLocationEntity>> = customLocationDao.observeTop5()
    fun observeAll(): Flow<List<CustomLocationEntity>> = customLocationDao.observeAll()

    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis()) =
        customLocationDao.recordUsage(label, now)

    suspend fun delete(location: CustomLocationEntity) = customLocationDao.delete(location)
}
