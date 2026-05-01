package org.spsl.evtracker.ui.settings

import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.usecase.ExportCsvUseCase
import org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCase
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeCsvFileSink
import org.spsl.evtracker.testing.FakeDataResetTransactionRunner
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun reset() {
        Dispatchers.resetMain()
    }

    private fun build(
        remoteJson: String? = null,
        readThrows: Throwable? = null,
        activeCarId: Int = -1,
        seededCars: List<CarEntity> = emptyList(),
        seededLocations: List<CustomLocationEntity> = emptyList(),
    ): Setup {
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val writer = FakeSettingsWriter()
        val backupRepo = ThrowingBackupRepository(remoteJson, readThrows)
        val scheduler = FakeBackupScheduler()
        val workManager = mock<WorkManager>()
        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = backupRepo,
            backupSerializer = org.spsl.evtracker.domain.service.BackupSerializer(),
            transactionRunner = FakeRestoreTransactionRunner(),
            snapshotWriter = FakeRestoreSnapshotWriter(),
            carReader = FakeCarReader(),
            chargeEventQueries = FakeChargeEventQueries(),
            locationReader = FakeLocationReader(),
            settingsWriter = writer,
            backupScheduler = scheduler,
            now = org.spsl.evtracker.testing.FakeNowProvider(),
        )
        val locationReader = FakeLocationReader(seededLocations)
        val carReader = FakeCarReader(seededCars)
        // Charge events fake — the ExportCsvUseCase needs a queries instance:
        val chargeEventStore = kotlinx.coroutines.flow.MutableStateFlow<List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>>(emptyList())
        val chargeEventQueries = FakeChargeEventQueries(chargeEventStore)
        val chargeEventWriter = FakeChargeEventWriter(chargeEventStore)
        val csvSink = FakeCsvFileSink()
        val resetActive = ResetActiveCarDataUseCase(chargeEventWriter, scheduler)
        val resetRunner = FakeDataResetTransactionRunner()
        val resetAll = ResetAllDataUseCase(resetRunner, writer, scheduler)
        val exportCsv = ExportCsvUseCase(carReader, chargeEventQueries, csvSink)
        val vm = SettingsViewModel(
            reader, writer, locationReader, carReader,
            backupRepo, scheduler, workManager, restoreUseCase,
            resetActive, resetAll, exportCsv,
        )
        return Setup(
            vm, reader, writer, backupRepo, scheduler, workManager,
            locationReader, carReader, csvSink, chargeEventQueries,
        )
    }

    private data class Setup(
        val vm: SettingsViewModel,
        val reader: FakeSettingsReader,
        val writer: FakeSettingsWriter,
        val backupRepo: ThrowingBackupRepository,
        val scheduler: FakeBackupScheduler,
        val workManager: WorkManager,
        val locationReader: FakeLocationReader,
        val carReader: FakeCarReader,
        val csvSink: FakeCsvFileSink,
        val chargeEventQueries: FakeChargeEventQueries,
    )

    /** Like FakeBackupRepository but lets a test throw from readRemoteBackup. */
    private class ThrowingBackupRepository(
        var remoteJson: String? = null,
        var throwOnRead: Throwable? = null,
    ) : org.spsl.evtracker.domain.backup.BackupRepository {
        override suspend fun backupCurrentData() {}
        override suspend fun readRemoteBackup(): String? {
            throwOnRead?.let { throw it }
            return remoteJson
        }
    }

    // -------------------------------------------------------------------------
    // E (Drive) tests — unchanged
    // -------------------------------------------------------------------------

    @Test
    fun onDriveAuthGranted_noRemote_setsDriveEnabledAndClearsAuthFlight() = runTest {
        val s = build(remoteJson = null)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        val state = s.vm.uiState.first()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertFalse(state.isAuthInFlight)
        assertNull(state.pendingRestoreLabel)
    }

    @Test
    fun onDriveAuthGranted_remoteFound_emitsRestorePromptAndClearsAuthFlight() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()

        val prompt = received.filterIsInstance<SettingsEvent.ShowRestorePrompt>().firstOrNull()
        assertNotNull("expected ShowRestorePrompt; got $received", prompt)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        assertEquals(prompt!!.backupDateLabel, state.pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsAuthRequired_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = DriveAuthRequiredException())
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_auth_failed, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsIOException_emitsNetworkErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = IOException("offline"))
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_network_error, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthFailed_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthFailed(R.string.drive_consent_cancelled)
        advanceUntilIdle()
        assertEquals(R.string.drive_consent_cancelled, (received.single() as SettingsEvent.ShowError).msgRes)
        assertFalse(s.vm.uiState.first().isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onConfirmRestore_invokesRestoreAndEmitsSuccess() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted() // primes pendingRestoreLabel
        advanceUntilIdle()

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onConfirmRestore()
        advanceUntilIdle()

        assertTrue(received.any { it is SettingsEvent.RestoreSucceeded })
        // RestoreBackupUseCase sets driveEnabled = true on Success.
        assertTrue(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onSkipRestore_setsDriveEnabledAndClearsPending() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onSkipRestore()
        advanceUntilIdle()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onRestorePromptDismissed_leavesDriveOff() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onRestorePromptDismissed()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onToggleDriveOff_cancelsWorkAndDisables() = runTest {
        val s = build()
        s.vm.onToggleDriveOff()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        verify(s.workManager).cancelUniqueWork(any())
    }

    // -------------------------------------------------------------------------
    // F1 tests — new
    // -------------------------------------------------------------------------

    @Test
    fun primaryMetric_select_compatibleUnit_writesOnlyMetric() = runTest {
        val s = build()
        s.vm.onPrimaryMetricSelected("kwh_per_100km")
        advanceUntilIdle()
        assertEquals("kwh_per_100km", s.writer.primaryMetric)
        // distanceUnit was "km" already; metric pick is compatible => no combined write.
        assertEquals("km", s.writer.distanceUnit)
    }

    @Test
    fun primaryMetric_select_incompatibleUnit_writesBoth_emitsAutoFlipped_unitToMiles() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onPrimaryMetricSelected("mi_per_kwh")
        advanceUntilIdle()
        job.cancel()
        assertEquals("mi_per_kwh", s.writer.primaryMetric)
        assertEquals("miles", s.writer.distanceUnit)
        assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_unit_flipped_to_miles })
    }

    @Test
    fun primaryMetric_select_incompatibleUnit_writesBoth_emitsAutoFlipped_unitToKm() = runTest {
        val s = build()
        s.reader.setDistanceUnit("miles")
        s.reader.setPrimaryMetric("mi_per_kwh")
        advanceUntilIdle() // let VM observe the new state
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onPrimaryMetricSelected("km_per_kwh")
        advanceUntilIdle()
        job.cancel()
        assertEquals("km_per_kwh", s.writer.primaryMetric)
        assertEquals("km", s.writer.distanceUnit)
        assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_unit_flipped_to_km })
    }

    @Test
    fun distanceUnit_select_compatibleMetric_writesOnlyUnit() = runTest {
        val s = build()
        s.vm.onDistanceUnitSelected("km")
        advanceUntilIdle()
        assertEquals("km", s.writer.distanceUnit)
        // primaryMetric stays default; no combined write
    }

    @Test
    fun distanceUnit_select_incompatibleMetric_writesBoth_emitsAutoFlipped_metric() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDistanceUnitSelected("miles")
        advanceUntilIdle()
        job.cancel()
        assertEquals("miles", s.writer.distanceUnit)
        assertEquals("mi_per_kwh", s.writer.primaryMetric)
        assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_metric_flipped_mi_per_kwh })
    }

    @Test
    fun currency_select_writesValue() = runTest {
        val s = build()
        s.vm.onCurrencySelected("USD")
        advanceUntilIdle()
        assertEquals("USD", s.writer.currency)
    }

    @Test
    fun theme_select_writesValue() = runTest {
        val s = build()
        s.vm.onThemeSelected("dark")
        advanceUntilIdle()
        assertEquals("dark", s.writer.theme)
    }

    @Test
    fun resetActiveCar_disabled_whenNoActiveCar() = runTest {
        val s = build(activeCarId = -1)
        advanceUntilIdle()
        s.vm.onResetActiveCarData()
        advanceUntilIdle()
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun resetActiveCar_callsUseCase_withActiveCarId() = runTest {
        val s = build(activeCarId = 5)
        advanceUntilIdle()
        s.vm.onResetActiveCarData()
        advanceUntilIdle()
        // Use FakeBackupScheduler enqueue count as proxy that the use case ran.
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun resetAllData_callsUseCase_emitsNavigateToWizard() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onResetAllData()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.any { it is SettingsEvent.NavigateToWizard })
        assertFalse(s.writer.resetInProgress)
    }

    @Test
    fun resetPreferences_setsSetupCompleteFalse_emitsNavigateToWizard() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onResetPreferences()
        advanceUntilIdle()
        job.cancel()
        assertFalse(s.writer.setupComplete)
        assertTrue(received.any { it is SettingsEvent.NavigateToWizard })
    }

    @Test
    fun exportCsv_disabled_whenNoActiveCar() = runTest {
        val s = build(activeCarId = -1)
        advanceUntilIdle()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onExportCsv()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.none { it is SettingsEvent.LaunchCsvShareIntent })
    }

    @Test
    fun exportCsv_success_emitsLaunchIntent() = runTest {
        val car = CarEntity(
            id = 5,
            name = "Tesla",
            make = "T",
            model = "M3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )
        val s = build(activeCarId = 5, seededCars = listOf(car))
        advanceUntilIdle()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onExportCsv()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.any { it is SettingsEvent.LaunchCsvShareIntent })
    }

    @Test
    fun exportCsv_ioException_emitsShowError() = runTest {
        val car = CarEntity(
            id = 5,
            name = "Tesla",
            make = "T",
            model = "M3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )
        val s = build(activeCarId = 5, seededCars = listOf(car))
        advanceUntilIdle()
        s.csvSink.failNext = IOException("disk full")
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onExportCsv()
        advanceUntilIdle()
        job.cancel()
        assertTrue(received.any { it is SettingsEvent.ShowError && it.msgRes == R.string.settings_export_csv_failed })
    }

    @Test
    fun customLocationCount_reflectsLocationReaderEmission() = runTest {
        val locations = listOf(
            CustomLocationEntity(id = 1, label = "A", useCount = 1, lastUsed = 1L),
            CustomLocationEntity(id = 2, label = "B", useCount = 1, lastUsed = 2L),
        )
        val s = build(seededLocations = locations)
        advanceUntilIdle()
        assertEquals(2, s.vm.uiState.value.customLocationCount)
    }
}
