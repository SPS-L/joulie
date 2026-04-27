# Domain Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Sub-project C from `docs/superpowers/specs/2026-04-26-domain-core-design.md` — the framework-free domain layer that D's UI, E's Drive backup, and F's polish all consume. Adds 5 pure services, 5 use cases, 7 narrow repository interfaces, 5 backup-related interfaces (2 backup + 3 Android-boundary), 5 corresponding implementations under `data/backup/`, plus shared test fakes.

**Architecture:** Domain owns the abstractions; data and Android-touching code provide the implementations. Use cases depend on narrow Reader/Writer/Queries interfaces (Interface Segregation Principle) so JVM tests can swap real Room/DataStore for in-memory fakes. The `BackupScheduler` interface owns the `driveEnabled` gate via its implementation contract — use cases call `enqueueBackup()` unconditionally; C ships a no-op scheduler that E later swaps for a WorkManager-backed implementation.

**Tech Stack:** Kotlin 1.9.21 · Hilt 2.50 (already wired by A, with KSP) · Jetpack Room 2.6.1 (entities and AppDatabase from B; this PR adds three `deleteAll()` DAO queries) · Gson 2.10.1 (already in app deps) for `BackupSerializer` · `kotlinx-coroutines-test` 1.7.3 for use case tests · JUnit 4 · NO new dependencies added by this PR.

**Spec source:** `docs/superpowers/specs/2026-04-26-domain-core-design.md` (commit `c3190ab` or later).

**Prerequisites:** Sub-projects A and B are merged on `main` at commit `e1488b8` or later.

---

## File map

### New files (production)

#### Domain models (`core/model/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/core/model/Stats.kt` | Computed aggregate statistics for one period: totals, all 3 efficiency metrics, cost rates, multi-currency flag. |
| `app/src/main/java/org/spsl/evtracker/core/model/MonthBucket.kt` | Per-calendar-month aggregate for charts (year, month, totalKwh, totalCost, currency). |
| `app/src/main/java/org/spsl/evtracker/core/model/BackupData.kt` | DTO matching `DESIGN.md §8` JSON schema; nested `CarDto`/`ChargeEventDto`/`CustomLocationDto`; `BackupVersionMismatch` exception class. |
| `app/src/main/java/org/spsl/evtracker/core/model/DateRange.kt` | `data class DateRange(startMillis: Long, endMillis: Long)`. |
| `app/src/main/java/org/spsl/evtracker/core/model/DashboardPeriod.kt` | Sealed class for the 5 period variants. |
| `app/src/main/java/org/spsl/evtracker/core/model/DashboardUiState.kt` | Output of `ObserveDashboardStatsUseCase`; nested `EmptyState` sealed; `ChargeTypeFilter` enum. |
| `app/src/main/java/org/spsl/evtracker/core/model/SaveChargeEventInput.kt` | Input + `CostInput` + `SaveChargeEventResult` sealed. |
| `app/src/main/java/org/spsl/evtracker/core/model/RestoreResult.kt` | Sealed result for `RestoreBackupUseCase`. |

#### Repository interfaces (`domain/repository/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/repository/CarReader.kt` | `observeAll`, `getById`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventQueries.kt` | `observeForCar`, `getInRange`, `getAllForCarSorted`, `getById`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventWriter.kt` | `insert`, `update`, `delete`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/LocationReader.kt` | `observeTop5`, `observeAll`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt` | `recordUsage`, `delete`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt` | `activeCarId`, `primaryMetric`, `distanceUnit`, `currency` Flows. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt` | `setActiveCarId`, `setDriveEnabled`. |

#### Backup interfaces (`domain/backup/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt` | `enqueueBackup()` — driveEnabled-gated by impl. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/BackupRepository.kt` | `backupCurrentData()`, `readRemoteBackup()`. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/RestoreTransactionRunner.kt` | Atomically deletes all rows then inserts the supplied entities. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/RestoreSnapshotWriter.kt` | Persists the JSON snapshot to a deterministic location. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/CsvFileSink.kt` | Allocates a CSV file, runs a write callback, returns a shareable Uri. |

#### Pure services (`domain/service/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/service/UnitConverter.kt` | km↔mi conversions; `object` (no DI). |
| `app/src/main/java/org/spsl/evtracker/domain/service/CostParser.kt` | Cost normalization; declares `enum class CostMode`. |
| `app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt` | Period → epoch-ms range. |
| `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt` | Delta-odo aggregates, multi-currency guard, monthly buckets. |
| `app/src/main/java/org/spsl/evtracker/domain/service/BackupSerializer.kt` | Gson-based BackupData ↔ JSON round-trip. |

#### Use cases (`domain/usecase/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCase.kt` | Combines activeCarId + observeForCar Flow + period/filter into `DashboardUiState`. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCase.kt` | Validate odo + parse cost + persist + record location + enqueue backup. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCase.kt` | Delete + enqueue backup. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCase.kt` | Fetch + parse + snapshot + transactional replace + setDriveEnabled. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCase.kt` | Resolve car + fetch events + write CSV via CsvFileSink. |

#### Backup implementations (`data/backup/`)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt` | No-op until E. |
| `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt` | No-op until E (returns null from `readRemoteBackup`). |
| `app/src/main/java/org/spsl/evtracker/data/backup/RoomRestoreTransactionRunner.kt` | Wraps `database.withTransaction { … }` for the clear-and-import flow. |
| `app/src/main/java/org/spsl/evtracker/data/backup/CacheDirRestoreSnapshotWriter.kt` | Writes JSON to `cacheDir/last_overwritten_backup.json`. |
| `app/src/main/java/org/spsl/evtracker/data/backup/AndroidCsvFileSink.kt` | Writes CSV to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` + returns FileProvider URI. |

#### Hilt module

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt` | 12 `@Binds`: 7 repo + 2 backup + 2 restore-infrastructure + 1 CSV sink. |

### Modified files (production)

| Path | What changes |
|---|---|
| `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt` | Append `: CarReader` to class declaration. |
| `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt` | Append `: ChargeEventQueries, ChargeEventWriter`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt` | Append `: LocationReader, LocationWriter`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` | Append `: SettingsReader, SettingsWriter`. Add a one-line `setDriveEnabled(enabled: Boolean)` method. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt` | Add `@Query("DELETE FROM cars") suspend fun deleteAll()`. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt` | Add `@Query("DELETE FROM charge_events") suspend fun deleteAll()`. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt` | Add `@Query("DELETE FROM custom_locations") abstract suspend fun deleteAll()`. |

### New files (tests)

| Path | Class | Cases |
|---|---|---|
| `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` | All 9 shared fakes in one file (cohesive; share entity types). | — |
| `app/src/test/java/org/spsl/evtracker/domain/service/UnitConverterTest.kt` | `UnitConverterTest` | 4 |
| `app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt` | `CostParserTest` | 7 |
| `app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverTest.kt` | `DateRangeResolverTest` | 5 |
| `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTest.kt` | `StatsCalculatorTest` | 6 |
| `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorCostTest.kt` | `StatsCalculatorCostTest` | 5 |
| `app/src/test/java/org/spsl/evtracker/domain/service/BackupSerializerTest.kt` | `BackupSerializerTest` | 4 |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCaseTest.kt` | `ObserveDashboardStatsUseCaseTest` | 5 |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt` | `SaveChargeEventUseCaseTest` | 5 |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCaseTest.kt` | `DeleteChargeEventUseCaseTest` | 1 |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt` | `RestoreBackupUseCaseTest` | 6 |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCaseTest.kt` | `ExportCsvUseCaseTest` | 2 |

**Total new JVM tests: 50.** After this PR, JVM count = 14 (A+B) + 50 = **64**.

---

## Notes for the worker

### Sandbox quirks (carryover from A and B)

- Gradle's default `~/.gradle` is on a read-only filesystem in the sandbox. ALWAYS use `GRADLE_USER_HOME=/tmp/gradle-home` and pass `dangerouslyDisableSandbox: true` to your Bash tool calls when running gradle. The directory is already populated from prior work.
- The Android SDK at `$ANDROID_HOME` is installed and working from A's setup.
- Per CLAUDE.md: never compound git commands with `&&`/`||`/`;`. Run `git add` and `git commit` as separate Bash calls.

### TDD discipline

For Tasks 5–16 (services + use cases), the order is **always**:
1. Write the failing test file.
2. Run the JVM test suite filtered to that test class. **Confirm compilation failure** (the production class doesn't exist yet, so the test references are unresolved). This is the failing-test step that proves the test exercises new code.
3. Write the production code.
4. Run the JVM test suite again. **Confirm pass.**
5. Commit (test + production in one commit).

The compile-failure step is meaningful, not a formality. **Don't skip it.**

### JVM-only tests

All Sub-project C tests are JVM unit tests (no `androidTest/`, no emulator needed). The use cases that touch Android primitives (`Context`, `AppDatabase`, `FileProvider`) do so through the 3 boundary interfaces (`RestoreTransactionRunner`, `RestoreSnapshotWriter`, `CsvFileSink`) declared in `domain/backup/`. Tests pass fakes; production code uses the Android implementations from `data/backup/`.

### Hilt: classes vs `object`

- Pure services use `@Inject constructor()` so Hilt can construct them. (`UnitConverter` is `object` because it has no state and Kotlin `object`s are singleton-by-language.)
- Use cases use `@Inject constructor(...)`.
- Repositories already exist and are `@Singleton @Inject`.
- `data/backup/` implementations are `@Singleton @Inject constructor(...)`.

### Don't add new dependencies

`androidx.room:room-ktx` (for `database.withTransaction`), Gson, `kotlinx-coroutines-test`, and `kotlinx-coroutines-android` are all already in the app's `dependencies` block from A and B. Verify by reading `app/build.gradle.kts` if uncertain — DO NOT add anything new.

---

## Task 1: Domain models (7 files; SaveChargeEventInput deferred to Task 6)

Create 7 model files under `core/model/` in one task. They're pure data classes with no behavior; nothing to TDD here.

`SaveChargeEventInput.kt` is the 8th model file but it depends on `domain.service.CostMode` which doesn't exist until Task 6. To keep main compilable after every task, we defer its creation to Task 6 — the same task that introduces `CostMode`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/Stats.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/MonthBucket.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/DateRange.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/DashboardPeriod.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/DashboardUiState.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/RestoreResult.kt`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/BackupData.kt`

- [ ] **Step 1: Create `Stats.kt`**

```kotlin
package org.spsl.evtracker.core.model

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
    val mixedCurrency: Boolean
)
```

- [ ] **Step 2: Create `MonthBucket.kt`**

```kotlin
package org.spsl.evtracker.core.model

data class MonthBucket(
    val year: Int,
    val month: Int,            // 1..12
    val totalKwh: Double,
    val totalCost: Double?,
    val currency: String?
)
```

- [ ] **Step 3: Create `DateRange.kt`**

```kotlin
package org.spsl.evtracker.core.model

data class DateRange(val startMillis: Long, val endMillis: Long)
```

- [ ] **Step 4: Create `DashboardPeriod.kt`**

```kotlin
package org.spsl.evtracker.core.model

sealed class DashboardPeriod {
    object SincePreviousCharge : DashboardPeriod()
    object Last7Days : DashboardPeriod()
    object Last30Days : DashboardPeriod()
    object Year : DashboardPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : DashboardPeriod()
}
```

- [ ] **Step 5: Create `DashboardUiState.kt`**

```kotlin
package org.spsl.evtracker.core.model

data class DashboardUiState(
    val emptyState: EmptyState? = null,
    val stats: Stats? = null,
    val showMultiCurrencyBanner: Boolean = false
)

sealed class EmptyState {
    object NoCar : EmptyState()
    object NoEvents : EmptyState()
}

enum class ChargeTypeFilter { ALL, AC, DC }
```

- [ ] **Step 6: Create `RestoreResult.kt`**

```kotlin
package org.spsl.evtracker.core.model

sealed class RestoreResult {
    object NoRemoteBackup : RestoreResult()
    data class VersionMismatch(val actualVersion: Int) : RestoreResult()
    data class Success(
        val carCount: Int,
        val eventCount: Int,
        val locationCount: Int
    ) : RestoreResult()
}
```

- [ ] **Step 7: Create `BackupData.kt`**

This is the largest model file. It includes the JSON DTOs for all three entity types and the `BackupVersionMismatch` exception.

```kotlin
package org.spsl.evtracker.core.model

import com.google.gson.annotations.SerializedName
import java.time.Instant
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

data class BackupData(
    @SerializedName("backup_version") val backupVersion: Int = CURRENT_VERSION,
    @SerializedName("exported_at")    val exportedAt: String,
    @SerializedName("cars")           val cars: List<CarDto>,
    @SerializedName("charge_events")  val chargeEvents: List<ChargeEventDto>,
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
            exportedAt = Instant.ofEpochMilli(now).toString(),
            cars = cars.map { CarDto.fromEntity(it) },
            chargeEvents = events.map { ChargeEventDto.fromEntity(it) },
            customLocations = locations.map { CustomLocationDto.fromEntity(it) }
        )
    }

    fun toEntities(): Triple<List<CarEntity>, List<ChargeEventEntity>, List<CustomLocationEntity>> =
        Triple(
            cars.map { it.toEntity() },
            chargeEvents.map { it.toEntity() },
            customLocations.map { it.toEntity() }
        )
}

data class CarDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("make") val make: String,
    @SerializedName("model") val model: String,
    @SerializedName("year") val year: Int?,
    @SerializedName("battery_kwh") val batteryKwh: Double?,
    @SerializedName("created_at") val createdAt: Long
) {
    fun toEntity() = CarEntity(id, name, make, model, year, batteryKwh, createdAt)

    companion object {
        fun fromEntity(e: CarEntity) =
            CarDto(e.id, e.name, e.make, e.model, e.year, e.batteryKwh, e.createdAt)
    }
}

data class ChargeEventDto(
    @SerializedName("id") val id: Int,
    @SerializedName("car_id") val carId: Int,
    @SerializedName("event_date") val eventDate: Long,
    @SerializedName("odometer_km") val odometerKm: Double,
    @SerializedName("kwh_added") val kwhAdded: Double,
    @SerializedName("charge_type") val chargeType: String,
    @SerializedName("cost_total") val costTotal: Double?,
    @SerializedName("cost_per_kwh") val costPerKwh: Double?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("note") val note: String,
    @SerializedName("created_at") val createdAt: Long
) {
    fun toEntity() = ChargeEventEntity(
        id, carId, eventDate, odometerKm, kwhAdded, chargeType,
        costTotal, costPerKwh, currency, location, note, createdAt
    )

    companion object {
        fun fromEntity(e: ChargeEventEntity) = ChargeEventDto(
            e.id, e.carId, e.eventDate, e.odometerKm, e.kwhAdded, e.chargeType,
            e.costTotal, e.costPerKwh, e.currency, e.location, e.note, e.createdAt
        )
    }
}

data class CustomLocationDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("label") val label: String,
    @SerializedName("use_count") val useCount: Int,
    @SerializedName("last_used") val lastUsed: Long
) {
    fun toEntity() = CustomLocationEntity(id, label, useCount, lastUsed)

    companion object {
        fun fromEntity(e: CustomLocationEntity) =
            CustomLocationDto(e.id, e.label, e.useCount, e.lastUsed)
    }
}

class BackupVersionMismatch(val actual: Int) :
    RuntimeException("Backup version $actual is incompatible with current version ${BackupData.CURRENT_VERSION}")
```

- [ ] **Step 8: Verify the build compiles**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL. The 7 model files are pure data classes with no broken cross-references; `SaveChargeEventInput.kt` (the 8th model) lands in Task 6 once `CostMode` exists.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/
```

```bash
git commit -m "feat(domain): add 7 core/model data classes (Stats, BackupData, DashboardUiState, etc.)"
```

---

## Task 2: Repository interfaces (`domain/repository/`)

Create all 7 interfaces. They're declarations only; the existing B classes already match the surfaces.

**Files:**
- Create 7 files under `app/src/main/java/org/spsl/evtracker/domain/repository/`.

- [ ] **Step 1: Create `CarReader.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CarEntity

interface CarReader {
    fun observeAll(): Flow<List<CarEntity>>
    suspend fun getById(id: Int): CarEntity?
}
```

- [ ] **Step 2: Create `ChargeEventQueries.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventQueries {
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>>
    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity>
    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity>
    suspend fun getById(id: Int): ChargeEventEntity?
}
```

- [ ] **Step 3: Create `ChargeEventWriter.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)
}
```

- [ ] **Step 4: Create `LocationReader.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationReader {
    fun observeTop5(): Flow<List<CustomLocationEntity>>
    fun observeAll(): Flow<List<CustomLocationEntity>>
}
```

- [ ] **Step 5: Create `LocationWriter.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationWriter {
    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis())
    suspend fun delete(location: CustomLocationEntity)
}
```

- [ ] **Step 6: Create `SettingsReader.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    val activeCarId: Flow<Int>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
}
```

- [ ] **Step 7: Create `SettingsWriter.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
}
```

- [ ] **Step 8: Verify build**

The interfaces are pure declarations; they reference only Kotlin types and B's entities.

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/repository/
```

```bash
git commit -m "feat(domain): add 7 repository interfaces (Reader/Writer/Queries splits)"
```

---

## Task 3: Backup interfaces (`domain/backup/`)

5 interface files.

**Files:**
- Create 5 files under `app/src/main/java/org/spsl/evtracker/domain/backup/`.

- [ ] **Step 1: Create `BackupScheduler.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Requests a backup of current local state.
 *
 * **Contract:** implementations own the `driveEnabled` gate. If Drive backup is disabled,
 * the implementation MUST no-op rather than schedule a Worker. Use cases (SaveChargeEvent,
 * DeleteChargeEvent, RestoreBackup) call [enqueueBackup] unconditionally after every
 * persisted state change — they do NOT read `driveEnabled` themselves.
 *
 * Bindings:
 * - C ships [org.spsl.evtracker.data.backup.NoOpBackupScheduler] which always no-ops.
 * - E swaps in a WorkManager-backed implementation that reads `driveEnabled` from
 *   SettingsRepository and either schedules a `OneTimeWorkRequest` or no-ops.
 */
interface BackupScheduler {
    fun enqueueBackup()
}
```

- [ ] **Step 2: Create `BackupRepository.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

interface BackupRepository {
    /** Serialize current local state and upload to Drive. */
    suspend fun backupCurrentData()

    /** Download evtracker_backup.json from the App Data folder. Returns null if no remote file. */
    suspend fun readRemoteBackup(): String?
}
```

- [ ] **Step 3: Create `RestoreTransactionRunner.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

/**
 * Atomically deletes all rows from cars, charge_events, custom_locations,
 * then inserts the supplied entities. Production: [org.spsl.evtracker.data.backup.RoomRestoreTransactionRunner]
 * wraps `database.withTransaction { … }`. Tests: in-memory fake.
 */
interface RestoreTransactionRunner {
    suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>
    )
}
```

- [ ] **Step 4: Create `RestoreSnapshotWriter.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Persists a JSON snapshot of pre-restore local state to a deterministic location.
 * Production: [org.spsl.evtracker.data.backup.CacheDirRestoreSnapshotWriter] writes to
 * cacheDir/last_overwritten_backup.json. Tests: capture the JSON in memory.
 */
interface RestoreSnapshotWriter {
    fun write(json: String)
}
```

- [ ] **Step 5: Create `CsvFileSink.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

import android.net.Uri
import java.io.Writer

/**
 * Allocates a CSV file for the given car/timestamp, invokes [body] to write its contents,
 * then returns a shareable URI for the file. Production: external Downloads + FileProvider.
 * Tests: ExportCsvUseCaseTest calls the writeCsv() helper directly without going through
 * the sink at all.
 */
interface CsvFileSink {
    suspend fun write(carName: String, body: (Writer) -> Unit): Uri
}
```

`Uri` is `android.net.Uri`. This makes `CsvFileSink` slightly Android-coupled — that's fine because the use case test bypasses the sink entirely (calls `writeCsv` on a `StringWriter`). The production sink is the only consumer and it lives in `data/backup/`.

- [ ] **Step 6: Verify build**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/backup/
```

```bash
git commit -m "feat(domain): add backup interfaces (Scheduler, Repository, RestoreTransactionRunner, RestoreSnapshotWriter, CsvFileSink)"
```

---

## Task 4: B-side touches — implement interfaces, add `setDriveEnabled`, add `deleteAll()`

This task makes B's existing classes implement the new interfaces and adds the small pieces C requires:
- `setDriveEnabled` on `SettingsRepository`
- `deleteAll()` on each of the 3 DAOs

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt`

- [ ] **Step 1: Append `: CarReader` to `CarRepository`**

In `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt`, change the class header from:

```kotlin
@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) {
```

to:

```kotlin
@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) : org.spsl.evtracker.domain.repository.CarReader {
```

(Or add `import org.spsl.evtracker.domain.repository.CarReader` and use the unqualified `CarReader` — either is fine.)

The existing `observeAll()` and `getById(id: Int)` methods already match the interface's signature; no method changes.

- [ ] **Step 2: Append `: ChargeEventQueries, ChargeEventWriter` to `ChargeEventRepository`**

In `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt`, change the class header from:

```kotlin
@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao
) {
```

to:

```kotlin
@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao
) : org.spsl.evtracker.domain.repository.ChargeEventQueries,
    org.spsl.evtracker.domain.repository.ChargeEventWriter {
```

All 7 existing methods already match.

- [ ] **Step 3: Append `: LocationReader, LocationWriter` to `LocationRepository`**

In `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt`, change the class header to:

```kotlin
@Singleton
class LocationRepository @Inject constructor(
    private val customLocationDao: CustomLocationDao
) : org.spsl.evtracker.domain.repository.LocationReader,
    org.spsl.evtracker.domain.repository.LocationWriter {
```

The existing `observeTop5`, `observeAll`, `recordUsage(label, now)`, and `delete(location)` methods all match.

- [ ] **Step 4: Append `: SettingsReader, SettingsWriter` to `SettingsRepository` AND add `setDriveEnabled`**

In `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`, change the class header to:

```kotlin
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : org.spsl.evtracker.domain.repository.SettingsReader,
    org.spsl.evtracker.domain.repository.SettingsWriter {
```

Then add a new method anywhere inside the class (a sensible spot is right after `setActiveCarId`):

```kotlin
override suspend fun setDriveEnabled(enabled: Boolean) {
    dataStore.edit { it[PreferenceKeys.DRIVE_ENABLED] = enabled }
}
```

Existing `setActiveCarId` needs to become `override suspend fun setActiveCarId(...)` — add the `override` modifier. Same for the existing `activeCarId`/`primaryMetric`/`distanceUnit`/`currency` Flow properties: add `override` modifier in front of `val`. Mark `theme` and `setupComplete` accessors as NOT override (they're not in `SettingsReader`).

The Kotlin compiler will tell you if you missed any — `override` is mandatory when implementing an interface member.

- [ ] **Step 5: Add `deleteAll()` to `CarDao`**

Inside `app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt`'s interface body, append:

```kotlin
@Query("DELETE FROM cars")
suspend fun deleteAll()
```

- [ ] **Step 6: Add `deleteAll()` to `ChargeEventDao`**

Inside `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt`'s interface body, append:

```kotlin
@Query("DELETE FROM charge_events")
suspend fun deleteAll()
```

- [ ] **Step 7: Add `deleteAll()` to `CustomLocationDao`**

Inside `app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt`'s `abstract class` body, append:

```kotlin
@Query("DELETE FROM custom_locations")
abstract suspend fun deleteAll()
```

The `abstract` modifier is required because `CustomLocationDao` is an abstract class (not an interface) hosting the `@Transaction recordUsage` body.

- [ ] **Step 8: Verify the build still compiles**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL. `setDriveEnabled` and the `deleteAll` methods compile against existing infrastructure; the new `: SomeReader, SomeWriter` clauses match the existing method signatures exactly so no method bodies need editing.

- [ ] **Step 9: Run JVM tests — they should still pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: same 14 tests pass as before B (4 SettingsRepositoryTest + 9 WizardViewModelTest, plus the 1 added in B Task 13 — actually all already in `main`'s test suite; total 14). The newly-added `override` keywords on existing methods don't break anything because the method bodies are unchanged.

If a test fails because `override` was added to a method whose previous behavior changed — DON'T change behavior. Roll back any over-reach.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/ app/src/main/java/org/spsl/evtracker/data/local/dao/
```

```bash
git commit -m "feat(data): implement domain repo interfaces; add setDriveEnabled + deleteAll DAO methods"
```

---

## Task 5: `UnitConverter` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/UnitConverterTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/UnitConverter.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

    @Test
    fun kmToMiles_positive() {
        assertEquals(62.137, UnitConverter.kmToMiles(100.0), 0.001)
    }

    @Test
    fun milesToKm_positive() {
        assertEquals(100.0, UnitConverter.milesToKm(62.1371), 0.001)
    }

    @Test
    fun kmToMiles_zero() {
        assertEquals(0.0, UnitConverter.kmToMiles(0.0), 0.0)
    }

    @Test
    fun efficiency_kmPerKwh_to_miPerKwh() {
        assertEquals(3.107, UnitConverter.kmPerKwhToMiPerKwh(5.0), 0.001)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.UnitConverterTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `UnitConverter` is unresolved.

- [ ] **Step 3: Implement `UnitConverter`**

```kotlin
package org.spsl.evtracker.domain.service

object UnitConverter {
    private const val KM_PER_MI = 1.609344

    fun kmToMiles(km: Double): Double = km / KM_PER_MI
    fun milesToKm(mi: Double): Double = mi * KM_PER_MI
    fun kmPerKwhToMiPerKwh(kmPerKwh: Double): Double = kmToMiles(kmPerKwh)
}
```

- [ ] **Step 4: Run tests — 4 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.UnitConverterTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL with all 4 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/UnitConverter.kt app/src/test/java/org/spsl/evtracker/domain/service/UnitConverterTest.kt
```

```bash
git commit -m "feat(domain): add UnitConverter (km↔mi)"
```

---

## Task 6: `CostParser` + `CostMode` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/CostParser.kt`

This task creates `CostMode`, then creates `SaveChargeEventInput.kt` (deferred from Task 1 because it depends on `CostMode`).

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostParserTest {

    private val parser = CostParser()

    @Test
    fun costZero_returnsNull() {
        val (total, perKwh) = parser.parse(0.0, 10.0, CostMode.TOTAL)
        assertNull(total); assertNull(perKwh)
    }

    @Test
    fun costBlank_returnsNull() {
        val (total, perKwh) = parser.parse(null, 10.0, CostMode.TOTAL)
        assertNull(total); assertNull(perKwh)
    }

    @Test
    fun costNegative_returnsNull() {
        val (total, perKwh) = parser.parse(-5.0, 10.0, CostMode.TOTAL)
        assertNull(total); assertNull(perKwh)
    }

    @Test
    fun costTotal_derivesPerKwh() {
        val (total, perKwh) = parser.parse(3.0, 10.0, CostMode.TOTAL)
        assertEquals(3.0, total!!, 0.0001)
        assertEquals(0.30, perKwh!!, 0.0001)
    }

    @Test
    fun costPerKwh_derivesTotal() {
        val (total, perKwh) = parser.parse(0.30, 10.0, CostMode.PER_KWH)
        assertEquals(3.0, total!!, 0.0001)
        assertEquals(0.30, perKwh!!, 0.0001)
    }

    @Test
    fun kwhZero_returnsNull() {
        val (total, perKwh) = parser.parse(5.0, 0.0, CostMode.TOTAL)
        assertNull(total); assertNull(perKwh)
    }

    @Test
    fun bothEntered_totalWins() {
        // Caller is expected to pass the TOTAL value when both fields are populated.
        // The parser only handles one mode at a time; this test documents the
        // call-site contract by verifying that calling with TOTAL=4.0 / kwh=10
        // yields perKwh=0.40 (NOT the user-typed 0.20).
        val (total, perKwh) = parser.parse(4.0, 10.0, CostMode.TOTAL)
        assertEquals(4.0, total!!, 0.0001)
        assertEquals(0.40, perKwh!!, 0.0001)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.CostParserTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `CostParser` and `CostMode` unresolved.

- [ ] **Step 3: Implement `CostParser` (with `CostMode` in the same file)**

```kotlin
package org.spsl.evtracker.domain.service

import javax.inject.Inject

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

- [ ] **Step 4: Run tests — 7 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.CostParserTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL with all 7 cases passing.

- [ ] **Step 5: Now create `SaveChargeEventInput.kt` (deferred from Task 1; CostMode now exists)**

Create `app/src/main/java/org/spsl/evtracker/core/model/SaveChargeEventInput.kt`:

```kotlin
package org.spsl.evtracker.core.model

import org.spsl.evtracker.domain.service.CostMode

data class SaveChargeEventInput(
    val eventId: Int? = null,
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: String,
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

The `CostMode` import resolves cleanly because Step 3 just declared it. Don't inline a copy of the enum here — keep it co-located with `CostParser` per the spec.

- [ ] **Step 6: Verify the full project compiles end-to-end**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/CostParser.kt app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt app/src/main/java/org/spsl/evtracker/core/model/SaveChargeEventInput.kt
```

```bash
git commit -m "feat(domain): add CostParser + CostMode + SaveChargeEventInput"
```

---

## Task 7: `DateRangeResolver` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.service

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.DashboardPeriod

class DateRangeResolverTest {

    private val resolver = DateRangeResolver()
    private val MS_PER_DAY = 24L * 60 * 60 * 1000

    // A fixed "now" so the tests are deterministic: 2026-04-26T12:00:00 in the JVM default TZ.
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 26, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun last7Days_returnsNowMinus7DaysToNow() {
        val r = resolver.resolve(DashboardPeriod.Last7Days, now)
        assertEquals(now - 7 * MS_PER_DAY, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun last30Days_returnsNowMinus30DaysToNow() {
        val r = resolver.resolve(DashboardPeriod.Last30Days, now)
        assertEquals(now - 30 * MS_PER_DAY, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun year_isJanuary1OfCurrentYearAtMidnight() {
        val r = resolver.resolve(DashboardPeriod.Year, now)
        val expectedStart = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expectedStart, r.startMillis)
        assertEquals(now, r.endMillis)
    }

    @Test
    fun custom_returnsItsInputs() {
        val r = resolver.resolve(DashboardPeriod.Custom(fromMillis = 1000L, toMillis = 2000L), now)
        assertEquals(1000L, r.startMillis)
        assertEquals(2000L, r.endMillis)
    }

    @Test
    fun sincePreviousCharge_isFromZeroToNow() {
        val r = resolver.resolve(DashboardPeriod.SincePreviousCharge, now)
        assertEquals(0L, r.startMillis)
        assertEquals(now, r.endMillis)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.DateRangeResolverTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `DateRangeResolver` unresolved.

- [ ] **Step 3: Implement `DateRangeResolver`**

```kotlin
package org.spsl.evtracker.domain.service

import java.util.Calendar
import javax.inject.Inject
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DateRange

class DateRangeResolver @Inject constructor() {

    fun resolve(period: DashboardPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange =
        when (period) {
            DashboardPeriod.SincePreviousCharge -> DateRange(0L, nowMillis)
            DashboardPeriod.Last7Days  -> DateRange(nowMillis - 7  * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Last30Days -> DateRange(nowMillis - 30 * MILLIS_PER_DAY, nowMillis)
            DashboardPeriod.Year       -> DateRange(startOfYear(nowMillis), nowMillis)
            is DashboardPeriod.Custom  -> DateRange(period.fromMillis, period.toMillis)
        }

    private fun startOfYear(nowMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
```

- [ ] **Step 4: Run tests — 5 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.DateRangeResolverTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL with all 5 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt app/src/test/java/org/spsl/evtracker/domain/service/DateRangeResolverTest.kt
```

```bash
git commit -m "feat(domain): add DateRangeResolver"
```

---

## Task 8: `StatsCalculator` — TDD (largest service)

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTest.kt`
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorCostTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`

The two test classes split test scope by concern: `StatsCalculatorTest` covers the basic delta-odo + monthly-bucket math; `StatsCalculatorCostTest` covers cost stats and the multi-currency guard.

- [ ] **Step 1: Write `StatsCalculatorTest.kt`**

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorTest {

    private val calc = StatsCalculator()

    private fun event(
        id: Int = 0,
        carId: Int = 1,
        eventDate: Long = 0L,
        odometerKm: Double = 0.0,
        kwhAdded: Double = 10.0
    ) = ChargeEventEntity(
        id = id, carId = carId, eventDate = eventDate,
        odometerKm = odometerKm, kwhAdded = kwhAdded
    )

    @Test
    fun emptyEvents_returnsZeroStats() {
        val s = calc.computeStats(emptyList(), "label")
        assertEquals(0.0, s.totalKwh, 0.0)
        assertEquals(0, s.chargeCount)
        assertNull(s.avgKmPerKwh); assertNull(s.avgKwhPer100Km); assertNull(s.avgMiPerKwh)
        assertNull(s.costPerKm); assertNull(s.costPer100Km)
        assertEquals(false, s.mixedCurrency)
    }

    @Test
    fun singleEvent_totalsButNoEfficiency() {
        val s = calc.computeStats(listOf(event(kwhAdded = 42.0)), "label")
        assertEquals(42.0, s.totalKwh, 0.0)
        assertEquals(1, s.chargeCount)
        assertNull(s.avgKmPerKwh)
        assertNull(s.costPerKm)
    }

    @Test
    fun twoEvents_correctEfficiency() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 0.0,   kwhAdded = 0.0),    // first event has no delta
                event(eventDate = 2, odometerKm = 100.0, kwhAdded = 20.0)
            ),
            "label"
        )
        assertEquals(2, s.chargeCount)
        assertEquals(20.0, s.totalKwh, 0.0)         // first event has 0 kWh; only second contributes
        assertEquals(100.0, s.totalDistanceKm, 0.0)
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)
        assertEquals(20.0, s.avgKwhPer100Km!!, 0.0001)
        assertEquals(3.107, s.avgMiPerKwh!!, 0.001)
    }

    @Test
    fun multipleEvents_sumCorrect() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 0.0,   kwhAdded = 0.0),
                event(eventDate = 2, odometerKm = 50.0,  kwhAdded = 10.0),   // dist=50, kwh=10
                event(eventDate = 3, odometerKm = 150.0, kwhAdded = 20.0)    // dist=100, kwh=20
            ),
            "label"
        )
        assertEquals(3, s.chargeCount)
        assertEquals(150.0, s.totalDistanceKm, 0.0)
        assertEquals(30.0, s.totalKwh, 0.0)         // 0 + 10 + 20
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)  // 150 / 30
    }

    @Test
    fun negativeOdometerDelta_skipped() {
        val s = calc.computeStats(
            listOf(
                event(eventDate = 1, odometerKm = 100.0, kwhAdded = 0.0),
                event(eventDate = 2, odometerKm = 50.0,  kwhAdded = 10.0),    // negative delta — skipped
                event(eventDate = 3, odometerKm = 150.0, kwhAdded = 20.0)     // dist=100 from #2 (50), kwh=20
            ),
            "label"
        )
        // Only the (50→150) pair contributes 100km/20kWh; the regression 100→50 is skipped.
        assertEquals(100.0, s.totalDistanceKm, 0.0)
        assertEquals(5.0, s.avgKmPerKwh!!, 0.0001)
    }

    @Test
    fun monthlyAggregation_correctBuckets() {
        // 4 events spanning 3 calendar months. Use known timestamps.
        val jan15 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.JANUARY, 15, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb1  = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.FEBRUARY, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val feb20 = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.FEBRUARY, 20, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val mar5  = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.MARCH, 5, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val buckets = calc.computeMonthlyBuckets(listOf(
            event(eventDate = jan15, kwhAdded = 10.0),
            event(eventDate = feb1,  kwhAdded = 5.0),
            event(eventDate = feb20, kwhAdded = 7.0),
            event(eventDate = mar5,  kwhAdded = 12.0)
        ))

        assertEquals(3, buckets.size)
        assertEquals(2026, buckets[0].year);  assertEquals(1, buckets[0].month);  assertEquals(10.0, buckets[0].totalKwh, 0.0)
        assertEquals(2026, buckets[1].year);  assertEquals(2, buckets[1].month);  assertEquals(12.0, buckets[1].totalKwh, 0.0)
        assertEquals(2026, buckets[2].year);  assertEquals(3, buckets[2].month);  assertEquals(12.0, buckets[2].totalKwh, 0.0)
    }
}
```

- [ ] **Step 2: Write `StatsCalculatorCostTest.kt`**

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorCostTest {

    private val calc = StatsCalculator()

    private fun event(
        eventDate: Long,
        odometerKm: Double,
        kwhAdded: Double,
        costTotal: Double? = null,
        currency: String? = null
    ) = ChargeEventEntity(
        carId = 1, eventDate = eventDate, odometerKm = odometerKm, kwhAdded = kwhAdded,
        costTotal = costTotal, currency = currency
    )

    @Test
    fun allCostNull_costStatsNull() {
        val s = calc.computeStats(listOf(
            event(1, 0.0,   0.0),
            event(2, 50.0,  10.0),
            event(3, 100.0, 10.0)
        ), "label")
        assertNull(s.costPerKm); assertNull(s.costPer100Km)
        assertEquals(false, s.mixedCurrency)
    }

    @Test
    fun mixedCost_sumNonNullOnly() {
        val s = calc.computeStats(listOf(
            event(1, 0.0,   0.0),                                       // first
            event(2, 50.0,  10.0, costTotal = 5.0,  currency = "EUR"),  // dist=50, cost=5
            event(3, 100.0, 10.0),                                       // dist=50, cost=null
            event(4, 200.0, 10.0, costTotal = 10.0, currency = "EUR"),  // dist=100, cost=10
            event(5, 250.0, 10.0)                                        // dist=50,  cost=null
        ), "label")
        // Total cost across costed events: 15.0
        // Total distance across ALL pairs with positive delta: 250
        assertEquals(15.0 / 250.0, s.costPerKm!!, 0.0001)
    }

    @Test
    fun singleCostEvent_correct() {
        val s = calc.computeStats(listOf(
            event(1, 0.0,  0.0),
            event(2, 50.0, 10.0, costTotal = 5.0, currency = "EUR")     // dist=50, cost=5
        ), "label")
        assertEquals(5.0 / 50.0, s.costPerKm!!, 0.0001)         // 0.10
        assertEquals(s.costPerKm!! * 100.0, s.costPer100Km!!, 0.0001)
    }

    @Test
    fun multipleCurrencies_costStatsNull() {
        val s = calc.computeStats(listOf(
            event(1, 0.0,    0.0),
            event(2, 50.0,   10.0, costTotal = 5.0, currency = "EUR"),
            event(3, 100.0,  10.0, costTotal = 6.0, currency = "EUR"),
            event(4, 150.0,  10.0, costTotal = 7.0, currency = "USD"),
            event(5, 200.0,  10.0, costTotal = 8.0, currency = "USD")
        ), "label")
        assertNull(s.costPerKm); assertNull(s.costPer100Km)
        assertTrue(s.mixedCurrency)
    }

    @Test
    fun singleCurrencyAcrossPeriod_costStatsComputed() {
        val s = calc.computeStats(listOf(
            event(1, 0.0,    0.0),
            event(2, 50.0,   10.0, costTotal = 5.0,  currency = "EUR"),
            event(3, 100.0,  10.0, costTotal = 6.0,  currency = "EUR"),
            event(4, 200.0,  10.0, costTotal = 12.0, currency = "EUR")
        ), "label")
        // Total cost = 23.0, total dist = 200
        assertEquals(23.0 / 200.0, s.costPerKm!!, 0.0001)
        assertEquals(false, s.mixedCurrency)
    }
}
```

- [ ] **Step 3: Verify both tests fail to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculator*" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `StatsCalculator` unresolved.

- [ ] **Step 4: Implement `StatsCalculator`**

```kotlin
package org.spsl.evtracker.domain.service

import java.util.Calendar
import javax.inject.Inject
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.core.model.Stats
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculator @Inject constructor() {

    fun computeStats(events: List<ChargeEventEntity>, label: String): Stats {
        val totalKwhAll = events.sumOf { it.kwhAdded }
        val chargeCount = events.size

        if (events.size < 2) {
            return Stats(
                label = label,
                totalKwh = totalKwhAll,
                totalDistanceKm = 0.0,
                avgKmPerKwh = null,
                avgKwhPer100Km = null,
                avgMiPerKwh = null,
                chargeCount = chargeCount,
                costPerKm = null,
                costPer100Km = null,
                mixedCurrency = false
            )
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

        return Stats(
            label = label,
            totalKwh = totalKwhAll,
            totalDistanceKm = totalDist,
            avgKmPerKwh = avgKmPerKwh,
            avgKwhPer100Km = avgKwhPer100Km,
            avgMiPerKwh = avgMiPerKwh,
            chargeCount = chargeCount,
            costPerKm = costPerKm,
            costPer100Km = costPer100Km,
            mixedCurrency = mixedCurrency
        )
    }

    fun computeMonthlyBuckets(events: List<ChargeEventEntity>): List<MonthBucket> {
        val costedCurrencies = events.mapNotNull { e -> e.costTotal?.let { e.currency } }.distinct()
        val singleCurrency = costedCurrencies.singleOrNull()
        val groups = events.groupBy { ev ->
            val cal = Calendar.getInstance().apply { timeInMillis = ev.eventDate }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
        return groups.map { (yearMonth, bucketEvents) ->
            val (year, month) = yearMonth
            val totalKwh = bucketEvents.sumOf { it.kwhAdded }
            val totalCost = if (singleCurrency != null) {
                val sum = bucketEvents.mapNotNull { it.costTotal }.sum()
                if (sum > 0) sum else null
            } else null
            MonthBucket(
                year = year,
                month = month,
                totalKwh = totalKwh,
                totalCost = totalCost,
                currency = if (totalCost != null) singleCurrency else null
            )
        }.sortedWith(compareBy({ it.year }, { it.month }))
    }
}
```

- [ ] **Step 5: Run tests — both classes pass (6 + 5 = 11 cases)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.StatsCalculator*" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 11 cases passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculator*.kt
```

```bash
git commit -m "feat(domain): add StatsCalculator with delta-odo aggregates and monthly buckets"
```

---

## Task 9: `BackupSerializer` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/service/BackupSerializerTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/BackupSerializer.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

class BackupSerializerTest {

    private val serializer = BackupSerializer()

    @Test
    fun roundTrip_preservesAllFields() {
        val original = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1, name = "Tesla", make = "T", model = "M3", year = 2024, batteryKwh = 60.0, createdAt = 1000L)),
            events = listOf(ChargeEventEntity(
                id = 17, carId = 1, eventDate = 2000L, odometerKm = 12345.0, kwhAdded = 22.4,
                chargeType = "AC", costTotal = 5.5, costPerKwh = 0.245, currency = "EUR",
                location = "Home", note = "first", createdAt = 3000L
            )),
            locations = listOf(CustomLocationEntity(id = 5, label = "Supercharger A6", useCount = 4, lastUsed = 4000L)),
            now = 5000L
        )

        val json = serializer.toJson(original)
        val parsed = serializer.fromJson(json)

        assertEquals(original.backupVersion, parsed.backupVersion)
        assertEquals(original.exportedAt, parsed.exportedAt)
        assertEquals(original.cars, parsed.cars)
        assertEquals(original.chargeEvents, parsed.chargeEvents)
        assertEquals(original.customLocations, parsed.customLocations)
    }

    @Test
    fun fromJson_throwsOnVersionMismatch() {
        val v2Json = """{"backup_version":2,"exported_at":"x","cars":[],"charge_events":[],"custom_locations":[]}"""
        val ex = assertThrows(BackupVersionMismatch::class.java) { serializer.fromJson(v2Json) }
        assertEquals(2, ex.actual)
    }

    @Test
    fun toJson_isHtmlEscapeFree() {
        val data = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1, name = "<&>",   createdAt = 1L)),
            events = emptyList(),
            locations = emptyList(),
            now = 0L
        )
        val json = serializer.toJson(data)
        assertFalse("HTML-escaped < should be absent", json.contains("\\u003c"))
        assertFalse("HTML-escaped > should be absent", json.contains("\\u003e"))
        assertFalse("HTML-escaped & should be absent", json.contains("\\u0026"))
    }

    @Test
    fun fromEntities_setsExportedAtToIso8601Utc() {
        val data = BackupData.fromEntities(
            cars = emptyList(), events = emptyList(), locations = emptyList(),
            now = 1714044000000L                 // 2024-04-25T10:00:00Z
        )
        assertEquals("2024-04-25T10:00:00Z", data.exportedAt)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.BackupSerializerTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `BackupSerializer` unresolved.

- [ ] **Step 3: Implement `BackupSerializer`**

```kotlin
package org.spsl.evtracker.domain.service

import com.google.gson.GsonBuilder
import javax.inject.Inject
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch

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

- [ ] **Step 4: Run tests — 4 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.service.BackupSerializerTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 4 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/service/BackupSerializer.kt app/src/test/java/org/spsl/evtracker/domain/service/BackupSerializerTest.kt
```

```bash
git commit -m "feat(domain): add BackupSerializer (Gson, version-validated)"
```

---

## Task 10: Backup-side implementations (`data/backup/`)

5 small classes — 2 no-ops + 3 Android implementations.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/RoomRestoreTransactionRunner.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/CacheDirRestoreSnapshotWriter.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/AndroidCsvFileSink.kt`

- [ ] **Step 1: Create `NoOpBackupScheduler`**

```kotlin
package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupScheduler

@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override fun enqueueBackup() {
        // No-op until E lands. See BackupScheduler KDoc for the contract.
    }
}
```

- [ ] **Step 2: Create `NoOpBackupRepository`**

```kotlin
package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupRepository

@Singleton
class NoOpBackupRepository @Inject constructor() : BackupRepository {
    override suspend fun backupCurrentData() {
        // No-op until E lands.
    }
    override suspend fun readRemoteBackup(): String? = null
}
```

- [ ] **Step 3: Create `RoomRestoreTransactionRunner`**

```kotlin
package org.spsl.evtracker.data.backup

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner

@Singleton
class RoomRestoreTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : RestoreTransactionRunner {
    override suspend fun replaceAll(
        cars: List<CarEntity>,
        events: List<ChargeEventEntity>,
        locations: List<CustomLocationEntity>
    ) {
        database.withTransaction {
            // Order: events first → locations → cars; then re-insert in the safe order.
            // (FK ON DELETE CASCADE on charge_events would also remove events when cars
            // are deleted, but explicit ordering is deterministic and doesn't depend on
            // cascade timing within a single transaction.)
            database.chargeEventDao().deleteAll()
            database.customLocationDao().deleteAll()
            database.carDao().deleteAll()

            cars.forEach { database.carDao().insert(it) }
            events.forEach { database.chargeEventDao().insert(it) }
            locations.forEach { database.customLocationDao().insertIfMissing(it) }
        }
    }
}
```

- [ ] **Step 4: Create `CacheDirRestoreSnapshotWriter`**

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter

@Singleton
class CacheDirRestoreSnapshotWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : RestoreSnapshotWriter {
    override fun write(json: String) {
        File(context.cacheDir, "last_overwritten_backup.json").writeText(json)
    }
}
```

- [ ] **Step 5: Create `AndroidCsvFileSink`**

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.CsvFileSink

@Singleton
class AndroidCsvFileSink @Inject constructor(
    @ApplicationContext private val context: Context
) : CsvFileSink {
    override suspend fun write(carName: String, body: (Writer) -> Unit): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = carName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "evtracker_${safeName}_$timestamp.csv")
        file.bufferedWriter().use(body)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
```

- [ ] **Step 6: Verify the build**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/backup/
```

```bash
git commit -m "feat(data): add NoOp + Android implementations for backup interfaces"
```

---

## Task 11: Shared test fakes

All 9 fakes in one file under `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. Cohesive — fakes for paired Reader/Writer share the underlying state.

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Create the file with all 9 fakes**

```kotlin
package org.spsl.evtracker.testing

import android.net.Uri
import java.io.Writer
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
    currencyInit: String = "EUR"
) : SettingsReader {
    private val activeCar = MutableStateFlow(activeCarIdInit)
    private val metric = MutableStateFlow(primaryMetricInit)
    private val unit = MutableStateFlow(distanceUnitInit)
    private val curr = MutableStateFlow(currencyInit)
    override val activeCarId: Flow<Int> = activeCar
    override val primaryMetric: Flow<String> = metric
    override val distanceUnit: Flow<String> = unit
    override val currency: Flow<String> = curr
    fun setActiveCarId(id: Int) { activeCar.value = id }
}

class FakeSettingsWriter : SettingsWriter {
    var activeCarId: Int = -1
        private set
    var driveEnabled: Boolean = false
        private set
    override suspend fun setActiveCarId(id: Int) { activeCarId = id }
    override suspend fun setDriveEnabled(enabled: Boolean) { driveEnabled = enabled }
}

class FakeBackupScheduler : BackupScheduler {
    var enqueueCount: Int = 0
        private set
    override fun enqueueBackup() { enqueueCount++ }
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
```

- [ ] **Step 2: Verify the file compiles**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:compileDebugUnitTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
```

```bash
git commit -m "test(domain): add shared in-memory fakes for use case tests"
```

---

## Task 12: `ObserveDashboardStatsUseCase` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCaseTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeSettingsReader

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardStatsUseCaseTest {

    private val MS_PER_DAY = 24L * 60 * 60 * 1000

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        activeCarId: Int = -1
    ): Triple<ObserveDashboardStatsUseCase, FakeChargeEventQueries, FakeSettingsReader> {
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries()
        queries.seed(events)
        val settings = FakeSettingsReader(activeCarIdInit = activeCarId)
        val useCase = ObserveDashboardStatsUseCase(
            carReader = carReader,
            chargeEventQueries = queries,
            settingsReader = settings,
            statsCalculator = StatsCalculator(),
            dateRangeResolver = DateRangeResolver()
        )
        return Triple(useCase, queries, settings)
    }

    @Test
    fun noCars_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(cars = emptyList(), activeCarId = -1)
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun activeCarMinusOne_emitsNoCarEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            activeCarId = -1
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoCar, state.emptyState)
    }

    @Test
    fun noEventsForActiveCar_emitsNoEventsEmptyState() = runTest {
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = emptyList(),
            activeCarId = 1
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertEquals(EmptyState.NoEvents, state.emptyState)
    }

    @Test
    fun eventsPresent_emitsStatsAndMultiCurrencyFlag() = runTest {
        val now = System.currentTimeMillis()
        val (useCase, _, _) = build(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = listOf(
                ChargeEventEntity(carId = 1, eventDate = now - 5 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 0.0, costTotal = 5.0, currency = "EUR"),
                ChargeEventEntity(carId = 1, eventDate = now - 3 * MS_PER_DAY, odometerKm = 100.0, kwhAdded = 20.0, costTotal = 6.0, currency = "USD"),
                ChargeEventEntity(carId = 1, eventDate = now - 1 * MS_PER_DAY, odometerKm = 200.0, kwhAdded = 20.0, costTotal = 7.0, currency = "EUR")
            ),
            activeCarId = 1
        )
        val state = useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).first()
        assertTrue(state.stats != null)
        assertTrue(state.showMultiCurrencyBanner)
    }

    @Test
    fun reEmitsWhenEventInsertedForActiveCar() = runTest(UnconfinedTestDispatcher()) {
        val carReader = FakeCarReader(listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        val queries = FakeChargeEventQueries()
        val writer = FakeChargeEventWriter(queries.shareStore())
        val settings = FakeSettingsReader(activeCarIdInit = 1)
        val useCase = ObserveDashboardStatsUseCase(carReader, queries, settings, StatsCalculator(), DateRangeResolver())

        val emissions = mutableListOf<org.spsl.evtracker.core.model.DashboardUiState>()
        val job = launch {
            useCase.observe(DashboardPeriod.Last30Days, ChargeTypeFilter.ALL).collect { emissions += it }
        }
        advanceUntilIdle()
        // Initial emission: NoEvents.
        assertEquals(EmptyState.NoEvents, emissions.last().emptyState)

        // Insert an event for the active car within the period window.
        val now = System.currentTimeMillis()
        writer.insert(ChargeEventEntity(carId = 1, eventDate = now - 1 * MS_PER_DAY, odometerKm = 0.0, kwhAdded = 10.0))
        advanceUntilIdle()
        writer.insert(ChargeEventEntity(carId = 1, eventDate = now,                  odometerKm = 100.0, kwhAdded = 20.0))
        advanceUntilIdle()
        // Latest emission should now have stats (two events ⇒ delta-odo computable).
        assertTrue("expected stats after insert; emissions=$emissions", emissions.last().stats != null)
        job.cancel()
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `ObserveDashboardStatsUseCase` unresolved.

- [ ] **Step 3: Implement `ObserveDashboardStatsUseCase`**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DashboardUiState
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.service.DateRangeResolver
import org.spsl.evtracker.domain.service.StatsCalculator

class ObserveDashboardStatsUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val settingsReader: SettingsReader,
    private val statsCalculator: StatsCalculator,
    private val dateRangeResolver: DateRangeResolver
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(period: DashboardPeriod, filter: ChargeTypeFilter): Flow<DashboardUiState> {
        return combine(settingsReader.activeCarId, carReader.observeAll()) { activeCarId, cars ->
            Pair(activeCarId, cars)
        }.flatMapLatest { (activeCarId, cars) ->
            when {
                cars.isEmpty() || activeCarId == -1 ->
                    flowOf(DashboardUiState(emptyState = EmptyState.NoCar))
                else ->
                    chargeEventQueries.observeForCar(activeCarId).map { events ->
                        buildUiState(events, period, filter)
                    }
            }
        }
    }

    private fun buildUiState(
        allEventsForCar: List<ChargeEventEntity>,
        period: DashboardPeriod,
        filter: ChargeTypeFilter
    ): DashboardUiState {
        val periodEvents = when (period) {
            DashboardPeriod.SincePreviousCharge -> allEventsForCar.takeLast(2)
            else -> {
                val range = dateRangeResolver.resolve(period)
                allEventsForCar.filter { it.eventDate in range.startMillis..range.endMillis }
            }
        }
        val filtered = when (filter) {
            ChargeTypeFilter.ALL -> periodEvents
            ChargeTypeFilter.AC  -> periodEvents.filter { it.chargeType == "AC" }
            ChargeTypeFilter.DC  -> periodEvents.filter { it.chargeType == "DC" }
        }
        return if (filtered.isEmpty()) {
            DashboardUiState(emptyState = EmptyState.NoEvents)
        } else {
            val stats = statsCalculator.computeStats(filtered, label = period.toString())
            DashboardUiState(stats = stats, showMultiCurrencyBanner = stats.mixedCurrency)
        }
    }
}
```

- [ ] **Step 4: Run tests — 5 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ObserveDashboardStatsUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 5 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCase.kt app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCaseTest.kt
```

```bash
git commit -m "feat(domain): add ObserveDashboardStatsUseCase (Flow-driven, re-emits on event changes)"
```

---

## Task 13: `SaveChargeEventUseCase` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.CostInput
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.domain.service.CostParser
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeLocationWriter

class SaveChargeEventUseCaseTest {

    private fun build(initialEvents: List<ChargeEventEntity> = emptyList()): SaveSetup {
        val queries = FakeChargeEventQueries()
        queries.seed(initialEvents)
        val writer = FakeChargeEventWriter(queries.shareStore())
        val locationWriter = FakeLocationWriter()
        val scheduler = FakeBackupScheduler()
        val useCase = SaveChargeEventUseCase(queries, writer, locationWriter, scheduler, CostParser())
        return SaveSetup(useCase, queries, locationWriter, scheduler)
    }

    private data class SaveSetup(
        val useCase: SaveChargeEventUseCase,
        val queries: FakeChargeEventQueries,
        val locationWriter: FakeLocationWriter,
        val scheduler: FakeBackupScheduler
    )

    @Test
    fun insert_success_recordsLocationAndEnqueuesBackup() = runTest {
        val s = build()
        val result = s.useCase(SaveChargeEventInput(
            carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0,
            chargeType = "AC", location = "Home"
        ))
        assertTrue(result is SaveChargeEventResult.Success)
        assertEquals(1, s.queries.current().size)
        assertEquals("Home", s.locationWriter.current().single().label)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun update_success_keepsId() = runTest {
        val existing = ChargeEventEntity(id = 5, carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0)
        val s = build(initialEvents = listOf(existing))
        val result = s.useCase(SaveChargeEventInput(
            eventId = 5, carId = 1, eventDate = 1000L, odometerKm = 110.0, kwhAdded = 12.0,
            chargeType = "AC"
        ))
        assertEquals(SaveChargeEventResult.Success(eventId = 5L), result)
        assertEquals(110.0, s.queries.current().single().odometerKm, 0.0)
    }

    @Test
    fun insertOdometerNotIncreasing_returnsResultAndPersistsNothing() = runTest {
        val previous = ChargeEventEntity(id = 1, carId = 1, eventDate = 1000L, odometerKm = 200.0, kwhAdded = 10.0)
        val s = build(initialEvents = listOf(previous))
        val result = s.useCase(SaveChargeEventInput(
            carId = 1, eventDate = 2000L, odometerKm = 150.0,            // regression
            kwhAdded = 10.0, chargeType = "AC"
        ))
        assertEquals(SaveChargeEventResult.OdometerNotIncreasing, result)
        assertEquals(1, s.queries.current().size)                     // only the original
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun updateOdometerCheck_ignoresOwnId() = runTest {
        val existing = ChargeEventEntity(id = 5, carId = 1, eventDate = 2000L, odometerKm = 200.0, kwhAdded = 10.0)
        val before   = ChargeEventEntity(id = 4, carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0)
        val s = build(initialEvents = listOf(before, existing))
        // Update event #5 — its previous neighbor by date is #4 (odo=100). New odo (210) is fine.
        val result = s.useCase(SaveChargeEventInput(
            eventId = 5, carId = 1, eventDate = 2000L, odometerKm = 210.0, kwhAdded = 12.0,
            chargeType = "AC"
        ))
        assertTrue(result is SaveChargeEventResult.Success)
    }

    @Test
    fun costInputZero_costFieldsAreNull() = runTest {
        val s = build()
        s.useCase(SaveChargeEventInput(
            carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0,
            chargeType = "AC",
            costInput = CostInput(value = 0.0, mode = CostMode.TOTAL, currency = "EUR")
        ))
        val saved = s.queries.current().single()
        assertNull(saved.costTotal); assertNull(saved.costPerKwh); assertNull(saved.currency)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.SaveChargeEventUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `SaveChargeEventUseCase` unresolved.

- [ ] **Step 3: Implement `SaveChargeEventUseCase`**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.service.CostParser

class SaveChargeEventUseCase @Inject constructor(
    private val chargeEventQueries: ChargeEventQueries,
    private val chargeEventWriter: ChargeEventWriter,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler,
    private val costParser: CostParser
) {
    suspend operator fun invoke(input: SaveChargeEventInput): SaveChargeEventResult {
        // 1. Validate odometer > previous event's (excluding own id when updating).
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

        // 4. Record location usage if non-blank.
        input.location?.takeIf { it.isNotBlank() }?.let { locationWriter.recordUsage(it) }

        // 5. Enqueue backup.
        backupScheduler.enqueueBackup()

        return SaveChargeEventResult.Success(savedId)
    }
}
```

- [ ] **Step 4: Run tests — 5 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.SaveChargeEventUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 5 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCase.kt app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt
```

```bash
git commit -m "feat(domain): add SaveChargeEventUseCase with odo validation + cost parsing"
```

---

## Task 14: `DeleteChargeEventUseCase` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCaseTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter

class DeleteChargeEventUseCaseTest {

    @Test
    fun invoke_deletesAndEnqueuesBackup() = runTest {
        val event = ChargeEventEntity(id = 1, carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0)
        val queries = FakeChargeEventQueries()
        queries.seed(listOf(event))
        val writer = FakeChargeEventWriter(queries.shareStore())
        val scheduler = FakeBackupScheduler()
        val useCase = DeleteChargeEventUseCase(writer, scheduler)

        useCase(event)

        assertEquals(0, queries.current().size)
        assertEquals(1, scheduler.enqueueCount)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.DeleteChargeEventUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `DeleteChargeEventUseCase` unresolved.

- [ ] **Step 3: Implement `DeleteChargeEventUseCase`**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventWriter

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

- [ ] **Step 4: Run tests — 1 passes**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.DeleteChargeEventUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 1 case passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCase.kt app/src/test/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCaseTest.kt
```

```bash
git commit -m "feat(domain): add DeleteChargeEventUseCase"
```

---

## Task 15: `RestoreBackupUseCase` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.testing.FakeBackupRepository
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsWriter

class RestoreBackupUseCaseTest {

    private val serializer = BackupSerializer()

    private fun build(
        remoteJson: String? = null,
        callRecorder: MutableList<String>? = null,
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        locations: List<CustomLocationEntity> = emptyList()
    ): RestoreSetup {
        val backupRepo = FakeBackupRepository(remoteJson = remoteJson)
        val transactionRunner = FakeRestoreTransactionRunner(callRecorder)
        val snapshotWriter = FakeRestoreSnapshotWriter(callRecorder)
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries(); queries.seed(events)
        val locationReader = FakeLocationReader(locations)
        val settingsWriter = FakeSettingsWriter()
        val scheduler = FakeBackupScheduler()
        val useCase = RestoreBackupUseCase(
            backupRepo, serializer, transactionRunner, snapshotWriter,
            carReader, queries, locationReader, settingsWriter, scheduler
        )
        return RestoreSetup(useCase, transactionRunner, snapshotWriter, settingsWriter, scheduler)
    }

    private data class RestoreSetup(
        val useCase: RestoreBackupUseCase,
        val txn: FakeRestoreTransactionRunner,
        val snap: FakeRestoreSnapshotWriter,
        val settings: FakeSettingsWriter,
        val scheduler: FakeBackupScheduler
    )

    @Test
    fun noRemoteBackup_setsDriveEnabledAndQueuesBackup() = runTest {
        val s = build(remoteJson = null)
        val r = s.useCase()
        assertEquals(RestoreResult.NoRemoteBackup, r)
        assertTrue(s.settings.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun versionMismatch_doesNotSetDriveEnabled() = runTest {
        val v2 = """{"backup_version":2,"exported_at":"x","cars":[],"charge_events":[],"custom_locations":[]}"""
        val s = build(remoteJson = v2)
        val r = s.useCase()
        assertTrue(r is RestoreResult.VersionMismatch)
        assertEquals(2, (r as RestoreResult.VersionMismatch).actualVersion)
        assertFalse(s.settings.driveEnabled)
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun success_clearsAndImportsAndEnqueuesBackup() = runTest {
        val data = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)),
            events = listOf(ChargeEventEntity(id = 7, carId = 1, eventDate = 1L, odometerKm = 100.0, kwhAdded = 10.0)),
            locations = listOf(CustomLocationEntity(id = 1, label = "Home", useCount = 1, lastUsed = 0L)),
            now = 0L
        )
        val s = build(remoteJson = serializer.toJson(data))
        val r = s.useCase()
        assertTrue(r is RestoreResult.Success)
        assertEquals(1, (r as RestoreResult.Success).carCount)
        assertEquals(1, r.eventCount)
        assertEquals(1, r.locationCount)
        assertEquals(listOf(CarEntity(id = 1, name = "T", createdAt = 0L)), s.txn.lastCars)
        assertNotNull(s.txn.lastEvents)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun success_writesCacheSnapshotBeforeDestructive() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val recorder = mutableListOf<String>()
        val s = build(remoteJson = serializer.toJson(data), callRecorder = recorder)
        s.useCase()
        // Order: snapshot then transaction.
        val snapIdx = recorder.indexOf("snapshot")
        val txnIdx  = recorder.indexOf("transaction")
        assertTrue("expected snapshot before transaction; recorder=$recorder", snapIdx in 0 until txnIdx)
    }

    @Test
    fun success_setsDriveEnabledAndQueuesBackup() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val s = build(remoteJson = serializer.toJson(data))
        s.useCase()
        assertTrue(s.settings.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
    }

    @Test
    fun success_setsDriveEnabledAfterTransactionCompletes() = runTest {
        // We use the call recorder to ensure the transaction call happened before we
        // assert on settings/scheduler state in the prior test. This test combines them
        // into a single ordering assertion.
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val recorder = mutableListOf<String>()
        val s = build(remoteJson = serializer.toJson(data), callRecorder = recorder)
        s.useCase()
        assertTrue("snapshot must come before transaction", recorder.indexOf("snapshot") < recorder.indexOf("transaction"))
        assertTrue(s.settings.driveEnabled)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.RestoreBackupUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `RestoreBackupUseCase` unresolved.

- [ ] **Step 3: Implement `RestoreBackupUseCase`**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.service.BackupSerializer

class RestoreBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val backupSerializer: BackupSerializer,
    private val transactionRunner: RestoreTransactionRunner,
    private val snapshotWriter: RestoreSnapshotWriter,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(): RestoreResult {
        val json = backupRepository.readRemoteBackup()
        if (json == null) {
            settingsWriter.setDriveEnabled(true)
            backupScheduler.enqueueBackup()
            return RestoreResult.NoRemoteBackup
        }

        val parsed = try {
            backupSerializer.fromJson(json)
        } catch (e: BackupVersionMismatch) {
            return RestoreResult.VersionMismatch(e.actual)
        }

        val currentCars = carReader.observeAll().first()
        val currentEvents = currentCars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val currentLocations = locationReader.observeAll().first()
        val snapshot = BackupData.fromEntities(currentCars, currentEvents, currentLocations)
        snapshotWriter.write(backupSerializer.toJson(snapshot))

        val (newCars, newEvents, newLocations) = parsed.toEntities()
        transactionRunner.replaceAll(newCars, newEvents, newLocations)

        settingsWriter.setDriveEnabled(true)
        backupScheduler.enqueueBackup()

        return RestoreResult.Success(
            carCount = newCars.size,
            eventCount = newEvents.size,
            locationCount = newLocations.size
        )
    }
}
```

- [ ] **Step 4: Run tests — 6 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.RestoreBackupUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 6 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCase.kt app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt
```

```bash
git commit -m "feat(domain): add RestoreBackupUseCase (snapshot, replace, setDriveEnabled, queue)"
```

---

## Task 16: `ExportCsvUseCase` — TDD

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCaseTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCase.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package org.spsl.evtracker.domain.usecase

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class ExportCsvUseCaseTest {

    private val useCase = ExportCsvUseCase(
        carReader = org.spsl.evtracker.testing.FakeCarReader(),
        chargeEventQueries = org.spsl.evtracker.testing.FakeChargeEventQueries(),
        csvFileSink = object : org.spsl.evtracker.domain.backup.CsvFileSink {
            override suspend fun write(carName: String, body: (java.io.Writer) -> Unit) =
                throw NotImplementedError("not used in this test class")
        }
    )

    private val sampleEvents = listOf(
        ChargeEventEntity(id = 1, carId = 1, eventDate = 1714044000000L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = "AC", costTotal = 5.0, currency = "EUR", note = "n"),
        ChargeEventEntity(id = 2, carId = 1, eventDate = 1714130400000L, odometerKm = 1100.0, kwhAdded = 12.0, chargeType = "DC")
    )

    @Test
    fun headerLineUsesKmOrMilesPerFlag() {
        val writerKm = StringWriter()
        useCase.writeCsv(writerKm, sampleEvents, useKm = true)
        val firstLineKm = writerKm.toString().lineSequence().first()
        assertTrue("expected odometer_km in $firstLineKm", firstLineKm.contains("odometer_km"))

        val writerMi = StringWriter()
        useCase.writeCsv(writerMi, sampleEvents, useKm = false)
        val firstLineMi = writerMi.toString().lineSequence().first()
        assertTrue("expected odometer_miles in $firstLineMi", firstLineMi.contains("odometer_miles"))
    }

    @Test
    fun rowCountMatchesEventCount() {
        val w = StringWriter()
        useCase.writeCsv(w, sampleEvents, useKm = true)
        // 1 header + 2 data rows = 3 non-empty lines.
        val nonEmpty = w.toString().lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(3, nonEmpty.size)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ExportCsvUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: compilation failure — `ExportCsvUseCase` unresolved.

- [ ] **Step 3: Implement `ExportCsvUseCase`**

```kotlin
package org.spsl.evtracker.domain.usecase

import android.net.Uri
import java.io.Writer
import java.time.Instant
import javax.inject.Inject
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.CsvFileSink
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.service.UnitConverter

class ExportCsvUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val csvFileSink: CsvFileSink
) {
    suspend fun export(carId: Int, useKm: Boolean): Uri {
        val car = carReader.getById(carId) ?: throw IllegalArgumentException("Unknown carId=$carId")
        val events = chargeEventQueries.getAllForCarSorted(carId)
        return csvFileSink.write(car.name) { writer -> writeCsv(writer, events, useKm) }
    }

    /** Package-private for unit tests; do not call directly from production. */
    internal fun writeCsv(writer: Writer, events: List<ChargeEventEntity>, useKm: Boolean) {
        writer.append("date,odometer_${if (useKm) "km" else "miles"},kwh,charge_type,location,cost_total,currency,note\n")
        for (e in events) {
            val odo = if (useKm) e.odometerKm else UnitConverter.kmToMiles(e.odometerKm)
            writer.append(Instant.ofEpochMilli(e.eventDate).toString()).append(',')
                  .append(odo.toString()).append(',')
                  .append(e.kwhAdded.toString()).append(',')
                  .append(e.chargeType).append(',')
                  .append(csvEscape(e.location ?: "")).append(',')
                  .append(e.costTotal?.toString() ?: "").append(',')
                  .append(e.currency ?: "").append(',')
                  .append(csvEscape(e.note)).append('\n')
        }
    }

    private fun csvEscape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"${s.replace("\"", "\"\"")}\""
        else s
}
```

- [ ] **Step 4: Run tests — 2 pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ExportCsvUseCaseTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL, 2 cases passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCase.kt app/src/test/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCaseTest.kt
```

```bash
git commit -m "feat(domain): add ExportCsvUseCase with sink-abstracted file I/O"
```

---

## Task 17: `DomainModule` + final acceptance

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt`

- [ ] **Step 1: Create `DomainModule.kt`**

```kotlin
package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.backup.AndroidCsvFileSink
import org.spsl.evtracker.data.backup.CacheDirRestoreSnapshotWriter
import org.spsl.evtracker.data.backup.NoOpBackupRepository
import org.spsl.evtracker.data.backup.NoOpBackupScheduler
import org.spsl.evtracker.data.backup.RoomRestoreTransactionRunner
import org.spsl.evtracker.data.repository.CarRepository
import org.spsl.evtracker.data.repository.ChargeEventRepository
import org.spsl.evtracker.data.repository.LocationRepository
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.CsvFileSink
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import org.spsl.evtracker.domain.backup.RestoreTransactionRunner
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    // Repository interfaces — bound to B's existing implementations.
    @Binds abstract fun bindCarReader(impl: CarRepository): CarReader
    @Binds abstract fun bindChargeEventQueries(impl: ChargeEventRepository): ChargeEventQueries
    @Binds abstract fun bindChargeEventWriter(impl: ChargeEventRepository): ChargeEventWriter
    @Binds abstract fun bindLocationReader(impl: LocationRepository): LocationReader
    @Binds abstract fun bindLocationWriter(impl: LocationRepository): LocationWriter
    @Binds abstract fun bindSettingsReader(impl: SettingsRepository): SettingsReader
    @Binds abstract fun bindSettingsWriter(impl: SettingsRepository): SettingsWriter

    // Backup interfaces — no-op until E swaps these.
    @Binds abstract fun bindBackupScheduler(impl: NoOpBackupScheduler): BackupScheduler
    @Binds abstract fun bindBackupRepository(impl: NoOpBackupRepository): BackupRepository

    // Restore-flow infrastructure.
    @Binds abstract fun bindRestoreTransactionRunner(impl: RoomRestoreTransactionRunner): RestoreTransactionRunner
    @Binds abstract fun bindRestoreSnapshotWriter(impl: CacheDirRestoreSnapshotWriter): RestoreSnapshotWriter

    // CSV export infrastructure.
    @Binds abstract fun bindCsvFileSink(impl: AndroidCsvFileSink): CsvFileSink
}
```

- [ ] **Step 2: Run the full build**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL. Hilt code generation succeeds (12 new `@Binds` declarations resolve cleanly).

- [ ] **Step 3: Run the full JVM test suite — 64 tests**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL.

Aggregate test count:
```bash
find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h "tests=\"" {} \;
```

Expected: 11 new test classes plus the 2 from A+B (SettingsRepositoryTest tests=5, WizardViewModelTest tests=9). Total 64 tests, 0 failures, 0 errors:
- `SettingsRepositoryTest tests="5"`
- `WizardViewModelTest tests="9"`
- `UnitConverterTest tests="4"`
- `CostParserTest tests="7"`
- `DateRangeResolverTest tests="5"`
- `StatsCalculatorTest tests="6"`
- `StatsCalculatorCostTest tests="5"`
- `BackupSerializerTest tests="4"`
- `ObserveDashboardStatsUseCaseTest tests="5"`
- `SaveChargeEventUseCaseTest tests="5"`
- `DeleteChargeEventUseCaseTest tests="1"`
- `RestoreBackupUseCaseTest tests="6"`
- `ExportCsvUseCaseTest tests="2"`

Total: 5 + 9 + 4 + 7 + 5 + 6 + 5 + 4 + 5 + 5 + 1 + 6 + 2 = **64 JVM tests**, 0 failures.

- [ ] **Step 4: Compile-check the instrumented suite (no emulator needed)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.

Expected: BUILD SUCCESSFUL. C adds no instrumented tests; B's 25 stay compile-clean.

- [ ] **Step 5: Manual smoke (optional, requires emulator)**

If a device or emulator is connected:
1. Install the debug APK.
2. Launch the app — wizard appears on first launch.
3. Walk through the wizard.
4. The dashboard placeholder still appears (D hasn't been implemented yet).
5. Confirm no crash on app start.

The no-op backup wiring is invisible to users. `RestoreBackupUseCase` would short-circuit on `NoRemoteBackup` if invoked, but no UI invokes it yet (F's Settings screen lands later).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/di/DomainModule.kt
```

```bash
git commit -m "feat(di): add DomainModule binding 12 domain interfaces"
```

---

## Coverage check (spec → tasks)

| Spec section | Implementation | Test |
|---|---|---|
| §3 Architecture (file map) | All `core/model/`, `domain/`, `data/backup/`, `di/DomainModule.kt` | — |
| §4 Repository interfaces | Task 2 | (verified by use case tests in 12, 13, 14, 15, 16) |
| §5.1 UnitConverter | Task 5 | UnitConverterTest |
| §5.2 CostParser | Task 6 | CostParserTest |
| §5.3 DateRangeResolver | Task 7 | DateRangeResolverTest |
| §5.4 StatsCalculator | Task 8 | StatsCalculatorTest, StatsCalculatorCostTest |
| §5.5 BackupSerializer | Task 9 | BackupSerializerTest |
| §6 Domain models | Task 1 | (covered indirectly by use case + service tests) |
| §7 Backup interfaces + no-op + Android impls | Tasks 3, 10 | (verified by use case tests via Fake* implementations) |
| §8.1 ObserveDashboardStatsUseCase | Task 12 | ObserveDashboardStatsUseCaseTest |
| §8.2 SaveChargeEventUseCase | Task 13 | SaveChargeEventUseCaseTest |
| §8.3 DeleteChargeEventUseCase | Task 14 | DeleteChargeEventUseCaseTest |
| §8.4 RestoreBackupUseCase | Task 15 | RestoreBackupUseCaseTest |
| §8.5 ExportCsvUseCase | Task 16 | ExportCsvUseCaseTest |
| §9 Hilt wiring | Task 17 | (compile-checked by `assembleDebug`) |
| §10 Tests | Tasks 5–16 | 50 new JVM tests across 11 classes |
