// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.locale.AndroidLocaleApplier
import org.spsl.evtracker.domain.locale.LocaleApplier
import javax.inject.Singleton

/**
 * Single-binding module for [LocaleApplier]. Kept separate from
 * `DomainModule` so instrumented tests can
 * `@UninstallModules(LocaleModule::class)` without dragging in unrelated
 * dependencies (mirrors the `DataResetModule` extraction pattern).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocaleModule {
    @Binds
    @Singleton
    abstract fun bindLocaleApplier(impl: AndroidLocaleApplier): LocaleApplier
}
