package org.spsl.evtracker.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource
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
import org.spsl.evtracker.domain.usecase.NowProvider
import java.io.IOException

/**
 * Deterministic clock for JVM tests. Replaces every direct
 * `System.currentTimeMillis()` call in tests that exercise NowProvider-driven
 * code paths (TASK-28).
 */
class FakeNowProvider(@Volatile var time: Long = 0L) : NowProvider {
    override fun nowMillis() = time
    fun advance(ms: Long) {
        time += ms
    }
}

class FakeCarReader(initial: List<CarEntity> = emptyList()) : CarReader {
    private val state = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<CarEntity>> = state
    override suspend fun getById(id: Int): CarEntity? = state.value.firstOrNull { it.id == id }
    fun seed(cars: List<CarEntity>) {
        state.value = cars
    }
}

class FakeChargeEventQueries(
    private val store: MutableStateFlow<List<ChargeEventEntity>> = MutableStateFlow(emptyList()),
) : ChargeEventQueries {

    /** Incremented every time observeForCar(...) is called, regardless of carId.
     *  Used by ChartsViewModelTest.distanceUnitChange_doesNotResubscribeEventStream
     *  to assert that flipping rendering inputs does not tear down the inner Room
     *  subscription. */
    @Volatile var observeForCarCallCount: Int = 0
        private set

    override fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>> {
        observeForCarCallCount++
        return store.map { it.filter { e -> e.carId == carId }.sortedBy { e -> e.eventDate } }
    }
    override suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId && it.eventDate in from..to }.sortedBy { it.eventDate }
    override suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity> =
        store.value.filter { it.carId == carId }.sortedBy { it.eventDate }
    override suspend fun getById(id: Int) = store.value.firstOrNull { it.id == id }
    fun seed(events: List<ChargeEventEntity>) {
        store.value = events
    }
    fun current(): List<ChargeEventEntity> = store.value
    fun shareStore(): MutableStateFlow<List<ChargeEventEntity>> = store
}

class FakeChargeEventWriter(
    private val store: MutableStateFlow<List<ChargeEventEntity>>,
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
    override suspend fun deleteForCar(carId: Int) {
        store.value = store.value.filter { it.carId != carId }
    }
    override suspend fun deleteAll() {
        store.value = emptyList()
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
    private val state: MutableStateFlow<List<CustomLocationEntity>> = MutableStateFlow(emptyList()),
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
    override suspend fun deleteAll() {
        state.value = emptyList()
    }
    fun current(): List<CustomLocationEntity> = state.value
}

class FakeSettingsReader(
    activeCarIdInit: Int = -1,
    primaryMetricInit: String = "kwh_per_100km",
    distanceUnitInit: String = "km",
    currencyInit: String = "EUR",
    driveEnabledInit: Boolean = false,
    lastBackupAtInit: Long? = null,
    themeInit: String = "system",
    resetInProgressInit: Boolean = false,
    setupCompleteInit: Boolean = true,
) : SettingsReader {
    private val activeCar = MutableStateFlow(activeCarIdInit)
    private val metric = MutableStateFlow(primaryMetricInit)
    private val unit = MutableStateFlow(distanceUnitInit)
    private val curr = MutableStateFlow(currencyInit)
    private val drive = MutableStateFlow(driveEnabledInit)
    private val backupAt = MutableStateFlow(lastBackupAtInit)
    private val themeFlow = MutableStateFlow(themeInit)
    private val resetInProgressFlow = MutableStateFlow(resetInProgressInit)
    private val setupCompleteFlow = MutableStateFlow(setupCompleteInit)
    override val activeCarId: Flow<Int> = activeCar
    override val primaryMetric: Flow<String> = metric
    override val distanceUnit: Flow<String> = unit
    override val currency: Flow<String> = curr
    override val driveEnabled: Flow<Boolean> = drive
    override val lastBackupAt: Flow<Long?> = backupAt
    override val theme: Flow<String> = themeFlow
    override val resetInProgress: Flow<Boolean> = resetInProgressFlow
    override val setupComplete: Flow<Boolean> = setupCompleteFlow
    fun setActiveCarId(id: Int) {
        activeCar.value = id
    }
    fun setDriveEnabled(enabled: Boolean) {
        drive.value = enabled
    }
    fun setLastBackupAt(value: Long?) {
        backupAt.value = value
    }
    fun setTheme(value: String) {
        themeFlow.value = value
    }
    fun setResetInProgress(value: Boolean) {
        resetInProgressFlow.value = value
    }
    fun setSetupComplete(value: Boolean) {
        setupCompleteFlow.value = value
    }
    fun setPrimaryMetric(value: String) {
        metric.value = value
    }
    fun setDistanceUnit(value: String) {
        unit.value = value
    }
    fun setCurrency(value: String) {
        curr.value = value
    }
}

class FakeSettingsWriter(
    val callRecorder: MutableList<String>? = null,
) : SettingsWriter {
    var activeCarId: Int = -1
        private set
    var driveEnabled: Boolean = false
        private set
    var lastBackupAt: Long? = null
        private set
    var theme: String = "system"
        private set
    var primaryMetric: String = "kwh_per_100km"
        private set
    var distanceUnit: String = "km"
        private set
    var currency: String = "EUR"
        private set
    var setupComplete: Boolean = true
        private set
    var resetInProgress: Boolean = false
        private set

    override suspend fun setActiveCarId(id: Int) {
        callRecorder?.add("setActiveCarId($id)")
        activeCarId = id
    }
    override suspend fun setDriveEnabled(enabled: Boolean) {
        callRecorder?.add("setDriveEnabled($enabled)")
        driveEnabled = enabled
    }
    override suspend fun setLastBackupAt(epochMs: Long) {
        callRecorder?.add("setLastBackupAt($epochMs)")
        lastBackupAt = epochMs
    }
    override suspend fun setTheme(value: String) {
        callRecorder?.add("setTheme($value)")
        theme = value
    }
    override suspend fun setPrimaryMetric(metric: String) {
        callRecorder?.add("setPrimaryMetric($metric)")
        primaryMetric = metric
    }
    override suspend fun setDistanceUnit(unit: String) {
        callRecorder?.add("setDistanceUnit($unit)")
        distanceUnit = unit
    }
    override suspend fun setCurrency(code: String) {
        callRecorder?.add("setCurrency($code)")
        currency = code
    }
    override suspend fun setSetupComplete(value: Boolean) {
        callRecorder?.add("setSetupComplete($value)")
        setupComplete = value
    }
    override suspend fun setResetInProgress(value: Boolean) {
        callRecorder?.add("setResetInProgress($value)")
        resetInProgress = value
    }
    override suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String) {
        callRecorder?.add("setPrimaryMetricAndDistanceUnit($metric,$unit)")
        this.primaryMetric = metric
        this.distanceUnit = unit
    }
    override suspend fun completeSetup(metric: String, unit: String, currency: String) {
        callRecorder?.add("completeSetup($metric,$unit,$currency)")
        this.primaryMetric = metric
        this.distanceUnit = unit
        this.currency = currency
        this.setupComplete = true
    }
    override suspend fun markGlobalResetInProgress() {
        callRecorder?.add("markGlobalResetInProgress")
        setupComplete = false
        activeCarId = -1
        resetInProgress = true
    }
}

class FakeBackupScheduler : BackupScheduler {
    var enqueueCount: Int = 0
        private set
    override suspend fun enqueueBackup() {
        enqueueCount++
    }
}

class FakeBackupRepository(
    var remoteJson: String? = null,
    var nextBackupResult: org.spsl.evtracker.domain.backup.BackupResult =
        org.spsl.evtracker.domain.backup.BackupResult.Success,
) : BackupRepository {
    var backupCurrentDataCount: Int = 0
        private set
    override suspend fun backupCurrentData(): org.spsl.evtracker.domain.backup.BackupResult {
        backupCurrentDataCount++
        return nextBackupResult
    }
    override suspend fun readRemoteBackup(): String? = remoteJson
}

class FakeRestoreTransactionRunner(
    val callRecorder: MutableList<String>? = null,
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
        locations: List<CustomLocationEntity>,
    ) {
        callRecorder?.add("transaction")
        lastCars = cars
        lastEvents = events
        lastLocations = locations
    }
}

class FakeRestoreSnapshotWriter(
    val callRecorder: MutableList<String>? = null,
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
    val nowProvider = FakeNowProvider()

    val useCase: org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase =
        org.spsl.evtracker.domain.usecase.SaveChargeEventUseCase(
            chargeEventQueries = queries,
            chargeEventWriter = writer,
            locationWriter = locationWriter,
            backupScheduler = backupScheduler,
            costParser = costParser,
            now = nowProvider,
        )

    fun seedEvents(events: List<ChargeEventEntity>) {
        store.value = events
    }
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

    override suspend fun deleteAll() {
        state.value = emptyList()
    }

    fun seed(cars: List<CarEntity>) {
        state.value = cars
    }
    fun current(): List<CarEntity> = state.value
}

class FakeDriveAuthManager(
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token"),
) : DriveAuthManager {
    var authorizeCallCount = 0
        private set
    var silentCallCount = 0
        private set
    override suspend fun authorize(): DriveAuthManager.AuthResult {
        authorizeCallCount++
        return nextResult
    }
    override suspend fun silentToken(): DriveAuthManager.AuthResult {
        silentCallCount++
        return when (val r = nextResult) {
            is DriveAuthManager.AuthResult.NeedsResolution ->
                DriveAuthManager.AuthResult.Failed("consent required")
            else -> r
        }
    }
}

class FakeDriveRemoteSource : DriveRemoteSource {
    private var fileId: String? = null
    private var body: ByteArray? = null

    /**
     * Throwable to raise from the next [failTimes] calls. After it has been
     * raised that many times, both fields auto-reset and subsequent calls
     * succeed normally. `failTimes = 1` is the legacy single-shot behaviour.
     */
    var failNext: Throwable? = null
    var failTimes: Int = 1

    /** Total method invocations on this fake (find / create / update / download), including ones that succeed. */
    var attemptCount: Int = 0
        private set

    /** Number of times [failNext] was actually raised as an exception. */
    var failuresRaised: Int = 0
        private set

    override suspend fun findBackupFileId(accessToken: String): String? {
        consumeFailure()
        return fileId
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String {
        consumeFailure()
        fileId = "fake-file-id"
        body = jsonBytes
        return fileId!!
    }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        consumeFailure()
        check(this.fileId == fileId) { "fileId mismatch: had=${this.fileId} got=$fileId" }
        body = jsonBytes
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        consumeFailure()
        return body ?: throw IOException("no body for $fileId")
    }

    fun seed(jsonBytes: ByteArray) {
        fileId = "fake-file-id"
        body = jsonBytes
    }
    fun lastUploadedBytes(): ByteArray? = body
    fun seededFileId(): String? = fileId

    private fun consumeFailure() {
        attemptCount++
        val e = failNext ?: return
        failTimes--
        if (failTimes <= 0) {
            failNext = null
            failTimes = 1
        }
        failuresRaised++
        throw e
    }
}

class FakeDataResetTransactionRunner(
    val callRecorder: MutableList<String>? = null,
    private val onClearStores: () -> Unit = {},
) : org.spsl.evtracker.domain.repository.DataResetTransactionRunner {
    var clearCallCount: Int = 0
        private set
    var failNext: Throwable? = null

    override suspend fun clearAllTables() {
        callRecorder?.add("clearAllTables")
        failNext?.let {
            failNext = null
            throw it
        }
        clearCallCount++
        onClearStores()
    }
}

class FakeCsvFileSink : org.spsl.evtracker.domain.backup.CsvFileSink {
    var failNext: Throwable? = null
    var lastCarName: String? = null
    override suspend fun write(carName: String, body: (java.io.Writer) -> Unit): android.net.Uri {
        failNext?.let {
            failNext = null
            throw it
        }
        lastCarName = carName
        body(java.io.StringWriter())
        // android.net.Uri.parse() stubs throw RuntimeException in JVM unit tests; use a mock instead.
        return org.mockito.kotlin.mock()
    }
}
