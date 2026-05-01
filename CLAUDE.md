# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`org.spsl.evtracker`) for logging EV charge events and analyzing efficiency/cost. Kotlin, MVVM with a domain/use-case layer plus narrow repositories, Gradle Kotlin DSL, and Hilt-based dependency injection. Min SDK 26, target/compile SDK 35, JDK 17. Room compiler runs via **KSP** (not kapt).

> **Status:** v1.0.1 tagged (`versionCode = 2`, `versionName = "1.0.1"`). All v1.0.0 sub-projects (A foundation/DI/Room, B repositories, C domain services + use cases, D Core UI: Dashboard/ChargeEdit/Cars/History, E Drive backup, F1 Settings + ManageLocations + reset use cases + startup auto-recovery, F2 Charts) and the M3 theming refactor are merged. Wizard, Dashboard, ChargeEdit, Cars, History, Settings, ManageLocations, and Charts are wired with a complete Material 3 token system (light + dark palettes seeded from `#1565C0`; tertiary overridden to a `#FB8C00` orange ramp for `docs/DESIGN.md §6` "DC orange"). JVM unit-test count: **275**. Instrumented suite compiles via `:app:assembleDebugAndroidTest` (running requires an emulator); Drive backup smoke per `docs/GOOGLE_CLOUD_SETUP.md` needs a Google account allow-listed on the OAuth consent screen. Release signing is wired through a gitignored `keystore.properties`; `.github/workflows/release.yml` builds, signs, verifies, and publishes the APK to a GitHub Release on every `v*` tag push.

**Post-v1 backlog refactors landed (newest first):**

- **TASK-07** (2026-05-01) — Drive backup error handling + retry. New `domain/backup/BackupResult` sealed class (`Success` / `AuthRequired` / `Failure(reason, cause?)`). `BackupRepository.backupCurrentData()` signature changes from `Unit` (throwing) to `BackupResult`. `DriveBackupRepository` runs its own bounded retry loop (3 attempts, exponential backoff `250 ms × 2^attempt`) for transient failures only — `IOException` (incl. `UnknownHostException`), HTTP 429, HTTP 5xx, HTTP 403 with quota/rate reasons. Non-recoverable cases short-circuit: 401 → `AuthRequired`; 403 auth reason → `AuthRequired`; **403 `storageQuotaExceeded` → `Failure("Drive storage full")`** (was previously misrouted to the conservative-auth fallback); unknown/unparseable 403 body → conservative `AuthRequired`. `DriveBackupWorker.doWork()` collapses to a `when (result)` translator — never returns `Result.retry()` to avoid amplifying the inner retry budget. `android.util.Log.e("DriveBackupRepository", ..., cause)` on every non-recoverable path; `app/build.gradle.kts` gains `testOptions.unitTests.isReturnDefaultValues = true` so JVM tests don't blow up on `Log.e`. The read path (`readRemoteBackup()`) keeps its existing throw-based contract — `SettingsViewModel` and `RestoreBackupUseCase` both already catch `DriveAuthRequiredException` / `IOException` and don't need changes. New `BackupResult.kt` (24 lines) + new tests cover storage-full vs auth, retry-then-success for 429 / 5xx / 403-quota / `UnknownHostException`, retry-exhaustion → `Failure`, and that auth and storage-full skip the retry loop. `FakeDriveRemoteSource` gains a `failTimes: Int = 1` budget + `failuresRaised: Int` counter so retry tests can drive multi-failure scenarios deterministically.
- **TASK-32** (2026-05-01) — toolchain bumped to **AGP 8.7.3 + Gradle 8.9**, both of which officially support `compileSdk = 35`, so the `android.suppressUnsupportedCompileSdk=35` workaround in `gradle.properties` (added during TASK-22) is removed. Kotlin 1.9.21 + KSP 1.9.21-1.0.16 + Hilt 2.50 stay on existing pins; the Kotlin 2.x / K2 migration stays out of scope. `:app:assembleDebug --warning-mode all` no longer prints the "tested up to compileSdk = 34" advisory or the "SDK XML version 4" parser warning.
- **TASK-17** (2026-05-01) — R8 keep-rule audit. `app/proguard-rules.pro` gains a defensive `-keep class com.github.mikephil.charting.** { *; }` + matching `-dontwarn` (MPAndroidChart ships zero consumer rules, verified via `app/build/outputs/mapping/release/configuration.txt`) and an evidence comment naming the three AAR-bundled rule files that already cover Hilt and Room (`hilt-android-2.50/proguard.txt`, `hilt-work-1.1.0/proguard.txt`, `room-runtime-2.6.1/proguard.txt`). Manual release-APK smoke matrix documented as `docs/TEST_PLAN.md §5b` — gate between "tag pushed → APK built" and "GitHub Release published".
- **TASK-25** (2026-05-01) — `chargeType: String` replaced with the `ChargeType` enum (`AC`, `DC_FAST`, `DC_ULTRA`). Room schema bumped v3 → v4 with `MIGRATION_3_4` rewriting legacy `'DC'` cells to `'DC_FAST'`; `@TypeConverters(ChargeTypeConverter)` registered on `AppDatabase`. `BackupData.CURRENT_VERSION` bumped 3 → 4; `BackupSerializer.fromJson` accepts `{3, 4}` and `ChargeTypeJsonAdapter` decodes legacy `"DC"` via `ChargeType.parseLegacy`. `StatsCalculator`, `ObserveDashboardStatsUseCase`, and `HistoryViewModel` filter via `ChargeType.AC` / `ChargeType.isDc`; `ChargeEditFragment` toggle buttons emit `ChargeType.AC` / `ChargeType.DC_FAST`; `HistoryAdapter` badge uses `chargeType.displayLabel()`.
- **TASK-11** (2026-05-01) — odometer regression UX. Create mode pre-fills `odometer` with `previousOdometerKm + 1.0` converted to display unit; `ChargeEditUiState` gains `previousOdometerKm`, `nextOdometerKm`, `odometerBelowPrevious`, `odometerAboveNext`. `setOdometer` recomputes the flags every keystroke (km/miles aware); `ChargeEditFragment` renders an inline `TextInputLayout.error` via parameterised strings `error_odometer_must_be_greater_than` / `error_odometer_must_be_less_than` and gates `chargeEditSave.isEnabled` on the flags. The use case's `OdometerNotIncreasing` fallback survives for same-`eventDate` races.
- **TASK-28** (2026-05-01) — every production `System.currentTimeMillis()` outside the canonical `DispatcherModule.provideNowProvider` binding removed. Entity defaults on `CarEntity.createdAt` / `ChargeEventEntity.createdAt` / `CustomLocationEntity.lastUsed` deleted; `BackupData.fromEntities`, `LocationWriter.recordUsage`, `DateRangeResolver.resolve` / `resolveCharts`, and `ChargeEditUiState.eventDateMillis` all require explicit values. `AddCarUseCase`, `SaveChargeEventUseCase`, `ObserveDashboardStatsUseCase`, `DriveBackupRepository`, `RestoreBackupUseCase`, and `ChargeEditViewModel` inject `NowProvider`. `ManageLocationsAdapter` takes a `nowProvider: () -> Long` constructor arg. `WorkerModule.provideClock(): () -> Long` deleted; `DriveBackupWorker` consumes `NowProvider` directly. New JVM `FakeNowProvider`.
- **Wizard disclaimer gate** (2026-05-01) — wizard now has a 4th page, `WizardPage4Fragment`, which surfaces the SPS-Lab acknowledgment + disclaimer body and a `MaterialSwitch` acceptance toggle. Finish is disabled until the switch is on; `WizardViewModel.UiState` gains `disclaimerAccepted`; `goNext` clamps at page 3; `WizardFlowTest` ticks the switch before Finish.
- **TASK-10** (2026-05-01) — in-app About / Info screen reachable from `Settings → About`, showing app + version, SPS-Lab acknowledgment with tappable links to sps-lab.org and cut.ac.cy, MIT license, disclaimer, and an open-source-libraries card. Bundled with the launcher icon pack drop-in — 20 PNG mipmaps replace the placeholder vector adaptive icon; `mipmap-anydpi-v26/ic_launcher{,_round}.xml` repointed at `@mipmap/...`; orphan vector drawables removed. New instrumented `AboutFragmentTest`.
- **TASK-29** (2026-05-01) — explicit `debug` build type with `applicationIdSuffix = ".debug"` and `versionNameSuffix = "-debug"`. `buildConfig = true` enabled, with three matched scaffolding fields — `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX` — declared on both build types. CI workflow gains `:app:assembleRelease` as a release smoke step. `GOOGLE_CLOUD_SETUP.md` Step 5b documents the third OAuth client needed for Drive on debug builds.
- **TASK-27** (2026-05-01) — bottom-nav visibility driven by a `hideBottomNav` per-destination argument in `nav_graph.xml`; `MainActivity` no longer references any specific destination ID for visibility decisions. New instrumented `MainActivityBottomNavTest` exercises dashboard ↔ chargeEdit transitions.
- **TASK-02** (2026-05-01) — safeguard KDoc on `DataResetTransactionRunner` and `RoomDataResetTransactionRunner` documenting that the destructive `clearAllTables()` is reachable only through `ResetAllDataUseCase`; calling it elsewhere bypasses the `resetInProgress` durable-flag protocol.
- **TASK-22** (2026-05-01) — `compileSdk` and `targetSdk` bumped to 35; `MainActivity` opts into edge-to-edge via `enableEdgeToEdge()` and pads its root `LinearLayout` by `WindowInsetsCompat.Type.systemBars() or displayCutout()` so the bottom nav and Snackbars stay above the gesture-nav indicator on Android 15+. (The follow-up toolchain bump that retired the suppression flag is TASK-32.)
- **TASK-24** (2026-05-01) — no concrete `data.repository.*` imports outside `di/`. `SettingsWriter` gained the atomic `completeSetup` method; `EVTrackerApp` consumes `SettingsReader`; `WizardViewModel` consumes `SettingsWriter`.
- **kwh/100km default flip** (2026-04-30) — preferences + Wizard + DashboardScreenState + ChartsScreenState now default to `kwh_per_100km`. Trend chart Y-axis switches transform on `primaryMetric` instead of just `distanceUnit`.
- **TASK-23** (2026-04-30) — `MainViewModel` owns startup auto-recovery state; sealed `StartupState`.
- **TASK-16** (2026-04-30) — ktlint + Android Lint CI gate.
- **TASK-01** (2026-04-30) — `@AggregationDispatcher` relocated from `di/` to `core/coroutines/`.

Repo root holds only `README.md`, `CLAUDE.md`, and build/config files. All project documentation lives under `docs/`:

- `docs/DESIGN.md` — canonical product + technical spec (v3). Source of truth when in conflict with anything else.
- `docs/GOOGLE_CLOUD_SETUP.md` — Drive API + OAuth Android client setup.
- `docs/BACKLOG.md` — open backlog of post-v1 refactors and new features (live).
- `docs/TEST_PLAN.md` — full test specification (all phases merged).
- `docs/AGENT_INSTRUCTIONS.md` — original implementation walkthrough used to bring the app up from an empty repo. **Historical**: all phases are merged.
- `docs/superpowers/specs/` and `docs/superpowers/plans/` — per-sub-project specs and plans (time-stamped, historical).

## Build & Test

```bash
./gradlew assembleDebug                        # target APK path: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                      # signed if keystore.properties exists, unsigned otherwise
./gradlew test                                 # JVM unit tests (~275)
./gradlew connectedAndroidTest                 # Espresso/Room — needs API 26+ device or emulator
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.UnitConverterTest.kmToMiles_positive"
```

Requires `ANDROID_HOME` set and Build Tools 35.

### Build types (TASK-29, merged 2026-05-01)

- **Debug** has `applicationIdSuffix = ".debug"` (so the runtime package becomes `org.spsl.evtracker.debug`) and `versionNameSuffix = "-debug"` (so `BuildConfig.VERSION_NAME` resolves to e.g. `"1.0.1-debug"`). Debug and release can be installed side-by-side on the same device.
- **`buildConfig = true`** is enabled in `buildFeatures`. `BuildConfig.VERSION_NAME`, `VERSION_CODE`, `DEBUG`, plus three custom fields — `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX` — resolve at compile time under `org.spsl.evtracker.BuildConfig` (the namespace, not the runtime package). The custom fields are scaffolding: declare consumers in the task that needs them. Both build types must declare every custom field — AGP fails the build if release omits a field that debug declares (or vice versa).
- **Drive on debug builds requires a third OAuth Android client** registered for `org.spsl.evtracker.debug` + the debug keystore SHA-1. See `docs/GOOGLE_CLOUD_SETUP.md` Step 5b. Until that client exists, Drive sign-in fails on debug builds; release is unaffected because release keeps `applicationId = org.spsl.evtracker`.

### Static analysis gate (TASK-16, merged 2026-04-30)

PRs and pushes to `main` are gated by `.github/workflows/ci.yml`. The same gate runs locally:

```bash
./gradlew ktlintCheck                          # ktlint 12.1.1 — Kotlin official style; auto-fix with ktlintFormat
./gradlew :app:lint                            # Android Lint, error-mode for HardcodedText/MissingTranslation/TypographyDashes/UnusedResources
./gradlew :app:testDebugUnitTest               # bundled into the same CI job
```

- Style is anchored by the repo-root `.editorconfig` (`ktlint_code_style = intellij_idea`, 4-space indent). The IDE's reformat output and ktlint's check agree.
- Pre-existing lint offenses are absorbed by `app/lint-baseline.xml`. **Only new violations break the build.** Regenerate the baseline only when retiring a rule (`./gradlew :app:updateLintBaseline`); do not regenerate to "clean up" — the baseline is append-only-by-omission.
- `MissingTranslation` is wired in error mode but cannot fire today (no `values-<lang>/` resources). It begins protecting coverage the moment TASK-15 lands its first translation.
- The release workflow (`.github/workflows/release.yml`) is independent; tag pushes still go through it.

## Release & CI

- **Signing:** `app/build.gradle.kts` reads a gitignored `keystore.properties` at repo root. Required keys: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. If the file is absent, `assembleRelease` still runs but produces an unsigned APK. The release keystore is **not** stored in the repo or in OneDrive; keep it under `~/keystores/` or a password manager.
- **CI workflow:** `.github/workflows/release.yml` triggers on `push: tags: 'v*'` and on `workflow_dispatch`. It decodes the keystore from a base64 secret, writes a transient `keystore.properties`, runs `:app:assembleRelease`, verifies the APK with `apksigner verify`, and uploads `evtracker-<tag>.apk` as both an Actions artifact and a GitHub Release asset (release auto-created with generated notes).
- **Required GitHub Secrets:** `KEYSTORE_BASE64` (base64 of the release `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Each release keystore SHA-1 also needs its own Google Cloud OAuth Android client (see `docs/GOOGLE_CLOUD_SETUP.md` Step 5) or Drive sign-in fails on release builds.
- **Cutting a release:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then `git tag vX.Y.Z` and `git push origin vX.Y.Z`. Tag pushes are run separately from the commit push per the global no-compound-git rule.

## Architecture (4-layer)

```
UI:       Fragments + ViewModels (Wizard ✓, Dashboard ✓, ChargeEdit ✓, Cars ✓, History ✓, Charts ✓, Settings ✓, ManageLocations ✓)
          BottomNavigationView in MainActivity reads the per-destination `hideBottomNav` arg in nav_graph.xml (TASK-27)
          ui/common/        MoneyFormat · DateFormat · PeriodLabels (pure helpers)
          core/model/ states DashboardScreenState · ChargeEditUiState · CarsUiState · CarFormState · HistoryUiState
Domain:   Use cases  SaveChargeEvent · DeleteChargeEvent · ObserveDashboardStats · RestoreBackup · ExportCsv
                     AddCar · RenameCar · DeleteCar (D)
          Services   StatsCalculator · CostParser · UnitConverter · DateRangeResolver · BackupSerializer
          Narrow IFs CarReader · CarWriter (D) · ChargeEventQueries · ChargeEventWriter · LocationReader · LocationWriter · SettingsReader · SettingsWriter
          Backup IFs BackupScheduler · BackupRepository · RestoreTransactionRunner · RestoreSnapshotWriter · CsvFileSink
Repo:     CarRepository (CarReader + CarWriter) · ChargeEventRepository · LocationRepository · SettingsRepository · BackupRepository
Data:     Room (CarDao, ChargeEventDao, CustomLocationDao) · Preferences DataStore · Drive AppData client (E ✓) · WorkManager backup scheduler (E ✓)
```

Legend: ✓ = wired

Single-Activity + Navigation Component. ViewBinding enabled. MPAndroidChart for charts. WorkManager is used for backup scheduling only, not as a substitute for domain logic.

**Narrow domain-interface rule (TASK-24, merged 2026-05-01).** ViewModels, Activities, Fragments, use cases, and the `Application` class depend only on `domain/repository/*` interfaces (`CarReader`, `ChargeEventQueries`, `SettingsReader`, `SettingsWriter`, `DataResetTransactionRunner`, etc.). Concrete `data.repository.*` classes are referenced only inside `di/` modules where Hilt binds them. Any new `import org.spsl.evtracker.data.repository.*` line outside `di/` is an architecture violation — the audit command is `grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"` and must return empty. When a concrete `SettingsRepository` method needs to be reachable from the UI/domain layer, promote its signature to `SettingsWriter` (or `SettingsReader`) — keep atomic multi-key writes intact when doing so.

**Bottom-nav visibility (TASK-27, merged 2026-05-01).** Each navigation destination that should hide the global `BottomNavigationView` declares `<argument android:name="hideBottomNav" app:argType="boolean" android:defaultValue="true"/>` in `res/navigation/nav_graph.xml`. `MainActivity` reads the argument generically inside `addOnDestinationChangedListener` (`args?.getBoolean("hideBottomNav") ?: false`) and never references specific destination IDs for visibility decisions. Destinations that omit the argument default to `false` (nav visible). Adding a new full-screen destination is a nav-graph edit only — never edit `MainActivity` for this. Currently set on `wizardFragment`, `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`.

`activity_main.xml` is a vertical `LinearLayout` with `FragmentContainerView` at `0dp`/`weight=1` and `BottomNavigationView` at `wrap_content` so the host always fills exactly the space above the actual measured nav-bar height across font scales and Material 3 theme variations. Each Fragment's own root layout is a `CoordinatorLayout` for Snackbar/FAB anchoring.

## Invariants — read before changing data, math, or storage

These are easy to break by accident and are scattered across docs/DESIGN.md / docs/AGENT_INSTRUCTIONS.md:

- **Odometer is always stored in km.** Unit toggle (km ↔ miles) is display-only; never rewrite stored values.
- **Cost = 0 or blank ⇒ `costTotal` and `costPerKwh` are stored `NULL`.** Events with `costTotal IS NULL` are excluded from every cost stat, every cost chart series, and the dashboard cost row hides when no costed events exist in the period. Use `parseCost(value, kwh, mode)` (returns `Pair<Double?, Double?>`) before insert. When `parseCost` is called with both fields populated, the **total** wins (per DESIGN §4.1).
- **Multi-currency periods:** if any two costed events in the visible period have different `currency` values, every cost stat returns `null` and the Dashboard shows a "Multi-currency period — cost stats hidden" banner instead of the cost cards.
- **Efficiency uses delta-odometer:** for sorted events, `dist = events[i].odometerKm - events[i-1].odometerKm`; skip rows where `dist <= 0`. The first event for a car cannot compute efficiency — show `"—"`. Aggregates use weighted averages: `Σ d_km / Σ e`, `Σ cost / Σ d_km`. Single-event periods still report `totalKwh` and `chargeCount` — only delta-based metrics are `null`.
- **Wizard gate:** on `MainActivity.onCreate`, if DataStore key `setupComplete` is `false` (default), navigate to `wizardFragment`. The wizard has **four** pages: 0=Welcome, 1=Metric/Unit, 2=Currency, 3=About + Disclaimer acceptance. The Finish button on page 3 is **disabled until the user toggles the `wizard_page4_accept` switch** — disclaimer acceptance is a hard gate, not advisory. The wizard's `finish()` writes `primaryMetric`, `distanceUnit`, `currency`, and `setupComplete=true` together. Settings → Reset preferences sets `setupComplete=false` and re-routes to the wizard, forcing the user to re-accept the disclaimer. Mid-wizard kill must leave `setupComplete=false`.
- **Wizard page 1 coupling:** see DESIGN §3.4 for the full metric→unit table. `mi_per_kwh` ⇒ `miles`; `km_per_kwh` and `kwh_per_100km` ⇒ `km`.
- **Location chips:** the form always shows three fixed chips (🏠 Home · 💼 Work · ⚡ Public) followed by the **top 5** custom labels from `custom_locations` (`ORDER BY useCount DESC, lastUsed DESC LIMIT 5`), then a `+ Add` chip. On save, call `LocationRepository.recordUsage(label)` (insert-or-increment).
- **Multi-car scope:** `activeCarId` lives in DataStore (`-1` = none). All queries filter by `carId`. Deleting a car cascades to its `charge_events` (FK `ON DELETE CASCADE`). "Reset all data" supports per-car or global.

## Database — Room v4

Entities: `Car`, `ChargeEvent`, `CustomLocation`. Current `@Database(version = 4)`. The `@TypeConverters(ChargeTypeConverter::class)` annotation on `AppDatabase` lets Room round-trip the `chargeType` column between SQLite TEXT and the `ChargeType` enum (TASK-25).

Migrations are mandatory and registered in `DatabaseModule.provideDatabase`:
- `MIGRATION_1_2`: adds `chargeType TEXT NOT NULL DEFAULT 'AC'` to `charge_events`.
- `MIGRATION_2_3`: creates `custom_locations` (camelCase columns `useCount`, `lastUsed`; unique index on `label`) and adds `costTotal`, `costPerKwh`, `currency`, `location`, `note` to `charge_events`. Note `note` ALTER must include `NOT NULL DEFAULT ''` to match the entity's non-nullable `String = ""`.
- `MIGRATION_3_4` (TASK-25): rewrites legacy `'DC'` chargeType cells to `'DC_FAST'` so the enum-backed `ChargeTypeConverter` reads canonical values; column type stays TEXT, no DDL change.

Indices on `charge_events`: composite `(carId, eventDate)` (matches dominant range query), `chargeType`, `location`. When adding a column, bump the version, add a migration that uses **camelCase** column names (Room's default for entity fields without `@ColumnInfo`), and add a `MigrationTest` case (see docs/TEST_PLAN.md §2.4).

## Google Drive backup

- Scope: `https://www.googleapis.com/auth/drive.appdata` (non-sensitive). File lives in the **App Data folder** (hidden from Drive UI), filename `evtracker_backup.json`.
- Backup JSON schema is versioned: current `backup_version = 4` and **must include `custom_locations`** with `label`, `useCount`, `lastUsed`. Bumping any entity requires bumping `backup_version` and updating the authoritative field list in `docs/DESIGN.md §8`. `BackupSerializer.fromJson` accepts both v3 and v4 (legacy `chargeType = "DC"` decodes to `ChargeType.DC_FAST` via `ChargeTypeJsonAdapter`).
- Backup model: the Drive file is a full snapshot. On first Drive enable, an existing remote snapshot uses a **replace-or-skip** flow; merge is not supported.
- Auto-backup: WorkManager `OneTimeWorkRequest` after every committed local change that affects the snapshot payload: charge event create/edit/committed delete, car create/edit/delete, custom-location committed delete, reset flows, successful restore, and first-time Drive enable when no remote backup exists. Required configuration: `NetworkType.CONNECTED`, `enqueueUniqueWork("drive_backup", REPLACE, ...)`, and exponential backoff starting at 30 s.
- Error model (TASK-07): `BackupRepository.backupCurrentData()` returns `BackupResult` (`Success` | `AuthRequired` | `Failure(reason, cause?)`). The repo runs its own bounded retry loop (`MAX_ATTEMPTS = 3`, exponential backoff 250 ms × 2^attempt) for transient failures only — network IOException, HTTP 429, HTTP 5xx, HTTP 403 with quota/rate reasons. Non-recoverable failures short-circuit: 401 → `AuthRequired`; 403 with auth reason → `AuthRequired`; 403 with `storageQuotaExceeded` → `Failure("Drive storage full")`; 403 with unknown / unparseable body → conservative `AuthRequired`. `DriveBackupWorker.doWork()` is a thin `when (result)` translator — `Result.success()` for Success, `Result.failure()` for everything else (no `Result.retry()`, since the repo already exhausted the retry budget). All non-recoverable paths log via `android.util.Log.e("DriveBackupRepository", ..., cause)`.
- Restore flow: on first Drive enable, fetch the file → if present, prompt "Found backup from [date]. This will replace data already on this device. Restore?" → on confirm, **first** export current local DB to `cacheDir/last_overwritten_backup.json`, then clear and import in one transaction; on skip, keep local data unchanged and continue with backup enabled. The undo snapshot is best-effort because cache eviction can remove it before the 24 h target.
- Multi-currency rule: `StatsCalculator` returns `null` cost stats whenever a period contains charge events with more than one distinct `currency`. Dashboard surfaces a "Multi-currency period — cost stats hidden" banner.

OAuth setup (full walkthrough in `docs/GOOGLE_CLOUD_SETUP.md`):
- Android OAuth client is bound to package name `org.spsl.evtracker` + keystore SHA-1. Don't change `applicationId` casually — it invalidates the OAuth client.
- Debug and release builds need **separate** OAuth clients (one per keystore SHA-1). Consent screen can stay in "Testing" status; tester emails must be allow-listed.
- **No `google-services.json`, no Firebase, no `com.google.gms.google-services` plugin.** The Authorization API (`Identity.getAuthorizationClient`) reads the OAuth client at runtime from your package + signing-cert SHA-1; nothing else is needed at build time. If you see references to placing `google-services.json` in `app/`, they are stale — the file is in `.gitignore` solely to defang accidental commits if a contributor adds Firebase later.
- The deprecated `GoogleSignIn.getClient(...)` API must not be used in new code. Use the Authorization API.
- Verify a backup landed by calling Drive `files.list` with `spaces=appDataFolder` — the App Data folder is hidden from the Drive UI.

## DataStore keys

Declared in a single `PreferenceKeys` object: `setupComplete`, `primaryMetric` (`km_per_kwh` | `kwh_per_100km` | `mi_per_kwh`), `distanceUnit` (`km` | `miles`), `currency`, `activeCarId`, `driveEnabled`, `theme`. Add new keys here, not inline.

## CSV export

`ExportCsvUseCase` writes to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` and shares via `FileProvider` authority `${packageName}.fileprovider`. Odometer column header switches label per unit pref but conversion happens at export time only — stored values stay in km.

## Conventions

- Add new screens by creating a Fragment + ViewModel pair and wiring into the Nav graph; do not introduce a second Activity.
- New efficiency or cost metrics: extend `Stats` / `EfficiencyStats` and the dashboard card layout; keep the formulas table in `docs/DESIGN.md §7` in sync.
- When changing the wizard, update `WizardViewModelTest` and `WizardFlowTest` (docs/TEST_PLAN.md §3.2, §4.1) — the gate behavior is covered by tests.

### ViewModel + event pattern (D-era)

ViewModels expose:
- `val uiState: StateFlow<XxxUiState>` built via `combine`/`flatMapLatest` over the narrow domain interfaces. Default values on every state field so VMs can use `MutableStateFlow(XxxUiState())` cheaply.
- `val events: SharedFlow<XxxEvent>` for one-shot effects (Snackbar, navigate, dialog). **Always `replay = 0`** with `extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST` — fragments collect inside `repeatOnLifecycle(STARTED)` and a non-zero replay would re-fire navigation events on rotation/back-stack pop.

Tests that need to observe an emitted event must subscribe BEFORE `tryEmit`:

```kotlin
val received = mutableListOf<XxxEvent>()
val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
vm.someTrigger()
advanceUntilIdle()  // or runCurrent() — required to flush the launched collection
job.cancel()
assertTrue(received.first() is ExpectedEvent)
```

History's swipe-delete uses a 5s cancellable `Job` per event id; tests use `StandardTestDispatcher` + `advanceTimeBy(5_001)` for time control. Don't use `UnconfinedTestDispatcher` for time-sensitive tests — it runs `delay` synchronously.

### Test infrastructure

JVM tests construct real domain use cases (`ObserveDashboardStatsUseCase`, `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`) wired through fakes in `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. Existing fakes:
`FakeCarReader`, `FakeCarRepository` (impl `CarReader, CarWriter` with shared `MutableStateFlow`), `FakeChargeEventQueries`, `FakeChargeEventWriter`, `FakeLocationReader`, `FakeLocationWriter`, `FakeSettingsReader`, `FakeSettingsWriter`, `FakeBackupScheduler`, `FakeBackupRepository` (returns `nextBackupResult: BackupResult` per TASK-07), `FakeDriveRemoteSource` (raises `failNext` for `failTimes` calls then auto-resets; exposes `attemptCount` + `failuresRaised` for retry-loop assertions), `FakeDriveAuthManager`, `FakeRestoreTransactionRunner`, `FakeRestoreSnapshotWriter`, `FakeSaveChargeEventGateway` (real `SaveChargeEventUseCase` wired through the chargeevent/location/backup fakes), `FakeNowProvider`.

Build commands (sandbox quirk: gradle's default `~/.gradle` is read-only here, always `GRADLE_USER_HOME=/tmp/gradle-home`):

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest   # compile-only; running needs an emulator
```

### Sub-project workflow

A/B/C/D used `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:subagent-driven-development` with feat/ branches and per-task spec+code reviews. Specs live at `docs/superpowers/specs/`; plans live at `docs/superpowers/plans/`. Branches merge to `main` via `--no-ff`, then push, then `git branch -d`. Never compound git commands — CLAUDE.md global rule.


## Compaction Policy
When compacting, always preserve:
- Current step in the multi-step build plan and acceptance criteria
- Architecture (MVVM/MVI/Clean), module graph, DI setup
- Full list of modified/created files and current test/lint status
- Gradle version catalog and dependency deltas
- Open bugs, failing tests, and follow-ups that haven't been resolved yet.
Discard: file-exploration transcripts, resolved threads, verbose tool output.
Update: the "Status" section with the new progress, and ensure all instructions are still accurate for the current state of the codebase.