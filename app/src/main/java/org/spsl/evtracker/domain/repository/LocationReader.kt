package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationReader {
    fun observeTop5(): Flow<List<CustomLocationEntity>>
    fun observeAll(): Flow<List<CustomLocationEntity>>
}
