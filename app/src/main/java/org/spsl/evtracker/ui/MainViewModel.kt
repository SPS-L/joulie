package org.spsl.evtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val resetAllDataUseCase: ResetAllDataUseCase,
) : ViewModel() {

    sealed class StartupState {
        data object Loading : StartupState()
        data class Ready(val setupComplete: Boolean) : StartupState()
        data class RecoveryFailed(val cause: Throwable?) : StartupState()
    }

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    @Volatile private var inFlight = false

    init { runStartupSequence() }

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
