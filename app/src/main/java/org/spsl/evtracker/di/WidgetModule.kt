// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.widget.AndroidWidgetRefresher
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Singleton

/**
 * Home-screen widget refresh trigger. Kept as its own module so
 * future tests can `@UninstallModules(WidgetModule::class)` and replace
 * the binding with a fake without disturbing other backup wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: AndroidWidgetRefresher): WidgetRefresher
}
