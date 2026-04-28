# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`org.spsl.evtracker`) for logging EV charge events and analyzing efficiency/cost. Kotlin, MVVM with a domain/use-case layer plus narrow repositories, Gradle Kotlin DSL, and Hilt-based dependency injection. Min SDK 26, target/compile SDK 34, JDK 17. Room compiler runs via **KSP** (not kapt).

> **Status:** Sub-projects A (foundation/DI/Room v3), B (repositories), C (domain services + use cases), and D (Core UI: Dashboard/ChargeEdit/Cars/History) are all merged. Wizard, Dashboard, ChargeEdit, Cars, and History are fully wired; Charts, Settings, and ManageLocations remain placeholder fragments until F. Sub-project E (real Drive auth + WorkManager-backed `BackupScheduler`/`BackupRepository`) lives behind no-op interfaces. JVM unit-test count: ~123. Instrumented suite compiles via `:app:assembleDebugAndroidTest` (running requires an emulator).

Root docs:
- `DESIGN.md` — canonical product + technical spec (v3). Source of truth when in conflict with anything else.
- `AGENT_INSTRUCTIONS.md` — full implementation walkthrough (all phases merged).
- `TEST_PLAN.md` — full test specification (all phases merged).
- `GOOGLE_CLOUD_SETUP.md` — Drive API + OAuth Android client setup.

## Build & Test

```bash
./gradlew assembleDebug                        # target APK path: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                      # needs keystore
./gradlew test                                 # JVM unit tests
./gradlew connectedAndroidTest                 # Espresso/Room — needs API 26+ device or emulator
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.UnitConverterTest.kmToMiles_positive"
```

Requires `ANDROID_HOME` set and Build Tools 34.

## Architecture (4-layer)

```
UI:       Fragments + ViewModels (Wizard ✓, Dashboard ✓, ChargeEdit ✓, Cars ✓, History ✓, Charts ⊘, Settings ⊘, ManageLocations ⊘)
          BottomNavigationView in MainActivity hides on Wizard / ChargeEdit / Cars / ManageLocations
          ui/common/        MoneyFormat · DateFormat · PeriodLabels (pure helpers)
          core/model/ states DashboardScreenState · ChargeEditUiState · CarsUiState · CarFormState · HistoryUiState
Domain:   Use cases  SaveChargeEvent · DeleteChargeEvent · ObserveDashboardStats · RestoreBackup · ExportCsv
                     AddCar · RenameCar · DeleteCar (D)
          Services   StatsCalculator · CostParser · UnitConverter · DateRangeResolver · BackupSerializer
          Narrow IFs CarReader · CarWriter (D) · ChargeEventQueries · ChargeEventWriter · LocationReader · LocationWriter · SettingsReader · SettingsWriter
          Backup IFs BackupScheduler · BackupRepository · RestoreTransactionRunner · RestoreSnapshotWriter · CsvFileSink
Repo:     CarRepository (CarReader + CarWriter) · ChargeEventRepository · LocationRepository · SettingsRepository · BackupRepository
Data:     Room (CarDao, ChargeEventDao, CustomLocationDao) · Preferences DataStore · Drive AppData client (E) · WorkManager backup scheduler (E)
```

Legend: ✓ = wired in D · ⊘ = placeholder fragment until F.

Single-Activity + Navigation Component. ViewBinding enabled. MPAndroidChart for charts. WorkManager is used for backup scheduling only, not as a substitute for domain logic.

`activity_main.xml` is a vertical `LinearLayout` with `FragmentContainerView` at `0dp`/`weight=1` and `BottomNavigationView` at `wrap_content` so the host always fills exactly the space above the actual measured nav-bar height across font scales and Material 3 theme variations. Each Fragment's own root layout is a `CoordinatorLayout` for Snackbar/FAB anchoring.

## Invariants — read before changing data, math, or storage

These are easy to break by accident and are scattered across DESIGN.md / AGENT_INSTRUCTIONS.md:

- **Odometer is always stored in km.** Unit toggle (km ↔ miles) is display-only; never rewrite stored values.
- **Cost = 0 or blank ⇒ `costTotal` and `costPerKwh` are stored `NULL`.** Events with `costTotal IS NULL` are excluded from every cost stat, every cost chart series, and the dashboard cost row hides when no costed events exist in the period. Use `parseCost(value, kwh, mode)` (returns `Pair<Double?, Double?>`) before insert. When `parseCost` is called with both fields populated, the **total** wins (per DESIGN §4.1).
- **Multi-currency periods:** if any two costed events in the visible period have different `currency` values, every cost stat returns `null` and the Dashboard shows a "Multi-currency period — cost stats hidden" banner instead of the cost cards.
- **Efficiency uses delta-odometer:** for sorted events, `dist = events[i].odometerKm - events[i-1].odometerKm`; skip rows where `dist <= 0`. The first event for a car cannot compute efficiency — show `"—"`. Aggregates use weighted averages: `Σ d_km / Σ e`, `Σ cost / Σ d_km`. Single-event periods still report `totalKwh` and `chargeCount` — only delta-based metrics are `null`.
- **Wizard gate:** on `MainActivity.onCreate`, if DataStore key `setupComplete` is `false` (default), navigate to `wizardFragment`. The wizard's `finish()` writes `primaryMetric`, `distanceUnit`, `currency`, and `setupComplete=true` together. Settings → Reset preferences sets `setupComplete=false` and re-routes to the wizard. Mid-wizard kill must leave `setupComplete=false`.
- **Wizard page 2 coupling:** see DESIGN §3.4 for the full metric→unit table. `mi_per_kwh` ⇒ `miles`; `km_per_kwh` and `kwh_per_100km` ⇒ `km`.
- **Location chips:** the form always shows three fixed chips (🏠 Home · 💼 Work · ⚡ Public) followed by the **top 5** custom labels from `custom_locations` (`ORDER BY useCount DESC, lastUsed DESC LIMIT 5`), then a `+ Add` chip. On save, call `LocationRepository.recordUsage(label)` (insert-or-increment).
- **Multi-car scope:** `activeCarId` lives in DataStore (`-1` = none). All queries filter by `carId`. Deleting a car cascades to its `charge_events` (FK `ON DELETE CASCADE`). "Reset all data" supports per-car or global.

## Database — Room v3

Entities: `Car`, `ChargeEvent`, `CustomLocation`. Current `@Database(version = 3)`.

Migrations are mandatory and registered in `AppDatabase.getInstance`:
- `MIGRATION_1_2`: adds `chargeType TEXT NOT NULL DEFAULT 'AC'` to `charge_events`.
- `MIGRATION_2_3`: creates `custom_locations` (camelCase columns `useCount`, `lastUsed`; unique index on `label`) and adds `costTotal`, `costPerKwh`, `currency`, `location`, `note` to `charge_events`. Note `note` ALTER must include `NOT NULL DEFAULT ''` to match the entity's non-nullable `String = ""`.

Indices on `charge_events`: composite `(carId, eventDate)` (matches dominant range query), `chargeType`, `location`. When adding a column, bump the version, add a migration that uses **camelCase** column names (Room's default for entity fields without `@ColumnInfo`), and add a `MigrationTest` case (see TEST_PLAN §2.4).

## Google Drive backup

- Scope: `https://www.googleapis.com/auth/drive.appdata` (non-sensitive). File lives in the **App Data folder** (hidden from Drive UI), filename `evtracker_backup.json`.
- Backup JSON schema is versioned: current `backup_version = 3` and **must include `custom_locations`** with `label`, `useCount`, `lastUsed`. Bumping any entity requires bumping `backup_version` and updating the authoritative field list in `DESIGN.md §8`.
- Backup model: the Drive file is a full snapshot. On first Drive enable, an existing remote snapshot uses a **replace-or-skip** flow; merge is not supported.
- Auto-backup: WorkManager `OneTimeWorkRequest` after every committed local change that affects the snapshot payload: charge event create/edit/committed delete, car create/edit/delete, custom-location committed delete, reset flows, successful restore, and first-time Drive enable when no remote backup exists. Required configuration: `NetworkType.CONNECTED`, `enqueueUniqueWork("drive_backup", REPLACE, ...)`, and exponential backoff starting at 30 s.
- Restore flow: on first Drive enable, fetch the file → if present, prompt "Found backup from [date]. This will replace data already on this device. Restore?" → on confirm, **first** export current local DB to `cacheDir/last_overwritten_backup.json`, then clear and import in one transaction; on skip, keep local data unchanged and continue with backup enabled. The undo snapshot is best-effort because cache eviction can remove it before the 24 h target.
- Multi-currency rule: `StatsCalculator` returns `null` cost stats whenever a period contains charge events with more than one distinct `currency`. Dashboard surfaces a "Multi-currency period — cost stats hidden" banner.

OAuth setup (full walkthrough in `GOOGLE_CLOUD_SETUP.md`):
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
- New efficiency or cost metrics: extend `Stats` / `EfficiencyStats` and the dashboard card layout; keep the formulas table in `DESIGN.md §7` in sync.
- When changing the wizard, update `WizardViewModelTest` and `WizardFlowTest` (TEST_PLAN §3.2, §4.1) — the gate behavior is covered by tests.

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
`FakeCarReader`, `FakeCarRepository` (impl `CarReader, CarWriter` with shared `MutableStateFlow`), `FakeChargeEventQueries`, `FakeChargeEventWriter`, `FakeLocationReader`, `FakeLocationWriter`, `FakeSettingsReader`, `FakeSettingsWriter`, `FakeBackupScheduler`, `FakeBackupRepository`, `FakeRestoreTransactionRunner`, `FakeRestoreSnapshotWriter`, `FakeSaveChargeEventGateway` (real `SaveChargeEventUseCase` wired through the chargeevent/location/backup fakes).

Build commands (sandbox quirk: gradle's default `~/.gradle` is read-only here, always `GRADLE_USER_HOME=/tmp/gradle-home`):

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest   # compile-only; running needs an emulator
```

### Sub-project workflow

A/B/C/D used `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:subagent-driven-development` with feat/ branches and per-task spec+code reviews. Specs live at `docs/superpowers/specs/`; plans live at `docs/superpowers/plans/`. Branches merge to `main` via `--no-ff`, then push, then `git branch -d`. Never compound git commands — CLAUDE.md global rule.


## gitflow
If needed to use gitflow, add 
  - name: Write google-services.json
    run: echo '${{ secrets.GOOGLE_SERVICES_JSON }}' > app/google-services.json

## Compaction Policy
When compacting, always preserve:
- Current step in the multi-step build plan and acceptance criteria
- Architecture (MVVM/MVI/Clean), module graph, DI setup
- Full list of modified/created files and current test/lint status
- Gradle version catalog and dependency deltas
- Open bugs, failing tests, and follow-ups that haven't been resolved yet.
Discard: file-exploration transcripts, resolved threads, verbose tool output.