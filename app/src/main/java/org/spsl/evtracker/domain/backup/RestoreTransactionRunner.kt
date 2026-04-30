package org.spsl.evtracker.domain.backup

import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

/**
 * Atomically deletes all rows from cars, charge_events, custom_locations,
 * then inserts the supplied entities. Production: [org.spsl.evtracker.data.backup.RoomRestoreTransactionRunner]
 * wraps `database.withTransaction { … }`. Tests: in-memory fake.
 */
interface RestoreTransactionRunner {
    suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>,
    )
}
