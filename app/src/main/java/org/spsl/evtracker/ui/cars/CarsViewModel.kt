// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.core.model.CarsEvent
import org.spsl.evtracker.core.model.CarsUiState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.EvModelReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.AddCarUseCase
import org.spsl.evtracker.domain.usecase.DeleteCarUseCase
import org.spsl.evtracker.domain.usecase.UpdateCarUseCase
import javax.inject.Inject

@HiltViewModel
class CarsViewModel @Inject constructor(
    carReader: CarReader,
    settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val addCar: AddCarUseCase,
    private val updateCar: UpdateCarUseCase,
    private val deleteCar: DeleteCarUseCase,
    private val evModelReader: EvModelReader,
) : ViewModel() {

    private val _events = MutableSharedFlow<CarsEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CarsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<CarsUiState> =
        combine(carReader.observeAll(), settingsReader.activeCarId) { cars, active ->
            CarsUiState(
                cars = cars.map { CarsUiState.CarRow(it, it.id == active) },
                activeCarId = active,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CarsUiState())

    fun onFabClick() {
        _events.tryEmit(CarsEvent.ShowAddDialog)
    }
    fun onRowEditClick(car: CarEntity) {
        _events.tryEmit(CarsEvent.ShowEditDialog(car))
    }
    fun onRowDeleteClick(car: CarEntity) {
        _events.tryEmit(CarsEvent.ShowDeleteConfirm(car))
    }
    fun onRowSetActiveClick(carId: Long) {
        viewModelScope.launch { settingsWriter.setActiveCarId(carId) }
    }

    fun submitAdd(form: CarFormState) {
        viewModelScope.launch {
            when (addCar(form)) {
                is AddCarUseCase.Result.NameBlank ->
                    _events.tryEmit(CarsEvent.ShowError(R.string.error_car_name_required))
                AddCarUseCase.Result.PersistenceFailed -> Unit
                is AddCarUseCase.Result.Success -> Unit
            }
        }
    }

    /**
     * Persist the Edit Car dialog (TASK-91). Replaces the legacy
     * rename-only path so the make / model / year / battery / WLTP
     * fields the user touches in the dialog actually round-trip to
     * [org.spsl.evtracker.data.local.entity.CarEntity].
     */
    fun submitEdit(carId: Long, form: CarFormState) {
        viewModelScope.launch {
            when (updateCar(carId, form)) {
                UpdateCarUseCase.Result.NameBlank ->
                    _events.tryEmit(CarsEvent.ShowError(R.string.error_car_name_required))
                UpdateCarUseCase.Result.NotFound -> Unit
                UpdateCarUseCase.Result.Success -> Unit
            }
        }
    }

    fun confirmDelete(carId: Long) {
        viewModelScope.launch { deleteCar(carId) }
    }

    /**
     * EV-database autocomplete adapter source (TASK-91). Suspending so
     * the dialog can call it from `lifecycleScope.launch` without
     * blocking the binding thread. Returns the distinct sorted make
     * list from the loaded [EvModelReader] cache.
     */
    suspend fun loadMakes(): List<String> = evModelReader.makes()

    /**
     * Filtered model list for the picked make (TASK-91). Empty list
     * when [make] is blank or the make is unknown.
     */
    suspend fun loadModelsForMake(make: String): List<EvModel> =
        evModelReader.modelsForMake(make)
}
