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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeEditEvent
import org.spsl.evtracker.core.model.ChargeEditUiState
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.testing.FakeCarReader
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
        eventId: Long = -1L,
        gateway: FakeSaveChargeEventGateway = FakeSaveChargeEventGateway(),
        distanceUnit: String = "km",
        currency: String = "EUR",
        activeCarId: Long = 1L,
        nominalBatteryKwh: Double? = null,
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
        val carReader = FakeCarReader(
            initial = listOf(
                CarEntity(
                    id = activeCarId,
                    name = "Test",
                    batteryKwh = nominalBatteryKwh,
                    createdAt = 0L,
                ),
            ),
        )
        val vm = ChargeEditViewModel(
            saveChargeEvent = gateway.useCase,
            locationReader = gateway.locationReader,
            chargeEventQueries = gateway.queries,
            carReader = carReader,
            settingsReader = freshReader,
            now = gateway.nowProvider,
            savedStateHandle = savedStateHandle,
        )
        return VmFixture(vm, gateway)
    }

    private data class VmFixture(val vm: ChargeEditViewModel, val gateway: FakeSaveChargeEventGateway)

    @Test
    fun createMode_blankInitialState() = runTest {
        val (vm, _) = build(eventId = -1L)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create && it.carId == 1L }
        assertEquals(1L, state.carId)
        assertEquals("", state.odometer)
        assertEquals("", state.kwh)
    }

    @Test
    fun editMode_loadsExistingEventAndPreFills() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 7L, carId = 3L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 20.0,
                    chargeType = ChargeType.DC_FAST, costTotal = 5.0, costPerKwh = 0.25, currency = "EUR",
                    location = "Home", note = "test", createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = 7L, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertEquals(3L, state.carId)
        assertEquals("100.0", state.odometer)
        assertEquals("20.0", state.kwh)
        assertEquals(ChargeType.DC_FAST, state.chargeType)
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
                    id = 7L,
                    carId = 1L,
                    eventDate = 0L,
                    odometerKm = 100.0,
                    kwhAdded = 20.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = 7L, gateway = gateway, distanceUnit = "miles")
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
        val written = gateway.queries.current().firstOrNull { it.carId == 1L }
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
        val written = gateway.queries.current().firstOrNull { it.carId == 1L }
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
        val written = gateway.queries.current().firstOrNull { it.carId == 1L }
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
                    id = 1L,
                    carId = 1L,
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
        val written = gateway.queries.current().firstOrNull { it.carId == 1L }
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
            CustomLocationEntity(id = 1L, label = "A", useCount = 3, lastUsed = 100L),
            CustomLocationEntity(id = 2L, label = "B", useCount = 2, lastUsed = 200L),
        )
        val (vm, _) = build(customLocations = locations)
        val state = vm.uiState.first { it.locationChips.custom.isNotEmpty() }
        assertEquals(listOf("A", "B"), state.locationChips.custom)
    }

    // -------------------------------------------------------------------------
    // odometer regression UX (prefill + inline error + Save gate)
    // -------------------------------------------------------------------------

    @Test
    fun createMode_noPreviousEvent_doesNotPrefillOdometer() = runTest {
        val (vm, _) = build()
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create }
        assertEquals("", state.odometer)
        assertNull(state.previousOdometerKm)
        assertNull(state.nextOdometerKm)
        assertFalse(state.odometerBelowPrevious)
        assertFalse(state.odometerAboveNext)
    }

    @Test
    fun createMode_withPreviousEvent_kmUnit_prefillsPrevPlusOne() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 1_000L,
                    odometerKm = 12_345.0,
                    kwhAdded = 20.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = -1, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create && it.odometer.isNotEmpty() }
        assertEquals("12346.0", state.odometer)
        assertEquals(12_345.0, state.previousOdometerKm!!, 0.0)
        assertNull(state.nextOdometerKm)
        assertFalse(
            "prefill (prev + 1 km) must be strictly greater, so flag stays false",
            state.odometerBelowPrevious,
        )
    }

    @Test
    fun createMode_withPreviousEvent_milesUnit_prefillsConverted() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 1_000L,
                    odometerKm = 100.0,
                    kwhAdded = 20.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = -1, gateway = gateway, distanceUnit = "miles")
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Create && it.odometer.isNotEmpty() }
        // (100 + 1) km = 101 km ≈ 62.7558 mi
        val expected = 101.0 / 1.609344
        assertEquals(expected, state.odometer.toDouble(), 0.001)
        assertEquals(100.0, state.previousOdometerKm!!, 0.0)
    }

    @Test
    fun editMode_middleEvent_populatesPreviousAndNextOdometer() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 3L, carId = 1L, eventDate = 3_000L, odometerKm = 300.0, kwhAdded = 10.0, createdAt = 0L),
            ),
        )
        val (vm, _) = build(eventId = 2L, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertEquals(100.0, state.previousOdometerKm!!, 0.0)
        assertEquals(300.0, state.nextOdometerKm!!, 0.0)
        assertFalse(state.odometerBelowPrevious)
        assertFalse(state.odometerAboveNext)
    }

    @Test
    fun editMode_firstEvent_hasNoPrevious() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L),
            ),
        )
        val (vm, _) = build(eventId = 1L, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertNull(state.previousOdometerKm)
        assertEquals(200.0, state.nextOdometerKm!!, 0.0)
    }

    @Test
    fun editMode_lastEvent_hasNoNext() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L),
            ),
        )
        val (vm, _) = build(eventId = 2L, gateway = gateway)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertEquals(100.0, state.previousOdometerKm!!, 0.0)
        assertNull(state.nextOdometerKm)
    }

    @Test
    fun setOdometer_belowPrevious_setsBelowPreviousFlag() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 1_000L,
                    odometerKm = 200.0,
                    kwhAdded = 10.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = -1, gateway = gateway)
        vm.uiState.first { it.previousOdometerKm == 200.0 }
        vm.setOdometer("150")
        val state = vm.uiState.first { it.odometer == "150" }
        assertTrue(state.odometerBelowPrevious)
    }

    @Test
    fun setOdometer_abovePrevious_clearsBelowPreviousFlag() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 1_000L,
                    odometerKm = 200.0,
                    kwhAdded = 10.0,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = -1, gateway = gateway)
        vm.uiState.first { it.previousOdometerKm == 200.0 }
        vm.setOdometer("150")
        vm.uiState.first { it.odometerBelowPrevious }
        vm.setOdometer("250")
        val state = vm.uiState.first { it.odometer == "250" }
        assertFalse(state.odometerBelowPrevious)
    }

    @Test
    fun setOdometer_aboveNext_inEditMode_setsAboveNextFlag() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 3L, carId = 1L, eventDate = 3_000L, odometerKm = 300.0, kwhAdded = 10.0, createdAt = 0L),
            ),
        )
        val (vm, _) = build(eventId = 2L, gateway = gateway)
        vm.uiState.first { it.nextOdometerKm == 300.0 }
        vm.setOdometer("350")
        val state = vm.uiState.first { it.odometer == "350" }
        assertTrue(state.odometerAboveNext)
    }

    // -- — kWh-from-SoC calculator --------------------------------------

    @Test
    fun createMode_loadsNominalBatteryKwhFromActiveCar() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        val state = vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        assertEquals(60.0, state.nominalBatteryKwh!!, 0.0)
        assertEquals(ChargeKwhSource.MEASURED, state.kwhSource)
        assertFalse(state.kwhCalculatorActive)
    }

    @Test
    fun calculator_withSocFieldsPrefilled_derivesKwhAndFlagsDerived() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        // User enters SoC % first, then taps the calculator link.
        vm.toggleSocExpanded()
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        val state = vm.uiState.first { it.kwhCalculatorActive }
        // 60 kWh × (0.80 - 0.20) = 36
        assertEquals("36", state.kwh)
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
        assertTrue(state.socExpanded)
    }

    @Test
    fun calculator_thenSocChange_recomputesKwh() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        vm.uiState.first { it.kwh == "36" }
        // User adjusts the SoC reading — kWh should update live.
        vm.setSocAfter("70")
        val state = vm.uiState.first { it.kwh != "36" }
        // 60 × (0.70 - 0.20) = 30
        assertEquals("30", state.kwh)
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun calculator_userManuallyEditsKwh_revertsToMeasured() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        vm.uiState.first { it.kwh == "36" && it.kwhSource == ChargeKwhSource.DERIVED_FROM_SOC }
        // User overrides the calculator result — flag flips back.
        vm.setKwh("36.5")
        val state = vm.uiState.first { it.kwh == "36.5" }
        assertEquals(ChargeKwhSource.MEASURED, state.kwhSource)
        assertFalse(state.kwhCalculatorActive)
    }

    @Test
    fun setKwh_echoesCurrentText_preservesProvenance() = runTest {
        // The fragment's text-change listener echoes the calculator's own
        // setText() — that echo must NOT clobber the provenance flag.
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        val derived = vm.uiState.first { it.kwh == "36" && it.kwhSource == ChargeKwhSource.DERIVED_FROM_SOC }
        // Simulate the fragment's text listener firing with the unchanged value.
        vm.setKwh(derived.kwh)
        val state = vm.uiState.first { it.kwh == "36" }
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun calculator_recalculate_afterUserEdit_reactivates() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        vm.uiState.first { it.kwhSource == ChargeKwhSource.DERIVED_FROM_SOC }
        vm.setKwh("40")
        vm.uiState.first { it.kwhSource == ChargeKwhSource.MEASURED }
        // Re-tap the calculator → DERIVED again, kWh re-derived.
        vm.onCalculateKwhFromSoc()
        val state = vm.uiState.first { it.kwh == "36" }
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun editMode_loadsExistingKwhSourceFromEntity() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 1_000L,
                    odometerKm = 100.0,
                    kwhAdded = 18.0,
                    socBefore = 0.20,
                    socAfter = 0.50,
                    kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
                    createdAt = 0L,
                ),
            ),
        )
        val (vm, _) = build(eventId = 1L, gateway = gateway, nominalBatteryKwh = 60.0)
        val state = vm.uiState.first { it.mode is ChargeEditUiState.Mode.Edit }
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun save_threadsKwhSourceToInput() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        val (vm, _) = build(gateway = gateway, nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setOdometer("100")
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.onCalculateKwhFromSoc()
        vm.uiState.first { it.kwhSource == ChargeKwhSource.DERIVED_FROM_SOC && it.kwh == "36" }
        vm.save()
        advanceUntilIdle()
        val saved = gateway.queries.current().single()
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, saved.kwhSource)
        // Battery-side kWh was 36 (60 × 0.6).
        assertEquals(36.0, saved.kwhAdded, 1e-6)
    }

    // Auto-activate the SoC calculator when the user has filled both SoC
    // fields with a valid range and kWh is blank. The visible-as-you-type
    // behaviour avoids the silent provenance flip a save-time auto-derive
    // would have had.

    @Test
    fun socFieldsFilledWithBlankKwh_autoActivatesCalculator() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        // No call to onCalculateKwhFromSoc — auto-activation should fire.
        val state = vm.uiState.first { it.kwhCalculatorActive }
        // 60 × (0.80 - 0.20) = 36
        assertEquals("36", state.kwh)
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun socFieldsFilledWithKwhAlreadyPresent_doesNotOverwriteKwh() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        // User types kWh first (measured value from a paper receipt).
        vm.setKwh("42")
        vm.uiState.first { it.kwh == "42" }
        // Then fills SoC for degradation tracking — must NOT overwrite the 42.
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        // Drain the flow a couple of state transitions; the kWh should stay "42"
        // and the calculator must remain inactive.
        val state = vm.uiState.first { it.socBeforeText == "20" && it.socAfterText == "80" }
        assertEquals("42", state.kwh)
        assertFalse(state.kwhCalculatorActive)
        assertEquals(ChargeKwhSource.MEASURED, state.kwhSource)
    }

    @Test
    fun socAfterLessThanBefore_doesNotAutoActivate() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("80")
        vm.setSocAfter("20") // invalid: after < before
        val state = vm.uiState.first { it.socAfterText == "20" }
        assertFalse(state.kwhCalculatorActive)
        assertEquals("", state.kwh)
    }

    @Test
    fun nominalBatteryKwhMissing_doesNotAutoActivate() = runTest {
        val (vm, _) = build(nominalBatteryKwh = null)
        vm.uiState.first { it.nominalBatteryKwh == null }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        val state = vm.uiState.first { it.socAfterText == "80" }
        assertFalse(state.kwhCalculatorActive)
        assertEquals("", state.kwh)
    }

    @Test
    fun userClearsKwhAfterAutoFill_thenChangesSoc_reActivates() = runTest {
        val (vm, _) = build(nominalBatteryKwh = 60.0)
        vm.uiState.first { it.nominalBatteryKwh == 60.0 }
        vm.setSocBefore("20")
        vm.setSocAfter("80")
        vm.uiState.first { it.kwhCalculatorActive && it.kwh == "36" }
        // User clears kWh → setKwh("") flips to MEASURED & deactivates.
        vm.setKwh("")
        vm.uiState.first { !it.kwhCalculatorActive && it.kwh == "" }
        // User adjusts SoC → auto-activation fires again because kWh is blank.
        vm.setSocAfter("90")
        val state = vm.uiState.first { it.kwhCalculatorActive && it.kwh != "" }
        // 60 × (0.90 - 0.20) = 42
        assertEquals("42", state.kwh)
        assertEquals(ChargeKwhSource.DERIVED_FROM_SOC, state.kwhSource)
    }

    @Test
    fun setOdometer_blank_clearsBothRegressionFlags() = runTest {
        val gateway = FakeSaveChargeEventGateway()
        gateway.seedEvents(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L),
                ChargeEventEntity(id = 3L, carId = 1L, eventDate = 3_000L, odometerKm = 300.0, kwhAdded = 10.0, createdAt = 0L),
            ),
        )
        val (vm, _) = build(eventId = 2L, gateway = gateway)
        vm.uiState.first { it.previousOdometerKm == 100.0 }
        vm.setOdometer("99")
        vm.uiState.first { it.odometerBelowPrevious }
        vm.setOdometer("")
        val state = vm.uiState.first { it.odometer == "" }
        assertFalse(state.odometerBelowPrevious)
        assertFalse(state.odometerAboveNext)
    }
}
