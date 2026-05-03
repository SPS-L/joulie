// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

/**
 * Atomically clears every row from cars, charge_events, and custom_locations.
 *
 * **Destructive operation — must only be called from
 * [org.spsl.evtracker.domain.usecase.ResetAllDataUseCase].** ViewModels, Fragments,
 * Activities, and other use cases must not depend on this interface or its
 * implementation directly. The use case is the only seam that participates in the
 * `resetInProgress` durable-flag protocol that lets startup auto-recovery resume
 * a half-finished reset (see `MainViewModel.runStartupSequence`); calling
 * [clearAllTables] from anywhere else bypasses that protocol and can leave the
 * app in an inconsistent state. The narrow-IF rule (CLAUDE.md §Architecture)
 * already keeps direct `data.repository.*` imports out of the UI layer; this
 * KDoc documents the additional, type-level rule that even the interface is
 * not for general consumption.
 *
 * Production: [org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner] wraps
 * `database.withTransaction { … }` and calls each DAO's `deleteAll()` inside the transaction.
 * Tests: [org.spsl.evtracker.testing.FakeDataResetTransactionRunner] records calls and
 * clears in-memory backing collections.
 */
interface DataResetTransactionRunner {
    suspend fun clearAllTables()
}
