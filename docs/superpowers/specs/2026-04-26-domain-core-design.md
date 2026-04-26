# Sub-project C: Domain Core — Design

**Date:** 2026-04-26
**Status:** Draft, awaiting user review
**Sources of truth this design defers to:** `DESIGN.md §5` (architecture), `§7` (formulas), `§8` (backup JSON shape); `AGENT_INSTRUCTIONS.md §5` (services + use cases); `TEST_PLAN.md §1` (JVM tests). Where this design narrows scope or makes specific implementation choices, those choices override the broader docs *for Sub-project C only*.

---

## 1. Context — what landed in A+B, what C picks up

### 1.1 Prerequisites

This spec assumes Sub-projects A and B are already merged into `main`. At the time of writing, the latest merge was commit `e1488b8` ("Merge Sub-project B (Data Layer) into main"). The spec's file map and "extends existing X" references are **additive on top of that state** — they are not standalone instructions for a fresh repo.

If the workspace is at any state earlier than `e1488b8`, this design cannot be applied as-is.

### 1.2 What A+B delivered

- **A:** Hilt + DataStore + Navigation Component scaffolding, functional 3-page wizard, `MainActivity` wizard gate, 8 placeholder destinations.
- **B:** Room v3 with three entities (`CarEntity`, `ChargeEventEntity`, `CustomLocationEntity`), three DAOs, `MIGRATION_1_2` + `MIGRATION_2_3`, three narrow concrete repositories (`CarRepository`, `ChargeEventRepository`, `LocationRepository`), `DatabaseModule`, and `SettingsRepository.activeCarId` extension. **No domain logic yet.**

### 1.3 What C introduces

Sub-project C ships the framework-free domain layer that D's UI, E's Drive backup, and F's polish all consume:

- **5 pure services** in `domain/service/`: `StatsCalculator`, `CostParser`, `UnitConverter`, `DateRangeResolver`, `BackupSerializer`.
- **5 use cases** in `domain/usecase/`: `ObserveDashboardStatsUseCase`, `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`, `RestoreBackupUseCase`, `ExportCsvUseCase`.
- **6 narrow role-specific interfaces** in `domain/repository/` that B's existing repos implement (no renames; just `: …` additions to existing class declarations).
- **2 backup interfaces** in `domain/backup/` (`BackupScheduler`, `BackupRepository`) plus **no-op implementations** in `data/backup/` that E will replace by editing the binding module.
- **Pure-data domain models** in `core/model/`: `Stats`, `MonthBucket`, `BackupData`, `DateRange`, `DashboardPeriod`, `DashboardUiState`, `SaveChargeEventInput`, `SaveChargeEventResult`.
- A new Hilt module `di/DomainModule.kt` wiring all 8 interfaces (6 repo + 2 backup).
- Three small `@Query("DELETE FROM …")` additions to B's DAOs to support `RestoreBackupUseCase`'s transactional clear-and-import.

Sub-projects D (Dashboard/ChargeEdit/Cars/History UI), E (Drive auth + real backup/scheduler), and F (Charts/CSV/Settings/ManageLocations) all depend on C.

---

## 2. Scope and acceptance criteria

### 2.1 In scope

- 5 pure services, 5 use cases, 6 repository interfaces, 2 backup interfaces + 2 no-op implementations.
- Pure-data domain models for `Stats`, `MonthBucket`, `BackupData`, `DateRange`, `DashboardPeriod`, `DashboardUiState`, `SaveChargeEventInput`, `SaveChargeEventResult`.
- Hilt `DomainModule` providing 8 `@Binds` declarations.
- B-side touches: append `: SomeReader, SomeWriter` to the four existing repository class declarations; add `deleteAll()` `@Query` methods to the three DAOs.

### 2.2 Out of scope

| Concern | Lands in |
|---|---|
| Real Drive backup or restore I/O | E (replaces `NoOpBackupScheduler`/`NoOpBackupRepository` bindings) |
| `DriveAuthManager` and Google Drive client wiring | E |
| `SettingsRepository.driveEnabled` Flow accessor | E (only consumed by E's scheduler/UI) |
| Dashboard, ChargeEdit, Cars, History ViewModels and UI | D |
| MPAndroidChart wiring (charts UI) | F (consumes `StatsCalculator.computeMonthlyBuckets` shipped now) |
| Settings screen, Manage Locations screen | F |
| `ExportCsvUseCase` per-row efficiency column (delta-odo across rows) | Not specified; F may add if requested |

### 2.3 Acceptance criteria

1. `./gradlew assembleDebug` succeeds.
2. `./gradlew test` passes — JVM count grows from 14 (A+B) to **61** (47 new JVM tests across 11 new test classes).
3. `./gradlew connectedDebugAndroidTest` is **expected** to pass on a connected emulator (compile-only verification in this development environment) — 25 instrumented tests total (3 from A's `WizardFlowTest` + 22 from B; C adds none).
4. Manual smoke: app still launches, wizard gate still works, dashboard placeholder still appears post-wizard. No-op backup wiring is invisible to users (no-op `enqueueBackup` does nothing; `readRemoteBackup` returns null).

---

## 3. Architecture additions

```
app/src/main/java/org/spsl/evtracker/
  core/
    model/                                       (new package)
      Stats.kt
      MonthBucket.kt
      BackupData.kt                              + nested CarDto/ChargeEventDto/CustomLocationDto + BackupVersionMismatch
      DateRange.kt
      DashboardPeriod.kt                         (sealed class)
      DashboardUiState.kt                        (data class + nested EmptyState sealed + ChargeTypeFilter enum)
      SaveChargeEventInput.kt                    (data class + CostInput + SaveChargeEventResult sealed)
  domain/                                        (new package)
    repository/
      CarReader.kt                               interface
      ChargeEventQueries.kt                      interface
      ChargeEventWriter.kt                       interface
      LocationReader.kt                          interface
      LocationWriter.kt                          interface
      SettingsReader.kt                          interface
    backup/
      BackupScheduler.kt                         interface
      BackupRepository.kt                        interface
    service/
      StatsCalculator.kt
      CostParser.kt                              + enum CostMode (re-exported from core/model? — see §5.2)
      UnitConverter.kt
      DateRangeResolver.kt
      BackupSerializer.kt
    usecase/
      ObserveDashboardStatsUseCase.kt
      SaveChargeEventUseCase.kt
      DeleteChargeEventUseCase.kt
      RestoreBackupUseCase.kt
      ExportCsvUseCase.kt
  data/
    backup/                                      (new package)
      NoOpBackupScheduler.kt
      NoOpBackupRepository.kt
  di/
    DomainModule.kt                              (new) 6 @Binds for repo interfaces, 2 @Binds for backup interfaces
```

Plus B-side modifications:

| File | Change |
|---|---|
| `data/repository/CarRepository.kt` | Append `: CarReader` to class declaration. No method changes — `observeAll` and `getById` are already present and match the interface. |
| `data/repository/ChargeEventRepository.kt` | Append `: ChargeEventQueries, ChargeEventWriter`. |
| `data/repository/LocationRepository.kt` | Append `: LocationReader, LocationWriter`. |
| `data/repository/SettingsRepository.kt` | Append `: SettingsReader`. |
| `data/local/dao/CarDao.kt` | Add `@Query("DELETE FROM cars") suspend fun deleteAll()`. |
| `data/local/dao/ChargeEventDao.kt` | Add `@Query("DELETE FROM charge_events") suspend fun deleteAll()`. |
| `data/local/dao/CustomLocationDao.kt` | Add `@Query("DELETE FROM custom_locations") abstract suspend fun deleteAll()`. |

Plus tests under `app/src/test/java/org/spsl/evtracker/{core,domain}/...` and a small fakes package under `app/src/test/java/org/spsl/evtracker/testing/` shared across use case tests.

---

## 4. Repository interfaces (`domain/repository/`)

Six narrow, role-specific interfaces. Each use case depends only on the surface it actually needs — Interface Segregation Principle. The interfaces deliberately name *roles*, not full repository capabilities. B's existing repos implement multiple of them.

```kotlin
// CarReader.kt
interface CarReader {
    fun observeAll(): Flow<List<CarEntity>>
    suspend fun getById(id: Int): CarEntity?
}

// ChargeEventQueries.kt
interface ChargeEventQueries {
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>>
    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity>
    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity>
    suspend fun getById(id: Int): ChargeEventEntity?
}

// ChargeEventWriter.kt
interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)
}

// LocationReader.kt
interface LocationReader {
    fun observeTop5(): Flow<List<CustomLocationEntity>>
    fun observeAll(): Flow<List<CustomLocationEntity>>
}

// LocationWriter.kt
interface LocationWriter {
    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis())
    suspend fun delete(location: CustomLocationEntity)
}

// SettingsReader.kt
interface SettingsReader {
    val activeCarId: Flow<Int>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
}
```

**`SettingsReader` deliberately omits `setupComplete` and `theme`.** Those are the wizard's and F's theme switcher's concerns — no use case in C reads them. D/E/F can extend `SettingsReader` (or add narrower interfaces) as they need new fields.

B's repository class declarations get one-line modifications:

```kotlin
// data/repository/CarRepository.kt
@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) : CarReader {     // <-- added
    // existing methods unchanged
}

// data/repository/ChargeEventRepository.kt
@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao
) : ChargeEventQueries, ChargeEventWriter {     // <-- added
    // existing methods unchanged
}

// data/repository/LocationRepository.kt
@Singleton
class LocationRepository @Inject constructor(
    private val customLocationDao: CustomLocationDao
) : LocationReader, LocationWriter {     // <-- added
    // existing methods unchanged
}

// data/repository/SettingsRepository.kt
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsReader {     // <-- added
    // existing methods unchanged
}
```

**No new methods need to be added to B's repos.** Each interface is a strict subset of what the existing class already exposes.

---

## 5. Pure services (`domain/service/`)

### 5.1 `UnitConverter`

```kotlin
// domain/service/UnitConverter.kt
object UnitConverter {
    private const val KM_PER_MI = 1.609344

    fun kmToMiles(km: Double): Double = km / KM_PER_MI
    fun milesToKm(mi: Double): Double = mi * KM_PER_MI
    fun kmPerKwhToMiPerKwh(kmPerKwh: Double): Double = kmToMiles(kmPerKwh)
}
```

Object (no state, no DI). The `0.621371` factor used elsewhere in DESIGN.md is the rounded reciprocal of `1.609344`; this implementation uses the canonical km↔mi factor directly to avoid rounding drift.

### 5.2 `CostParser`

```kotlin
// domain/service/CostParser.kt
enum class CostMode { TOTAL, PER_KWH }

class CostParser @Inject constructor() {
    fun parse(value: Double?, kwh: Double, mode: CostMode): Pair<Double?, Double?> {
        if (value == null || value <= 0.0 || kwh <= 0.0) return Pair(null, null)
        return when (mode) {
            CostMode.TOTAL -> Pair(value, value / kwh)
            CostMode.PER_KWH -> Pair(value * kwh, value)
        }
    }
}
```

Returns `Pair(costTotal, costPerKwh)`. Both `null` when input is invalid (cost ≤ 0, kwh ≤ 0, or value missing). `CostMode` enum lives in the same file.

### 5.3 `DateRangeResolver`

```kotlin
// domain/service/DateRangeResolver.kt
class DateRangeResolver @Inject constructor() {

    fun resolve(period: DashboardPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange =
        when (period) {
            DashboardPeriod.SincePreviousCharge -> DateRange(0L, nowMillis)            // unbounded; use case picks last 2 events
            DashboardPeriod.Last7Days  -> DateRange(nowMillis - 7  * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Last30Days -> DateRange(nowMillis - 30 * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Year       -> DateRange(startOfYear(nowMillis), nowMillis)
            is DashboardPeriod.Custom  -> DateRange(period.fromMillis, period.toMillis)
        }

    private fun startOfYear(nowMillis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object { const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000 }
}
```

Tests pass an explicit `nowMillis` for determinism. `Calendar` is JDK-only (no Android dependency); JVM tests run fine. The Year boundary uses the JVM default time zone, matching what the user sees on their device.

### 5.4 `StatsCalculator`

Implements DESIGN.md §7 verbatim — delta-odometer pairs with weighted aggregates, multi-currency guard. The `Stats` model carries all 3 efficiency metrics so the dashboard can render any of them as primary without re-running the calculation.

```kotlin
// core/model/Stats.kt
data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgKmPerKwh: Double?,
    val avgKwhPer100Km: Double?,
    val avgMiPerKwh: Double?,
    val chargeCount: Int,
    val costPerKm: Double?,
    val costPer100Km: Double?,
    val mixedCurrency: Boolean             // true ⇒ all cost stats null even if cost data exists
)

// core/model/MonthBucket.kt
data class MonthBucket(
    val year: Int,
    val month: Int,                         // 1..12
    val totalKwh: Double,
    val totalCost: Double?,                 // null if no costed events that month OR mixed currency overall
    val currency: String?                   // null if totalCost is null
)
```

```kotlin
// domain/service/StatsCalculator.kt
class StatsCalculator @Inject constructor() {

    fun computeStats(events: List<ChargeEventEntity>, label: String): Stats {
        val totalKwhAll = events.sumOf { it.kwhAdded }
        val chargeCount = events.size

        if (events.size < 2) {
            return Stats(label, totalKwhAll, 0.0, null, null, null, chargeCount, null, null, mixedCurrency = false)
        }

        val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
        val mixedCurrency = costedCurrencies.size > 1

        val sorted = events.sortedBy { it.eventDate }
        var pairKwh = 0.0
        var totalDist = 0.0
        var totalCost = 0.0
        var costCount = 0

        for (i in 1 until sorted.size) {
            val dist = sorted[i].odometerKm - sorted[i - 1].odometerKm
            if (dist > 0) {
                pairKwh += sorted[i].kwhAdded
                totalDist += dist
                if (!mixedCurrency) {
                    sorted[i].costTotal?.let { totalCost += it; costCount++ }
                }
            }
        }

        val avgKmPerKwh    = if (pairKwh > 0)  totalDist / pairKwh else null
        val avgKwhPer100Km = avgKmPerKwh?.let { 100.0 / it }
        val avgMiPerKwh    = avgKmPerKwh?.let { UnitConverter.kmPerKwhToMiPerKwh(it) }
        val costPerKm      = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
        val costPer100Km   = costPerKm?.times(100.0)

        return Stats(label, totalKwhAll, totalDist, avgKmPerKwh, avgKwhPer100Km, avgMiPerKwh, chargeCount, costPerKm, costPer100Km, mixedCurrency)
    }

    fun computeMonthlyBuckets(events: List<ChargeEventEntity>): List<MonthBucket> {
        // Group by (year, month) of eventDate. Sum kwhAdded per bucket.
        // Sum costTotal per bucket only if all costed events in that bucket share the same currency
        // AND the OVERALL events list is single-currency (matches the dashboard "mixed-currency banner" rule).
        // Return sorted ascending by (year, month).
    }
}
```

Test contract (TEST_PLAN §1.2) requires:
- `emptyEvents_returnsZeroStats` — empty list → totalKwh=0, chargeCount=0, all efficiency/cost stats null, mixedCurrency=false.
- `singleEvent_totalsButNoEfficiency` — 1 event, kwh=42 → totalKwh=42, chargeCount=1, all efficiency stats null.
- `twoEvents_correctEfficiency` — odo 0→100km, 20kWh → avgKmPerKwh=5.0; avgKwhPer100Km=20.0; avgMiPerKwh ≈ 3.107.
- `multipleEvents_sumCorrect` — 3 events with positive deltas → weighted aggregate matches manual calc.
- `negativeOdometerDelta_skipped` — events with odo decreasing → those pairs contribute nothing to dist/efficiency/cost.
- `monthlyAggregation_correctBuckets` — events spanning 3 calendar months → 3 buckets, correct kWh sums, sorted ascending.

### 5.5 `BackupSerializer`

Gson-based round-trip on the `BackupData` shape from DESIGN.md §8. Field naming uses `@SerializedName` to map camelCase Kotlin to snake_case JSON. `fromJson` validates `backup_version == 3`, throws `BackupVersionMismatch` on bad version.

```kotlin
// core/model/BackupData.kt
data class BackupData(
    @SerializedName("backup_version") val backupVersion: Int = 3,
    @SerializedName("exported_at")   val exportedAt: String,        // ISO 8601 UTC
    @SerializedName("cars")          val cars: List<CarDto>,
    @SerializedName("charge_events") val chargeEvents: List<ChargeEventDto>,
    @SerializedName("custom_locations") val customLocations: List<CustomLocationDto>
) {
    companion object {
        const val CURRENT_VERSION = 3

        fun fromEntities(
            cars: List<CarEntity>,
            events: List<ChargeEventEntity>,
            locations: List<CustomLocationEntity>,
            now: Long = System.currentTimeMillis()
        ): BackupData = BackupData(
            backupVersion = CURRENT_VERSION,
            exportedAt = isoUtcInstant(now),
            cars = cars.map { CarDto.fromEntity(it) },
            chargeEvents = events.map { ChargeEventDto.fromEntity(it) },
            customLocations = locations.map { CustomLocationDto.fromEntity(it) }
        )
    }

    fun toEntities(): Triple<List<CarEntity>, List<ChargeEventEntity>, List<CustomLocationEntity>> =
        Triple(cars.map { it.toEntity() }, chargeEvents.map { it.toEntity() }, customLocations.map { it.toEntity() })
}

class BackupVersionMismatch(val actual: Int) :
    RuntimeException("Backup version $actual is incompatible with current version ${BackupData.CURRENT_VERSION}")
```

DTOs (`CarDto`, `ChargeEventDto`, `CustomLocationDto`) are nested data classes with `@SerializedName` snake_case keys per DESIGN.md §8 and `fromEntity`/`toEntity` companion methods. `isoUtcInstant(now)` formats via `java.time.Instant.ofEpochMilli(now).toString()` (JVM-only, no Android dependency).

```kotlin
// domain/service/BackupSerializer.kt
class BackupSerializer @Inject constructor() {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(data: BackupData): String = gson.toJson(data)

    fun fromJson(json: String): BackupData {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed.backupVersion != BackupData.CURRENT_VERSION) {
            throw BackupVersionMismatch(parsed.backupVersion)
        }
        return parsed
    }
}
```

`disableHtmlEscaping()` keeps the JSON readable (no `<` etc. for `<`/`>`/`&`).

---

## 6. Domain models (`core/model/`)

### 6.1 `DashboardPeriod`

```kotlin
sealed class DashboardPeriod {
    object SincePreviousCharge : DashboardPeriod()
    object Last7Days : DashboardPeriod()
    object Last30Days : DashboardPeriod()
    object Year : DashboardPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : DashboardPeriod()
}
```

### 6.2 `DateRange`

```kotlin
data class DateRange(val startMillis: Long, val endMillis: Long)
```

Inclusive on both ends.

### 6.3 `DashboardUiState`

```kotlin
data class DashboardUiState(
    val emptyState: EmptyState? = null,             // null = data, non-null = empty state
    val stats: Stats? = null,                       // null when emptyState != null
    val showMultiCurrencyBanner: Boolean = false
)

sealed class EmptyState {
    object NoCar : EmptyState()
    object NoEvents : EmptyState()
}

enum class ChargeTypeFilter { ALL, AC, DC }
```

The use case sets `emptyState = NoCar` when `activeCarId == -1` or no cars exist. `emptyState = NoEvents` when there are no events for the active car in the resolved range. `showMultiCurrencyBanner` echoes `stats.mixedCurrency` when stats are present.

### 6.4 `SaveChargeEventInput` / `Result`

```kotlin
data class SaveChargeEventInput(
    val eventId: Int? = null,           // null = insert; non-null = update existing
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: String,             // "AC" | "DC"
    val costInput: CostInput? = null,
    val location: String? = null,
    val note: String = ""
)

data class CostInput(
    val value: Double,
    val mode: CostMode,
    val currency: String
)

sealed class SaveChargeEventResult {
    data class Success(val eventId: Long) : SaveChargeEventResult()
    object OdometerNotIncreasing : SaveChargeEventResult()
}
```

### 6.5 `RestoreResult`

```kotlin
sealed class RestoreResult {
    object NoRemoteBackup : RestoreResult()
    data class VersionMismatch(val actualVersion: Int) : RestoreResult()
    data class Success(val carCount: Int, val eventCount: Int, val locationCount: Int) : RestoreResult()
}
```

---

## 7. Backup interfaces + no-op stubs

### 7.1 Interfaces (`domain/backup/`)

```kotlin
interface BackupScheduler {
    fun enqueueBackup()
}

interface BackupRepository {
    /** Serialize current local state and upload to Drive. */
    suspend fun backupCurrentData()

    /** Download evtracker_backup.json from the App Data folder. Returns null if no remote file. */
    suspend fun readRemoteBackup(): String?
}
```

### 7.2 No-op implementations (`data/backup/`)

```kotlin
@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override fun enqueueBackup() {
        // No-op until E lands — see DomainModule binding swap.
    }
}

@Singleton
class NoOpBackupRepository @Inject constructor() : BackupRepository {
    override suspend fun backupCurrentData() {
        // No-op until E lands.
    }
    override suspend fun readRemoteBackup(): String? = null
}
```

E's plan: edit `DomainModule.kt` to bind `WorkManagerBackupScheduler` and `DriveBackupRepository` (E's new classes) instead of the no-ops. No other domain-layer changes needed.

---

## 8. Use cases (`domain/usecase/`)

### 8.1 `ObserveDashboardStatsUseCase`

```kotlin
class ObserveDashboardStatsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val dateRangeResolver: DateRangeResolver
) {
    fun observe(period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { activeCarId, cars ->
            Pair(activeCarId, cars)
        }.flatMapLatest { (activeCarId, cars) ->
            when {
                cars.isEmpty() || activeCarId == -1 -> flowOf(DashboardUiState(emptyState = EmptyState.NoCar))
                else -> buildStatsFlow(activeCarId, period, filter)
            }
        }
    }

    private fun buildStatsFlow(carId: Int, period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState> = flow {
        val events = when (period) {
            DashboardPeriod.SincePreviousCharge -> {
                chargeEventQueries.getAllForCarSorted(carId).takeLast(2)
            }
            else -> {
                val range = dateRangeResolver.resolve(period)
                chargeEventQueries.getInRange(carId, range.startMillis, range.endMillis)
            }
        }
        val filtered = when (filter) {
            ChargeTypeFilter.ALL -> events
            ChargeTypeFilter.AC -> events.filter { it.chargeType == "AC" }
            ChargeTypeFilter.DC -> events.filter { it.chargeType == "DC" }
        }
        if (filtered.isEmpty()) {
            emit(DashboardUiState(emptyState = EmptyState.NoEvents))
        } else {
            val stats = statsCalculator.computeStats(filtered, label = period.toString())
            emit(DashboardUiState(stats = stats, showMultiCurrencyBanner = stats.mixedCurrency))
        }
    }
}
```

The flow re-emits when `activeCarId` changes or when the car list changes. It does NOT re-emit on every event insert — that would require observing per-car events. For Sub-project C's scope, suspend fetches inside the flow are acceptable; D can refine to a hot per-car observation if performance demands.

### 8.2 `SaveChargeEventUseCase`

```kotlin
class SaveChargeEventUseCase @Inject constructor(
    private val chargeEventQueries: ChargeEventQueries,
    private val chargeEventWriter: ChargeEventWriter,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler,
    private val costParser: CostParser
) {
    suspend operator fun invoke(input: SaveChargeEventInput): SaveChargeEventResult {
        // 1. Validate odometer > previous event's (ignoring the same id when updating).
        val sorted = chargeEventQueries.getAllForCarSorted(input.carId)
        val previous = sorted.lastOrNull { it.eventDate < input.eventDate && it.id != input.eventId }
        if (previous != null && input.odometerKm <= previous.odometerKm) {
            return SaveChargeEventResult.OdometerNotIncreasing
        }

        // 2. Normalize cost.
        val (costTotal, costPerKwh) = input.costInput?.let { ci ->
            costParser.parse(ci.value, input.kwhAdded, ci.mode)
        } ?: Pair(null, null)
        val currency = if (costTotal != null) input.costInput?.currency else null

        val entity = ChargeEventEntity(
            id = input.eventId ?: 0,
            carId = input.carId,
            eventDate = input.eventDate,
            odometerKm = input.odometerKm,
            kwhAdded = input.kwhAdded,
            chargeType = input.chargeType,
            costTotal = costTotal,
            costPerKwh = costPerKwh,
            currency = currency,
            location = input.location?.takeIf { it.isNotBlank() },
            note = input.note
        )

        // 3. Persist (insert or update by id).
        val savedId: Long = if (input.eventId == null) {
            chargeEventWriter.insert(entity)
        } else {
            chargeEventWriter.update(entity)
            input.eventId.toLong()
        }

        // 4. Record location usage.
        input.location?.takeIf { it.isNotBlank() }?.let { locationWriter.recordUsage(it) }

        // 5. Enqueue backup.
        backupScheduler.enqueueBackup()

        return SaveChargeEventResult.Success(savedId)
    }
}
```

Validation carve-out: when updating event #X, the "previous" event is the one immediately before #X by `eventDate`, *excluding* #X itself. This prevents an in-place update from triggering a false-positive odometer regression against its own row.

### 8.3 `DeleteChargeEventUseCase`

```kotlin
class DeleteChargeEventUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(event: ChargeEventEntity) {
        chargeEventWriter.delete(event)
        backupScheduler.enqueueBackup()
    }
}
```

The undo window (Snackbar timer) is a UI/ViewModel concern and lives in D. This use case fires only after the window expires.

### 8.4 `RestoreBackupUseCase`

The use case takes two abstractions instead of touching Android directly: a `RestoreTransactionRunner` that performs the transactional clear-and-import, and a `RestoreSnapshotWriter` that writes the cache snapshot file. Both have production implementations in `data/backup/` and JVM-testable fakes.

```kotlin
// domain/backup/RestoreTransactionRunner.kt
interface RestoreTransactionRunner {
    /** Atomically deletes all rows then inserts the supplied entities. */
    suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>
    )
}

// domain/backup/RestoreSnapshotWriter.kt
interface RestoreSnapshotWriter {
    /** Persists the JSON to a deterministic location (cacheDir/last_overwritten_backup.json in production). */
    fun write(json: String)
}

// domain/usecase/RestoreBackupUseCase.kt
class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val backupSerializer: BackupSerializer,
    private val transactionRunner: RestoreTransactionRunner,
    private val snapshotWriter: RestoreSnapshotWriter,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(): RestoreResult {
        // 1. Fetch remote.
        val json = backupRepository.readRemoteBackup() ?: return RestoreResult.NoRemoteBackup

        // 2. Parse + version check.
        val parsed = try {
            backupSerializer.fromJson(json)
        } catch (e: BackupVersionMismatch) {
            return RestoreResult.VersionMismatch(e.actual)
        }

        // 3. Snapshot current local state to cacheDir/last_overwritten_backup.json.
        val currentCars = carReader.observeAll().first()
        val currentEvents = currentCars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val currentLocations = locationReader.observeAll().first()
        val snapshot = BackupData.fromEntities(currentCars, currentEvents, currentLocations)
        snapshotWriter.write(backupSerializer.toJson(snapshot))

        // 4. Transactional clear-and-import.
        val (newCars, newEvents, newLocations) = parsed.toEntities()
        transactionRunner.replaceAll(newCars, newEvents, newLocations)

        // 5. Schedule backup so post-restore state is what future backups upload.
        backupScheduler.enqueueBackup()

        return RestoreResult.Success(
            carCount = newCars.size,
            eventCount = newEvents.size,
            locationCount = newLocations.size
        )
    }
}
```

```kotlin
// data/backup/RoomRestoreTransactionRunner.kt
@Singleton
class RoomRestoreTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : RestoreTransactionRunner {
    override suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>
    ) {
        database.withTransaction {                                       // Room KTX suspending transaction
            database.chargeEventDao().deleteAll()                        // events first (deterministic; avoids relying on cascade ordering)
            database.customLocationDao().deleteAll()
            database.carDao().deleteAll()
            cars.forEach { database.carDao().insert(it) }
            events.forEach { database.chargeEventDao().insert(it) }
            locations.forEach { database.customLocationDao().insertIfMissing(it) }
        }
    }
}

// data/backup/CacheDirRestoreSnapshotWriter.kt
@Singleton
class CacheDirRestoreSnapshotWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : RestoreSnapshotWriter {
    override fun write(json: String) {
        File(context.cacheDir, "last_overwritten_backup.json").writeText(json)
    }
}
```

`DomainModule` adds two more `@Binds`: `RoomRestoreTransactionRunner` → `RestoreTransactionRunner` and `CacheDirRestoreSnapshotWriter` → `RestoreSnapshotWriter`.

Notes:
- `database.withTransaction { … }` is the suspend-friendly Room KTX equivalent of `runInTransaction { runBlocking { … } }`. Requires `androidx.room:room-ktx` (already in B's deps).
- The snapshot is written for the future "Undo restore" affordance in F's Settings screen. C lays the file down; F reads it.
- Tests: `FakeRestoreTransactionRunner` records the lists it was called with; `FakeRestoreSnapshotWriter` captures the written JSON in memory. Both are ~10 lines.

### 8.5 `ExportCsvUseCase`

The use case writes CSV through a `CsvFileSink` abstraction so all Android-specific bits (`getExternalFilesDir`, `FileProvider`) live in one production class and the CSV-formatting logic is JVM-testable in isolation.

```kotlin
// domain/backup/CsvFileSink.kt  (placed under domain/backup/ alongside the other infrastructure interfaces)
interface CsvFileSink {
    /**
     * Allocates a CSV file for the given car/timestamp, invokes [body] to write its contents,
     * then returns a shareable URI for the file. Production: external Downloads + FileProvider.
     * Tests: a tempFolder file + a stub URI.
     */
    suspend fun write(carName: String, body: (Writer) -> Unit): Uri
}

// domain/usecase/ExportCsvUseCase.kt
class ExportCsvUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val csvFileSink: CsvFileSink
) {
    suspend fun export(carId: Int, useKm: Boolean): Uri {
        val car = carReader.getById(carId) ?: throw IllegalArgumentException("Unknown carId=$carId")
        val events = chargeEventQueries.getAllForCarSorted(carId)
        return csvFileSink.write(car.name) { writer ->
            writeCsv(writer, events, useKm)
        }
    }

    /** JVM-testable directly: pass a StringWriter and assert its contents. */
    internal fun writeCsv(writer: Writer, events: List<ChargeEventEntity>, useKm: Boolean) {
        writer.appendLine("date,odometer_${if (useKm) "km" else "miles"},kwh,charge_type,location,cost_total,currency,note")
        for (e in events) {
            val odo = if (useKm) e.odometerKm else UnitConverter.kmToMiles(e.odometerKm)
            writer.append(java.time.Instant.ofEpochMilli(e.eventDate).toString()).append(',')
                  .append(odo.toString()).append(',')
                  .append(e.kwhAdded.toString()).append(',')
                  .append(e.chargeType).append(',')
                  .append(csvEscape(e.location ?: "")).append(',')
                  .append(e.costTotal?.toString() ?: "").append(',')
                  .append(e.currency ?: "").append(',')
                  .appendLine(csvEscape(e.note))
        }
    }

    private fun csvEscape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"${s.replace("\"", "\"\"")}\""
        else s
}
```

```kotlin
// data/backup/AndroidCsvFileSink.kt
@Singleton
class AndroidCsvFileSink @Inject constructor(
    @ApplicationContext private val context: Context
) : CsvFileSink {
    override suspend fun write(carName: String, body: (Writer) -> Unit): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = carName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "evtracker_${safeName}_$timestamp.csv")
        file.bufferedWriter().use(body)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
```

`DomainModule` adds one more `@Binds`: `AndroidCsvFileSink` → `CsvFileSink`.

Tests target `writeCsv(...)` directly with a `StringWriter` — no Android, no file I/O, no `CsvFileSink` needed at test time.

Per-row efficiency column (delta-odo across rows) is **deliberately omitted** in C's scope. DESIGN.md §6 mentions "first event efficiency blank" as a hint that the column might exist, but the spec doesn't mandate the column itself. F can add it if user feedback demands.

---

## 9. Hilt wiring (`di/DomainModule.kt`)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // Repository interface bindings — B's classes implement the interfaces directly.
    @Binds abstract fun bindCarReader(impl: CarRepository): CarReader
    @Binds abstract fun bindChargeEventQueries(impl: ChargeEventRepository): ChargeEventQueries
    @Binds abstract fun bindChargeEventWriter(impl: ChargeEventRepository): ChargeEventWriter
    @Binds abstract fun bindLocationReader(impl: LocationRepository): LocationReader
    @Binds abstract fun bindLocationWriter(impl: LocationRepository): LocationWriter
    @Binds abstract fun bindSettingsReader(impl: SettingsRepository): SettingsReader

    // Backup interface bindings — no-op until E swaps these.
    @Binds abstract fun bindBackupScheduler(impl: NoOpBackupScheduler): BackupScheduler
    @Binds abstract fun bindBackupRepository(impl: NoOpBackupRepository): BackupRepository

    // Restore-flow infrastructure (Android-touching; JVM tests use fakes).
    @Binds abstract fun bindRestoreTransactionRunner(impl: RoomRestoreTransactionRunner): RestoreTransactionRunner
    @Binds abstract fun bindRestoreSnapshotWriter(impl: CacheDirRestoreSnapshotWriter): RestoreSnapshotWriter

    // CSV export infrastructure.
    @Binds abstract fun bindCsvFileSink(impl: AndroidCsvFileSink): CsvFileSink
}
```

`@Binds` generates no factory class (cheaper than `@Provides`). Hilt's `@Singleton` on the implementations is honored.

Existing `AppModule` and `DatabaseModule` are unchanged.

---

## 10. Tests

### 10.1 Shared test fakes (`app/src/test/java/.../testing/`)

```text
testing/
  FakeCarReader.kt
  FakeChargeEventQueries.kt
  FakeChargeEventWriter.kt           // wraps FakeChargeEventQueries' state for round-trip
  FakeLocationReader.kt
  FakeLocationWriter.kt
  FakeSettingsReader.kt
  FakeBackupScheduler.kt              // counter on enqueueBackup()
  FakeBackupRepository.kt             // settable readRemoteBackup return
```

Each fake is ~30-40 lines. They share state across read/write interface pairs where the same in-memory list is read by `*Queries` and written by `*Writer`.

Example:
```kotlin
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
}

class FakeChargeEventWriter(private val queries: FakeChargeEventQueries) : ChargeEventWriter {
    private var nextId = 1L
    override suspend fun insert(event: ChargeEventEntity): Long {
        val id = nextId++
        queries.seed(queries.current() + event.copy(id = id.toInt()))
        return id
    }
    override suspend fun update(event: ChargeEventEntity) {
        queries.seed(queries.current().map { if (it.id == event.id) event else it })
    }
    override suspend fun delete(event: ChargeEventEntity) {
        queries.seed(queries.current().filter { it.id != event.id })
    }
}
```

### 10.2 Service tests (JVM, no Android deps)

| Class | Cases | Coverage |
|---|---|---|
| `UnitConverterTest` | 4 | `kmToMiles_positive` (100→62.137±0.001), `milesToKm_positive` (62.137→100±0.001), `kmToMiles_zero`, `efficiency_kmPerKwh_to_miPerKwh` (5.0→3.107±0.001). |
| `CostParserTest` | 7 | Per `TEST_PLAN §1.3`: `costZero_returnsNull`, `costBlank_returnsNull`, `costNegative_returnsNull`, `costTotal_derivesPerKwh`, `costPerKwh_derivesTotal`, `kwhZero_returnsNull`, `bothEntered_totalWins` (just covers the TOTAL-mode call path; the input-validation rule that "total wins" lives in the use case, not the parser — the parser only handles one mode at a time). |
| `DateRangeResolverTest` | 5 | Each period case with a fixed `nowMillis`: `last7Days`, `last30Days`, `year_isJanuary1OfCurrentYear`, `custom_returnsInputs`, `sincePreviousCharge_isFromZeroToNow`. |
| `StatsCalculatorTest` | 6 | Per `TEST_PLAN §1.2`: `emptyEvents_returnsZeroStats`, `singleEvent_totalsButNoEfficiency`, `twoEvents_correctEfficiency`, `multipleEvents_sumCorrect`, `negativeOdometerDelta_skipped`, `monthlyAggregation_correctBuckets`. |
| `StatsCalculatorCostTest` | 5 | Per `TEST_PLAN §1.4`: `allCostNull_costStatsNull`, `mixedCost_sumNonNullOnly`, `singleCostEvent_correct`, `multipleCurrencies_costStatsNull`, `singleCurrencyAcrossPeriod_costStatsComputed`. |
| `BackupSerializerTest` | 4 | `roundTrip_preservesAllFields` (build BackupData with sample entities, toJson, fromJson, assert equality), `fromJson_throwsOnVersionMismatch`, `toJson_isHtmlEscapeFree`, `fromEntities_setsExportedAtToIso8601Utc`. |

**Service tests subtotal: 31**

### 10.3 Use case tests (JVM with shared fakes)

| Class | Cases | Coverage |
|---|---|---|
| `ObserveDashboardStatsUseCaseTest` | 4 | `noCars_emitsNoCarEmptyState`, `activeCarMinusOne_emitsNoCarEmptyState`, `noEventsForActiveCar_emitsNoEventsEmptyState`, `eventsPresent_emitsStatsAndMultiCurrencyFlag`. |
| `SaveChargeEventUseCaseTest` | 5 | `insert_success_recordsLocationAndEnqueuesBackup`, `update_success_keepsId`, `insertOdometerNotIncreasing_returnsResultAndPersistsNothing`, `updateOdometerCheck_ignoresOwnId`, `costInputZero_costFieldsAreNull` (verifies CostParser integration). |
| `DeleteChargeEventUseCaseTest` | 1 | `invoke_deletesAndEnqueuesBackup`. |
| `RestoreBackupUseCaseTest` | 4 | `noRemoteBackup_returnsShortCircuit`, `versionMismatch_propagatesActualVersion`, `success_clearsAndImportsAndEnqueuesBackup` (asserts the fake `RestoreTransactionRunner` received the parsed entity lists), `success_writesCacheSnapshotBeforeDestructive` (asserts the fake `RestoreSnapshotWriter` was called before the transaction runner — sequence verified via call-order assertions on a single shared `MutableList<String>`). |
| `ExportCsvUseCaseTest` | 2 | `headerLineUsesKmOrMilesPerFlag`, `rowCountMatchesEventCount`. Tests call the package-private `writeCsv(StringWriter, events, useKm)` helper directly — no `CsvFileSink` needed, no Android, no file I/O. The Android sink itself (`AndroidCsvFileSink`) is integration-covered when D wires the History export button. |

**Use case tests subtotal: 16**

### 10.4 New JVM total

47 new JVM tests across 11 new test classes. Combined with A+B's 14, the project will have **61 JVM tests** after this PR.

### 10.5 Instrumented tests

C adds **none**. The 25 instrumented tests from A+B (`WizardFlowTest` + 22 from B) still need an emulator to run.

---

## 11. Risks and notes

- **JVM-test boundaries for Android-touching code.** Three Android-specific concerns (Room's `@Database`, `Context.cacheDir` writes, `getExternalFilesDir` + `FileProvider`) are abstracted behind three small interfaces — `RestoreTransactionRunner`, `RestoreSnapshotWriter`, `CsvFileSink` — declared under `domain/backup/`. Production implementations (`RoomRestoreTransactionRunner`, `CacheDirRestoreSnapshotWriter`, `AndroidCsvFileSink`) live under `data/backup/` and are bound by `DomainModule`. JVM tests use trivial in-memory fakes (~10 lines each) — `FakeRestoreTransactionRunner` records the entity lists it was called with; `FakeRestoreSnapshotWriter` captures the JSON in a `String?`; `ExportCsvUseCaseTest` calls the package-private `writeCsv(StringWriter, ...)` helper directly without going through the sink at all. This keeps every C-side test in pure JVM at the cost of three small interfaces with one production implementation each.
- **`StatsCalculator` re-sorts inside `computeStats`**. The DAO already returns sorted-by-`eventDate` lists, but the calculator sorts defensively so callers passing arbitrary input still get correct results. Cheap O(n log n).
- **`MonthBucket` cost rule.** `monthlyBuckets` returns `totalCost = null` for any bucket if the *overall events list* is mixed-currency. Same rule as the dashboard. No per-bucket currency switching.
- **`SaveChargeEventUseCase` validation against own id.** When updating event #X, the previous-event lookup explicitly excludes #X by `id`. Test covers this path.
- **`RestoreBackupUseCase` snapshot ordering.** The cache snapshot is written *before* any destructive DB operation. If the file write throws (disk full, etc.), no data is lost. If the in-transaction clear-and-import throws after the snapshot lands, the transaction rolls back; the cache file is then "stale" but harmless.
- **`location?.takeIf { it.isNotBlank() }`** — `SaveChargeEventUseCase` writes the location to the event entity AND records usage only when non-blank. This means a typed-then-cleared location field results in the event having `location = null` and no `recordUsage` call. Matches the chip-row UX where leaving the field empty just means "no location."

---

## 12. Coverage check (spec → implementation files)

| Spec section | Implementation file(s) | Test file(s) |
|---|---|---|
| §3 Architecture (file map) | All `core/model/`, `domain/`, `data/backup/`, `di/DomainModule.kt` | — |
| §4 Repository interfaces | 6 files in `domain/repository/` | (verified by use case tests) |
| §5.1 UnitConverter | `domain/service/UnitConverter.kt` | `UnitConverterTest` |
| §5.2 CostParser | `domain/service/CostParser.kt` | `CostParserTest` |
| §5.3 DateRangeResolver | `domain/service/DateRangeResolver.kt` | `DateRangeResolverTest` |
| §5.4 StatsCalculator | `domain/service/StatsCalculator.kt`, `core/model/Stats.kt`, `core/model/MonthBucket.kt` | `StatsCalculatorTest`, `StatsCalculatorCostTest` |
| §5.5 BackupSerializer | `domain/service/BackupSerializer.kt`, `core/model/BackupData.kt` | `BackupSerializerTest` |
| §6 Domain models | `core/model/{DashboardPeriod,DateRange,DashboardUiState,SaveChargeEventInput}.kt` | (covered indirectly by use case tests) |
| §7 Backup interfaces + no-op | `domain/backup/{BackupScheduler,BackupRepository}.kt`, `data/backup/{NoOpBackupScheduler,NoOpBackupRepository}.kt` | (verified by use case tests via `FakeBackupScheduler`/`FakeBackupRepository`) |
| §8.1 ObserveDashboardStatsUseCase | `domain/usecase/ObserveDashboardStatsUseCase.kt` | `ObserveDashboardStatsUseCaseTest` |
| §8.2 SaveChargeEventUseCase | `domain/usecase/SaveChargeEventUseCase.kt` | `SaveChargeEventUseCaseTest` |
| §8.3 DeleteChargeEventUseCase | `domain/usecase/DeleteChargeEventUseCase.kt` | `DeleteChargeEventUseCaseTest` |
| §8.4 RestoreBackupUseCase | `domain/usecase/RestoreBackupUseCase.kt` | `RestoreBackupUseCaseTest` |
| §8.5 ExportCsvUseCase | `domain/usecase/ExportCsvUseCase.kt` | `ExportCsvUseCaseTest` |
| §9 Hilt wiring | `di/DomainModule.kt` | (compile-checked by `assembleDebug`) |
