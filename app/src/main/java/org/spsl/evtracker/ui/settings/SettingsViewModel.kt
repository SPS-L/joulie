// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.EvDbUpdateState
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.core.model.SettingsUiState
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.locale.LocaleApplier
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.FetchOutcome
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.repository.UpdateResult
import org.spsl.evtracker.domain.usecase.ExportCsvUseCase
import org.spsl.evtracker.domain.usecase.PushBackupNowUseCase
import org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCase
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.domain.usecase.UpdateEvDatabaseUseCase
import org.spsl.evtracker.domain.usecase.WipeRemoteBackupUseCase
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val locationReader: LocationReader,
    private val carReader: CarReader,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val workManager: WorkManager,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val resetActiveCarDataUseCase: ResetActiveCarDataUseCase,
    private val resetAllDataUseCase: ResetAllDataUseCase,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val pushBackupNowUseCase: PushBackupNowUseCase,
    private val wipeRemoteBackupUseCase: WipeRemoteBackupUseCase,
    private val localeApplier: LocaleApplier,
    private val carbonIntensitySource: CarbonIntensitySource,
    private val updateEvDatabase: UpdateEvDatabaseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsReader.driveEnabled.collect { v -> _uiState.update { it.copy(driveEnabled = v) } }
        }
        viewModelScope.launch {
            settingsReader.lastBackupAt.collect { v -> _uiState.update { it.copy(lastBackupAt = v) } }
        }
        viewModelScope.launch {
            settingsReader.primaryMetric.collect { v -> _uiState.update { it.copy(primaryMetric = v) } }
        }
        viewModelScope.launch {
            settingsReader.distanceUnit.collect { v -> _uiState.update { it.copy(distanceUnit = v) } }
        }
        viewModelScope.launch {
            settingsReader.currency.collect { v -> _uiState.update { it.copy(currency = v) } }
        }
        viewModelScope.launch {
            settingsReader.theme.collect { v -> _uiState.update { it.copy(theme = v) } }
        }
        viewModelScope.launch {
            // collect the persisted language tag so the Settings UI
            // can show the currently-selected option in the dialog.
            settingsReader.languageTag.collect { v -> _uiState.update { it.copy(languageTag = v) } }
        }
        viewModelScope.launch {
            // CO₂ tracker preferences. Drive the row summaries +
            // the dialog's prefilled value.
            settingsReader.iceBaselineLPer100km.collect { v ->
                _uiState.update { it.copy(iceBaselineLPer100km = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.co2Enabled.collect { v ->
                _uiState.update { it.copy(co2Enabled = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.electricityMapsApiKey.collect { v ->
                _uiState.update { it.copy(electricityMapsApiKey = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.electricityMapsZone.collect { v ->
                _uiState.update { it.copy(electricityMapsZone = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.activeCarId.collect { id ->
                val name = if (id == -1L) null else carReader.getById(id)?.name
                _uiState.update { it.copy(activeCarId = id, activeCarName = name) }
            }
        }
        viewModelScope.launch {
            locationReader.observeAll().collect { list ->
                _uiState.update { it.copy(customLocationCount = list.size) }
            }
        }
        // EV-database refresh metadata (TASK-91). Three flows that
        // together drive the "Last updated" summary line in Settings.
        viewModelScope.launch {
            settingsReader.evDbLastUpdatedAt.collect { v ->
                _uiState.update { it.copy(evDbLastUpdatedAt = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.evDbVersion.collect { v ->
                _uiState.update { it.copy(evDbVersion = v) }
            }
        }
        viewModelScope.launch {
            settingsReader.evDbVehicleCount.collect { v ->
                _uiState.update { it.copy(evDbVehicleCount = v) }
            }
        }
    }

    /**
     * Tap handler for Settings → "Update EV database" (TASK-91).
     * Idempotent: a second tap while [EvDbUpdateState.Loading] is on
     * screen is a no-op. The result state is purely UI-side; the
     * persistent cache + summary line are driven by the DataStore
     * flows wired in `init`.
     */
    fun onUpdateEvDatabase() {
        if (_uiState.value.evDbState is EvDbUpdateState.Loading) return
        viewModelScope.launch {
            _uiState.update { it.copy(evDbState = EvDbUpdateState.Loading) }
            val next = when (val r = updateEvDatabase()) {
                is UpdateResult.Success ->
                    EvDbUpdateState.Success(r.version, r.vehicleCount)
                is UpdateResult.ValidationFailed ->
                    EvDbUpdateState.Failure(R.string.settings_ev_db_error_validation)
                is UpdateResult.NetworkError ->
                    EvDbUpdateState.Failure(R.string.settings_ev_db_error_network)
                is UpdateResult.ParseError ->
                    EvDbUpdateState.Failure(R.string.settings_ev_db_error_parse)
            }
            _uiState.update { it.copy(evDbState = next) }
        }
    }

    /**
     * Clear the transient EV-DB update outcome so the Settings row
     * returns to Idle after the Fragment has rendered the Snackbar.
     * The persisted summary line keeps the "Last updated" text either
     * way — this purely controls the Snackbar one-shot.
     */
    fun onEvDbStateConsumed() {
        _uiState.update { it.copy(evDbState = EvDbUpdateState.Idle) }
    }

    // -- Drive ----------------------------------------------------------------

    fun onDriveAuthGranted() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthInFlight = true) }
            try {
                val json = backupRepository.readRemoteBackup()
                if (json == null) {
                    settingsWriter.setDriveEnabled(true)
                    backupScheduler.enqueueBackup()
                    _uiState.update { it.copy(isAuthInFlight = false) }
                    return@launch
                }
                val snapshot = parseRemoteSnapshot(json)
                // if the user has already been offered (and Skipped or
                // Restored) this exact remote snapshot, enable Drive silently
                // instead of re-firing the destructive restore prompt.
                val lastSeen = settingsReader.lastSeenRemoteBackupExportedAt.first()
                if (snapshot.exportedAt.isNotEmpty() && snapshot.exportedAt == lastSeen) {
                    settingsWriter.setDriveEnabled(true)
                    backupScheduler.enqueueBackup()
                    _uiState.update { it.copy(isAuthInFlight = false) }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isAuthInFlight = false,
                        pendingRestoreLabel = snapshot.label,
                        pendingRestoreExportedAt = snapshot.exportedAt.takeIf { v -> v.isNotEmpty() },
                    )
                }
                _events.tryEmit(SettingsEvent.ShowRestorePrompt(snapshot.label))
            } catch (_: DriveAuthRequiredException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_auth_failed))
            } catch (_: IOException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_network_error))
            }
        }
    }

    fun onDriveAuthFailed(msgRes: Int) {
        _uiState.update { it.copy(isAuthInFlight = false) }
        _events.tryEmit(SettingsEvent.ShowError(msgRes))
    }

    fun onConfirmRestore() {
        viewModelScope.launch {
            // capture the snapshot identity BEFORE the restore use case
            // overwrites local data — after restore the marker means "the local
            // DB is now this snapshot; don't re-prompt to restore it again."
            val pendingExportedAt = _uiState.value.pendingRestoreExportedAt
            when (restoreBackupUseCase()) {
                is RestoreResult.Success -> {
                    if (!pendingExportedAt.isNullOrEmpty()) {
                        settingsWriter.setLastSeenRemoteBackupExportedAt(pendingExportedAt)
                    }
                    _uiState.update {
                        it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null)
                    }
                    _events.tryEmit(SettingsEvent.RestoreSucceeded)
                }
                is RestoreResult.VersionMismatch -> {
                    _uiState.update {
                        it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null)
                    }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_remote_backup_too_new))
                }
                RestoreResult.NoRemoteBackup -> {
                    _uiState.update {
                        it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null)
                    }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_restore_failed))
                }
            }
        }
    }

    fun onSkipRestore() {
        viewModelScope.launch {
            // persist the marker BEFORE flipping driveEnabled so a fast
            // re-entry of Settings sees the marker already populated.
            val pendingExportedAt = _uiState.value.pendingRestoreExportedAt
            if (!pendingExportedAt.isNullOrEmpty()) {
                settingsWriter.setLastSeenRemoteBackupExportedAt(pendingExportedAt)
            }
            settingsWriter.setDriveEnabled(true)
            backupScheduler.enqueueBackup()
            _uiState.update {
                it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null)
            }
        }
    }

    fun onRestorePromptDismissed() {
        // No marker write here: dismiss != Skip. The user neither accepted nor
        // declined the snapshot, so the next entry should still offer it.
        _uiState.update {
            it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null)
        }
    }

    fun onToggleDriveOff() {
        viewModelScope.launch {
            settingsWriter.setDriveEnabled(false)
            workManager.cancelUniqueWork(BackupScheduler.UNIQUE_WORK_NAME)
        }
    }

    // -- — manual Drive controls ----------------------------------------

    /**
     * "Back up now" tap. No-op if any manual operation is already running —
     * push and wipe are mutually exclusive, and a duplicate tap on push is
     * a no-op (we don't stack uploads).
     */
    fun onPushBackupClicked() {
        if (_uiState.value.isManualBackupRunning || _uiState.value.isManualWipeRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isManualBackupRunning = true) }
            try {
                val event = when (val r = pushBackupNowUseCase()) {
                    BackupResult.Success -> SettingsEvent.BackupNowSucceeded
                    BackupResult.AuthRequired -> SettingsEvent.BackupNowFailed(R.string.drive_auth_failed)
                    is BackupResult.Failure -> SettingsEvent.BackupNowFailed(pushFailureMessage(r))
                }
                _events.tryEmit(event)
            } finally {
                _uiState.update { it.copy(isManualBackupRunning = false) }
            }
        }
    }

    /**
     * "Wipe remote backup" → Confirm. Same mutual-exclusion contract as push.
     */
    fun onConfirmWipeClicked() {
        if (_uiState.value.isManualBackupRunning || _uiState.value.isManualWipeRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isManualWipeRunning = true) }
            try {
                val event = when (val r = wipeRemoteBackupUseCase()) {
                    BackupResult.Success -> SettingsEvent.WipeSucceeded
                    BackupResult.AuthRequired -> SettingsEvent.WipeFailed(R.string.drive_auth_failed)
                    is BackupResult.Failure -> SettingsEvent.WipeFailed(wipeFailureMessage(r))
                }
                _events.tryEmit(event)
            } finally {
                _uiState.update { it.copy(isManualWipeRunning = false) }
            }
        }
    }

    private fun pushFailureMessage(r: BackupResult.Failure): Int = when (r.reason) {
        "Drive storage full" -> R.string.drive_storage_full
        else -> R.string.drive_backup_now_failure
    }

    private fun wipeFailureMessage(r: BackupResult.Failure): Int = when (r.reason) {
        "Drive storage full" -> R.string.drive_storage_full
        else -> R.string.drive_wipe_failure
    }

    fun onPrimaryMetricSelected(metric: String) {
        viewModelScope.launch {
            val requiredUnit = unitFor(metric)
            if (requiredUnit != _uiState.value.distanceUnit) {
                settingsWriter.setPrimaryMetricAndDistanceUnit(metric, requiredUnit)
                _events.tryEmit(SettingsEvent.AutoFlipped(unitFlipMsgRes(requiredUnit)))
            } else {
                settingsWriter.setPrimaryMetric(metric)
            }
        }
    }

    fun onDistanceUnitSelected(unit: String) {
        viewModelScope.launch {
            val current = _uiState.value.primaryMetric
            val newMetric = defaultMetricFor(unit, current)
            if (newMetric != current) {
                settingsWriter.setPrimaryMetricAndDistanceUnit(newMetric, unit)
                _events.tryEmit(SettingsEvent.AutoFlipped(metricFlipMsgRes(newMetric)))
            } else {
                settingsWriter.setDistanceUnit(unit)
            }
        }
    }

    fun onCurrencySelected(code: String) {
        viewModelScope.launch { settingsWriter.setCurrency(code) }
    }

    fun onThemeSelected(theme: String) {
        viewModelScope.launch { settingsWriter.setTheme(theme) }
    }

    /**
     * Persist the selected language tag and apply it to the
     * running process. Empty string ("") = follow system. The
     * [LocaleApplier.apply] call triggers an Activity recreation on most
     * Android versions; the DataStore write is durable so the new
     * Activity reads the right tag at startup.
     */
    fun onLanguageSelected(tag: String) {
        viewModelScope.launch {
            settingsWriter.setLanguageTag(tag)
            localeApplier.apply(tag)
        }
    }

    /**
     * Persist the user-edited petrol baseline in L/100km.
     * Caller (Settings dialog) is responsible for validating > 0 — the
     * VM-level check here is a defensive last line; sub-zero values get
     * silently dropped to avoid corrupting Stats math.
     */
    fun onIceBaselineSelected(value: Double) {
        if (value <= 0.0) return
        viewModelScope.launch { settingsWriter.setIceBaselineLPer100km(value) }
    }

    /** Toggle the opt-in CO₂ master switch. */
    fun onCo2EnabledToggled(enabled: Boolean) {
        viewModelScope.launch { settingsWriter.setCo2Enabled(enabled) }
    }

    /** Persist the Electricity Maps API key (empty string clears it). */
    fun onElectricityMapsApiKeySet(value: String) {
        viewModelScope.launch { settingsWriter.setElectricityMapsApiKey(value.trim()) }
    }

    @Volatile private var apiKeyTestInFlight = false

    /**
     * TASK-90: probe the [candidateKey] against the user's currently-selected
     * Electricity Maps zone WITHOUT writing it to Settings. Lets the
     * dialog's "Test" button validate a key (or a key+zone pair) before
     * the user saves it. The result is emitted as an [SettingsEvent] so
     * the Fragment can update the inline status text inside the dialog.
     */
    fun onTestElectricityMapsApiKey(candidateKey: String) {
        if (apiKeyTestInFlight) return
        val trimmed = candidateKey.trim()
        if (trimmed.isBlank()) {
            _events.tryEmit(
                SettingsEvent.ApiKeyTestFinished(R.string.settings_co2_api_key_test_empty),
            )
            return
        }
        apiKeyTestInFlight = true
        _events.tryEmit(SettingsEvent.ApiKeyTestStarted)
        viewModelScope.launch {
            try {
                val zone = settingsReader.electricityMapsZone.first()
                val outcome = carbonIntensitySource.probeApiKey(zone, trimmed)
                _events.tryEmit(outcome.toApiKeyTestEvent(zone))
            } finally {
                apiKeyTestInFlight = false
            }
        }
    }

    private fun FetchOutcome.toApiKeyTestEvent(zone: String): SettingsEvent.ApiKeyTestFinished =
        when (this) {
            is FetchOutcome.Success -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_success,
                zone = zone,
                intensity = intensityGCo2PerKwh,
            )
            FetchOutcome.AuthError -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_auth,
            )
            FetchOutcome.NetworkError -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_network,
            )
            FetchOutcome.RateLimited -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_rate_limited,
            )
            FetchOutcome.ServerError -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_server,
            )
            FetchOutcome.Disabled -> SettingsEvent.ApiKeyTestFinished(
                resultStringRes = R.string.settings_co2_api_key_test_empty,
            )
        }

    /**
     * Persist the Electricity Maps grid-zone code. Always upper-cased
     * so the API call uses a canonical form regardless of the dialog's
     * keyboard hints. Blank input is silently dropped (the dialog
     * already validates non-blank).
     *
     * **TASK-85.** After persisting a *new* zone (different from the
     * current one), fires a refresh against `CarbonIntensitySource` with
     * the new zone passed explicitly so the dashboard pill picks up the
     * new zone's intensity — or surfaces the matching error reason
     * (most commonly [org.spsl.evtracker.domain.repository.FetchOutcome.AuthError]
     * because free-tier keys are bound to a single zone at signup; TASK-89
     * surfaces that expectation in the UI before the user gets here).
     *
     * The fetch is dispatched directly rather than via
     * [org.spsl.evtracker.domain.usecase.RefreshCarbonIntensityUseCase] so
     * the new zone arrives at the repo immediately, bypassing the
     * DataStore write-then-read race that would otherwise have the
     * use case re-read the *old* zone from settings.
     */
    fun onElectricityMapsZoneSet(value: String) {
        val normalized = value.trim().uppercase(Locale.US)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val previous = settingsReader.electricityMapsZone.first()
            settingsWriter.setElectricityMapsZone(normalized)
            if (normalized == previous) return@launch
            if (!settingsReader.co2Enabled.first()) return@launch
            val apiKey = settingsReader.electricityMapsApiKey.first()
            if (apiKey.isBlank()) return@launch
            runCatching { carbonIntensitySource.fetchCarbonIntensity(normalized, apiKey) }
        }
    }

    fun onResetPreferences() {
        viewModelScope.launch {
            settingsWriter.setSetupComplete(false)
            _events.tryEmit(SettingsEvent.NavigateToWizard)
        }
    }

    fun onResetActiveCarData() {
        val carId = _uiState.value.activeCarId
        if (carId == -1L) return
        viewModelScope.launch { resetActiveCarDataUseCase(carId) }
    }

    fun onResetAllData() {
        viewModelScope.launch {
            resetAllDataUseCase()
            _events.tryEmit(SettingsEvent.NavigateToWizard)
        }
    }

    fun onExportCsv() {
        val carId = _uiState.value.activeCarId
        if (carId == -1L) return
        viewModelScope.launch {
            try {
                // distance is now always emitted as canonical km in
                // the export — the previous useKm flip was dropped so research
                // consumers get a locale-independent schema.
                val uri = exportCsvUseCase.export(carId)
                _events.tryEmit(SettingsEvent.LaunchCsvShareIntent(uri))
            } catch (_: IOException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            } catch (_: IllegalArgumentException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            }
        }
    }

    /**
     * Date-ranged CSV export. The fragment owns the
     * `MaterialDatePicker` UI and forwards the selected range here.
     * `startMillis` and `endMillis` are inclusive on both ends; the
     * use case treats them via `LongRange` semantics
     * (`startMillis..endMillis`).
     */
    fun onExportCsvRange(startMillis: Long, endMillis: Long) {
        val carId = _uiState.value.activeCarId
        if (carId == -1L) return
        if (endMillis < startMillis) return
        viewModelScope.launch {
            try {
                val uri = exportCsvUseCase.export(carId, startMillis..endMillis)
                _events.tryEmit(SettingsEvent.LaunchCsvShareIntent(uri))
            } catch (_: IOException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            } catch (_: IllegalArgumentException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            }
        }
    }

    /**
     * Parse the remote backup JSON once and return both the raw
     * `exported_at` string (used for the durable last-seen marker) and the
     * human-readable label (shown in the restore prompt). `exportedAt` is
     * the empty string when the JSON has no parseable `exported_at` field —
     * callers must treat empty as "no identity available, do not write the
     * marker." Mirrors the legacy `parseExportedAtLabel` behaviour for the
     * label side.
     */
    private data class RemoteBackupSnapshot(val exportedAt: String, val label: String)

    private fun parseRemoteSnapshot(json: String): RemoteBackupSnapshot {
        val match = EXPORTED_AT_REGEX.find(json) ?: return RemoteBackupSnapshot("", UNKNOWN_DATE)
        val iso = match.groupValues[1]
        val label = try {
            DATE_FORMAT.format(Date(Instant.parse(iso).toEpochMilli()))
        } catch (_: Throwable) {
            UNKNOWN_DATE
        }
        return RemoteBackupSnapshot(exportedAt = iso, label = label)
    }

    private fun unitFor(metric: String): String = when (metric) {
        "mi_per_kwh" -> "miles"
        "km_per_kwh", "kwh_per_100km" -> "km"
        else -> "km"
    }

    private fun defaultMetricFor(unit: String, currentMetric: String): String =
        when (unit) {
            "miles" -> "mi_per_kwh"
            "km" -> if (currentMetric == "mi_per_kwh") "km_per_kwh" else currentMetric
            else -> currentMetric
        }

    @StringRes private fun unitFlipMsgRes(newUnit: String): Int = when (newUnit) {
        "miles" -> R.string.settings_unit_flipped_to_miles
        else -> R.string.settings_unit_flipped_to_km
    }

    @StringRes private fun metricFlipMsgRes(newMetric: String): Int = when (newMetric) {
        "kwh_per_100km" -> R.string.settings_metric_flipped_kwh_per_100km
        "mi_per_kwh" -> R.string.settings_metric_flipped_mi_per_kwh
        else -> R.string.settings_metric_flipped_km_per_kwh
    }

    companion object {
        private val EXPORTED_AT_REGEX = Regex("\"exported_at\"\\s*:\\s*\"([^\"]+)\"")
        private const val UNKNOWN_DATE = "an earlier date"
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
