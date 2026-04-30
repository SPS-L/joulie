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
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.AddCarUseCase
import org.spsl.evtracker.domain.usecase.DeleteCarUseCase
import org.spsl.evtracker.domain.usecase.RenameCarUseCase
import javax.inject.Inject

@HiltViewModel
class CarsViewModel @Inject constructor(
    carReader: CarReader,
    settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val addCar: AddCarUseCase,
    private val renameCar: RenameCarUseCase,
    private val deleteCar: DeleteCarUseCase,
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
    fun onRowSetActiveClick(carId: Int) {
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

    fun submitRename(carId: Int, newName: String) {
        viewModelScope.launch {
            when (renameCar(carId, newName)) {
                RenameCarUseCase.Result.NameBlank ->
                    _events.tryEmit(CarsEvent.ShowError(R.string.error_car_name_required))
                RenameCarUseCase.Result.Success -> Unit
            }
        }
    }

    fun confirmDelete(carId: Int) {
        viewModelScope.launch { deleteCar(carId) }
    }
}
