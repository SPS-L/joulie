package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class AddCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val now: NowProvider,
) {
    suspend operator fun invoke(form: CarFormState): Result {
        if (form.name.isBlank()) return Result.NameBlank
        val entity = CarEntity(
            name = form.name.trim(),
            make = form.make.trim(),
            model = form.model.trim(),
            year = form.year.toIntOrNull(),
            batteryKwh = form.batteryKwh.toDoubleOrNull(),
            createdAt = now.nowMillis(),
        )
        val newId = carWriter.insert(entity)
        if (newId <= 0L) return Result.PersistenceFailed
        if (settingsReader.activeCarId.first() == -1L) {
            settingsWriter.setActiveCarId(newId)
        }
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
        return Result.Success(newId)
    }

    sealed class Result {
        data class Success(val id: Long) : Result()
        object NameBlank : Result()
        object PersistenceFailed : Result()
    }
}
