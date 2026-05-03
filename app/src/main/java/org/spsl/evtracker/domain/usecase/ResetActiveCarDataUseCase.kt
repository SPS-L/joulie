// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class ResetActiveCarDataUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
) {
    suspend operator fun invoke(carId: Long) {
        require(carId != -1L) { "ResetActiveCarDataUseCase called with carId=-1" }
        chargeEventWriter.deleteForCar(carId)
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()
    }
}
