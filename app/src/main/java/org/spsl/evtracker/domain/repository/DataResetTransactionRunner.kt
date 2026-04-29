package org.spsl.evtracker.domain.repository

/**
 * Atomically clears every row from cars, charge_events, and custom_locations.
 *
 * Production: [org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner] wraps
 * `database.withTransaction { … }` and calls each DAO's `deleteAll()` inside the transaction.
 * Tests: [org.spsl.evtracker.testing.FakeDataResetTransactionRunner] records calls and
 * clears in-memory backing collections.
 */
interface DataResetTransactionRunner {
    suspend fun clearAllTables()
}
