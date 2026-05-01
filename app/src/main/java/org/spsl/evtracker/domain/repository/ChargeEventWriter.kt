package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)

    /** F1: per-active-car reset. */
    suspend fun deleteForCar(carId: Long)

    /** F1: global reset. */
    suspend fun deleteAll()
}
