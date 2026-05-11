package org.spsl.evtracker.ui.cars

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.core.model.CarsEvent
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.domain.usecase.AddCarUseCase
import org.spsl.evtracker.domain.usecase.DeleteCarUseCase
import org.spsl.evtracker.domain.usecase.UpdateCarUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeEvModelReader
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class CarsViewModelTest {

    @Before fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun build(
        cars: List<CarEntity> = emptyList(),
        activeCarId: Long = -1L,
    ): VmFixture {
        val repo = FakeCarRepository(initial = cars)
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val writer = FakeSettingsWriter()
        val scheduler = FakeBackupScheduler()
        val widgetRefresher = org.spsl.evtracker.testing.FakeWidgetRefresher()
        val add = AddCarUseCase(repo, reader, writer, scheduler, widgetRefresher, FakeNowProvider())
        val update = UpdateCarUseCase(repo, repo, scheduler, widgetRefresher)
        val delete = DeleteCarUseCase(repo, repo, reader, writer, scheduler, widgetRefresher)
        val evModels = FakeEvModelReader()
        val vm = CarsViewModel(repo, reader, writer, add, update, delete, evModels)
        return VmFixture(vm, repo, writer, scheduler)
    }

    private data class VmFixture(
        val vm: CarsViewModel,
        val repo: FakeCarRepository,
        val settingsWriter: FakeSettingsWriter,
        val scheduler: FakeBackupScheduler,
    )

    @Test
    fun noCars_emitsEmpty() = runTest {
        val (vm, _, _, _) = build()
        val state = vm.uiState.first()
        assertTrue(state.empty)
    }

    @Test
    fun marksActiveCarInList() = runTest {
        val cars = listOf(
            CarEntity(id = 1L, name = "A", createdAt = 0L),
            CarEntity(id = 2L, name = "B", createdAt = 0L),
        )
        val (vm, _, _, _) = build(cars = cars, activeCarId = 2L)
        val state = vm.uiState.first { it.cars.size == 2 }
        assertEquals(2, state.activeCarId)
        assertFalse(state.cars[0].isActive)
        assertTrue(state.cars[1].isActive)
    }

    @Test
    fun addNameBlank_emitsErrorEvent() = runTest {
        val (vm, _, _, _) = build()
        val received = mutableListOf<CarsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.submitAdd(CarFormState(name = ""))
        advanceUntilIdle()
        job.cancel()
        assertTrue("got $received", received.isNotEmpty())
        val event = received.first()
        assertTrue(event is CarsEvent.ShowError)
        assertEquals(R.string.error_car_name_required, (event as CarsEvent.ShowError).messageRes)
    }

    @Test
    fun setActive_writesToSettings() = runTest {
        val (vm, _, settingsWriter, _) = build()
        vm.onRowSetActiveClick(7)
        advanceUntilIdle()
        assertEquals(7, settingsWriter.activeCarId)
    }

    @Test
    fun submitAdd_success_doesNotEmitError() = runTest {
        val (vm, _, _, _) = build()
        val received = mutableListOf<CarsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.submitAdd(CarFormState(name = "Tesla"))
        advanceUntilIdle()
        job.cancel()
        // Should NOT have emitted ShowError
        assertNull(received.firstOrNull { it is CarsEvent.ShowError })
    }
}
