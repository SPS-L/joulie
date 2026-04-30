package org.spsl.evtracker.data.repository

import androidx.room.withTransaction
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDataResetTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : DataResetTransactionRunner {
    override suspend fun clearAllTables() {
        database.withTransaction {
            database.chargeEventDao().deleteAll()
            database.customLocationDao().deleteAll()
            database.carDao().deleteAll()
        }
    }
}
