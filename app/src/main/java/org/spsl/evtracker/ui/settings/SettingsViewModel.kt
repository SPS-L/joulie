package org.spsl.evtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.core.model.SettingsUiState
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val workManager: WorkManager,
    private val restoreBackupUseCase: RestoreBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsReader.driveEnabled.collect { enabled ->
                _uiState.update { it.copy(driveEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsReader.lastBackupAt.collect { ts ->
                _uiState.update { it.copy(lastBackupAt = ts) }
            }
        }
    }

    /** Called by Fragment when the auth flow returned Success (silent or post-consent). */
    fun onDriveAuthGranted() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthInFlight = true) }
            try {
                val json = backupRepository.readRemoteBackup()
                if (json == null) {
                    settingsWriter.setDriveEnabled(true)
                    backupScheduler.enqueueBackup()
                    _uiState.update { it.copy(isAuthInFlight = false) }
                } else {
                    val label = parseExportedAtLabel(json)
                    _uiState.update { it.copy(isAuthInFlight = false, pendingRestoreLabel = label) }
                    _events.tryEmit(SettingsEvent.ShowRestorePrompt(label))
                }
            } catch (_: DriveAuthRequiredException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_auth_failed))
            } catch (_: IOException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_network_error))
            }
        }
    }

    /** Called by Fragment when the auth flow returned Failed (or consent was cancelled). */
    fun onDriveAuthFailed(msgRes: Int) {
        _uiState.update { it.copy(isAuthInFlight = false) }
        _events.tryEmit(SettingsEvent.ShowError(msgRes))
    }

    fun onConfirmRestore() {
        viewModelScope.launch {
            when (restoreBackupUseCase()) {
                is RestoreResult.Success -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.RestoreSucceeded)
                }
                is RestoreResult.VersionMismatch -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_remote_backup_too_new))
                }
                RestoreResult.NoRemoteBackup -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_restore_failed))
                }
            }
        }
    }

    fun onSkipRestore() {
        viewModelScope.launch {
            settingsWriter.setDriveEnabled(true)
            backupScheduler.enqueueBackup()
            _uiState.update { it.copy(pendingRestoreLabel = null) }
        }
    }

    fun onRestorePromptDismissed() {
        _uiState.update { it.copy(pendingRestoreLabel = null) }
    }

    fun onToggleDriveOff() {
        viewModelScope.launch {
            settingsWriter.setDriveEnabled(false)
            workManager.cancelUniqueWork(BackupScheduler.UNIQUE_WORK_NAME)
        }
    }

    private fun parseExportedAtLabel(json: String): String {
        return try {
            val match = EXPORTED_AT_REGEX.find(json)
            val iso = match?.groupValues?.get(1) ?: return UNKNOWN_DATE
            val instant = Instant.parse(iso)
            DATE_FORMAT.format(Date(instant.toEpochMilli()))
        } catch (_: Throwable) {
            UNKNOWN_DATE
        }
    }

    companion object {
        private val EXPORTED_AT_REGEX = Regex("\"exported_at\"\\s*:\\s*\"([^\"]+)\"")
        private const val UNKNOWN_DATE = "an earlier date"
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
