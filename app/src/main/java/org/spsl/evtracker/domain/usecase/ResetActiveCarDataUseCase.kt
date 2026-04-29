package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventWriter

class ResetActiveCarDataUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(carId: Int) {
        require(carId != -1) { "ResetActiveCarDataUseCase called with carId=-1" }
        chargeEventWriter.deleteForCar(carId)
        backupScheduler.enqueueBackup()
    }
}
