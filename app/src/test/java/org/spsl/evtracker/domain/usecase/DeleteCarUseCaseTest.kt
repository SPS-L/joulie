package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter
import org.spsl.evtracker.testing.FakeWidgetRefresher

class DeleteCarUseCaseTest {

    private fun useCase(
        cars: FakeCarRepository,
        activeCarId: Long,
        scheduler: FakeBackupScheduler = FakeBackupScheduler(),
        settingsWriter: FakeSettingsWriter = FakeSettingsWriter(),
    ): Triple<DeleteCarUseCase, FakeSettingsWriter, FakeBackupScheduler> {
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val uc = DeleteCarUseCase(cars, cars, reader, settingsWriter, scheduler, FakeWidgetRefresher())
        return Triple(uc, settingsWriter, scheduler)
    }

    @Test
    fun deleteActiveCar_clearsToNextRemaining() = runTest {
        val cars = FakeCarRepository(
            initial = listOf(
                CarEntity(id = 1L, name = "A", createdAt = 0L),
                CarEntity(id = 2L, name = "B", createdAt = 0L),
            ),
        )
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 1L)
        uc(carId = 1L)
        assertEquals(2L, settingsWriter.activeCarId)
    }

    @Test
    fun deleteOnlyCar_clearsToMinusOne() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1L, name = "Only", createdAt = 0L)))
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 1L)
        uc(carId = 1L)
        assertEquals(-1L, settingsWriter.activeCarId)
    }

    @Test
    fun deleteInactiveCar_leavesActiveAlone() = runTest {
        val cars = FakeCarRepository(
            initial = listOf(
                CarEntity(id = 3L, name = "C", createdAt = 0L),
                CarEntity(id = 5L, name = "E", createdAt = 0L),
            ),
        )
        val initialWriter = FakeSettingsWriter()
        val (uc, settingsWriter, _) = useCase(cars, activeCarId = 5L, settingsWriter = initialWriter)
        uc(carId = 3L)
        assertEquals(-1L, settingsWriter.activeCarId)
    }

    @Test
    fun success_enqueuesBackup() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1L, name = "A", createdAt = 0L)))
        val (uc, _, scheduler) = useCase(cars, activeCarId = 1L)
        uc(carId = 1L)
        assertEquals(1, scheduler.enqueueCount)
    }
}
