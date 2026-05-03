// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.repository

import androidx.room.withTransaction
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [DataResetTransactionRunner].
 *
 * **Destructive operation — must only be reached through
 * [org.spsl.evtracker.domain.usecase.ResetAllDataUseCase].** Hilt binds this
 * class to the [DataResetTransactionRunner] interface in
 * [org.spsl.evtracker.di.DomainModule]; consumers depend on the interface, not
 * on this concrete type, and even the interface is reserved for the use case.
 * See the interface KDoc for the rationale (the `resetInProgress` durable-flag
 * protocol).
 */
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
