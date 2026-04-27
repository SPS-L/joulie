package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)
}
