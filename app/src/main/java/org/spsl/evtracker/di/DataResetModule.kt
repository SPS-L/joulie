// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner

/**
 * Single-binding module so instrumented tests covering the startup
 * auto-recovery flow (e.g. `MainActivityResetRecoveryTest`) can swap in
 * a spy / failing implementation via `@UninstallModules(DataResetModule::class)`
 * without uninstalling all of [DomainModule]'s bindings.
 *
 * Mirrors the `BackupModule` pattern — keep modules small enough that an
 * `@UninstallModules` call doesn't drag in unrelated dependencies the test
 * has to re-bind by hand.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataResetModule {
    @Binds
    abstract fun bindDataResetTransactionRunner(
        impl: RoomDataResetTransactionRunner,
    ): DataResetTransactionRunner
}
