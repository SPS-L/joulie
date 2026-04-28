package org.spsl.evtracker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.CarWriter
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

class FakeCarReader(initial: List<CarEntity> = emptyList()) : CarReader {
    private val state = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<CarEntity>> = state
    override suspend fun getById(id: Int): CarEntity? = state.value.firstOrNull { it.id == id }
    fun seed(cars: List<CarEntity>) { state.value = cars }
}

class FakeChargeEventQueries(
    private val store: MutableStateFlow<List<ChargeEventEntity>> = MutableStateFlow(emptyList())
) : ChargeEventQueries {
    override fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>> =
        store.map { it.filter { e -> e.carId == carId }.sortedBy { e -> e.eventDate } }
    override suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId && it.eventDate in from..to }.sortedBy { it.eventDate }
    override suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId }.sortedBy { it.eventDate }
    override suspend fun getById(id: Int) = store.value.firstOrNull { it.id == id }
    fun seed(events: List<ChargeEventEntity>) { store.value = events }
    fun current(): List<ChargeEventEntity> = store.value
    fun shareStore(): MutableStateFlow<List<ChargeEventEntity>> = store
}

class FakeChargeEventWriter(
    private val store: MutableStateFlow<List<ChargeEventEntity>>
) : ChargeEventWriter {
    private var nextId = 1L
    override suspend fun insert(event: ChargeEventEntity): Long {
        val id = nextId++
        store.value = store.value + event.copy(id = id.toInt())
        return id
    }
    override suspend fun update(event: ChargeEventEntity) {
        store.value = store.value.map { if (it.id == event.id) event else it }
    }
    override suspend fun delete(event: ChargeEventEntity) {
        store.value = store.value.filter { it.id != event.id }
    }
}

class FakeLocationReader(initial: List<CustomLocationEntity> = emptyList()) : LocationReader {
    val state = MutableStateFlow(initial)
    override fun observeTop5(): Flow<List<CustomLocationEntity>> =
        state.map { it.sortedWith(compareByDescending<CustomLocationEntity> { c -> c.useCount }.thenByDescending { c -> c.lastUsed }).take(5) }
    override fun observeAll(): Flow<List<CustomLocationEntity>> =
        state.map { it.sortedWith(compareByDescending<CustomLocationEntity> { c -> c.useCount }.thenByDescending { c -> c.lastUsed }) }
}

class FakeLocationWriter(
    private val state: MutableStateFlow<List<CustomLocationEntity>> = MutableStateFlow(emptyList())
) : LocationWriter {
    override suspend fun recordUsage(label: String, now: Long) {
        val existing = state.value.firstOrNull { it.label == label }
        state.value = if (existing != null) {
            state.value.map { if (it.label == label) it.copy(useCount = it.useCount + 1, lastUsed = now) else it }
        } else {
            state.value + CustomLocationEntity(id = (state.value.maxOfOrNull { it.id } ?: 0) + 1, label = label, useCount = 1, lastUsed = now)
        }
    }
    override suspend fun delete(location: CustomLocationEntity) {
        state.value = state.value.filter { it.id != location.id }
    }
    fun current(): List<CustomLocationEntity> = state.value
}

class FakeSettingsReader(
    activeCarIdInit: Int = -1,
    primaryMetricInit: String = "km_per_kwh",
    distanceUnitInit: String = "km",
    currencyInit: String = "EUR",
    driveEnabledInit: Boolean = false,
    lastBackupAtInit: Long? = null
) : SettingsReader {
    private val activeCar = MutableStateFlow(activeCarIdInit)
    private val metric = MutableStateFlow(primaryMetricInit)
    private val unit = MutableStateFlow(distanceUnitInit)
    private val curr = MutableStateFlow(currencyInit)
    private val drive = MutableStateFlow(driveEnabledInit)
    private val backupAt = MutableStateFlow(lastBackupAtInit)
    override val activeCarId: Flow<Int> = activeCar
    override val primaryMetric: Flow<String> = metric
    override val distanceUnit: Flow<String> = unit
    override val currency: Flow<String> = curr
    override val driveEnabled: Flow<Boolean> = drive
    override val lastBackupAt: Flow<Long?> = backupAt
    fun setActiveCarId(id: Int) { activeCar.value = id }
    fun setDriveEnabled(enabled: Boolean) { drive.value = enabled }
    fun setLastBackupAt(value: Long?) { backupAt.value = value }
}

class FakeSettingsWriter : SettingsWriter {
    var activeCarId: Int = -1
        private set
    var driveEnabled: Boolean = false
        private set
    var lastBackupAt: Long? = null
        private set
    override suspend fun setActiveCarId(id: Int) { activeCarId = id }
    override suspend fun setDriveEnabled(enabled: Boolean) { driveEnabled = enabled }
    override suspend fun setLastBackupAt(epochMs: Long) { lastBackupAt = epochMs }
}

class FakeBackupScheduler : BackupScheduler {
    var enqueueCount: Int = 0
        private set
    override suspend fun enqueueBackup() { enqueueCount++ }
}

class FakeBackupRepository(
    var remoteJson: String? = null
) : BackupRepository {
    var backupCurrentDataCount: Int = 0
        private set
    override suspend fun backupCurrentData() { backupCurrentDataCount++ }
    override suspend fun readRemoteBackup(): String? = remoteJson
}

class FakeRestoreTransactionRunner(
    val callRecorder: MutableList<String>? = null
) : RestoreTransactionRunner {
    var lastCars: List<CarEntity>? = null
        private set
    var lastEvents: List<ChargeEventEntity>? = null
        private set
    var lastLocations: List<CustomLocationEntity>? = null
        private set
    override suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>
    ) {
        callRecorder?.add("transaction")
        lastCars = cars; lastEvents = events; lastLocations = locations
    }
}

class FakeRestoreSnapshotWriter(
    val callRecorder: MutableList<String>? = null
) : RestoreSnapshotWriter {
    var capturedJson: String? = null
        private set
    override fun write(json: String) {
        callRecorder?.add("snapshot")
        capturedJson = json
    }
}

class FakeSaveChargeEventGateway {
    private val store = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
    val queries = FakeChargeEventQueries(store)
    val writer = FakeChargeEventWriter(store)
    val locationWriter = FakeLocationWriter()
    val locationReader = FakeLocationReader()
    val backupScheduler = FakeBackupScheduler()
    val costParser = org.spsl.evtracker.domain.service.CostParser()

    val useCase: org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase =
        org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase(
            chargeEventQueries = queries,
            chargeEventWriter = writer,
            locationWriter = locationWriter,
            backupScheduler = backupScheduler,
            costParser = costParser
        )

    fun seedEvents(events: List<ChargeEventEntity>) { store.value = events }
}

class FakeCarRepository(initial: List<CarEntity> = emptyList()) : CarReader, CarWriter {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeAll(): Flow<List<CarEntity>> = state
    override suspend fun getById(id: Int): CarEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(car: CarEntity): Long {
        val id = nextId++
        state.value = state.value + car.copy(id = id)
        return id.toLong()
    }

    override suspend fun rename(carId: Int, newName: String) {
        state.value = state.value.map { if (it.id == carId) it.copy(name = newName) else it }
    }

    override suspend fun deleteById(carId: Int) {
        state.value = state.value.filter { it.id != carId }
    }

    fun seed(cars: List<CarEntity>) { state.value = cars }
    fun current(): List<CarEntity> = state.value
}
