package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

class AddCarUseCaseTest {

    private fun setup(activeCarId: Int = -1, initialCars: List<org.spsl.evtracker.data.local.entity.CarEntity> = emptyList()): Quadruple {
        val cars = FakeCarRepository(initial = initialCars)
        val settingsReader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val settingsWriter = FakeSettingsWriter()
        val scheduler = FakeBackupScheduler()
        val useCase = AddCarUseCase(cars, settingsReader, settingsWriter, scheduler)
        return Quadruple(useCase, cars, settingsWriter, scheduler)
    }

    private data class Quadruple(
        val useCase: AddCarUseCase,
        val cars: FakeCarRepository,
        val settingsWriter: FakeSettingsWriter,
        val scheduler: FakeBackupScheduler,
    )

    @Test
    fun nameBlank_returnsError() = runTest {
        val (useCase, cars, _, scheduler) = setup()
        val result = useCase(CarFormState(name = ""))
        assertTrue(result is AddCarUseCase.Result.NameBlank)
        assertTrue(cars.current().isEmpty())
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    fun firstCar_setsActiveCarId() = runTest {
        val (useCase, _, settingsWriter, _) = setup(activeCarId = -1)
        val result = useCase(CarFormState(name = "Tesla"))
        assertTrue(result is AddCarUseCase.Result.Success)
        assertEquals(1, settingsWriter.activeCarId)
    }

    @Test
    fun secondCar_doesNotChangeActive() = runTest {
        val existing = org.spsl.evtracker.data.local.entity.CarEntity(id = 5, name = "Old", createdAt = 0L)
        val (useCase, _, settingsWriter, _) = setup(activeCarId = 5, initialCars = listOf(existing))
        useCase(CarFormState(name = "New"))
        assertEquals(-1, settingsWriter.activeCarId) // FakeSettingsWriter default; setActiveCarId never called
    }

    @Test
    fun success_enqueuesBackup() = runTest {
        val (useCase, _, _, scheduler) = setup()
        useCase(CarFormState(name = "Tesla"))
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun nameTrimmed() = runTest {
        val (useCase, cars, _, _) = setup()
        useCase(CarFormState(name = "  Tesla  "))
        assertEquals("Tesla", cars.current().single().name)
    }

    @Test
    fun numericFieldsParseLeniently() = runTest {
        val (useCase, cars, _, _) = setup()
        useCase(CarFormState(name = "Tesla", year = "abc", batteryKwh = ""))
        val inserted = cars.current().single()
        assertNull(inserted.year)
        assertNull(inserted.batteryKwh)
    }
}
