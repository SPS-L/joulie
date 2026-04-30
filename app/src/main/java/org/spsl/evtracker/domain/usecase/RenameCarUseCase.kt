package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarWriter
import javax.inject.Inject

class RenameCarUseCase @Inject constructor(
    private val carWriter: CarWriter,
    private val backupScheduler: BackupScheduler,
) {
    suspend operator fun invoke(carId: Int, newName: String): Result {
        if (newName.isBlank()) return Result.NameBlank
        carWriter.rename(carId, newName.trim())
        backupScheduler.enqueueBackup()
        return Result.Success
    }

    sealed class Result {
        object Success : Result()
        object NameBlank : Result()
    }
}
