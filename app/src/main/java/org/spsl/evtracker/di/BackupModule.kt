// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.backup.AndroidDriveAuthManager
import org.spsl.evtracker.data.backup.GoogleDriveRemoteSource
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindDriveAuthManager(impl: AndroidDriveAuthManager): DriveAuthManager

    @Binds
    @Singleton
    abstract fun bindDriveRemoteSource(impl: GoogleDriveRemoteSource): DriveRemoteSource
}
