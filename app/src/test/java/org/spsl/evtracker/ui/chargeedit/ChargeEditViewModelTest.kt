package org.spsl.evtracker.ui.chargeedit

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeEditEvent
import org.spsl.evtracker.core.model.ChargeEditUiState
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.testing.FakeSaveChargeEventGateway
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeEditViewModelTest {

    @Before fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun build(
        eventId: Int = -1,
        gateway: FakeSaveChargeEventGateway = FakeSaveChargeEventGateway(),
        distanceUnit: String = "km",
        currency: String = "EUR",
        activeCarId: Int = 1,
        customLocations: List<CustomLocationEntity> = emptyList(),
    ): VmFixture {
        gateway.locationReader.state.value = customLocations
        val freshReader = FakeSettingsReader(
            activeCarIdInit = activeCarId,
            primaryMetricInit = "km_per_kwh",
            distanceUnitInit = distanceUnit,
            currencyInit = currency,
        )
        val savedStateHandle = SavedStateHandle(mapOf("eventId" to eventId))
        val vm = ChargeEditViewModel(
            saveChargeEvent = gateway.useCase,
            locationReader = gateway.locationReader,
            chargeEventQueries = gateway.queries,
            settingsReader = freshReader,
            now = gateway.nowProvider,
            savedStateHandle = savedStateHandle,
        )
        return VmFixture(vm, gateway)
    }

    private data class VmFixture(val vm: ChargeEditViewModel, val gateway: FakeSaveChargeEventGateway)

    @Test
    fun createMode_blankInitialState() = runTest {
        val (vm, _) = build(eventId = -1)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create && it.carId == 1 }
        assertEquals(1, state.carId)
        assertEquals("", state.odometer)
        assertEquals("", state.kwh)
    }

    @Test
    fun editMode_loadsExistingEventAndPreFills() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 7, carId = 3, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 20.0,
                    chargeType = "DC", costTotal = 5.0, costPerKwh = 0.25, currency = "EUR",
                    location = "Home", note = "test", createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = 7, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertEquals(3, state.carId)
        assertEquals("100.0", state.odometer)
        assertEquals("20.0", state.kwh)
        assertEquals("DC", state.chargeType)
        assertTrue(state.costExpanded)
        assertEquals("5.0", state.costValue)
        assertEquals("Home", state.location)
        assertEquals("test", state.note)
    }

    @Test
    fun editMode_milesUnit_odometerDisplayedInMiles() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 7,
                    carId = 1,
                    eventDate = 0L,
                    odometerKm = 100.0,
                    kwhAdded = 20.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = 7, gateway = gateway, distanceUnit = "miles")
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        // 100 km ≈ 62.137 mi
        assertEquals(62.137, state.odometer.toDouble(), 0.005)
    }

    @Test
    fun save_blankOdometer_setsError() = runTest {
        val (vm, gateway) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setKwh("20")
        vm.save()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(R.string.error_odometer_required, state.odometerError)
        // Use case should not have been called → no event written
        assertTrue(gateway.queries.current().isEmpty())
    }

    @Test
    fun save_blankKwh_setsError() = runTest {
        val (vm, _) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.save()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(R.string.error_kwh_required, state.kwhError)
    }

    @Test
    fun save_costZero_resultsInNullCostInDb() = runTest {
        val (vm, gateway) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.setKwh("20")
        vm.toggleCostExpanded()
        vm.setCostValue("0")
        vm.save()
        advanceUntilIdle()
        val written = gateway.queries.current().firstOrNull { it.carId == 1 }
        assertNotNull(written)
        assertNull(written!!.costTotal)
    }

    @Test
    fun save_costBlank_resultsInNullCostInDb() = runTest {
        val (vm, gateway) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.setKwh("20")
        vm.toggleCostExpanded()
        vm.setCostValue("")
        vm.save()
        advanceUntilIdle()
        val written = gateway.queries.current().firstOrNull { it.carId == 1 }
        assertNotNull(written)
        assertNull(written!!.costTotal)
    }

    @Test
    fun save_costTotalEntered_persistsCost() = runTest {
        val (vm, gateway) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.setKwh("20")
        vm.toggleCostExpanded()
        vm.setCostMode(CostMode.TOTAL)
        vm.setCostValue("5.5")
        vm.save()
        advanceUntilIdle()
        val written = gateway.queries.current().firstOrNull { it.carId == 1 }
        assertNotNull(written)
        assertEquals(5.5, written!!.costTotal!!, 0.001)
        assertEquals("EUR", written.currency)
    }

    @Test
    fun save_useCaseReturnsOdoNotIncreasing_setsError() = runTest {
        val gateway = FakeSaveChargeEventGateway().also { it.nowProvider.time = 1_000L }
        // Seed an existing event at odometer 200 so saving at 100 fails with OdometerNotIncreasing.
        // The previous event must have eventDate < the new event's eventDate to be considered "previous".
        // The new event's eventDateMillis is the FakeNowProvider time (1_000L), so eventDate 0L
        // is "before" it.
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1,
                    carId = 1,
                    eventDate = 0L,
                    odometerKm = 200.0,
                    kwhAdded = 10.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(gateway = gateway)
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.setKwh("20")
        vm.save()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(R.string.error_odometer_must_be_higher, state.odometerError)
    }

    @Test
    fun save_success_emitsSavedAndExitEvent() = runTest {
        val (vm, _) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("100")
        vm.setKwh("20")
        val received = mutableListOf<ChargeEditEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { received += it }
        }
        vm.save()
        advanceUntilIdle()
        job.cancel()
        assertTrue("got $received", received.isNotEmpty() && received.first() is ChargeEditEvent.SavedAndExit)
    }

    @Test
    fun save_milesInput_convertsToKmBeforeUseCaseCall() = runTest {
        val (vm, gateway) = build(distanceUnit = "miles")
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.setOdometer("62.137")
        vm.setKwh("20")
        vm.save()
        advanceUntilIdle()
        val written = gateway.queries.current().firstOrNull { it.carId == 1 }
        assertNotNull(written)
        // 62.137 mi ≈ 100 km
        assertEquals(100.0, written!!.odometerKm, 0.05)
    }

    @Test
    fun selectLocationChip_setsLocationField() = runTest {
        val (vm, _) = build()
        vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        vm.selectLocationChip("Home")
        val state = vm.uiState.first { it.location == "Home" }
        assertEquals("Home", state.location)
    }

    @Test
    fun customLocationsObserved() = runTest {
        val locations = listOf(
            CustomLocationEntity(id = 1, label = "A", useCount = 3, lastUsed = 100L),
            CustomLocationEntity(id = 2, label = "B", useCount = 2, lastUsed = 200L),
        )
        val (vm, _) = build(customLocations = locations)
        val state = vm.uiState.first { it.locationChips.custom.isNotEmpty() }
        assertEquals(listOf("A", "B"), state.locationChips.custom)
    }
}
