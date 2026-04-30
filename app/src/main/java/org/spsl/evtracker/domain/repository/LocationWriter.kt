package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationWriter {
    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis())
    suspend fun delete(location: CustomLocationEntity)

    /** F1: global reset. */
    suspend fun deleteAll()
}
