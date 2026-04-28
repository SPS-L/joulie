package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository

class RenameCarUseCaseTest {

    @Test
    fun nameBlank_returnsError() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1, name = "Old", createdAt = 0L)))
        val scheduler = FakeBackupScheduler()
        val useCase = RenameCarUseCase(cars, scheduler)
        val result = useCase(carId = 1, newName = "")
        assertTrue(result is RenameCarUseCase.Result.NameBlank)
        assertEquals("Old", cars.current().single().name)
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    fun success_renamesAndEnqueues() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1, name = "Old", createdAt = 0L)))
        val scheduler = FakeBackupScheduler()
        val useCase = RenameCarUseCase(cars, scheduler)
        val result = useCase(carId = 1, newName = "New")
        assertTrue(result is RenameCarUseCase.Result.Success)
        assertEquals("New", cars.current().single().name)
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun nameTrimmed() = runTest {
        val cars = FakeCarRepository(initial = listOf(CarEntity(id = 1, name = "Old", createdAt = 0L)))
        val useCase = RenameCarUseCase(cars, FakeBackupScheduler())
        useCase(carId = 1, newName = "  Trimmed  ")
        assertEquals("Trimmed", cars.current().single().name)
    }
}
