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

- **Auth seam.** `domain/backup/DriveAuthManager.kt` (interface) plus `data/backup/AndroidDriveAuthManager.kt` (Authorization API implementation). Single `@Singleton` binding with `@ApplicationContext`; the Authorization API client itself doesn't need an Activity to construct or call. The Activity-bound piece is launching the *consent resolution intent*, and that's owned by `SettingsFragment` via `ActivityResultLauncher<IntentSenderRequest>` — not by the auth manager. The Worker uses the same singleton instance via `silentToken()`.
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
      AndroidDriveAuthManager.kt         (new — @Singleton, @ApplicationContext, wraps Identity.getAuthorizationClient)
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
    BackupModule.kt                      (new — @Binds for DriveAuthManager and DriveRemoteSource; @InstallIn(SingletonComponent::class))
    DomainModule.kt                      (modified — rebind BackupScheduler + BackupRepository)
    WorkerModule.kt                      (new — provides WorkManager + Clock)
  ui/
    settings/
      SettingsFragment.kt                (rewritten — owns DriveAuthManager interaction + ActivityResultLauncher; feeds plain results to VM)
      SettingsViewModel.kt               (rewritten — pure state machine; no DriveAuthManager dependency, no IntentSender exposure)
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
     * - [AuthResult.NeedsResolution] when the user must launch a consent intent.
     *   The caller (SettingsFragment) launches the [IntentSender] via
     *   `ActivityResultLauncher<IntentSenderRequest>` and re-invokes auth on result.
     * - [AuthResult.Failed] for transient errors (network, GMS unavailable) or revoked consent.
     *
     * Both interactive and silent callers (SettingsFragment for the toggle-on flow,
     * DriveBackupRepository for backup/read) hit the same singleton instance.
     * `silentToken()` is a thin wrapper that collapses NeedsResolution into Failed
     * for the Worker path that has no Activity to run resolution from.
     */
    suspend fun authorize(): AuthResult

    /**
     * Like [authorize] but never returns NeedsResolution. Used by DriveBackupRepository
     * when called from the Worker: if consent is required, returns Failed and the
     * worker returns Result.failure().
     */
    suspend fun silentToken(): AuthResult

    sealed class AuthResult {
        data class Success(val accessToken: String) : AuthResult()
        data class NeedsResolution(val intentSender: android.content.IntentSender) : AuthResult()
        data class Failed(val reason: String, val cause: Throwable? = null) : AuthResult()
    }
}
```

**Implementation** (`AndroidDriveAuthManager`, `@Singleton`, `@ApplicationContext`):

```kotlin
@Singleton
class AndroidDriveAuthManager @Inject constructor(
    @ApplicationContext context: Context
) : DriveAuthManager {

    private val client = Identity.getAuthorizationClient(context)

    override suspend fun authorize(): AuthResult = awaitAuthorize()
    override suspend fun silentToken(): AuthResult = when (val r = awaitAuthorize()) {
        is AuthResult.NeedsResolution -> AuthResult.Failed("consent required")
        else -> r
    }

    private suspend fun awaitAuthorize(): AuthResult = /* suspendCancellableCoroutine wrapping client.authorize(...) */
}
```

The Authorization API client is constructable from `@ApplicationContext`; the Activity is only needed at the *launch site* of the returned `IntentSender`, which is `SettingsFragment`'s `ActivityResultLauncher<IntentSenderRequest>` — not the auth manager's responsibility.

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
    private val auth: DriveAuthManager,
    private val remote: DriveRemoteSource,
    private val serializer: BackupSerializer,
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val locationReader: LocationReader,
) : BackupRepository {

    override suspend fun backupCurrentData() = translatingAuthErrors {
        val token = requireToken()
        val cars = carReader.observeAll().first()
        val events = cars.flatMap { chargeEventQueries.getAllForCarSorted(it.id) }
        val locations = locationReader.observeAll().first()
        val json = serializer.toJson(BackupData.fromEntities(cars, events, locations))
        val bytes = json.toByteArray(Charsets.UTF_8)
        val existing = remote.findBackupFileId(token)
        if (existing == null) remote.createBackup(token, bytes)
        else remote.updateBackup(token, existing, bytes)
    }

    override suspend fun readRemoteBackup(): String? = translatingAuthErrors {
        val token = requireToken()
        val fileId = remote.findBackupFileId(token) ?: return@translatingAuthErrors null
        remote.downloadBackup(token, fileId).toString(Charsets.UTF_8)
    }

    private suspend fun requireToken(): String =
        (auth.silentToken() as? DriveAuthManager.AuthResult.Success)?.accessToken
            ?: throw DriveAuthRequiredException()

    /**
     * Translate Drive HTTP errors at the boundary:
     * - 401: always auth (token expired/invalid).
     * - 403: ONLY auth when the error reason is auth-related (`appNotAuthorized`,
     *   `insufficientFilePermissions`, `insufficientPermissions`, `forbidden`).
     *   Drive also uses 403 for retryable conditions: `rateLimitExceeded`,
     *   `userRateLimitExceeded`, `quotaExceeded` — those propagate as IOException
     *   so the Worker retries with exponential backoff.
     * - Anything else (5xx, transport IOException) propagates unchanged.
     *
     * Keeps the Worker's error rule two-branch: DriveAuthRequiredException →
     * Result.failure(); other IOException → Result.retry().
     */
    private inline fun <T> translatingAuthErrors(block: () -> T): T = try {
        block()
    } catch (e: HttpResponseException) {
        when {
            e.statusCode == 401 -> throw DriveAuthRequiredException()
            e.statusCode == 403 && isAuthReason(e) -> throw DriveAuthRequiredException()
            else -> throw e   // 403/quota, 5xx, etc. — let Worker retry
        }
    }

    private fun isAuthReason(e: HttpResponseException): Boolean {
        val reason = parseFirstErrorReason(e.content) ?: return true   // unknown reason → conservative: treat as auth
        return reason in AUTH_REASONS
    }

    /** Drive error bodies: `{"error":{"errors":[{"reason":"…"}], …}}`. Returns the first reason string. */
    private fun parseFirstErrorReason(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = com.google.gson.JsonParser.parseString(body).asJsonObject
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
    }
}

class DriveAuthRequiredException : IOException("Drive consent required or revoked")
```

`HttpResponseException` is `com.google.api.client.http.HttpResponseException` — a subclass of `IOException` thrown by the Drive client for HTTP-level errors. Translation happens once, here, so the Worker never has to inspect HTTP status codes. The `403 + reason` discrimination is the *only* place that knows Drive's error vocabulary; everywhere else (Worker, repository callers, tests) deals with the two-class hierarchy `DriveAuthRequiredException` (auth) vs plain `IOException` (transient).

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

The 5-second `setInitialDelay` (matching `AGENT_INSTRUCTIONS.md §7.2`) gives `REPLACE` a debounce window: a burst of saves within 5 s collapses to a single upload because the previously enqueued work is replaced before it starts running. Note this only collapses *enqueue bursts* — if a worker is already executing when a new save arrives, REPLACE cancels the running worker and enqueues a fresh one, so there's still a chance of two uploads on long-running tasks. The CONNECTED network constraint does *not* contribute debouncing; on an online device, work begins as soon as the initial delay elapses.

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

The auth-side split: **the Fragment owns the `DriveAuthManager` interaction** (and the `ActivityResultLauncher`); the ViewModel is a pure state machine that consumes plain results. This avoids the Hilt scoping mismatch (a `@HiltViewModel` lives in `ViewModelComponent`, which is *not* a child of `ActivityComponent`, so Activity-scoped bindings can't be injected) and keeps `IntentSender` out of the ViewModel layer entirely.

`SettingsUiState`:

```kotlin
data class SettingsUiState(
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    val pendingRestoreLabel: String? = null   // non-null while restore prompt is on screen
)

sealed class SettingsEvent {
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()
}
```

`SettingsEvent.LaunchConsent` is **deliberately absent** — the Fragment receives `IntentSender` directly from `auth.authorize()` without round-tripping through the ViewModel.

**Critical invariant: `driveEnabled = true` is set ONLY after the user has finalized Replace or Skip.** Setting it earlier (e.g. immediately after auth success when a remote snapshot exists) creates a race where any local mutation while the restore prompt is on screen could enqueue an upload that overwrites the remote snapshot the user is still deciding to restore from.

`SettingsViewModel` API:

1. `onToggleDriveOff()`: `settingsWriter.setDriveEnabled(false)`; `workManager.cancelUniqueWork("drive_backup")`. Done.
2. `onDriveAuthGranted()` — called by the Fragment after `DriveAuthManager` returned Success (silent or post-consent):
   - `uiState.value = uiState.value.copy(isAuthInFlight = true)`.
   - Call `backupRepository.readRemoteBackup()` inside a try/catch (it is documented to throw — see §4.3).
   - **`isAuthInFlight` is cleared at the end of the call** in *every* branch — by the time control returns from this method (or the prompt is emitted), auth and the probe are both finished and any waiting indicator on the toggle should drop:
     - **returns `null`** (no remote snapshot): set `driveEnabled = true`, `enqueueBackup()`, clear `isAuthInFlight`.
     - **returns non-null JSON**: parse `exportedAt` into a label; set `pendingRestoreLabel = label`; clear `isAuthInFlight`; emit `ShowRestorePrompt(label)`. **Do NOT set `driveEnabled = true` yet.** Local mutations during this window stay silent because `driveEnabled` is still false. The Fragment dropping `isAuthInFlight` re-enables the switch underneath the dialog so the user can also dismiss by toggling off.
     - **throws `DriveAuthRequiredException`** (consent revoked between authorize and the probe): emit `ShowError(R.string.drive_auth_failed)`; clear `isAuthInFlight`; leave `driveEnabled = false`. The toggle returns to OFF on the next `uiState` collect.
     - **throws any other `IOException`** (network / Drive 5xx / quota): emit `ShowError(R.string.drive_network_error)`; clear `isAuthInFlight`; leave `driveEnabled = false`. The user can retry by toggling again.
3. `onDriveAuthFailed(@StringRes msgRes: Int)` — Fragment translates `Failed` / consent-cancelled into a string resource and calls this. Emits `ShowError`; clears isAuthInFlight.
4. `onConfirmRestore()` — only valid while `pendingRestoreLabel != null`. Invokes `RestoreBackupUseCase` (which sets `driveEnabled = true` itself on Success). On `Success` emit `RestoreSucceeded`; on `VersionMismatch` emit `ShowError`. Clears `pendingRestoreLabel`.
5. `onSkipRestore()` — only valid while `pendingRestoreLabel != null`. *Now* set `driveEnabled = true` and `enqueueBackup()` (uploads the local snapshot, intentionally overwriting remote per user's explicit choice). Clears `pendingRestoreLabel`.
6. `onRestorePromptDismissed()` — back-pressed without choosing. Treat as Skip-equivalent? **No** — leave `driveEnabled = false` and clear `pendingRestoreLabel`. The user can re-toggle to retry. This prevents the "user dismissed the dialog by accident → unintended overwrite" footgun.

`SettingsFragment` responsibilities:

- Field-injects `DriveAuthManager` directly (`@Inject lateinit var auth: DriveAuthManager`). The auth manager is `@Singleton`; field injection on a `@AndroidEntryPoint` Fragment is supported.
- Owns `ActivityResultLauncher<IntentSenderRequest>`.
- On user toggle ON:
  ```
  viewLifecycleOwner.lifecycleScope.launch {
      when (val result = auth.authorize()) {
          is AuthResult.Success         -> viewModel.onDriveAuthGranted()
          is AuthResult.NeedsResolution -> consentLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
          is AuthResult.Failed          -> viewModel.onDriveAuthFailed(R.string.drive_auth_failed)
      }
  }
  ```
- On `consentLauncher` callback: if `RESULT_OK`, re-run `auth.authorize()` (now silent Success); if anything else, call `viewModel.onDriveAuthFailed(R.string.drive_consent_cancelled)`.
- On user toggle OFF: `viewModel.onToggleDriveOff()`.
- Renders:
  - `MaterialSwitch` bound to `uiState.driveEnabled`.
  - "Last backup: <date | Never>" via `DateFormatter`.
  - The remaining settings rows (theme, units, currency, reset, CSV, manage locations) as inert disabled rows.
  - `repeatOnLifecycle(STARTED)` collector on `events`: `ShowRestorePrompt` → `MaterialAlertDialogBuilder` (with `setCancelable(true)` and `setOnDismissListener { viewModel.onRestorePromptDismissed() }`); `RestoreSucceeded` → `Snackbar`; `ShowError` → `Snackbar`.

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

In a new **`BackupModule`** (`@InstallIn(SingletonComponent::class)`, `abstract class`):

- `@Binds @Singleton abstract fun bindDriveRemoteSource(impl: GoogleDriveRemoteSource): DriveRemoteSource`
- `@Binds @Singleton abstract fun bindDriveAuthManager(impl: AndroidDriveAuthManager): DriveAuthManager`

Single binding, single component scope. The Authorization API client constructed with `@ApplicationContext` works for all callers; the Activity-bound part of the auth flow (launching the consent `IntentSender`) lives in `SettingsFragment` via `ActivityResultLauncher`, not in any Hilt-bound class.

### 4.10 (removed)

The earlier draft of this spec proposed two `DriveAuthManager` provider modules — one in `ActivityComponent`, one in `SingletonComponent` — to give `SettingsViewModel` an activity-scoped instance. That approach is **invalid**: a `@HiltViewModel` lives in `ViewModelComponent`, which is *not* a child of `ActivityComponent`, so an `ActivityComponent` binding cannot be injected into the ViewModel. The corrected design (above) keeps a single `@Singleton` binding and moves interactive auth into `SettingsFragment`, which can both inject the singleton instance and own the `ActivityResultLauncher` needed to launch consent.

---

## 5. Sequences

### 5.1 Toggle Drive ON, no remote backup

```
User flips switch ON
  ↓
SettingsFragment: launches coroutine → auth.authorize()
  ↓
AuthorizationResult silent success → AuthResult.Success(token)
  ↓
Fragment: viewModel.onDriveAuthGranted()
  ↓
ViewModel: backupRepository.readRemoteBackup() → null
ViewModel: settingsWriter.setDriveEnabled(true)   ← only NOW, after no-remote confirmed
ViewModel: backupScheduler.enqueueBackup()
  ↓
WorkManagerBackupScheduler reads driveEnabled=true → enqueues drive_backup (5 s initial delay)
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
SettingsFragment: auth.authorize() → Success(token)
  ↓
Fragment: viewModel.onDriveAuthGranted()
  ↓
ViewModel: backupRepository.readRemoteBackup() → JSON  (driveEnabled STILL false; auto-backup gated)
ViewModel: parse exportedAt; pendingRestoreLabel = "April 25, 2026 at 10:00 AM"
ViewModel: emit ShowRestorePrompt(label)
  ↓
Fragment: MaterialAlertDialog "Found backup from April 25, 2026 at 10:00 AM. This will replace any data already on this device. Restore?"

  ── Branch A: user taps Restore ──
  ↓
viewModel.onConfirmRestore() → RestoreBackupUseCase()
  → snapshotWriter.write(currentLocalJson) → cacheDir/last_overwritten_backup.json
  → transactionRunner.replaceAll(newCars, newEvents, newLocations)
  → setDriveEnabled(true)   ← driveEnabled set HERE, by RestoreBackupUseCase, after replace committed
  → backupScheduler.enqueueBackup()
  ↓
ViewModel: emit RestoreSucceeded → Snackbar

  ── Branch B: user taps Skip ──
  ↓
viewModel.onSkipRestore()
  → settingsWriter.setDriveEnabled(true)   ← driveEnabled set HERE, after explicit skip
  → backupScheduler.enqueueBackup()        (uploads local snapshot, deliberately overwriting remote)

  ── Branch C: user dismisses dialog (back press) ──
  ↓
viewModel.onRestorePromptDismissed()
  → driveEnabled stays false; pendingRestoreLabel cleared; toggle resets to OFF on next state collect
```

The critical property: until the user finalizes Branch A, B, or C, `driveEnabled` remains `false`. The `WorkManagerBackupScheduler` therefore short-circuits any concurrent local mutation, and the remote snapshot stays intact while the prompt is on screen. This addresses the overwrite-race scenario flagged in `TEST_PLAN.md §5`.

### 5.3 Toggle Drive ON, consent required

```
User flips switch ON
  ↓
SettingsFragment: auth.authorize()
  ↓
AuthorizationResult has pendingIntent → AuthResult.NeedsResolution(intentSender)
  ↓
Fragment: consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
  ↓
System consent sheet — user grants
  ↓
ActivityResult callback (RESULT_OK) → Fragment re-runs auth.authorize()
  ↓
auth.authorize() → AuthResult.Success(token)   (silent — consent now cached)
  ↓
Fragment: viewModel.onDriveAuthGranted()  ← continues §5.1 or §5.2 flow
```

ActivityResult callback with anything other than `RESULT_OK` (user backed out of consent, system cancelled, etc.) → Fragment calls `viewModel.onDriveAuthFailed(R.string.drive_consent_cancelled)` directly, without re-invoking the auth manager.

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

If 5 charges save within the 5 s initial-delay window, `REPLACE` collapses the queue to a single upload that captures all 5 events at run time. If saves arrive *after* the worker has started executing, `REPLACE` cancels the in-flight worker and enqueues a fresh one — so a long-running upload concurrent with a save burst can produce two uploads. This is acceptable: at worst we double-write the same App Data file with the second write being authoritative.

### 5.5 Toggle Drive OFF

```
User flips switch OFF
  ↓
viewModel.onToggleDriveOff()
  → settingsWriter.setDriveEnabled(false)
  → workManager.cancelUniqueWork("drive_backup")
  ↓
Future use-case calls to enqueueBackup() see driveEnabled=false → no-op
```

The remote backup file is intentionally NOT deleted on toggle-off. The user can re-enable Drive later and choose Replace or Skip against the existing remote snapshot.

---

## 6. Error handling

### 6.1 Worker error matrix

`DriveBackupRepository` translates *only auth-class* Drive HTTP errors to `DriveAuthRequiredException` at the boundary (see §4.3 `translatingAuthErrors`). Quota and rate-limit responses are *not* auth — they pass through as `IOException` so the Worker retries with exponential backoff. The Worker's rule is two-branch: `DriveAuthRequiredException` → `failure`; every other `IOException` → `retry` (capped at 5 attempts, then `failure`).

| Trigger | At repository boundary | Reaches worker as | Worker `Result` |
|---|---|---|---|
| Happy path | `Success(token)` + 2xx | (no exception) | `success`, `setLastBackupAt(now)` |
| Consent revoked / never granted | `silentToken()` returns Failed | `DriveAuthRequiredException` | `failure` (no retry — user must re-toggle in Settings) |
| GMS unavailable on device | `silentToken()` returns Failed | `DriveAuthRequiredException` | `failure` |
| Drive 401 (token expired/invalid) | translated by repository | `DriveAuthRequiredException` | `failure` |
| Drive 403 with auth reason (`appNotAuthorized`, `insufficientFilePermissions`, `insufficientPermissions`, `forbidden`) | translated by repository | `DriveAuthRequiredException` | `failure` |
| Drive 403 with quota/rate reason (`rateLimitExceeded`, `userRateLimitExceeded`, `quotaExceeded`) | propagated as-is | `IOException` | `retry` while `runAttemptCount < 5`; else `failure` |
| Drive 403 with unknown reason / unparseable body | translated by repository (conservative) | `DriveAuthRequiredException` | `failure` |
| Network down (transport `IOException`) | propagated as-is | `IOException` (not `HttpResponseException`) | `retry` while `runAttemptCount < 5`; else `failure` |
| Drive 5xx | `HttpResponseException(5xx)` propagated as-is (subclass of IOException) | `IOException` | `retry` while `runAttemptCount < 5`; else `failure` |

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
- **`SettingsViewModelTest` (9)** with all fakes (the VM no longer touches `DriveAuthManager`, so all auth-side cases are simulated by calling `onDriveAuthGranted` / `onDriveAuthFailed` directly). Every branch of `onDriveAuthGranted` and `onDriveAuthFailed` asserts `isAuthInFlight == false` after the call, since the rule "by the time control returns, auth and probe are done" is the single most important loading-state invariant for the UI:
  - `onDriveAuthGranted` with no remote backup → sets driveEnabled=true, enqueues backup, **isAuthInFlight cleared**
  - `onDriveAuthGranted` with remote backup → emits `ShowRestorePrompt`, driveEnabled stays false, **isAuthInFlight cleared**, `pendingRestoreLabel` set
  - `onDriveAuthGranted` when `readRemoteBackup` throws `DriveAuthRequiredException` → emits `ShowError(drive_auth_failed)`, driveEnabled stays false, **isAuthInFlight cleared**
  - `onDriveAuthGranted` when `readRemoteBackup` throws `IOException` → emits `ShowError(drive_network_error)`, driveEnabled stays false, **isAuthInFlight cleared**
  - `onDriveAuthFailed` → emits `ShowError`, driveEnabled stays false, **isAuthInFlight cleared**
  - `onConfirmRestore` → invokes `RestoreBackupUseCase`, emits `RestoreSucceeded` (RestoreBackupUseCase sets driveEnabled=true), `pendingRestoreLabel` cleared
  - `onSkipRestore` → sets driveEnabled=true, enqueues backup, `pendingRestoreLabel` cleared
  - `onRestorePromptDismissed` → leaves driveEnabled=false, `pendingRestoreLabel` cleared
  - `onToggleDriveOff` → cancels work, sets driveEnabled=false
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

- **Silent token failure on legitimate users.** If Authorization API changes its behavior (Google has rev'd this surface twice in five years), `silentToken()` could spuriously return `Failed` even after consent. Mitigation: Worker returns `failure` in that case; user re-toggles Drive in Settings. No data loss.
- **Drive REST quota.** App Data folder operations are charged against the per-user-per-app quota. Normal usage (one upload per change-burst, debounced via the 5 s initial delay + `REPLACE`) should stay well under limits. Worst case: a power user editing 100 charges in a single session can produce up to ~20 uploads if the bursts span longer than the worker's run time. We do not implement explicit rate limiting in E.
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
- ✅ Internal consistency — `BackupScheduler` suspend conversion reflected in §1.3, §3, §4.4, §4.5, §7.4. Auth seam is a single `@Singleton` binding consistent across §1.3, §3, §4.1, §4.7, §4.9, §4.10. Sequence §5.3 lets the Fragment own the post-consent re-auth call (consistent with §4.7). The post-auth probe failure paths in §4.7 are mirrored by tests in §7.1. Worker error model is consistent between §4.3 (translation) and §6.1 (matrix), including the 403-quota carve-out.
- ✅ Scope check — single sub-project, single feature branch. Out-of-scope table is precise.
- ✅ Ambiguity check — `setInitialDelay(5 s)` is included in §4.4 with the rationale that it provides debounce for enqueue bursts (not running-worker overlap). `driveEnabled` is set only after Replace or Skip is finalized, never speculatively, per §4.7 and §5.2.

### 10.1 Review-cycle changes (2026-04-28 spec rev 2)

- DI: dropped the proposed `ActivityBackupModule` + `SingletonBackupModule` split. A `@HiltViewModel` lives in `ViewModelComponent`, which is *not* a child of `ActivityComponent`, so the original two-module design was uninjectable. Replaced with a single `@Singleton` `BackupModule` in `SingletonComponent` and moved the interactive auth call to `SettingsFragment` (which can field-inject the singleton manager and own the `ActivityResultLauncher`).
- Restore gate: the toggle-on flow no longer sets `driveEnabled = true` immediately after auth Success when a remote snapshot exists. `driveEnabled` is now set strictly inside `RestoreBackupUseCase` (Confirm branch) or `onSkipRestore` (Skip branch). This eliminates the overwrite race where local mutations during the prompt could enqueue an upload and clobber the remote snapshot the user is still deciding to restore from.
- Debounce: restored `setInitialDelay(5 s)` to match `AGENT_INSTRUCTIONS.md §7.2`. The earlier "network constraint debounces" rationale was incorrect — `NetworkType.CONNECTED` does not debounce on an already-online device.
- Worker error policy: `DriveBackupRepository` now translates Drive HTTP 401/403 to `DriveAuthRequiredException` at the boundary, so the Worker's two-branch error rule (auth-required → fail; everything else `IOException` → retry) is consistent across §4.3, §4.5, and §6.1.

### 10.2 Review-cycle changes (2026-04-28 spec rev 3)

- 403 narrowing: §4.3 and §6.1 now distinguish auth-class 403 (`appNotAuthorized`, `insufficientFilePermissions`, `insufficientPermissions`, `forbidden`) from quota/rate-limit 403 (`rateLimitExceeded`, `userRateLimitExceeded`, `quotaExceeded`). Only the former translates to `DriveAuthRequiredException`; the latter passes through as `IOException` so the Worker retries with exponential backoff. Unknown reasons fall back to auth-class for safety. Resolves the prior over-classification that would have turned transient quota errors into permanent worker failures with the wrong remediation path.
- Post-auth probe failure paths: §4.7 now explicitly enumerates what `onDriveAuthGranted` does when `readRemoteBackup` throws — `DriveAuthRequiredException` → `ShowError(drive_auth_failed)`, generic `IOException` → `ShowError(drive_network_error)`, both leaving `driveEnabled = false` and clearing `isAuthInFlight`. §7.1 covers each branch with a dedicated test.
- Stale references purged: §5.3 no longer says "ViewModel re-runs auth.authorize" — the Fragment owns the post-consent re-auth call. §7.1 `SettingsViewModelTest` cases are rewritten in terms of the rev-2 VM API (`onDriveAuthGranted` / `onDriveAuthFailed` / `onRestorePromptDismissed`); the obsolete `LaunchConsent` event no longer appears in any test.

### 10.3 Review-cycle changes (2026-04-28 spec rev 4)

- `isAuthInFlight` clearing: §4.7 now states explicitly that `onDriveAuthGranted` clears `isAuthInFlight` on **every** branch — including the remote-backup-found branch that previously left it stuck true while the prompt was on screen. Rule: by the time control returns from `onDriveAuthGranted` (or the prompt is emitted), auth and the probe are both finished; any toggle/progress indicator gated on `isAuthInFlight` should drop. §7.1 `SettingsViewModelTest` cases now assert `isAuthInFlight == false` on every branch of `onDriveAuthGranted` and `onDriveAuthFailed`.
