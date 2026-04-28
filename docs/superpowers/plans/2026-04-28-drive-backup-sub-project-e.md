# Sub-project E — Drive Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the no-op Drive-backup seam landed in C with a working Authorization-API + Drive-REST + WorkManager pipeline plus the smallest Settings UI required to drive it.

**Architecture:** Two new domain interfaces (`DriveAuthManager`, `DriveRemoteSource`) front the platform code. `DriveBackupRepository` composes them with the existing `BackupSerializer` and entity readers. `WorkManagerBackupScheduler` gates on `driveEnabled` and enqueues a unique `OneTimeWorkRequest<DriveBackupWorker>` (CONNECTED, exp-backoff 30 s, initial delay 5 s for debounce). `BackupScheduler.enqueueBackup()` becomes `suspend`. `EVTrackerApp` implements `Configuration.Provider` so Hilt-injected workers work. `SettingsFragment` owns the auth Activity launcher; `SettingsViewModel` is a pure state machine (no `IntentSender`, no `DriveAuthManager` dependency).

**Tech Stack:** Kotlin / Hilt 2.50 / KSP / Room 2.6 / DataStore Preferences / Coroutines 1.7 / WorkManager 2.9 / `androidx.hilt:hilt-work` 1.1 / `play-services-auth` 21.2 (Authorization API) / `google-api-services-drive` v3 / Gson / Material 3.

**Reference docs:** `docs/superpowers/specs/2026-04-28-drive-backup-design.md` (Sub-project E spec — every section number cited below references that file unless noted otherwise).

**Build commands** (sandbox quirk: gradle's default `~/.gradle` is read-only, always set `GRADLE_USER_HOME`):

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest    # compile-only; running needs an emulator
```

---

## File map

### Created
- `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt` — interface, `AuthResult` sealed class, `DriveAuthRequiredException`
- `app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt` — interface
- `app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt` — `@Singleton` Authorization-API impl
- `app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt` — `@Singleton` Drive-REST impl
- `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt` — replaces `NoOpBackupRepository`
- `app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt` — replaces `NoOpBackupScheduler`
- `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt` — `@HiltWorker CoroutineWorker`
- `app/src/main/java/org/spsl/evtracker/di/BackupModule.kt` — binds `DriveAuthManager`, `DriveRemoteSource`
- `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt` — provides `WorkManager`, `Clock`
- `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt` — `SettingsUiState`, `SettingsEvent`
- `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt` — 6 tests
- `app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt` — 4 tests
- `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt` — 9 tests
- `app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt` — instrumented happy/retry/failure

### Modified
- `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt` — `suspend fun enqueueBackup()`
- `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt` — `override suspend fun` (kept until Task 13 deletes it)
- `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt` — `driveEnabled`, `lastBackupAt`
- `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt` — `setLastBackupAt`
- `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` — implement new accessors
- `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt` — `LAST_BACKUP_AT`
- `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt` — `Configuration.Provider`
- `app/src/main/AndroidManifest.xml` — `xmlns:tools`, `tools:node="remove"` for `WorkManagerInitializer`
- `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt` — rebind scheduler+repository to real impls
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt` — full rewrite (state machine)
- `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt` — owns `DriveAuthManager` + `ActivityResultLauncher`
- `app/src/main/res/layout/fragment_settings.xml` — Drive section + placeholder rows
- `app/src/main/res/values/strings.xml` — Drive UI strings + error messages + restore dialog
- `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` — `FakeBackupScheduler.enqueueBackup` becomes suspend; `FakeSettingsReader` adds `driveEnabled`/`lastBackupAt`; `FakeSettingsWriter` adds `setLastBackupAt`; new `FakeDriveAuthManager`, `FakeDriveRemoteSource`
- `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt` — 3 new cases
- `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt` — 3 new cases (round-trip via Drive path)

### Deleted
- `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt`

> Build deps (`androidx-hilt-work`, `androidx-hilt-compiler`, `play-services-auth` 21.2, `google-api-services-drive`, `androidx-work-testing`) are **already** in `gradle/libs.versions.toml` and `app/build.gradle.kts` (verified at HEAD `579b865`). No Gradle changes are part of this plan.

---

## Task ordering rationale

1. Tasks 1–2 establish a green baseline and convert `BackupScheduler.enqueueBackup` to `suspend` first — every later task depends on the new signature.
2. Tasks 3–6 add the new domain seams and test fakes so subsequent TDD has the types it needs to instantiate.
3. Tasks 7–9 build the data-layer pieces (repository → scheduler → worker) bottom-up. Each is testable on the JVM (worker via instrumented test in Task 19).
4. Task 10 wires WorkManager into Hilt — gated until after the worker class exists, otherwise the manifest change breaks the build.
5. Tasks 11–12 add real Auth/Drive-REST implementations (no JVM tests; covered by manual smoke + the instrumented worker test).
6. Task 13 swaps DI bindings and deletes the no-ops — gated until both real impls exist.
7. Tasks 14–17 build the Settings UI on top of the now-real backend.
8. Tasks 18–19 add cross-cutting tests.
9. Task 20 is the final verification.

---

## Task 1: Branch baseline + green-build verification

**Goal:** Confirm we're on the expected branch with a clean baseline before any change.

**Files:**
- Read-only — `app/build.gradle.kts`, `gradle/libs.versions.toml`, current git HEAD

- [ ] **Step 1: Confirm working tree state**

```bash
git -C /home/apetros/OneDriveCUT/Code/EV-android-app status --short
git -C /home/apetros/OneDriveCUT/Code/EV-android-app log -1 --oneline
git -C /home/apetros/OneDriveCUT/Code/EV-android-app rev-parse --abbrev-ref HEAD
```

Expected:
- branch `feat/drive-backup-sub-project-e`
- HEAD at or descending from `579b865`
- working tree may have minor edits in `app/build.gradle.kts` and `gradle/libs.versions.toml` (the spec confirms these were already updated for E)

If the branch differs, stop and surface the discrepancy.

- [ ] **Step 2: Run JVM unit tests as a baseline**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with ~123 tests passing. If anything is failing, stop and fix the unrelated regression first — do not pile new work on a red baseline.

- [ ] **Step 3: Compile instrumented sources**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. (Running the suite needs an emulator and is not gated here.)

- [ ] **Step 4: No commit**

This task is verification only. Proceed to Task 2.

---

## Task 2: Convert `BackupScheduler.enqueueBackup()` to `suspend`

**Goal:** Single-shot rename so all later work compiles. Per spec §1.3, all 7 production callers are already `suspend`; the change is mechanical for callers, and the interface, the no-op, and the fake have to mirror the new signature.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt:112-116` (`FakeBackupScheduler`)

- [ ] **Step 1: Update the interface**

Replace the body of `BackupScheduler.kt`:

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Requests a backup of current local state.
 *
 * **Contract:** implementations own the `driveEnabled` gate. If Drive backup is disabled,
 * the implementation MUST no-op rather than schedule a Worker. Use cases call [enqueueBackup]
 * unconditionally after every persisted state change — they do NOT read `driveEnabled` themselves.
 *
 * Suspending so implementations can read DataStore for the gate without blocking.
 *
 * Bindings (E):
 * - [org.spsl.evtracker.data.backup.WorkManagerBackupScheduler] reads
 *   `SettingsReader.driveEnabled` and either enqueues a `OneTimeWorkRequest` or no-ops.
 */
interface BackupScheduler {
    suspend fun enqueueBackup()
}
```

- [ ] **Step 2: Update the no-op (still bound until Task 13)**

Replace the body of `NoOpBackupScheduler.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.BackupScheduler

@Singleton
class NoOpBackupScheduler @Inject constructor() : BackupScheduler {
    override suspend fun enqueueBackup() {
        // No-op until Task 13 swaps the binding to WorkManagerBackupScheduler.
    }
}
```

- [ ] **Step 3: Update the fake**

In `Fakes.kt`, replace `FakeBackupScheduler`:

```kotlin
class FakeBackupScheduler : BackupScheduler {
    var enqueueCount: Int = 0
        private set
    override suspend fun enqueueBackup() { enqueueCount++ }
}
```

- [ ] **Step 4: Verify the rest of the codebase still compiles + tests pass**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with ~123 tests still green. The 7 production callers (`SaveChargeEventUseCase:60`, `DeleteChargeEventUseCase:14`, `AddCarUseCase:33`, `RenameCarUseCase:14`, `DeleteCarUseCase:24`, `RestoreBackupUseCase:33` and `:53`) and their tests already run inside `suspend` / `runTest` blocks, so no caller changes are required. If the compiler points at a non-suspend call site, fix that file (likely test code) and re-run.

- [ ] **Step 5: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/domain/backup/BackupScheduler.kt \
        app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt \
        app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(E): make BackupScheduler.enqueueBackup() suspend"
```

---

## Task 3: Add domain seams — `DriveAuthManager`, `DriveRemoteSource`, `DriveAuthRequiredException`

**Goal:** Pure-interface task. Lays the contracts that Tasks 7/8/9/11/12 implement, with the `AuthResult` sealed class and the `DriveAuthRequiredException` exception used at the repository boundary (spec §4.1, §4.2, §4.3).

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt`
- Create: `app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt`

- [ ] **Step 1: Write `DriveAuthManager.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

import android.content.IntentSender
import java.io.IOException

/**
 * Resolves an OAuth2 access token for the `drive.appdata` scope.
 *
 * Two entry points:
 * - [authorize] — used by SettingsFragment when the user toggles Drive ON. May return
 *   [AuthResult.NeedsResolution] so the Fragment can launch the consent IntentSender.
 * - [silentToken] — used by DriveBackupRepository (and therefore the Worker). Collapses
 *   NeedsResolution into Failed because the worker has no Activity to resolve from.
 */
interface DriveAuthManager {

    /** Result of an authorization attempt. */
    sealed class AuthResult {
        data class Success(val accessToken: String) : AuthResult()
        data class NeedsResolution(val intentSender: IntentSender) : AuthResult()
        data class Failed(val reason: String, val cause: Throwable? = null) : AuthResult()
    }

    suspend fun authorize(): AuthResult

    /** Like [authorize] but never returns NeedsResolution. NeedsResolution → Failed. */
    suspend fun silentToken(): AuthResult
}

/** Thrown by [DriveAuthManager.silentToken] callers when consent is required or revoked. */
class DriveAuthRequiredException(
    message: String = "Drive consent required or revoked",
    cause: Throwable? = null
) : IOException(message, cause)
```

- [ ] **Step 2: Write `DriveRemoteSource.kt`**

```kotlin
package org.spsl.evtracker.domain.backup

/**
 * Drive REST seam. All methods take an explicit [accessToken] because the auth manager
 * refreshes tokens per-call (tokens expire). Implementations execute on Dispatchers.IO.
 *
 * The "appDataFolder" space is hidden from the regular Drive UI but accessible via
 * `files.list?spaces=appDataFolder`.
 */
interface DriveRemoteSource {
    /** Returns the Drive fileId of the existing `evtracker_backup.json` in App Data, or null. */
    suspend fun findBackupFileId(accessToken: String): String?

    /** Creates a new `evtracker_backup.json` in App Data. Returns the new fileId. */
    suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String

    /** Replaces the body of an existing fileId. */
    suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray)

    /** Downloads the body of fileId. */
    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray
}
```

- [ ] **Step 3: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. No tests for interfaces themselves.

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/domain/backup/DriveAuthManager.kt \
        app/src/main/java/org/spsl/evtracker/domain/backup/DriveRemoteSource.kt
git commit -m "feat(E): add DriveAuthManager and DriveRemoteSource domain seams"
```

---

## Task 4: Extend `SettingsReader` and `SettingsWriter`

**Goal:** Add `driveEnabled: Flow<Boolean>` and `lastBackupAt: Flow<Long?>` to the reader, `setLastBackupAt` to the writer (spec §4.8). The `SettingsRepository` impl is updated in Task 5; this task only widens the interfaces and updates the fakes so the build stays green.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` (provisional impl so the override is satisfied)
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` — `FakeSettingsReader`, `FakeSettingsWriter`

- [ ] **Step 1: Update `SettingsReader.kt`**

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
}
```

- [ ] **Step 2: Update `SettingsWriter.kt`**

```kotlin
package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
    suspend fun setLastBackupAt(epochMs: Long)
}
```

- [ ] **Step 3: Update `SettingsRepository.kt` to satisfy both interfaces**

In `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`, add the four new members (the `LAST_BACKUP_AT` preference key is added in Task 5; for now, key it directly via `longPreferencesKey("lastBackupAt")` inline so this task compiles standalone — Task 5 swaps it to the named constant):

Inside the class, after the existing `currency` flow, add:

```kotlin
    override val driveEnabled: Flow<Boolean> =
        dataStore.data.map { it[PreferenceKeys.DRIVE_ENABLED] ?: false }

    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[androidx.datastore.preferences.core.longPreferencesKey("lastBackupAt")] }
```

After the existing `setDriveEnabled` writer, add:

```kotlin
    override suspend fun setLastBackupAt(epochMs: Long) {
        dataStore.edit {
            it[androidx.datastore.preferences.core.longPreferencesKey("lastBackupAt")] = epochMs
        }
    }
```

(Task 5 replaces both inline keys with the canonical `PreferenceKeys.LAST_BACKUP_AT` constant.)

- [ ] **Step 4: Update fakes in `Fakes.kt`**

Replace `FakeSettingsReader`:

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
```

Replace `FakeSettingsWriter`:

```kotlin
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
```

- [ ] **Step 5: Run all JVM tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with ~123 tests still green. Existing callers of `FakeSettingsReader` constructors with positional or named arguments continue to work because all new params have defaults.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt \
        app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt \
        app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "feat(E): widen Settings interfaces with driveEnabled and lastBackupAt"
```

---

## Task 5: `LAST_BACKUP_AT` preference key + `SettingsRepository` round-trip tests

**Goal:** Replace the inline `longPreferencesKey("lastBackupAt")` from Task 4 with a named constant in `PreferenceKeys`, and add three round-trip tests covering the new accessors (spec §4.8).

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests first**

Append to `SettingsRepositoryTest.kt` inside the existing class:

```kotlin
    @Test
    fun driveEnabled_defaultsFalse_andRoundTrips() = runTest {
        assertFalse(repo.driveEnabled.first())
        repo.setDriveEnabled(true)
        assertTrue(repo.driveEnabled.first())
        repo.setDriveEnabled(false)
        assertFalse(repo.driveEnabled.first())
    }

    @Test
    fun lastBackupAt_defaultsNull_andRoundTrips() = runTest {
        assertEquals(null, repo.lastBackupAt.first())
        repo.setLastBackupAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, repo.lastBackupAt.first())
    }

    @Test
    fun setLastBackupAt_doesNotAffectDriveEnabled() = runTest {
        repo.setLastBackupAt(42L)
        assertFalse(repo.driveEnabled.first())
        assertEquals(42L, repo.lastBackupAt.first())
    }
```

- [ ] **Step 2: Run them — should fail**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.repository.SettingsRepositoryTest"
```

The driveEnabled and lastBackupAt round-trips actually pass against Task 4's inline-key impl. Treat that as a sanity check; the change in this task is structural (named constant). Expected: all tests pass — Task 4's impl already round-trips correctly. If a test fails, fix the inline impl first.

- [ ] **Step 3: Add the named preference key**

In `PreferenceKeys.kt`, add at the end of the object:

```kotlin
    val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt") // consumed by Sub-project E
```

Add the import line at the top:

```kotlin
import androidx.datastore.preferences.core.longPreferencesKey
```

- [ ] **Step 4: Replace the inline key in `SettingsRepository.kt`**

Change the two `androidx.datastore.preferences.core.longPreferencesKey("lastBackupAt")` references introduced in Task 4 to `PreferenceKeys.LAST_BACKUP_AT`:

```kotlin
    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[PreferenceKeys.LAST_BACKUP_AT] }

    override suspend fun setLastBackupAt(epochMs: Long) {
        dataStore.edit { it[PreferenceKeys.LAST_BACKUP_AT] = epochMs }
    }
```

- [ ] **Step 5: Re-run tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. The 3 new tests count against the suite's growth toward ~143.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/preferences/PreferenceKeys.kt \
        app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt \
        app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt
git commit -m "feat(E): add LAST_BACKUP_AT preference key + repository tests"
```

---

## Task 6: Test-only fakes — `FakeDriveAuthManager`, `FakeDriveRemoteSource`

**Goal:** Add the two fakes that Tasks 7–8 and 18–19 will use. Spec §7.3 specifies the contract verbatim; we paste it.

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] **Step 1: Append the fakes to `Fakes.kt`**

Add the imports near the top of the file:

```kotlin
import java.io.IOException
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource
```

Append at the bottom of the file:

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

class FakeDriveRemoteSource : DriveRemoteSource {
    private var fileId: String? = null
    private var body: ByteArray? = null
    var failNext: Throwable? = null

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

    fun seed(jsonBytes: ByteArray) { fileId = "fake-file-id"; body = jsonBytes }
    fun lastUploadedBytes(): ByteArray? = body
    fun seededFileId(): String? = fileId

    private fun consumeFailure() {
        val e = failNext ?: return
        failNext = null
        throw e
    }
}
```

- [ ] **Step 2: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. No new tests yet; the fakes are exercised in Task 7+.

- [ ] **Step 3: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "test(E): add FakeDriveAuthManager and FakeDriveRemoteSource"
```

---

## Task 7: `DriveBackupRepository` — TDD

**Goal:** Implement the real `BackupRepository` (spec §4.3). It composes `BackupSerializer` + `DriveAuthManager.silentToken()` + `DriveRemoteSource`, and translates auth-class HTTP errors at the boundary so the Worker only sees `DriveAuthRequiredException` vs plain `IOException`. Six JVM tests.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`

- [ ] **Step 1: Write the failing test class**

`app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import com.google.api.client.http.HttpResponseException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

    private val serializer = BackupSerializer()

    private fun build(
        cars: List<CarEntity> = emptyList(),
        events: List<ChargeEventEntity> = emptyList(),
        locations: List<CustomLocationEntity> = emptyList(),
        auth: FakeDriveAuthManager = FakeDriveAuthManager(),
        remote: FakeDriveRemoteSource = FakeDriveRemoteSource()
    ): Setup {
        val carReader = FakeCarReader(cars)
        val queries = FakeChargeEventQueries().also { it.seed(events) }
        val locReader = FakeLocationReader(locations)
        val repo = DriveBackupRepository(auth, remote, serializer, carReader, queries, locReader)
        return Setup(repo, auth, remote)
    }

    private data class Setup(
        val repo: DriveBackupRepository,
        val auth: FakeDriveAuthManager,
        val remote: FakeDriveRemoteSource
    )

    @Test
    fun backup_noExistingFile_callsCreate() = runTest {
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.repo.backupCurrentData()
        assertNotNull(s.remote.lastUploadedBytes())
        assertEquals("fake-file-id", s.remote.seededFileId())
    }

    @Test
    fun backup_existingFile_callsUpdate() = runTest {
        val seed = serializer.toJson(BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L))
        val s = build(cars = listOf(CarEntity(id = 1, name = "T", createdAt = 0L)))
        s.remote.seed(seed.toByteArray(Charsets.UTF_8))
        s.repo.backupCurrentData()
        // Same fileId retained — update path.
        assertEquals("fake-file-id", s.remote.seededFileId())
        // The body was overwritten with current state (one car).
        val parsed = serializer.fromJson(s.remote.lastUploadedBytes()!!.toString(Charsets.UTF_8))
        assertEquals(1, parsed.cars.size)
    }

    @Test
    fun backup_serializerRoundTripPreservesAllFields() = runTest {
        val car = CarEntity(id = 1, name = "T", createdAt = 5L)
        val event = ChargeEventEntity(
            id = 7, carId = 1, eventDate = 1L,
            odometerKm = 100.0, kwhAdded = 10.0, chargeType = "DC",
            costTotal = 5.0, costPerKwh = 0.5, currency = "EUR",
            location = "Home", note = "n"
        )
        val loc = CustomLocationEntity(id = 1, label = "Home", useCount = 1, lastUsed = 9L)
        val s = build(cars = listOf(car), events = listOf(event), locations = listOf(loc))
        s.repo.backupCurrentData()
        val parsed = serializer.fromJson(s.remote.lastUploadedBytes()!!.toString(Charsets.UTF_8))
        assertEquals(listOf(car), parsed.cars.map { it.toEntity() })
        assertEquals(listOf(event), parsed.chargeEvents.map { it.toEntity() })
        assertEquals(listOf(loc), parsed.customLocations.map { it.toEntity() })
    }

    @Test
    fun backup_silentTokenFailed_throwsDriveAuthRequired() = runTest {
        val auth = FakeDriveAuthManager(nextResult = DriveAuthManager.AuthResult.Failed("revoked"))
        val s = build(auth = auth)
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }

    @Test
    fun read_noFileId_returnsNull() = runTest {
        val s = build()
        assertNull(s.repo.readRemoteBackup())
    }

    @Test
    fun read_existingFile_returnsBody() = runTest {
        val s = build()
        s.remote.seed("hello".toByteArray(Charsets.UTF_8))
        assertEquals("hello", s.repo.readRemoteBackup())
    }

    @Test
    fun backup_drive401_translatesToDriveAuthRequired() = runTest {
        val s = build()
        s.remote.failNext = HttpResponseException
            .Builder(401, "Unauthorized", com.google.api.client.http.HttpHeaders())
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected DriveAuthRequiredException")
        } catch (_: DriveAuthRequiredException) {
            // ok
        }
    }

    @Test
    fun backup_drive403QuotaExceeded_propagatesAsIOException() = runTest {
        val s = build()
        val body = """{"error":{"errors":[{"reason":"quotaExceeded"}],"code":403}}"""
        s.remote.failNext = HttpResponseException
            .Builder(403, "Forbidden", com.google.api.client.http.HttpHeaders())
            .setContent(body)
            .build()
        try {
            s.repo.backupCurrentData()
            fail("expected IOException, not DriveAuthRequiredException")
        } catch (e: DriveAuthRequiredException) {
            fail("403 quotaExceeded must NOT translate to auth: $e")
        } catch (_: IOException) {
            // ok — Worker will retry
        }
    }
}
```

> Note: spec §7.1 lists six DriveBackupRepository tests; this file adds two more (the 401 and 403-quota cases) so the §6.1 error matrix is regression-tested at the JVM layer. They round up the JVM count toward the spec's ~143 target.

- [ ] **Step 2: Run — should fail to compile (`DriveBackupRepository` does not exist yet)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.DriveBackupRepositoryTest"
```

Expected: compilation error referencing `DriveBackupRepository`.

- [ ] **Step 3: Implement the repository**

Create `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import com.google.api.client.http.HttpResponseException
import com.google.gson.JsonParser
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
        val json = serializer.toJson(BackupData.fromEntities(cars, events, locations))
        val bytes = json.toByteArray(Charsets.UTF_8)
        runTranslating {
            val existing = remote.findBackupFileId(token)
            if (existing == null) remote.createBackup(token, bytes)
            else remote.updateBackup(token, existing, bytes)
        }
    }

    override suspend fun readRemoteBackup(): String? = runTranslating {
        val token = requireToken()
        val fileId = remote.findBackupFileId(token) ?: return@runTranslating null
        remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }

    private suspend fun requireToken(): String =
        when (val r = auth.silentToken()) {
            is DriveAuthManager.AuthResult.Success -> r.accessToken
            else -> throw DriveAuthRequiredException()
        }

    /**
     * Translate Drive HTTP errors at the boundary:
     * - 401 → always auth (token expired/invalid).
     * - 403 with auth reason (`appNotAuthorized`, `insufficientFilePermissions`,
     *   `insufficientPermissions`, `forbidden`) → auth.
     * - 403 with quota/rate reason or unparseable body but not in AUTH_REASONS → IOException
     *   so the Worker retries with exponential backoff.
     * - Unknown reason on 403 → conservative: treat as auth.
     * - Anything else (5xx, transport IOException) propagates unchanged.
     *
     * Result: Worker error rule stays two-branch — DriveAuthRequiredException → failure;
     * anything else IOException → retry.
     */
    private inline fun <T> runTranslating(block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        when {
            e.statusCode == 401 -> throw DriveAuthRequiredException(cause = e)
            e.statusCode == 403 && isAuthReason(e) -> throw DriveAuthRequiredException(cause = e)
            else -> throw e
        }
    }

    private fun isAuthReason(e: HttpResponseException): Boolean {
        val reason = parseFirstErrorReason(e.content)
        // Quota/rate-limit reasons are explicitly NOT auth — they retry.
        if (reason != null && reason in QUOTA_REASONS) return false
        if (reason != null && reason in AUTH_REASONS) return true
        // Unknown / unparseable → conservative auth treatment.
        return reason !in QUOTA_REASONS
    }

    private fun parseFirstErrorReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonObject("error")
                ?.getAsJsonArray("errors")
                ?.firstOrNull()?.asJsonObject
                ?.get("reason")?.asString
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val AUTH_REASONS = setOf(
            "appNotAuthorized",
            "insufficientFilePermissions",
            "insufficientPermissions",
            "forbidden"
        )
        private val QUOTA_REASONS = setOf(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "quotaExceeded"
        )
    }
}
```

- [ ] **Step 4: Re-run tests — should pass**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.backup.DriveBackupRepositoryTest"
```

Expected: 8 tests pass (6 from spec §7.1 + 2 boundary cases).

- [ ] **Step 5: Run the full suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with the existing tests still green.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt \
        app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt
git commit -m "feat(E): DriveBackupRepository with auth-error translation"
```

---

## Task 8: `WorkManagerBackupScheduler` — TDD

**Goal:** Implement the scheduler that gates on `driveEnabled` and enqueues a unique `OneTimeWorkRequest<DriveBackupWorker>` (spec §4.4). Four JVM tests via `WorkManagerTestInitHelper`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt`

> Note: tests reference `DriveBackupWorker` only by class literal. The class itself is created in Task 9; we add a temporary typed reference now and let the test compile by referencing the worker class directly. If the test fails to compile in Step 2, briefly stub out `DriveBackupWorker` as a placeholder `CoroutineWorker` (no body) — Task 9 fills it in.

- [ ] **Step 1: Create the test directory and file (instrumented)**

The existing test classpath has no Robolectric, and `WorkManagerTestInitHelper` needs an Android runtime, so this test goes under `app/src/androidTest/`. It is compile-only on the sandbox; runs on the same emulator the existing instrumented suite uses.

```bash
mkdir -p /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/androidTest/java/org/spsl/evtracker/data/backup
```

Create `app/src/androidTest/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt`:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.testing.FakeSettingsReader

@RunWith(AndroidJUnit4::class)
class WorkManagerBackupSchedulerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun driveDisabled_doesNotEnqueue() = runTest {
        val reader = FakeSettingsReader(driveEnabledInit = false)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()
        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        assertTrue("expected no work, was $infos", infos.isEmpty())
    }

    @Test
    fun driveEnabled_enqueuesUniqueWork() = runTest {
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()
        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
    }

    @Test
    fun rapidCalls_collapseToOneViaReplace() = runTest {
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        repeat(5) { sched.enqueueBackup() }
        val infos = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get()
        // ExistingWorkPolicy.REPLACE: at most one ENQUEUED/RUNNING; earlier states cancelled.
        val active = infos.count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        assertEquals(1, active)
    }

    @Test
    fun enqueuedWork_requiresConnectedNetwork() = runTest {
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()
        val info = workManager.getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME).get().first()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }
}
```

- [ ] **Step 2: Compile — should fail (`WorkManagerBackupScheduler` does not exist)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: compile error referencing `WorkManagerBackupScheduler`.

- [ ] **Step 3: Implement the scheduler**

`app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt`:

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
            .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "drive_backup"
        const val INITIAL_DELAY_SECONDS = 5L
        const val BACKOFF_SECONDS = 30L
    }
}
```

- [ ] **Step 4: Stub `DriveBackupWorker` so the scheduler compiles**

`app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt` — minimal stub for compilation, replaced in Task 9:

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}
```

- [ ] **Step 5: Compile instrumented sources**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run JVM suite to confirm nothing else broke**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/backup/WorkManagerBackupScheduler.kt \
        app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt \
        app/src/androidTest/java/org/spsl/evtracker/data/backup/WorkManagerBackupSchedulerTest.kt
git commit -m "feat(E): WorkManagerBackupScheduler + DriveBackupWorker stub"
```

---

## Task 9: `DriveBackupWorker` — full implementation

**Goal:** Replace the Task 8 stub with the real worker (spec §4.5). Three error branches: `DriveAuthRequiredException` → `failure`; other `IOException` → `retry` while `runAttemptCount < 5`, else `failure`; `BackupVersionMismatch` → `failure`. Records `lastBackupAt = clock()` on success.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`

- [ ] **Step 1: Replace the stub with the real implementation**

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
    private val clock: () -> Long
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        backupRepository.backupCurrentData()
        settingsWriter.setLastBackupAt(clock())
        Result.success()
    } catch (e: DriveAuthRequiredException) {
        Result.failure()
    } catch (e: BackupVersionMismatch) {
        Result.failure()
    } catch (e: IOException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
```

- [ ] **Step 2: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. Hilt's KSP processor will fail loudly if the assisted-inject types don't resolve — this is a sanity check.

- [ ] **Step 3: Run JVM tests (worker logic itself isn't unit-tested here; behaviour covered by the instrumented test in Task 19)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt
git commit -m "feat(E): DriveBackupWorker with documented error matrix"
```

---

## Task 10: WorkManager Hilt wiring — `Configuration.Provider` + manifest + `WorkerModule`

**Goal:** `EVTrackerApp` advertises a `HiltWorkerFactory`-backed `Configuration` so `@HiltWorker` types are constructible (spec §4.6). Manifest disables WorkManager's auto-initializer. `WorkerModule` provides `WorkManager` and the injectable `Clock`.

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`

- [ ] **Step 1: Update `EVTrackerApp.kt`**

Replace the file contents:

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

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
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

- [ ] **Step 2: Update `AndroidManifest.xml`**

Add `xmlns:tools` to the `<manifest>` root and inject the `<provider>` block to remove WorkManager's default initializer. Replace the file contents:

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

- [ ] **Step 3: Create `WorkerModule.kt`**

`app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`:

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

@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideClock(): () -> Long = { System.currentTimeMillis() }
}
```

- [ ] **Step 4: Verify build (Hilt KSP must accept the new injection sites)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run JVM tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt
git commit -m "feat(E): WorkManager Hilt wiring (Configuration.Provider + WorkerModule)"
```

---

## Task 11: Real `AndroidDriveAuthManager` — Authorization API

**Goal:** Implement `DriveAuthManager` against `Identity.getAuthorizationClient` (spec §4.1). No JVM tests — covered by manual smoke (§2.3) and the instrumented Task 19.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt`

- [ ] **Step 1: Implement the manager**

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthManager.AuthResult

@Singleton
class AndroidDriveAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DriveAuthManager {

    private val client = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest by lazy {
        AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
    }

    override suspend fun authorize(): AuthResult = await()

    override suspend fun silentToken(): AuthResult = when (val r = await()) {
        is AuthResult.NeedsResolution -> AuthResult.Failed("consent required")
        else -> r
    }

    private suspend fun await(): AuthResult = suspendCancellableCoroutine { cont ->
        client.authorize(request)
            .addOnSuccessListener { result: AuthorizationResult ->
                val pending = result.pendingIntent
                if (pending != null) {
                    cont.resume(AuthResult.NeedsResolution(pending.intentSender))
                } else {
                    val token = result.accessToken
                    if (!token.isNullOrEmpty()) cont.resume(AuthResult.Success(token))
                    else cont.resume(AuthResult.Failed("no token returned"))
                }
            }
            .addOnFailureListener { t ->
                cont.resume(AuthResult.Failed(t.message ?: "authorize failed", t))
            }
            .addOnCanceledListener {
                cont.resume(AuthResult.Failed("cancelled"))
            }
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
```

- [ ] **Step 2: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/backup/AndroidDriveAuthManager.kt
git commit -m "feat(E): AndroidDriveAuthManager wraps Authorization API"
```

---

## Task 12: Real `GoogleDriveRemoteSource` — Drive REST

**Goal:** Implement `DriveRemoteSource` with `google-api-services-drive` (spec §4.2). All calls run on `Dispatchers.IO`. Bearer token is set per-call by injecting an `HttpRequestInitializer`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt`

- [ ] **Step 1: Implement the source**

```kotlin
package org.spsl.evtracker.data.backup

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spsl.evtracker.domain.backup.DriveRemoteSource

@Singleton
class GoogleDriveRemoteSource @Inject constructor() : DriveRemoteSource {

    private fun client(accessToken: String): Drive {
        val initializer = HttpRequestInitializer { req ->
            req.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory(), initializer)
            .setApplicationName("EV Tracker")
            .build()
    }

    override suspend fun findBackupFileId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val list = client(accessToken).files().list()
            .setSpaces("appDataFolder")
            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
            .setFields("files(id)")
            .execute()
        list.files?.firstOrNull()?.id
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val metadata = File().apply {
                name = BACKUP_FILE_NAME
                parents = listOf("appDataFolder")
            }
            val content = ByteArrayContent("application/json", jsonBytes)
            client(accessToken).files().create(metadata, content)
                .setFields("id")
                .execute().id
        }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val content = ByteArrayContent("application/json", jsonBytes)
            client(accessToken).files().update(fileId, null, content).execute()
        }
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            client(accessToken).files().get(fileId).executeMediaAsInputStream().use { it.readBytes() }
        }

    companion object {
        const val BACKUP_FILE_NAME = "evtracker_backup.json"
    }
}
```

- [ ] **Step 2: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/data/backup/GoogleDriveRemoteSource.kt
git commit -m "feat(E): GoogleDriveRemoteSource (App Data folder REST)"
```

---

## Task 13: DI rewire — `BackupModule` + update `DomainModule` + delete no-ops

**Goal:** Swap the `BackupScheduler` and `BackupRepository` bindings to the real impls; add `BackupModule` for the new auth + remote-source bindings (spec §4.9, §4.10). Delete `NoOpBackupScheduler` and `NoOpBackupRepository`.

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/di/BackupModule.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/di/DomainModule.kt`
- Delete: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt`
- Delete: `app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt`

- [ ] **Step 1: Create `BackupModule.kt`**

```kotlin
package org.spsl.evtracker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.spsl.evtracker.data.backup.AndroidDriveAuthManager
import org.spsl.evtracker.data.backup.GoogleDriveRemoteSource
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindDriveAuthManager(impl: AndroidDriveAuthManager): DriveAuthManager

    @Binds
    @Singleton
    abstract fun bindDriveRemoteSource(impl: GoogleDriveRemoteSource): DriveRemoteSource
}
```

- [ ] **Step 2: Rewire `DomainModule.kt`**

In `DomainModule.kt`, swap the two backup bindings. Replace the `// Backup interfaces — no-op until E swaps these.` block:

```kotlin
    // Backup interfaces — bound to E's real implementations.
    @Binds abstract fun bindBackupScheduler(impl: org.spsl.evtracker.data.backup.WorkManagerBackupScheduler): BackupScheduler
    @Binds abstract fun bindBackupRepository(impl: org.spsl.evtracker.data.backup.DriveBackupRepository): BackupRepository
```

Also remove the now-unused imports:

```kotlin
// delete:
import org.spsl.evtracker.data.backup.NoOpBackupRepository
import org.spsl.evtracker.data.backup.NoOpBackupScheduler
```

- [ ] **Step 3: Delete the no-ops**

```bash
rm /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt
rm /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt
```

- [ ] **Step 4: Verify build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Hilt's KSP processor confirms the binding graph closes (every `@Inject`-required `BackupScheduler`, `BackupRepository`, `DriveAuthManager`, `DriveRemoteSource`, `WorkManager`, `() -> Long` resolves to a single binding).

- [ ] **Step 5: Run JVM tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. (No tests reference the no-ops directly.)

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/di/BackupModule.kt \
        app/src/main/java/org/spsl/evtracker/di/DomainModule.kt
git rm app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupScheduler.kt \
       app/src/main/java/org/spsl/evtracker/data/backup/NoOpBackupRepository.kt
git commit -m "refactor(E): rewire DI to real Drive backup implementations"
```

---

## Task 14: Strings + `SettingsUiState` / `SettingsEvent`

**Goal:** Add the Drive-section strings and the UI state classes that Tasks 15–17 consume (spec §4.7, §6.2).

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt`

- [ ] **Step 1: Add strings**

Append to the top-level `<resources>` in `strings.xml` (the existing `Drive` block already has a few strings; add the missing ones from §6.2 + dialog text and "Never"):

```xml
    <!-- Drive (Sub-project E) -->
    <string name="settings_section_drive">Backup &amp; restore</string>
    <string name="settings_drive_label">Google Drive backup</string>
    <string name="settings_drive_subtitle_off">Off</string>
    <string name="settings_drive_subtitle_on">On — auto-backup on every change</string>
    <string name="settings_last_backup_never">Never</string>
    <string name="settings_last_backup_label">Last backup: %1$s</string>
    <string name="drive_restore_dialog_title">Restore from Drive?</string>
    <string name="drive_restore_dialog_body">Found a backup from %1$s. Restoring will replace any data already on this device.</string>
    <string name="drive_restore_dialog_confirm">Restore</string>
    <string name="drive_restore_dialog_skip">Skip</string>
    <string name="drive_restore_succeeded">Backup restored.</string>
    <string name="drive_auth_failed">Couldn’t sign in to Google Drive. Try again.</string>
    <string name="drive_consent_cancelled">Drive backup not enabled — consent was cancelled.</string>
    <string name="drive_network_error">No network connection. Backup will retry when you’re online.</string>
    <string name="drive_remote_backup_too_new">This backup was created by a newer version. Update the app to restore.</string>
    <string name="drive_restore_failed">Couldn’t restore backup. Local data is unchanged.</string>
```

- [ ] **Step 2: Create `SettingsUiState.kt`**

```kotlin
package org.spsl.evtracker.core.model

import androidx.annotation.StringRes

data class SettingsUiState(
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    /** Non-null while the restore prompt is on screen. */
    val pendingRestoreLabel: String? = null
)

sealed class SettingsEvent {
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()
}
```

- [ ] **Step 3: Build sanity check**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/res/values/strings.xml \
        app/src/main/java/org/spsl/evtracker/core/model/SettingsUiState.kt
git commit -m "feat(E): Drive Settings strings + SettingsUiState/Event"
```

---

## Task 15: `SettingsViewModel` — TDD state machine

**Goal:** Implement `SettingsViewModel` per spec §4.7. Pure state machine — no `DriveAuthManager`, no `IntentSender`. Nine JVM tests covering every branch. **Critical invariant:** every branch of `onDriveAuthGranted` and `onDriveAuthFailed` clears `isAuthInFlight`; `driveEnabled = true` is set only after Replace, Skip, or `RestoreBackupUseCase.Success` — never speculatively.

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Write the failing test class**

```kotlin
package org.spsl.evtracker.ui.settings

import androidx.work.WorkManager
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.RestoreResult
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase
import org.spsl.evtracker.testing.FakeBackupRepository
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarReader
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeLocationReader
import org.spsl.evtracker.testing.FakeRestoreSnapshotWriter
import org.spsl.evtracker.testing.FakeRestoreTransactionRunner
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }

    private fun build(
        remoteJson: String? = null,
        readThrows: Throwable? = null
    ): Setup {
        val reader = FakeSettingsReader()
        val writer = FakeSettingsWriter()
        val backupRepo = ThrowingBackupRepository(remoteJson, readThrows)
        val scheduler = FakeBackupScheduler()
        val workManager = mock<WorkManager>()
        val restoreUseCase = RestoreBackupUseCase(
            backupRepository = backupRepo,
            backupSerializer = org.spsl.evtracker.domain.service.BackupSerializer(),
            transactionRunner = FakeRestoreTransactionRunner(),
            snapshotWriter = FakeRestoreSnapshotWriter(),
            carReader = FakeCarReader(),
            chargeEventQueries = FakeChargeEventQueries(),
            locationReader = FakeLocationReader(),
            settingsWriter = writer,
            backupScheduler = scheduler
        )
        val vm = SettingsViewModel(reader, writer, backupRepo, scheduler, workManager, restoreUseCase)
        return Setup(vm, reader, writer, backupRepo, scheduler, workManager)
    }

    private data class Setup(
        val vm: SettingsViewModel,
        val reader: FakeSettingsReader,
        val writer: FakeSettingsWriter,
        val backupRepo: ThrowingBackupRepository,
        val scheduler: FakeBackupScheduler,
        val workManager: WorkManager
    )

    /** Like FakeBackupRepository but lets a test throw from readRemoteBackup. */
    private class ThrowingBackupRepository(
        var remoteJson: String? = null,
        var throwOnRead: Throwable? = null
    ) : org.spsl.evtracker.domain.backup.BackupRepository {
        override suspend fun backupCurrentData() {}
        override suspend fun readRemoteBackup(): String? {
            throwOnRead?.let { throw it }
            return remoteJson
        }
    }

    @Test
    fun onDriveAuthGranted_noRemote_setsDriveEnabledAndClearsAuthFlight() = runTest {
        val s = build(remoteJson = null)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        val state = s.vm.uiState.first()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertFalse(state.isAuthInFlight)
        assertNull(state.pendingRestoreLabel)
    }

    @Test
    fun onDriveAuthGranted_remoteFound_emitsRestorePromptAndClearsAuthFlight() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData
            .fromEntities(emptyList(), emptyList(), emptyList(), now = 1_700_000_000_000L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()

        val prompt = received.filterIsInstance<SettingsEvent.ShowRestorePrompt>().firstOrNull()
        assertNotNull("expected ShowRestorePrompt; got $received", prompt)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        assertEquals(prompt!!.backupDateLabel, state.pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsAuthRequired_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = DriveAuthRequiredException())
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_auth_failed, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthGranted_readThrowsIOException_emitsNetworkErrorAndClearsAuthFlight() = runTest {
        val s = build(readThrows = IOException("offline"))
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        assertEquals(R.string.drive_network_error, (received.single() as SettingsEvent.ShowError).msgRes)
        val state = s.vm.uiState.first()
        assertFalse(s.writer.driveEnabled)
        assertFalse(state.isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onDriveAuthFailed_emitsErrorAndClearsAuthFlight() = runTest {
        val s = build()
        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onDriveAuthFailed(R.string.drive_consent_cancelled)
        advanceUntilIdle()
        assertEquals(R.string.drive_consent_cancelled, (received.single() as SettingsEvent.ShowError).msgRes)
        assertFalse(s.vm.uiState.first().isAuthInFlight)
        job.cancel()
    }

    @Test
    fun onConfirmRestore_invokesRestoreAndEmitsSuccess() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()                  // primes pendingRestoreLabel
        advanceUntilIdle()

        val received = mutableListOf<SettingsEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { s.vm.events.collect { received += it } }
        s.vm.onConfirmRestore()
        advanceUntilIdle()

        assertTrue(received.any { it is SettingsEvent.RestoreSucceeded })
        // RestoreBackupUseCase sets driveEnabled = true on Success.
        assertTrue(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
        job.cancel()
    }

    @Test
    fun onSkipRestore_setsDriveEnabledAndClearsPending() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onSkipRestore()
        advanceUntilIdle()
        assertTrue(s.writer.driveEnabled)
        assertEquals(1, s.scheduler.enqueueCount)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onRestorePromptDismissed_leavesDriveOff() = runTest {
        val data = org.spsl.evtracker.core.model.BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val json = org.spsl.evtracker.domain.service.BackupSerializer().toJson(data)
        val s = build(remoteJson = json)
        s.vm.onDriveAuthGranted()
        advanceUntilIdle()
        s.vm.onRestorePromptDismissed()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        assertNull(s.vm.uiState.first().pendingRestoreLabel)
    }

    @Test
    fun onToggleDriveOff_cancelsWorkAndDisables() = runTest {
        val s = build()
        s.vm.onToggleDriveOff()
        advanceUntilIdle()
        assertFalse(s.writer.driveEnabled)
        verify(s.workManager).cancelUniqueWork(any())
    }
}
```

> The test file uses `mockito-kotlin` 5.2.1 (already on the classpath via `libs.mockito.kotlin`). The 5.x line lives under `org.mockito.kotlin`, which is what the imports above use.

- [ ] **Step 2: Run — should fail to compile**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.settings.SettingsViewModelTest"
```

Expected: compile error (the new VM ctor signature does not exist yet).

- [ ] **Step 3: Implement `SettingsViewModel.kt`**

Replace the file:

```kotlin
package org.spsl.evtracker.ui.settings

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
import org.spsl.evtracker.data.backup.WorkManagerBackupScheduler
import org.spsl.evtracker.domain.backup.BackupRepository
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.domain.usecase.RestoreBackupUseCase

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val settingsWriter: SettingsWriter,
    private val backupRepository: BackupRepository,
    private val backupScheduler: BackupScheduler,
    private val workManager: WorkManager,
    private val restoreBackupUseCase: RestoreBackupUseCase
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
            settingsReader.driveEnabled.collect { enabled ->
                _uiState.update { it.copy(driveEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsReader.lastBackupAt.collect { ts ->
                _uiState.update { it.copy(lastBackupAt = ts) }
            }
        }
    }

    /** Called by Fragment when the auth flow returned Success (silent or post-consent). */
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

    /** Called by Fragment when the auth flow returned Failed (or consent was cancelled). */
    fun onDriveAuthFailed(msgRes: Int) {
        _uiState.update { it.copy(isAuthInFlight = false) }
        _events.tryEmit(SettingsEvent.ShowError(msgRes))
    }

    fun onConfirmRestore() {
        viewModelScope.launch {
            when (val r = restoreBackupUseCase()) {
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
            workManager.cancelUniqueWork(WorkManagerBackupScheduler.UNIQUE_NAME)
        }
    }

    private fun parseExportedAtLabel(json: String): String = try {
        // BackupData.exportedAt is an ISO-8601 string. Round-trip via Gson on a partial schema would
        // be expensive; a regex is enough for the human-readable label we need here.
        val match = EXPORTED_AT_REGEX.find(json)
        val iso = match?.groupValues?.get(1) ?: return UNKNOWN_DATE
        val instant = Instant.parse(iso)
        DATE_FORMAT.format(Date(instant.toEpochMilli()))
    } catch (_: Throwable) {
        UNKNOWN_DATE
    }

    companion object {
        private val EXPORTED_AT_REGEX = Regex("\"exported_at\"\\s*:\\s*\"([^\"]+)\"")
        private const val UNKNOWN_DATE = "an earlier date"
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
```

- [ ] **Step 4: Re-run tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.settings.SettingsViewModelTest"
```

Expected: 9 tests pass.

- [ ] **Step 5: Run the full suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/ui/settings/SettingsViewModel.kt \
        app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt
git commit -m "feat(E): SettingsViewModel state machine + 9 tests"
```

---

## Task 16: `fragment_settings.xml` layout — Drive section + placeholder rows

**Goal:** Replace the placeholder layout with a Drive section (switch + last-backup line) and disabled placeholder rows for the F-era settings (theme/units/currency/reset/CSV/manage locations).

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`

- [ ] **Step 1: Replace the file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
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

            <!-- F placeholders. Disabled until F lands. -->
            <TextView
                android:id="@+id/placeholder_theme"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:paddingTop="16dp"
                android:text="@string/pref_theme"/>

            <TextView
                android:id="@+id/placeholder_units"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:text="@string/pref_distance_unit"/>

            <TextView
                android:id="@+id/placeholder_currency"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:text="@string/pref_currency"/>

            <TextView
                android:id="@+id/placeholder_reset"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:text="@string/pref_reset_data"/>

            <TextView
                android:id="@+id/placeholder_export"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:text="@string/pref_export_csv"/>

            <TextView
                android:id="@+id/placeholder_manage_locations"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:minHeight="48dp"
                android:gravity="center_vertical"
                android:enabled="false"
                android:alpha="0.5"
                android:text="@string/pref_manage_locations"/>
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: Add tools namespace at root**

The first line above declares only `xmlns:android` and `xmlns:app`. Add `xmlns:tools="http://schemas.android.com/tools"` to the root element so the `tools:text` preview line on `text_last_backup` resolves. Replace the opening tag with:

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
```

- [ ] **Step 3: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/res/layout/fragment_settings.xml
git commit -m "feat(E): fragment_settings layout (Drive section + F placeholders)"
```

---

## Task 17: `SettingsFragment` — owns auth + `ActivityResultLauncher`

**Goal:** Replace the Fragment to render the new layout, drive `SettingsViewModel`, own the `ActivityResultLauncher<IntentSenderRequest>`, and field-inject `DriveAuthManager` (spec §4.7, §5.3).

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package org.spsl.evtracker.ui.settings

import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.SettingsEvent
import org.spsl.evtracker.databinding.FragmentSettingsBinding
import org.spsl.evtracker.domain.backup.DriveAuthManager

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject lateinit var auth: DriveAuthManager
    private val viewModel: SettingsViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Consent granted — re-run authorize() to pick up the cached silent token.
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = auth.authorize()) {
                    is DriveAuthManager.AuthResult.Success -> viewModel.onDriveAuthGranted()
                    else -> viewModel.onDriveAuthFailed(R.string.drive_auth_failed)
                }
            }
        } else {
            viewModel.onDriveAuthFailed(R.string.drive_consent_cancelled)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // Avoid re-emitting the listener when we set checked from state.
                        if (binding.switchDrive.isChecked != state.driveEnabled) {
                            binding.switchDrive.setOnCheckedChangeListener(null)
                            binding.switchDrive.isChecked = state.driveEnabled
                            binding.switchDrive.setOnCheckedChangeListener { _, isChecked ->
                                if (isChecked) onUserToggledOn() else viewModel.onToggleDriveOff()
                            }
                        }
                        binding.switchDrive.isEnabled = !state.isAuthInFlight
                        binding.textLastBackup.text = formatLastBackup(state.lastBackupAt)
                    }
                }
                launch {
                    viewModel.events.collect { ev ->
                        when (ev) {
                            is SettingsEvent.ShowRestorePrompt -> showRestoreDialog(ev.backupDateLabel)
                            is SettingsEvent.RestoreSucceeded ->
                                Snackbar.make(binding.root, R.string.drive_restore_succeeded, Snackbar.LENGTH_LONG).show()
                            is SettingsEvent.ShowError ->
                                Snackbar.make(binding.root, ev.msgRes, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun onUserToggledOn() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = auth.authorize()) {
                is DriveAuthManager.AuthResult.Success -> viewModel.onDriveAuthGranted()
                is DriveAuthManager.AuthResult.NeedsResolution ->
                    consentLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                is DriveAuthManager.AuthResult.Failed ->
                    viewModel.onDriveAuthFailed(R.string.drive_auth_failed)
            }
        }
    }

    private fun showRestoreDialog(label: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.drive_restore_dialog_title)
            .setMessage(getString(R.string.drive_restore_dialog_body, label))
            .setPositiveButton(R.string.drive_restore_dialog_confirm) { _, _ -> viewModel.onConfirmRestore() }
            .setNegativeButton(R.string.drive_restore_dialog_skip) { _, _ -> viewModel.onSkipRestore() }
            .setOnDismissListener { viewModel.onRestorePromptDismissed() }
            .setCancelable(true)
            .show()
    }

    private fun formatLastBackup(epochMs: Long?): String {
        if (epochMs == null) return getString(R.string.settings_last_backup_label, getString(R.string.settings_last_backup_never))
        val label = DATE_FORMAT.format(Date(epochMs))
        return getString(R.string.settings_last_backup_label, label)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.US)
    }
}
```

> **Note about the dialog dismiss listener:** `setOnDismissListener` fires after every button press (Confirm, Skip, *and* back-press). To avoid double-clearing `pendingRestoreLabel`, the VM `onRestorePromptDismissed` is idempotent — it just sets the field to `null` if it's already null. No extra guarding needed.

- [ ] **Step 2: Build**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full JVM suite (no Fragment tests yet)**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/ui/settings/SettingsFragment.kt
git commit -m "feat(E): SettingsFragment owns auth + restore dialog"
```

---

## Task 18: Extend `RestoreBackupUseCaseTest` with the Drive path

**Goal:** Three new tests (spec §7.1) — end-to-end serializer round-trip via `FakeDriveRemoteSource` (driving the *real* `DriveBackupRepository.readRemoteBackup` is overkill for the use-case test; we use `FakeBackupRepository` seeded with serialized JSON), version-mismatch via the Drive path, and confirming the post-completion `enqueueBackup()` plumbs through the suspend interface.

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt`

- [ ] **Step 1: Append three test cases**

Append inside the existing `RestoreBackupUseCaseTest` class:

```kotlin
    @Test
    fun success_roundTripPreservesAllEntities() = runTest {
        val cars = listOf(CarEntity(id = 1, name = "T", createdAt = 1L))
        val events = listOf(ChargeEventEntity(
            id = 7, carId = 1, eventDate = 2L, odometerKm = 100.0, kwhAdded = 10.0,
            chargeType = "DC", costTotal = 5.0, costPerKwh = 0.5, currency = "EUR",
            location = "Home", note = "n"
        ))
        val locations = listOf(CustomLocationEntity(id = 1, label = "Home", useCount = 3, lastUsed = 9L))
        val data = BackupData.fromEntities(cars, events, locations, now = 0L)
        val s = build(remoteJson = serializer.toJson(data))
        s.useCase()
        // The transaction runner gets the entity lists derived from the JSON round-trip.
        assertEquals(cars, s.txn.lastCars)
        assertEquals(events, s.txn.lastEvents)
        assertEquals(locations, s.txn.lastLocations)
    }

    @Test
    fun versionTooNew_alsoSurfacesAsVersionMismatch() = runTest {
        val v99 = """{"backup_version":99,"exported_at":"x","cars":[],"charge_events":[],"custom_locations":[]}"""
        val s = build(remoteJson = v99)
        val r = s.useCase()
        assertTrue(r is RestoreResult.VersionMismatch)
        assertEquals(99, (r as RestoreResult.VersionMismatch).actualVersion)
    }

    @Test
    fun success_enqueuesBackupAfterRestore() = runTest {
        val data = BackupData.fromEntities(emptyList(), emptyList(), emptyList(), now = 0L)
        val s = build(remoteJson = serializer.toJson(data))
        s.useCase()
        // Suspend interface plumbed through — the fake's enqueueBackup ran.
        assertEquals(1, s.scheduler.enqueueCount)
    }
```

- [ ] **Step 2: Run**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.domain.usecase.RestoreBackupUseCaseTest"
```

Expected: 9 tests pass (6 original + 3 new).

- [ ] **Step 3: Run the full suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with the JVM count near ~143 (6 RestoreBackup new + 3 SettingsRepository new + 8 DriveBackupRepository + 9 SettingsViewModel = 26 new on top of the ~123 baseline ≈ 149; spec's "≈20" was an estimate).

- [ ] **Step 4: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt
git commit -m "test(E): extend RestoreBackupUseCase with Drive-path round-trip"
```

---

## Task 19: Instrumented `DriveBackupWorkerTest`

**Goal:** Verify the worker's three Result branches via `TestListenableWorkerBuilder` with a Hilt-injected fake auth + remote source (spec §7.2). Compile-only on the sandbox; runs on emulator.

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt`

- [ ] **Step 1: Write the test class**

```kotlin
package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.di.BackupModule
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveAuthRequiredException
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource

@HiltAndroidTest
@UninstallModules(BackupModule::class)
@RunWith(AndroidJUnit4::class)
class DriveBackupWorkerTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var fakeAuth: FakeDriveAuthManager
    @Inject lateinit var fakeRemote: FakeDriveRemoteSource

    @Module
    @InstallIn(SingletonComponent::class)
    object TestBackupModule {
        @Provides @Singleton fun fakeAuth(): FakeDriveAuthManager = FakeDriveAuthManager()
        @Provides @Singleton fun fakeRemote(): FakeDriveRemoteSource = FakeDriveRemoteSource()
        @Provides @Singleton fun bindAuth(impl: FakeDriveAuthManager): DriveAuthManager = impl
        @Provides @Singleton fun bindRemote(impl: FakeDriveRemoteSource): DriveRemoteSource = impl
    }

    private lateinit var context: Context

    @Before fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun happyPath_returnsSuccess() = runBlocking {
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun authRevoked_returnsFailure() = runBlocking {
        fakeAuth.nextResult = DriveAuthManager.AuthResult.Failed("revoked")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun ioError_returnsRetry() = runBlocking {
        fakeRemote.failNext = IOException("offline")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
```

- [ ] **Step 2: Compile instrumented sources**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL. (Running this needs a connected emulator; the sandbox cannot.)

- [ ] **Step 3: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/androidTest/java/org/spsl/evtracker/data/backup/DriveBackupWorkerTest.kt
git commit -m "test(E): instrumented DriveBackupWorker happy/retry/failure"
```

---

## Task 20: Final verification + CLAUDE.md sync

**Goal:** Run the full test suite, build the debug APK, and update `CLAUDE.md`'s status banner to reflect that E has landed.

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Run JVM tests**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. JVM count ~149 (6 RestoreBackup-extension + 3 SettingsRepository + 8 DriveBackupRepository + 9 SettingsViewModel = 26 new on top of the ~123 baseline). Spec §2.3 budgets ~143 (≈20 new); we're slightly above due to the two extra `DriveBackupRepository` boundary cases. Both numbers satisfy the acceptance criterion that the JVM count grew commensurately.

- [ ] **Step 2: Build debug APK**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Compile instrumented suite**

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Update `CLAUDE.md` status banner**

Edit the "Status" paragraph (currently: *"Sub-projects A, B, C, and D … Sub-project E (real Drive auth + WorkManager-backed `BackupScheduler`/`BackupRepository`) lives behind no-op interfaces."*) to:

> **Status:** Sub-projects A (foundation/DI/Room v3), B (repositories), C (domain services + use cases), D (Core UI: Dashboard/ChargeEdit/Cars/History), and E (Drive backup: real Authorization API + Drive REST + WorkManager scheduler + Settings UI Drive section) are all merged. Wizard, Dashboard, ChargeEdit, Cars, History, and the Drive-section Settings are fully wired; Charts, the rest of Settings (theme/units/currency/reset/CSV/manage locations), and ManageLocations remain placeholder fragments until F. Sub-project F covers everything else. JVM unit-test count: ~149.

Update the architecture table line for the Data layer to show real bindings:

> Data: Room (CarDao, ChargeEventDao, CustomLocationDao) · Preferences DataStore · Drive AppData client (E ✓) · WorkManager backup scheduler (E ✓)

- [ ] **Step 5: Manual smoke checklist** (from spec §2.3, requires a Google account allow-listed per `GOOGLE_CLOUD_SETUP.md` and an emulator with Play services)

Document this as a check, not as code:

> **Manual smoke (run before merging into `main`):**
> 1. Toggle Drive ON in Settings → consent sheet appears → grant.
> 2. Save a charge event → within ~10 s `evtracker_backup.json` exists in App Data folder
>    (verify: `curl -H "Authorization: Bearer $TOKEN" "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime)"`).
> 3. Settings shows "Last backup: just now".
> 4. Toggle Drive OFF → no further backups run.
> 5. Re-install (or, once F lands, "Reset all data") and toggle Drive ON → restore prompt with the previous backup's date appears → Confirm restores; Skip leaves local empty.

- [ ] **Step 6: Commit**

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add CLAUDE.md
git commit -m "docs(E): update CLAUDE.md status banner — Sub-project E landed"
```

- [ ] **Step 7: Hand off**

`feat/drive-backup-sub-project-e` is now ready for the manual smoke + merge into `main` via `--no-ff` (per the A/B/C/D pattern in `CLAUDE.md`).

---

## Self-review (filled in after writing the plan)

### Spec coverage

| Spec section | Coverage |
|---|---|
| §1.3 Suspend conversion | Task 2 |
| §3 File map (created/modified/deleted) | All in File map above + Tasks 3–17 |
| §4.1 `DriveAuthManager` | Task 3 (interface), Task 11 (impl) |
| §4.2 `DriveRemoteSource` | Task 3 (interface), Task 12 (impl) |
| §4.3 `DriveBackupRepository` + `translatingAuthErrors` + `AUTH_REASONS` + 403/quota carve-out | Task 7 (incl. 403 quota test) |
| §4.4 `WorkManagerBackupScheduler` (5 s initial delay, 30 s exp backoff, REPLACE) | Task 8 |
| §4.5 `DriveBackupWorker` (3-branch error matrix, MAX_ATTEMPTS=5, clock injection) | Task 9 |
| §4.6 `EVTrackerApp` `Configuration.Provider` + manifest + `WorkerModule` | Task 10 |
| §4.7 `SettingsFragment` + `SettingsViewModel` state machine + isAuthInFlight clears on every branch | Tasks 14, 15, 17 |
| §4.8 `SettingsReader`/`Writer` extensions + `LAST_BACKUP_AT` | Tasks 4, 5 |
| §4.9 `BackupModule` + DomainModule rewire | Task 13 |
| §4.10 (DI failed earlier-design refuted) | Task 13 (single `@Singleton` BackupModule, no Activity-component split) |
| §5.1–5.5 Sequences | Implicitly covered by SettingsViewModelTest (Task 15) and the worker test (Task 19) |
| §6.1 Worker error matrix | Task 7 (boundary translation), Task 9 (worker mapping), Task 19 (instrumented happy/retry/auth) |
| §6.2 Strings | Task 14 |
| §7.1 JVM tests (≈20) | Tasks 5, 7, 15, 18 (3 + 8 + 9 + 3 = 23, exceeds ≈20 floor) |
| §7.2 Instrumented worker test | Task 19 |
| §7.3 Test infrastructure additions to Fakes.kt | Tasks 4 and 6 |
| §7.4 Compatibility test sweep | Task 2 (suspend cascade verified by full-suite run) |
| §8.1 Risks (silent token failure, quota, manifest auto-init) | Mitigations are baked into Tasks 9, 8, 10 |
| §10 Spec self-review | Reflected in the plan's coverage table above |

### Placeholder scan

- No "TBD/TODO/implement later" markers.
- Every code block is complete and runnable.
- Every test step shows the assertion code.
- The `DateFormat` instance reused between Fragment and ViewModel intentionally duplicates the format string — both files need it for their own purposes; pulling it into a shared util is out-of-scope cleanup.

### Type & method consistency

- `BackupScheduler.enqueueBackup()` is `suspend` everywhere from Task 2 onward.
- `WorkManagerBackupScheduler.UNIQUE_NAME` ("drive_backup") referenced in tests, in `cancelUniqueWork`, and in the manual smoke notes.
- `DriveAuthManager.AuthResult` sealed class and the three subtypes (`Success`, `NeedsResolution`, `Failed`) match between interface (Task 3), real impl (Task 11), fake (Task 6), Fragment (Task 17), and tests (Task 15).
- `DriveAuthRequiredException` is `IOException`-derived; the worker (Task 9) `catch`-orders auth-required *before* `IOException` so the auth branch wins.
- `SettingsViewModel` constructor signature (Task 15) matches what `SettingsFragment` injects via `@HiltViewModel` and what the test's `Setup.build()` instantiates.
- `RestoreBackupUseCase` signature is unchanged (no plan task touches it). Existing `RestoreBackupUseCaseTest` continues to work because it constructs the use case with the unchanged param list (Task 18 only adds new tests, no change to `build()`).
- `BackupData.exportedAt` is a `String` (per `BackupData.kt:11`) — `parseExportedAtLabel` regex extracts that string before `Instant.parse`. Consistent.

### Scope check

Single sub-project, single feature branch (`feat/drive-backup-sub-project-e`). No multi-subsystem decomposition needed.
