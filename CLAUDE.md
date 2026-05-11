# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`org.spsl.evtracker`) for logging EV charge events and analyzing efficiency/cost. Brand name **Joulie**; the GitHub repo is [`SPS-L/joulie`](https://github.com/SPS-L/joulie). The Kotlin namespace stays `org.spsl.evtracker` and the Room DB filename is unchanged so existing user data survives upgrades.

Stack: Kotlin, MVVM with a domain/use-case layer plus narrow repositories, Gradle Kotlin DSL, and Hilt-based dependency injection. Min SDK 26, target/compile SDK 35, JDK 17. Room compiler runs via **KSP** (not kapt).

Brand assets live under `docs/branding/` with the canonical `Joulie_Brand_Guide.pdf` as source of truth, master logos (light / dark / mono / mark-only), editable SVG sources under `svg/`, regeneration pipelines under `scripts/`, and Play Store hi-res icon + light/dark feature graphics under `play-store/`. Read `docs/branding/README.md` before changing any brand surface. **Voice rules** (Brand Guide §1): plain English; never use the em-dash in prose.

The palette is the Joulie brand: primary `#0D47FF` (vibrant blue), secondary `#00C2D1` (teal), tertiary `#FFD54D` (yellow), accent `#A6F43C` (green). Two supporting colour tokens: `joulie_ink_deep #0A1B5E` (wordmark on light surfaces) and `joulie_spark_green #A6F43C` (accent affordances).

Release signing is wired through a gitignored `keystore.properties`; `.github/workflows/release.yml` builds, signs, verifies, and publishes the APK to a GitHub Release on every `v*` tag push. The **GitHub Pages landing page** lives at [sps-l.github.io/joulie](https://sps-l.github.io/joulie/), built and deployed by `.github/workflows/pages.yml` (Pages source = *GitHub Actions*). The privacy page at [/privacy](https://sps-l.github.io/joulie/privacy) is rendered from `PRIVACY.md` at deploy time via pandoc using the `docs/_privacy-template.html` wrapper, so edits to the markdown propagate to the live URL within ~30s.

Repo root holds only `README.md`, `CLAUDE.md`, `CONTRIBUTING.md`, `PRIVACY.md`, and build/config files. All project documentation lives under `docs/`:

- `README.md`, user-facing landing page (About / Download / Privacy / License).
- `CONTRIBUTING.md`, contributor entry point: architecture, build, test, CI gate, releasing, SPDX-header rule, project-doc index.
- `PRIVACY.md`, privacy policy (referenced from the Play Store listing).
- `docs/DESIGN.md`, canonical product + technical spec. Source of truth when in conflict with anything else.
- `docs/GOOGLE_CLOUD_SETUP.md`, Drive API + OAuth Android client setup.
- `docs/BACKLOG.md`, open backlog of post-v1 refactors and new features.
- `docs/TEST_PLAN.md`, full test specification.
- `docs/METHODOLOGY.md`, CO₂ tracker methodology, coefficients, and sources.

## Build & Test

```bash
./gradlew assembleDebug                        # target APK path: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                      # signed if keystore.properties exists, unsigned otherwise
./gradlew test                                 # JVM unit tests
./gradlew connectedAndroidTest                 # Espresso/Room, needs API 26+ device or emulator
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.UnitConverterTest.kmToMiles_positive"
scripts/run-instrumented.sh                    # local instrumented suite via ~/Android/Sdk emulator
```

Requires `ANDROID_HOME` set and Build Tools 35.

### Local instrumented runs

`scripts/run-instrumented.sh` is the day-to-day runner for the instrumented suite. It boots the AVD (KVM-accelerated, ~30 s; falls back to software with a warning if `/dev/kvm` is unavailable, ~10 min boot), disables the three animation scales (Espresso requires this; without `settings put global *_animation_scale 0`, swipe-and-Snackbar tests deterministically fail with "Animations or transitions are enabled on the target device"), then runs `:app:connectedDebugAndroidTest`.

```bash
scripts/run-instrumented.sh                       # full suite
scripts/run-instrumented.sh ChartsFragmentTest    # one class
scripts/run-instrumented.sh stop                  # tear emulator down
```

One-time setup is documented in the script header (sdkmanager / qemu-kvm / avdmanager). The default AVD name is `joulie-test` (override with `JOULIE_AVD=...`).

### Build types

- **Debug** has `applicationIdSuffix = ".debug"` (so the runtime package becomes `org.spsl.evtracker.debug`) and `versionNameSuffix = "-debug"` (so `BuildConfig.VERSION_NAME` resolves to e.g. `"1.0.1-debug"`). Debug and release can be installed side-by-side on the same device.
- **`buildConfig = true`** is enabled in `buildFeatures`. `BuildConfig.VERSION_NAME`, `VERSION_CODE`, `DEBUG`, plus three custom fields, `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX`, resolve at compile time under `org.spsl.evtracker.BuildConfig` (the namespace, not the runtime package). Both build types must declare every custom field — AGP fails the build if release omits a field that debug declares (or vice versa).
- **Drive on debug builds requires a third OAuth Android client** registered for `org.spsl.evtracker.debug` + the debug keystore SHA-1. See `docs/GOOGLE_CLOUD_SETUP.md` Step 5b. Until that client exists, Drive sign-in fails on debug builds; release is unaffected because release keeps `applicationId = org.spsl.evtracker`.

### Static analysis gate

PRs and pushes to `main` are gated by `.github/workflows/ci.yml`. The same gate runs locally:

```bash
./gradlew ktlintCheck                          # ktlint 12.1.1, Kotlin official style; auto-fix with ktlintFormat
./gradlew :app:lint                            # Android Lint, error-mode for HardcodedText/MissingTranslation/TypographyDashes/UnusedResources
./gradlew :app:testDebugUnitTest               # bundled into the same CI job
./gradlew :app:verifyRoborazziDebug            # screenshot baselines (TASK-79); diff-fails the build on visual regression
```

- Style is anchored by the repo-root `.editorconfig` (`ktlint_code_style = intellij_idea`, 4-space indent). The IDE's reformat output and ktlint's check agree.
- Pre-existing lint offenses are absorbed by `app/lint-baseline.xml`. **Only new violations break the build.** Regenerate the baseline only when retiring a rule (`./gradlew :app:updateLintBaseline`); do not regenerate to "clean up", the baseline is append-only-by-omission.
- `MissingTranslation` is in error mode and protects coverage across the four shipped locales (`values/`, `values-el/`, `values-tr/`, `values-ru/`). New translatable strings must land in every locale file or the build fails.
- The release workflow (`.github/workflows/release.yml`) is independent; tag pushes still go through it.

**Screenshot baselines (TASK-79 / TASK-30 / TASK-86).** `app/src/test/screenshots/` holds 26 PNGs: 14 for the 7 `ChartsTabFragment` tabs × light + dark themes (TASK-79), and 12 for the dashboard carbon-intensity pill — 5 buckets × light + dark themes plus Loading and Error states captured once each (TASK-86, since the renderer paints Loading/Error with theme-agnostic surface-variant colours). All render via Roborazzi 1.59.0 + Robolectric 4.14.1 in `@GraphicsMode(NATIVE)` mode. ChartsTab tests host the production fragment under a `FakeChartsParentFragment` with a Mockito-mocked `ChartsViewModel`; the carbon-pill test inflates `WidgetCarbonIntensityBinding` directly inside `HiltTestActivity` and calls `CarbonIntensityRenderer.render` with a fixed `nowMs` so the "Updated X ago" subtitle stays stable. Recapture is **a separate PR** titled `screenshot baseline refresh` — never bundle baseline regeneration with feature changes, so reviewers can scan the PNG diff for unintended pixel motion. **Documented exception:** TASK-30 (the Vico migration that *is* a rendering library swap) bundled its baseline regeneration with the migration code, since the visual change is the entire point of the feature. Recapture command: `./gradlew :app:recordRoborazziDebug`.

## Release & CI

- **Signing:** `app/build.gradle.kts` reads a gitignored `keystore.properties` at repo root. Required keys: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. If the file is absent, `assembleRelease` still runs but produces an unsigned APK. The release keystore is **not** stored in the repo or in OneDrive; keep it under `~/keystores/` or a password manager.
- **CI workflow:** `.github/workflows/release.yml` triggers on `push: tags: 'v*'` and on `workflow_dispatch`. It decodes the keystore from a base64 secret, writes a transient `keystore.properties`, writes the ADI registration token from a secret to `app/src/main/assets/adi-registration.properties`, runs `:app:assembleRelease`, verifies the APK with `apksigner verify`, asserts the ADI asset is bundled in the signed APK via `unzip -p`, and uploads `joulie-<tag>.apk` as both an Actions artifact and a GitHub Release asset (release auto-created with generated notes).
- **Required GitHub Secrets:** `KEYSTORE_BASE64` (base64 of the release `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `ADI_REGISTRATION_TOKEN` (raw single-line token from the Android Developer Verification page). Each release keystore SHA-1 also needs its own Google Cloud OAuth Android client (see `docs/GOOGLE_CLOUD_SETUP.md` Step 5) or Drive sign-in fails on release builds.
- **ADI (Android Developer Verification) registration token** lives at `app/src/main/assets/adi-registration.properties`, gitignored, sensitivity-class same as `keystore.properties`. The file is a single line containing the developer-account-specific snippet from Google's verification page. The CI release workflow writes this asset from the `ADI_REGISTRATION_TOKEN` secret before `assembleRelease` and asserts the bundled asset is non-empty in the signed APK. If the secret is unset or the asset is missing or zero-length, the workflow fails fast. Both locally-built and CI-built release APKs are therefore registered against the release keystore's SHA-256.
- **Cutting a release:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then `git tag vX.Y.Z` and `git push origin vX.Y.Z`. Tag pushes are run separately from the commit push per the global no-compound-git rule.

## Architecture (4-layer)

```
UI:       Fragments + ViewModels (Wizard, Dashboard, ChargeEdit, Cars, History, Charts, Settings, ManageLocations)
          BottomNavigationView in MainActivity reads the per-destination `hideBottomNav` arg in nav_graph.xml
          ui/common/        MoneyFormat · DateFormat · PeriodLabels (pure helpers)
          core/model/ states DashboardScreenState · ChargeEditUiState · CarsUiState · CarFormState · HistoryUiState
Domain:   Use cases  SaveChargeEvent · DeleteChargeEvent · ObserveDashboardStats · RestoreBackup · ExportCsv
                     AddCar · RenameCar · DeleteCar · PushBackupNow · WipeRemoteBackup · RefreshCarbonIntensity
          Services   StatsCalculator · CostParser · UnitConverter · DateRangeResolver · BackupSerializer · CapacityEstimator
                     CarbonIntensityFormatter (in domain/service/, TASK-82 pure pill-state mapper)
                     BackupOutcomeReporter (in domain/notification/)
                     LastChargeWidgetSnapshot (in domain/widget/)
          Narrow IFs CarReader · CarWriter · ChargeEventQueries · ChargeEventWriter · LocationReader · LocationWriter · SettingsReader · SettingsWriter
          Backup IFs BackupScheduler · BackupRepository · RestoreTransactionRunner · RestoreSnapshotWriter · CsvFileSink · BackupNotifier · WidgetRefresher
Repo:     CarRepository · ChargeEventRepository · LocationRepository · SettingsRepository · BackupRepository
Data:     Room (CarDao, ChargeEventDao, CustomLocationDao) · Preferences DataStore · Drive AppData client · WorkManager backup scheduler
          AndroidBackupNotifier (in data/notification/)
          AndroidWidgetRefresher + LastChargeWidget AppWidgetProvider (in widget/ and data/widget/)
```

Single-Activity + Navigation Component. ViewBinding enabled. **Vico 2.0.0** for line / column charts, custom `ui/common/PieChartView` for the AC/DC and Locations donut charts (Vico has no pie primitive). WorkManager is used for backup scheduling only, not as a substitute for domain logic.

**Narrow domain-interface rule.** ViewModels, Activities, Fragments, use cases, and the `Application` class depend only on `domain/repository/*` interfaces (`CarReader`, `ChargeEventQueries`, `SettingsReader`, `SettingsWriter`, `DataResetTransactionRunner`, etc.). Concrete `data.repository.*` classes are referenced only inside `di/` modules where Hilt binds them. Any new `import org.spsl.evtracker.data.repository.*` line outside `di/` is an architecture violation; the audit command is `grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"` and must return empty. When a concrete `SettingsRepository` method needs to be reachable from the UI/domain layer, promote its signature to `SettingsWriter` (or `SettingsReader`), keep atomic multi-key writes intact when doing so.

**Bottom-nav visibility.** Each navigation destination that should hide the global `BottomNavigationView` declares `<argument android:name="hideBottomNav" app:argType="boolean" android:defaultValue="true"/>` in `res/navigation/nav_graph.xml`. `MainActivity` reads the argument generically inside `addOnDestinationChangedListener` (`args?.getBoolean("hideBottomNav") ?: false`) and never references specific destination IDs for visibility decisions. Destinations that omit the argument default to `false` (nav visible). Adding a new full-screen destination is a nav-graph edit only — never edit `MainActivity` for this. Currently set on `wizardFragment`, `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`.

`activity_main.xml` is a vertical `LinearLayout` with `FragmentContainerView` at `0dp`/`weight=1` and `BottomNavigationView` at `wrap_content` so the host always fills exactly the space above the actual measured nav-bar height across font scales and Material 3 theme variations. Each Fragment's own root layout is a `CoordinatorLayout` for Snackbar/FAB anchoring.

## Invariants — read before changing data, math, or storage

These are easy to break by accident; the canonical source of truth is `docs/DESIGN.md`:

- **Odometer is always stored in km.** Unit toggle (km ↔ miles) is display-only; never rewrite stored values.
- **Cost = 0 or blank ⇒ `costTotal` and `costPerKwh` are stored `NULL`.** Events with `costTotal IS NULL` are excluded from every cost stat, every cost chart series, and the dashboard cost row hides when no costed events exist in the period. Use `parseCost(value, kwh, mode)` (returns `Pair<Double?, Double?>`) before insert. When `parseCost` is called with both fields populated, the **total** wins (per DESIGN §4.1).
- **Multi-currency periods:** if any two costed events in the visible period have different `currency` values, every cost stat returns `null` and the Dashboard shows a "Multi-currency period, cost stats hidden" banner instead of the cost cards.
- **Efficiency uses delta-odometer:** for sorted events, `dist = events[i].odometerKm - events[i-1].odometerKm`; skip rows where `dist <= 0`. The first event for a car cannot compute efficiency, show `"—"`. Aggregates use weighted averages: `Σ d_km / Σ e`, `Σ cost / Σ d_km`. Single-event periods still report `totalKwh` and `chargeCount`; only delta-based metrics are `null`.
- **Wizard gate:** on `MainActivity.onCreate`, if DataStore key `setupComplete` is `false` (default), navigate to `wizardFragment`. The wizard has **four** pages: 0=Welcome, 1=Metric/Unit, 2=Currency, 3=About + Disclaimer acceptance. The Finish button on page 3 is **disabled until the user toggles the `wizard_page4_accept` switch** — disclaimer acceptance is a hard gate, not advisory. The wizard's `finish()` writes `primaryMetric`, `distanceUnit`, `currency`, and `setupComplete=true` together. Settings → Reset preferences sets `setupComplete=false` and re-routes to the wizard, forcing the user to re-accept the disclaimer. Mid-wizard kill must leave `setupComplete=false`.
- **Wizard page 1 coupling:** see DESIGN §3.4 for the full metric→unit table. `mi_per_kwh` ⇒ `miles`; `km_per_kwh` and `kwh_per_100km` ⇒ `km`.
- **Location chips:** the form always shows three fixed chips (🏠 Home · 💼 Work · ⚡ Public) followed by the **top 5** custom labels from `custom_locations` (`ORDER BY useCount DESC, lastUsed DESC LIMIT 5`), then a `+ Add` chip. On save, call `LocationRepository.recordUsage(label)` (insert-or-increment).
- **Multi-car scope:** `activeCarId` lives in DataStore (`-1` = none). All queries filter by `carId`. Deleting a car cascades to its `charge_events` (FK `ON DELETE CASCADE`). "Reset all data" supports per-car or global.

## Database, Room v7

Entities: `Car`, `ChargeEvent`, `CustomLocation`. Current `@Database(version = 8)`. All three entities use `@PrimaryKey val id: Long` and `ChargeEventEntity.carId: Long`. The `@TypeConverters(ChargeTypeConverter::class, ChargeKwhSourceConverter::class)` annotation on `AppDatabase` lets Room round-trip the `chargeType` column between SQLite TEXT and the `ChargeType` enum, and the `kwhSource` column between SQLite TEXT and the `ChargeKwhSource` enum. `ChargeEventEntity` carries optional `socBefore: Double?` and `socAfter: Double?` fields stored as fractions in `0.0..1.0`, a non-null `kwhSource: ChargeKwhSource = ChargeKwhSource.MEASURED` provenance flag, and an optional `gridIntensityGCo2PerKwh: Double?` capturing the Electricity Maps grid mix at save time (TASK-80).

Migrations are mandatory and registered in `DatabaseModule.provideDatabase`:
- `MIGRATION_1_2`: adds `chargeType TEXT NOT NULL DEFAULT 'AC'` to `charge_events`.
- `MIGRATION_2_3`: creates `custom_locations` (camelCase columns `useCount`, `lastUsed`; unique index on `label`) and adds `costTotal`, `costPerKwh`, `currency`, `location`, `note` to `charge_events`. The `note` ALTER must include `NOT NULL DEFAULT ''` to match the entity's non-nullable `String = ""`.
- `MIGRATION_3_4`: rewrites legacy `'DC'` chargeType cells to `'DC_FAST'` so the enum-backed `ChargeTypeConverter` reads canonical values; column type stays TEXT, no DDL change.
- `MIGRATION_4_5`: no-op DDL alongside the Kotlin `Int` → `Long` PK widening. SQLite `INTEGER` columns already store 64 bits, so no DDL is needed; the migration registration acts as a tripwire so future downgrades trip Room's schema validator instead of silently truncating Long values to Int.
- `AutoMigration(from = 5, to = 6)`: adds nullable `socBefore` and `socAfter` REAL columns to `charge_events` for state-of-charge data. Both columns default to NULL on legacy rows; `CapacityEstimator` consumes them when present (else falls back to the 80%-of-nominal heuristic). Replaced the manual `MIGRATION_5_6` in v1.9.33 (TASK-39); Room synthesises equivalent SQL from the v5↔v6 schema diff at compile time.
- `AutoMigration(from = 6, to = 7)`: adds `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` to `charge_events`. Round-tripped via `ChargeKwhSourceConverter` into the `ChargeKwhSource` enum (`MEASURED` / `DERIVED_FROM_SOC`). Legacy rows backfill cleanly to `MEASURED`. `CapacityEstimator` skips `DERIVED_FROM_SOC` events on both the exact and heuristic paths because the derived `kwhAdded` is tautological against `Δsoc × nominalBatteryKwh`. Replaced the manual `MIGRATION_6_7` in v1.9.33 (TASK-39); the entity field carries `@ColumnInfo(defaultValue = "MEASURED")` so Room's KSP can synthesise the `ALTER TABLE … ADD COLUMN … DEFAULT 'MEASURED'` statement.
- `AutoMigration(from = 7, to = 8)`: adds the nullable `grid_intensity_g_co2_per_kwh REAL` column to `charge_events` for the Electricity Maps live feed (TASK-80). Legacy rows migrate to `NULL`. Rows with `gridIntensityGCo2PerKwh = null` simply don't contribute to CO₂ stats (TASK-81 removed the static-grid-intensity fallback; there is no manual override). The column name uses snake_case via `@ColumnInfo(name = ...)` to keep the JSON backup field name and SQLite column name aligned.

Indices on `charge_events`: composite `(carId, eventDate)` (matches dominant range query), `chargeType`, `location`. When adding a column, bump the version, default to `@AutoMigration(from, to)` on `@Database` for additive bumps (see convention below), and add a `MigrationTest` case (see `docs/TEST_PLAN.md` §2.4).

**Auto-migration convention (TASK-39).** Additive schema bumps from v8 onward default to `@AutoMigration(from, to)` on the `@Database` annotation. Manual `Migration` constants are reserved for non-additive changes: cell-value rewrites (e.g. `MIGRATION_3_4`'s `'DC'` → `'DC_FAST'` UPDATE), table renames or drops, column type changes that SQLite cannot reinterpret, and any migration that needs `AutoMigrationSpec` callbacks. Any non-nullable column added via auto-migration must declare `@ColumnInfo(defaultValue = "...")` on the entity field so KSP can emit the SQL default in the generated `ALTER TABLE`.

## Google Drive backup

- Scope: `https://www.googleapis.com/auth/drive.appdata` (non-sensitive). File lives in the **App Data folder** (hidden from Drive UI), filename `evtracker_backup.json`.
- Backup JSON schema is versioned: current `backup_version = 8` and **must include `custom_locations`** with `label`, `useCount`, `lastUsed`. Bumping any entity requires bumping `backup_version` and updating the authoritative field list in `docs/DESIGN.md §8`. `BackupSerializer.fromJson` accepts `{3, 4, 5, 6, 7, 8}` (legacy `chargeType = "DC"` decodes to `ChargeType.DC_FAST` via `ChargeTypeJsonAdapter`; v3/v4 Int ids narrow into v5+ Long DTO fields automatically via Gson; v3/v4/v5 backups simply leave the new optional `soc_before`/`soc_after` fields at `null`; v3..v6 backups likewise omit `kwh_source`, and `ChargeEventDto.toEntity()` coalesces the absent value to `ChargeKwhSource.MEASURED`; v3..v7 backups omit `grid_intensity_g_co2_per_kwh`, which round-trips to `null` on the nullable entity column).
- Backup model: the Drive file is a full snapshot. On first Drive enable, an existing remote snapshot uses a **replace-or-skip** flow; merge is not supported.
- Auto-backup: WorkManager `OneTimeWorkRequest` after every committed local change that affects the snapshot payload — charge event create/edit/committed delete, car create/edit/delete, custom-location committed delete, reset flows, successful restore, and first-time Drive enable when no remote backup exists. Required configuration: `NetworkType.CONNECTED`, `enqueueUniqueWork("drive_backup", REPLACE, ...)`, and exponential backoff starting at 30 s.
- Error model: `BackupRepository.backupCurrentData()` returns `BackupResult` (`Success` | `AuthRequired` | `Failure(reason, cause?)`). The repo runs its own bounded retry loop (`MAX_ATTEMPTS = 3`, exponential backoff 250 ms × 2^attempt) for transient failures only — network IOException, HTTP 429, HTTP 5xx, HTTP 403 with quota/rate reasons. Non-recoverable failures short-circuit: 401 → `AuthRequired`; 403 with auth reason → `AuthRequired`; 403 with `storageQuotaExceeded` → `Failure("Drive storage full")`; 403 with unknown / unparseable body → conservative `AuthRequired`. `DriveBackupWorker.doWork()` is a thin `when (result)` translator — `Result.success()` for Success, `Result.failure()` for everything else. **Never `Result.retry()`**, since the repo already exhausted the retry budget. All non-recoverable paths log via `android.util.Log.e("DriveBackupRepository", ..., cause)`.
- Restore flow: on first Drive enable, fetch the file → if present, prompt "Found backup from [date]. This will replace data already on this device. Restore?" → on confirm, **first** export current local DB to `cacheDir/last_overwritten_backup.json`, then clear and import in one transaction; on skip, keep local data unchanged and continue with backup enabled. The undo snapshot is best-effort because cache eviction can remove it before the 24 h target.
- Restore-prompt suppression: the user's Skip / Confirm decision is recorded as the snapshot's `exported_at` string in DataStore key `lastSeenRemoteBackupExportedAt`. On every `onDriveAuthGranted` call, `SettingsViewModel` compares the remote `exported_at` to the marker — when they match, Drive is silently re-enabled and `enqueueBackup()` runs without firing `ShowRestorePrompt`. `WipeRemoteBackupUseCase` resets the marker to `""` on success so a fresh upload + Drive re-toggle prompts exactly once for the new snapshot. The destructive restore-prompt is therefore shown at most once per remote snapshot identity.
- Multi-currency rule: `StatsCalculator` returns `null` cost stats whenever a period contains charge events with more than one distinct `currency`. Dashboard surfaces a "Multi-currency period, cost stats hidden" banner.
- Failure notifications: `BackupOutcomeReporter` (in `domain/notification/`) increments `consecutiveBackupFailures` on each `Failure` / `AuthRequired` and resets to 0 on `Success`. At threshold 3 it calls `BackupNotifier.notifyChronicFailure()` (channel `backup_status`, IMPORTANCE_LOW, sticky). `AuthRequired` always fires `notifyAuthRequired()` (channel `backup_auth`, IMPORTANCE_DEFAULT). Both channels are created in `EVTrackerApp.onCreate` via `AndroidBackupNotifier.ensureChannels(this)`. `MainActivity` shows the `POST_NOTIFICATIONS` rationale + system request only when failures cross threshold AND permission missing AND user hasn't previously denied AND API >= 33; denial is sticky via `notificationPermissionDenied`. `safeNotify` gates `NotificationManagerCompat.notify` on `areNotificationsEnabled()` so the call sites are silent no-ops when permission is missing.
- Manual controls: Settings → Drive section exposes "Back up now" and "Wipe remote backup" buttons (visibility gated on `driveEnabled`). Both invoke standalone use cases (`PushBackupNowUseCase`, `WipeRemoteBackupUseCase`); the auto-backup `WorkManager` worker contract is unchanged — manual push is one extra path, not a replacement. Wipe calls `BackupRepository.deleteRemoteBackup()` which lists `appDataFolder` for `evtracker_backup.json`, calls Drive `files.delete`, and (on `Success`) clears `lastBackupAt = 0L` so the UI's stale-timestamp hint reverts. Wipe is gated by a `MaterialAlertDialog` confirmation. Mutual exclusion: a slow push blocks wipe and vice versa via `isManualBackupRunning` / `isManualWipeRunning` on `SettingsUiState`.

OAuth setup (full walkthrough in `docs/GOOGLE_CLOUD_SETUP.md`):
- Android OAuth client is bound to package name `org.spsl.evtracker` + keystore SHA-1. Don't change `applicationId` casually — it invalidates the OAuth client.
- Debug and release builds need **separate** OAuth clients (one per keystore SHA-1). Consent screen can stay in "Testing" status; tester emails must be allow-listed.
- **No `google-services.json`, no Firebase, no `com.google.gms.google-services` plugin.** The Authorization API (`Identity.getAuthorizationClient`) reads the OAuth client at runtime from your package + signing-cert SHA-1; nothing else is needed at build time. The file is in `.gitignore` solely to defang accidental commits if a contributor adds Firebase later.
- The deprecated `GoogleSignIn.getClient(...)` API must not be used in new code. Use the Authorization API.
- Verify a backup landed by calling Drive `files.list` with `spaces=appDataFolder` — the App Data folder is hidden from the Drive UI.

## DataStore keys

Declared in a single `PreferenceKeys` object: `setupComplete`, `primaryMetric` (`km_per_kwh` | `kwh_per_100km` | `mi_per_kwh`), `distanceUnit` (`km` | `miles`), `currency`, `activeCarId`, `driveEnabled`, `theme`, `consecutiveBackupFailures`, `notificationPermissionDenied`, `lastSeenRemoteBackupExportedAt` (ISO-8601 string of the remote snapshot most recently Skipped or Restored; empty string = never seen; reset to `""` on `WipeRemoteBackupUseCase` success), `languageTag` (BCP-47 language tag for the in-app locale picker; empty string = follow system), `iceBaselineLPer100km` (CO₂ ICE counterfactual baseline, default 7.0), `co2Enabled` (TASK-80 opt-in master switch; default `false` so the Dashboard CO₂ card and Charts CO₂ tab stay hidden until the user enables tracking), `electricityMapsApiKey` (Electricity Maps API read token; empty string = unset — when blank with CO₂ on, no per-event intensity is captured and CO₂ surfaces stay hidden because there is no manual fallback, TASK-81), `electricityMapsZone` (uppercase IETF/ISO 3166 region code, default `CY`), `electricityMapsCacheZone` / `electricityMapsCacheIntensity` / `electricityMapsCacheFetchedAtMs` (TASK-81 persistent 1-hour throttle for the Electricity Maps API — written atomically by `setElectricityMapsCache` after a successful fetch; read on every fetch to enforce the once-per-hour-per-zone rate limit across process restarts). Add new keys here, not inline.

## CSV export

`ExportCsvUseCase` writes to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` and shares via `FileProvider` authority `${packageName}.fileprovider`. Schema is the canonical 14-column header: `event_date_iso, car_name, odometer_km, kwh, kwh_source, charge_type, location, cost_total, cost_per_kwh, currency, km_per_kwh, soc_before, soc_after, note`. Same header for full-history (`export(carId)`) and date-ranged (`export(carId, range: LongRange)`) exports — research consumers anchor on a stable schema. Distance always emits as canonical kilometres regardless of the user's display preference for locale-independence. `km_per_kwh` is computed per-row using the same delta-odometer convention as `StatsCalculator` (first row blank because no prior event exists in the exported slice; `prevOdo` advances unconditionally so transient rollbacks don't break the chain). All text columns route through a hardened `csvEscape` (RFC 4180 + OWASP formula-injection prefixes); numeric / timestamp columns deliberately bypass.

## Conventions

- Add new screens by creating a Fragment + ViewModel pair and wiring into the Nav graph; do not introduce a second Activity.
- New efficiency or cost metrics: extend `Stats` / `EfficiencyStats` and the dashboard card layout; keep the formulas table in `docs/DESIGN.md §7` in sync.
- When changing the wizard, update `WizardViewModelTest` and `WizardFlowTest` (`docs/TEST_PLAN.md` §3.2, §4.1) — the gate behavior is covered by tests.

### ViewModel + event pattern

ViewModels expose:
- `val uiState: StateFlow<XxxUiState>` built via `combine`/`flatMapLatest` over the narrow domain interfaces. Default values on every state field so VMs can use `MutableStateFlow(XxxUiState())` cheaply.
- `val events: SharedFlow<XxxEvent>` for one-shot effects (Snackbar, navigate, dialog). **Always `replay = 0`** with `extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST` — fragments collect inside `repeatOnLifecycle(STARTED)` and a non-zero replay would re-fire navigation events on rotation/back-stack pop.

Tests that need to observe an emitted event must subscribe BEFORE `tryEmit`:

```kotlin
val received = mutableListOf<XxxEvent>()
val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
vm.someTrigger()
advanceUntilIdle()  // or runCurrent(), required to flush the launched collection
job.cancel()
assertTrue(received.first() is ExpectedEvent)
```

History's swipe-delete uses a 5s cancellable `Job` per event id; tests use `StandardTestDispatcher` + `advanceTimeBy(5_001)` for time control. Don't use `UnconfinedTestDispatcher` for time-sensitive tests — it runs `delay` synchronously.

### Test infrastructure

JVM tests construct real domain use cases (`ObserveDashboardStatsUseCase`, `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`) wired through fakes in `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. Existing fakes:
`FakeCarReader`, `FakeCarRepository` (impl `CarReader, CarWriter` with shared `MutableStateFlow`), `FakeChargeEventQueries`, `FakeChargeEventWriter`, `FakeLocationReader`, `FakeLocationWriter`, `FakeSettingsReader`, `FakeSettingsWriter`, `FakeBackupScheduler`, `FakeBackupRepository` (returns `nextBackupResult: BackupResult`), `FakeDriveRemoteSource` (raises `failNext` for `failTimes` calls then auto-resets; exposes `attemptCount` + `failuresRaised` for retry-loop assertions), `FakeDriveAuthManager`, `FakeRestoreTransactionRunner`, `FakeRestoreSnapshotWriter`, `FakeSaveChargeEventGateway`, `FakeNowProvider`.

Instrumented fragment tests for `@AndroidEntryPoint` fragments must use `org.spsl.evtracker.testing.launchFragmentInHiltContainer` (backed by the debug-variant `HiltTestActivity`), not `androidx.fragment.app.testing.launchFragmentInContainer` — Hilt fragments must be attached to an `@AndroidEntryPoint` activity at runtime.

> **Sandbox quirk for AI agents:** when Gradle's default `~/.gradle` is read-only in the runner, prefix commands with `GRADLE_USER_HOME=/tmp/gradle-home`. Human contributors don't need this.

### Branch + commit workflow

Tasks land on `feat/<name>` branches and merge to `main` via `--no-ff`, followed by a separate push and `git branch -d`. Never compound git commands — global rule.

## Compaction Policy

When compacting, always preserve:
- Current step in the multi-step build plan and acceptance criteria
- Architecture (MVVM/MVI/Clean), module graph, DI setup
- Full list of modified/created files and current test/lint status
- Gradle version catalog and dependency deltas
- Open bugs, failing tests, and follow-ups that haven't been resolved yet.

Discard: file-exploration transcripts, resolved threads, verbose tool output.

Update: ensure all instructions are still accurate for the current state of the codebase.
