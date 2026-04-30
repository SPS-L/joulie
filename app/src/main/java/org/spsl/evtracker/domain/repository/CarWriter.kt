package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CarEntity

interface CarWriter {
    suspend fun insert(car: CarEntity): Long
    suspend fun rename(carId: Int, newName: String)
    suspend fun deleteById(carId: Int)

    /** F1: global reset. */
    suspend fun deleteAll()
}
