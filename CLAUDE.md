# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`org.spsl.evtracker`) for logging EV charge events and analyzing efficiency/cost. Kotlin, MVVM with a domain/use-case layer plus narrow repositories, Gradle Kotlin DSL, and Hilt-based dependency injection. Min SDK 26, target/compile SDK 35, JDK 17. Room compiler runs via **KSP** (not kapt).

> **Status:** v1.9.9 tagged (`versionCode = 25`, `versionName = "1.9.9"`). Project rebranded to **Joulie** and the GitHub repo renamed to [`SPS-L/joulie`](https://github.com/SPS-L/joulie); the Kotlin namespace stays `org.spsl.evtracker` and the Room DB filename is unchanged so existing user data survives the upgrade. v1.9.0 adopts the **Joulie Brand Pack v1.0** drop (gradient bolt-J launcher with a `<monochrome>` layer for Android 13+ themed icons; brand-blue splash with white bolt; redesigned wizard welcome and About screens with gradient hero artwork; SPS-Lab badge recoloured from `#FB8C00` to `#FFD54D`; multi-density notification icon; two new colour tokens `joulie_ink_deep #0A1B5E` and `joulie_spark_green #A6F43C`; one new translatable string `wizard_page1_intro_title` with el / tr / ru translations from the pack). The palette is the **Joulie brand**: primary `#0D47FF` (vibrant blue), secondary `#00C2D1` (teal), tertiary `#FFD54D` (yellow, replaces the pre-rebrand "DC orange" `#FB8C00`), accent `#A6F43C` (green). Brand assets live under `docs/branding/` with the canonical [`Joulie_Brand_Guide.pdf`](docs/branding/Joulie_Brand_Guide.pdf) as source of truth, master logos (`joulie_logo_light.png`, `joulie_logo_dark.png`, `joulie_logo_white_bg.png`, `joulie_logo_dark_bg.png`, `joulie_mark_only.png`, `joulie_mark_mono.png`, `brand_palette.png`), editable SVG sources under `svg/`, regeneration pipelines under `scripts/`, and Play Store hi-res icon + light / dark feature graphics under `play-store/`. Read [`docs/branding/README.md`](docs/branding/README.md) before changing any brand surface. **Voice rules** (Brand Guide section 1): plain English; never use the em-dash in prose; prose retrofitted across all 9 user-facing and contributor docs in the v1.9.0 drop. JVM unit-test count: **433**. Instrumented suite compiles via `:app:assembleDebugAndroidTest` (running requires an emulator); Drive backup smoke per `docs/GOOGLE_CLOUD_SETUP.md` needs a Google account allow-listed on the OAuth consent screen. Release signing is wired through a gitignored `keystore.properties`; `.github/workflows/release.yml` builds, signs, verifies, and publishes the APK to a GitHub Release on every `v*` tag push. **GitHub Pages landing page** lives at [sps-l.github.io/joulie](https://sps-l.github.io/joulie/), built and deployed by `.github/workflows/pages.yml` (Pages source = *GitHub Actions*). The privacy page at [/privacy](https://sps-l.github.io/joulie/privacy) is rendered from `PRIVACY.md` at deploy time via pandoc using the `docs/_privacy-template.html` wrapper, so edits to the markdown propagate to the live URL within ~30s.

**Recent post-v1 refactors (load-bearing context for ongoing work). Older entries live in `git log` and `docs/BACKLOG.md`:**

- **TASK-53** (2026-05-03), defense-in-depth single-car invariant guard on `StatsCalculator`. Private `requireSingleCar(events)` helper applied as first line of body in five aggregations: `computeStats`, `computeMonthlyBuckets`, `computeEfficiencyTrend`, `computeAcDcSplit`, `computeLocationDistribution`. **`detectMixedCurrency` is intentionally exempt** (semantic question is currency identity, not car identity), exemption is documented in the function KDoc. Empty input passes the guard (`distinct().size == 0` is `<= 1`). The require message includes the offending carIds so a stack-trace bug report points straight at the wrong call site. New `StatsCalculatorInvariantTest` with 8 cases.
- **TASK-20** (2026-05-04), CO₂ savings tracker. Pure-domain `domain/service/CO2Calculator` with `evCo2Kg`, `iceCounterfactualCo2Kg`, `savedCo2Kg`, `cumulativeTrend`. EPA `PETROL_CO2_KG_PER_LITRE = 2.31`. Two new `doublePreferencesKey` entries: `ICE_BASELINE_L_PER_100KM` (default 7.0) and `GRID_INTENSITY_G_CO2_PER_KWH` (default 577.0, Cyprus 2025). `Stats` grows `evCo2Kg` + `iceCo2Kg` (both null when prefs unset/0; Dashboard hides the entire CO₂ card on null). New Charts `TabKind.CO2` two-series cumulative `LineChart`. Settings → CO₂ tracker section uses the locale-aware parser (accepts `7.0` and `7,0`). Methodology in `docs/METHODOLOGY.md`. **TASK-49 (per-event live grid intensity) deliberately phased OUT**, Electricity Maps charges €6,000/yr/zone, cyprusgrid.com WAF-blocks, ENTSO-E hourly-mix-derivation deferred.
- **TASK-55** (2026-05-04), language picker (Settings + first-run wizard). Narrow `domain/locale/LocaleApplier` IF wraps `AppCompatDelegate.setApplicationLocales`; `data/locale/AndroidLocaleApplier` impl bound via `LocaleModule`. New `PreferenceKeys.LANGUAGE_TAG` (default `""` = follow system). `res/xml/locales_config.xml` + `android:localeConfig` declaration on the manifest's `<application>` provides the OS-level per-app picker on Android 13+. Settings + wizard page 0 expose a "Follow system" + four-autonym dialog. **Autonyms (`language_name_en` / `_el` / `_tr` / `_ru`) are `translatable="false"`** so a Greek user always sees "Ελληνικά" regardless of the current app locale.
- **TASK-15** (2026-05-04), i18n foundation: Greek, Turkish, and Russian translations covering all 248 translatable strings. Plurals use each locale's CLDR rules (`one/other` for Greek/Turkish; `one/few/many/other` for Russian). 32 strings in canonical `values/strings.xml` are `translatable="false"` (brand names, units, technical AC/DC labels, em-dash display strings, format strings, legal text). **⚠ LLM-translation caveat: first-pass translations, must be reviewed by native speakers before any production release.**
- **TASK-46** (2026-05-04), battery-health "Estimated" warning chip on the Dashboard. `CapacityEstimator.latestIsExact(points): Boolean?` and companion `HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT = 105.0` (5%-margin avoids false alarms on exact-path readings just over 100%). `Stats` gains `batteryHealthIsHeuristic` + `batteryHealthIsOverestimated` (heuristic AND `pct >= 105.0`). Two flags rather than one collapsed flag, keeps the option open for a softer "Estimated" tag in future UX without the over-estimation warning.
- **TASK-43** (2026-05-03), kWh-from-SoC calculator + provenance flag. `core/model/ChargeKwhSource` enum (`MEASURED` / `DERIVED_FROM_SOC`) with `parseLegacy`; paired Room TypeConverter and Gson adapter. `domain/service/KwhFromSocCalculator.compute(socBefore, socAfter, nominalBatteryKwh)` returns `max(0, Δsoc × nominal)` (battery-side kWh, charging-loss caveat documented in KDoc). `CapacityEstimator` skips `DERIVED_FROM_SOC` events on **both** the exact and heuristic paths (the derived `kwhAdded` is tautological against `Δsoc × nominal`). ChargeEdit auto-activates the calculator when SoC fields are filled and kWh is blank (input-time only, save-time silent auto-derive was rejected as surprising). History rows render a tertiary-container "Est." badge for `DERIVED_FROM_SOC` events.
- **TASK-54** (2026-05-03), fix Drive switch firing on every Settings entry + durable last-seen-snapshot marker. The Drive listener is attached lazily inside the StateFlow collector (not in `onViewCreated`) because Android's view-state restoration synchronously fires `setChecked` between `onCreateView` and `onStart`. New `LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT` DataStore key records the snapshot identity at Skip / Confirm time so `onDriveAuthGranted` silently re-enables Drive when the marker matches the remote `exported_at`, the destructive restore-prompt fires at most once per remote snapshot. `WipeRemoteBackupUseCase` clears the marker on `BackupResult.Success`.
- **TASK-44** (2026-05-03), `StatsCalculator.computeStats` cost accumulation hoisted above the `events.size < 2` early-return so single-event periods now report `totalCost`. The mixed-currency rule still wins (`totalCost = null` on ≥ 2 distinct currencies). Closed BUG-03 from the 2026-05-03 audit: Dashboard "Total cost" now agrees with the monthly cost chart for any single period.
- **TASK-09** (2026-05-03), date-ranged CSV export + canonical 14-column schema (`event_date_iso, car_name, odometer_km, kwh, kwh_source, charge_type, location, cost_total, cost_per_kwh, currency, km_per_kwh, soc_before, soc_after, note`). Distance always emits as canonical km regardless of display preference. `export(carId, range: LongRange?)` for full-history (`null`) or range-filtered. Text columns route through hardened `csvEscape` (RFC 4180 + OWASP formula-injection prefixes from TASK-52); numeric / timestamp columns deliberately bypass.
- **TASK-07** (2026-05-01), Drive backup error handling. `BackupResult` sealed class (`Success` / `AuthRequired` / `Failure(reason, cause?)`). `DriveBackupRepository` runs its own bounded retry loop (3 attempts, 250ms × 2^attempt) for transient failures only, IOException, HTTP 429, HTTP 5xx, HTTP 403 with quota/rate reasons. Non-recoverable: 401 → `AuthRequired`; 403 auth → `AuthRequired`; 403 `storageQuotaExceeded` → `Failure("Drive storage full")`; unknown 403 body → conservative `AuthRequired`. `DriveBackupWorker.doWork()` is a `when (result)` translator, **never returns `Result.retry()`** so the inner retry budget isn't amplified.

Repo root holds only `README.md`, `CLAUDE.md`, and build/config files. All project documentation lives under `docs/`:

- `README.md`, user-facing landing page (About / Download / Privacy / License).
- `CONTRIBUTING.md`, contributor entry point: architecture, build, test, CI gate, releasing, SPDX-header rule, project-doc index.
- `PRIVACY.md`, privacy policy (referenced from the Play Store listing).
- `docs/DESIGN.md`, canonical product + technical spec (v3). Source of truth when in conflict with anything else.
- `docs/GOOGLE_CLOUD_SETUP.md`, Drive API + OAuth Android client setup.
- `docs/BACKLOG.md`, open backlog of post-v1 refactors and new features (live).
- `docs/TEST_PLAN.md`, full test specification (all phases merged).
- `docs/METHODOLOGY.md`, CO₂ tracker methodology, coefficients, and sources (TASK-20).

## Build & Test

```bash
./gradlew assembleDebug                        # target APK path: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                      # signed if keystore.properties exists, unsigned otherwise
./gradlew test                                 # JVM unit tests (~430)
./gradlew connectedAndroidTest                 # Espresso/Room, needs API 26+ device or emulator (canonical CI execution: nightly-instrumented.yml, API 26 + API 35 matrix)
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.UnitConverterTest.kmToMiles_positive"
```

Requires `ANDROID_HOME` set and Build Tools 35.

### Build types (TASK-29, merged 2026-05-01)

- **Debug** has `applicationIdSuffix = ".debug"` (so the runtime package becomes `org.spsl.evtracker.debug`) and `versionNameSuffix = "-debug"` (so `BuildConfig.VERSION_NAME` resolves to e.g. `"1.0.1-debug"`). Debug and release can be installed side-by-side on the same device.
- **`buildConfig = true`** is enabled in `buildFeatures`. `BuildConfig.VERSION_NAME`, `VERSION_CODE`, `DEBUG`, plus three custom fields, `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`, `DRIVE_FOLDER_SUFFIX`, resolve at compile time under `org.spsl.evtracker.BuildConfig` (the namespace, not the runtime package). The custom fields are scaffolding: declare consumers in the task that needs them. Both build types must declare every custom field, AGP fails the build if release omits a field that debug declares (or vice versa).
- **Drive on debug builds requires a third OAuth Android client** registered for `org.spsl.evtracker.debug` + the debug keystore SHA-1. See `docs/GOOGLE_CLOUD_SETUP.md` Step 5b. Until that client exists, Drive sign-in fails on debug builds; release is unaffected because release keeps `applicationId = org.spsl.evtracker`.

### Static analysis gate (TASK-16, merged 2026-04-30)

PRs and pushes to `main` are gated by `.github/workflows/ci.yml`. The same gate runs locally:

```bash
./gradlew ktlintCheck                          # ktlint 12.1.1, Kotlin official style; auto-fix with ktlintFormat
./gradlew :app:lint                            # Android Lint, error-mode for HardcodedText/MissingTranslation/TypographyDashes/UnusedResources
./gradlew :app:testDebugUnitTest               # bundled into the same CI job
```

- Style is anchored by the repo-root `.editorconfig` (`ktlint_code_style = intellij_idea`, 4-space indent). The IDE's reformat output and ktlint's check agree.
- Pre-existing lint offenses are absorbed by `app/lint-baseline.xml`. **Only new violations break the build.** Regenerate the baseline only when retiring a rule (`./gradlew :app:updateLintBaseline`); do not regenerate to "clean up", the baseline is append-only-by-omission.
- `MissingTranslation` is in error mode and protects coverage across the four shipped locales (`values/`, `values-el/`, `values-tr/`, `values-ru/`). New translatable strings must land in every locale file or the build fails.
- The release workflow (`.github/workflows/release.yml`) is independent; tag pushes still go through it.

## Release & CI

- **Signing:** `app/build.gradle.kts` reads a gitignored `keystore.properties` at repo root. Required keys: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. If the file is absent, `assembleRelease` still runs but produces an unsigned APK. The release keystore is **not** stored in the repo or in OneDrive; keep it under `~/keystores/` or a password manager.
- **CI workflow:** `.github/workflows/release.yml` triggers on `push: tags: 'v*'` and on `workflow_dispatch`. It decodes the keystore from a base64 secret, writes a transient `keystore.properties`, runs `:app:assembleRelease`, verifies the APK with `apksigner verify`, and uploads `joulie-<tag>.apk` as both an Actions artifact and a GitHub Release asset (release auto-created with generated notes).
- **Required GitHub Secrets:** `KEYSTORE_BASE64` (base64 of the release `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Each release keystore SHA-1 also needs its own Google Cloud OAuth Android client (see `docs/GOOGLE_CLOUD_SETUP.md` Step 5) or Drive sign-in fails on release builds.
- **ADI (Android Developer Verification) registration token** lives at `app/src/main/assets/adi-registration.properties`, gitignored, sensitivity-class same as `keystore.properties`. The file is a single line containing the developer-account-specific snippet from Google's verification page (e.g. `CZCZQUJG5FL76AAAAAAAAAAAAA`). Locally-built release APKs that include this asset can be uploaded to Google's verification page to register the developer identity against the release keystore's SHA-256 (registered fingerprint for `org.spsl.evtracker`: `1C:14:8E:BE:84:10:70:B3:F6:C4:74:F1:ED:32:4A:FA:D3:33:3A:FB:73:57:53:B9:DF:B9:13:46:AE:71:FD:78`). The CI release workflow does NOT currently bake this asset, TASK-56 captures the wiring (GitHub Secret + `printf`-into-assets step + post-build `unzip -p` verification). Until TASK-56 lands, only locally-built APKs are registered; CI tag pushes produce unregistered APKs.
- **Cutting a release:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then `git tag vX.Y.Z` and `git push origin vX.Y.Z`. Tag pushes are run separately from the commit push per the global no-compound-git rule.

## Architecture (4-layer)

```
UI:       Fragments + ViewModels (Wizard ✓, Dashboard ✓, ChargeEdit ✓, Cars ✓, History ✓, Charts ✓, Settings ✓, ManageLocations ✓)
          BottomNavigationView in MainActivity reads the per-destination `hideBottomNav` arg in nav_graph.xml (TASK-27)
          ui/common/        MoneyFormat · DateFormat · PeriodLabels (pure helpers)
          core/model/ states DashboardScreenState · ChargeEditUiState · CarsUiState · CarFormState · HistoryUiState
Domain:   Use cases  SaveChargeEvent · DeleteChargeEvent · ObserveDashboardStats · RestoreBackup · ExportCsv
                     AddCar · RenameCar · DeleteCar (D) · PushBackupNow · WipeRemoteBackup (TASK-31)
          Services   StatsCalculator · CostParser · UnitConverter · DateRangeResolver · BackupSerializer · CapacityEstimator (TASK-14)
                     BackupOutcomeReporter (TASK-19, in domain/notification/)
                     LastChargeWidgetSnapshot (TASK-12, in domain/widget/)
          Narrow IFs CarReader · CarWriter (D) · ChargeEventQueries · ChargeEventWriter · LocationReader · LocationWriter · SettingsReader · SettingsWriter
          Backup IFs BackupScheduler · BackupRepository · RestoreTransactionRunner · RestoreSnapshotWriter · CsvFileSink · BackupNotifier (TASK-19) · WidgetRefresher (TASK-12)
Repo:     CarRepository (CarReader + CarWriter) · ChargeEventRepository · LocationRepository · SettingsRepository · BackupRepository
Data:     Room (CarDao, ChargeEventDao, CustomLocationDao) · Preferences DataStore · Drive AppData client (E ✓) · WorkManager backup scheduler (E ✓)
          AndroidBackupNotifier (TASK-19, in data/notification/)
          AndroidWidgetRefresher + LastChargeWidget AppWidgetProvider (TASK-12, in widget/ and data/widget/)
```

Legend: ✓ = wired

Single-Activity + Navigation Component. ViewBinding enabled. MPAndroidChart for charts. WorkManager is used for backup scheduling only, not as a substitute for domain logic.

**Narrow domain-interface rule (TASK-24, merged 2026-05-01).** ViewModels, Activities, Fragments, use cases, and the `Application` class depend only on `domain/repository/*` interfaces (`CarReader`, `ChargeEventQueries`, `SettingsReader`, `SettingsWriter`, `DataResetTransactionRunner`, etc.). Concrete `data.repository.*` classes are referenced only inside `di/` modules where Hilt binds them. Any new `import org.spsl.evtracker.data.repository.*` line outside `di/` is an architecture violation, the audit command is `grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"` and must return empty. When a concrete `SettingsRepository` method needs to be reachable from the UI/domain layer, promote its signature to `SettingsWriter` (or `SettingsReader`), keep atomic multi-key writes intact when doing so.

**Bottom-nav visibility (TASK-27, merged 2026-05-01).** Each navigation destination that should hide the global `BottomNavigationView` declares `<argument android:name="hideBottomNav" app:argType="boolean" android:defaultValue="true"/>` in `res/navigation/nav_graph.xml`. `MainActivity` reads the argument generically inside `addOnDestinationChangedListener` (`args?.getBoolean("hideBottomNav") ?: false`) and never references specific destination IDs for visibility decisions. Destinations that omit the argument default to `false` (nav visible). Adding a new full-screen destination is a nav-graph edit only, never edit `MainActivity` for this. Currently set on `wizardFragment`, `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`.

`activity_main.xml` is a vertical `LinearLayout` with `FragmentContainerView` at `0dp`/`weight=1` and `BottomNavigationView` at `wrap_content` so the host always fills exactly the space above the actual measured nav-bar height across font scales and Material 3 theme variations. Each Fragment's own root layout is a `CoordinatorLayout` for Snackbar/FAB anchoring.

## Invariants, read before changing data, math, or storage

These are easy to break by accident and the canonical source of truth is `docs/DESIGN.md`:

- **Odometer is always stored in km.** Unit toggle (km ↔ miles) is display-only; never rewrite stored values.
- **Cost = 0 or blank ⇒ `costTotal` and `costPerKwh` are stored `NULL`.** Events with `costTotal IS NULL` are excluded from every cost stat, every cost chart series, and the dashboard cost row hides when no costed events exist in the period. Use `parseCost(value, kwh, mode)` (returns `Pair<Double?, Double?>`) before insert. When `parseCost` is called with both fields populated, the **total** wins (per DESIGN §4.1).
- **Multi-currency periods:** if any two costed events in the visible period have different `currency` values, every cost stat returns `null` and the Dashboard shows a "Multi-currency period, cost stats hidden" banner instead of the cost cards.
- **Efficiency uses delta-odometer:** for sorted events, `dist = events[i].odometerKm - events[i-1].odometerKm`; skip rows where `dist <= 0`. The first event for a car cannot compute efficiency, show `"—"`. Aggregates use weighted averages: `Σ d_km / Σ e`, `Σ cost / Σ d_km`. Single-event periods still report `totalKwh` and `chargeCount`, only delta-based metrics are `null`.
- **Wizard gate:** on `MainActivity.onCreate`, if DataStore key `setupComplete` is `false` (default), navigate to `wizardFragment`. The wizard has **four** pages: 0=Welcome, 1=Metric/Unit, 2=Currency, 3=About + Disclaimer acceptance. The Finish button on page 3 is **disabled until the user toggles the `wizard_page4_accept` switch**, disclaimer acceptance is a hard gate, not advisory. The wizard's `finish()` writes `primaryMetric`, `distanceUnit`, `currency`, and `setupComplete=true` together. Settings → Reset preferences sets `setupComplete=false` and re-routes to the wizard, forcing the user to re-accept the disclaimer. Mid-wizard kill must leave `setupComplete=false`.
- **Wizard page 1 coupling:** see DESIGN §3.4 for the full metric→unit table. `mi_per_kwh` ⇒ `miles`; `km_per_kwh` and `kwh_per_100km` ⇒ `km`.
- **Location chips:** the form always shows three fixed chips (🏠 Home · 💼 Work · ⚡ Public) followed by the **top 5** custom labels from `custom_locations` (`ORDER BY useCount DESC, lastUsed DESC LIMIT 5`), then a `+ Add` chip. On save, call `LocationRepository.recordUsage(label)` (insert-or-increment).
- **Multi-car scope:** `activeCarId` lives in DataStore (`-1` = none). All queries filter by `carId`. Deleting a car cascades to its `charge_events` (FK `ON DELETE CASCADE`). "Reset all data" supports per-car or global.

## Database, Room v7

Entities: `Car`, `ChargeEvent`, `CustomLocation`. Current `@Database(version = 7)`. All three entities use `@PrimaryKey val id: Long` and `ChargeEventEntity.carId: Long` (TASK-26). The `@TypeConverters(ChargeTypeConverter::class, ChargeKwhSourceConverter::class)` annotation on `AppDatabase` lets Room round-trip the `chargeType` column between SQLite TEXT and the `ChargeType` enum (TASK-25), and the `kwhSource` column between SQLite TEXT and the `ChargeKwhSource` enum (TASK-43). `ChargeEventEntity` carries optional `socBefore: Double?` and `socAfter: Double?` fields (TASK-14) stored as fractions in `0.0..1.0`, and a non-null `kwhSource: ChargeKwhSource = ChargeKwhSource.MEASURED` provenance flag (TASK-43).

Migrations are mandatory and registered in `DatabaseModule.provideDatabase`:
- `MIGRATION_1_2`: adds `chargeType TEXT NOT NULL DEFAULT 'AC'` to `charge_events`.
- `MIGRATION_2_3`: creates `custom_locations` (camelCase columns `useCount`, `lastUsed`; unique index on `label`) and adds `costTotal`, `costPerKwh`, `currency`, `location`, `note` to `charge_events`. Note `note` ALTER must include `NOT NULL DEFAULT ''` to match the entity's non-nullable `String = ""`.
- `MIGRATION_3_4` (TASK-25): rewrites legacy `'DC'` chargeType cells to `'DC_FAST'` so the enum-backed `ChargeTypeConverter` reads canonical values; column type stays TEXT, no DDL change.
- `MIGRATION_4_5` (TASK-26): no-op migration that bumps the schema version to 5 alongside the Kotlin `Int` → `Long` PK widening. SQLite `INTEGER` columns already store 64 bits, so no DDL is needed, the migration registration acts as a tripwire so future downgrades trip Room's schema validator instead of silently truncating Long values to Int.
- `MIGRATION_5_6` (TASK-14): adds nullable `socBefore` and `socAfter` REAL columns to `charge_events` for state-of-charge data. Both columns default to NULL on legacy rows; `CapacityEstimator` consumes them when present (else falls back to the 80%-of-nominal heuristic).
- `MIGRATION_6_7` (TASK-43): adds `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` to `charge_events`. Round-tripped via `ChargeKwhSourceConverter` into the `ChargeKwhSource` enum (`MEASURED` / `DERIVED_FROM_SOC`). Legacy rows backfill cleanly to `MEASURED`. `CapacityEstimator` skips `DERIVED_FROM_SOC` events on both the exact and heuristic paths because the derived `kwhAdded` is tautological against `Δsoc × nominalBatteryKwh`.

Indices on `charge_events`: composite `(carId, eventDate)` (matches dominant range query), `chargeType`, `location`. When adding a column, bump the version, add a migration that uses **camelCase** column names (Room's default for entity fields without `@ColumnInfo`), and add a `MigrationTest` case (see docs/TEST_PLAN.md §2.4).

## Google Drive backup

- Scope: `https://www.googleapis.com/auth/drive.appdata` (non-sensitive). File lives in the **App Data folder** (hidden from Drive UI), filename `evtracker_backup.json`.
- Backup JSON schema is versioned: current `backup_version = 7` and **must include `custom_locations`** with `label`, `useCount`, `lastUsed`. Bumping any entity requires bumping `backup_version` and updating the authoritative field list in `docs/DESIGN.md §8`. `BackupSerializer.fromJson` accepts `{3, 4, 5, 6, 7}` (legacy `chargeType = "DC"` decodes to `ChargeType.DC_FAST` via `ChargeTypeJsonAdapter`; v3/v4 Int ids narrow into v5+ Long DTO fields automatically via Gson; v3/v4/v5 backups simply leave the new optional `soc_before`/`soc_after` fields at `null`; v3..v6 backups likewise omit `kwh_source`, and `ChargeEventDto.toEntity()` coalesces the absent value to `ChargeKwhSource.MEASURED`).
- Backup model: the Drive file is a full snapshot. On first Drive enable, an existing remote snapshot uses a **replace-or-skip** flow; merge is not supported.
- Auto-backup: WorkManager `OneTimeWorkRequest` after every committed local change that affects the snapshot payload: charge event create/edit/committed delete, car create/edit/delete, custom-location committed delete, reset flows, successful restore, and first-time Drive enable when no remote backup exists. Required configuration: `NetworkType.CONNECTED`, `enqueueUniqueWork("drive_backup", REPLACE, ...)`, and exponential backoff starting at 30 s.
- Error model (TASK-07): `BackupRepository.backupCurrentData()` returns `BackupResult` (`Success` | `AuthRequired` | `Failure(reason, cause?)`). The repo runs its own bounded retry loop (`MAX_ATTEMPTS = 3`, exponential backoff 250 ms × 2^attempt) for transient failures only, network IOException, HTTP 429, HTTP 5xx, HTTP 403 with quota/rate reasons. Non-recoverable failures short-circuit: 401 → `AuthRequired`; 403 with auth reason → `AuthRequired`; 403 with `storageQuotaExceeded` → `Failure("Drive storage full")`; 403 with unknown / unparseable body → conservative `AuthRequired`. `DriveBackupWorker.doWork()` is a thin `when (result)` translator, `Result.success()` for Success, `Result.failure()` for everything else (no `Result.retry()`, since the repo already exhausted the retry budget). All non-recoverable paths log via `android.util.Log.e("DriveBackupRepository", ..., cause)`.
- Restore flow: on first Drive enable, fetch the file → if present, prompt "Found backup from [date]. This will replace data already on this device. Restore?" → on confirm, **first** export current local DB to `cacheDir/last_overwritten_backup.json`, then clear and import in one transaction; on skip, keep local data unchanged and continue with backup enabled. The undo snapshot is best-effort because cache eviction can remove it before the 24 h target.
- Restore-prompt suppression (TASK-54): the user's Skip / Confirm decision is recorded as the snapshot's `exported_at` string in DataStore key `lastSeenRemoteBackupExportedAt`. On every `onDriveAuthGranted` call, `SettingsViewModel` compares the remote `exported_at` to the marker, when they match, Drive is silently re-enabled and `enqueueBackup()` runs without firing `ShowRestorePrompt`. `WipeRemoteBackupUseCase` resets the marker to `""` on success so a fresh upload + Drive re-toggle prompts exactly once for the new snapshot. The destructive restore-prompt is therefore shown at most once per remote snapshot identity.
- Multi-currency rule: `StatsCalculator` returns `null` cost stats whenever a period contains charge events with more than one distinct `currency`. Dashboard surfaces a "Multi-currency period, cost stats hidden" banner.
- Failure notifications (TASK-19): `BackupOutcomeReporter` (in `domain/notification/`) increments `consecutiveBackupFailures` on each `Failure` / `AuthRequired` and resets to 0 on `Success`. At threshold 3 it calls `BackupNotifier.notifyChronicFailure()` (channel `backup_status`, IMPORTANCE_LOW, sticky). `AuthRequired` always fires `notifyAuthRequired()` (channel `backup_auth`, IMPORTANCE_DEFAULT). Both channels are created in `EVTrackerApp.onCreate` via `AndroidBackupNotifier.ensureChannels(this)`. `MainActivity` shows the `POST_NOTIFICATIONS` rationale + system request only when failures cross threshold AND permission missing AND user hasn't previously denied AND API >= 33, denial is sticky via `notificationPermissionDenied`. `safeNotify` gates `NotificationManagerCompat.notify` on `areNotificationsEnabled()` so the call sites are silent no-ops when permission is missing.
- Manual controls (TASK-31): Settings → Drive section exposes "Back up now" and "Wipe remote backup" buttons (visibility gated on `driveEnabled`). Both invoke standalone use cases (`PushBackupNowUseCase`, `WipeRemoteBackupUseCase`), the auto-backup `WorkManager` worker contract is unchanged; manual push is one extra path, not a replacement. Wipe calls `BackupRepository.deleteRemoteBackup()` which lists `appDataFolder` for `evtracker_backup.json`, calls Drive `files.delete`, and (on `Success`) clears `lastBackupAt = 0L` so the UI's stale-timestamp hint reverts. Wipe is gated by a `MaterialAlertDialog` confirmation. Mutual exclusion: a slow push blocks wipe and vice versa via `isManualBackupRunning` / `isManualWipeRunning` on `SettingsUiState`.

OAuth setup (full walkthrough in `docs/GOOGLE_CLOUD_SETUP.md`):
- Android OAuth client is bound to package name `org.spsl.evtracker` + keystore SHA-1. Don't change `applicationId` casually, it invalidates the OAuth client.
- Debug and release builds need **separate** OAuth clients (one per keystore SHA-1). Consent screen can stay in "Testing" status; tester emails must be allow-listed.
- **No `google-services.json`, no Firebase, no `com.google.gms.google-services` plugin.** The Authorization API (`Identity.getAuthorizationClient`) reads the OAuth client at runtime from your package + signing-cert SHA-1; nothing else is needed at build time. If you see references to placing `google-services.json` in `app/`, they are stale, the file is in `.gitignore` solely to defang accidental commits if a contributor adds Firebase later.
- The deprecated `GoogleSignIn.getClient(...)` API must not be used in new code. Use the Authorization API.
- Verify a backup landed by calling Drive `files.list` with `spaces=appDataFolder`, the App Data folder is hidden from the Drive UI.

## DataStore keys

Declared in a single `PreferenceKeys` object: `setupComplete`, `primaryMetric` (`km_per_kwh` | `kwh_per_100km` | `mi_per_kwh`), `distanceUnit` (`km` | `miles`), `currency`, `activeCarId`, `driveEnabled`, `theme`, `consecutiveBackupFailures` (TASK-19), `notificationPermissionDenied` (TASK-19), `lastSeenRemoteBackupExportedAt` (TASK-54, ISO-8601 string of the remote snapshot most recently Skipped or Restored; empty string = never seen; reset to `""` on `WipeRemoteBackupUseCase` success). Add new keys here, not inline.

## CSV export

`ExportCsvUseCase` writes to `getExternalFilesDir(DIRECTORY_DOWNLOADS)` and shares via `FileProvider` authority `${packageName}.fileprovider`. Schema is the canonical 14-column header (TASK-09): `event_date_iso, car_name, odometer_km, kwh, kwh_source, charge_type, location, cost_total, cost_per_kwh, currency, km_per_kwh, soc_before, soc_after, note`. Same header for full-history (`export(carId)`) and date-ranged (`export(carId, range: LongRange)`) exports, research consumers anchor on a stable schema. Distance always emits as canonical kilometres regardless of the user's display preference; the `useKm` flip was dropped in TASK-09 for locale-independence. `km_per_kwh` is computed per-row using the same delta-odometer convention as `StatsCalculator` (first row blank because no prior event exists in the exported slice; `prevOdo` advances unconditionally so transient rollbacks don't break the chain). All text columns route through the hardened `csvEscape` from TASK-52 (RFC 4180 + OWASP formula-injection prefixes); numeric / timestamp columns bypass.

## Conventions

- Add new screens by creating a Fragment + ViewModel pair and wiring into the Nav graph; do not introduce a second Activity.
- New efficiency or cost metrics: extend `Stats` / `EfficiencyStats` and the dashboard card layout; keep the formulas table in `docs/DESIGN.md §7` in sync.
- When changing the wizard, update `WizardViewModelTest` and `WizardFlowTest` (docs/TEST_PLAN.md §3.2, §4.1), the gate behavior is covered by tests.

### ViewModel + event pattern (D-era)

ViewModels expose:
- `val uiState: StateFlow<XxxUiState>` built via `combine`/`flatMapLatest` over the narrow domain interfaces. Default values on every state field so VMs can use `MutableStateFlow(XxxUiState())` cheaply.
- `val events: SharedFlow<XxxEvent>` for one-shot effects (Snackbar, navigate, dialog). **Always `replay = 0`** with `extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST`, fragments collect inside `repeatOnLifecycle(STARTED)` and a non-zero replay would re-fire navigation events on rotation/back-stack pop.

Tests that need to observe an emitted event must subscribe BEFORE `tryEmit`:

```kotlin
val received = mutableListOf<XxxEvent>()
val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received += it } }
vm.someTrigger()
advanceUntilIdle()  // or runCurrent(), required to flush the launched collection
job.cancel()
assertTrue(received.first() is ExpectedEvent)
```

History's swipe-delete uses a 5s cancellable `Job` per event id; tests use `StandardTestDispatcher` + `advanceTimeBy(5_001)` for time control. Don't use `UnconfinedTestDispatcher` for time-sensitive tests, it runs `delay` synchronously.

### Test infrastructure

JVM tests construct real domain use cases (`ObserveDashboardStatsUseCase`, `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`) wired through fakes in `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. Existing fakes:
`FakeCarReader`, `FakeCarRepository` (impl `CarReader, CarWriter` with shared `MutableStateFlow`), `FakeChargeEventQueries`, `FakeChargeEventWriter`, `FakeLocationReader`, `FakeLocationWriter`, `FakeSettingsReader`, `FakeSettingsWriter`, `FakeBackupScheduler`, `FakeBackupRepository` (returns `nextBackupResult: BackupResult` per TASK-07), `FakeDriveRemoteSource` (raises `failNext` for `failTimes` calls then auto-resets; exposes `attemptCount` + `failuresRaised` for retry-loop assertions), `FakeDriveAuthManager`, `FakeRestoreTransactionRunner`, `FakeRestoreSnapshotWriter`, `FakeSaveChargeEventGateway` (real `SaveChargeEventUseCase` wired through the chargeevent/location/backup fakes), `FakeNowProvider`.

> **Sandbox quirk for AI agents:** when Gradle's default `~/.gradle` is read-only in the runner, prefix commands with `GRADLE_USER_HOME=/tmp/gradle-home`. Human contributors don't need this.

### Branch + commit workflow

Tasks land on `feat/<name>` branches and merge to `main` via `--no-ff`, followed by a separate push and `git branch -d`. Never compound git commands, CLAUDE.md global rule.


## Compaction Policy
When compacting, always preserve:
- Current step in the multi-step build plan and acceptance criteria
- Architecture (MVVM/MVI/Clean), module graph, DI setup
- Full list of modified/created files and current test/lint status
- Gradle version catalog and dependency deltas
- Open bugs, failing tests, and follow-ups that haven't been resolved yet.
Discard: file-exploration transcripts, resolved threads, verbose tool output.
Update: the "Status" section with the new progress, and ensure all instructions are still accurate for the current state of the codebase.