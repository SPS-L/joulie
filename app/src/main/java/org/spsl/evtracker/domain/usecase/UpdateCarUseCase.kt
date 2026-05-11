// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

/**
 * Full-row update for the Edit Car dialog (TASK-91).
 *
 * The pre-TASK-91 flow only persisted the renamed `name` through a
 * dedicated rename use case, make / model / year / battery edits in
 * the dialog were silently discarded. With the EV-database
 * autocomplete promoting Make and Model to canonical values (and
 * auto-filling battery + WLTP), the dialog now needs an honest write
 * path. This use case fans out exactly the same way [AddCarUseCase]
 * does on success, enqueue a Drive backup + refresh the home-screen
 * widget.
 */
class UpdateCarUseCase @Inject constructor(
    private val carReader: CarReader,
    private val carWriter: CarWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend operator fun invoke(carId: Long, form: CarFormState): Result {
        if (form.name.isBlank()) return Result.NameBlank
        val existing = carReader.getById(carId) ?: return Result.NotFound
        val updated = existing.copy(
            name = form.name.trim(),
            make = form.make.trim(),
            model = form.model.trim(),
            year = form.year.toIntOrNull(),
            batteryKwh = form.batteryKwh.toDoubleOrNull(),
            wltpKwhPer100km = form.wltpKwhPer100km,
        )
        carWriter.update(updated)
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        object NameBlank : Result()
        object NotFound : Result()
    }
}
