// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class RenameCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend operator fun invoke(carId: Long, newName: String): Result {
        if (newName.isBlank()) return Result.NameBlank
        carWriter.rename(carId, newName.trim())
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        object NameBlank : Result()
    }
}
