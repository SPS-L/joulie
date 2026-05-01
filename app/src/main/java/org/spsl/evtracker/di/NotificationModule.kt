package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.notification.AndroidBackupNotifier
import org.spsl.evtracker.domain.notification.BackupNotifier
import javax.inject.Singleton

/**
 * Kept separate from [BackupModule] so the existing instrumented
 * `DriveBackupWorkerTest` can `@UninstallModules(BackupModule::class)`
 * without losing the notifier binding (which is independent of Drive).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindBackupNotifier(impl: AndroidBackupNotifier): BackupNotifier
}
