package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

class AddCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val carReader: CarReader,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(form: CarFormState): Result {
        if (form.name.isBlank()) return Result.NameBlank
        val entity = CarEntity(
            name = form.name.trim(),
            make = form.make.trim(),
            model = form.model.trim(),
            year = form.year.toIntOrNull(),
            batteryKwh = form.batteryKwh.toDoubleOrNull()
        )
        val rowId = carWriter.insert(entity)
        if (rowId <= 0L) return Result.PersistenceFailed
        val newId = rowId.toInt()
        if (settingsReader.activeCarId.first() == -1) {
            settingsWriter.setActiveCarId(newId)
        }
        backupScheduler.enqueueBackup()
        return Result.Success(newId)
    }

    sealed class Result {
        data class Success(val id: Int) : Result()
        object NameBlank : Result()
        object PersistenceFailed : Result()
    }
}
