# Sub-project E: Drive Backup — Design

**Date:** 2026-04-28
**Status:** Draft, awaiting user review
**Sources of truth this design defers to:** `DESIGN.md §8` (Drive backup), `AGENT_INSTRUCTIONS.md §7` (backup infrastructure), `GOOGLE_CLOUD_SETUP.md` (OAuth setup), `TEST_PLAN.md §5` (Drive/backup tests). Where this design narrows scope or makes specific implementation choices, those choices override the broader docs *for Sub-project E only*.

---

## 1. Context — what landed in A+B+C+D, what E picks up

### 1.1 Prerequisites

This spec assumes Sub-projects A, B, C, and D are merged into `main`. At the time of writing, the latest merge was commit `4ecc5b3` ("Merge Sub-project D (Core UI) into main"). The spec's file map and "extends existing X" references are **additive on top of that state** — they are not standalone instructions for a fresh repo.

If the workspace is at any state earlier than `4ecc5b3`, this design cannot be applied as-is.

### 1.2 What A+B+C+D delivered for backup

- **A:** DataStore key `DRIVE_ENABLED` declared (unused).
- **B:** Room v3 schema with `cars`, `charge_events`, `custom_locations` — the entities that backup serializes.
- **C:** Domain seams shipped behind no-ops:
  - `domain/backup/BackupScheduler.kt` — `fun enqueueBackup()`, bound to `NoOpBackupScheduler`.
  - `domain/backup/BackupRepository.kt` — `suspend fun backupCurrentData()`, `suspend fun readRemoteBackup(): String?`, bound to `NoOpBackupRepository`.
  - `domain/backup/RestoreSnapshotWriter.kt` — bound to `CacheDirRestoreSnapshotWriter` (writes `cacheDir/last_overwritten_backup.json`).
  - `domain/backup/RestoreTransactionRunner.kt` — bound to `RoomRestoreTransactionRunner` (`database.withTransaction { … }`).
  - `domain/service/BackupSerializer.kt` — Gson-based `toJson`/`fromJson` with version check (`backup_version = 3`).
  - `core/model/BackupData.kt` — full DTO graph + `BackupVersionMismatch` exception.
  - `domain/usecase/RestoreBackupUseCase.kt` — fully wired, sets `driveEnabled = true` on completion, calls `BackupScheduler.enqueueBackup()`. Returns `RestoreResult.NoRemoteBackup | VersionMismatch | Success`.
  - All persisted-change use cases (`SaveChargeEvent`, `DeleteChargeEvent`, `AddCar`, `RenameCar`, `DeleteCar`, `RestoreBackup`) call `backupScheduler.enqueueBackup()` after every commit.
  - `SettingsWriter.setDriveEnabled(enabled: Boolean)` — writer-only; no read-side `Flow<Boolean>` yet.
- **D:** No backup work; `SettingsFragment` remains an empty placeholder shipped in A.
- **Build dependencies already declared in `gradle/libs.versions.toml`:**
  - `play-services-auth` 21.2.0 (Authorization API)
  - `google-api-client-android` 2.2.0
  - `google-api-services-drive` v3-rev20231128-2.0.0
  - `androidx-work-runtime-ktx` 2.9.0 (+ `androidx-work-testing` for tests)
  - `gson` 2.10.1
- **Manifest already includes:**
  - `<uses-permission android:name="android.permission.INTERNET" />`
  - `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`
  - `<meta-data android:name="com.google.android.gms.version" .../>`

### 1.3 What E introduces

Sub-project E ships the real Drive-backup back-end plus the smallest UI needed to drive it:

- **Auth seam.** `domain/backup/DriveAuthManager.kt` (interface) plus `data/backup/AndroidDriveAuthManager.kt` (Authorization API implementation). Activity-scoped because consent intents must launch from an Activity; silent re-auth from app context for the Worker.
- **Drive REST seam.** `domain/backup/DriveRemoteSource.kt` (interface) plus `data/backup/GoogleDriveRemoteSource.kt` (uses `google-api-services-drive` with bearer-token request initializer).
- **Real `BackupRepository`.** `data/backup/DriveBackupRepository.kt` — composes `BackupSerializer`, the four entity readers, and `DriveRemoteSource`; replaces `NoOpBackupRepository` in the Hilt binding.
- **Real `BackupScheduler`.** `data/backup/WorkManagerBackupScheduler.kt` — gates on `settingsReader.driveEnabled.first()`, enqueues `OneTimeWorkRequest<DriveBackupWorker>` with `enqueueUniqueWork("drive_backup", REPLACE, ...)`. Replaces `NoOpBackupScheduler` in the Hilt binding.
- **Worker.** `data/backup/DriveBackupWorker.kt` — `@HiltWorker CoroutineWorker` that resolves a silent token, runs `backupRepository.backupCurrentData()`, writes `lastBackupAt = now`. Maps errors to `Result.success` / `Result.retry` / `Result.failure` per a documented matrix.
- **WorkManager Hilt wiring.** `EVTrackerApp` implements `Configuration.Provider`, injects `HiltWorkerFactory`. `androidx.hilt:hilt-work` + KSP `androidx.hilt:hilt-compiler` added. Manifest disables WorkManager's default initializer.
- **Suspend BackupScheduler interface.** `BackupScheduler.enqueueBackup()` becomes `suspend fun enqueueBackup()` so the scheduler can read DataStore for the `driveEnabled` gate without blocking. All 7 existing callers are already suspend functions; the change is mechanical.
- **Settings — Drive section only.** Minimal `SettingsFragment` + `SettingsViewModel`: a Drive backup toggle, a "Last backup: …" line, and a Restore confirmation dialog. The remaining Settings entries (theme picker, units, currency, reset preferences, CSV export, manage locations) stay placeholders for F.
- **Settings DataStore additions.** `LAST_BACKUP_AT` (`longPreferencesKey`) — writer-side `setLastBackupAt(epochMs: Long)`, reader-side `lastBackupAt: Flow<Long?>` (null when unset). `DRIVE_ENABLED` gains a reader-side `driveEnabled: Flow<Boolean>` accessor.
- **No-op deletions.** `NoOpBackupScheduler` and `NoOpBackupRepository` are deleted; their DI bindings replaced rather than supplemented. Their existence past E would be dead code.

Sub-projects D (already merged) and F (Charts/CSV/Settings polish/ManageLocations) are unaffected by E except that F will inherit the partial `SettingsFragment` E ships and extend it.

---

## 2. Scope and acceptance criteria

### 2.1 In scope

- Real Drive backup end-to-end: serialize → upload to App Data folder → record `lastBackupAt`.
- Real Drive download path used by `RestoreBackupUseCase`.
- Auth flow: first-time consent via Activity, silent re-auth in subsequent runs and Worker runs.
- Settings UI just for Drive: toggle, last-backup line, restore prompt dialog. Other Settings entries stay placeholders.
- WorkManager wiring: unique work `drive_backup`, network constraint, exponential backoff 30 s.
- `BackupScheduler.enqueueBackup()` becomes suspend; gate reads `driveEnabled` and no-ops when disabled.
- `SettingsReader` gains `driveEnabled: Flow<Boolean>` and `lastBackupAt: Flow<Long?>` accessors. `SettingsWriter` gains `setLastBackupAt(epochMs: Long)`.
- New PreferenceKey `LAST_BACKUP_AT`.
- Hilt bindings rewired: `BackupScheduler → WorkManagerBackupScheduler`, `BackupRepository → DriveBackupRepository`, `DriveAuthManager → AndroidDriveAuthManager`, `DriveRemoteSource → GoogleDriveRemoteSource`.
- `EVTrackerApp` implements `Configuration.Provider`; manifest removes WorkManager's auto-initializer.
- `androidx.hilt:hilt-work` + `androidx.hilt:hilt-compiler` added to deps.
- `NoOpBackupScheduler` and `NoOpBackupRepository` deleted; `DomainModule` updated.

### 2.2 Out of scope

| Concern | Lands in |
|---|---|
| Settings UI for theme, units, currency, reset preferences | F |
| CSV export entry point in Settings | F |
| Manage Locations entry point in Settings | F |
| Charts wiring | F |
| Backup history list / advanced status UI | F (if desired) |
| Re-encrypted token storage | Not needed — silent re-auth is the supported pattern |
| Conflict resolution beyond replace/skip | Per DESIGN §8: merge is explicitly out of scope at the product level |
| Multi-device concurrent edit | DESIGN §8 documents the file as full-snapshot; no append-merge |
| Manual "Backup now" button | Deferred — auto-backup covers all persisted changes; user can force one by re-toggling Drive (which calls `enqueueBackup()` after auth) |

### 2.3 Acceptance criteria

1. `./gradlew assembleDebug` succeeds with `GRADLE_USER_HOME=/tmp/gradle-home` (sandbox quirk).
2. `./gradlew test` passes — JVM count grows from ~123 to **~143** (≈20 new tests across `WorkManagerBackupSchedulerTest`, `DriveBackupRepositoryTest`, `SettingsViewModelTest`, expanded `RestoreBackupUseCaseTest`, and updated `Fakes.kt`).
3. `./gradlew :app:assembleDebugAndroidTest` compiles. Running on a connected emulator is **expected** to pass for `DriveBackupWorkerTest` (HiltWorkerFactory + fake DriveRemoteSource); existing instrumented tests remain green.
4. Manual smoke (requires emulator with Google Play services and a Google account allow-listed per `GOOGLE_CLOUD_SETUP.md`):
   - Toggle Drive ON in Settings → consent sheet appears → grant.
   - Save a charge event → within ~10 s the file `evtracker_backup.json` exists in the App Data folder (verify via Drive `files.list` with `spaces=appDataFolder`).
   - Settings shows "Last backup: just now".
   - Toggle Drive OFF → no further backups run.
   - Re-install (or "Reset all data" once F lands) and toggle Drive ON → restore prompt with the previous backup's date appears → Confirm restores; Skip leaves local empty.

---

## 3. Architecture additions

```
app/src/main/java/org/spsl/evtracker/
  data/
    backup/
      AndroidDriveAuthManager.kt         (new — @ActivityScoped wraps Identity.getAuthorizationClient)
      GoogleDriveRemoteSource.kt         (new — @Singleton, Drive REST via google-api-services-drive)
      DriveBackupRepository.kt           (new — replaces NoOpBackupRepository)
      WorkManagerBackupScheduler.kt      (new — replaces NoOpBackupScheduler)
      DriveBackupWorker.kt               (new — @HiltWorker)
      NoOpBackupRepository.kt            (DELETED)
      NoOpBackupScheduler.kt             (DELETED)
    repository/
      SettingsRepository.kt              (modified — add driveEnabled Flow, lastBackupAt Flow, setLastBackupAt)
    preferences/
      PreferenceKeys.kt                  (modified — add LAST_BACKUP_AT)
  domain/
    backup/
      BackupScheduler.kt                 (modified — `suspend fun enqueueBackup()` + revised KDoc)
      DriveAuthManager.kt                (new — interface)
      DriveRemoteSource.kt               (new — interface)
    repository/
      SettingsReader.kt                  (modified — add driveEnabled, lastBackupAt)
      SettingsWriter.kt                  (modified — add setLastBackupAt)
    usecase/
      RestoreBackupUseCase.kt            (no signature change; still calls suspend enqueueBackup())
      AddCarUseCase.kt                   (no signature change — already suspend)
      RenameCarUseCase.kt                (no signature change — already suspend)
      DeleteCarUseCase.kt                (no signature change — already suspend)
      SaveChargeEventUseCase.kt          (no signature change — already suspend)
      DeleteChargeEventUseCase.kt        (no signature change — already suspend)
  di/
    DataModule.kt                        (modified — add bindings for DriveAuthManager + DriveRemoteSource)
    DomainModule.kt                      (modified — rebind BackupScheduler + BackupRepository)
    WorkerModule.kt                      (new — provides WorkManager + Configuration)
  ui/
    settings/
      SettingsFragment.kt                (rewritten — Drive toggle + last-backup + ActivityResultLauncher)
      SettingsViewModel.kt               (rewritten — uiState + events flow)
  EVTrackerApp.kt                        (modified — implements Configuration.Provider)

app/src/main/AndroidManifest.xml         (modified — remove WorkManager auto-initializer via tools:node="remove")
app/src/main/res/layout/fragment_settings.xml  (rewritten — Drive section + placeholder remainder)
app/src/main/res/values/strings.xml      (modified — Drive UI strings, restore dialog text, error messages)
app/build.gradle.kts                     (modified — add androidx.hilt:hilt-work + hilt-compiler)
gradle/libs.versions.toml                (modified — add androidx-hilt-work + androidx-hilt-compiler aliases)

app/src/test/java/org/spsl/evtracker/
  data/backup/
    DriveBackupRepositoryTest.kt         (new)
    WorkManagerBackupSchedulerTest.kt    (new)
  ui/settings/
    SettingsViewModelTest.kt             (new)
  domain/usecase/
    RestoreBackupUseCaseTest.kt          (extended — uses real BackupSerializer round-trip with FakeDriveRemoteSource)
  testing/
    Fakes.kt                             (modified — add FakeDriveAuthManager, FakeDriveRemoteSource; expand FakeSettingsReader/Writer)

app/src/androidTest/java/org/spsl/evtracker/
  data/backup/
    DriveBackupWorkerTest.kt             (new — @HiltAndroidTest, TestListenableWorkerBuilder + Hilt-injected fakes)
```

---

## 4. Components

### 4.1 `DriveAuthManager` (domain interface)

```kotlin
package org.spsl.evtracker.domain.backup

interface DriveAuthManager {
    /**
     * Resolves an OAuth2 access token for `drive.appdata`.
     *
     * Returns:
     * - [AuthResult.Success] when consent has already been granted (silent path)
     * - [AuthResult.NeedsResolution] when the user must launch a consent intent;
     *   only the Activity-scoped instance produces this. The Worker calls
     *   [silentToken] which collapses NeedsResolution into Failed.
     * - [AuthResult.Failed] for transient errors (network, GMS unavailable) or revoked consent.
     */
    suspend fun authorize(): AuthResult

    /**
     * Like [authorize] but never returns NeedsResolution. Worker code uses this:
     * if consent is required the result is Failed and the worker returns Result.failure().
     */
    suspend fun silentToken(): AuthResult

    sealed class AuthResult {
        data class Success(val accessToken: String) : AuthResult()
        data class NeedsResolution(val intentSender: android.content.IntentSender) : AuthResult()
        data class Failed(val reason: String, val cause: Throwable? = null) : AuthResult()
    }
}
```

**Implementation** (`AndroidDriveAuthManager`, `@ActivityScoped`):

```kotlin
@ActivityScoped
class AndroidDriveAuthManager @Inject constructor(
    @ActivityContext private val context: Context
) : DriveAuthManager {

    private val client = Identity.getAuthorizationClient(context)

    override suspend fun authorize(): AuthResult = await { interactive = true }
    override suspend fun silentToken(): AuthResult = await { interactive = false }

    private suspend fun await(block: AwaitOpts.() -> Unit): AuthResult { /* … */ }
}
```

The Worker variant uses an `@Provides` that constructs a singleton-scoped `AndroidDriveAuthManager` with `@ApplicationContext` — Authorization API works with either context for silent calls.

### 4.2 `DriveRemoteSource` (domain interface)

```kotlin
package org.spsl.evtracker.domain.backup

interface DriveRemoteSource {
    /** Returns the Drive fileId of the existing evtracker_backup.json in the App Data folder, or null. */
    suspend fun findBackupFileId(accessToken: String): String?

    /** Creates a new evtracker_backup.json in the App Data folder. Returns the new fileId. */
    suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String

    /** Replaces the body of an existing fileId. */
    suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray)

    /** Downloads the body of fileId. */
    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray
}
```

**Implementation** (`GoogleDriveRemoteSource`, `@Singleton`):

- Uses `Drive.Builder(NetHttpTransport(), GsonFactory(), HttpRequestInitializer { req -> req.headers.authorization = "Bearer $token" })`.
- File metadata: `{ "name": "evtracker_backup.json", "parents": ["appDataFolder"] }`.
- List query: `spaces=appDataFolder`, `q=name='evtracker_backup.json' and trashed=false`, `fields=files(id)`.
- Create: `files().create(metadata, ByteArrayContent("application/json", jsonBytes)).setFields("id").execute()`.
- Update: `files().update(fileId, null /* no metadata change */, ByteArrayContent("application/json", jsonBytes)).execute()`.
- Download: `files().get(fileId).executeMediaAsInputStream()` → `readBytes()`.
- All calls run on `Dispatchers.IO`. Errors propagate as `IOException`.

The token is passed per-call rather than baked into the singleton because tokens expire and the auth manager refreshes them on each backup.

### 4.3 `DriveBackupRepository` (replaces `NoOpBackupRepository`)

```kotlin
@Singleton
class DriveBackupRepository @Inject constructor(
    private val auth: DriveAuthManager,           // singleton-scoped variant
    private val remote: DriveRemoteSource,
    private val serializer: BackupSerializer,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
) : BackupRepository {

    override suspend fun backupCurrentData() {
        val token = (auth.silentToken() as? AuthResult.Success)?.accessToken
            ?: throw DriveAuthRequiredException()
        val cars = carReader.observeAll().first()
        val events = cars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val locations = locationReader.observeAll().first()
        val json = serializer.toJson(BackupData.fromEntities(cars, events, locations))
        val bytes = json.toByteArray(Charsets.UTF_8)
        val existing = remote.findBackupFileId(token)
        if (existing == null) remote.createBackup(token, bytes)
        else remote.updateBackup(token, existing, bytes)
    }

    override suspend fun readRemoteBackup(): String? {
        val token = (auth.silentToken() as? AuthResult.Success)?.accessToken
            ?: throw DriveAuthRequiredException()
        val fileId = remote.findBackupFileId(token) ?: return null
        return remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }
}

class DriveAuthRequiredException : IOException("Drive consent required or revoked")
```

`DriveAuthRequiredException` is the sentinel the Worker maps to `Result.failure()`. All other `IOException`s map to `Result.retry()`.

### 4.4 `WorkManagerBackupScheduler` (replaces `NoOpBackupScheduler`)

`BackupScheduler.kt` becomes:

```kotlin
package org.spsl.evtracker.domain.backup

interface BackupScheduler {
    /**
     * Suspending so implementations can read DataStore for the `driveEnabled` gate.
     *
     * **Contract:** implementations own the gate. If `driveEnabled` is false, the
     * implementation MUST no-op rather than schedule a Worker. Use cases call this
     * unconditionally after every persisted state change — they do NOT read driveEnabled.
     */
    suspend fun enqueueBackup()
}
```

Implementation:

```kotlin
@Singleton
class WorkManagerBackupScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val settingsReader: SettingsReader,
) : BackupScheduler {

    override suspend fun enqueueBackup() {
        if (!settingsReader.driveEnabled.first()) return
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "drive_backup"
    }
}
```

`DESIGN.md §8`'s `setInitialDelay(5s)` from `AGENT_INSTRUCTIONS.md §7.2` is intentionally dropped — the network constraint already debounces in practice and the 5 s delay is awkward in tests; if this turns out to be too aggressive in the field, F can add it back.

### 4.5 `DriveBackupWorker`

```kotlin
@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsWriter: SettingsWriter,
    private val clock: () -> Long,                  // injectable for tests
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        backupRepository.backupCurrentData()
        settingsWriter.setLastBackupAt(clock())
        Result.success()
    } catch (e: DriveAuthRequiredException) {
        Result.failure()
    } catch (e: IOException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    } catch (e: BackupVersionMismatch) {
        Result.failure()
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
```

`clock` is provided via Hilt with a default `{ System.currentTimeMillis() }`; tests override it.

### 4.6 `EVTrackerApp` + `WorkerModule`

```kotlin
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
        // … existing theme code unchanged …
    }
}
```

Manifest:

```xml
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
```

`WorkerModule`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object WorkerModule {
    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    @Provides @Singleton
    fun provideClock(): () -> Long = { System.currentTimeMillis() }
}
```

### 4.7 `SettingsFragment` + `SettingsViewModel` (Drive section only)

`SettingsUiState`:

```kotlin
data class SettingsUiState(
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
)

sealed class SettingsEvent {
    data class LaunchConsent(val intentSender: IntentSender) : SettingsEvent()
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()
}
```

`SettingsViewModel` orchestrates:

1. `onToggleDrive(checked)`:
   - If `checked == false`: `settingsWriter.setDriveEnabled(false)`; `WorkManager.cancelUniqueWork("drive_backup")`. Done.
   - If `checked == true`:
     - `auth.authorize()` →
       - `Success(token)` → `setDriveEnabled(true)`; call `readRemoteBackup()` (`null` → `enqueueBackup()`; non-null → emit `ShowRestorePrompt`)
       - `NeedsResolution(intentSender)` → emit `LaunchConsent(intentSender)`; `Fragment.ActivityResultLauncher` launches; on result success, the fragment calls `viewModel.onConsentResult(success = true)`, which restarts the toggle-on flow
       - `Failed(reason)` → emit `ShowError`
2. `onConfirmRestore()`: invoke `RestoreBackupUseCase`. On `Success` emit `RestoreSucceeded`; on `VersionMismatch` emit `ShowError(R.string.backup_version_mismatch)`.
3. `onSkipRestore()`: just `enqueueBackup()` (initial backup of local state).

Fragment renders:
- A `MaterialSwitchPreference`-equivalent row (or plain `MaterialSwitch` inside a `LinearLayout`) bound to `uiState.driveEnabled`.
- Below it: "Last backup: <relative time>" via `DateFormat.formatRelative(uiState.lastBackupAt)`. When null, shows "Never".
- The remaining settings rows (theme, units, currency, reset, CSV, manage locations) are rendered as inert disabled rows with text "Coming in next update" — F replaces them.
- `repeatOnLifecycle(STARTED)` collector on `events`, dispatching to `MaterialAlertDialogBuilder` (restore prompt) or `IntentSenderRequest.Builder` (consent) or `Snackbar` (error).

### 4.8 `SettingsRepository` extensions

```kotlin
override val driveEnabled: Flow<Boolean> =
    dataStore.data.map { it[PreferenceKeys.DRIVE_ENABLED] ?: false }

override val lastBackupAt: Flow<Long?> =
    dataStore.data.map { it[PreferenceKeys.LAST_BACKUP_AT] }

override suspend fun setLastBackupAt(epochMs: Long) {
    dataStore.edit { it[PreferenceKeys.LAST_BACKUP_AT] = epochMs }
}
```

Preference key:

```kotlin
val LAST_BACKUP_AT = longPreferencesKey("lastBackupAt")
```

### 4.9 DI changes in `DomainModule`

- `bindBackupScheduler(impl: WorkManagerBackupScheduler): BackupScheduler` — replaces `NoOpBackupScheduler` binding.
- `bindBackupRepository(impl: DriveBackupRepository): BackupRepository` — replaces `NoOpBackupRepository` binding.

In a separate **`DataModule`** (or new `BackupModule`):

- `@Binds bindDriveRemoteSource(impl: GoogleDriveRemoteSource): DriveRemoteSource`
- `@Binds bindDriveAuthManager(impl: AndroidDriveAuthManager): DriveAuthManager` — note: requires an `@ActivityScoped` module installed in `ActivityComponent` (see §4.10) AND a singleton-scoped variant for the Worker.

### 4.10 The two `DriveAuthManager` instances

The Activity needs an Activity-scoped `DriveAuthManager` (so consent intents can resolve to the right Activity). The Worker needs a singleton-scoped one (no Activity context). Solution:

- `data/backup/AndroidDriveAuthManager.kt` is the single class; it accepts a `Context` (no `@ActivityContext` qualifier).
- Two Hilt modules:
  - `ActivityBackupModule` (`@InstallIn(ActivityComponent::class)`): provides `DriveAuthManager` from `@ActivityContext Context`. Used by `SettingsViewModel`.
  - `SingletonBackupModule` (`@InstallIn(SingletonComponent::class)`): provides `DriveAuthManager` from `@ApplicationContext Context`. Used by `DriveBackupRepository` (which is `@Singleton`).
- Both produce `AuthResult` correctly; only the Activity-scoped one will ever return `NeedsResolution` because the user only triggers interactive consent from Settings.

This avoids the "Activity-scoped binding leaks into Worker" trap.

---

## 5. Sequences

### 5.1 Toggle Drive ON, no remote backup

```
User flips switch ON
  ↓
SettingsFragment.onCheckedChange → viewModel.onToggleDrive(true)
  ↓
ViewModel: auth.authorize()
  ↓
AuthorizationResult silent success → AuthResult.Success(token)
  ↓
ViewModel: settingsWriter.setDriveEnabled(true)
ViewModel: backupRepository.readRemoteBackup() → null
ViewModel: backupScheduler.enqueueBackup()
  ↓
WorkManagerBackupScheduler reads driveEnabled=true → enqueues drive_backup
  ↓
DriveBackupWorker.doWork
  → backupRepository.backupCurrentData() → uploads via Drive REST
  → settingsWriter.setLastBackupAt(now)
  → Result.success()
  ↓
SettingsFragment recomposes: "Drive backup ON · Last backup: just now"
```

### 5.2 Toggle Drive ON, remote backup exists

```
User flips switch ON
  ↓
ViewModel: auth.authorize() → Success(token)
  ↓
ViewModel: setDriveEnabled(true); readRemoteBackup() → JSON
  ↓
ViewModel: parse exportedAt; emit ShowRestorePrompt("April 25, 2026 at 10:00 AM")
  ↓
Fragment: MaterialAlertDialog "Found backup from April 25, 2026 at 10:00 AM. This will replace any data already on this device. Restore?"
  ↓
User taps Restore → viewModel.onConfirmRestore()
  ↓
ViewModel: RestoreBackupUseCase()
  → readRemoteBackup() (cached on parsed result? No — re-fetches; safe and small)
  → snapshotWriter.write(currentLocalJson) → cacheDir/last_overwritten_backup.json
  → transactionRunner.replaceAll(newCars, newEvents, newLocations)
  → setDriveEnabled(true)  ← already true; no-op
  → backupScheduler.enqueueBackup()
  ↓
ViewModel: emit RestoreSucceeded → Snackbar "Restored 1 car · 14 events · 3 locations"
```

### 5.3 Toggle Drive ON, consent required

```
User flips switch ON
  ↓
ViewModel: auth.authorize()
  ↓
AuthorizationResult.hasResolution() = true → AuthResult.NeedsResolution(intentSender)
  ↓
ViewModel: emit LaunchConsent(intentSender); uiState.isAuthInFlight = true
  ↓
Fragment: ActivityResultLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
  ↓
System consent sheet — user grants
  ↓
ActivityResult callback → viewModel.onConsentResult(granted = true)
  ↓
ViewModel re-runs the auth.authorize() → now Success silently → continues §5.1 or §5.2 flow
```

### 5.4 Auto-backup after charge save

```
User saves charge in ChargeEdit
  ↓
SaveChargeEventUseCase.invoke(...)
  → ...DB writes...
  → backupScheduler.enqueueBackup()  ← NOW SUSPEND
  ↓
WorkManagerBackupScheduler:
  → driveEnabled.first() = true → enqueueUniqueWork(REPLACE)
  ↓
DriveBackupWorker.doWork (after network constraint met)
  → silentToken() → Success(token)
  → readers gather all data → BackupSerializer → DriveRemoteSource.update/create
  → setLastBackupAt(now)
  → Result.success()
```

If 5 charges save in quick succession, REPLACE collapses them: only one upload runs (with all 5 events present).

### 5.5 Toggle Drive OFF

```
User flips switch OFF
  ↓
viewModel.onToggleDrive(false)
  → settingsWriter.setDriveEnabled(false)
  → workManager.cancelUniqueWork("drive_backup")
  ↓
Future use-case calls to enqueueBackup() see driveEnabled=false → no-op
```

The remote backup file is intentionally NOT deleted on toggle-off. The user can re-enable Drive later and choose to restore.

---

## 6. Error handling

### 6.1 Worker error matrix

| Trigger | `AuthResult.silentToken()` | `DriveRemoteSource` outcome | Worker `Result` |
|---|---|---|---|
| Happy path | `Success(token)` | success | `success`, `setLastBackupAt(now)` |
| Consent revoked / never granted | `Failed` or `NeedsResolution` (latter is impossible from singleton-scoped manager but treat the same) | n/a | `failure` (no retry — needs UI) |
| GMS unavailable | `Failed("GMS unavailable")` | n/a | `failure` |
| Network down (HTTP transport throws) | `Success(token)` | `IOException` | `retry` while `runAttemptCount < 5`; else `failure` |
| 401/403 from Drive | `Success(token)` | `HttpResponseException(401|403)` | `failure` (caught as IOException; reauth needed) |
| 5xx from Drive | `Success(token)` | `IOException` | `retry` |
| Backup version mismatch on read | n/a (write-only path) | n/a | n/a |

### 6.2 Settings UI error messages

- `R.string.drive_auth_failed` — "Couldn't sign in to Google Drive. Try again."
- `R.string.drive_consent_cancelled` — "Drive backup not enabled — consent was cancelled."
- `R.string.drive_network_error` — "No network connection. Backup will retry when you're online."
- `R.string.drive_remote_backup_too_new` — "This backup was created by a newer version of EV Tracker. Update the app to restore."
- `R.string.drive_restore_failed` — "Couldn't restore backup. Local data is unchanged."

### 6.3 Multi-currency on restore

The `RestoreBackupUseCase` already imports the data verbatim. Multi-currency rendering is handled by `StatsCalculator` returning `null` cost stats and the Dashboard banner — no E-specific work needed.

---

## 7. Testing strategy

### 7.1 JVM tests (~20 new)

- **`WorkManagerBackupSchedulerTest` (4)**:
  - enqueues unique work when driveEnabled=true
  - no-ops when driveEnabled=false
  - rapid successive calls collapse into one work request (REPLACE policy)
  - constraint includes NetworkType.CONNECTED
- **`DriveBackupRepositoryTest` (6)** with `FakeDriveAuthManager` + `FakeDriveRemoteSource`:
  - happy path: existing fileId → updateBackup
  - happy path: no fileId → createBackup
  - serializer round-trip preserves all fields (asserts using BackupData equality)
  - silent token failure throws DriveAuthRequiredException
  - readRemoteBackup returns null when no fileId
  - readRemoteBackup returns body when fileId exists
- **`SettingsViewModelTest` (7)** with all fakes:
  - toggle ON when no auth → emits LaunchConsent
  - toggle ON when auth succeeds and no remote → enqueues backup, sets driveEnabled
  - toggle ON when remote backup exists → emits ShowRestorePrompt
  - confirmRestore → calls RestoreBackupUseCase → emits RestoreSucceeded
  - skipRestore → enqueues backup, no restore call
  - toggle OFF → cancels work + sets driveEnabled=false
  - lastBackupAt flow propagates into uiState
- **Extended `RestoreBackupUseCaseTest` (3 new)**:
  - end-to-end with a real `BackupSerializer` + `FakeDriveRemoteSource` seeded with the JSON from a captured `BackupData.fromEntities(...)`
  - replays an old `backup_version=2` payload and asserts `VersionMismatch(2)` (verifies the version guard from C still works through the Drive path)
  - restore calls `enqueueBackup()` post-completion (verifies plumbing through new suspend interface)

### 7.2 Instrumented tests (~1 new)

- **`DriveBackupWorkerTest`** — `@HiltAndroidTest` with a `@TestInstallIn(replaces = [BackupModule::class])` module that binds `FakeDriveAuthManager` + `FakeDriveRemoteSource`:
  - `TestListenableWorkerBuilder<DriveBackupWorker>(context).setWorkerFactory(hiltWorkerFactory).build().doWork()` returns `Result.success` on happy path
  - returns `Result.retry` on network failure
  - returns `Result.failure` on `DriveAuthRequiredException`

Existing instrumented tests (Migration tests, WizardFlowTest, DashboardFragmentTest, ChargeEditFragmentTest) remain untouched.

### 7.3 Test infrastructure additions to `Fakes.kt`

```kotlin
class FakeDriveAuthManager(
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token")
) : DriveAuthManager {
    var authorizeCallCount = 0; private set
    var silentCallCount = 0; private set
    override suspend fun authorize(): DriveAuthManager.AuthResult {
        authorizeCallCount++; return nextResult
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
        failNext?.let { throw it.also { failNext = null } }
        return fileId
    }
    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String {
        failNext?.let { throw it.also { failNext = null } }
        fileId = "fake-file-id"; body = jsonBytes; return fileId!!
    }
    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        failNext?.let { throw it.also { failNext = null } }
        check(this.fileId == fileId); body = jsonBytes
    }
    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        failNext?.let { throw it.also { failNext = null } }
        return body ?: throw IOException("no body")
    }

    fun seed(jsonBytes: ByteArray) { fileId = "fake-file-id"; body = jsonBytes }
    fun lastUploadedBytes(): ByteArray? = body
}
```

`FakeSettingsReader`/`FakeSettingsWriter` gain `driveEnabled` and `lastBackupAt` `MutableStateFlow`s. `FakeBackupScheduler.enqueueBackup` becomes `suspend`.

### 7.4 Compatibility test sweep

Because `BackupScheduler.enqueueBackup` becomes suspend, the implementer must update the existing fake (`FakeBackupScheduler` in `Fakes.kt:118` ≈) and any in-test direct callers. Grep:
```
backupScheduler.enqueueBackup\(\)
```
returns 7 production sites (all already in suspend functions) and ~6 test sites (all already in `runTest { ... }` blocks). Mechanical conversion.

---

## 8. Risk and rollout

### 8.1 Known risks

- **Silent token failure on legitimate users.** If Authorization API changes its behavior (Google has rev'd this surface twice in five years), `silentToken()` could spuriously return `NeedsResolution` even after consent. Mitigation: `Worker` returns `failure` in that case; user re-toggles Drive in Settings. No data loss.
- **Drive REST quota.** App Data folder operations are charged against the per-user-per-app quota. Normal usage (one upload per change-burst, debounced via REPLACE) should stay well under limits. Worst case: a power user editing 100 charges in a session triggers 100 enqueues that collapse to ~1–10 actual uploads. We do not implement explicit rate limiting in E.
- **OAuth client SHA-1 mismatch.** Already covered by `GOOGLE_CLOUD_SETUP.md`. The first manual test on a new keystore fails fast with "Sign-in failed".
- **Manifest auto-init removal break.** Removing the `WorkManagerInitializer` meta-data via `tools:node="remove"` is the canonical approach but requires `xmlns:tools` in the manifest root. Add it if missing. If other components register WorkManager workers in the future, all must use the Hilt factory or be added to the `Configuration`.

### 8.2 Rollout

E ships as one merged feature branch (`feat/drive-backup-sub-project-e`) following the same A/B/C/D pattern. Until merged, `main` stays usable with the existing no-op bindings. After merge, manual smoke per §2.3 step 4 is the gate.

### 8.3 Backward compatibility

- A user running an A/B/C/D build who later updates to E: their local data is unchanged; the Drive switch defaults to OFF; first toggle-on triggers the standard consent → no-remote → initial-backup path. No migration needed.
- The `backup_version=3` schema is unchanged. Restore from a v3 backup made on E or earlier works identically.

---

## 9. Open questions (for spec review)

None at draft time. The five Q&A in brainstorming locked architecture. If new questions arise during implementation, they go into the plan as task-level decisions, not back into this spec.

---

## 10. Spec self-review checklist

- ✅ Placeholder scan — no TBD/TODO; every component has a code-level interface or method list.
- ✅ Internal consistency — `BackupScheduler` suspend conversion is reflected in §1.3, §3, §4.4, §4.5, §7.4.
- ✅ Scope check — single sub-project, single feature branch. Out-of-scope table is precise.
- ✅ Ambiguity check — the two `DriveAuthManager` instances (Activity-scoped vs Singleton-scoped) are explicitly addressed in §4.10. The `setInitialDelay(5s)` from `AGENT_INSTRUCTIONS.md §7.2` is explicitly dropped in §4.4.
