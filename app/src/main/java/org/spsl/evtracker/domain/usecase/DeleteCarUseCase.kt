package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class DeleteCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val carReader: CarReader,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend operator fun invoke(carId: Long) {
        carWriter.deleteById(carId)
        if (settingsReader.activeCarId.first() == carId) {
            val remaining = carReader.observeAll().first()
            settingsWriter.setActiveCarId(remaining.firstOrNull()?.id ?: -1L)
        }
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
    }
}
