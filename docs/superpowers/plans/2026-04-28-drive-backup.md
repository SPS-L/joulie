# Drive Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This is the same workflow used to ship Sub-projects A (foundation), B (data layer), C (domain core), and D (core UI) on `main`; no other execution environment is implied.

**Goal:** Ship Sub-project E from `docs/superpowers/specs/2026-04-28-drive-backup-design.md` — replace the no-op `BackupScheduler`/`BackupRepository` from C with real WorkManager + Google Drive AppData implementations, plus a Drive-only Settings UI that drives the OAuth consent and restore-on-enable flows. Adds `DriveAuthManager` and `DriveRemoteSource` domain seams, `WorkManagerBackupScheduler`, `DriveBackupRepository`, `AndroidDriveAuthManager`, `GoogleDriveRemoteSource`, `DriveBackupWorker`, a new `BackupModule` Hilt module, two new `SettingsReader`/`SettingsWriter` accessors, and a rewritten `SettingsFragment` + `SettingsViewModel`.

**Architecture:** Domain owns thin interfaces (`DriveAuthManager`, `DriveRemoteSource`, suspend `BackupScheduler.enqueueBackup`); data layer provides the Android-touching implementations. The Authorization API is wrapped behind `DriveAuthManager.AuthResult` so the Worker (silent path) and Settings (interactive path) share the same seam. `DriveBackupRepository` composes the Drive remote source with the existing `BackupSerializer` and entity readers — no Drive-specific code leaks into use cases. `WorkManagerBackupScheduler` reads `driveEnabled` and short-circuits when off; this enforces the gate documented in the C-era `BackupScheduler` KDoc.

**Tech Stack:** Kotlin 1.9.21 · Hilt 2.50 (already wired) · `androidx.hilt:hilt-work` 1.1.0 (added in this PR; pulls KSP processor) · `play-services-auth` 21.2.0 (already in deps) · `google-api-services-drive` v3-rev20231128-2.0.0 + `google-api-client-android` 2.2.0 (already in deps) · `androidx.work:work-runtime-ktx` 2.9.0 (already in deps) · `androidx.work:work-testing` 2.9.0 (already in test deps) · Gson 2.10.1 (already in deps).

**Spec source:** `docs/superpowers/specs/2026-04-28-drive-backup-design.md` (commit `97ef5ca` or later).

**Prerequisites:** Sub-projects A, B, C, and D are merged on `main` at commit `4ecc5b3` or later. The current branch `feat/drive-backup-sub-project-e` already exists and contains the spec commit.

---

## File map

### New files (production)

#### Domain layer

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt` | Interface + nested `AuthResult` sealed (`Success`, `NeedsResolution`, `Failed`). Two suspending entry points: `authorize()` and `silentToken()`. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt` | Interface for the four Drive REST operations: `findBackupFileId`, `createBackup`, `updateBackup`, `downloadBackup`. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthRequiredException.kt` | `class DriveAuthRequiredException : IOException("Drive consent required or revoked")`. Sentinel mapped by `DriveBackupWorker` to `Result.failure()`. |

#### Data layer (production)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt` | Authorization API impl. Single class; takes a generic `Context` (works with both `@ActivityContext` and `@ApplicationContext` because `silentToken()` only succeeds silently). |
| `app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt` | `@Singleton` implementation using `google-api-services-drive`. Bearer token injected per-call via `HttpRequestInitializer`. |
| `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt` | Replaces `NoOpBackupRepository`. Composes `DriveAuthManager`, `DriveRemoteSource`, `BackupSerializer`, three readers. |
| `app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt` | Replaces `NoOpBackupScheduler`. Gates on `settingsReader.driveEnabled`; enqueues unique `OneTimeWorkRequest<DriveBackupWorker>`. |
| `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt` | `@HiltWorker CoroutineWorker` with retry/failure mapping. |

#### DI

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt` | `@Provides @Singleton` for `WorkManager`, `() -> Long` (clock). |
| `app/src/main/java/org/spsl/evtracker/di/BackupModule.kt` | `@Binds` for `DriveRemoteSource → GoogleDriveRemoteSource`. Single `SingletonComponent`-installed module is enough for E since `DriveAuthManager` is provided per-context (see `ActivityBackupModule` + `SingletonBackupModule`). |
| `app/src/main/java/org/spsl/evtracker/di/ActivityBackupModule.kt` | `@InstallIn(ActivityComponent::class)` `@ActivityScoped` provides `DriveAuthManager` from `@ActivityContext Context`. Used by `SettingsViewModel` (transitively, through the activity-scoped binding). |
| `app/src/main/java/org/spsl/evtracker/di/SingletonBackupModule.kt` | `@InstallIn(SingletonComponent::class)` `@Singleton` provides `DriveAuthManager` from `@ApplicationContext Context`. Used by `DriveBackupRepository`. |

#### UI

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt` | `data class SettingsUiState`. Top-level `sealed class SettingsEvent` with `LaunchConsent`, `ShowRestorePrompt`, `RestoreSucceeded`, `ShowError`. |

### Modified files (production)

| Path | What changes |
|---|---|
| `gradle/libs.versions.toml` | Add `hiltWork = "1.1.0"` version, `androidx-hilt-work` library, `androidx-hilt-compiler` library. |
| `app/build.gradle.kts` | Add `implementation(libs.androidx.hilt.work)` and `ksp(libs.androidx.hilt.compiler)`. |
| `app/src/main/AndroidManifest.xml` | Add `xmlns:tools="http://schemas.android.com/tools"` to `<manifest>`. Add `<provider>` block that removes `WorkManagerInitializer` from `androidx.startup.InitializationProvider`. |
| `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt` | Implement `Configuration.Provider`. Inject `HiltWorkerFactory`. Override `workManagerConfiguration`. |
| `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt` | Add `LAST_BACKUP_AT = longPreferencesKey("lastBackupAt")`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt` | Add `val driveEnabled: Flow<Boolean>` and `val lastBackupAt: Flow<Long?>`. |
| `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt` | Add `suspend fun setLastBackupAt(epochMs: Long)`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` | Implement the new accessors against DataStore. |
| `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt` | `fun enqueueBackup()` → `suspend fun enqueueBackup()`. Update KDoc to drop the C-era "no-op until E" line and document the `driveEnabled` gate as enforced. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCase.kt` | The call site `backupScheduler.enqueueBackup()` continues to compile (already in suspend context). No source change required. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCase.kt` | Same — call site unchanged. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/AddCarUseCase.kt` | Same — call site unchanged. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/RenameCarUseCase.kt` | Same — call site unchanged. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/DeleteCarUseCase.kt` | Same — call site unchanged. |
| `app/src/main/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCase.kt` | Same — call site unchanged. |
| `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt` | Replace `bindBackupScheduler(impl: NoOpBackupScheduler)` with `WorkManagerBackupScheduler`. Replace `bindBackupRepository(impl: NoOpBackupRepository)` with `DriveBackupRepository`. |
| `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt` | **DELETED**. |
| `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt` | **DELETED**. |
| `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt` | Rewritten — full `StateFlow<SettingsUiState>` + `SharedFlow<SettingsEvent>` orchestration. |
| `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` | Rewritten — Drive toggle, "Last backup" line, `ActivityResultLauncher` for consent, restore dialog, error Snackbar. Other Settings rows kept as inert placeholders for F. |
| `app/src/main/res/layout/fragment_settings.xml` | Rewritten — `CoordinatorLayout` + Drive section + placeholder rows for theme/units/currency/reset/CSV/manage-locations. |
| `app/src/main/res/values/strings.xml` | Add ~15 new strings for Settings + restore + error messages. |

### New files (tests)

| Path | Class | Cases |
|---|---|---|
| `app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt` | `WorkManagerBackupSchedulerTest` | 4 |
| `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt` | `DriveBackupRepositoryTest` | 6 |
| `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt` | `SettingsViewModelTest` | 7 |
| `app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt` | `DriveBackupWorkerTest` (`@HiltAndroidTest`) | 3 |

### Modified files (tests)

| Path | What changes |
|---|---|
| `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` | Add `FakeDriveAuthManager`, `FakeDriveRemoteSource`. Expand `FakeSettingsReader` with `driveEnabled`/`lastBackupAt` `MutableStateFlow`s. Expand `FakeSettingsWriter` with `setLastBackupAt`. Make `FakeBackupScheduler.enqueueBackup` suspend. |
| `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt` | Add 3 new tests that use a real `BackupSerializer` round-trip via `FakeDriveRemoteSource` (rather than the JSON-string-only path). |

**Total new JVM tests in E: 17 across 3 new test files + 3 added tests in `RestoreBackupUseCaseTest` = 20.** After this PR, JVM count = 123 (D) + 20 = **143**. New instrumented test: 1 file with 3 cases (compile verified; running needs an emulator).

---

## Notes for the worker

### Sandbox quirks (carryover from A/B/C/D)

- Gradle's default `~/.gradle` is on a read-only filesystem in the sandbox. ALWAYS use `GRADLE_USER_HOME=/tmp/gradle-home` and pass `dangerouslyDisableSandbox: true` to your Bash tool calls when running gradle.
- The Android SDK at `$ANDROID_HOME` is installed and working.
- Per CLAUDE.md and the global instructions: **never compound git commands** with `&&`/`||`/`;`. Run `git add` and `git commit` as separate Bash calls.

### TDD discipline (carryover from prior sub-projects)

For Tasks that introduce new production classes with JVM tests (Tasks 6, 7, 14, 17), the order is **always**:

1. Write the failing test file.
2. Run the JVM test suite filtered to that test class. **Confirm compilation failure** (the production class doesn't exist yet, so the test references are unresolved). This is the failing-test step that proves the test exercises new code.
3. Write the production code.
4. Run the JVM test suite again. **Confirm pass.**
5. Commit (test + production in one commit).

For tasks that are purely Android-side production code (Tasks 8, 9, 10, 11, 15, 16) JVM TDD is not workable — `assembleDebug` is the verification step. Those tasks call out the verification command explicitly per step.

### Hilt @HiltWorker checklist

`@HiltWorker` requires three independent pieces of wiring:

1. **`androidx.hilt:hilt-work`** + **`androidx.hilt:hilt-compiler`** in `app/build.gradle.kts` (added in Task 1).
2. **`EVTrackerApp implements Configuration.Provider`** with an injected `HiltWorkerFactory` (Task 11).
3. **Manifest-level removal of `WorkManagerInitializer`** so WorkManager waits for the Application's `Configuration.Provider` (Task 11).

Skipping any of the three causes silent runtime failure: WorkManager will instantiate the worker via reflection without Hilt and crash on the first `@Inject` field.

### `SettingsReader` and the test fakes

The new `driveEnabled` and `lastBackupAt` flows are added to the **interface** in Task 2. Once added, the existing `FakeSettingsReader` in `Fakes.kt` will fail to compile (it doesn't implement them). Task 2 also fixes the fake. Then in later tasks, ViewModels and use cases that already inject `SettingsReader` keep compiling unchanged.

### The two `DriveAuthManager` Hilt providers

Per spec §4.10, the implementer creates **one** `AndroidDriveAuthManager` class and **two** `@Provides` modules:

- `ActivityBackupModule` (`@InstallIn(ActivityComponent::class)`, `@ActivityScoped`) — creates an instance from `@ActivityContext Context`. The `SettingsViewModel` uses this instance via field injection through the activity component.
- `SingletonBackupModule` (`@InstallIn(SingletonComponent::class)`, `@Singleton`) — creates an instance from `@ApplicationContext Context`. `DriveBackupRepository` (which is `@Singleton`) gets this instance.

Both providers return `DriveAuthManager`, the interface. Hilt resolves the binding by component scope, so there is no clash.

### When making the `enqueueBackup()` suspend conversion

This is a single global change in Task 3. The interface, the no-op (still present in this task — deletion is in Task 13), the test fake, and the 7 production callers must all flip in one commit so `assembleDebug` and `:app:testDebugUnitTest` both stay green.

### What stays a placeholder for F

The Settings UI in E shows ONE working interactive row (the Drive backup toggle + last-backup line + restore dialog). All other entries (theme picker, distance unit, currency, reset preferences, CSV export, manage locations) render as inert disabled rows with the text "Coming in next update". F replaces them with real controls.

---

## Task 1: Build dependencies for `@HiltWorker`

Add `androidx.hilt:hilt-work` and its compiler so `@HiltWorker` and `HiltWorkerFactory` are on the classpath. No code change yet.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version and library aliases to the catalog**

Open `gradle/libs.versions.toml`. In the `[versions]` block, add a line:

```toml
hiltWork = "1.1.0"
```

In the `[libraries]` block, add two lines after the existing `androidx-hilt-navigation-fragment` entry:

```toml
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltWork" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltWork" }
```

- [ ] **Step 2: Wire the new aliases in `app/build.gradle.kts`**

In the `dependencies { … }` block, add the `androidx.hilt.work` runtime alongside the existing Hilt aliases (after `implementation(libs.hilt.android)` and `ksp(libs.hilt.android.compiler)`):

```kotlin
implementation(libs.androidx.hilt.work)
ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 3: Verify the build still compiles**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The new dependencies should resolve from `google()` and pull `androidx.hilt:hilt-work:1.1.0` and `androidx.hilt:hilt-compiler:1.1.0`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add androidx.hilt:hilt-work for @HiltWorker support"
```

---

## Task 2: Extend `SettingsReader`/`SettingsWriter` with `driveEnabled` and `lastBackupAt`

Add the two new accessors that the scheduler, repository, worker, and Settings UI all need. Update `SettingsRepository` and `Fakes.kt` so existing callers keep compiling.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Add the `LAST_BACKUP_AT` preference key**

Replace the contents of `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`:

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
    val ACTIVE_CAR_ID  = intPreferencesKey("activeCarId")
    val DRIVE_ENABLED  = booleanPreferencesKey("driveEnabled")
    val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt")
    val THEME          = stringPreferencesKey("theme")
}
```

- [ ] **Step 2: Add accessors to `SettingsReader` interface**

Replace the contents of `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`:

```kotlin
package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsReader {
    val activeCarId: Flow<Int>
    val primaryMetric: Flow<String>
    val distanceUnit: Flow<String>
    val currency: Flow<String>
    val driveEnabled: Flow<Boolean>
    val lastBackupAt: Flow<Long?>
}
```

- [ ] **Step 3: Add `setLastBackupAt` to `SettingsWriter` interface**

Replace the contents of `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt`:

```kotlin
package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)
}
```

- [ ] **Step 4: Implement the new accessors in `SettingsRepository`**

In `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`, add `override` modifiers and the two new fields. Replace the file with:

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

    val theme: Flow<String> =
        dataStore.data.map { it[PreferenceKeys.THEME] ?: "system" }

    override val activeCarId: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

    override val driveEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.DRIVE_ENABLED] ?: false }

    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[PreferenceKeys.LAST_BACKUP_AT] }

    suspend fun completeSetup(metric: String, unit: String, currency: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.PRIMARY_METRIC] = metric
            prefs[PreferenceKeys.DISTANCE_UNIT]  = unit
            prefs[PreferenceKeys.CURRENCY]       = currency
            prefs[PreferenceKeys.SETUP_COMPLETE] = true
        }
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[PreferenceKeys.THEME] = theme }
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

- [ ] **Step 5: Update `FakeSettingsReader` and `FakeSettingsWriter`**

Open `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. Replace the existing `FakeSettingsReader` class with:

```kotlin
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
    private val lastBackup = MutableStateFlow(lastBackupAtInit)
    override val activeCarId: Flow<Int> = activeCar
    override val primaryMetric: Flow<String> = metric
    override val distanceUnit: Flow<String> = unit
    override val currency: Flow<String> = curr
    override val driveEnabled: Flow<Boolean> = drive
    override val lastBackupAt: Flow<Long?> = lastBackup
    fun setActiveCarId(id: Int) { activeCar.value = id }
    fun setDriveEnabled(enabled: Boolean) { drive.value = enabled }
    fun setLastBackupAt(value: Long?) { lastBackup.value = value }
}
```

Replace the existing `FakeSettingsWriter` class with:

```kotlin
class FakeSettingsWriter(
    private val activeCar: MutableStateFlow<Int> = MutableStateFlow(-1),
    private val drive: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val lastBackup: MutableStateFlow<Long?> = MutableStateFlow(null)
) : SettingsWriter {
    override suspend fun setActiveCarId(id: Int) { activeCar.value = id }
    override suspend fun setDriveEnabled(enabled: Boolean) { drive.value = enabled }
    override suspend fun setLastBackupAt(epochMs: Long) { lastBackup.value = epochMs }
    fun activeCarId(): Int = activeCar.value
    fun driveEnabled(): Boolean = drive.value
    fun lastBackupAt(): Long? = lastBackup.value
}
```

- [ ] **Step 6: Verify build and tests are green**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All ~123 existing tests pass — they don't reference the new fields, and the new fakes satisfy the expanded interfaces.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(settings): add driveEnabled and lastBackupAt accessors"
```

---

## Task 3: Convert `BackupScheduler.enqueueBackup()` to suspend

A single global change so the scheduler can read DataStore without blocking. The 7 production callers are already inside `suspend` functions, and the 6 (or so) test sites are inside `runTest { … }` blocks. Mechanical conversion.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Update the interface**

Replace the contents of `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt`:

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Requests a backup of current local state.
 *
 * **Contract:** implementations own the `driveEnabled` gate. If Drive backup is disabled,
 * the implementation MUST no-op rather than schedule a Worker. Use cases (SaveChargeEvent,
 * DeleteChargeEvent, RestoreBackup, AddCar, RenameCar, DeleteCar) call [enqueueBackup]
 * unconditionally after every persisted state change — they do NOT read `driveEnabled`
 * themselves.
 *
 * Suspending so implementations can read DataStore for the gate.
 *
 * Production binding: [org.spsl.evtracker.data.backup.WorkManagerBackupScheduler].
 */
interface BackupScheduler {
    suspend fun enqueueBackup()
}
```

- [ ] **Step 2: Update the no-op implementation**

Replace the contents of `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupScheduler

@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override suspend fun enqueueBackup() {
        // Replaced by WorkManagerBackupScheduler in Task 12; deleted entirely in Task 13.
    }
}
```

- [ ] **Step 3: Update the `FakeBackupScheduler` in `Fakes.kt`**

In `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`, replace the existing `FakeBackupScheduler` class with:

```kotlin
class FakeBackupScheduler : BackupScheduler {
    var enqueueCount: Int = 0
        private set
    override suspend fun enqueueBackup() { enqueueCount++ }
}
```

- [ ] **Step 4: Verify the suspend conversion compiles cleanly**

The 7 production callers (`SaveChargeEventUseCase.kt`, `DeleteChargeEventUseCase.kt`, `AddCarUseCase.kt`, `RenameCarUseCase.kt`, `DeleteCarUseCase.kt`, `RestoreBackupUseCase.kt` — calls in two places) are already inside `suspend operator fun invoke(...)` bodies, so `backupScheduler.enqueueBackup()` continues to compile without source changes.

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify tests are green**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: all ~123 tests pass. Test sites that call `backupScheduler.enqueueBackup()` (e.g. `SaveChargeEventUseCaseTest`, `DeleteChargeEventUseCaseTest`, `RestoreBackupUseCaseTest`) are inside `runTest { … }` and continue to compile.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "refactor(backup): make BackupScheduler.enqueueBackup suspend"
```

---

## Task 4: `DriveAuthManager` interface + `AuthResult` sealed + fake

Pure-domain interface that the Worker, the repository, and the Settings VM all consume. No Android dependencies — `IntentSender` is a `framework` type but it's exposed as a sealed-class case, so JVM tests never construct it.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt`:

```kotlin
package org.spsl.evtracker.domain.backup

import android.content.IntentSender

/**
 * Authorization-API wrapper for the `drive.appdata` scope.
 *
 * Two callers, two scopes (see DI: `ActivityBackupModule` + `SingletonBackupModule`):
 * - Settings UI: activity-scoped instance; can return [AuthResult.NeedsResolution] which
 *   the fragment launches via `ActivityResultLauncher<IntentSenderRequest>`.
 * - DriveBackupRepository (and DriveBackupWorker through it): singleton-scoped instance;
 *   uses [silentToken] which collapses NeedsResolution into Failed.
 */
interface DriveAuthManager {
    /** Interactive authorize. May return any [AuthResult] case. */
    suspend fun authorize(): AuthResult

    /**
     * Silent (non-interactive) authorize. Never returns [AuthResult.NeedsResolution];
     * if consent is required, returns [AuthResult.Failed].
     */
    suspend fun silentToken(): AuthResult

    sealed class AuthResult {
        data class Success(val accessToken: String) : AuthResult()
        data class NeedsResolution(val intentSender: IntentSender) : AuthResult()
        data class Failed(val reason: String, val cause: Throwable? = null) : AuthResult()
    }
}
```

- [ ] **Step 2: Add `FakeDriveAuthManager` to `Fakes.kt`**

In `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`, add an import:

```kotlin
import org.spsl.evtracker.domain.backup.DriveAuthManager
```

Then add the new fake at the bottom of the file (after the existing fakes):

```kotlin
class FakeDriveAuthManager(
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token")
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
```

- [ ] **Step 3: Verify build is green**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL. The new interface and fake compile; nothing references them yet.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(backup): add DriveAuthManager interface + FakeDriveAuthManager"
```

---

## Task 5: `DriveRemoteSource` interface + fake + `DriveAuthRequiredException`

Domain seam for the four Drive REST operations. Tested only through `DriveBackupRepositoryTest` (Task 7) — no standalone test for the interface itself.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthRequiredException.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt`:

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Drive REST operations for the App Data folder. The single backup file is named
 * `evtracker_backup.json` (constant in the implementation).
 *
 * All operations take a fresh OAuth2 access token per call; tokens expire and the
 * caller (DriveBackupRepository) is responsible for re-fetching one through
 * DriveAuthManager before each operation sequence.
 *
 * Errors are surfaced as IOException; callers (DriveBackupWorker via DriveBackupRepository)
 * map IOException to Result.retry() and DriveAuthRequiredException to Result.failure().
 */
interface DriveRemoteSource {
    /** Returns the Drive fileId of the existing evtracker_backup.json, or null when absent. */
    suspend fun findBackupFileId(accessToken: String): String?

    /** Creates a new evtracker_backup.json. Returns the new fileId. */
    suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String

    /** Replaces the body of an existing fileId. */
    suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray)

    /** Downloads the body of fileId. */
    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray
}
```

- [ ] **Step 2: Create the sentinel exception**

Create `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthRequiredException.kt`:

```kotlin
package org.spsl.evtracker.domain.backup

import java.io.IOException

/**
 * Raised by DriveBackupRepository when DriveAuthManager.silentToken() does not
 * return Success. Mapped by DriveBackupWorker to Result.failure() (no retry —
 * the user must grant consent in Settings).
 */
class DriveAuthRequiredException : IOException("Drive consent required or revoked")
```

- [ ] **Step 3: Add `FakeDriveRemoteSource` to `Fakes.kt`**

In `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`, add an import:

```kotlin
import java.io.IOException
import org.spsl.evtracker.domain.backup.DriveRemoteSource
```

Then add the fake at the end of the file:

```kotlin
class FakeDriveRemoteSource : DriveRemoteSource {
    private var fileId: String? = null
    private var body: ByteArray? = null
    var failNext: Throwable? = null
    var lastTokenSeen: String? = null
        private set
    var createCount = 0
        private set
    var updateCount = 0
        private set

    override suspend fun findBackupFileId(accessToken: String): String? {
        lastTokenSeen = accessToken
        failNext?.let { failNext = null; throw it }
        return fileId
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String {
        lastTokenSeen = accessToken
        failNext?.let { failNext = null; throw it }
        fileId = "fake-file-id"
        body = jsonBytes
        createCount++
        return fileId!!
    }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        lastTokenSeen = accessToken
        failNext?.let { failNext = null; throw it }
        check(this.fileId == fileId) { "updateBackup with unknown fileId" }
        body = jsonBytes
        updateCount++
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        lastTokenSeen = accessToken
        failNext?.let { failNext = null; throw it }
        return body ?: throw IOException("no body")
    }

    fun seed(jsonBytes: ByteArray) {
        fileId = "fake-file-id"
        body = jsonBytes
    }

    fun lastUploadedBytes(): ByteArray? = body
    fun hasFile(): Boolean = fileId != null
}
```

- [ ] **Step 4: Verify build is green**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthRequiredException.kt app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(backup): add DriveRemoteSource interface + DriveAuthRequiredException"
```

---

## Task 6: `WorkManagerBackupScheduler` (production + JVM tests)

Production implementation gated on `driveEnabled`. JVM-testable via `WorkManagerTestInitHelper` from `androidx.work.work-testing`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.spsl.evtracker.testing.FakeSettingsReader

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WorkManagerBackupSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun enqueueBackup_whenDriveDisabled_isNoOp() = runTest {
        val settings = FakeSettingsReader(driveEnabledInit = false)
        val scheduler = WorkManagerBackupScheduler(workManager, settings)

        scheduler.enqueueBackup()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        assertTrue("expected no work enqueued when drive is disabled", infos.isEmpty())
    }

    @Test
    fun enqueueBackup_whenDriveEnabled_enqueuesUniqueWork() = runTest {
        val settings = FakeSettingsReader(driveEnabledInit = true)
        val scheduler = WorkManagerBackupScheduler(workManager, settings)

        scheduler.enqueueBackup()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        assertEquals(1, infos.size)
        // ENQUEUED while constraints not yet met (no network in Robolectric).
        assertEquals(WorkInfo.State.ENQUEUED, infos.first().state)
    }

    @Test
    fun enqueueBackup_appliesNetworkConnectedConstraint() = runTest {
        val settings = FakeSettingsReader(driveEnabledInit = true)
        val scheduler = WorkManagerBackupScheduler(workManager, settings)

        scheduler.enqueueBackup()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        val constraints = infos.first().constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun enqueueBackup_replacesPriorWork_whenCalledRapidly() = runTest {
        val settings = FakeSettingsReader(driveEnabledInit = true)
        val scheduler = WorkManagerBackupScheduler(workManager, settings)

        scheduler.enqueueBackup()
        scheduler.enqueueBackup()
        scheduler.enqueueBackup()

        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        // Replace policy collapses to a single live work item.
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
    }
}
```

`Robolectric` is not currently in the project's test deps. Add it as an `app/build.gradle.kts` `testImplementation` line in this same task — it's the standard JVM way to drive Android-typed `WorkManager`. Add to `gradle/libs.versions.toml`:

```toml
robolectric = "4.11.1"
```

```toml
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

And in `app/build.gradle.kts` `dependencies { … }`:

```kotlin
testImplementation(libs.robolectric)
```

`androidx.test.core` is already pulled transitively through `androidx-test-ext-junit`; no separate dependency needed.

- [ ] **Step 2: Run the test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.WorkManagerBackupSchedulerTest"
```

Expected: FAIL — `Unresolved reference: WorkManagerBackupScheduler`.

- [ ] **Step 3: Write the production code**

Create `app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.SettingsReader

@Singleton
class WorkManagerBackupScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settingsReader: SettingsReader
) : BackupScheduler {

    override suspend fun enqueueBackup() {
        if (!settingsReader.driveEnabled.first()) return
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "drive_backup"
        const val BACKOFF_SECONDS = 30L
    }
}
```

The compile fails because `DriveBackupWorker` doesn't exist yet. Stub it now to keep this task self-contained — Task 10 fills in the body. Create `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Stub — full implementation lands in Task 10. Returns Result.success() so the
 * worker doesn't crash if accidentally enqueued before the @HiltWorker wiring
 * is in place.
 */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.WorkManagerBackupSchedulerTest"
```

Expected: 4 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt
git commit -m "feat(backup): WorkManagerBackupScheduler + Robolectric tests"
```

---

## Task 7: `DriveBackupRepository` (production + JVM tests)

Composes `DriveAuthManager` (silent path), `DriveRemoteSource`, `BackupSerializer`, and the three entity readers. Replaces `NoOpBackupRepository`. JVM-testable end-to-end through fakes.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource
import org.spsl.evtracker.testing.FakeLocationReader

class DriveBackupRepositoryTest {

    private fun newRepo(
        auth: FakeDriveAuthManager = FakeDriveAuthManager(),
        remote: FakeDriveRemoteSource = FakeDriveRemoteSource(),
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        locations: List<CustomLocationEntity> = emptyList()
    ): Triple<DriveBackupRepository, FakeDriveAuthManager, FakeDriveRemoteSource> {
        val carReader = FakeCarReader(cars)
        val eventStore = MutableStateFlow(events)
        val eventQueries = FakeChargeEventQueries(eventStore)
        val locationReader = FakeLocationReader(locations)
        val repo = DriveBackupRepository(
            auth = auth,
            remote = remote,
            serializer = BackupSerializer(),
            carReader = carReader,
            chargeEventQueries = eventQueries,
            locationReader = locationReader
        )
        return Triple(repo, auth, remote)
    }

    @Test
    fun backupCurrentData_whenNoRemoteFile_callsCreate() = runTest {
        val (repo, _, remote) = newRepo(
            cars = listOf(CarEntity(1, "Tesla", "Tesla", "M3", 2024, 60.0, 0L))
        )
        repo.backupCurrentData()
        assertEquals(1, remote.createCount)
        assertEquals(0, remote.updateCount)
        assertNotNull(remote.lastUploadedBytes())
    }

    @Test
    fun backupCurrentData_whenRemoteFileExists_callsUpdate() = runTest {
        val remote = FakeDriveRemoteSource().apply { seed("{}".toByteArray()) }
        val (repo, _, _) = newRepo(remote = remote)
        repo.backupCurrentData()
        assertEquals(0, remote.createCount)
        assertEquals(1, remote.updateCount)
    }

    @Test
    fun backupCurrentData_uploadsValidBackupJson() = runTest {
        val car = CarEntity(1, "Tesla", "Tesla", "M3", 2024, 60.0, 100L)
        val event = ChargeEventEntity(1, 1, 1714000000000L, 12345.0, 22.4, "AC", null, null, null, null, "", 100L)
        val (repo, _, remote) = newRepo(cars = listOf(car), events = listOf(event))
        repo.backupCurrentData()
        val uploaded = String(remote.lastUploadedBytes()!!, Charsets.UTF_8)
        val parsed = BackupSerializer().fromJson(uploaded)
        assertEquals(BackupData.CURRENT_VERSION, parsed.backupVersion)
        assertEquals(1, parsed.cars.size)
        assertEquals(1, parsed.chargeEvents.size)
    }

    @Test
    fun backupCurrentData_whenSilentTokenFails_throwsDriveAuthRequired() = runTest {
        val auth = FakeDriveAuthManager(nextResult = DriveAuthManager.AuthResult.Failed("revoked"))
        val (repo, _, _) = newRepo(auth = auth)
        assertThrows(DriveAuthRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { repo.backupCurrentData() }
        }
    }

    @Test
    fun readRemoteBackup_whenNoFile_returnsNull() = runTest {
        val (repo, _, _) = newRepo()
        assertNull(repo.readRemoteBackup())
    }

    @Test
    fun readRemoteBackup_whenFileExists_returnsBody() = runTest {
        val payload = "{\"backup_version\":3}".toByteArray()
        val remote = FakeDriveRemoteSource().apply { seed(payload) }
        val (repo, _, _) = newRepo(remote = remote)
        val result = repo.readRemoteBackup()
        assertTrue(result!!.contains("\"backup_version\":3"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.DriveBackupRepositoryTest"
```

Expected: FAIL — `Unresolved reference: DriveBackupRepository`.

- [ ] **Step 3: Write the production code**

Create `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.LocationReader
import org.spsl.evtracker.domain.service.BackupSerializer

@Singleton
class DriveBackupRepository @Inject constructor(
    private val auth: DriveAuthManager,
    private val remote: DriveRemoteSource,
    private val serializer: BackupSerializer,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader
) : BackupRepository {

    override suspend fun backupCurrentData() {
        val token = requireToken()
        val cars = carReader.observeAll().first()
        val events = cars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val locations = locationReader.observeAll().first()
        val snapshot = BackupData.fromEntities(cars, events, locations)
        val bytes = serializer.toJson(snapshot).toByteArray(Charsets.UTF_8)
        val existing = remote.findBackupFileId(token)
        if (existing == null) {
            remote.createBackup(token, bytes)
        } else {
            remote.updateBackup(token, existing, bytes)
        }
    }

    override suspend fun readRemoteBackup(): String? {
        val token = requireToken()
        val fileId = remote.findBackupFileId(token) ?: return null
        return remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }

    private suspend fun requireToken(): String {
        val result = auth.silentToken()
        return when (result) {
            is DriveAuthManager.AuthResult.Success -> result.accessToken
            else -> throw DriveAuthRequiredException()
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.DriveBackupRepositoryTest"
```

Expected: 6 tests, all PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt
git commit -m "feat(backup): DriveBackupRepository with full JVM tests"
```

---

## Task 8: `AndroidDriveAuthManager` — Authorization API implementation

The first Android-touching production class. JVM tests would require mocking the Authorization API (which lives in Google Play services); compile-only verification suffices for E. The fakes from Task 4 cover the test surface that the rest of the app sees.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt`

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthManager.AuthResult

/**
 * Single class used by both Hilt scopes (Activity-scoped for Settings consent UI;
 * Singleton-scoped for Worker silent token). The Authorization API client itself
 * works with either context for silent calls; only interactive calls require the
 * Activity-scoped instance to surface the resolution intent.
 */
class AndroidDriveAuthManager(
    context: Context
) : DriveAuthManager {

    private val client = Identity.getAuthorizationClient(context)

    override suspend fun authorize(): AuthResult = await(interactive = true)

    override suspend fun silentToken(): AuthResult {
        val result = await(interactive = false)
        return when (result) {
            is AuthResult.NeedsResolution -> AuthResult.Failed("consent required")
            else -> result
        }
    }

    private suspend fun await(@Suppress("UNUSED_PARAMETER") interactive: Boolean): AuthResult =
        suspendCancellableCoroutine { cont ->
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
                .build()
            client.authorize(request)
                .addOnSuccessListener { result ->
                    val token = result.accessToken
                    val pendingIntent = result.pendingIntent
                    when {
                        token != null -> cont.resume(AuthResult.Success(token))
                        pendingIntent != null ->
                            cont.resume(AuthResult.NeedsResolution(pendingIntent.intentSender))
                        else -> cont.resume(AuthResult.Failed("authorize returned no token and no resolution"))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
}
```

The `interactive` parameter is currently unused — both branches call the same `client.authorize()`. The Authorization API does not distinguish silent vs interactive at request time; the response's `pendingIntent` indicates whether resolution is required. Keeping the parameter documents the caller's intent and may grow into a real difference later.

- [ ] **Step 2: Verify the build still compiles**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The class is not yet bound in DI; the binding lands in Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt
git commit -m "feat(backup): AndroidDriveAuthManager (Authorization API impl)"
```

---

## Task 9: `GoogleDriveRemoteSource` — Drive REST implementation

Uses `google-api-services-drive` (already in deps). Bearer token injected per-call via `HttpRequestInitializer`. Compile-only verification — JVM tests of the real Drive client require network or heavy mocking, and `DriveBackupRepositoryTest` already covers the contract through `FakeDriveRemoteSource`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt`

- [ ] **Step 1: Write the implementation**

Create `app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spsl.evtracker.domain.backup.DriveRemoteSource

@Singleton
class GoogleDriveRemoteSource @Inject constructor() : DriveRemoteSource {

    override suspend fun findBackupFileId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val drive = driveClient(accessToken)
        val list = drive.files().list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
            .setFields("files(id)")
            .execute()
        list.files?.firstOrNull()?.id
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val drive = driveClient(accessToken)
            val metadata = File()
                .setName(BACKUP_FILE_NAME)
                .setParents(listOf(APP_DATA_FOLDER))
            val media = ByteArrayContent(JSON_MIME, jsonBytes)
            val created = drive.files().create(metadata, media)
                .setFields("id")
                .execute()
            created.id
        }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val drive = driveClient(accessToken)
            val media = ByteArrayContent(JSON_MIME, jsonBytes)
            drive.files().update(fileId, null /* no metadata change */, media).execute()
        }
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val drive = driveClient(accessToken)
            val output = java.io.ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(output)
            output.toByteArray()
        }

    private fun driveClient(accessToken: String): Drive {
        val transport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val initializer = com.google.api.client.http.HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(transport, jsonFactory, initializer)
            .setApplicationName("EV Tracker")
            .build()
    }

    companion object {
        const val APP_DATA_FOLDER = "appDataFolder"
        const val BACKUP_FILE_NAME = "evtracker_backup.json"
        const val JSON_MIME = "application/json"

        @Suppress("unused")
        private val SCOPE_NAME: String = DriveScopes.DRIVE_APPDATA  // doc reference; not consumed here
    }
}
```

- [ ] **Step 2: Verify the build still compiles**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. The class will be bound to `DriveRemoteSource` in Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt
git commit -m "feat(backup): GoogleDriveRemoteSource (Drive REST AppData impl)"
```

---

## Task 10: `DriveBackupWorker` (real implementation)

Replace the Task-6 stub with the real `@HiltWorker` body. JVM tests for `@HiltWorker` are not practical (they require Hilt runtime + WorkerFactory); the worker behaviour is exercised end-to-end in the instrumented `DriveBackupWorkerTest` (Task 17) and unit-tested at the repository level (Task 7).

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`

- [ ] **Step 1: Replace the stub with the real implementation**

Replace the contents of `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.repository.SettingsWriter

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        backupRepository.backupCurrentData()
        settingsWriter.setLastBackupAt(clock.now())
        Result.success()
    } catch (_: DriveAuthRequiredException) {
        Result.failure()
    } catch (_: BackupVersionMismatch) {
        Result.failure()
    } catch (_: IOException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Tiny seam so tests can supply a deterministic time. Provided by `WorkerModule`.
 */
fun interface Clock {
    fun now(): Long
}
```

- [ ] **Step 2: Verify the build still compiles**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD FAILED — `Clock` has no Hilt provider yet. Task 11 adds the `WorkerModule` that provides it. Continue to Task 11; the build will go green once that lands.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt
git commit -m "feat(backup): real DriveBackupWorker with @HiltWorker"
```

---

## Task 11: `WorkerModule` + `EVTrackerApp` `Configuration.Provider` + manifest tweak

Three pieces of wiring that together make Hilt actually inject the worker.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `WorkerModule`**

Create `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`:

```kotlin
package org.spsl.evtracker.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.spsl.evtracker.data.backup.Clock

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }
}
```

- [ ] **Step 2: Update `EVTrackerApp` to implement `Configuration.Provider`**

Replace the contents of `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`:

```kotlin
package org.spsl.evtracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.repository.SettingsRepository

@HiltAndroidApp
class EVTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Default to follow-system synchronously; update once DataStore yields the persisted value.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        CoroutineScope(Dispatchers.Main).launch {
            val theme = settingsRepository.theme.first()
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
```

- [ ] **Step 3: Update the manifest to disable WorkManager auto-init**

Replace the contents of `app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".EVTrackerApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.EVTracker.SplashScreen">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />

    </application>
</manifest>
```

- [ ] **Step 4: Verify the build**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL — `Clock` is now bound, `HiltWorkerFactory` is now injected, and `WorkManager` initializes from `EVTrackerApp.workManagerConfiguration`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt app/src/main/AndroidManifest.xml
git commit -m "feat(worker): wire HiltWorkerFactory + disable WorkManager auto-init"
```

---

## Task 12: DI rebind — replace no-ops with real implementations + Drive provider modules

Three module changes happen together: `DomainModule` swaps the no-op bindings, two new modules (`ActivityBackupModule`, `SingletonBackupModule`) provide `DriveAuthManager` per-component, and `BackupModule` binds `DriveRemoteSource`.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/BackupModule.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/ActivityBackupModule.kt`
- Create: `app/src/main/java/org/spsl/evtracker/di/SingletonBackupModule.kt`

- [ ] **Step 1: Update `DomainModule`**

Replace the contents of `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt`:

```kotlin
package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.backup.AndroidCsvFileSink
import org.spsl.evtracker.data.backup.CacheDirRestoreSnapshotWriter
import org.spsl.evtracker.data.backup.DriveBackupRepository
import org.spsl.evtracker.data.backup.RoomRestoreTransactionRunner
import org.spsl.evtracker.data.backup.WorkManagerBackupScheduler
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
import org.spsl.evtracker.domain.repository.CarWriter
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
    @Binds abstract fun bindCarWriter(impl: CarRepository): CarWriter
    @Binds abstract fun bindChargeEventQueries(impl: ChargeEventRepository): ChargeEventQueries
    @Binds abstract fun bindChargeEventWriter(impl: ChargeEventRepository): ChargeEventWriter
    @Binds abstract fun bindLocationReader(impl: LocationRepository): LocationReader
    @Binds abstract fun bindLocationWriter(impl: LocationRepository): LocationWriter
    @Binds abstract fun bindSettingsReader(impl: SettingsRepository): SettingsReader
    @Binds abstract fun bindSettingsWriter(impl: SettingsRepository): SettingsWriter

    // Backup interfaces — real implementations as of Sub-project E.
    @Binds abstract fun bindBackupScheduler(impl: WorkManagerBackupScheduler): BackupScheduler
    @Binds abstract fun bindBackupRepository(impl: DriveBackupRepository): BackupRepository

    // Restore-flow infrastructure (unchanged from C).
    @Binds abstract fun bindRestoreTransactionRunner(impl: RoomRestoreTransactionRunner): RestoreTransactionRunner
    @Binds abstract fun bindRestoreSnapshotWriter(impl: CacheDirRestoreSnapshotWriter): RestoreSnapshotWriter

    // CSV export infrastructure.
    @Binds abstract fun bindCsvFileSink(impl: AndroidCsvFileSink): CsvFileSink
}
```

- [ ] **Step 2: Create `BackupModule` (Drive REST binding)**

Create `app/src/main/java/org/spsl/evtracker/di/BackupModule.kt`:

```kotlin
package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.spsl.evtracker.data.backup.GoogleDriveRemoteSource
import org.spsl.evtracker.domain.backup.DriveRemoteSource

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun bindDriveRemoteSource(impl: GoogleDriveRemoteSource): DriveRemoteSource
}
```

- [ ] **Step 3: Create `ActivityBackupModule`**

Create `app/src/main/java/org/spsl/evtracker/di/ActivityBackupModule.kt`:

```kotlin
package org.spsl.evtracker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import dagger.hilt.android.components.ActivityComponent
import org.spsl.evtracker.data.backup.AndroidDriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthManager

@Module
@InstallIn(ActivityComponent::class)
object ActivityBackupModule {
    @Provides
    @ActivityScoped
    fun provideDriveAuthManager(@ActivityContext context: Context): DriveAuthManager =
        AndroidDriveAuthManager(context)
}
```

- [ ] **Step 4: Create `SingletonBackupModule`**

Create `app/src/main/java/org/spsl/evtracker/di/SingletonBackupModule.kt`:

```kotlin
package org.spsl.evtracker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.spsl.evtracker.data.backup.AndroidDriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthManager

@Module
@InstallIn(SingletonComponent::class)
object SingletonBackupModule {
    @Provides
    @Singleton
    fun provideDriveAuthManager(@ApplicationContext context: Context): DriveAuthManager =
        AndroidDriveAuthManager(context)
}
```

- [ ] **Step 5: Verify the build**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Hilt finds two bindings for `DriveAuthManager` — one in `SingletonComponent`, one in `ActivityComponent` — which is legal because they live in different scopes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/di/DomainModule.kt app/src/main/java/org/spsl/evtracker/di/BackupModule.kt app/src/main/java/org/spsl/evtracker/di/ActivityBackupModule.kt app/src/main/java/org/spsl/evtracker/di/SingletonBackupModule.kt
git commit -m "feat(di): rebind backup interfaces to real impls; add Drive auth modules"
```

---

## Task 13: Delete the no-op classes

Now that `DomainModule` no longer references them, the no-op classes are dead code.

**Files:**
- Delete: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- Delete: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt`

- [ ] **Step 1: Remove the files**

```bash
git rm app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt
```

- [ ] **Step 2: Verify the build is still green**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. All ~129 unit tests pass (123 from D + 4 scheduler + 6 repository - 0 deleted).

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(backup): remove no-op stubs replaced by Drive impls"
```

---

## Task 14: `SettingsViewModel` (rewritten + JVM tests)

The Drive toggle / restore prompt orchestrator. The ViewModel does not directly inject `WorkManager` — `WorkManagerBackupScheduler` and `BackupScheduler` already give it the enqueue surface. To cancel work on toggle-off, the ViewModel injects a tiny new domain seam `BackupCanceller`. (Adding a `cancelBackup()` to `BackupScheduler` would also work; the seam keeps the interface single-responsibility.)

Wait — that's a design deviation from the spec. The spec §5.5 says `workManager.cancelUniqueWork("drive_backup")` from the VM. To keep the spec literal: the VM injects `WorkManager` directly. WorkManager is already provided in `WorkerModule`. That's the cleaner path.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt`
- Create: `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Create `SettingsUiState` and `SettingsEvent`**

Create `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt`:

```kotlin
package org.spsl.evtracker.core.model

import android.content.IntentSender
import androidx.annotation.StringRes

data class SettingsUiState(
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false
)

sealed class SettingsEvent {
    data class LaunchConsent(val intentSender: IntentSender) : SettingsEvent()
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package org.spsl.evtracker.ui.settings

import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val workManager: WorkManager = mock()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun newVm(
        auth: FakeDriveAuthManager = FakeDriveAuthManager(),
        remoteJson: String? = null,
        scheduler: FakeBackupScheduler = FakeBackupScheduler(),
        settingsReader: FakeSettingsReader = FakeSettingsReader(),
        settingsWriter: FakeSettingsWriter = FakeSettingsWriter()
    ): SettingsViewModel {
        val backupRepository = org.spsl.evtracker.testing.FakeBackupRepository(remoteJson = remoteJson)
        val eventStore = MutableStateFlow(emptyList<ChargeEventEntity>())
        val carReader = FakeCarReader()
        val eventQueries = FakeChargeEventQueries(eventStore)
        val locationReader = FakeLocationReader()
        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = backupRepository,
            backupSerializer = BackupSerializer(),
            transactionRunner = FakeRestoreTransactionRunner(),
            snapshotWriter = FakeRestoreSnapshotWriter(),
            carReader = carReader,
            chargeEventQueries = eventQueries,
            locationReader = locationReader,
            settingsWriter = settingsWriter,
            backupScheduler = scheduler
        )
        return SettingsViewModel(
            auth = auth,
            backupRepository = backupRepository,
            backupScheduler = scheduler,
            restoreUseCase = restoreUseCase,
            settingsReader = settingsReader,
            settingsWriter = settingsWriter,
            workManager = workManager
        )
    }

    @Test
    fun toggleOn_whenAuthSucceedsAndNoRemote_setsDriveEnabledAndEnqueuesBackup() = runTest {
        val scheduler = FakeBackupScheduler()
        val writer = FakeSettingsWriter()
        val vm = newVm(scheduler = scheduler, settingsWriter = writer)
        vm.onToggleDrive(true)
        advanceUntilIdle()
        assertTrue(writer.driveEnabled())
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun toggleOn_whenAuthNeedsResolution_emitsLaunchConsent() = runTest {
        val intentSender: android.content.IntentSender = mock()
        val auth = FakeDriveAuthManager(
            nextResult = DriveAuthManager.AuthResult.NeedsResolution(intentSender)
        )
        val vm = newVm(auth = auth)
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
        vm.onToggleDrive(true)
        advanceUntilIdle()
        assertTrue(received.any { it is SettingsEvent.LaunchConsent })
        job.cancel()
    }

    @Test
    fun toggleOn_whenRemoteBackupExists_emitsShowRestorePrompt() = runTest {
        val json = Gson().toJson(JsonObject().apply {
            addProperty("backup_version", BackupData.CURRENT_VERSION)
            addProperty("exported_at", "2026-04-25T10:00:00Z")
            add("cars", Gson().toJsonTree(emptyList<Any>()))
            add("charge_events", Gson().toJsonTree(emptyList<Any>()))
            add("custom_locations", Gson().toJsonTree(emptyList<Any>()))
        })
        val vm = newVm(remoteJson = json)
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
        vm.onToggleDrive(true)
        advanceUntilIdle()
        assertTrue(received.any { it is SettingsEvent.ShowRestorePrompt })
        job.cancel()
    }

    @Test
    fun confirmRestore_runsUseCaseAndEmitsRestoreSucceeded() = runTest {
        val json = Gson().toJson(JsonObject().apply {
            addProperty("backup_version", BackupData.CURRENT_VERSION)
            addProperty("exported_at", "2026-04-25T10:00:00Z")
            add("cars", Gson().toJsonTree(emptyList<Any>()))
            add("charge_events", Gson().toJsonTree(emptyList<Any>()))
            add("custom_locations", Gson().toJsonTree(emptyList<Any>()))
        })
        val vm = newVm(remoteJson = json)
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
        vm.onConfirmRestore()
        advanceUntilIdle()
        assertTrue(received.contains(SettingsEvent.RestoreSucceeded))
        job.cancel()
    }

    @Test
    fun skipRestore_enqueuesBackupWithoutRestore() = runTest {
        val scheduler = FakeBackupScheduler()
        val vm = newVm(scheduler = scheduler)
        vm.onSkipRestore()
        advanceUntilIdle()
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun toggleOff_setsDriveDisabledAndCancelsWork() = runTest {
        val writer = FakeSettingsWriter()
        val vm = newVm(settingsWriter = writer)
        vm.onToggleDrive(false)
        advanceUntilIdle()
        assertFalse(writer.driveEnabled())
        verify(workManager).cancelUniqueWork(org.spsl.evtracker.data.backup.WorkManagerBackupScheduler.UNIQUE_NAME)
    }

    @Test
    fun lastBackupAtFlowPropagatesIntoUiState() = runTest {
        val reader = FakeSettingsReader(lastBackupAtInit = 1714000000000L)
        val vm = newVm(settingsReader = reader)
        advanceUntilIdle()
        assertEquals(1714000000000L, vm.uiState.value.lastBackupAt)
        assertNull(vm.uiState.value.lastBackupAt?.let { null })  // no-op assertion to prevent unused warning
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.settings.SettingsViewModelTest"
```

Expected: FAIL — `SettingsViewModel` constructor does not match. (The current placeholder VM has only `@Inject constructor()`.)

- [ ] **Step 4: Write the production code**

Replace the contents of `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt`:

```kotlin
package org.spsl.evtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.core.model.SettingsUiState
import org.spsl.evtracker.data.backup.WorkManagerBackupScheduler
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.service.BackupSerializer
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: DriveAuthManager,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val restoreUseCase: RestoreBackupUseCase,
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val workManager: WorkManager
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
        combine(settingsReader.driveEnabled, settingsReader.lastBackupAt) { drive, last ->
            _uiState.value.copy(driveEnabled = drive, lastBackupAt = last)
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun onToggleDrive(checked: Boolean) {
        if (!checked) {
            viewModelScope.launch {
                settingsWriter.setDriveEnabled(false)
                workManager.cancelUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME)
            }
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthInFlight = true)
            when (val result = auth.authorize()) {
                is DriveAuthManager.AuthResult.Success -> handleAuthSuccess()
                is DriveAuthManager.AuthResult.NeedsResolution -> {
                    _events.tryEmit(SettingsEvent.LaunchConsent(result.intentSender))
                }
                is DriveAuthManager.AuthResult.Failed -> {
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_auth_failed))
                    _uiState.value = _uiState.value.copy(isAuthInFlight = false)
                }
            }
        }
    }

    fun onConsentResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch { handleAuthSuccess() }
        } else {
            _events.tryEmit(SettingsEvent.ShowError(R.string.drive_consent_cancelled))
            _uiState.value = _uiState.value.copy(isAuthInFlight = false)
        }
    }

    private suspend fun handleAuthSuccess() {
        settingsWriter.setDriveEnabled(true)
        val remote = try {
            backupRepository.readRemoteBackup()
        } catch (e: java.io.IOException) {
            _events.tryEmit(SettingsEvent.ShowError(R.string.drive_network_error))
            _uiState.value = _uiState.value.copy(isAuthInFlight = false)
            return
        }
        if (remote == null) {
            backupScheduler.enqueueBackup()
        } else {
            val label = parseExportedAtLabel(remote)
            _events.tryEmit(SettingsEvent.ShowRestorePrompt(label))
        }
        _uiState.value = _uiState.value.copy(isAuthInFlight = false)
    }

    fun onConfirmRestore() {
        viewModelScope.launch {
            val result = try {
                restoreUseCase()
            } catch (e: BackupVersionMismatch) {
                _events.tryEmit(SettingsEvent.ShowError(R.string.drive_remote_backup_too_new))
                return@launch
            }
            when (result) {
                is RestoreResult.Success -> _events.tryEmit(SettingsEvent.RestoreSucceeded)
                is RestoreResult.VersionMismatch ->
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_remote_backup_too_new))
                RestoreResult.NoRemoteBackup ->
                    _events.tryEmit(SettingsEvent.ShowError(R.string.drive_restore_failed))
            }
        }
    }

    fun onSkipRestore() {
        viewModelScope.launch { backupScheduler.enqueueBackup() }
    }

    private fun parseExportedAtLabel(remoteJson: String): String {
        return try {
            val parsed = BackupSerializer().fromJson(remoteJson)
            val instant = Instant.parse(parsed.exportedAt)
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (_: Throwable) {
            "an earlier date"
        }
    }
}
```

- [ ] **Step 5: Add the new strings (referenced by tests indirectly via the production code)**

Append to `app/src/main/res/values/strings.xml` (inside the existing `<resources>` element; do not duplicate existing keys):

```xml
<string name="drive_backup_title">Google Drive backup</string>
<string name="drive_backup_summary">Auto-sync your charges to a hidden folder on your Drive</string>
<string name="drive_last_backup_label">Last backup: %1$s</string>
<string name="drive_last_backup_never">Never</string>
<string name="drive_auth_failed">Couldn\'t sign in to Google Drive. Try again.</string>
<string name="drive_consent_cancelled">Drive backup not enabled — consent was cancelled.</string>
<string name="drive_network_error">No network connection. Backup will retry when you\'re online.</string>
<string name="drive_remote_backup_too_new">This backup was created by a newer version of EV Tracker. Update the app to restore.</string>
<string name="drive_restore_failed">Couldn\'t restore backup. Local data is unchanged.</string>
<string name="drive_restore_prompt_title">Restore from Drive backup?</string>
<string name="drive_restore_prompt_body">Found backup from %1$s. This will replace any data already on this device. Restore?</string>
<string name="drive_restore_prompt_confirm">Restore</string>
<string name="drive_restore_prompt_skip">Skip</string>
<string name="drive_restore_succeeded">Restored from Drive backup.</string>
<string name="settings_placeholder_row">Coming in next update</string>
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.settings.SettingsViewModelTest"
```

Expected: 7 tests, all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt app/src/main/res/values/strings.xml app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): SettingsViewModel with Drive toggle + restore orchestration"
```

---

## Task 15: `SettingsFragment` rewrite + layout

The minimal Drive-only Settings UI. Other rows render as inert disabled placeholders.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/layout/fragment_settings.xml`

- [ ] **Step 1: Rewrite the layout**

Replace the contents of `app/src/main/res/layout/fragment_settings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:title="@string/menu_settings" />
    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Drive backup section -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/drive_backup_title"
                android:textAppearance="?attr/textAppearanceTitleMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/drive_backup_summary"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:layout_marginTop="4dp" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_drive_enabled"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/drive_backup_title" />

            <TextView
                android:id="@+id/text_last_backup"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:textAppearance="?attr/textAppearanceBodySmall"
                tools:text="Last backup: just now"
                xmlns:tools="http://schemas.android.com/tools" />

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginTop="16dp"
                android:background="?android:attr/listDivider" />

            <!-- Placeholder rows for F. -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:enabled="false"
                android:text="Theme · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:enabled="false"
                android:text="Distance unit · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:enabled="false"
                android:text="Currency · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:enabled="false"
                android:text="Reset preferences · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:enabled="false"
                android:text="Export CSV · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:enabled="false"
                android:text="Manage locations · @string/settings_placeholder_row"
                android:textAppearance="?attr/textAppearanceBodyMedium" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

The placeholder rows hard-code their labels because they will be replaced wholesale in F; introducing string resources for labels we are about to delete is wasteful.

- [ ] **Step 2: Rewrite `SettingsFragment`**

Replace the contents of `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt`:

```kotlin
package org.spsl.evtracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.databinding.FragmentSettingsBinding

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchDriveEnabled.setOnCheckedChangeListener { _, checked ->
            viewModel.onToggleDrive(checked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (binding.switchDriveEnabled.isChecked != state.driveEnabled) {
                            binding.switchDriveEnabled.isChecked = state.driveEnabled
                        }
                        binding.textLastBackup.text = formatLastBackup(state.lastBackupAt)
                    }
                }
                launch {
                    viewModel.events.collect { event -> handleEvent(event) }
                }
            }
        }
    }

    private fun formatLastBackup(epochMs: Long?): String {
        return if (epochMs == null) {
            getString(R.string.drive_last_backup_label, getString(R.string.drive_last_backup_never))
        } else {
            val formatted = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMs))
            getString(R.string.drive_last_backup_label, formatted)
        }
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.LaunchConsent -> {
                consentLauncher.launch(IntentSenderRequest.Builder(event.intentSender).build())
            }
            is SettingsEvent.ShowRestorePrompt -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.drive_restore_prompt_title)
                    .setMessage(getString(R.string.drive_restore_prompt_body, event.backupDateLabel))
                    .setPositiveButton(R.string.drive_restore_prompt_confirm) { _, _ ->
                        viewModel.onConfirmRestore()
                    }
                    .setNegativeButton(R.string.drive_restore_prompt_skip) { _, _ ->
                        viewModel.onSkipRestore()
                    }
                    .show()
            }
            SettingsEvent.RestoreSucceeded -> {
                Snackbar.make(binding.root, R.string.drive_restore_succeeded, Snackbar.LENGTH_LONG).show()
            }
            is SettingsEvent.ShowError -> {
                Snackbar.make(binding.root, event.msgRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 3: Verify the build**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt app/src/main/res/layout/fragment_settings.xml
git commit -m "feat(settings): Drive-only SettingsFragment with consent + restore UI"
```

---

## Task 16: Wire `SettingsFragment` into the navigation graph

The placeholder `settingsFragment` destination already exists in `nav_graph.xml` from A; verify it points at the right class and the bottom-nav item ID matches. No new arguments needed for E.

**Files:**
- Verify (likely no change): `app/src/main/res/navigation/nav_graph.xml`
- Verify (likely no change): `app/src/main/res/menu/bottom_nav.xml`

- [ ] **Step 1: Confirm `settingsFragment` destination is wired correctly**

Open `app/src/main/res/navigation/nav_graph.xml` and confirm a `<fragment android:id="@+id/settingsFragment" android:name="org.spsl.evtracker.ui.settings.SettingsFragment" .../>` block exists. (D's plan added it.) If missing, add:

```xml
<fragment
    android:id="@+id/settingsFragment"
    android:name="org.spsl.evtracker.ui.settings.SettingsFragment"
    android:label="@string/menu_settings"
    tools:layout="@layout/fragment_settings" />
```

- [ ] **Step 2: Confirm bottom-nav menu maps to `settingsFragment`**

Open `app/src/main/res/menu/bottom_nav.xml` and confirm one item has `android:id="@+id/settingsFragment"`. If the IDs are different (e.g. `@+id/settings`), the destination ID in the nav graph must match the menu item ID for `setupWithNavController` to wire correctly.

- [ ] **Step 3: Verify the build still installs and the Settings tab shows the new UI**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit (if any changes were necessary)**

If any nav graph or menu changes were needed:

```bash
git add app/src/main/res/navigation/nav_graph.xml app/src/main/res/menu/bottom_nav.xml
git commit -m "fix(nav): align Settings destination ID with bottom nav"
```

If no changes were necessary, skip the commit and proceed to Task 17.

---

## Task 17: Extend `RestoreBackupUseCaseTest` with Drive round-trip cases

C's `RestoreBackupUseCaseTest` already has 6 cases that use a `FakeBackupRepository` with raw JSON strings. Add 3 more cases that go through a real `BackupSerializer` round-trip — verifying that the integration of `DriveBackupRepository` shape (write JSON via serializer, read back JSON, parse) is end-to-end correct.

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt`

- [ ] **Step 1: Add the 3 new tests**

Append the following to `RestoreBackupUseCaseTest.kt` inside the existing class body (do NOT duplicate the imports — add only what's missing):

Add these imports if not present:

```kotlin
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.testing.FakeDriveRemoteSource
```

Then append the 3 test methods (placement: end of the class, before the closing brace):

```kotlin
@Test
fun restore_endToEnd_throughBackupSerializerRoundTrip_succeeds() = runTest {
    val car = CarEntity(1, "Tesla", "Tesla", "M3", 2024, 60.0, 100L)
    val event = ChargeEventEntity(1, 1, 1714000000000L, 12345.0, 22.4, "AC", null, null, null, null, "", 100L)
    val location = CustomLocationEntity(1, "Home", 5, 1714000000000L)
    val snapshot = BackupData.fromEntities(listOf(car), listOf(event), listOf(location))
    val json = BackupSerializer().toJson(snapshot)
    val backupRepository = FakeBackupRepository(remoteJson = json)
    val transactionRunner = FakeRestoreTransactionRunner()
    val scheduler = FakeBackupScheduler()
    val writer = FakeSettingsWriter()
    val useCase = RestoreBackupUseCase(
        backupRepository = backupRepository,
        backupSerializer = BackupSerializer(),
        transactionRunner = transactionRunner,
        snapshotWriter = FakeRestoreSnapshotWriter(),
        carReader = FakeCarReader(),
        chargeEventQueries = FakeChargeEventQueries(MutableStateFlow(emptyList())),
        locationReader = FakeLocationReader(),
        settingsWriter = writer,
        backupScheduler = scheduler
    )

    val result = useCase()

    assertTrue(result is RestoreResult.Success)
    val success = result as RestoreResult.Success
    assertEquals(1, success.carCount)
    assertEquals(1, success.eventCount)
    assertEquals(1, success.locationCount)
    assertTrue(writer.driveEnabled())
    assertEquals(1, scheduler.enqueueCount)
}

@Test
fun restore_versionMismatch_returnsVersionMismatchAndDoesNotMutate() = runTest {
    // Hand-craft a payload with backup_version=2 to simulate an older app's snapshot.
    val oldJson = """
        {"backup_version":2,"exported_at":"2026-01-01T00:00:00Z","cars":[],"charge_events":[],"custom_locations":[]}
    """.trimIndent()
    val backupRepository = FakeBackupRepository(remoteJson = oldJson)
    val transactionRunner = FakeRestoreTransactionRunner()
    val scheduler = FakeBackupScheduler()
    val useCase = RestoreBackupUseCase(
        backupRepository = backupRepository,
        backupSerializer = BackupSerializer(),
        transactionRunner = transactionRunner,
        snapshotWriter = FakeRestoreSnapshotWriter(),
        carReader = FakeCarReader(),
        chargeEventQueries = FakeChargeEventQueries(MutableStateFlow(emptyList())),
        locationReader = FakeLocationReader(),
        settingsWriter = FakeSettingsWriter(),
        backupScheduler = scheduler
    )
    val result = useCase()
    assertTrue(result is RestoreResult.VersionMismatch)
    assertEquals(2, (result as RestoreResult.VersionMismatch).actualVersion)
    assertEquals(0, scheduler.enqueueCount)
    assertEquals(null, transactionRunner.lastCars)  // no replaceAll call
}

@Test
fun restore_alwaysCallsEnqueueBackupAfterSuccess() = runTest {
    val snapshot = BackupData.fromEntities(emptyList(), emptyList(), emptyList())
    val json = BackupSerializer().toJson(snapshot)
    val scheduler = FakeBackupScheduler()
    val useCase = RestoreBackupUseCase(
        backupRepository = FakeBackupRepository(remoteJson = json),
        backupSerializer = BackupSerializer(),
        transactionRunner = FakeRestoreTransactionRunner(),
        snapshotWriter = FakeRestoreSnapshotWriter(),
        carReader = FakeCarReader(),
        chargeEventQueries = FakeChargeEventQueries(MutableStateFlow(emptyList())),
        locationReader = FakeLocationReader(),
        settingsWriter = FakeSettingsWriter(),
        backupScheduler = scheduler
    )
    useCase()
    assertEquals(1, scheduler.enqueueCount)
}
```

- [ ] **Step 2: Verify the test passes**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.RestoreBackupUseCaseTest"
```

Expected: 9 tests (6 existing + 3 new), all PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt
git commit -m "test(backup): extend RestoreBackupUseCaseTest with serializer round-trips"
```

---

## Task 18: Instrumented `DriveBackupWorkerTest` + final verification

Instrumented test verifies the `@HiltWorker` wiring is correct end-to-end through `HiltWorkerFactory` + `TestListenableWorkerBuilder`. Compile-only verification in this environment; running needs an emulator with API 26+ and Google Play services.

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt`
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/backup/TestBackupModule.kt`

- [ ] **Step 1: Create the test module that overrides production bindings**

Create `app/src/androidTest/java/org/spsl/evtracker/data/backup/TestBackupModule.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import org.spsl.evtracker.di.BackupModule
import org.spsl.evtracker.di.SingletonBackupModule
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource

class TestDriveAuthManager @javax.inject.Inject constructor() : DriveAuthManager {
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("test-token")
    override suspend fun authorize(): DriveAuthManager.AuthResult = nextResult
    override suspend fun silentToken(): DriveAuthManager.AuthResult =
        if (nextResult is DriveAuthManager.AuthResult.NeedsResolution)
            DriveAuthManager.AuthResult.Failed("consent")
        else nextResult
}

class TestDriveRemoteSource @javax.inject.Inject constructor() : DriveRemoteSource {
    @Volatile var fileId: String? = null
    @Volatile var body: ByteArray? = null
    @Volatile var failOnNext: Throwable? = null

    override suspend fun findBackupFileId(accessToken: String): String? {
        failOnNext?.let { failOnNext = null; throw it }
        return fileId
    }
    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String {
        failOnNext?.let { failOnNext = null; throw it }
        fileId = "test-file"; body = jsonBytes; return fileId!!
    }
    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        failOnNext?.let { failOnNext = null; throw it }
        body = jsonBytes
    }
    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        failOnNext?.let { failOnNext = null; throw it }
        return body ?: throw java.io.IOException("no body")
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SingletonBackupModule::class, BackupModule::class])
abstract class TestBackupModule {
    @Binds @Singleton abstract fun bindDriveAuthManager(impl: TestDriveAuthManager): DriveAuthManager
    @Binds @Singleton abstract fun bindDriveRemoteSource(impl: TestDriveRemoteSource): DriveRemoteSource
}
```

- [ ] **Step 2: Create the worker test**

Create `app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DriveBackupWorkerTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var auth: DriveAuthManager
    @Inject lateinit var remote: TestDriveRemoteSource

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun doWork_happyPath_returnsSuccess() = runBlocking {
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_authRequired_returnsFailure() = runBlocking {
        (auth as TestDriveAuthManager).nextResult = DriveAuthManager.AuthResult.Failed("revoked")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_networkError_returnsRetry() = runBlocking {
        remote.failOnNext = java.io.IOException("network down")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
```

- [ ] **Step 3: Verify the instrumented test compiles**

Run:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. Running on a connected emulator (`./gradlew :app:connectedDebugAndroidTest`) is the gate before merging; this environment's sandbox does not have an emulator attached.

- [ ] **Step 4: Final full-suite verification**

Run the complete unit-test suite plus the debug build:

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on all three. JVM unit tests count = ~143.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt app/src/androidTest/java/org/spsl/evtracker/data/backup/TestBackupModule.kt
git commit -m "test(backup): instrumented DriveBackupWorkerTest with HiltWorkerFactory"
```

- [ ] **Step 6: Push the branch**

```bash
git push -u origin feat/drive-backup-sub-project-e
```

---

## Plan self-review

### Spec coverage

- §1 Context — Task 1 (deps), Task 2 (settings extensions), Task 3 (suspend conversion). ✓
- §2 Scope — every In-scope item maps to a task: deps (1), settings flows (2), suspend (3), DriveAuthManager (4, 8, 12), DriveRemoteSource (5, 9, 12), DriveBackupRepository (7, 12), WorkManagerBackupScheduler (6, 12), DriveBackupWorker (6 stub, 10 real, 11 wiring, 18 test), Settings UI (14, 15, 16), no-op deletion (13). ✓
- §3 Architecture — file map matches Task list 1-to-1. ✓
- §4 Components — every named class has its own task. ✓
- §5 Sequences — VM tests in Task 14 cover §5.1 (toggle ON, no remote), §5.2 (remote exists), §5.5 (toggle OFF). §5.3 (consent required) tested at the auth-emit-event level in Task 14. §5.4 (auto-backup after charge save) is covered by Task 6 + the existing `SaveChargeEventUseCaseTest`. ✓
- §6 Error handling — Task 14 covers VM-level errors; Task 10 codes the worker error matrix; Task 18 tests the worker error matrix end-to-end. ✓
- §7 Testing — JVM tests added in Tasks 6, 7, 14, 17 totaling 20; instrumented test in Task 18. ✓
- §8 Risk and rollout — out of plan scope (operational concern). ✓

### Placeholder scan

- No "TBD" / "TODO" / "implement later" anywhere.
- All test bodies are complete code blocks.
- All commands include expected output.

### Type consistency

- `BackupScheduler.enqueueBackup` is `suspend fun` from Task 3 onward; all callers re-checked.
- `DriveAuthManager.AuthResult` cases (`Success`, `NeedsResolution`, `Failed`) named consistently across tasks 4, 8, 14, 18.
- `DriveRemoteSource` method names (`findBackupFileId`, `createBackup`, `updateBackup`, `downloadBackup`) consistent across tasks 5, 7, 9, 18.
- `WorkManagerBackupScheduler.UNIQUE_NAME` constant referenced from Task 6 and Task 14.
- `Clock` interface defined in Task 10, provided in Task 11 — consistent.
- `SettingsUiState` and `SettingsEvent` defined in Task 14, used only there + in Task 15 — consistent.
