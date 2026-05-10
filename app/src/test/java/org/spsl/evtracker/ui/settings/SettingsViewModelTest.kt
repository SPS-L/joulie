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
import org.spsl.evtracker.domain.usecase.PushBackupNowUseCase
import org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCase
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.domain.usecase.WipeRemoteBackupUseCase
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
        activeCarId: Long = -1L,
        seededCars: List<CarEntity> = emptyList(),
        seededLocations: List<CustomLocationEntity> = emptyList(),
    ): Setup {
        val reader = FakeSettingsReader(activeCarIdInit = activeCarId)
        val writer = FakeSettingsWriter()
        val backupRepo = ThrowingBackupRepository(remoteJson, readThrows)
        val scheduler = FakeBackupScheduler()
        val workManager = mock<WorkManager>()
        val widgetRefresher = org.spsl.evtracker.testing.FakeWidgetRefresher()
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
            widgetRefresher = widgetRefresher,
            now = org.spsl.evtracker.testing.FakeNowProvider(),
        )
        val locationReader = FakeLocationReader(seededLocations)
        val carReader = FakeCarReader(seededCars)
        // Charge events fake — the ExportCsvUseCase needs a queries instance:
        val chargeEventStore = kotlinx.coroutines.flow.MutableStateFlow<List<org.spsl.evtracker.data.local.entity.ChargeEventEntity>>(emptyList())
        val chargeEventQueries = FakeChargeEventQueries(chargeEventStore)
        val chargeEventWriter = FakeChargeEventWriter(chargeEventStore)
        val csvSink = FakeCsvFileSink()
        val resetActive = ResetActiveCarDataUseCase(chargeEventWriter, scheduler, widgetRefresher)
        val resetRunner = FakeDataResetTransactionRunner()
        val resetAll = ResetAllDataUseCase(
            resetRunner = resetRunner,
            settingsWriter = writer,
            backupScheduler = scheduler,
            widgetRefresher = widgetRefresher,
            carbonIntensitySource = org.spsl.evtracker.testing.FakeCarbonIntensitySource(),
        )
        val exportCsv = ExportCsvUseCase(carReader, chargeEventQueries, csvSink)
        val pushBackupNow = PushBackupNowUseCase(backupRepo, writer, org.spsl.evtracker.testing.FakeNowProvider(time = 1_700_000_000_000L))
        val wipeRemoteBackup = WipeRemoteBackupUseCase(backupRepo, writer)
        val localeApplier = org.spsl.evtracker.testing.FakeLocaleApplier()
        val vm = SettingsViewModel(
            reader, writer, locationReader, carReader,
            backupRepo, scheduler, workManager, restoreUseCase,
            resetActive, resetAll, exportCsv,
            pushBackupNow, wipeRemoteBackup,
            localeApplier,
        )
        return Setup(
            vm, reader, writer, backupRepo, scheduler, workManager,
            locationReader, carReader, csvSink, chargeEventQueries, localeApplier,
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
        val localeApplier: org.spsl.evtracker.testing.FakeLocaleApplier,
    )

    /** Like FakeBackupRepository but lets a test throw from readRemoteBackup. */
    private class ThrowingBackupRepository(
        var remoteJson: String? = null,
        var throwOnRead: Throwable? = null,
        var nextBackupResult: org.spsl.evtracker.domain.backup.BackupResult =
            org.spsl.evtracker.domain.backup.BackupResult.Success,
        var nextDeleteResult: org.spsl.evtracker.domain.backup.BackupResult =
            org.spsl.evtracker.domain.backup.BackupResult.Success,
    ) : org.spsl.evtracker.domain.backup.BackupRepository {
        var backupCurrentDataCount: Int = 0
            private set
        var deleteCount: Int = 0
            private set

        /** When non-null, the next [backupCurrentData] call delays this many ms before returning. */
        var backupDelayMs: Long? = null

        override suspend fun backupCurrentData(): org.spsl.evtracker.domain.backup.BackupResult {
            backupCurrentDataCount++
            backupDelayMs?.let { kotlinx.coroutines.delay(it) }
            return nextBackupResult
        }
        override suspend fun readRemoteBackup(): String? {
            throwOnRead?.let { throw it }
            return remoteJson
        }
        override suspend fun deleteRemoteBackup(): org.spsl.evtracker.domain.backup.BackupResult {
            deleteCount++
            return nextDeleteResult
        }
    }

    // -------------------------------------------------------------------------
    // Drive backup tests
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

    // -------------------------------------------------------------------------
    // — durable last-seen-snapshot marker
    // -------------------------------------------------------------------------

    @Test
    fun onDriveAuthGranted_remoteExists_firstTime_emitsPrompt() = runTest {
        // Marker default is empty string ("never seen"); a remote snapshot
        // with a parseable exported_at must trigger the restore prompt.
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            "expected ShowRestorePrompt; got $received",
            received.any { it is SettingsEvent.ShowRestorePrompt },
        )
        assertFalse(
            "Drive must NOT be flipped on yet — user hasn't decided",
            s.writer.driveEnabled,
        )
        // pendingRestoreExportedAt must be populated so Skip / Confirm can
        // write the durable marker without re-reading the remote backup.
        assertEquals(
            "2023-11-14T22:13:20Z",
            s.vm.uiState.value.pendingRestoreExportedAt,
        )
    }

    @Test
    fun onDriveAuthGranted_remoteExists_alreadySeen_skipsSilently() = runTest {
        // Pre-seed the marker to match the remote's exported_at — simulates
        // "user already tapped Skip on this snapshot before". Drive should
        // be silently enabled with NO ShowRestorePrompt.
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.reader.setLastSeenRemoteBackupExportedAt("2023-11-14T22:13:20Z")

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            "no ShowRestorePrompt should fire when marker matches; got $received",
            received.none { it is SettingsEvent.ShowRestorePrompt },
        )
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertNull(s.vm.uiState.value.pendingRestoreLabel)
    }

    @Test
    fun onDriveAuthGranted_remoteWithDifferentExportedAt_promptsAgain() = runTest {
        // Marker holds an older snapshot's exported_at; the current remote
        // has a NEWER exported_at (e.g., user wiped + a fresh backup landed).
        // The user must be re-prompted exactly once for the new snapshot.
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_800_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.reader.setLastSeenRemoteBackupExportedAt("2023-11-14T22:13:20Z") // old marker

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            "different exportedAt must re-prompt; got $received",
            received.any { it is SettingsEvent.ShowRestorePrompt },
        )
        assertFalse(s.writer.driveEnabled)
    }

    @Test
    fun onSkipRestore_persistsLastSeenExportedAt() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        // Skip the prompt.
        s.vm.onSkipRestore()
        advanceUntilIdle()

        assertEquals(
            "Skip must persist the snapshot's exported_at as the durable marker",
            "2023-11-14T22:13:20Z",
            s.writer.lastSeenRemoteBackupExportedAt,
        )
        assertNull(s.vm.uiState.value.pendingRestoreLabel)
        assertNull(s.vm.uiState.value.pendingRestoreExportedAt)
    }

    @Test
    fun onConfirmRestore_success_persistsLastSeenExportedAt() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()

        s.vm.onConfirmRestore()
        advanceUntilIdle()

        assertEquals(
            "successful restore must persist the marker so the same snapshot is never re-prompted",
            "2023-11-14T22:13:20Z",
            s.writer.lastSeenRemoteBackupExportedAt,
        )
        assertTrue(s.writer.driveEnabled)
    }

    @Test
    fun onDriveAuthGranted_noRemote_keepsExistingMarker() = runTest {
        // No remote backup path: must NOT touch the marker (a previous
        // Skip / Restore decision is still meaningful — the user may simply
        // be re-toggling Drive after the remote was wiped externally).
        val s = build(remoteJson = null)
        s.reader.setLastSeenRemoteBackupExportedAt("2023-11-14T22:13:20Z")
        // The fake Reader's seed is independent of the Writer's storage; the
        // Writer's lastSeenRemoteBackupExportedAt starts at "" and must STAY
        // at "" because the use case never writes to it on the no-remote path.
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()

        assertEquals(
            "no-remote path must not write the marker",
            "",
            s.writer.lastSeenRemoteBackupExportedAt,
        )
        assertTrue(s.writer.driveEnabled)
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
    // Reset / preferences tests
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
        val s = build(activeCarId = -1L)
        advanceUntilIdle()
        s.vm.onResetActiveCarData()
        advanceUntilIdle()
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun resetActiveCar_callsUseCase_withActiveCarId() = runTest {
        val s = build(activeCarId = 5L)
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
        val s = build(activeCarId = -1L)
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
            id = 5L,
            name = "Tesla",
            make = "T",
            model = "M3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )
        val s = build(activeCarId = 5L, seededCars = listOf(car))
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
            id = 5L,
            name = "Tesla",
            make = "T",
            model = "M3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )
        val s = build(activeCarId = 5L, seededCars = listOf(car))
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
            CustomLocationEntity(id = 1L, label = "A", useCount = 1, lastUsed = 1L),
            CustomLocationEntity(id = 2L, label = "B", useCount = 1, lastUsed = 2L),
        )
        val s = build(seededLocations = locations)
        advanceUntilIdle()
        assertEquals(2, s.vm.uiState.value.customLocationCount)
    }

    // -------------------------------------------------------------------------
    // — manual Drive controls
    // -------------------------------------------------------------------------

    @Test
    fun onPushBackupClicked_success_emitsBackupNowSucceeded_andTogglesRunningFlag() = runTest {
        val s = build()
        s.backupRepo.nextBackupResult = org.spsl.evtracker.domain.backup.BackupResult.Success
        // Slow the upload by a tick so we can observe isManualBackupRunning = true mid-flight.
        s.backupRepo.backupDelayMs = 1L

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onPushBackupClicked()
        // Drain just enough work to start the launched coroutine and flip the flag,
        // but stop before the delayed backupCurrentData() returns.
        dispatcher.scheduler.runCurrent()
        assertTrue("flag must flip true while upload is in flight", s.vm.uiState.value.isManualBackupRunning)
        advanceUntilIdle()

        assertEquals(1, s.backupRepo.backupCurrentDataCount)
        assertEquals(SettingsEvent.BackupNowSucceeded, received.lastOrNull())
        assertFalse(s.vm.uiState.value.isManualBackupRunning)
        assertEquals(1_700_000_000_000L, s.writer.lastBackupAt)
        job.cancel()
    }

    @Test
    fun onPushBackupClicked_authRequired_emitsBackupNowFailed_withAuthString() = runTest {
        val s = build()
        s.backupRepo.nextBackupResult = org.spsl.evtracker.domain.backup.BackupResult.AuthRequired

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onPushBackupClicked()
        advanceUntilIdle()

        val ev = received.last()
        assertTrue("expected BackupNowFailed but was $ev", ev is SettingsEvent.BackupNowFailed)
        assertEquals(R.string.drive_auth_failed, (ev as SettingsEvent.BackupNowFailed).msgRes)
        assertNull("lastBackupAt must stay null after AuthRequired", s.writer.lastBackupAt)
        job.cancel()
    }

    @Test
    fun onPushBackupClicked_failure_emitsBackupNowFailed() = runTest {
        val s = build()
        s.backupRepo.nextBackupResult = org.spsl.evtracker.domain.backup.BackupResult.Failure("HTTP 500")

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onPushBackupClicked()
        advanceUntilIdle()

        assertTrue(received.last() is SettingsEvent.BackupNowFailed)
        assertNull(s.writer.lastBackupAt)
        job.cancel()
    }

    @Test
    fun onConfirmWipeClicked_success_emitsWipeSucceeded_andClearsLastBackupAt() = runTest {
        val s = build()
        s.writer.setLastBackupAt(1_700_000_000_000L)
        s.backupRepo.nextDeleteResult = org.spsl.evtracker.domain.backup.BackupResult.Success

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onConfirmWipeClicked()
        advanceUntilIdle()

        assertEquals(1, s.backupRepo.deleteCount)
        assertEquals(SettingsEvent.WipeSucceeded, received.last())
        assertEquals(0L, s.writer.lastBackupAt)
        assertFalse(s.vm.uiState.value.isManualWipeRunning)
        job.cancel()
    }

    @Test
    fun onConfirmWipeClicked_failure_emitsWipeFailed_andDoesNotClearLastBackupAt() = runTest {
        val s = build()
        s.writer.setLastBackupAt(1_700_000_000_000L)
        s.backupRepo.nextDeleteResult = org.spsl.evtracker.domain.backup.BackupResult.Failure("HTTP 500")

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onConfirmWipeClicked()
        advanceUntilIdle()

        assertTrue(received.last() is SettingsEvent.WipeFailed)
        assertEquals(1_700_000_000_000L, s.writer.lastBackupAt)
        job.cancel()
    }

    @Test
    fun onConfirmWipeClicked_whilePushInFlight_isNoOp() = runTest {
        val s = build()
        // Slow the in-flight push so we can attempt the wipe while it's running.
        s.backupRepo.backupDelayMs = 1_000L
        s.backupRepo.nextBackupResult = org.spsl.evtracker.domain.backup.BackupResult.Success

        s.vm.onPushBackupClicked()
        dispatcher.scheduler.runCurrent()
        assertTrue(s.vm.uiState.value.isManualBackupRunning)
        // Wipe must be ignored entirely — no deleteRemoteBackup call.
        s.vm.onConfirmWipeClicked()
        dispatcher.scheduler.runCurrent()
        assertEquals(0, s.backupRepo.deleteCount)
        assertFalse(s.vm.uiState.value.isManualWipeRunning)

        // Let the slow push finish so runTest doesn't complain about pending coroutines.
        advanceUntilIdle()
    }

    // -------------------------------------------------------------------------
    // — language picker
    // -------------------------------------------------------------------------

    @Test
    fun onLanguageSelected_persistsTagAndAppliesLocale() = runTest {
        val s = build()
        s.vm.onLanguageSelected("el")
        advanceUntilIdle()
        assertEquals("el", s.writer.languageTag)
        assertEquals("el", s.localeApplier.lastAppliedTag)
        assertEquals(1, s.localeApplier.applyCallCount)
    }

    @Test
    fun onLanguageSelected_followSystem_writesEmptyString() = runTest {
        val s = build()
        s.vm.onLanguageSelected("ru")
        advanceUntilIdle()
        s.vm.onLanguageSelected("")
        advanceUntilIdle()
        // Empty-string write replaces the previous "ru" choice; LocaleApplier
        // sees "" so it can map to LocaleListCompat.getEmptyLocaleList.
        assertEquals("", s.writer.languageTag)
        assertEquals("", s.localeApplier.lastAppliedTag)
    }

    @Test
    fun languageTag_collectedFromSettingsReader_intoUiState() = runTest {
        val s = build()
        // Pre-seed the reader-side flow; the VM's init-collector should
        // surface it into UiState so the dialog can show the right
        // selected option.
        s.reader.setLanguageTag("tr")
        advanceUntilIdle()
        assertEquals("tr", s.vm.uiState.value.languageTag)
    }
}
