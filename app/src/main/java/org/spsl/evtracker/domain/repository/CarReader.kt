package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CarEntity

interface CarReader {
    fun observeAll(): Flow<List<CarEntity>>
    suspend fun getById(id: Long): CarEntity?
}
