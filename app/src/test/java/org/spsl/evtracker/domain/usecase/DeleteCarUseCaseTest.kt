package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

class DeleteCarUseCaseTest {

    private fun useCase(
        cars: FakeCarRepository,
        activeCarId: Int,
        scheduler: FakeBackupScheduler = FakeBackupScheduler(),
        settingsWriter: FakeSettingsWriter = FakeSettingsWriter(),
    ): Triple<DeleteCarUseCase, FakeSettingsWriter, FakeBackupScheduler> {
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val uc = DeleteCarUseCase(cars, cars, reader, settingsWriter, scheduler)
        return Triple(uc, settingsWriter, scheduler)
    }

    @Test
    fun deleteActiveCar_clearsToNextRemaining() = runTest {
        val cars = FakeCarRepository(
            initial = listOf(
                CarEntity(id = 1, name = "A", createdAt = 0L),
                CarEntity(id = 2, name = "B", createdAt = 0L),
            ),
        )
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 1)
        uc(carId = 1)
        assertEquals(2, settingsWriter.activeCarId)
    }

    @Test
    fun deleteOnlyCar_clearsToMinusOne() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1, name = "Only", createdAt = 0L)))
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 1)
        uc(carId = 1)
        assertEquals(-1, settingsWriter.activeCarId)
    }

    @Test
    fun deleteInactiveCar_leavesActiveAlone() = runTest {
        val cars = FakeCarRepository(
            initial = listOf(
                CarEntity(id = 3, name = "C", createdAt = 0L),
                CarEntity(id = 5, name = "E", createdAt = 0L),
            ),
        )
        val initialWriter = FakeSettingsWriter()
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 5, settingsWriter = initialWriter)
        uc(carId = 3)
        assertEquals(-1, settingsWriter.activeCarId)
    }

    @Test
    fun success_enqueuesBackup() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1, name = "A", createdAt = 0L)))
        val (uc, _, scheduler) = useCase(cars, activeCarId = 1)
        uc(carId = 1)
        assertEquals(1, scheduler.enqueueCount)
    }
}
