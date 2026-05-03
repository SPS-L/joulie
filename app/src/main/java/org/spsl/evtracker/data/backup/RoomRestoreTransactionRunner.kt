// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.backup

import androidx.room.withTransaction
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRestoreTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : RestoreTransactionRunner {
    override suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>,
    ) {
        database.withTransaction {
            database.chargeEventDao().deleteAll()
            database.customLocationDao().deleteAll()
            database.carDao().deleteAll()

            cars.forEach { database.carDao().insert(it) }
            events.forEach { database.chargeEventDao().insert(it) }
            locations.forEach { database.customLocationDao().insertIfMissing(it) }
        }
    }
}
