package org.spsl.evtracker.data.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.LocationWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val customLocationDao: CustomLocationDao,
) : LocationReader, LocationWriter {
    override fun observeTop5(): Flow<List<CustomLocationEntity>> = customLocationDao.observeTop5()
    override fun observeAll(): Flow<List<CustomLocationEntity>> = customLocationDao.observeAll()

    override suspend fun recordUsage(label: String, now: Long) =
        customLocationDao.recordUsage(label, now)

    override suspend fun delete(location: CustomLocationEntity) = customLocationDao.delete(location)
    override suspend fun deleteAll() = customLocationDao.deleteAll()
}
