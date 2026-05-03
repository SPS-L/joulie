// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.spsl.evtracker.domain.notification.BackupOutcomeReporter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val resetAllDataUseCase: ResetAllDataUseCase,
) : ViewModel() {

    sealed class StartupState {
        data object Loading : StartupState()
        data class Ready(val setupComplete: Boolean) : StartupState()
        data class RecoveryFailed(val cause: Throwable?) : StartupState()
    }

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    /**
     * TASK-19: true once the user has hit the chronic-failure threshold
     * AND has not previously dismissed the notification permission rationale.
     * The Activity is responsible for the platform-level checks (API >= 33,
     * `ContextCompat.checkSelfPermission`) — the VM only owns the DataStore
     * half of the gate.
     */
    val shouldOfferNotificationPermission: StateFlow<Boolean> =
        combine(
            settingsReader.consecutiveBackupFailures,
            settingsReader.notificationPermissionDenied,
        ) { failures, denied ->
            failures >= BackupOutcomeReporter.CHRONIC_FAILURE_THRESHOLD && !denied
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    @Volatile private var inFlight = false

    init {
        runStartupSequence()
    }

    /** Sticky write — never reverted. Called when the user denies the rationale or the system prompt. */
    fun markNotificationPermissionDenied() {
        viewModelScope.launch { settingsWriter.setNotificationPermissionDenied(true) }
    }

    fun runStartupSequence() {
        if (inFlight) return
        inFlight = true
        _startupState.value = StartupState.Loading
        viewModelScope.launch {
            try {
                if (settingsReader.resetInProgress.first()) {
                    val result = runCatching { resetAllDataUseCase() }
                    if (result.isFailure) {
                        _startupState.value = StartupState.RecoveryFailed(result.exceptionOrNull())
                        return@launch
                    }
                }
                val complete = settingsReader.setupComplete.first()
                _startupState.value = StartupState.Ready(complete)
            } finally {
                inFlight = false
            }
        }
    }
}
