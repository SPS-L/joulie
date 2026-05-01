package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class DeleteChargeEventUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend operator fun invoke(event: ChargeEventEntity) {
        chargeEventWriter.delete(event)
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
    }
}
