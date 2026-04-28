package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.backup.AndroidCsvFileSink
import org.spsl.evtracker.data.backup.CacheDirRestoreSnapshotWriter
import org.spsl.evtracker.data.backup.RoomRestoreTransactionRunner
import org.spsl.evtracker.data.repository.CarRepository
import org.spsl.evtracker.data.repository.ChargeEventRepository
import org.spsl.evtracker.data.repository.LocationRepository
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.CsvFileSink
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // Repository interfaces — bound to B's existing implementations.
    @Binds abstract fun bindCarReader(impl: CarRepository): CarReader
    @Binds abstract fun bindCarWriter(impl: CarRepository): CarWriter
    @Binds abstract fun bindChargeEventQueries(impl: ChargeEventRepository): ChargeEventQueries
    @Binds abstract fun bindChargeEventWriter(impl: ChargeEventRepository): ChargeEventWriter
    @Binds abstract fun bindLocationReader(impl: LocationRepository): LocationReader
    @Binds abstract fun bindLocationWriter(impl: LocationRepository): LocationWriter
    @Binds abstract fun bindSettingsReader(impl: SettingsRepository): SettingsReader
    @Binds abstract fun bindSettingsWriter(impl: SettingsRepository): SettingsWriter

    // Backup interfaces — bound to E's real implementations.
    @Binds abstract fun bindBackupScheduler(impl: org.spsl.evtracker.data.backup.WorkManagerBackupScheduler): BackupScheduler
    @Binds abstract fun bindBackupRepository(impl: org.spsl.evtracker.data.backup.DriveBackupRepository): BackupRepository

    // Restore-flow infrastructure.
    @Binds abstract fun bindRestoreTransactionRunner(impl: RoomRestoreTransactionRunner): RestoreTransactionRunner
    @Binds abstract fun bindRestoreSnapshotWriter(impl: CacheDirRestoreSnapshotWriter): RestoreSnapshotWriter

    // CSV export infrastructure.
    @Binds abstract fun bindCsvFileSink(impl: AndroidCsvFileSink): CsvFileSink
}
