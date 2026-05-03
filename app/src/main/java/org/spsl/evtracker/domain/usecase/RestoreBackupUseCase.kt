// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val backupSerializer: BackupSerializer,
    private val transactionRunner: RestoreTransactionRunner,
    private val snapshotWriter: RestoreSnapshotWriter,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val now: NowProvider,
) {
    suspend operator fun invoke(): RestoreResult {
        val json = backupRepository.readRemoteBackup()
        if (json == null) {
            settingsWriter.setDriveEnabled(true)
            backupScheduler.enqueueBackup()
            widgetRefresher.refresh()
            return RestoreResult.NoRemoteBackup
        }

        val parsed = try {
            backupSerializer.fromJson(json)
        } catch (e: BackupVersionMismatch) {
            return RestoreResult.VersionMismatch(e.actual)
        }

        val currentCars = carReader.observeAll().first()
        val currentEvents = currentCars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val currentLocations = locationReader.observeAll().first()
        val snapshot = BackupData.fromEntities(currentCars, currentEvents, currentLocations, now = now.nowMillis())
        snapshotWriter.write(backupSerializer.toJson(snapshot))

        val (newCars, newEvents, newLocations) = parsed.toEntities()
        transactionRunner.replaceAll(newCars, newEvents, newLocations)

        settingsWriter.setDriveEnabled(true)
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()

        return RestoreResult.Success(
            carCount = newCars.size,
            eventCount = newEvents.size,
            locationCount = newLocations.size,
        )
    }
}
