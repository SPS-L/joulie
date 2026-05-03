// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.core.model.ManageLocationsEvent
import org.spsl.evtracker.core.model.ManageLocationsUiState
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.LocationWriter
import javax.inject.Inject

@HiltViewModel
class ManageLocationsViewModel @Inject constructor(
    private val locationReader: LocationReader,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageLocationsUiState())
    val uiState: StateFlow<ManageLocationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ManageLocationsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ManageLocationsEvent> = _events.asSharedFlow()

    private val pendingJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            locationReader.observeAll().collect { list ->
                _uiState.update { it.copy(locations = list) }
            }
        }
    }

    fun onSwipeDelete(label: String) {
        if (label in _uiState.value.pendingDeletions) return
        _uiState.update { it.copy(pendingDeletions = it.pendingDeletions + label) }
        val job = viewModelScope.launch {
            delay(UNDO_DURATION_MS)
            commitDelete(label)
        }
        pendingJobs[label] = job
        _events.tryEmit(ManageLocationsEvent.ShowUndoSnackbar(label))
    }

    fun onUndoDelete(label: String) {
        pendingJobs.remove(label)?.cancel()
        _uiState.update { it.copy(pendingDeletions = it.pendingDeletions - label) }
    }

    private suspend fun commitDelete(label: String) {
        val target = _uiState.value.locations.firstOrNull { it.label == label }
        if (target != null) {
            locationWriter.delete(target)
            backupScheduler.enqueueBackup()
        }
        pendingJobs.remove(label)
        _uiState.update { it.copy(pendingDeletions = it.pendingDeletions - label) }
    }

    public override fun onCleared() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        super.onCleared()
    }

    companion object {
        private const val UNDO_DURATION_MS = 5_000L
    }
}
