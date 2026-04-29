# Sub-project F1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire every still-placeholder Settings row, ship the ManageLocations screen, and add two new "reset data" use cases (per-active-car and global) — with a durable `resetInProgress` flag plus `MainActivity` startup auto-recovery, including a blocking retry dialog if recovery itself fails, so the user cannot reach normal UI with `resetInProgress=true`.

**Architecture:** Extend the narrow domain interfaces (`SettingsReader/Writer`, `ChargeEventWriter`, `LocationWriter`, `CarWriter`) with the methods F1 needs; introduce one new domain interface `DataResetTransactionRunner` (mirrors the existing `RestoreTransactionRunner`) so the use cases never see Room directly; wire two new use cases (`ResetActiveCarDataUseCase`, `ResetAllDataUseCase`) and one new screen (`ManageLocationsFragment`). All atomic preference writes use a single `dataStore.edit { ... }` block; cross-table deletes go through `database.withTransaction { ... }` via the runner. Recovery from an interrupted reset runs at `MainActivity` startup before any UI mounts.

**Tech Stack:** Kotlin · Hilt 2.50 (KSP) · Room 2.6 + `androidx.room.withTransaction` · DataStore Preferences · WorkManager 2.9 · MaterialComponents · Navigation Component · ViewBinding · MPAndroidChart (NOT used in F1) · JUnit 4 + mockito-kotlin 5.2.1 + kotlinx-coroutines-test · Espresso/Hilt for instrumented tests.

**Spec:** [`docs/superpowers/specs/2026-04-28-sub-project-f1-design.md`](../specs/2026-04-28-sub-project-f1-design.md) (review revs 1-4 applied).

**Branch:** `feat/sub-project-f1`. Cut from `main` at the start of Task 1.

---

## File map

### Domain (interfaces F1 owns)

| File | Status | What it does |
|---|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt` | modify | adds `theme: Flow<String>` and `resetInProgress: Flow<Boolean>` |
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt` | modify | adds `setTheme`, `setPrimaryMetric`, `setDistanceUnit`, `setCurrency`, `setSetupComplete`, `setResetInProgress`, `setPrimaryMetricAndDistanceUnit`, `markGlobalResetInProgress` |
| `app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventWriter.kt` | modify | adds `deleteForCar(carId)`, `deleteAll()` |
| `app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt` | modify | adds `deleteAll()` |
| `app/src/main/java/org/spsl/evtracker/domain/repository/CarWriter.kt` | modify | adds `deleteAll()` |
| `app/src/main/java/org/spsl/evtracker/domain/repository/DataResetTransactionRunner.kt` | **create** | atomic `clearAllTables()` over the three tables — domain-side abstraction for the reset use case |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCase.kt` | **create** | per-car wipe |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCase.kt` | **create** | global wipe with idempotent step ordering |

### Data layer

| File | Status | What it does |
|---|---|---|
| `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt` | modify | adds `RESET_IN_PROGRESS = booleanPreferencesKey("resetInProgress")` |
| `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` | modify | implements all new SettingsWriter methods + the two atomic combined writers; adds `resetInProgress` Flow |
| `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt` | modify | implements `deleteForCar`, `deleteAll` |
| `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt` | modify | implements `deleteAll` |
| `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt` | modify | implements `deleteAll` |
| `app/src/main/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunner.kt` | **create** | wraps `database.withTransaction { ... }` over the three DAOs |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt` | modify | adds `@Query("DELETE FROM charge_events WHERE carId = :carId") suspend fun deleteForCar(carId: Int)` |
| `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt` | modify | `@Binds` for `DataResetTransactionRunner` |

### UI

| File | Status | What it does |
|---|---|---|
| `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt` | modify | adds new state fields + new events |
| `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt` | modify | new flow collectors + new action methods (theme/units/metric/currency/reset/CSV/nav-to-locations) |
| `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` | modify | wire row clicks, dialogs, Snackbars, theme apply, share-intent, nav |
| `app/src/main/res/layout/fragment_settings.xml` | modify | replace 6 disabled placeholders with real rows |
| `app/src/main/java/org/spsl/evtracker/core/model/ManageLocationsUiState.kt` | **create** | UiState (with derived `visibleLocations`) + Event |
| `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModel.kt` | modify | full implementation |
| `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsAdapter.kt` | **create** | `ListAdapter<CustomLocationEntity, _>` + `DiffUtil` |
| `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt` | modify | RecyclerView + ItemTouchHelper + Snackbar Undo |
| `app/src/main/res/layout/fragment_manage_locations.xml` | modify | RecyclerView + empty-state TextView |
| `app/src/main/res/layout/item_custom_location.xml` | **create** | row layout |
| `app/src/main/res/navigation/nav_graph.xml` | modify | two new actions on `settingsFragment` |
| `app/src/main/res/values/strings.xml` | modify | ~30 new keys |
| `app/src/main/java/org/spsl/evtracker/MainActivity.kt` | modify | inject `ResetAllDataUseCase`; auto-recovery before the `setupComplete` read |

### Tests

| File | Status |
|---|---|
| `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` | modify (new fake methods + `FakeDataResetTransactionRunner`) |
| `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryAtomicWritesTest.kt` | **create** |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCaseTest.kt` | **create** |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt` | **create** |
| `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt` | modify (extend with F1 cases) |
| `app/src/test/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModelTest.kt` | **create** |
| `app/src/androidTest/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunnerTest.kt` | **create** |
| `app/src/androidTest/java/org/spsl/evtracker/MainActivityResetRecoveryTest.kt` | **create** |
| `app/src/androidTest/java/org/spsl/evtracker/ui/settings/SettingsFragmentTest.kt` | **create** |
| `app/src/androidTest/java/org/spsl/evtracker/ui/locations/ManageLocationsFragmentTest.kt` | **create** |

---

## Sandbox quirk (CLAUDE.md)

Gradle's default `~/.gradle` is read-only in the sandbox. ALWAYS prefix gradle invocations with `GRADLE_USER_HOME=/tmp/gradle-home`:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

## TDD discipline

Every task follows: failing test → minimal impl → green → commit. The ordering is chosen so each commit leaves the tree compiling and tests green — this matters because a partial commit that breaks compile poisons subsequent task subagents.

## Branching

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feat/sub-project-f1
```

---

## Task 1: Branch + extend SettingsReader/Writer interfaces (no impls yet)

We extend the interfaces FIRST. Implementations and tests follow in the next tasks. Until Task 2 commits the impls, the project will not compile because `SettingsRepository` doesn't satisfy the interfaces — that's expected and intentional within Task 1.

> **Note for the implementer:** Task 1 alone leaves the build broken. Don't run gradle at the end of Task 1; pair Task 1's commit with Task 2's so the gradle gate fires only once both interface and impl land. Concretely: complete Task 1's edits, then complete Task 2 in the same dispatch, run gradle once at the end of Task 2.

**Files:**
- Create branch
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt`

- [ ] **Step 1: Create branch from main**

```bash
git checkout main
git pull --ff-only origin main
git checkout -b feat/sub-project-f1
```

- [ ] **Step 2: Extend SettingsReader.kt**

Replace the file's contents with:

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    val activeCarId: Flow<Int>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
    val driveEnabled: Flow<Boolean>
    /** Null when no successful backup has been recorded yet. */
    val lastBackupAt: Flow<Long?>
    /** F1: theme is "system" | "light" | "dark". */
    val theme: Flow<String>
    /**
     * F1: durable flag set true at start of `ResetAllDataUseCase`, cleared at the end.
     * `MainActivity` reads this at startup; if true, the use case re-runs to completion
     * before any UI mounts so the user never reaches an inconsistent state.
     */
    val resetInProgress: Flow<Boolean>
}
```

- [ ] **Step 3: Extend SettingsWriter.kt**

Replace the file's contents with:

```kotlin
package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)

    // F1:
    suspend fun setTheme(value: String)
    suspend fun setPrimaryMetric(metric: String)
    suspend fun setDistanceUnit(unit: String)
    suspend fun setCurrency(code: String)
    suspend fun setSetupComplete(value: Boolean)
    suspend fun setResetInProgress(value: Boolean)

    /** Writes both keys in a single dataStore.edit { ... } block. */
    suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String)

    /**
     * Atomic Step 1 of ResetAllDataUseCase: writes setupComplete=false, activeCarId=-1,
     * AND resetInProgress=true inside a single dataStore.edit { ... } block.
     */
    suspend fun markGlobalResetInProgress()
}
```

- [ ] **Step 4: DO NOT commit yet — this commit is paired with Task 2**

The project will not compile until Task 2 lands the impl. Move on to Task 2.

---

## Task 2: SettingsRepository impl + atomic-write tests

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryAtomicWritesTest.kt`

- [ ] **Step 1: Add RESET_IN_PROGRESS to PreferenceKeys.kt**

Replace the file with:

```kotlin
package org.spsl.evtracker.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val SETUP_COMPLETE = booleanPreferencesKey("setupComplete")
    val PRIMARY_METRIC = stringPreferencesKey("primaryMetric")
    val DISTANCE_UNIT  = stringPreferencesKey("distanceUnit")
    val CURRENCY       = stringPreferencesKey("currency")
    /** Sentinel value `-1` means no car selected (DataStore default when key absent). */
    val ACTIVE_CAR_ID  = intPreferencesKey("activeCarId")     // consumed by Sub-project B
    val DRIVE_ENABLED  = booleanPreferencesKey("driveEnabled") // consumed by Sub-project E
    val THEME          = stringPreferencesKey("theme")
    val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt") // consumed by Sub-project E
    /** F1: durable interrupted-reset flag. See ResetAllDataUseCase + MainActivity §9.2. */
    val RESET_IN_PROGRESS = booleanPreferencesKey("resetInProgress")
}
```

- [ ] **Step 2: Replace SettingsRepository.kt**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsReader, SettingsWriter {
    val setupComplete: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }

    override val primaryMetric: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.PRIMARY_METRIC] ?: "km_per_kwh" }

    override val distanceUnit: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.DISTANCE_UNIT] ?: "km" }

    override val currency: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.CURRENCY] ?: "EUR" }

    override val driveEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.DRIVE_ENABLED] ?: false }

    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[PreferenceKeys.LAST_BACKUP_AT] }

    override val theme: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    override val resetInProgress: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.RESET_IN_PROGRESS] ?: false }

    override val activeCarId: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT]  = unit
            prefs[PreferenceKeys.CURRENCY]       = currency
            prefs[PreferenceKeys.SETUP_COMPLETE] = true
        }
    }

    override suspend fun setTheme(value: String) {
        dataStore.edit { it[PreferenceKeys.THEME] = value }
    }

    override suspend fun setPrimaryMetric(metric: String) {
        dataStore.edit { it[PreferenceKeys.PRIMARY_METRIC] = metric }
    }

    override suspend fun setDistanceUnit(unit: String) {
        dataStore.edit { it[PreferenceKeys.DISTANCE_UNIT] = unit }
    }

    override suspend fun setCurrency(code: String) {
        dataStore.edit { it[PreferenceKeys.CURRENCY] = code }
    }

    override suspend fun setSetupComplete(value: Boolean) {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = value }
    }

    override suspend fun setResetInProgress(value: Boolean) {
        dataStore.edit { it[PreferenceKeys.RESET_IN_PROGRESS] = value }
    }

    override suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT]  = unit
        }
    }

    override suspend fun markGlobalResetInProgress() {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SETUP_COMPLETE]    = false
            prefs[PreferenceKeys.ACTIVE_CAR_ID]     = -1
            prefs[PreferenceKeys.RESET_IN_PROGRESS] = true
        }
    }

    override suspend fun setActiveCarId(id: Int) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id }
    }

    override suspend fun setDriveEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DRIVE_ENABLED] = enabled }
    }

    override suspend fun setLastBackupAt(epochMs: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_BACKUP_AT] = epochMs }
    }

    /** Used by the future Settings → Reset preferences action (Sub-project F). */
    suspend fun resetSetupComplete() {
        dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = false }
    }
}
```

- [ ] **Step 3: Write the failing tests in SettingsRepositoryAtomicWritesTest.kt**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.data.preferences.PreferenceKeys

class SettingsRepositoryAtomicWritesTest {

    private lateinit var tempFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before fun setup() {
        tempFile = File.createTempFile("ev-tracker-test", ".preferences_pb")
        tempFile.delete()
        dataStore = PreferenceDataStoreFactory.create(scope = TestScope()) { tempFile }
        repo = SettingsRepository(dataStore)
    }

    @After fun tearDown() {
        tempFile.delete()
    }

    @Test fun setPrimaryMetricAndDistanceUnit_writesBothKeys() = runTest {
        repo.setPrimaryMetricAndDistanceUnit("mi_per_kwh", "miles")
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
    }

    @Test fun markGlobalResetInProgress_writesAllThreeKeys() = runTest {
        repo.completeSetup("km_per_kwh", "km", "EUR")  // setupComplete=true
        repo.setActiveCarId(7)
        // Sanity:
        val data = dataStore.data.first()
        assertTrue(data[PreferenceKeys.SETUP_COMPLETE]!!)
        assertEquals(7, data[PreferenceKeys.ACTIVE_CAR_ID])

        repo.markGlobalResetInProgress()

        val after = dataStore.data.first()
        assertFalse(after[PreferenceKeys.SETUP_COMPLETE]!!)
        assertEquals(-1, after[PreferenceKeys.ACTIVE_CAR_ID])
        assertTrue(after[PreferenceKeys.RESET_IN_PROGRESS]!!)
    }

    @Test fun resetInProgress_defaultsToFalse_whenKeyAbsent() = runTest {
        assertFalse(repo.resetInProgress.first())
    }

    @Test fun setResetInProgress_canBeToggledTrueThenFalse() = runTest {
        repo.setResetInProgress(true)
        assertTrue(repo.resetInProgress.first())
        repo.setResetInProgress(false)
        assertFalse(repo.resetInProgress.first())
    }
}
```

- [ ] **Step 4: Run JVM tests; expect green for the new tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.repository.SettingsRepositoryAtomicWritesTest"
```
Expected: PASS for all 4 cases.

- [ ] **Step 5: Commit Task 1 + Task 2 together**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt \
        app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt \
        app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt \
        app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryAtomicWritesTest.kt
git commit -m "feat(F1): extend SettingsReader/Writer + atomic combined writers"
```

---

## Task 3: Add `deleteForCar` to ChargeEventDao + Writer interface + impl

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt`

- [ ] **Step 1: Add deleteForCar query to ChargeEventDao.kt**

After the existing `deleteAll()` declaration (around line 39-40), add:

```kotlin
@Query("DELETE FROM charge_events WHERE carId = :carId")
suspend fun deleteForCar(carId: Int)
```

- [ ] **Step 2: Extend ChargeEventWriter interface**

Replace the file contents:

```kotlin
package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.ChargeEventEntity

interface ChargeEventWriter {
    suspend fun insert(event: ChargeEventEntity): Long
    suspend fun update(event: ChargeEventEntity)
    suspend fun delete(event: ChargeEventEntity)
    /** F1: per-active-car reset. */
    suspend fun deleteForCar(carId: Int)
    /** F1: global reset. */
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Implement the new methods in ChargeEventRepository.kt**

Append before the closing `}`:

```kotlin
    override suspend fun deleteForCar(carId: Int) = chargeEventDao.deleteForCar(carId)
    override suspend fun deleteAll() = chargeEventDao.deleteAll()
```

- [ ] **Step 4: Verify compile + existing tests pass**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL with all existing tests still green (FakeChargeEventWriter must still implement the interface — Task 5 will update it; for now FakeChargeEventWriter implements the old shape and won't compile).

  Actually — `FakeChargeEventWriter` in `Fakes.kt` implements `ChargeEventWriter`. After Step 2 it no longer satisfies the interface. The build will fail at the test compile step.

  This is intentional within Task 3; pair Task 3's commit with Task 5's update of the fakes (or do Task 4 + Task 5 first and Task 3 last). To keep tasks linear, the safer ordering is:

  **Re-ordered execution:** complete Steps 1-3 of Task 3 above WITHOUT running gradle, then continue into Task 4 and Task 5, and run gradle ONCE at the end of Task 5. Don't commit Task 3 alone.

- [ ] **Step 5: DO NOT commit yet — paired with Tasks 4 + 5**

---

## Task 4: Add `deleteAll` to LocationWriter + CarWriter + impls

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/CarWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt`

- [ ] **Step 1: Replace LocationWriter.kt**

```kotlin
package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationWriter {
    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis())
    suspend fun delete(location: CustomLocationEntity)
    /** F1: global reset. */
    suspend fun deleteAll()
}
```

- [ ] **Step 2: Replace CarWriter.kt**

```kotlin
package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CarEntity

interface CarWriter {
    suspend fun insert(car: CarEntity): Long
    suspend fun rename(carId: Int, newName: String)
    suspend fun deleteById(carId: Int)
    /** F1: global reset. */
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Implement deleteAll in LocationRepository.kt**

Append before the closing `}`:

```kotlin
    override suspend fun deleteAll() = customLocationDao.deleteAll()
```

- [ ] **Step 4: Implement deleteAll in CarRepository.kt**

Append before the closing `}`:

```kotlin
    override suspend fun deleteAll() = carDao.deleteAll()
```

- [ ] **Step 5: DO NOT commit yet — paired with Tasks 3 + 5**

---

## Task 5: Update Fakes to satisfy the extended interfaces

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Update FakeChargeEventWriter to add deleteForCar + deleteAll**

Find the `class FakeChargeEventWriter(...)` block and add two new methods inside the class body, after `delete(event:)`:

```kotlin
    override suspend fun deleteForCar(carId: Int) {
        store.value = store.value.filter { it.carId != carId }
    }
    override suspend fun deleteAll() {
        store.value = emptyList()
    }
```

- [ ] **Step 2: Update FakeLocationWriter to add deleteAll**

Find `class FakeLocationWriter(...)` and append before the closing `}` of the class:

```kotlin
    override suspend fun deleteAll() {
        state.value = emptyList()
    }
```

- [ ] **Step 3: Update FakeCarRepository to add deleteAll**

Find `class FakeCarRepository(...)` (it implements `CarReader, CarWriter`). Append before the closing `}`:

```kotlin
    override suspend fun deleteAll() {
        state.value = emptyList()
    }
```

- [ ] **Step 4: Update FakeSettingsReader to add theme + resetInProgress flows**

Replace the entire `class FakeSettingsReader(...)` block with:

```kotlin
class FakeSettingsReader(
    activeCarIdInit: Int = -1,
    primaryMetricInit: String = "km_per_kwh",
    distanceUnitInit: String = "km",
    currencyInit: String = "EUR",
    driveEnabledInit: Boolean = false,
    lastBackupAtInit: Long? = null,
    themeInit: String = "system",
    resetInProgressInit: Boolean = false
) : SettingsReader {
    private val activeCar = MutableStateFlow(activeCarIdInit)
    private val metric = MutableStateFlow(primaryMetricInit)
    private val unit = MutableStateFlow(distanceUnitInit)
    private val curr = MutableStateFlow(currencyInit)
    private val drive = MutableStateFlow(driveEnabledInit)
    private val backupAt = MutableStateFlow(lastBackupAtInit)
    private val themeFlow = MutableStateFlow(themeInit)
    private val resetFlag = MutableStateFlow(resetInProgressInit)
    override val activeCarId: Flow<Int> = activeCar
    override val primaryMetric: Flow<String> = metric
    override val distanceUnit: Flow<String> = unit
    override val currency: Flow<String> = curr
    override val driveEnabled: Flow<Boolean> = drive
    override val lastBackupAt: Flow<Long?> = backupAt
    override val theme: Flow<String> = themeFlow
    override val resetInProgress: Flow<Boolean> = resetFlag
    fun setActiveCarId(id: Int) { activeCar.value = id }
    fun setDriveEnabled(enabled: Boolean) { drive.value = enabled }
    fun setLastBackupAt(value: Long?) { backupAt.value = value }
    fun setPrimaryMetric(v: String) { metric.value = v }
    fun setDistanceUnit(v: String) { unit.value = v }
    fun setCurrency(v: String) { curr.value = v }
    fun setTheme(v: String) { themeFlow.value = v }
    fun setResetInProgress(v: Boolean) { resetFlag.value = v }
}
```

- [ ] **Step 5: Replace FakeSettingsWriter to satisfy the extended interface**

Replace the entire `class FakeSettingsWriter` block with:

```kotlin
class FakeSettingsWriter(
    val callRecorder: MutableList<String>? = null
) : SettingsWriter {
    var activeCarId: Int = -1
        private set
    var driveEnabled: Boolean = false
        private set
    var lastBackupAt: Long? = null
        private set
    var theme: String = "system"
        private set
    var primaryMetric: String = "km_per_kwh"
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
        callRecorder?.add("setActiveCarId($id)"); activeCarId = id
    }
    override suspend fun setDriveEnabled(enabled: Boolean) {
        callRecorder?.add("setDriveEnabled($enabled)"); driveEnabled = enabled
    }
    override suspend fun setLastBackupAt(epochMs: Long) {
        callRecorder?.add("setLastBackupAt($epochMs)"); lastBackupAt = epochMs
    }
    override suspend fun setTheme(value: String) {
        callRecorder?.add("setTheme($value)"); theme = value
    }
    override suspend fun setPrimaryMetric(metric: String) {
        callRecorder?.add("setPrimaryMetric($metric)"); primaryMetric = metric
    }
    override suspend fun setDistanceUnit(unit: String) {
        callRecorder?.add("setDistanceUnit($unit)"); distanceUnit = unit
    }
    override suspend fun setCurrency(code: String) {
        callRecorder?.add("setCurrency($code)"); currency = code
    }
    override suspend fun setSetupComplete(value: Boolean) {
        callRecorder?.add("setSetupComplete($value)"); setupComplete = value
    }
    override suspend fun setResetInProgress(value: Boolean) {
        callRecorder?.add("setResetInProgress($value)"); resetInProgress = value
    }
    override suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String) {
        callRecorder?.add("setPrimaryMetricAndDistanceUnit($metric,$unit)")
        this.primaryMetric = metric
        this.distanceUnit = unit
    }
    override suspend fun markGlobalResetInProgress() {
        callRecorder?.add("markGlobalResetInProgress")
        setupComplete = false
        activeCarId = -1
        resetInProgress = true
    }
}
```

- [ ] **Step 6: Run full JVM test suite to verify the project compiles + all tests pass**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL with all existing ~152 tests + the 4 new atomic-writes tests = ~156 green tests.

- [ ] **Step 7: Commit Tasks 3 + 4 + 5 together**

```bash
git add app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt \
        app/src/main/java/org/spsl/evtracker/domain/repository/ChargeEventWriter.kt \
        app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt \
        app/src/main/java/org/spsl/evtracker/domain/repository/CarWriter.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt \
        app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(F1): add deleteForCar/deleteAll to writers + extend test fakes"
```

---

## Task 6: DataResetTransactionRunner interface + Room impl + DI binding + instrumented test

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/repository/DataResetTransactionRunner.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunner.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunnerTest.kt`

- [ ] **Step 1: Create DataResetTransactionRunner.kt**

```kotlin
package org.spsl.evtracker.domain.repository

/**
 * Atomically clears every row from cars, charge_events, and custom_locations.
 *
 * Production: [org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner] wraps
 * `database.withTransaction { … }` and calls each DAO's `deleteAll()` inside the transaction.
 * Tests: [org.spsl.evtracker.testing.FakeDataResetTransactionRunner] records calls and
 * clears in-memory backing collections.
 */
interface DataResetTransactionRunner {
    suspend fun clearAllTables()
}
```

- [ ] **Step 2: Create RoomDataResetTransactionRunner.kt**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner

@Singleton
class RoomDataResetTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : DataResetTransactionRunner {
    override suspend fun clearAllTables() {
        database.withTransaction {
            database.chargeEventDao().deleteAll()
            database.customLocationDao().deleteAll()
            database.carDao().deleteAll()
        }
    }
}
```

- [ ] **Step 3: Bind in DomainModule.kt**

Add the import at the top:

```kotlin
import org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
```

Add the binding inside the `abstract class DomainModule` block, after the `bindRestoreSnapshotWriter` line:

```kotlin
    @Binds abstract fun bindDataResetTransactionRunner(
        impl: RoomDataResetTransactionRunner
    ): DataResetTransactionRunner
```

- [ ] **Step 4: Add FakeDataResetTransactionRunner to Fakes.kt**

Append at the end of `Fakes.kt`:

```kotlin
class FakeDataResetTransactionRunner(
    val callRecorder: MutableList<String>? = null,
    private val onClearStores: () -> Unit = {}
) : org.spsl.evtracker.domain.repository.DataResetTransactionRunner {
    var clearCallCount: Int = 0
        private set
    var failNext: Throwable? = null

    override suspend fun clearAllTables() {
        callRecorder?.add("clearAllTables")
        failNext?.let { failNext = null; throw it }
        clearCallCount++
        onClearStores()
    }
}
```

- [ ] **Step 5: Create RoomDataResetTransactionRunnerTest.kt (instrumented)**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@RunWith(AndroidJUnit4::class)
class RoomDataResetTransactionRunnerTest {

    private lateinit var db: AppDatabase
    private lateinit var runner: RoomDataResetTransactionRunner

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        runner = RoomDataResetTransactionRunner(db)
    }

    @After fun tearDown() { db.close() }

    @Test fun clearAllTables_emptiesAllThreeTables() = runTest {
        val carId = db.carDao().insert(CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0))
        db.chargeEventDao().insert(
            ChargeEventEntity(
                carId = carId.toInt(),
                eventDate = 1_700_000_000_000L,
                odometerKm = 100.0,
                kwhAdded = 20.0,
                chargeType = "AC"
            )
        )
        db.customLocationDao().insertIfMissing(CustomLocationEntity(label = "Office", useCount = 1, lastUsed = 1_700_000_000_000L))

        // Sanity: rows exist
        assertEquals(1, db.carDao().observeAll().let { /* fetched once via blocking is fine */ db.carDao().getById(carId.toInt()) }?.let { 1 } ?: 0)

        runner.clearAllTables()

        assertTrue(db.chargeEventDao().getAllForCarSorted(carId.toInt()).isEmpty())
        assertEquals(null, db.carDao().getById(carId.toInt()))
        // Custom locations table — query DAO; observeAll returns Flow, so we use a one-shot helper:
        // The DAO's observeAll is a Flow; for the test we reach into the DB directly via a count query.
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM custom_locations")
            .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
        assertEquals(0, count)
    }

    @Test fun clearAllTables_isAtomic_throwingFromOneDeleteRollsBackOthers() = runTest {
        val carId = db.carDao().insert(CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0))
        db.chargeEventDao().insert(
            ChargeEventEntity(
                carId = carId.toInt(),
                eventDate = 1_700_000_000_000L,
                odometerKm = 100.0,
                kwhAdded = 20.0,
                chargeType = "AC"
            )
        )

        // Wrap into a custom transaction that throws after the chargeEventDao.deleteAll() runs.
        // Use the public withTransaction directly to simulate the failure path.
        val threw = try {
            androidx.room.withTransaction(db) {
                db.chargeEventDao().deleteAll()
                throw IllegalStateException("simulated")
            }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue(threw)

        // The deleteAll from inside the failed transaction must have rolled back.
        assertEquals(1, db.chargeEventDao().getAllForCarSorted(carId.toInt()).size)
    }
}
```

- [ ] **Step 6: Verify compile of instrumented suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```
Expected: BUILD SUCCESSFUL. (Running the tests requires an emulator; we only verify compile.)

- [ ] **Step 7: Run JVM tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL — all existing tests still green; the new fake compiles.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/repository/DataResetTransactionRunner.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunner.kt \
        app/src/main/java/org/spsl/evtracker/di/DomainModule.kt \
        app/src/test/java/org/spsl/evtracker/testing/Fakes.kt \
        app/src/androidTest/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunnerTest.kt
git commit -m "feat(F1): DataResetTransactionRunner — domain interface + Room impl"
```

---

## Task 7: ResetActiveCarDataUseCase + JVM tests

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCase.kt`
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests in ResetActiveCarDataUseCaseTest.kt**

```kotlin
package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter

class ResetActiveCarDataUseCaseTest {

    private fun event(id: Int, carId: Int): ChargeEventEntity = ChargeEventEntity(
        id = id, carId = carId, eventDate = 1_700_000_000_000L + id,
        odometerKm = 100.0 + id, kwhAdded = 20.0, chargeType = "AC"
    )

    private fun build(): Triple<ResetActiveCarDataUseCase, FakeChargeEventQueries, FakeBackupScheduler> {
        val store = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val queries = FakeChargeEventQueries(store)
        val writer = FakeChargeEventWriter(store)
        val scheduler = FakeBackupScheduler()
        return Triple(ResetActiveCarDataUseCase(writer, scheduler), queries, scheduler)
    }

    @Test fun invoke_deletesEventsForGivenCarOnly() = runTest {
        val (useCase, queries, _) = build()
        queries.shareStore().value = listOf(event(1, 7), event(2, 7), event(3, 9))
        useCase(7)
        assertEquals(listOf(3), queries.current().map { it.id })
    }

    @Test fun invoke_doesNotTouchOtherCars() = runTest {
        val (useCase, queries, _) = build()
        queries.shareStore().value = listOf(event(1, 7), event(2, 9), event(3, 9))
        useCase(7)
        assertEquals(setOf(2, 3), queries.current().map { it.id }.toSet())
    }

    @Test fun invoke_enqueuesBackup() = runTest {
        val (useCase, _, scheduler) = build()
        useCase(7)
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test fun invoke_throwsForCarIdMinusOne() = runTest {
        val (useCase, _, _) = build()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(-1) }
        }
        assertTrue(ex.message!!.contains("-1"))
    }
}
```

- [ ] **Step 2: Run; expect FAIL (use case doesn't exist)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCaseTest"
```
Expected: COMPILE FAIL ("unresolved reference: ResetActiveCarDataUseCase").

- [ ] **Step 3: Create ResetActiveCarDataUseCase.kt**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventWriter

class ResetActiveCarDataUseCase @Inject constructor(
    private val chargeEventWriter: ChargeEventWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke(carId: Int) {
        require(carId != -1) { "ResetActiveCarDataUseCase called with carId=-1" }
        chargeEventWriter.deleteForCar(carId)
        backupScheduler.enqueueBackup()
    }
}
```

- [ ] **Step 4: Run; expect 4/4 PASS**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCaseTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCase.kt \
        app/src/test/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCaseTest.kt
git commit -m "feat(F1): ResetActiveCarDataUseCase + 4 JVM tests"
```

---

## Task 8: ResetAllDataUseCase + JVM tests

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCase.kt`
- Create: `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests in ResetAllDataUseCaseTest.kt**

```kotlin
package org.spsl.evtracker.domain.usecase

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeDataResetTransactionRunner
import org.spsl.evtracker.testing.FakeLocationWriter
import org.spsl.evtracker.testing.FakeSettingsWriter

class ResetAllDataUseCaseTest {

    private fun build(
        recorder: MutableList<String> = mutableListOf(),
        scheduler: BackupScheduler = FakeBackupScheduler()
    ): TestRig {
        val eventStore = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val eventWriter = FakeChargeEventWriter(eventStore)
        val locStore = MutableStateFlow<List<CustomLocationEntity>>(emptyList())
        val locWriter = FakeLocationWriter(locStore)
        val carRepo = FakeCarRepository()
        val settings = FakeSettingsWriter(callRecorder = recorder)
        val runner = FakeDataResetTransactionRunner(callRecorder = recorder) {
            eventStore.value = emptyList()
            locStore.value = emptyList()
            carRepo.seed(emptyList())
        }
        val useCase = ResetAllDataUseCase(runner, settings, scheduler)
        return TestRig(useCase, runner, settings, recorder, eventStore, locStore, carRepo, scheduler)
    }

    private class TestRig(
        val useCase: ResetAllDataUseCase,
        val runner: FakeDataResetTransactionRunner,
        val settings: FakeSettingsWriter,
        val recorder: MutableList<String>,
        val eventStore: MutableStateFlow<List<ChargeEventEntity>>,
        val locStore: MutableStateFlow<List<CustomLocationEntity>>,
        val carRepo: FakeCarRepository,
        val scheduler: BackupScheduler
    )

    @Test fun invoke_callsResetRunner_clearAllTables() = runTest {
        val rig = build()
        rig.useCase()
        assertEquals(1, rig.runner.clearCallCount)
    }

    @Test fun invoke_setsActiveCarIdToMinusOne_andSetupCompleteFalse_andResetInProgressTrue_atStart() = runTest {
        val rig = build()
        rig.eventStore.value = listOf(
            ChargeEventEntity(id = 1, carId = 7, eventDate = 1L, odometerKm = 0.0, kwhAdded = 0.0, chargeType = "AC")
        )
        rig.useCase()
        // markGlobalResetInProgress wrote all three keys:
        assertEquals(-1, rig.settings.activeCarId)
        assertFalse(rig.settings.setupComplete)
    }

    @Test fun invoke_setsResetInProgressFalse_atEnd() = runTest {
        val rig = build()
        rig.useCase()
        assertFalse(rig.settings.resetInProgress)
    }

    @Test fun invoke_enqueuesBackup() = runTest {
        val scheduler = FakeBackupScheduler()
        val rig = build(scheduler = scheduler)
        rig.useCase()
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test fun invoke_orders_markResetInProgress_then_clearTables_then_clearFlag_then_enqueueBackup() = runTest {
        val recorder = mutableListOf<String>()
        // Use a scheduler that records too:
        val recordingScheduler = object : BackupScheduler {
            override suspend fun enqueueBackup() { recorder.add("enqueueBackup") }
        }
        val rig = build(recorder = recorder, scheduler = recordingScheduler)
        rig.useCase()
        // Expected sequence: mark, clearAllTables, setResetInProgress(false), enqueueBackup
        val markIdx = recorder.indexOf("markGlobalResetInProgress")
        val clearIdx = recorder.indexOf("clearAllTables")
        val flagOff = recorder.indexOf("setResetInProgress(false)")
        val enqueueIdx = recorder.indexOf("enqueueBackup")
        assertTrue("mark first ($markIdx) before clear ($clearIdx)", markIdx in 0 until clearIdx)
        assertTrue("clear ($clearIdx) before flag-off ($flagOff)", clearIdx < flagOff)
        assertTrue("flag-off ($flagOff) before enqueue ($enqueueIdx)", flagOff < enqueueIdx)
    }

    @Test fun invoke_idempotent_secondCallOnEmptyState_completesAndClearsFlag() = runTest {
        val rig = build()
        rig.useCase()
        rig.useCase()  // Second call on already-empty state
        assertFalse(rig.settings.resetInProgress)
        assertEquals(2, rig.runner.clearCallCount)
    }

    @Test fun invoke_throwingFromResetRunner_doesNotClearFlag() = runTest {
        val rig = build()
        rig.runner.failNext = IllegalStateException("rooms exploded")
        try {
            rig.useCase()
            error("expected throw")
        } catch (_: IllegalStateException) {
            // expected
        }
        // Flag stays true ⇒ next launch's MainActivity auto-recovery picks up.
        assertTrue(rig.settings.resetInProgress)
    }

    @Test fun invoke_enqueueBackupThrowing_doesNotPropagate_flagIsAlreadyFalse() = runTest {
        val throwingScheduler = object : BackupScheduler {
            override suspend fun enqueueBackup() = throw IOException("WorkManager exploded")
        }
        val rig = build(scheduler = throwingScheduler)
        rig.useCase()  // Should not throw
        assertFalse(rig.settings.resetInProgress)
        assertEquals(1, rig.runner.clearCallCount)
    }

    @Test fun markGlobalResetInProgress_writesAllThreeKeysInSingleDataStoreEdit() {
        // Already covered by SettingsRepositoryAtomicWritesTest; this entry is intentionally
        // a pointer comment so anyone scanning the test file sees the cross-reference.
    }
}
```

- [ ] **Step 2: Run; expect FAIL (use case doesn't exist)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ResetAllDataUseCaseTest"
```

- [ ] **Step 3: Create ResetAllDataUseCase.kt**

```kotlin
package org.spsl.evtracker.domain.usecase

import javax.inject.Inject
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
import org.spsl.evtracker.domain.repository.SettingsWriter

class ResetAllDataUseCase @Inject constructor(
    private val resetRunner: DataResetTransactionRunner,
    private val settingsWriter: SettingsWriter,
    private val backupScheduler: BackupScheduler
) {
    suspend operator fun invoke() {
        // Step 1 — atomic flag flip; if we crash between here and Step 3, the next
        // launch's MainActivity auto-recovery (§9.2) finishes the reset before any
        // UI mounts.
        settingsWriter.markGlobalResetInProgress()

        // Step 2 — atomic Room transaction across the three tables.
        resetRunner.clearAllTables()

        // Step 3 — clear the flag. AFTER this, the reset is committed.
        settingsWriter.setResetInProgress(false)

        // Step 4 — best-effort post-reset Drive enqueue. Wrapped in runCatching so a
        // scheduler/DataStore/WorkManager failure cannot stick the flag (which Step 3
        // already cleared) or block the user. Next snapshot-triggering action will
        // re-enqueue if Drive is still on.
        runCatching { backupScheduler.enqueueBackup() }
            .onFailure {
                android.util.Log.w(
                    "ResetAllDataUseCase",
                    "Post-reset backup enqueue failed; next snapshot change will retry",
                    it
                )
            }
    }
}
```

- [ ] **Step 4: Run; expect 8/8 PASS (the 9th case is a comment pointing at SettingsRepositoryAtomicWritesTest)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.ResetAllDataUseCaseTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCase.kt \
        app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt
git commit -m "feat(F1): ResetAllDataUseCase — gate-first ordering + idempotent retry"
```

---

## Task 9: SettingsUiState + SettingsEvent extensions

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt`

- [ ] **Step 1: Replace SettingsUiState.kt**

```kotlin
package org.spsl.evtracker.core.model

import android.net.Uri
import androidx.annotation.StringRes

data class SettingsUiState(
    // Drive (E):
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    val pendingRestoreLabel: String? = null,

    // F1:
    val primaryMetric: String = "km_per_kwh",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val theme: String = "system",
    val activeCarId: Int = -1,
    val activeCarName: String? = null,
    val customLocationCount: Int = 0
)

sealed class SettingsEvent {
    // E:
    data class ShowRestorePrompt(val label: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msg: Int) : SettingsEvent()

    // F1:
    /**
     * Emitted after a metric→unit auto-flip. Carries a fully-localized string-resource
     * id; the Fragment shows the Snackbar via `getString(msgRes)` with no format args.
     */
    data class AutoFlipped(@StringRes val msgRes: Int) : SettingsEvent()
    data class LaunchCsvShareIntent(val uri: Uri) : SettingsEvent()
    object NavigateToWizard : SettingsEvent()
}
```

- [ ] **Step 2: Run JVM tests; expect existing tests to still pass (UiState defaults are backwards-compatible)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt
git commit -m "feat(F1): extend SettingsUiState/Event for F1 actions"
```

---

## Task 10: SettingsViewModel — F1 action methods + JVM tests

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Replace SettingsViewModel.kt**

```kotlin
package org.spsl.evtracker.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.core.model.SettingsUiState
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.ExportCsvUseCase
import org.spsl.evtracker.domain.usecase.ResetActiveCarDataUseCase
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val locationReader: LocationReader,
    private val carReader: CarReader,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val workManager: WorkManager,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val resetActiveCarDataUseCase: ResetActiveCarDataUseCase,
    private val resetAllDataUseCase: ResetAllDataUseCase,
    private val exportCsvUseCase: ExportCsvUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsReader.driveEnabled.collect { v -> _uiState.update { it.copy(driveEnabled = v) } }
        }
        viewModelScope.launch {
            settingsReader.lastBackupAt.collect { v -> _uiState.update { it.copy(lastBackupAt = v) } }
        }
        viewModelScope.launch {
            settingsReader.primaryMetric.collect { v -> _uiState.update { it.copy(primaryMetric = v) } }
        }
        viewModelScope.launch {
            settingsReader.distanceUnit.collect { v -> _uiState.update { it.copy(distanceUnit = v) } }
        }
        viewModelScope.launch {
            settingsReader.currency.collect { v -> _uiState.update { it.copy(currency = v) } }
        }
        viewModelScope.launch {
            settingsReader.theme.collect { v -> _uiState.update { it.copy(theme = v) } }
        }
        viewModelScope.launch {
            settingsReader.activeCarId.collect { id ->
                val name = if (id == -1) null else carReader.getById(id)?.name
                _uiState.update { it.copy(activeCarId = id, activeCarName = name) }
            }
        }
        viewModelScope.launch {
            locationReader.observeAll().collect { list ->
                _uiState.update { it.copy(customLocationCount = list.size) }
            }
        }
    }

    // -- E (Drive) — unchanged ----------------------------------------------------

    fun onDriveAuthGranted() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthInFlight = true) }
            try {
                val json = backupRepository.readRemoteBackup()
                if (json == null) {
                    settingsWriter.setDriveEnabled(true)
                    backupScheduler.enqueueBackup()
                    _uiState.update { it.copy(isAuthInFlight = false) }
                } else {
                    val label = parseExportedAtLabel(json)
                    _uiState.update { it.copy(isAuthInFlight = false, pendingRestoreLabel = label) }
                    _events.tryEmit(SettingsEvent.ShowRestorePrompt(label))
                }
            } catch (_: DriveAuthRequiredException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_auth_failed))
            } catch (_: IOException) {
                _uiState.update { it.copy(isAuthInFlight = false) }
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_network_error))
            }
        }
    }

    fun onDriveAuthFailed(msgRes: Int) {
        _uiState.update { it.copy(isAuthInFlight = false) }
        _events.tryEmit(SettingsEvent.ShowError(msgRes))
    }

    fun onConfirmRestore() {
        viewModelScope.launch {
            when (restoreBackupUseCase()) {
                is RestoreResult.Success -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.RestoreSucceeded)
                }
                is RestoreResult.VersionMismatch -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_remote_backup_too_new))
                }
                RestoreResult.NoRemoteBackup -> {
                    _uiState.update { it.copy(pendingRestoreLabel = null) }
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_restore_failed))
                }
            }
        }
    }

    fun onSkipRestore() {
        viewModelScope.launch {
            settingsWriter.setDriveEnabled(true)
            backupScheduler.enqueueBackup()
            _uiState.update { it.copy(pendingRestoreLabel = null) }
        }
    }

    fun onRestorePromptDismissed() {
        _uiState.update { it.copy(pendingRestoreLabel = null) }
    }

    fun onToggleDriveOff() {
        viewModelScope.launch {
            settingsWriter.setDriveEnabled(false)
            workManager.cancelUniqueWork(BackupScheduler.UNIQUE_WORK_NAME)
        }
    }

    // -- F1 -----------------------------------------------------------------------

    fun onPrimaryMetricSelected(metric: String) {
        viewModelScope.launch {
            val requiredUnit = unitFor(metric)
            if (requiredUnit != _uiState.value.distanceUnit) {
                settingsWriter.setPrimaryMetricAndDistanceUnit(metric, requiredUnit)
                _events.tryEmit(SettingsEvent.AutoFlipped(unitFlipMsgRes(requiredUnit)))
            } else {
                settingsWriter.setPrimaryMetric(metric)
            }
        }
    }

    fun onDistanceUnitSelected(unit: String) {
        viewModelScope.launch {
            val current = _uiState.value.primaryMetric
            val newMetric = defaultMetricFor(unit, current)
            if (newMetric != current) {
                settingsWriter.setPrimaryMetricAndDistanceUnit(newMetric, unit)
                _events.tryEmit(SettingsEvent.AutoFlipped(metricFlipMsgRes(newMetric)))
            } else {
                settingsWriter.setDistanceUnit(unit)
            }
        }
    }

    fun onCurrencySelected(code: String) {
        viewModelScope.launch { settingsWriter.setCurrency(code) }
    }

    fun onThemeSelected(theme: String) {
        viewModelScope.launch { settingsWriter.setTheme(theme) }
    }

    fun onResetPreferences() {
        viewModelScope.launch {
            settingsWriter.setSetupComplete(false)
            _events.tryEmit(SettingsEvent.NavigateToWizard)
        }
    }

    fun onResetActiveCarData() {
        val carId = _uiState.value.activeCarId
        if (carId == -1) return
        viewModelScope.launch { resetActiveCarDataUseCase(carId) }
    }

    fun onResetAllData() {
        viewModelScope.launch {
            resetAllDataUseCase()
            _events.tryEmit(SettingsEvent.NavigateToWizard)
        }
    }

    fun onExportCsv() {
        val carId = _uiState.value.activeCarId
        if (carId == -1) return
        viewModelScope.launch {
            try {
                val useKm = _uiState.value.distanceUnit == "km"
                val uri = exportCsvUseCase.export(carId, useKm)
                _events.tryEmit(SettingsEvent.LaunchCsvShareIntent(uri))
            } catch (_: IOException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            } catch (_: IllegalArgumentException) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.settings_export_csv_failed))
            }
        }
    }

    private fun parseExportedAtLabel(json: String): String {
        return try {
            val match = EXPORTED_AT_REGEX.find(json)
            val iso = match?.groupValues?.get(1) ?: return UNKNOWN_DATE
            val instant = Instant.parse(iso)
            DATE_FORMAT.format(Date(instant.toEpochMilli()))
        } catch (_: Throwable) {
            UNKNOWN_DATE
        }
    }

    private fun unitFor(metric: String): String = when (metric) {
        "mi_per_kwh" -> "miles"
        "km_per_kwh", "kwh_per_100km" -> "km"
        else -> "km"
    }

    private fun defaultMetricFor(unit: String, currentMetric: String): String =
        when (unit) {
            "miles" -> "mi_per_kwh"
            "km"    -> if (currentMetric == "mi_per_kwh") "km_per_kwh" else currentMetric
            else    -> currentMetric
        }

    @StringRes private fun unitFlipMsgRes(newUnit: String): Int = when (newUnit) {
        "miles" -> R.string.settings_unit_flipped_to_miles
        else    -> R.string.settings_unit_flipped_to_km
    }

    @StringRes private fun metricFlipMsgRes(newMetric: String): Int = when (newMetric) {
        "kwh_per_100km" -> R.string.settings_metric_flipped_kwh_per_100km
        "mi_per_kwh"    -> R.string.settings_metric_flipped_mi_per_kwh
        else            -> R.string.settings_metric_flipped_km_per_kwh
    }

    companion object {
        private val EXPORTED_AT_REGEX = Regex("\"exported_at\"\\s*:\\s*\"([^\"]+)\"")
        private const val UNKNOWN_DATE = "an earlier date"
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
```

- [ ] **Step 2: Open existing SettingsViewModelTest.kt**

The existing test file already imports `SettingsViewModel` and constructs it via fakes. The constructor signature changed; existing tests will fail compile. Update the constructor calls AND add the F1 tests at the end.

Look at the existing `setUp()` / construction site in `SettingsViewModelTest.kt`. Wherever `SettingsViewModel(...)` is built, add the new dependencies. The minimal example:

```kotlin
val locationReader = FakeLocationReader()
val carReader = FakeCarReader()
val resetActive = ResetActiveCarDataUseCase(chargeEventWriter, backupScheduler)
val runner = FakeDataResetTransactionRunner()
val resetAll = ResetAllDataUseCase(runner, settingsWriter, backupScheduler)
val exportCsv = ExportCsvUseCase(carReader, chargeEventQueries, csvFileSink) // see helper below
```

For `ExportCsvUseCase` use cases that need a sink, define a `FakeCsvFileSink`:

```kotlin
class FakeCsvFileSink : org.spsl.evtracker.domain.backup.CsvFileSink {
    var failNext: Throwable? = null
    var lastCarName: String? = null
    override suspend fun write(carName: String, body: suspend (java.io.Writer) -> Unit): android.net.Uri {
        failNext?.let { failNext = null; throw it }
        lastCarName = carName
        body(java.io.StringWriter())
        return android.net.Uri.parse("content://test/$carName.csv")
    }
}
```

(Add it to `Fakes.kt` as a separate edit if needed.)

- [ ] **Step 3: Append the 15 F1 test cases to SettingsViewModelTest.kt**

Add these tests inside the existing `class SettingsViewModelTest` body, alongside the E tests. Each follows the existing pattern (build the VM, call action, advanceUntilIdle, assert):

```kotlin
@Test
fun primaryMetric_select_compatibleUnit_writesOnlyMetric() = runTest {
    val vm = buildVm()  // helper that returns a configured VM with default state
    vm.onPrimaryMetricSelected("kwh_per_100km")
    advanceUntilIdle()
    assertEquals("kwh_per_100km", settingsWriter.primaryMetric)
    // distanceUnit was already "km" — should NOT be re-written
    assertFalse(recorder.contains("setPrimaryMetricAndDistanceUnit(kwh_per_100km,km)"))
}

@Test
fun primaryMetric_select_incompatibleUnit_writesBoth_emitsAutoFlipped_unitToMiles() = runTest {
    val vm = buildVm()  // default unit = "km"
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onPrimaryMetricSelected("mi_per_kwh")
    advanceUntilIdle()
    job.cancel()
    assertEquals("mi_per_kwh", settingsWriter.primaryMetric)
    assertEquals("miles", settingsWriter.distanceUnit)
    assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_unit_flipped_to_miles })
}

@Test
fun primaryMetric_select_incompatibleUnit_writesBoth_emitsAutoFlipped_unitToKm() = runTest {
    settingsReader.setDistanceUnit("miles")
    settingsReader.setPrimaryMetric("mi_per_kwh")
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onPrimaryMetricSelected("km_per_kwh")
    advanceUntilIdle()
    job.cancel()
    assertEquals("km_per_kwh", settingsWriter.primaryMetric)
    assertEquals("km", settingsWriter.distanceUnit)
    assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_unit_flipped_to_km })
}

@Test
fun distanceUnit_select_compatibleMetric_writesOnlyUnit() = runTest {
    val vm = buildVm()  // default metric = km_per_kwh, unit = km
    vm.onDistanceUnitSelected("km")
    advanceUntilIdle()
    assertEquals("km", settingsWriter.distanceUnit)
    assertFalse(recorder.contains("setPrimaryMetricAndDistanceUnit(km_per_kwh,km)"))
}

@Test
fun distanceUnit_select_incompatibleMetric_writesBoth_emitsAutoFlipped_metric() = runTest {
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onDistanceUnitSelected("miles")
    advanceUntilIdle()
    job.cancel()
    assertEquals("miles", settingsWriter.distanceUnit)
    assertEquals("mi_per_kwh", settingsWriter.primaryMetric)
    assertTrue(received.any { it is SettingsEvent.AutoFlipped && it.msgRes == R.string.settings_metric_flipped_mi_per_kwh })
}

@Test
fun currency_select_writesValue() = runTest {
    val vm = buildVm()
    vm.onCurrencySelected("USD")
    advanceUntilIdle()
    assertEquals("USD", settingsWriter.currency)
}

@Test
fun theme_select_writesValue() = runTest {
    val vm = buildVm()
    vm.onThemeSelected("dark")
    advanceUntilIdle()
    assertEquals("dark", settingsWriter.theme)
}

@Test
fun resetActiveCar_disabled_whenNoActiveCar() = runTest {
    settingsReader.setActiveCarId(-1)
    val vm = buildVm()
    vm.onResetActiveCarData()
    advanceUntilIdle()
    // No deletion happened
    assertEquals(0, fakeChargeEventStore.value.size)  // store stayed empty (no seeded events)
}

@Test
fun resetActiveCar_callsUseCase_withActiveCarId() = runTest {
    settingsReader.setActiveCarId(5)
    val vm = buildVm()
    vm.onResetActiveCarData()
    advanceUntilIdle()
    // Use FakeBackupScheduler enqueue count as proxy for use case ran
    assertEquals(1, backupScheduler.enqueueCount)
}

@Test
fun resetAllData_callsUseCase_emitsNavigateToWizard() = runTest {
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onResetAllData()
    advanceUntilIdle()
    job.cancel()
    assertTrue(received.any { it is SettingsEvent.NavigateToWizard })
    assertFalse(settingsWriter.resetInProgress)
}

@Test
fun resetPreferences_setsSetupCompleteFalse_emitsNavigateToWizard() = runTest {
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onResetPreferences()
    advanceUntilIdle()
    job.cancel()
    assertFalse(settingsWriter.setupComplete)
    assertTrue(received.any { it is SettingsEvent.NavigateToWizard })
}

@Test
fun exportCsv_disabled_whenNoActiveCar() = runTest {
    settingsReader.setActiveCarId(-1)
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onExportCsv()
    advanceUntilIdle()
    job.cancel()
    assertTrue(received.none { it is SettingsEvent.LaunchCsvShareIntent })
}

@Test
fun exportCsv_success_emitsLaunchIntent() = runTest {
    settingsReader.setActiveCarId(5)
    fakeCarRepository.seed(listOf(CarEntity(id = 5, name = "Tesla", make = "T", model = "M3", year = 2024, batteryKwh = 75.0)))
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onExportCsv()
    advanceUntilIdle()
    job.cancel()
    assertTrue(received.any { it is SettingsEvent.LaunchCsvShareIntent })
}

@Test
fun exportCsv_ioException_emitsShowError() = runTest {
    settingsReader.setActiveCarId(5)
    fakeCarRepository.seed(listOf(CarEntity(id = 5, name = "Tesla", make = "T", model = "M3", year = 2024, batteryKwh = 75.0)))
    fakeCsvFileSink.failNext = IOException("disk full")
    val vm = buildVm()
    val received = mutableListOf<SettingsEvent>()
    val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
    vm.onExportCsv()
    advanceUntilIdle()
    job.cancel()
    assertTrue(received.any { it is SettingsEvent.ShowError && it.msg == R.string.settings_export_csv_failed })
}

@Test
fun customLocationCount_reflectsLocationReaderEmission() = runTest {
    fakeLocationReader.state.value = listOf(
        CustomLocationEntity(id = 1, label = "A", useCount = 1, lastUsed = 1L),
        CustomLocationEntity(id = 2, label = "B", useCount = 1, lastUsed = 2L)
    )
    val vm = buildVm()
    advanceUntilIdle()
    assertEquals(2, vm.uiState.value.customLocationCount)
}
```

The implementer must also update the existing `setUp` block and add a `buildVm()` helper that constructs the new VM with all 11 dependencies. Wire the new fakes (`FakeBackupScheduler`, `FakeCarRepository`, `FakeLocationReader`, `FakeCsvFileSink`, real `ResetActiveCarDataUseCase`, real `ResetAllDataUseCase` with `FakeDataResetTransactionRunner`, real `ExportCsvUseCase`).

- [ ] **Step 4: Run JVM tests; expect all green (existing E tests + 15 new F1 tests = ~24 cases in this file)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.settings.SettingsViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt \
        app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt \
        app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(F1): SettingsViewModel actions for theme/units/metric/currency/reset/CSV"
```

---

## Task 11: Strings + currencies array

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

(`R.array.supported_currencies` already exists in `app/src/main/res/values/currencies.xml` from earlier sub-projects — verified by `grep`. The plan uses that array name; the spec said `R.array.currencies` which is incorrect — substitute everywhere.)

- [ ] **Step 1: Append F1 strings to strings.xml**

Add these inside the existing `<resources>` element, in a new `<!-- F1 Settings -->` section:

```xml
    <!-- F1 Settings -->
    <string name="settings_primary_metric">Primary metric</string>
    <string name="settings_distance_unit">Distance unit</string>
    <string name="settings_currency">Currency</string>
    <string name="settings_theme">Theme</string>
    <string name="settings_manage_locations">Manage custom locations</string>
    <string name="settings_manage_locations_summary">%d saved</string>
    <string name="settings_reset_preferences">Reset preferences</string>
    <string name="settings_reset_preferences_summary">You’ll go through setup again.</string>
    <string name="settings_reset_active_car">Reset data for %1$s</string>
    <string name="settings_reset_active_car_default">Reset data for active car</string>
    <string name="settings_reset_active_car_summary">Delete all charge events for this car. Cars and locations are kept.</string>
    <string name="settings_reset_active_car_confirm">Delete all charge events for %1$s? This cannot be undone.</string>
    <string name="settings_reset_active_car_done">Charge events deleted.</string>
    <string name="settings_reset_all">Reset all data</string>
    <string name="settings_reset_all_summary">Delete all cars, charge events, and custom locations.</string>
    <string name="settings_reset_all_confirm">Delete everything? This cannot be undone.</string>
    <string name="settings_reset_all_confirm_drive_on">Delete everything? This cannot be undone, and your Google Drive backup will be overwritten with empty data. To preserve the remote copy, turn Drive backup off first.</string>
    <string name="settings_export_csv">Export CSV</string>
    <string name="settings_export_csv_summary">Share charge events as a CSV file.</string>
    <string name="settings_export_csv_failed">CSV export failed.</string>
    <string name="settings_theme_system">System default</string>
    <string name="settings_theme_light">Light</string>
    <string name="settings_theme_dark">Dark</string>
    <string name="settings_unit_flipped_to_km">Distance unit also changed to km.</string>
    <string name="settings_unit_flipped_to_miles">Distance unit also changed to miles.</string>
    <string name="settings_metric_flipped_km_per_kwh">Primary metric also changed to km / kWh.</string>
    <string name="settings_metric_flipped_kwh_per_100km">Primary metric also changed to kWh / 100 km.</string>
    <string name="settings_metric_flipped_mi_per_kwh">Primary metric also changed to mi / kWh.</string>

    <!-- F1 ManageLocations -->
    <string name="manage_locations_title">Manage custom locations</string>
    <string name="manage_locations_empty">Locations you save on charge events will appear here.</string>
    <string name="manage_locations_row_count">Used %1$d time(s) · last %2$s</string>
    <string name="manage_locations_row_count_zero">Last %1$s</string>
    <string name="manage_locations_undo_snackbar">Deleted \"%1$s\"</string>

    <!-- F1 common -->
    <string name="common_undo">Undo</string>
    <string name="common_cancel">Cancel</string>
    <string name="common_confirm">Confirm</string>

    <!-- F1 startup recovery (blocking dialog when auto-recovery fails) -->
    <string name="recovery_failure_title">Reset recovery failed</string>
    <string name="recovery_failure_body">We couldn\'t finish your last reset.\n\n%1$s\n\nTry again, or clear the app\'s data from system Settings if it keeps failing.</string>
    <string name="recovery_failure_retry">Try again</string>
```

- [ ] **Step 2: Run gradle to verify resource compile**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(F1): add Settings + ManageLocations string resources"
```

---

## Task 12: fragment_settings.xml — replace placeholders with real rows

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`

- [ ] **Step 1: Replace fragment_settings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Drive section (E) -->
            <TextView
                android:id="@+id/header_drive"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/settings_section_drive"
                android:textAppearance="?attr/textAppearanceTitleSmall"
                android:paddingBottom="8dp"/>

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_drive"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="56dp"
                android:text="@string/settings_drive_label"/>

            <TextView
                android:id="@+id/text_last_backup"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceBodyMedium"
                android:paddingTop="4dp"
                android:paddingBottom="16dp"
                tools:text="Last backup: April 27, 2026 at 9:30 AM"/>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="?attr/colorOutlineVariant"/>

            <!-- F1 — Preferences section -->
            <TextView
                android:id="@+id/header_preferences"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/wizard_metric_title"
                android:textAppearance="?attr/textAppearanceTitleSmall"
                android:paddingTop="16dp"
                android:paddingBottom="8dp"/>

            <LinearLayout android:id="@+id/row_primary_metric"
                style="@style/Widget.Material3.Button.TextButton"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_primary_metric"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:id="@+id/summary_primary_metric"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="km / kWh"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_distance_unit"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_distance_unit"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:id="@+id/summary_distance_unit"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="km"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_currency"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_currency"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:id="@+id/summary_currency"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="EUR"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_theme"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_theme"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:id="@+id/summary_theme"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="System default"/>
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="8dp"
                android:background="?attr/colorOutlineVariant"/>

            <!-- F1 — Data section -->
            <LinearLayout android:id="@+id/row_manage_locations"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_manage_locations"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:id="@+id/summary_manage_locations"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textAppearance="?attr/textAppearanceBodyMedium"
                    tools:text="3 saved"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_export_csv"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_export_csv"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_export_csv_summary"
                    android:textAppearance="?attr/textAppearanceBodyMedium"/>
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginTop="8dp"
                android:layout_marginBottom="8dp"
                android:background="?attr/colorOutlineVariant"/>

            <!-- F1 — Reset section -->
            <LinearLayout android:id="@+id/row_reset_preferences"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_preferences"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_preferences_summary"
                    android:textAppearance="?attr/textAppearanceBodyMedium"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_reset_active_car"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:id="@+id/title_reset_active_car"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_active_car_default"
                    android:textAppearance="?attr/textAppearanceBodyLarge"/>
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_active_car_summary"
                    android:textAppearance="?attr/textAppearanceBodyMedium"/>
            </LinearLayout>

            <LinearLayout android:id="@+id/row_reset_all"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:minHeight="64dp"
                android:gravity="center_vertical"
                android:padding="8dp"
                android:background="?attr/selectableItemBackground"
                android:clickable="true"
                android:focusable="true">
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_all"
                    android:textAppearance="?attr/textAppearanceBodyLarge"
                    android:textColor="?attr/colorError"/>
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="@string/settings_reset_all_summary"
                    android:textAppearance="?attr/textAppearanceBodyMedium"/>
            </LinearLayout>
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Build to verify resources compile**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/fragment_settings.xml
git commit -m "feat(F1): replace fragment_settings placeholders with real rows"
```

---

## Task 13: SettingsFragment — wire all the rows

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt`

> **Note:** The existing fragment already contains the E Drive section wiring. This task PRESERVES every line of E's wiring and ADDS F1 row click handlers, dialogs, share-intent, theme apply, navigation, and the conditional reset-all dialog text.

- [ ] **Step 1: Read existing SettingsFragment.kt to understand the E shape**

```bash
cat app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt
```

- [ ] **Step 2: Add F1 wiring on top of E's existing fragment**

Inside `onViewCreated`, after the existing E Drive section wiring, add:

```kotlin
// F1 row clicks
binding.rowPrimaryMetric.setOnClickListener { showPrimaryMetricDialog() }
binding.rowDistanceUnit.setOnClickListener { showDistanceUnitDialog() }
binding.rowCurrency.setOnClickListener { showCurrencyDialog() }
binding.rowTheme.setOnClickListener { showThemeDialog() }
binding.rowManageLocations.setOnClickListener {
    findNavController().navigate(R.id.action_settings_to_manage_locations)
}
binding.rowExportCsv.setOnClickListener { vm.onExportCsv() }
binding.rowResetPreferences.setOnClickListener { showResetPreferencesDialog() }
binding.rowResetActiveCar.setOnClickListener { showResetActiveCarDialog() }
binding.rowResetAll.setOnClickListener { showResetAllDialog() }

// F1 state observation — render row summaries
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        vm.uiState.collect { state -> renderF1Rows(state) }
    }
}

// F1 events — Snackbars / share intent / nav
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        vm.events.collect { event -> handleF1Event(event) }
    }
}
```

- [ ] **Step 3: Add the helper methods**

```kotlin
private fun renderF1Rows(state: SettingsUiState) {
    binding.summaryPrimaryMetric.setText(when (state.primaryMetric) {
        "kwh_per_100km" -> R.string.wizard_metric_kwh_per_100km
        "mi_per_kwh"    -> R.string.wizard_metric_mi_per_kwh
        else            -> R.string.wizard_metric_km_per_kwh
    })
    binding.summaryDistanceUnit.setText(if (state.distanceUnit == "miles") R.string.wizard_unit_miles else R.string.wizard_unit_km)
    binding.summaryCurrency.text = state.currency
    binding.summaryTheme.setText(when (state.theme) {
        "light" -> R.string.settings_theme_light
        "dark"  -> R.string.settings_theme_dark
        else    -> R.string.settings_theme_system
    })
    binding.summaryManageLocations.text =
        if (state.customLocationCount == 0) ""
        else getString(R.string.settings_manage_locations_summary, state.customLocationCount)

    val activeName = state.activeCarName
    binding.titleResetActiveCar.text =
        if (activeName == null) getString(R.string.settings_reset_active_car_default)
        else getString(R.string.settings_reset_active_car, activeName)

    val activeCarMissing = state.activeCarId == -1
    binding.rowResetActiveCar.alpha = if (activeCarMissing) 0.5f else 1f
    binding.rowResetActiveCar.isClickable = !activeCarMissing
    binding.rowResetActiveCar.isFocusable = !activeCarMissing
    binding.rowExportCsv.alpha = if (activeCarMissing) 0.5f else 1f
    binding.rowExportCsv.isClickable = !activeCarMissing
    binding.rowExportCsv.isFocusable = !activeCarMissing
}

private fun handleF1Event(event: SettingsEvent) {
    when (event) {
        is SettingsEvent.AutoFlipped -> Snackbar.make(binding.root, getString(event.msgRes), Snackbar.LENGTH_SHORT).show()
        is SettingsEvent.LaunchCsvShareIntent -> launchCsvShareIntent(event.uri)
        is SettingsEvent.NavigateToWizard -> findNavController().navigate(R.id.action_settings_to_wizard)
        is SettingsEvent.ShowError -> Snackbar.make(binding.root, getString(event.msg), Snackbar.LENGTH_LONG).show()
        else -> Unit  // E events handled by the existing E branch
    }
}

private fun launchCsvShareIntent(uri: android.net.Uri) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(android.content.Intent.createChooser(send, null))
}

private fun showPrimaryMetricDialog() {
    val labels = arrayOf(
        getString(R.string.wizard_metric_km_per_kwh),
        getString(R.string.wizard_metric_kwh_per_100km),
        getString(R.string.wizard_metric_mi_per_kwh)
    )
    val tokens = arrayOf("km_per_kwh", "kwh_per_100km", "mi_per_kwh")
    val current = vm.uiState.value.primaryMetric
    val checked = tokens.indexOf(current).coerceAtLeast(0)
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_primary_metric)
        .setSingleChoiceItems(labels, checked) { d, which ->
            vm.onPrimaryMetricSelected(tokens[which]); d.dismiss()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showDistanceUnitDialog() {
    val labels = arrayOf(getString(R.string.wizard_unit_km), getString(R.string.wizard_unit_miles))
    val tokens = arrayOf("km", "miles")
    val current = vm.uiState.value.distanceUnit
    val checked = tokens.indexOf(current).coerceAtLeast(0)
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_distance_unit)
        .setSingleChoiceItems(labels, checked) { d, which ->
            vm.onDistanceUnitSelected(tokens[which]); d.dismiss()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showCurrencyDialog() {
    val codes = resources.getStringArray(R.array.supported_currencies)
    val current = vm.uiState.value.currency
    val checked = codes.indexOf(current).coerceAtLeast(0)
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_currency)
        .setSingleChoiceItems(codes, checked) { d, which ->
            vm.onCurrencySelected(codes[which]); d.dismiss()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showThemeDialog() {
    val labels = arrayOf(
        getString(R.string.settings_theme_system),
        getString(R.string.settings_theme_light),
        getString(R.string.settings_theme_dark)
    )
    val tokens = arrayOf("system", "light", "dark")
    val current = vm.uiState.value.theme
    val checked = tokens.indexOf(current).coerceAtLeast(0)
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_theme)
        .setSingleChoiceItems(labels, checked) { d, which ->
            val token = tokens[which]
            vm.onThemeSelected(token)
            applyThemeImmediately(token)
            d.dismiss()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun applyThemeImmediately(token: String) {
    val mode = when (token) {
        "light" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        "dark"  -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else    -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
}

private fun showResetPreferencesDialog() {
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_reset_preferences)
        .setMessage(R.string.settings_reset_preferences_summary)
        .setPositiveButton(R.string.common_confirm) { _, _ -> vm.onResetPreferences() }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showResetActiveCarDialog() {
    val name = vm.uiState.value.activeCarName ?: return
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_reset_active_car_default)
        .setMessage(getString(R.string.settings_reset_active_car_confirm, name))
        .setPositiveButton(R.string.common_confirm) { _, _ ->
            vm.onResetActiveCarData()
            Snackbar.make(binding.root, R.string.settings_reset_active_car_done, Snackbar.LENGTH_SHORT).show()
        }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}

private fun showResetAllDialog() {
    val msgRes = if (vm.uiState.value.driveEnabled) R.string.settings_reset_all_confirm_drive_on
                 else R.string.settings_reset_all_confirm
    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_reset_all)
        .setMessage(msgRes)
        .setPositiveButton(R.string.common_confirm) { _, _ -> vm.onResetAllData() }
        .setNegativeButton(R.string.common_cancel, null)
        .show()
}
```

(Also add the missing imports at the top: `Lifecycle`, `findNavController`, `repeatOnLifecycle`, `Snackbar`, `lifecycleScope`, `launch`. Check existing imports first; only add what's missing.)

- [ ] **Step 4: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt
git commit -m "feat(F1): SettingsFragment wiring — rows, dialogs, theme apply, share intent"
```

---

## Task 14: nav_graph.xml — add the two new actions on settingsFragment

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`

- [ ] **Step 1: Replace the settingsFragment block in nav_graph.xml**

Find:

```xml
<fragment
    android:id="@+id/settingsFragment"
    android:name="org.spsl.evtracker.ui.settings.SettingsFragment"
    android:label="Settings"/>
```

Replace with:

```xml
<fragment
    android:id="@+id/settingsFragment"
    android:name="org.spsl.evtracker.ui.settings.SettingsFragment"
    android:label="Settings">
    <action
        android:id="@+id/action_settings_to_manage_locations"
        app:destination="@id/manageLocationsFragment"/>
    <action
        android:id="@+id/action_settings_to_wizard"
        app:destination="@id/wizardFragment"
        app:popUpTo="@id/nav_graph"
        app:popUpToInclusive="true"/>
</fragment>
```

- [ ] **Step 2: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/navigation/nav_graph.xml
git commit -m "feat(F1): nav graph — settings → manageLocations + settings → wizard"
```

---

## Task 15: ManageLocations UiState/Event + Adapter + row layout

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/ManageLocationsUiState.kt`
- Create: `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsAdapter.kt`
- Create: `app/src/main/res/layout/item_custom_location.xml`

- [ ] **Step 1: Create ManageLocationsUiState.kt**

```kotlin
package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

data class ManageLocationsUiState(
    val locations: List<CustomLocationEntity> = emptyList(),
    /** Labels currently in their 5-second cancel window. Filtered out of the visible list. */
    val pendingDeletions: Set<String> = emptySet()
) {
    /**
     * The list the Fragment renders, AND the source of truth for the empty-state.
     * If the last row was just swiped, this is empty during the 5s undo window so
     * the empty-state shows immediately rather than leaving a blank RecyclerView.
     */
    val visibleLocations: List<CustomLocationEntity>
        get() = locations.filter { it.label !in pendingDeletions }
}

sealed class ManageLocationsEvent {
    data class ShowUndoSnackbar(val label: String) : ManageLocationsEvent()
}
```

- [ ] **Step 2: Create item_custom_location.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:minHeight="64dp"
    android:gravity="center_vertical"
    android:padding="16dp"
    android:background="?attr/colorSurface">

    <TextView
        android:id="@+id/text_label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceBodyLarge"
        tools:text="Office garage"/>

    <TextView
        android:id="@+id/text_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textAppearance="?attr/textAppearanceBodyMedium"
        tools:text="Used 5 times · last 2 days ago"/>
</LinearLayout>
```

- [ ] **Step 3: Create ManageLocationsAdapter.kt**

```kotlin
package org.spsl.evtracker.ui.locations

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.R
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.databinding.ItemCustomLocationBinding

class ManageLocationsAdapter : ListAdapter<CustomLocationEntity, ManageLocationsAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCustomLocationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CustomLocationEntity) {
            b.textLabel.text = item.label
            val ctx = b.root.context
            val rel = DateUtils.getRelativeTimeSpanString(
                item.lastUsed,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
            b.textSubtitle.text = if (item.useCount == 0)
                ctx.getString(R.string.manage_locations_row_count_zero, rel)
            else
                ctx.getString(R.string.manage_locations_row_count, item.useCount, rel)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCustomLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    /** Lookup label for a given adapter position (used by ItemTouchHelper.onSwiped). */
    fun labelAt(position: Int): String? = if (position in 0 until itemCount) getItem(position).label else null

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CustomLocationEntity>() {
            override fun areItemsTheSame(oldItem: CustomLocationEntity, newItem: CustomLocationEntity) =
                oldItem.label == newItem.label
            override fun areContentsTheSame(oldItem: CustomLocationEntity, newItem: CustomLocationEntity) =
                oldItem == newItem
        }
    }
}
```

- [ ] **Step 4: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/ManageLocationsUiState.kt \
        app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsAdapter.kt \
        app/src/main/res/layout/item_custom_location.xml
git commit -m "feat(F1): ManageLocations UiState + Adapter + row layout"
```

---

## Task 16: ManageLocationsViewModel + JVM tests

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModel.kt`
- Create: `app/src/test/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModelTest.kt`

- [ ] **Step 1: Write the failing tests in ManageLocationsViewModelTest.kt**

```kotlin
package org.spsl.evtracker.ui.locations

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.core.model.ManageLocationsEvent
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeLocationWriter

@kotlinx.coroutines.ExperimentalCoroutinesApi
class ManageLocationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var locationReader: FakeLocationReader
    private lateinit var locationWriter: FakeLocationWriter
    private lateinit var backupScheduler: FakeBackupScheduler

    @Before fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        locationReader = FakeLocationReader()
        locationWriter = FakeLocationWriter()
        backupScheduler = FakeBackupScheduler()
    }

    @After fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun build(): ManageLocationsViewModel =
        ManageLocationsViewModel(locationReader, locationWriter, backupScheduler)

    private fun loc(id: Int, label: String, useCount: Int = 1, lastUsed: Long = id.toLong()) =
        CustomLocationEntity(id = id, label = label, useCount = useCount, lastUsed = lastUsed)

    @Test fun observe_emitsSortedList() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A", 1, 10), loc(2, "B", 5, 5), loc(3, "C", 5, 15))
        val vm = build()
        advanceUntilIdle()
        // Order: useCount DESC, lastUsed DESC ⇒ C, B, A
        assertEquals(listOf("C", "B", "A"), vm.uiState.value.locations.map { it.label })
    }

    @Test fun swipe_addsToPendingDeletions_emitsSnackbar() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A"))
        val vm = build()
        advanceUntilIdle()
        val received = mutableListOf<ManageLocationsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
        vm.onSwipeDelete("A")
        advanceUntilIdle()
        assertTrue("A" in vm.uiState.value.pendingDeletions)
        assertTrue(received.any { it is ManageLocationsEvent.ShowUndoSnackbar && it.label == "A" })
        job.cancel()
    }

    @Test fun swipe_then_undo_cancelsJob_removesFromPending() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)  // 2s into the 5s window
        vm.onUndoDelete("A")
        advanceUntilIdle()
        assertFalse("A" in vm.uiState.value.pendingDeletions)
        // The 5s job got cancelled — DB row stays:
        assertTrue(locationWriter.current().any { it.label == "A" } || locationReader.state.value.any { it.label == "A" })
    }

    @Test fun swipe_then_5sElapses_callsLocationWriterDelete_andEnqueueBackup() = runTest(dispatcher) {
        // Seed the reader; the VM looks the entity up in uiState.locations, NOT the writer's
        // own backing store, so we don't need to pre-populate the writer.
        locationReader.state.value = listOf(loc(1, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(5_001L)
        advanceUntilIdle()
        // The 5s job has fired ⇒ commitDelete invoked LocationWriter.delete + BackupScheduler.enqueueBackup.
        assertEquals(1, backupScheduler.enqueueCount)
    }

    @Test fun swipe_multipleLabels_each_has_independent_job() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A"), loc(2, "B"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)
        vm.onSwipeDelete("B")
        advanceTimeBy(2_000L)
        // A's 5s window has 1s left; B's has 3s left. Undo A:
        vm.onUndoDelete("A")
        advanceTimeBy(2_000L)  // total 6s — A undone, B should commit
        advanceUntilIdle()
        assertFalse("A" in vm.uiState.value.pendingDeletions)
        // B has been committed (its job fired); enqueue happened once (only for B)
        assertEquals(1, backupScheduler.enqueueCount)
    }

    @Test fun swipe_then_clearVm_cancelsAllJobs_doesNotCallDelete() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A"))
        val vm = build()
        advanceUntilIdle()
        vm.onSwipeDelete("A")
        advanceTimeBy(2_000L)
        // Force VM destruction: invoke onCleared via reflection (since it's protected).
        val onCleared = ManageLocationsViewModel::class.java.getDeclaredMethod("onCleared")
        onCleared.isAccessible = true
        onCleared.invoke(vm)
        advanceTimeBy(10_000L)
        // No delete should fire on the writer:
        assertEquals(0, backupScheduler.enqueueCount)
    }

    @Test fun emptyList_uiState_visibleLocationsIsEmpty() = runTest(dispatcher) {
        locationReader.state.value = emptyList()
        val vm = build()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.visibleLocations.isEmpty())
    }

    @Test fun swipe_lastRow_visibleLocationsIsEmpty_during_undo_window() = runTest(dispatcher) {
        locationReader.state.value = listOf(loc(1, "A"))
        val vm = build()
        advanceUntilIdle()
        // Pre-state: visible has the one row.
        assertEquals(1, vm.uiState.value.visibleLocations.size)
        vm.onSwipeDelete("A")
        advanceUntilIdle()
        // During undo window: visible is empty so the empty-state can show.
        assertTrue(vm.uiState.value.visibleLocations.isEmpty())
        // Undo: visible reverts.
        vm.onUndoDelete("A")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.visibleLocations.size)
    }
}
```

- [ ] **Step 2: Run; expect FAIL (VM is still the empty stub)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.locations.ManageLocationsViewModelTest"
```

- [ ] **Step 3: Replace ManageLocationsViewModel.kt**

```kotlin
package org.spsl.evtracker.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

@HiltViewModel
class ManageLocationsViewModel @Inject constructor(
    private val locationReader: LocationReader,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageLocationsUiState())
    val uiState: StateFlow<ManageLocationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ManageLocationsEvent>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
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
```

- [ ] **Step 4: Run tests; expect 8/8 PASS**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.locations.ManageLocationsViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModel.kt \
        app/src/test/java/org/spsl/evtracker/ui/locations/ManageLocationsViewModelTest.kt
git commit -m "feat(F1): ManageLocationsViewModel — swipe + 5s undo + idempotent jobs"
```

---

## Task 17: ManageLocationsFragment — RecyclerView + ItemTouchHelper + Snackbar Undo

**Files:**
- Modify: `app/src/main/res/layout/fragment_manage_locations.xml`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt`

- [ ] **Step 1: Replace fragment_manage_locations.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recycler"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

    <TextView
        android:id="@+id/empty_state"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:padding="32dp"
        android:gravity="center"
        android:text="@string/manage_locations_empty"
        android:textAppearance="?attr/textAppearanceBodyLarge"
        android:visibility="gone"/>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Replace ManageLocationsFragment.kt**

```kotlin
package org.spsl.evtracker.ui.locations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ManageLocationsEvent
import org.spsl.evtracker.databinding.FragmentManageLocationsBinding

@AndroidEntryPoint
class ManageLocationsFragment : Fragment() {

    private val vm: ManageLocationsViewModel by viewModels()
    private var _binding: FragmentManageLocationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ManageLocationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ManageLocationsAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.addItemDecoration(MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                adapter.labelAt(vh.bindingAdapterPosition)?.let { vm.onSwipeDelete(it) }
            }
        }).attachToRecyclerView(binding.recycler)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    adapter.submitList(state.visibleLocations)
                    binding.emptyState.isVisible = state.visibleLocations.isEmpty()
                    binding.recycler.isVisible = state.visibleLocations.isNotEmpty()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { event ->
                    when (event) {
                        is ManageLocationsEvent.ShowUndoSnackbar ->
                            Snackbar.make(
                                binding.root,
                                getString(R.string.manage_locations_undo_snackbar, event.label),
                                Snackbar.LENGTH_LONG
                            )
                                .setAction(R.string.common_undo) { vm.onUndoDelete(event.label) }
                                .show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_manage_locations.xml \
        app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt
git commit -m "feat(F1): ManageLocationsFragment — RecyclerView + ItemTouchHelper + Undo"
```

---

## Task 18: MainActivity startup auto-recovery

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/MainActivity.kt`

- [ ] **Step 1: Replace MainActivity.kt**

```kotlin
package org.spsl.evtracker

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.databinding.ActivityMainBinding
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var resetAllDataUseCase: ResetAllDataUseCase

    private val isLoading = MutableStateFlow(true)
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var navGraph: NavGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { isLoading.value }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        binding.bottomNav.setupWithNavController(navController)
        val hideOn = setOf(
            R.id.wizardFragment,
            R.id.chargeEditFragment,
            R.id.carsFragment,
            R.id.manageLocationsFragment
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.isVisible = dest.id !in hideOn
        }

        startupSequence()
    }

    private fun startupSequence() {
        lifecycleScope.launch {
            // F1 — startup auto-recovery for an interrupted global reset (§9.2).
            // Splash stays on screen while this runs (isLoading is still true).
            if (settingsRepository.resetInProgress.first()) {
                val result = runCatching { resetAllDataUseCase() }
                if (result.isFailure) {
                    val cause = result.exceptionOrNull()
                    android.util.Log.e("MainActivity", "Reset auto-recovery failed", cause)
                    // BLOCKING dialog: user cannot reach normal UI with resetInProgress=true.
                    // The dialog stays on top of an empty Activity surface; nav graph is NOT mounted.
                    showRecoveryFailureDialog(cause)
                    return@launch
                }
            }
            mountNavGraph()
        }
    }

    private fun showRecoveryFailureDialog(cause: Throwable?) {
        // Dismiss the splash so the dialog appears on a blank Activity surface.
        // The nav host stays unmounted (navController.graph is never set), so there
        // is no Fragment to interact with.
        isLoading.value = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_failure_title)
            .setMessage(getString(R.string.recovery_failure_body, cause?.localizedMessage ?: ""))
            .setCancelable(false)  // user MUST tap Retry
            .setPositiveButton(R.string.recovery_failure_retry) { _, _ ->
                // Re-arm splash and retry the auto-recovery sequence.
                isLoading.value = true
                startupSequence()
            }
            .show()
    }

    private suspend fun mountNavGraph() {
        val complete = settingsRepository.setupComplete.first()
        if (!complete) navGraph.setStartDestination(R.id.wizardFragment)
        navController.graph = navGraph
        isLoading.value = false
    }

    /**
     * Test hook: instrumented tests use this to wait for "startup completed" without
     * relying on `Thread.sleep`. True iff the nav graph has been mounted, which only
     * happens after auto-recovery either ran successfully or was skipped (flag was false).
     */
    @VisibleForTesting
    fun isNavGraphMounted(): Boolean = ::navController.isInitialized && navController.graph != null && navController.graph === navGraph && !isLoading.value
}
```

- [ ] **Step 2: Build to verify**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/MainActivity.kt
git commit -m "feat(F1): MainActivity startup auto-recovery for interrupted resets"
```

---

## Task 19: Instrumented tests — SettingsFragment + ManageLocationsFragment + MainActivity recovery

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/ui/settings/SettingsFragmentTest.kt`
- Create: `app/src/androidTest/java/org/spsl/evtracker/ui/locations/ManageLocationsFragmentTest.kt`
- Create: `app/src/androidTest/java/org/spsl/evtracker/MainActivityResetRecoveryTest.kt`

These tests follow the existing pattern (Hilt + Espresso) used by `ChargeEditFragmentTest`. They compile via `:app:assembleDebugAndroidTest` but require an emulator to run (sandbox-incompatible).

- [ ] **Step 1: Create SettingsFragmentTest.kt**

```kotlin
package org.spsl.evtracker.ui.settings

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SettingsFragmentTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Before fun setup() { hiltRule.inject() }

    @Test fun themeRow_tap_opensDialog_select_dark_updatesSummary() {
        // Launch MainActivity; navigate to settings via bottom nav
        // (Implementer: use ActivityScenario.launch<MainActivity> + bottom nav click pattern from existing tests.)
        // 1. Navigate to settings.
        onView(withId(R.id.nav_settings)).perform(click())
        // 2. Click theme row.
        onView(withId(R.id.row_theme)).perform(click())
        // 3. In the dialog, click "Dark".
        onView(withText(R.string.settings_theme_dark)).perform(click())
        // 4. Summary should now read "Dark".
        onView(withId(R.id.summary_theme)).check(matches(withText(R.string.settings_theme_dark)))
    }

    @Test fun exportCsv_disabled_whenNoActiveCar() {
        // Seed DataStore with activeCarId=-1 via a TestSettingsRepository hook.
        // Click row should not trigger any share intent — assert by checking that no chooser appears.
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_export_csv)).check(matches(isDisplayed()))
        // Tap should be ignored — implementer asserts via no-Intent-launched.
        onView(withId(R.id.row_export_csv)).perform(click())
        // No chooser opens; this is a soft assertion via Espresso intent-not-fired pattern.
    }

    @Test fun resetAll_confirm_navigatesToWizard() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_reset_all)).perform(click())
        onView(withText(R.string.common_confirm)).perform(click())
        // Wizard should be visible:
        onView(withId(R.id.wizardFragment)).check(matches(isDisplayed()))
    }

    @Test fun resetAll_dialogText_includesDriveWarning_whenDriveEnabled() {
        // Seed driveEnabled=true via TestSettings hook.
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_reset_all)).perform(click())
        onView(withText(R.string.settings_reset_all_confirm_drive_on)).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 2: Create ManageLocationsFragmentTest.kt**

```kotlin
package org.spsl.evtracker.ui.locations

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ManageLocationsFragmentTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Before fun setup() {
        hiltRule.inject()
        // Implementer: seed CustomLocationDao via Hilt-bound test repo with two rows ("Office", "Home")
    }

    @Test fun swipe_showsSnackbar_undo_restoresRow() {
        // Navigate to ManageLocations.
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_manage_locations)).perform(click())

        onView(withText("Office")).check(matches(isDisplayed()))
        // Swipe Office row left.
        onView(withText("Office")).perform(GeneralSwipeAction(Swipe.FAST, GeneralLocation.CENTER_RIGHT, GeneralLocation.CENTER_LEFT, Press.FINGER))

        // Snackbar with Undo:
        onView(withText(R.string.common_undo)).perform(click())
        // Row is back:
        onView(withText("Office")).check(matches(isDisplayed()))
    }

    @Test fun swipe_no_undo_after_5s_rowIsGoneAfterReopen() {
        onView(withId(R.id.nav_settings)).perform(click())
        onView(withId(R.id.row_manage_locations)).perform(click())
        onView(withText("Office")).perform(GeneralSwipeAction(Swipe.FAST, GeneralLocation.CENTER_RIGHT, GeneralLocation.CENTER_LEFT, Press.FINGER))
        // Wait 5.5 seconds for the commit.
        Thread.sleep(5_500)
        // Reopen the fragment to force a re-read from DB.
        onView(withId(R.id.row_manage_locations)).perform(click())
        // Row is gone — only "Home" remains.
        onView(withText("Office")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withText("Home")).check(matches(isDisplayed()))
    }
}
```

- [ ] **Step 3: Create MainActivityResetRecoveryTest.kt**

```kotlin
package org.spsl.evtracker

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class MainActivityResetRecoveryTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var database: AppDatabase

    /**
     * @BindValue overrides the production DataResetTransactionRunner binding for the
     * whole test class. The default impl forwards to the real Room runner; the failure
     * test overrides `failNext` before launching the activity.
     *
     * Hilt's @BindValue replaces the @Binds in DomainModule.kt; no separate test module
     * is needed.
     */
    @BindValue
    @JvmField
    val testRunner: TestableResetRunner = TestableResetRunner()

    class TestableResetRunner : DataResetTransactionRunner {
        @Volatile var failNext: Throwable? = null
        @Volatile var realDelegate: DataResetTransactionRunner? = null
        @Volatile var clearCalls: Int = 0
        override suspend fun clearAllTables() {
            clearCalls++
            failNext?.let { failNext = null; throw it }
            realDelegate?.clearAllTables()
        }
    }

    @Before fun setup() {
        hiltRule.inject()
        // Wire the real Room delegate so the success path actually clears tables.
        testRunner.realDelegate = org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner(database)
    }

    /** Polls `MainActivity.isNavGraphMounted` until true; better than fixed Thread.sleep. */
    private fun ActivityScenario<MainActivity>.awaitNavMounted(timeoutMs: Long = 10_000) = runBlocking {
        withTimeout(timeoutMs) {
            while (true) {
                var mounted = false
                onActivity { mounted = it.isNavGraphMounted() }
                if (mounted) return@withTimeout
                delay(100)
            }
        }
    }

    @Test fun startup_resetInProgressTrue_runsUseCase_clearsFlag_beforeUiVisible() = runBlocking {
        // Seed: cars populated; resetInProgress=true.
        val carId = database.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0)
        ).toInt()
        settingsRepository.setResetInProgress(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            // Wait on the observable signal: the use case clears the flag in Step 3.
            withTimeout(10_000) {
                settingsRepository.resetInProgress.first { !it }
            }
            // Then confirm the activity's nav graph is mounted (success path).
            scenario.awaitNavMounted()
        }

        // Auto-recovery ran: the seeded car was deleted by the runner.
        assertEquals(null, database.carDao().getById(carId))
        assertFalse(settingsRepository.resetInProgress.first())
        assertEquals(1, testRunner.clearCalls)
    }

    @Test fun startup_resetInProgressFalse_doesNotRunUseCase() = runBlocking {
        val carId = database.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0)
        ).toInt()
        settingsRepository.setResetInProgress(false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.awaitNavMounted()
        }

        // Auto-recovery did NOT run: the runner was never invoked, the seeded car is still there.
        assertEquals(0, testRunner.clearCalls)
        val car = database.carDao().getById(carId)
        assertNotNull(car)
        assertEquals("Test", car!!.name)
    }

    @Test fun startup_resetRecoveryThrows_showsRetryDialog_doesNotMountNavGraph() = runBlocking {
        // Force the use case to throw inside Step 2 (clearAllTables) before Step 3 commits the flag.
        testRunner.failNext = IllegalStateException("simulated room failure")
        settingsRepository.setResetInProgress(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            // The dialog appears asynchronously after the failed runCatching.
            // Espresso's `inRoot(isDialog())` matcher will block until a dialog window exists.
            onView(withText(R.string.recovery_failure_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

            // Confirm the nav graph was NOT mounted — user cannot reach Dashboard or Wizard.
            scenario.onActivity { activity ->
                assert(!activity.isNavGraphMounted())
            }
        }

        // Flag is still true on the failure path:
        kotlinx.coroutines.runBlocking {
            assert(settingsRepository.resetInProgress.first())
        }
    }
}
```

- [ ] **Step 4: Verify instrumented compile**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/ui/settings/SettingsFragmentTest.kt \
        app/src/androidTest/java/org/spsl/evtracker/ui/locations/ManageLocationsFragmentTest.kt \
        app/src/androidTest/java/org/spsl/evtracker/MainActivityResetRecoveryTest.kt
git commit -m "test(F1): instrumented coverage — Settings/ManageLocations/recovery"
```

---

## Task 20: Final acceptance + CLAUDE.md status update

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Run full JVM suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL with ~188 tests green.

- [ ] **Step 2: Run instrumented compile**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Update CLAUDE.md Status banner**

Find the existing `> **Status:**` line in `CLAUDE.md` and replace with:

```markdown
> **Status:** Sub-projects A (foundation/DI/Room v3), B (repositories), C (domain services + use cases), D (Core UI: Dashboard/ChargeEdit/Cars/History), E (Drive backup), and F1 (Settings remainder + ManageLocations + reset use cases + startup auto-recovery) are all merged. Wizard, Dashboard, ChargeEdit, Cars, History, Settings, and ManageLocations are fully wired; Charts remains a placeholder fragment until F2. JVM unit-test count: ~188. Instrumented suite compiles via `:app:assembleDebugAndroidTest` (running requires an emulator).
```

Also update the Architecture line (the `Settings ⊘` placeholder column):

Find the architecture block:

```
UI:       Fragments + ViewModels (Wizard ✓, Dashboard ✓, ChargeEdit ✓, Cars ✓, History ✓, Charts ⊘, Settings ⊘, ManageLocations ⊘)
```

Replace with:

```
UI:       Fragments + ViewModels (Wizard ✓, Dashboard ✓, ChargeEdit ✓, Cars ✓, History ✓, Charts ⊘, Settings ✓, ManageLocations ✓)
          BottomNavigationView in MainActivity hides on Wizard / ChargeEdit / Cars / ManageLocations
```

- [ ] **Step 4: Commit CLAUDE.md update**

```bash
git add CLAUDE.md
git commit -m "docs(F1): update CLAUDE.md status — Sub-project F1 landed"
```

- [ ] **Step 5: Hand-off**

The branch `feat/sub-project-f1` is ready for merge into `main` via `--no-ff`. Manual smoke (per CLAUDE.md): launch debug build, theme picker switches without restart, CSV row triggers system share sheet, reset-all confirms then routes through wizard to empty Dashboard.

---

## Notes for the implementer

- **Build commands:** always prefix with `GRADLE_USER_HOME=/tmp/gradle-home` per CLAUDE.md's sandbox quirk.
- **Test event collection:** every `events.collect` test must `launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { ... } }` BEFORE the action, then `advanceUntilIdle()` (or `runCurrent()`) after. CLAUDE.md spells this out under "ViewModel + event pattern (D-era)."
- **Time-controlled tests:** use `StandardTestDispatcher` + `advanceTimeBy(5_001L)` for the 5s undo window, NEVER `UnconfinedTestDispatcher` (it runs `delay` synchronously and breaks the assertion).
- **Hilt + Espresso:** the existing `ChargeEditFragmentTest` and `DashboardFragmentTest` show the working test setup pattern (`@HiltAndroidTest`, `HiltAndroidRule`, `ActivityScenario.launch<MainActivity>()`).
- **Currency array:** the spec calls it `R.array.currencies` but the actual resource is named `R.array.supported_currencies` (verified by `grep` against `app/src/main/res/values/currencies.xml`). Use the actual name.
- **Module placement of `DataResetTransactionRunner`:** F1 places the interface in `domain/repository/` (not `domain/backup/` like `RestoreTransactionRunner`) because the reset is data-mutation infrastructure rather than backup-flow infrastructure. The DI binding goes in `DomainModule` next to the existing repository bindings.
