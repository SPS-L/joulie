# Agent Instructions — Build EV Efficiency Tracker APK

> **⚠ Historical document.** This was the original step-by-step build walkthrough used to bring the app up from an empty repository. As of v1.0.1 (April 2026), every phase below is implemented and merged on `main`, including the post-v1 refactors tracked in [`BACKLOG.md`](BACKLOG.md). This file is preserved for architectural-intent reference and onboarding contributors who want to understand how the app was originally built; it is **not** the source of truth for live development. For current invariants and conventions see [`../CLAUDE.md`](../CLAUDE.md), and for live work items see [`BACKLOG.md`](BACKLOG.md). The canonical product + technical spec remains [`DESIGN.md`](DESIGN.md).

This document gives a complete step-by-step guide for an AI coding agent (or developer) to implement the full application defined in [`DESIGN.md`](DESIGN.md) and produce a debug APK.

> **Covers all phases:** project scaffold, DB, basic UI, charts, Drive backup, CSV, first-boot wizard, location chips, cost handling, multi-metric display.

---

## Prerequisites

- JDK 17+
- Android SDK (API 26 min, API 35 target, Build Tools 35.0.0)
- Gradle 8.4+
- `ANDROID_HOME` environment variable set
- Internet access (to download Maven dependencies)
- Google Play Services available on target device/emulator (for Drive backup)

---

## Step 1 — Scaffold Project

Verify the working tree:
```bash
git status
```

The repo currently contains only docs and Gradle config — no Kotlin source. Start by creating `app/src/main/java/org/spsl/evtracker/MainActivity.kt`, then implement all other Kotlin source files and XML resources described in [`DESIGN.md`](DESIGN.md).

> **Column-naming convention (critical):** Room generates **camelCase** column names from Kotlin field names by default. This guide uses camelCase consistently in `ALTER TABLE`, `CREATE TABLE`, and `@Query` SQL — do not switch to snake_case unless you also annotate every field with `@ColumnInfo(name = "...")`. The SQL shown in `DESIGN.md §4.1` is illustrative; the actual column names match the entity field names.

---

## Step 2 — Establish App Structure And Dependency Injection

Create the package layout described in `DESIGN.md §5`.

Recommended source tree:

```text
org/spsl/evtracker/
  EVTrackerApp.kt
  di/
  core/model/
  core/util/
  data/local/entity/
  data/local/dao/
  data/local/db/
  data/preferences/
  data/backup/
  data/backup/drive/
  data/repository/
  domain/service/
  domain/usecase/
  ui/dashboard/
  ui/chargeedit/
  ui/history/
  ui/charts/
  ui/settings/
  ui/wizard/
  ui/cars/
  ui/locations/
```

Preferred dependency setup: **Hilt**.

- Add Hilt dependencies and Gradle plugins.
- Create `EVTrackerApp : Application()` annotated with `@HiltAndroidApp`.
- Annotate `MainActivity` with `@AndroidEntryPoint`.
- Provide Room, DataStore, repositories, use cases, and backup infrastructure from Hilt modules in `di/`.

Do not scatter object construction across Fragments or ViewModels.

### 2.1 Navigation scaffold

Create the Navigation Component files and IDs before implementing Fragment logic.

- Add `app/src/main/res/navigation/nav_graph.xml`.
- Define destinations for `wizardFragment`, `dashboardFragment`, `chargeEditFragment`, `carsFragment`, `settingsFragment`, `chartsFragment`, `historyFragment`, and `manageLocationsFragment`.
- Add navigation actions that are already referenced elsewhere in the docs, such as `action_wizard_to_dashboard`.
- Wire the host container in the activity layout using a stable ID such as `nav_host_fragment` and keep that ID consistent with `MainActivity` navigation calls.

This should be created alongside the first Fragment so references like `R.id.wizardFragment` and `R.id.nav_host_fragment` are real from the start.

---

## Step 3 — Implement Core Models And Data Sources

### 3.1 Core and entity models

Keep pure models separate from Room entities.

- `data/local/entity/CarEntity.kt`
- `data/local/entity/ChargeEventEntity.kt`
- `data/local/entity/CustomLocationEntity.kt`
- `core/model/Stats.kt`
- `core/model/MonthBucket.kt`
- `core/model/BackupData.kt`

The Room entities still mirror the schema from `DESIGN.md §4`.

```kotlin
@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val batteryKwh: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

```kotlin
@Entity(
    tableName = "charge_events",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["carId", "eventDate"]),
        Index("chargeType"),
        Index("location")
    ]
)
data class ChargeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: String = "AC",
    val costTotal: Double? = null,
    val costPerKwh: Double? = null,
    val currency: String? = null,
    val location: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

```kotlin
data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgEfficiencyKmPerKwh: Double?,
    val chargeCount: Int,
    val costPerKm: Double?,
    val costPer100km: Double?
)
```

### 3.2 DAOs and database

Keep DAOs storage-focused.

- `CarDao` exposes car CRUD and ordered car list.
- `ChargeEventDao` exposes per-car queries, range queries, sorted queries, and delete helpers.
- `CustomLocationDao` exposes top-5 query, full list query, insert-or-increment support, and delete.

`AppDatabase` remains version 3 with `MIGRATION_1_2` and `MIGRATION_2_3` exactly matching the camelCase schema.

Do not add stats computation or backup behavior inside DAO or database classes.

### 3.3 Preferences and infrastructure adapters

Create these infrastructure pieces early:

- `data/preferences/PreferenceKeys.kt`
- `data/preferences/SettingsLocalDataSource.kt`
- `data/backup/drive/DriveAuthManager.kt`
- `data/backup/drive/DriveBackupClient.kt`
- `data/backup/BackupScheduler.kt`

`PreferenceKeys` still contains:

```kotlin
object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT = stringPreferencesKey("distanceUnit")
    val CURRENCY = stringPreferencesKey("currency")
    val ACTIVE_CAR_ID = intPreferencesKey("activeCarId")
    val DRIVE_ENABLED = booleanPreferencesKey("driveEnabled")
    val THEME = stringPreferencesKey("theme")
}
```

The backup scheduler should be a thin wrapper around WorkManager so backup enqueueing is injectable and testable.

---

## Step 4 — Implement Narrow Repositories

Repositories should aggregate data sources, not host business rules.

### CarRepository.kt

Responsibilities:

- observe car list
- insert, update, delete cars
- support active-car-safe delete/reset helpers when needed

### ChargeEventRepository.kt

Responsibilities:

- insert, update, delete charge events
- observe events for the active car
- query events by date range or full sorted history

Do **not** put `StatsCalculator`, cost parsing, or chart aggregation in this repository.

### LocationRepository.kt

Responsibilities:

- observe top 5 custom locations
- observe full location list
- record usage of a label
- delete a custom location

### SettingsRepository.kt

Responsibilities:

- read and write setup keys, theme, active car, and Drive enabled state
- expose preferences as Flows

### BackupRepository.kt

Responsibilities:

- serialize current app data to backup JSON
- upload to Drive App Data folder
- download and parse `evtracker_backup.json`

This repository owns raw backup I/O only. It does not decide when backup or restore should happen.

---

## Step 5 — Implement Domain Services And Use Cases

This is the layer that holds application rules.

### 5.1 Pure services (`domain/service/`)

Create framework-free services for business logic.

**StatsCalculator.kt**

```kotlin
class StatsCalculator {
    fun computeStats(events: List<ChargeEventEntity>, label: String): Stats {
        val totalKwhAll = events.sumOf { it.kwhAdded }
        val chargeCount = events.size
        if (events.size < 2) {
            return Stats(label, totalKwhAll, 0.0, null, chargeCount, null, null)
        }

        val costedCurrencies = events.mapNotNull { event -> event.costTotal?.let { event.currency } }.distinct()
        val mixedCurrency = costedCurrencies.size > 1

        var pairKwh = 0.0
        var totalDist = 0.0
        var totalCost = 0.0
        var costCount = 0

        for (index in 1 until events.size) {
            val dist = events[index].odometerKm - events[index - 1].odometerKm
            if (dist > 0) {
                pairKwh += events[index].kwhAdded
                totalDist += dist
                if (!mixedCurrency) {
                    events[index].costTotal?.let {
                        totalCost += it
                        costCount++
                    }
                }
            }
        }

        val avgKmPerKwh = if (pairKwh > 0) totalDist / pairKwh else null
        val costPerKm = if (costCount > 0 && totalDist > 0) totalCost / totalDist else null
        return Stats(label, totalKwhAll, totalDist, avgKmPerKwh, chargeCount, costPerKm, costPerKm?.times(100.0))
    }
}
```

**CostParser.kt**

```kotlin
enum class CostMode { TOTAL, PER_KWH }

class CostParser {
    fun parse(value: Double?, kwh: Double, mode: CostMode): Pair<Double?, Double?> {
        if (value == null || value <= 0.0 || kwh <= 0.0) return Pair(null, null)
        return when (mode) {
            CostMode.TOTAL -> Pair(value, value / kwh)
            CostMode.PER_KWH -> Pair(value * kwh, value)
        }
    }
}
```

**DateRangeResolver.kt**

```kotlin
data class DateRange(val startMillis: Long, val endMillis: Long)

class DateRangeResolver {
    fun resolve(period: DashboardPeriod, nowMillis: Long = System.currentTimeMillis()): DateRange {
        // convert SincePreviousCharge / 7d / 30d / Year / Custom into an inclusive epoch-millis range
    }
}
```

**BackupSerializer.kt**

```kotlin
class BackupSerializer {
    fun toJson(data: BackupData): String {
        // serialize using the authoritative v3 schema from DESIGN.md §8
    }

    fun fromJson(json: String): BackupData {
        // deserialize and validate backup_version before returning BackupData
    }
}
```

Also create `UnitConverter.kt` as a pure Kotlin helper.

### 5.2 Use cases (`domain/usecase/`)

Create use cases for every cross-source workflow.

**SaveChargeEventUseCase**

- validate odometer is greater than the prior entry for the same car
- normalize cost input with `CostParser`
- persist the event
- record location usage
- enqueue backup when Drive is enabled

```kotlin
class SaveChargeEventUseCase @Inject constructor(
    private val chargeEventRepository: ChargeEventRepository,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val backupScheduler: BackupScheduler,
    private val costParser: CostParser
) {
    suspend operator fun invoke(input: SaveChargeEventInput) {
        // validate previous odometer, normalize cost, save, update learned locations, schedule backup
    }
}
```

**ObserveDashboardStatsUseCase**

- combine active car, selected period, AC/DC filter, and preferences
- fetch the required events
- calculate stats and empty-state/banner conditions
- return one UI-ready state object for dashboard rendering

**DeleteChargeEventUseCase**

- finalize deletion only after undo expiry or explicit confirmation
- schedule backup after the delete is truly committed

**RestoreBackupUseCase**

- download and parse backup data
- validate `backup_version`
- export current local data to `cacheDir/last_overwritten_backup.json`
- replace local tables transactionally
- update settings needed for a consistent post-restore state

**ExportCsvUseCase**

- map odometer display values at export time only
- leave first-event efficiency blank
- share via `FileProvider`

---

## Step 6 — Implement ViewModels And UI

Build the UI around screen state and use cases, not direct DAO or DataStore access.

### 6.1 ViewModels

Each feature gets a dedicated ViewModel in `ui/<feature>/`.

- `WizardViewModel`
- `DashboardViewModel`
- `ChargeEditViewModel`
- `CarsViewModel`
- `SettingsViewModel`
- `ChartsViewModel`
- `HistoryViewModel`
- `ManageLocationsViewModel`

Use Hilt ViewModel injection:

```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val observeDashboardStats: ObserveDashboardStatsUseCase
) : ViewModel() {
    // expose a single StateFlow<DashboardUiState>
}
```

Do not inject Room DAOs directly into ViewModels.

### 6.2 Wizard flow

`WizardViewModel` should talk to `SettingsRepository`, not raw DataStore. Finishing the wizard must be a suspendable completion path so persistence finishes before navigation.

```kotlin
data class WizardUiState(
    val selectedMetric: String = "km_per_kwh",
    val selectedUnit: String = "km",
    val selectedCurrency: String = "EUR"
)

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    fun updateMetric(metric: String) {
        _uiState.update { it.copy(selectedMetric = metric) }
    }

    fun updateUnit(unit: String) {
        _uiState.update { it.copy(selectedUnit = unit) }
    }

    fun updateCurrency(currency: String) {
        _uiState.update { it.copy(selectedCurrency = currency) }
    }

    suspend fun finish() {
        val state = uiState.value
        settingsRepository.completeSetup(
            metric = state.selectedMetric,
            unit = state.selectedUnit,
            currency = state.selectedCurrency
        )
    }
}
```

In `WizardFragment`, collect `uiState`, render the selected values into the View-based widgets, and call `finish()` from a coroutine before navigating.

### 6.3 Screen implementation notes

**Dashboard**

- `MaterialToolbar` with car selector
- period tabs: Since previous charge, 7d, 30d, Year, Custom
- filter chips: All, AC, DC
- large primary metric card plus secondary cards
- banner for multi-currency periods
- empty states for no car vs no events

**Charge Edit**

- form state lives in `ChargeEditUiState`
- top 5 custom chips are collected from `LocationRepository`
- fixed Home / Work / Public chips are always present
- save action goes through `SaveChargeEventUseCase`

**History**

- paged list for the active car
- edit target routing
- delete with Snackbar undo backed by `DeleteChargeEventUseCase`

**Charts**

- chart models should come from the same stats/query rules as dashboard state
- keep MPAndroidChart setup in UI, but keep aggregation logic outside the Fragment

---

## Step 7 — Implement Backup Infrastructure And Restore Flow

**Scope:** `https://www.googleapis.com/auth/drive.appdata`.

Use the Authorization API, not `GoogleSignIn.getClient(...)`.

### 7.1 DriveAuthManager.kt

Keep auth concerns separate from backup serialization and restore logic.

```kotlin
class DriveAuthManager @Inject constructor(
    @ActivityContext private val context: Context
) {
    private val client = Identity.getAuthorizationClient(context)

    suspend fun authorizeForAppData(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
```

### 7.2 BackupRepository and scheduler

`BackupRepository` owns raw upload/download work.

- `backupCurrentData()` serializes all backed-up entities using the authoritative schema in `DESIGN.md §8`
- `readRemoteBackup()` downloads and parses `evtracker_backup.json`

`BackupScheduler` wraps WorkManager:

```kotlin
class BackupScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun enqueueBackup() {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork("drive_backup", ExistingWorkPolicy.REPLACE, request)
    }
}
```

Schedule backup after all persisted changes that affect the backup payload, not only charge saves.

### 7.3 Restore flow

Implement restore through `RestoreBackupUseCase`, not directly in `SettingsFragment`.

Required behavior:

- fetch remote backup after Drive auth succeeds
- if a backup exists, show replace confirmation
- write current local data to `cacheDir/last_overwritten_backup.json`
- clear and import in one transaction
- expose undo availability in Settings until the cached snapshot expires

---

## Step 8 — Implement CSV Export Through A Use Case

Create `ExportCsvUseCase.kt` rather than a free-floating exporter object.

```kotlin
class ExportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun export(carName: String, events: List<ChargeEventEntity>, useKm: Boolean): Uri {
        // write CSV to external downloads dir and return FileProvider Uri
    }
}
```

Rules:

- header reflects the selected display unit
- stored odometer remains km; conversion happens only at export time
- first event efficiency column is blank
- note, currency, location, and cost fields are all included
- use `${packageName}.fileprovider`

---

## Step 9 — Build & Package APK

```bash
cd EV-android-app
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

For release (requires keystore):
```bash
./gradlew assembleRelease
```

---

## Step 10 — Run Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires connected device or emulator API 26+)
./gradlew connectedAndroidTest
```

---

## Final Checklist

- [ ] All Kotlin files implemented per DESIGN.md
- [ ] All XML layouts implemented
- [ ] Hilt wiring in place: `@HiltAndroidApp`, `@AndroidEntryPoint`, and DI modules for Room, DataStore, repositories, use cases, and backup infrastructure
- [ ] DB version = 3; migrations 1→2 and 2→3 registered
- [ ] `setupComplete` defaults to `false`; wizard shown on first launch
- [ ] Wizard persists `primaryMetric`, `distanceUnit`, `currency`, and `setupComplete=true` before navigating to Dashboard
- [ ] `custom_locations` table created; top-5 chips rendered dynamically
- [ ] Home / Work / Public chips always present in charge form
- [ ] Cost = 0 or blank → `costTotal = NULL`; excluded from all stats
- [ ] Stats, cost parsing, unit conversion, and backup serialization live outside repositories and Fragments
- [ ] Charge save, delete, dashboard aggregation, restore, and CSV export flow through use cases
- [ ] Dashboard hides cost rows when no cost data
- [ ] All 3 efficiency metrics visible on dashboard; primary metric highlighted per pref
- [ ] Drive backup JSON version = 3; includes `custom_locations`
- [ ] Backup scheduler is injected and enqueues unique WorkManager backup jobs for persisted data changes
- [ ] Nav graph connects all fragments including WizardFragment
- [ ] Dark theme tested
- [ ] Drive backup tested with emulator
- [ ] CSV export tested — file opens in spreadsheet app
- [ ] All unit tests pass
- [ ] All instrumented tests pass
- [ ] APK installs on API 26+ device/emulator
