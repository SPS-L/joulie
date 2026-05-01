# EV Tracker — Development Backlog

Tasks 1–15 were generated from a senior Android developer code review of the `main` branch (April 2026). Tasks 16–21 are follow-up improvements identified during a 2026-04-30 verification pass against `main` (CI/release pipeline, R8 keep-rules, a11y posture, and SPS-Lab research relevance). Each task is written as a self-contained instruction suitable for a coding agent.

---

## Task Overview

| Task | Priority | Description | Requires | Done |
|------|----------|-------------|----------|------|
| TASK-01 | 🔴 | Relocate `AggregationDispatcher` out of `di/` | — | ☑ |
| TASK-02 | 🔴 | Add safeguard KDoc to `RoomDataResetTransactionRunner` (structural rule already holds) | — | ☑ |
| TASK-03 | — | ~~Unify `UiState` vs `ScreenState` naming in `core/model`~~ — **closed, premise wrong** | — | ☒ |
| TASK-04 | 🟡 | JVM unit tests for `CostParser` | — | ☑ |
| TASK-05 | — | ~~JVM unit tests for `EfficiencyPoint`~~ — **closed, premise wrong** | — | ☒ |
| TASK-06 | 🟡 | JVM unit tests for use cases | — | ☑ |
| TASK-07 | 🟡 | Drive backup error handling & retry logic | — | ☑ |
| TASK-08 | 🟢 | Replace `CarEditDialog` with a Compose `AlertDialog` (requires adding Compose) | — | ☐ |
| TASK-09 | 🟢 | CSV export of charge events with efficiency column, date-range picker | — | ☐ |
| TASK-10 | 🟢 | In-app About / Info screen with SPS-Lab acknowledgment | — | ☑ |
| TASK-11 | 🟡 | Odometer regression detection UX improvement | — | ☑ |
| TASK-12 | 🟡 | Widget: last-charge summary on home screen | — | ☐ |
| TASK-13 | 🟢 | Charging session timer / live session mode | — | ☐ |
| TASK-14 | 🟡 | Battery capacity degradation tracker | — | ☑ |
| TASK-15 | 🟢 | Localisation (i18n) foundation | TASK-16 | ☐ |
| TASK-16 | 🟢 | Static analysis & code style gate in CI (ktlint + Android Lint) | — | ☑ |
| TASK-17 | 🟡 | R8/ProGuard follow-up audit: MPAndroidChart keep rule + release smoke test | — | ☑ |
| TASK-18 | 🟡 | Accessibility (a11y) pass — TalkBack, contentDescription, contrast, touch targets | — | ☐ |
| TASK-19 | 🟡 | Backup failure notification channel + Android 13+ `POST_NOTIFICATIONS` handling | — | ☑ |
| TASK-20 | 🟢 | CO₂ savings tracker (ICE baseline, Cyprus grid intensity, methodology doc) | — | ☐ |
| TASK-21 | 🟢 | Android Baseline Profile module for cold-start performance | — | ☐ |
| TASK-22 | 🔴 | Upgrade `targetSdk` and `compileSdk` to API 35 | TASK-16 | ☑ |
| TASK-23 | 🔴 | Move startup `isLoading` state into `MainViewModel` | — | ☑ |
| TASK-24 | 🔴 | Enforce ViewModel/Activity consumption of the existing narrow domain interfaces (no concrete `data.repository.*` imports outside `di/`) | TASK-23 | ☑ |
| TASK-25 | 🟡 | Replace `chargeType: String` with a sealed class / TypeConverter-backed enum | — | ☑ |
| TASK-26 | 🟡 | Change all Room primary-key and foreign-key fields from `Int` to `Long` | — | ☑ |
| TASK-27 | 🟡 | Decouple bottom-nav visibility from hardcoded `hideOn` set in `MainActivity` | — | ☑ |
| TASK-28 | 🟡 | Consolidate time on existing `NowProvider`; remove direct `System.currentTimeMillis()` from entities and helpers; drop the parallel `() -> Long` clock in `WorkerModule` | — | ☑ |
| TASK-29 | 🟢 | Add explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags | — | ☑ |
| TASK-30 | 🟢 | Migrate from MPAndroidChart to Vico (line/bar) + custom `Canvas` `PieChartView` (pie tabs) | — | ☐ |
| TASK-31 | 🟡 | Manual Drive controls in Settings: "Back up now" (force overwrite) and "Wipe remote backup" (delete the App Data file) | — | ☐ |
| TASK-32 | 🟡 | Bump AGP (and Gradle wrapper) to a version that officially supports `compileSdk = 35`; remove the `android.suppressUnsupportedCompileSdk` workaround | — | ☑ |
| TASK-33 | 🟢 | Audit Kotlin 2.x / K2 + KSP + Hilt compatibility now that AGP 8.7.3 is in place | TASK-32 | ☐ |
| TASK-34 | 🟡 | Nightly managed-AVD job for `connectedAndroidTest` — keep off the PR gate | TASK-16 | ☐ |
| TASK-35 | 🟢 | Roborazzi screenshot tests for Dashboard + Charts (must land before TASK-30) | — | ☐ |
| TASK-36 | 🟡 | Inline-comment the "no `Result.retry()`" invariant in `DriveBackupWorker.doWork()` | — | ☑ |
| TASK-37 | 🔴 | Replace Google Drive backup with a Storage Access Framework (SAF) implementation (F-Droid blocker) | — | ⏸ |

**Priority legend:** 🔴 High (architecture/data safety) · 🟡 Medium (robustness/UX) · 🟢 Low (new feature)  
**Status legend:** ☐ open · ☑ done · ☒ closed (premise no longer holds) · ⏸ under consideration (do not start without explicit go-ahead)  
**Requires column:** `TASK-NN` means the named task should land first. `—` means no hard prerequisite. Soft coordination notes (Room schema-version claiming, TASK-30 keep-rule cleanup) live in *Notes for Agents* below rather than the column.  
Mark done by replacing `☐` with `☑` when a task is merged.

---

## 🔴 TASK-01 — Relocate `AggregationDispatcher` to the correct package ☑ Done (2026-04-30)

> **Outcome:** the file (a Hilt `@Qualifier` annotation, not a class with logic) was relocated from
> `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt` to
> `app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`.
> The new package is a cross-cutting home for coroutine qualifiers (room for `IoDispatcher`,
> `MainDispatcher`, etc.) and removes the previous `domain → di` import in
> `ObserveChartsModelsUseCase`. Imports updated in `di/DispatcherModule.kt` and
> `domain/usecase/ObserveChartsModelsUseCase.kt`. See spec
> `superpowers/specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md`
> and plan `superpowers/plans/2026-04-30-task01-aggregation-dispatcher-relocation.md`.
> The original task text is preserved below for historical context.

The file `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`
is incorrectly placed inside the `di` (dependency injection) package.
A dispatcher is a domain or data concern, not a DI module.

1. Move `AggregationDispatcher.kt` to:
   `app/src/main/java/org/spsl/evtracker/domain/service/AggregationDispatcher.kt`
2. Update all import statements across the project to reflect the new path.
3. If `AggregationDispatcher` is injected via Hilt, update the binding in
   `AppModule.kt` or create a dedicated `DispatcherModule.kt` in `di/` that
   provides it — but the class itself must not live in `di/`.
4. Verify the project builds and all instrumented tests pass after the move.

---

## 🔴 TASK-02 — Add safeguard KDoc to `RoomDataResetTransactionRunner` ☑ Done (2026-05-01)

> **Outcome:** the safeguard KDoc was added to both
> `domain/repository/DataResetTransactionRunner.kt` and
> `data/repository/RoomDataResetTransactionRunner.kt`. The block on the
> interface explains that `clearAllTables()` is reachable only through
> [`ResetAllDataUseCase`] because the use case owns the `resetInProgress`
> durable-flag protocol that lets startup auto-recovery resume a
> half-finished reset; the impl points back to the interface KDoc and
> notes that even the interface is not for general consumption. The
> structural rule already held empirically (`grep` finds only the impl
> file and `di/DomainModule.kt`), and the wider narrow-IF rule from
> TASK-24 is now codified in CLAUDE.md §Architecture, so this KDoc is the
> last layer — a type-level reminder for the next agent reading the
> code. Mechanical enforcement (a custom ktlint rule) remains a TASK-16
> follow-up; the current cost/benefit doesn't justify spinning one up
> for two files. The original task text is preserved below.

> **Verification (2026-04-30):** the structural rule already holds. A
> `grep -rn "RoomDataResetTransactionRunner" app/src/main/java` returns only
> `data/repository/RoomDataResetTransactionRunner.kt` (the implementation) and
> `di/DomainModule.kt` (the `@Binds` to `DataResetTransactionRunner`). All
> consumers — currently only `ResetAllDataUseCase` — depend on the narrow
> `DataResetTransactionRunner` interface. The remaining work is documentation
> only.

1. Add a KDoc to `RoomDataResetTransactionRunner` and to
   `DataResetTransactionRunner` explicitly stating: *"Destructive operation —
   must only be called from `ResetAllDataUseCase`. ViewModels and Fragments
   must not depend on this type or its interface directly."*
2. Optionally add a `lint-baseline.xml` or ktlint custom rule (TASK-16
   follow-up) once that infrastructure exists, to mechanically enforce the
   no-direct-consumer invariant.

---

## ☒ TASK-03 — ~~Unify `UiState` vs `ScreenState` naming convention in `core/model`~~

> **Closed (2026-04-30):** premise is wrong. `ChartsUiState` and
> `ChartsScreenState` are not duplicates. `ChartsScreenState` is the outer
> screen frame (`period`, `distanceUnit`, plus a `charts: ChartsUiState`
> field), and `ChartsUiState` is the inner sealed content state
> (`Loading / NoCar / NoEvents / Loaded`). Same pattern for
> `DashboardScreenState` (frame) wrapping `DashboardUiState` (content with
> `emptyState`, `stats`, `showMultiCurrencyBanner`). The split is intentional;
> renaming would conflate the two layers. If a stylistic rename of `*ScreenState`
> ever becomes desirable, file a fresh task — but it is not the
> deduplication described here.

---

## 🟡 TASK-04 — Add JVM unit tests for `CostParser` ☑ Done (verified 2026-04-30)

> **Outcome:** `app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt`
> exists (55 lines) and covers zero-cost, blank, negative, total→perKwh
> derivation, and per-kWh→total derivation paths. The original task text is
> preserved below for historical context.

The class `app/src/main/java/org/spsl/evtracker/domain/service/CostParser.kt`
has no corresponding JVM unit tests. This is a pure logic class and must be
tested without an emulator.

Create the file:
`app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt`

Write unit tests covering:
1. Standard cost parsing with a valid decimal input (e.g., `"0.25"`).
2. Cost parsing with a comma as decimal separator (e.g., `"0,25"`).
3. Empty string input — expect a specific default or exception.
4. Null input if the function accepts nullable strings.
5. Negative values — define and test expected behavior.
6. Values with currency symbols (e.g., `"€0.25"`) — confirm correct handling.

Use JUnit 4 or JUnit 5 consistent with the existing test setup. Do not use
any Android framework classes — this must be a pure JVM test.

---

## ☒ TASK-05 — ~~Add JVM unit tests for `EfficiencyPoint`~~

> **Closed (2026-04-30):** premise is wrong. `EfficiencyPoint` is a 2-field
> data class (`eventTimeMillis: Long`, `kmPerKwh: Double`) with zero logic.
> The proposed cases (zero distance, NaN handling, overflow,
> cost-per-km derivation) belong to the *producer* of these points, not the
> point itself — the production logic lives in `StatsCalculator` /
> `EfficiencyStats`, which already have JVM tests in
> `app/src/test/java/org/spsl/evtracker/domain/service/`. If gaps exist
> there, file a fresh task naming the missing scenarios.

---

## 🟡 TASK-06 — Add JVM unit tests for `RenameCarUseCase` and `ResetAllDataUseCase` ☑ Done (verified 2026-04-30)

> **Outcome:** both test files exist on `main`:
> `app/src/test/java/org/spsl/evtracker/domain/usecase/RenameCarUseCaseTest.kt`
> (42 lines) and `…/ResetAllDataUseCaseTest.kt` (130 lines), both wired
> through the existing fakes in
> `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. The original task
> text is preserved below for historical context.

The domain use cases have no JVM unit tests. Business logic must be tested
independently of the Android framework.

### `RenameCarUseCaseTest`

Create:
`app/src/test/java/org/spsl/evtracker/domain/usecase/RenameCarUseCaseTest.kt`

Tests required:
- Renaming with a valid non-empty name calls the repository exactly once.
- Renaming with a blank name throws `IllegalArgumentException` or returns
  a failure result (match the actual implementation's contract).
- Renaming with a name exceeding max length (if validated) is rejected.
- Mock `CarRepository` using Mockito or MockK (whichever is already in
  the project's test dependencies).

### `ResetAllDataUseCaseTest`

Create:
`app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt`

Tests required:
- Successful reset calls `RoomDataResetTransactionRunner` exactly once.
- If the transaction runner throws, the use case propagates the exception
  or wraps it correctly.
- Mock all dependencies; do not use a real Room database.

Use `kotlinx-coroutines-test` if the use cases are suspending functions.

---

## 🟡 TASK-07 — Add error handling and retry logic to `DriveBackupRepository` ☑ Done (2026-05-01)

> **Outcome:** new `domain/backup/BackupResult.kt` sealed class —
> `Success` / `AuthRequired` / `Failure(reason, cause?)`. The
> `BackupRepository.backupCurrentData()` contract flips from
> `Unit`-returning + throwing to `BackupResult`-returning, with
> exceptions handled internally. `DriveBackupRepository` gains a
> bounded retry loop: `MAX_ATTEMPTS = 3`, exponential backoff
> `250 ms × 2^attempt` (250 / 500 / 1000 ms total), retrying transient
> failures only — network `IOException` incl. `UnknownHostException`,
> HTTP 429, HTTP 5xx, and HTTP 403 with quota / rate reasons. Auth
> errors (401, 403 auth-reason) and `storageQuotaExceeded` 403 short-
> circuit the loop; unknown / unparseable 403 bodies stay on the
> conservative-auth path. The previously-missing `storageQuotaExceeded`
> branch (Drive full) now correctly maps to
> `Failure("Drive storage full", cause)` instead of being lumped into
> the conservative-auth fallback. Every non-recoverable path logs via
> `android.util.Log.e("DriveBackupRepository", ..., cause)`;
> `app/build.gradle.kts` gains `testOptions.unitTests.isReturnDefaultValues
> = true` so JVM tests don't blow up on the Android stub.
> `DriveBackupWorker.doWork()` collapses from a multi-catch ladder to
> a `when (result)` translator and stops returning `Result.retry()` —
> the repo already exhausted the retry budget, so amplifying it via
> WorkManager would duplicate effort. The read path
> (`readRemoteBackup()`) keeps its existing exception contract because
> `SettingsViewModel.onDriveAuthGranted` and `RestoreBackupUseCase`
> already catch `DriveAuthRequiredException` / `IOException`; the
> `withRetry` helper still wraps it so transient blips on the read
> path retry too. Test surface: ten new JVM cases on
> `DriveBackupRepositoryTest` cover storage-full vs auth, retry-then-
> success on 429 / 500 / 403-quota / `UnknownHostException`,
> retry-exhaustion → `Failure("HTTP 429")` /
> `Failure("Network failure: …")`, no-retry on auth / storage-full,
> and the read path's 401-throws + 429-retries-then-succeeds.
> `FakeDriveRemoteSource` gains `failTimes: Int = 1` budget +
> `failuresRaised: Int` counter so retry tests drive multi-failure
> scenarios deterministically. JVM unit-test count: 265 → 275. All
> gates green: `:app:assembleRelease` (R8), `:app:lint`, `ktlintCheck`,
> `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`. The
> original task text is preserved below for historical context.

The class `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`
interfaces with the Google Drive API. It must handle common failure modes.

1. Audit the current implementation for missing error handling around:
   - Network failures (`IOException`, `UnknownHostException`).
   - Google API quota exceeded (HTTP 429).
   - Expired or revoked OAuth token (HTTP 401).
   - Drive storage full (HTTP 403 with specific error domain).

2. For transient errors (network, 429), implement exponential backoff retry
   with a maximum of 3 attempts using `kotlinx.coroutines` delay.

3. For auth errors (401), emit a specific sealed class result or error state
   so the UI can prompt the user to re-authenticate.

4. For non-recoverable errors, log the failure using `android.util.Log` at
   the `ERROR` level with tag `"DriveBackupRepository"`.

5. Expose a sealed class return type from the backup function if not already done:
   ```kotlin
   sealed class BackupResult {
       object Success : BackupResult()
       data class Failure(val reason: String) : BackupResult()
       object AuthRequired : BackupResult()
   }
   ```

6. Add or update the corresponding unit tests for these error paths in
   `app/src/test/`.

---

## 🟢 TASK-08 — Replace `CarEditDialog` with a Compose `AlertDialog`

> **Premise correction (2026-04-30):** `CarEditDialog` is **not** a
> `DialogFragment`. It is a Kotlin `object` wrapping
> `MaterialAlertDialogBuilder` over `DialogEditCarBinding` (see
> `app/src/main/java/org/spsl/evtracker/ui/cars/CarEditDialog.kt`). Compose is
> also **not** in the dependency graph today — no `androidx.compose.*`
> entries appear in `app/build.gradle.kts` or `gradle/libs.versions.toml`.
> The work below therefore has two parts: introducing Compose to the project,
> and porting the dialog. Treat introducing Compose as the gating decision —
> if the team prefers staying on Views, close this task and the dialog can
> stay as-is.

### Step 1 — decide whether to adopt Compose

Pulling Compose in for a single dialog is rarely worth it. Reasonable triggers
to actually adopt it: planned Compose-first new screens, the future
`PieChartView` work in TASK-30 benefits from Compose Canvas, or a desire to
phase out ViewBinding. If none of these apply, close this task.

### Step 2 — add Compose dependencies (only if Step 1 is "yes")

Add to `gradle/libs.versions.toml` and reference from `app/build.gradle.kts`:

- `androidx.compose:compose-bom` (use `platform(...)`)
- `androidx.compose.ui:ui`
- `androidx.compose.material3:material3`
- `androidx.activity:activity-compose`
- `androidx.fragment:fragment-compose` (or use `ComposeView` from a Fragment)

Enable `buildFeatures.compose = true` and configure `composeOptions`. Verify
release build still compiles (`./gradlew :app:assembleRelease`).

### Step 3 — port the dialog

1. Create a Composable `CarEditDialogCompose(state: CarFormState, onConfirm: (CarFormState) -> Unit, onDismiss: () -> Unit)` rendering a Material3 `AlertDialog` with the same fields as `R.layout.dialog_edit_car` (name, make, model, battery kWh).
2. Render it from `CarsFragment` via a `ComposeView` whose visibility is driven by `CarsViewModel` state (a `showDialog: CarFormState?` field plus an event).
3. Delete `CarEditDialog.kt` and `R.layout.dialog_edit_car` once unreferenced. Confirm `CarsFragment` and `CarsViewModel` still compile.
4. Add a UI test in `app/src/androidTest/.../CarsFragmentTest.kt` (or extend the existing one) asserting the dialog renders, validates blank names, and emits `onConfirm` with trimmed input.

---

## 🟢 TASK-09 — Add date-ranged CSV export for charge events with efficiency column

> **Premise correction (2026-04-30):** `EfficiencyPoint` is just a
> `(eventTimeMillis, kmPerKwh)` data class and does not carry distance,
> energy, or cost — so it cannot fill the originally proposed CSV header.
> An `ExportCsvUseCase` already exists and writes to
> `getExternalFilesDir(DIRECTORY_DOWNLOADS)`. The remaining work is a
> **date-ranged variant** of the existing exporter that also emits the
> per-row efficiency derived from the previous event for the same car.

1. Add a `ExportChargeEventsRangeUseCase` (or extend `ExportCsvUseCase` with
   a `DateRange?` parameter) that:
   - Accepts `startMillis: Long`, `endMillis: Long`, optional `carId: Long`.
   - Reads charge events through the existing `ChargeEventQueries` narrow IF
     (no new repo type).
   - Computes the same per-row delta-odometer efficiency the History screen
     uses (`kmPerKwh = (odo[i] - odo[i-1]) / kwh[i]`; first event for a car
     emits an empty efficiency cell).
   - Writes `event_date_iso,car_name,odometer_km,kwh,charge_type,location,cost_total,cost_per_kwh,currency,km_per_kwh,note` and shares via the existing FileProvider authority `${packageName}.fileprovider`.
2. Add an "Export range…" entry in Settings next to the existing CSV export
   that opens `MaterialDatePicker.Builder.dateRangePicker()` and calls the
   new use case via `SettingsViewModel`.
3. Add JVM unit tests for the new use case covering: empty range, single
   event (efficiency cell empty), multi-event efficiency derivation, mixed
   currency rows, and `costTotal IS NULL` rows. Use the existing fakes.

---

## 🟢 TASK-10 — Add In-App "About / Info" Screen ☑ Done (2026-05-01)

> **Outcome:** new `AboutFragment` at
> `app/src/main/java/org/spsl/evtracker/ui/about/AboutFragment.kt` with
> a CoordinatorLayout + MaterialToolbar + NestedScrollView body of
> Material 3 cards (app info, acknowledgment, license, disclaimer,
> open-source libraries). Reachable from `Settings → About` via
> `R.id.action_settings_to_about`; the destination declares
> `hideBottomNav=true` per the TASK-27 convention. Version text comes
> from `BuildConfig.VERSION_NAME` / `VERSION_CODE` (unblocked by
> TASK-29). Two tappable URL rows fire `Intent.ACTION_VIEW` and fall
> back to a Snackbar on `ActivityNotFoundException`. The bundled
> launcher icon pack drop-in: 20 PNG mipmaps (4 files × 5 density
> buckets) extracted from `exported-assets.zip` →
> `ev_tracker_icons.zip` into `app/src/main/res/mipmap-*/`;
> `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
> repointed from `@drawable/ic_launcher_{foreground,background}` to
> `@mipmap/...`; the now-orphan vector drawables in
> `drawable/ic_launcher_{foreground,background}.xml` deleted (lint
> `UnusedResources` is in error mode). `drawable/ic_spslab_badge.xml`
> kept and consumed by the About app-info card header. New
> instrumented `AboutFragmentTest` covers the five required assertions
> (version non-empty, SPS-Lab visible, sps-lab.org link present,
> license card contains "MIT", disclaimer contains "liability"); the
> test compiles via `:app:assembleDebugAndroidTest` (running needs an
> emulator). Spec:
> `superpowers/specs/2026-05-01-task10-about-screen-design.md`. Plan:
> `superpowers/plans/2026-05-01-task10-about-screen.md`. The original
> task text is preserved below for historical context.

> **Prerequisite resolved (2026-05-01):** TASK-29 has merged.
> `buildFeatures.buildConfig = true` is enabled, so
> `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE` are available
> under `org.spsl.evtracker.BuildConfig`. The About screen can read them
> directly. Note that on debug builds `VERSION_NAME` resolves to e.g.
> `"1.0.1-debug"` (from `versionNameSuffix = "-debug"`); decide whether
> the About screen should display that suffix verbatim or strip it.

Add a dedicated About screen accessible from the Settings or main navigation
that displays app metadata, acknowledgments, license, and a disclaimer.
This screen is important for SPS-Lab attribution and research transparency.

### Screen content requirements

The screen must display all of the following sections:

#### App Info
- App name: `EV Efficiency Tracker`
- Current version name and version code (read dynamically from
  `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`).
- Build date (optional; can be a hardcoded string updated at release time).

#### Acknowledgment
```
Developed by the Sustainable Power Systems Lab (SPS-Lab)
Cyprus University of Technology
Limassol, Cyprus
```
- Render `https://sps-lab.org` as a tappable hyperlink that opens in the
  default browser using an `Intent.ACTION_VIEW` intent.
- Render `https://cut.ac.cy` as a second tappable hyperlink.

#### License
```
MIT License

Copyright (c) 2024–2026 Sustainable Power Systems Lab (SPS-Lab),
Cyprus University of Technology.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
Display this in a scrollable `TextView` or Compose `Text` inside a card.

#### Disclaimer
```
This application is provided for research and personal use only.
Efficiency and cost estimates are based on user-entered data and do
not constitute professional energy or financial advice. The SPS-Lab
and Cyprus University of Technology accept no liability for decisions
made based on data recorded or displayed by this app.
```

#### Open Source Notices
- Add a section titled "Open Source Libraries" listing the key
  dependencies with their licenses:
  - Room (Apache 2.0)
  - Hilt / Dagger (Apache 2.0)
  - MPAndroidChart or equivalent charting library used (check `build.gradle.kts`)
  - Kotlin Coroutines (Apache 2.0)
  - Google Drive API client (Apache 2.0)

### Implementation instructions

1. Create a new Fragment (or Compose screen, if Compose is already in use):
   - Fragment: `app/src/main/java/org/spsl/evtracker/ui/about/AboutFragment.kt`
   - Layout: `app/src/main/res/layout/fragment_about.xml` (or Compose equivalent)

2. The screen must be reachable from the Settings screen via a menu item or
   preference row labelled `"About"`. Add a navigation entry in
   `app/src/main/res/navigation/` nav graph (if using Navigation Component).

3. All static text (app name, lab name, URLs, license, disclaimer) must be
   defined as string resources in `app/src/main/res/values/strings.xml`,
   not hardcoded in the layout or Kotlin files.

4. The SPS-Lab URL and CUT URL must open in the device browser. Use:
   ```kotlin
   val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
   startActivity(intent)
   ```
   Wrap in a try/catch for `ActivityNotFoundException`.

5. Style using Material 3 components consistent with the rest of the app
   (M3 card surfaces, `MaterialToolbar` with back navigation).

6. Add an instrumented UI test:
   `app/src/androidTest/java/org/spsl/evtracker/ui/about/AboutFragmentTest.kt`

   Tests required:
   - Version name is displayed and is non-empty.
   - "SPS-Lab" text is visible on screen.
   - The URL link for `sps-lab.org` is present.
   - The license card is visible and contains the word "MIT".
   - The disclaimer card is visible and contains the word "liability".

### Branding & launcher icon drop-in (assets provided 2026-05-01)

A complete launcher-icon asset pack is bundled at the repo root in
`exported-assets.zip` (uncommitted, ~5 MB). The inner
`ev_tracker_icons.zip` mirrors the exact `res/` directory structure to
drop into `app/src/main/res/`:

```
mipmap-mdpi/        → 48×48
mipmap-hdpi/        → 72×72
mipmap-xhdpi/       → 96×96
mipmap-xxhdpi/      → 144×144
mipmap-xxxhdpi/     → 192×192
play_store/         → 512×512
```

Each density folder contains **4 files**:

| File | Purpose |
|------|---------|
| `ic_launcher.png` | Legacy square launcher icon |
| `ic_launcher_round.png` | Circular variant (Android 7.1+) |
| `ic_launcher_foreground.png` | Adaptive icon foreground layer (RGBA) |
| `ic_launcher_background.png` | Adaptive icon background layer (solid navy `#0D2B5E`) |

#### How to apply

1. Unzip `exported-assets.zip` → extract the inner `ev_tracker_icons.zip` →
   copy all `mipmap-*` folders into `app/src/main/res/`, replacing existing
   ones. (Pre-flight check: a `find app/src/main/res -name "ic_launcher*"`
   currently lists only the vector drawables and the
   `mipmap-anydpi-v26/ic_launcher{,_round}.xml` files — there are no
   density-bucket PNGs to overwrite yet.)
2. `AndroidManifest.xml` already references `@mipmap/ic_launcher` and
   `@mipmap/ic_launcher_round` — no changes needed.
3. **Reconcile with the existing vector adaptive icon (commit `e1958d7`).**
   `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` currently reads:

   ```xml
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@drawable/ic_launcher_background"/>
       <foreground android:drawable="@drawable/ic_launcher_foreground"/>
   </adaptive-icon>
   ```

   To use the new PNG mipmaps as the adaptive-icon source, rewrite both
   `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` to
   reference `@mipmap/...` instead:

   ```xml
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@mipmap/ic_launcher_background"/>
       <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
   </adaptive-icon>
   ```

   The two vector drawables added by `e1958d7`
   (`drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`)
   will become orphans after this rewrite. Delete them — Android Lint's
   `UnusedResources` is in error mode and would otherwise fail the
   build. **Keep `drawable/ic_spslab_badge.xml`** — it's the SPS-Lab pill
   badge intended for the About screen header (not part of the launcher
   icon pipeline) and is referenced from the About fragment layout you
   write in this task.

4. Use `play_store/ic_launcher_512.png` when uploading to the Google Play
   Console (not committed to the repo — keep it under `dist/` locally or
   attach to the GitHub Release).

#### Acceptance for the icon work

- `find app/src/main/res -name "ic_launcher*.png"` lists 20 files
  (4 per density × 5 densities).
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` both
  reference `@mipmap/ic_launcher_foreground` + `@mipmap/ic_launcher_background`.
- `drawable/ic_launcher_foreground.xml` and
  `drawable/ic_launcher_background.xml` are deleted; the build remains
  green (`./gradlew :app:lint :app:assembleRelease`).
- The launcher icon visually shows the EV silhouette + lightning bolt
  on a navy `#0D2B5E` background after install — verify on a device
  and on the Play Store listing's icon preview.

---

## 🟡 TASK-11 — Odometer Regression UX Improvement ☑ Done (2026-05-01)

> **Outcome:** `ChargeEditUiState` gained four fields —
> `previousOdometerKm`, `nextOdometerKm`, `odometerBelowPrevious`,
> `odometerAboveNext` — populated by `ChargeEditViewModel.init` from a
> single `getAllForCarSorted(carId)` call (Create mode looks up the
> latest entry; Edit mode locates the chronological neighbours of the
> event being edited). Create mode pre-fills `odometer` with
> `(previousOdometerKm + 1.0).toDisplayUnit()` so the field starts in a
> known-valid state when the car has history. `setOdometer(text)` parses
> the input, converts miles → km when needed, and toggles the regression
> flags on every keystroke; the flags clear on blank/unparseable input.
> `ChargeEditFragment.render` formats the inline `TextInputLayout.error`
> from the new parameterised strings
> `error_odometer_must_be_greater_than` /
> `error_odometer_must_be_less_than` and gates `chargeEditSave.isEnabled`
> on `!odometerBelowPrevious && !odometerAboveNext` (in addition to the
> existing `!saving`). The use case still owns the
> `OdometerNotIncreasing` fallback for same-`eventDate` races.
> Ten new JVM cases in `ChargeEditViewModelTest` cover prefill (km +
> miles), neighbour wiring (middle/first/last), and flag transitions on
> `setOdometer`. JVM unit-test count: 247 → 257. Spec:
> `superpowers/specs/2026-05-01-task11-odometer-regression-ux-design.md`.
> Plan: `superpowers/plans/2026-05-01-task11-odometer-regression-ux.md`.
> The original task text is preserved below for historical context.


The app currently validates that each new charge event's odometer is greater
than the previous one, but the error experience can be improved.

**Current behavior:** a generic validation error is shown after the user
attempts to save.

**Required improvements:**

1. In `ChargeEditFragment` (or the corresponding ViewModel), pre-fill the
   odometer field with the **last recorded odometer value + 1** for the
   active car when creating a new charge event (not when editing an existing
   one). This gives the user a useful starting point.

2. Show an **inline warning** (not just on save) below the odometer field
   as soon as the entered value is less than or equal to the previous
   odometer. Use a `TextInputLayout` error message that updates on
   `TextWatcher` changes. The warning text must be:
   `"Must be greater than last entry ([previous value] [unit])"`
   where `[unit]` is `km` or `mi` per the user's preference.

3. The Save button must remain disabled while the odometer regression
   warning is shown.

4. When editing an existing event (not the most recent one), do not apply
   the regression check against the car's latest odometer. Instead, validate
   that the edited value is:
   - Greater than the event immediately before it in chronological order.
   - Less than the event immediately after it in chronological order.
   Show appropriate inline messages for each case.

5. Add unit tests to `ChargeEditViewModelTest` (or create it) for:
   - Pre-fill logic (new event vs. edit event).
   - Inline error message content and Save button enabled state.

---

## 🟡 TASK-12 — Home Screen Widget: Last Charge Summary

Add a `AppWidgetProvider`-based Android home screen widget that displays
a compact summary of the most recent charge event for the active car.

### Widget content

The widget (minimum 2×2 cells) must show:
- Car name
- Date of last charge (relative: "Today", "Yesterday", or "3 days ago")
- kWh added
- Efficiency value (in the user's preferred metric, e.g., `6.2 km/kWh`)
- Cost (if non-null, formatted with currency symbol)
- A small `⚡` icon indicating AC or DC charge type

If no charge events exist for the active car, show: `"No charges logged yet."`

### Implementation

1. Create `app/src/main/java/org/spsl/evtracker/widget/LastChargeWidget.kt`
   extending `AppWidgetProvider`.

2. Create the widget layout:
   `app/src/main/res/layout/widget_last_charge.xml`
   Use `RemoteViews`-compatible views only (`TextView`, `ImageView`, `LinearLayout`).

3. Create the widget metadata:
   `app/src/main/res/xml/widget_last_charge_info.xml`
   Set `minWidth="110dp"`, `minHeight="110dp"`, `updatePeriodMillis="0"` (use
   WorkManager or a broadcast instead of polling).

4. Register the widget in `AndroidManifest.xml` with
   `android.appwidget.action.APPWIDGET_UPDATE` intent filter.

5. Wire the widget update to the existing WorkManager backup job or a
   separate `OneTimeWorkRequest` triggered after every committed charge event
   save or delete. Use `AppWidgetManager.updateAppWidget()` to push new
   `RemoteViews` data.

6. Tapping the widget must open the app's Dashboard screen via a
   `PendingIntent`.

7. Add an instrumented test that verifies the `RemoteViews` are populated
   correctly with mock data.

---

## 🟢 TASK-13 — Live Charging Session Timer

Add an optional "I am charging now" mode that lets the user start a timed
charging session and auto-fills the timestamp and duration on the charge
edit form when they finish.

### Behavior

1. Add a **"Start charging session"** floating action button (or prominent
   button) on the Dashboard screen, visible only when no session is active.

2. When tapped, record `sessionStartTime = System.currentTimeMillis()` in
   a `StateFlow` inside `DashboardViewModel` (not persisted to DB; in-memory only).

3. While a session is active:
   - Show a persistent, non-dismissible notification: `"Charging in progress
     — [elapsed time]"`; update the elapsed time every minute using a
     `CoroutineScope` + `delay` loop in a `ForegroundService`.
   - Replace the Dashboard FAB with a **"Stop & Log"** button.
   - Show an elapsed time chip on the Dashboard (e.g., `⏱ 1h 23m`).

4. When the user taps **"Stop & Log"**:
   - Navigate to `ChargeEditFragment`.
   - Pre-fill the `event_date` with `sessionStartTime`.
   - Pre-fill a `note` field with `"Session duration: [elapsed]"`.
   - Stop the foreground service and clear the notification.

5. If the app is killed while a session is active, use `DataStore` to
   persist `sessionStartTime` so the session survives process death. On
   next launch, if `sessionStartTime` is non-null and the active car has
   not had a new charge event logged since that time, resume the active
   session display.

6. Add unit tests for session start/stop state transitions in
   `DashboardViewModelTest`.

---

## 🟡 TASK-14 — Battery Capacity Degradation Tracker ☑ Done (2026-05-01)

> **Outcome:** `ChargeEventEntity` gains optional `socBefore` and
> `socAfter` `Double?` fields stored as fractions in `0.0..1.0`. Room
> schema bumped v5 → v6 with `MIGRATION_5_6 = ALTER TABLE
> charge_events ADD COLUMN socBefore REAL; ADD COLUMN socAfter REAL`
> — purely additive, no rebuild. `BackupData.CURRENT_VERSION` 5 → 6
> with `BackupSerializer.SUPPORTED_VERSIONS = {3, 4, 5, 6}`; older
> backups simply leave the new fields at `null`. New
> `domain/service/CapacityEstimator` service computes per-event
> effective capacity as `kwhAdded / (socAfter - socBefore)` when both
> SoC fields are set (exact path), or `kwhAdded` itself when
> `kwhAdded ≥ 0.8 × nominalBatteryKwh` (heuristic fallback for
> near-full charges). Events that satisfy neither rule are skipped;
> the chart needs at least 3 qualifying points and a non-null nominal
> capacity to render. `Stats` gains `batteryHealthPercent: Double?`
> (latest effective capacity ÷ nominal × 100, **not clamped** —
> over-100% values surface heuristic over-estimation as diagnostic
> info). `ObserveDashboardStatsUseCase` and `ObserveChartsModelsUseCase`
> both inject `CapacityEstimator` and pass the active car's
> `batteryKwh`. `ChargeEditUiState` gains `socExpanded`,
> `socBeforeText`, `socAfterText`, `socError`; the form has a new
> "+ Add SoC data" expandable card with two paired `0..100` percent
> inputs. Validation: both blank → drop them; both filled, parseable,
> in-range, with `after > before` → save as fractions; otherwise
> show inline error from one of three new strings
> (`error_soc_both_required`, `error_soc_range`,
> `error_soc_after_must_exceed_before`). Dashboard renders a new
> `dashboard_card_battery_health` MaterialCardView showing
> "Battery health · NN%" when `Stats.batteryHealthPercent != null`.
> Charts adds a 6th tab `DEGRADATION` rendering an MPAndroidChart
> `LineChart` of `effectiveCapacityKwh` over time with a dashed
> `LimitLine` at the car's `nominalBatteryKwh`; falls back to two
> empty-state messages — "Set the car's nominal battery capacity in
> Cars to enable this chart" / "Need at least 3 qualifying charges".
> New JVM `CapacityEstimatorTest` (16 cases) covers exact path,
> heuristic boundary at 80%, mixed events sorted by date,
> zero/negative kWh skipped, `batteryHealthPercent` semantics, and
> the no-clamp guarantee. New instrumented
> `migrate_5_to_6_addsSocColumns` test; `migrate_1_to_5_validatesSchema`
> renamed to `migrate_1_to_6_validatesSchema` and asserts both new
> columns default to `NULL` on legacy rows. JVM unit-test count
> 275 → 291 (+16). All gates green: `:app:assembleDebug`,
> `:app:assembleRelease` (R8), `:app:assembleDebugAndroidTest`,
> `ktlintCheck`, `:app:lint`, `:app:testDebugUnitTest`.

The `cars` table already has a `battery_kwh` field for the nominal battery
capacity. Use it to track and visualise real-world effective capacity over
time, which is directly relevant to the SPS-Lab’s EV integration research.

### Feature description

An EV’s effective battery capacity can be estimated from charge events where:
- The car was charged from a known low state (e.g., cost-per-kWh data implies
  a near-full charge), OR
- The user manually enters the SoC (state of charge) before and after.

For a simpler first implementation, use the following heuristic:
- Identify charge events where `kwh_added ≥ 0.8 × battery_kwh` (likely a
  near-full charge from low SoC).
- Plot `kwh_added` for these events over time as a proxy for effective capacity.

### Implementation

1. Add two **optional** fields to the `charge_events` table via a Room
   migration (version bump to 4):
   - `soc_before REAL` — State of Charge before charging (0.0–1.0 or 0–100;
     store as a fraction 0.0–1.0 internally).
   - `soc_after REAL` — State of Charge after charging.

2. Add optional SoC input fields to `ChargeEditFragment`:
   - Two optional fields labelled "SoC before (%)" and "SoC after (%)".
   - Collapsed by default behind a "+ Add SoC data" expansion tap.
   - Validate: 0–100 range; `soc_after > soc_before`.

3. Add a new "Degradation" tab or card in the Charts screen:
   - X-axis: date of charge event.
   - Y-axis: effective capacity (kWh), computed as
     `kwh_added / (soc_after - soc_before)` when both SoC fields are
     non-null, otherwise fall back to the heuristic `kwh_added` proxy.
   - Draw a horizontal dashed reference line at the car’s nominal
     `battery_kwh` value.
   - Only show this chart if the car has a `battery_kwh` set and at
     least 3 qualifying data points exist.

4. Add a `batteryHealthPercent` computed property:
   `(latestEffectiveCapacity / nominalCapacity) × 100`
   Display this as a secondary stat card on the Dashboard when available.

5. Add unit tests for the capacity calculation logic in a new
   `CapacityEstimatorTest.kt` in `app/src/test/`.

---

## 🟢 TASK-15 — Localisation (i18n) Foundation

The app currently has all user-facing strings hardcoded in English. Add
proper i18n support so the app can be translated in the future.

1. Audit all Kotlin source files and XML layouts for hardcoded user-facing
   strings. Extract every string to
   `app/src/main/res/values/strings.xml`.
   Do **not** extract:
   - Log tag strings (internal developer-facing)
   - Room entity column names
   - DataStore preference key names

2. Ensure all `String.format()` calls use named or positional format
   arguments compatible with `getString(R.string.x, arg)` so translators
   can reorder arguments.

3. Add plurals resources (`<plurals>`) for any strings that vary by count
   (e.g., "1 charge event" vs. "3 charge events").

4. **English is the default locale** — `app/src/main/res/values/strings.xml`
   stays as-is. Add three target translations:
   - Greek — `app/src/main/res/values-el/strings.xml`
   - Turkish — `app/src/main/res/values-tr/strings.xml`
   - Russian — `app/src/main/res/values-ru/strings.xml`

   Translate at minimum: all navigation labels, screen titles, button
   labels, empty state messages, and error messages. Domain-specific
   technical terms (kWh, km/kWh, AC, DC) should remain in their
   internationally recognised forms.

   `MissingTranslation` is already wired in error mode (TASK-16) but
   cannot fire today because no `values-<lang>/` resources exist. The
   moment the first translated `strings.xml` lands, **every English
   string must have a translation in all three locales** or the build
   breaks. Plan the extraction (step 1) and the three translations
   together to avoid landing a half-localised state.

5. Add a lint rule to `app/build.gradle.kts` to fail the build on
   hardcoded strings in layouts:
   ```kotlin
   android {
       lint {
           error += "HardcodedText"
       }
   }
   ```

6. Verify the app renders correctly at the two most common system font
   scales (100% and 150%) after the string extraction, as translated
   strings are often longer than English equivalents.

---

## 🟢 TASK-16 — Static analysis & code-style gate in CI

> **Merged 2026-04-30 on `main`.** Implementation: `.github/workflows/ci.yml`
> (PR + push-to-main triggers), ktlint 12.1.1 plugin in `app/build.gradle.kts`,
> Android Lint error mode for `HardcodedText` / `MissingTranslation` /
> `TypographyDashes` / `UnusedResources`, `app/lint-baseline.xml` absorbs 58
> pre-existing offenses. `.editorconfig` pins Kotlin official / IntelliJ style.
> Local one-liner: `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest`.
> Spec: `docs/superpowers/specs/2026-04-30-task16-ci-static-analysis-design.md`.

> **Priority raised to 🔴 (2026-04-30):** without this gate, regressions on
> hardcoded strings (TASK-15), API-35 deprecations (TASK-22), and
> `ChargeType` enum/conversion (TASK-25) cannot be caught at PR time. Land
> this before the next round of feature work.

The only CI workflow today is `.github/workflows/release.yml`, which triggers
**only on `v*` tag pushes** and `workflow_dispatch`. It runs `:app:assembleRelease`,
verifies signing, and publishes the APK — but no linter, no style checker, and
no static analysis ever runs against PRs or `main`. With ~176 Kotlin files in
`app/src`, style drift will accumulate silently and hardcoded-string regressions
(needed by TASK-15) cannot be detected before merge.

**What to add:**

1. Create a **new** workflow file `.github/workflows/ci.yml` (do not edit
   `release.yml`) that triggers on `pull_request` and `push: branches: [main]`.

2. Add **ktlint** via the `org.jlleitschuh.gradle.ktlint` plugin to the root
   `build.gradle.kts` (or `app/build.gradle.kts`). Configure it to use the
   project's existing 4-space indentation and Kotlin official code style.

3. Enable **Android Lint** in error mode for the rules most likely to bite
   this project, in `app/build.gradle.kts`:

   ```kotlin
   android {
       lint {
           abortOnError = true
           checkReleaseBuilds = true
           error += listOf("HardcodedText", "MissingTranslation",
                           "TypographyDashes", "UnusedResources")
           // Once TASK-15 (i18n) lands, also flip "HardcodedText" + "MissingTranslation"
           // from optional to required — currently they should error already, since
           // there are no localised strings yet to miss.
       }
   }
   ```

4. The new `ci.yml` job runs `./gradlew ktlintCheck lint` on Java 17 with the
   gradle action cache. Fail the build on any violation.

5. Generate a `lint-baseline.xml` for existing pre-existing offenses by running
   `./gradlew lint --baseline lint-baseline.xml` once locally; commit the
   baseline so only **new** violations break the build.

6. Document the new gate in `../CLAUDE.md` (Build & Test section): "PRs must
   pass `:app:ktlintCheck :app:lint` before merge."

---

## 🟡 TASK-17 — R8/ProGuard follow-up audit: chart library + release smoke test ☑ Done (2026-05-01)

> **Outcome:** `app/proguard-rules.pro` gains a defensive
> `-keep class com.github.mikephil.charting.** { *; }` + matching
> `-dontwarn` (MPAndroidChart ships zero consumer ProGuard rules,
> verified by `grep mikephil app/build/outputs/mapping/release/configuration.txt`
> returning empty before the change and three lines after). The same
> file gains an evidence comment naming the three AAR-bundled rule
> files that already cover Hilt and Room
> (`hilt-android-2.50/proguard.txt`, `hilt-work-1.1.0/proguard.txt`,
> `room-runtime-2.6.1/proguard.txt`) so future contributors don't add
> redundant `-keep dagger.hilt.*` / `-keep androidx.room.*` rules.
> `:app:assembleRelease` runs R8 cleanly with the new rules and emits
> the signed `app-release.apk` (verified locally). The end-to-end
> manual smoke matrix (12 steps: wizard → log charge → all five Charts
> tabs → Drive backup landing in App Data folder → reset preferences
> auto-recovery → CSV export → About) is documented as
> `docs/TEST_PLAN.md §5b "Release-APK smoke test"` and is the gate
> between "tag pushed → APK built" and "GitHub Release published".
> The smoke matrix itself requires a physical device or API-26+
> emulator with an allow-listed Google account, so the act of running
> it stays a release-time human task — but CI now produces the APK
> deterministically and the matrix names every observable that R8 can
> realistically break. The original task text is preserved below.

The release build's R8 rules (`app/proguard-rules.pro`) were partially audited
in commit `48f0f14` (v1.0.1) for the Drive sync path: keep rules now cover
`com.google.api.client.**`, `com.google.api.services.drive.**`, Gson
reflection, and the backup DTOs (`BackupData`, `CarDto`, `ChargeEventDto`,
`CustomLocationDto`). What's **not** yet covered or verified:

1. **MPAndroidChart** — add a defensive keep rule even though MPAndroidChart's
   release surface is mostly non-reflective; some renderers and
   `IValueFormatter` subclasses can still bite under aggressive R8:
   ```
   -keep class com.github.mikephil.charting.** { *; }
   -dontwarn com.github.mikephil.charting.**
   ```

2. **Hilt + Room** — both ship comprehensive consumer ProGuard rules with
   their AARs (verify by running `./gradlew :app:assembleRelease` and
   inspecting `app/build/outputs/mapping/release/configuration.txt`). Add a
   short comment to `proguard-rules.pro` stating that no app-side keep rules
   are needed for these libraries, with the AAR rule paths as evidence — this
   prevents a future contributor from adding redundant rules "just in case".

3. **End-to-end release-APK smoke test** (this is the bulk of the work):
   - Build a signed release APK locally: `./gradlew :app:assembleRelease`.
   - Install on a physical device or API-34 emulator: `adb install -r app/build/outputs/apk/release/app-release.apk`.
   - Walk the smoke matrix:
     a. Wizard completes successfully (writes `setupComplete=true`).
     b. Add a car, add a charge event with cost; verify Dashboard stats render.
     c. Open Charts; verify all six tabs (Trend / Monthly kWh / Monthly cost / AC vs DC / Locations / Degradation) render without crash.
     d. Settings → enable Drive backup; sign in; trigger a backup; verify `evtracker_backup.json` lands in the App Data folder via `files.list?spaces=appDataFolder`.
     e. Reset preferences; verify wizard re-routes.
     f. CSV export from Settings; verify the share sheet opens.
   - File a follow-up issue for any crash or rendering bug discovered.

4. Document the smoke test as a manual checklist in `TEST_PLAN.md` (new
   section "Release-APK smoke test") so future tag pushes can be verified
   against it before publishing the GitHub Release.

> **Note on priority:** the original proposal flagged this 🔴 on the premise
> that R8 rules had never been audited. Since the Drive/Gson path is already
> covered as of v1.0.1, the residual risk is bounded — hence 🟡, not 🔴.

---

## 🟡 TASK-18 — Accessibility (a11y) pass

There is no mention of accessibility in `DESIGN.md`, `../CLAUDE.md`, or
`TEST_PLAN.md`. The app is intended for public use and must meet at least
WCAG 2.1 AA.

**Required changes:**

1. Add `contentDescription` (or `android:contentDescription` in XML) to every
   `ImageView`, icon button, AC/DC toggle, and chart view. MPAndroidChart
   charts are entirely invisible to TalkBack without an explicit description
   on their host `View` — supply a short summary of what the chart shows.

2. Audit interactive elements for a minimum touch target of 48×48dp. Likely
   offenders: the location chips on `ChargeEditFragment`, the small AC/DC
   badge, and the History row delete button.

3. Mark purely decorative views (`View` dividers, background shapes, brand
   ornaments) with `android:importantForAccessibility="no"` so TalkBack
   skips them.

4. For every `TextInputLayout` field that has a separate companion label or
   helper text in another view, set `android:labelFor` so the label gets
   announced.

5. Verify the charge-type `MaterialButtonToggleGroup` announces state
   changes (e.g., "AC selected", "DC selected"). Add explicit
   `contentDescription` strings on each child `MaterialButton` if needed.

6. Add `AccessibilityChecks.enable()` in the Espresso test setup
   (`app/src/androidTest/.../TestApplication.kt` or a base test class). This
   automatically fails any instrumented test that interacts with a view
   missing required a11y metadata.

7. Run a contrast audit on both light and dark M3 themes. Pay special
   attention to the `#FB8C00` DC orange tertiary token (DESIGN §6) and any
   white-on-tertiary text — verify ≥ 4.5:1 against the surface it's drawn
   on. Tools: Material Theme Builder's contrast checker or the WebAIM
   contrast checker.

8. Add a section "A11y" to `DESIGN.md` documenting the WCAG 2.1 AA target
   and the smoke checklist (TalkBack walkthrough of the wizard,
   add-event flow, and Settings).

---

## 🟡 TASK-19 — Backup failure notification channel ☑ Done (2026-05-01)

> **Outcome:** two NotificationChannels registered idempotently from
> `EVTrackerApp.onCreate` via
> `AndroidBackupNotifier.ensureChannels(this)` — `backup_status`
> (IMPORTANCE_LOW, sticky chronic-failure card) and `backup_auth`
> (IMPORTANCE_DEFAULT, auth-required card). New `BackupNotifier`
> domain interface (`notifyChronicFailure` / `notifyAuthRequired` /
> `clearAll`) implemented by `AndroidBackupNotifier`; the
> `safeNotify` wrapper gates each `notify()` on
> `NotificationManagerCompat.areNotificationsEnabled()` so the call
> sites are silently no-op when permission is missing (which also
> satisfies lint's `MissingPermission` rule). `BackupOutcomeReporter`
> in `domain/notification/` owns the increment-on-failure /
> reset-on-success / threshold-3 logic so it stays JVM-testable;
> `DriveBackupWorker` is a thin caller that forwards each `BackupResult`
> through it before translating to WorkManager. Tap-targets use
> `NavDeepLinkBuilder` to land in `settingsFragment`. New DataStore
> keys `consecutiveBackupFailures` (Int, reset to 0 on success) and
> `notificationPermissionDenied` (Boolean, sticky once true);
> surfaced through `SettingsReader`/`SettingsWriter`. `MainActivity`
> launches a `MaterialAlertDialog` rationale + system permission
> request only when the chronic-failure threshold is crossed AND
> `POST_NOTIFICATIONS` isn't granted AND the user hasn't previously
> denied AND `Build.VERSION.SDK_INT >= TIRAMISU` (Android 13). Denial
> via the rationale, the system prompt, or a back-press all set the
> sticky `notificationPermissionDenied` flag; we never re-prompt.
> `Manifest.uses-permission` for `POST_NOTIFICATIONS` declared.
> 8 new JVM cases on `BackupOutcomeReporterTest` covering success
> reset, increment-without-fire below threshold, fire-at-threshold,
> sticky firing past threshold, AuthRequired increments without
> chronic, success-after-streak, and AuthRequired-at-threshold (still
> doesn't fire chronic). `Fakes.kt` extended with
> `FakeBackupNotifier` and the two new keys on `FakeSettingsReader` /
> `FakeSettingsWriter`. `NotificationModule` (separate from
> `BackupModule`) holds the `BackupNotifier` binding so the existing
> `DriveBackupWorkerTest` `@UninstallModules(BackupModule::class)`
> doesn't lose the notifier. JVM unit-test count 291 → 299. The
> original task text is preserved below for historical context.

> **TASK-07 has landed.** Step §4 below consumes the
> `BackupResult.AuthRequired` sealed-class variant directly — the
> stable error model exists in `domain/backup/BackupResult.kt`.

Drive auto-backup runs via WorkManager (`enqueueUniqueWork("drive_backup", REPLACE, ...)`).
On failure (network down, OAuth revoked, quota exceeded), the only signal is
the "Last backup: …" timestamp in `SettingsFragment` — which most users will
never look at. There is currently **no notification code anywhere in
`app/src/main/java`**.

**What to implement:**

1. Register a `NotificationChannel` (`id = "backup_status"`,
   `IMPORTANCE_LOW`) inside `EvTrackerApplication.onCreate()` (the
   `@HiltAndroidApp` class). Use `NotificationManagerCompat.from(this).createNotificationChannel(...)`.

2. Track consecutive failures in DataStore (new key
   `consecutiveBackupFailures: Int`, declared in `PreferenceKeys`). Increment
   on each `Result.failure()` from the backup worker; reset to 0 on
   `Result.success()`.

3. After 3 consecutive failures, post a low-priority sticky notification
   (`"Drive backup failed. Tap to open Settings."`) with a `PendingIntent`
   that opens `MainActivity` and navigates to `SettingsFragment`. Cancel the
   notification on the next success.

4. For HTTP-401 (auth revoked / token expired — see TASK-07's sealed-class
   error model), post a higher-importance notification (`IMPORTANCE_DEFAULT`):
   `"Drive sign-in required — tap to reconnect."` Tapping deep-links to the
   "Sign in to Drive" affordance in Settings.

5. Handle Android 13+ `POST_NOTIFICATIONS` runtime permission. Request the
   permission **only after the first user-visible failure** (not on app
   launch), with a short rationale dialog explaining that notifications are
   used for backup status only. If denied, do nothing — never re-prompt.

6. Add a JVM unit test class
   `app/src/test/.../BackupNotificationManagerTest.kt` covering the failure
   counter (increment-on-failure, reset-on-success, 3-strike threshold). Use
   the existing fakes (`FakeBackupRepository`, `FakeSettingsWriter`).

7. Document the new channel and permission in `DESIGN.md` (new subsection
   under Drive backup) and in `GOOGLE_CLOUD_SETUP.md` (auth-failure UX
   walkthrough for testers).

---

## 🟢 TASK-20 — CO₂ savings tracker

This is the most research-aligned addition for SPS-Lab. The app already tracks
kWh consumed and distance driven; it never contextualises the environmental
impact against an ICE-vehicle baseline. This ties directly to the lab's grid
decarbonisation and renewable-integration work, and the Cyprus grid intensity
default makes the result locally meaningful.

**Implementation:**

1. Add two preferences to Settings (declared in `PreferenceKeys`):

   - `iceBaselineLPer100km: Float` — preset choices presented as a dropdown:
     - Small petrol car: 6.0 L/100km
     - Average petrol car: 8.0 L/100km
     - Large petrol / SUV: 11.0 L/100km
     - Custom (user-entered, validate 1.0 ≤ x ≤ 20.0)

   - `gridIntensityGCO2PerKwh: Float` — preset choices:
     - Cyprus grid: 600 gCO₂/kWh **(default — relevant for CUT context)**
     - EU average: 250 gCO₂/kWh
     - Renewable-heavy (Norway-style): 20 gCO₂/kWh
     - Custom (validate 0 ≤ x ≤ 1500)

2. Add a pure service `app/src/main/java/org/spsl/evtracker/domain/service/CO2Calculator.kt`:

   ```kotlin
   class CO2Calculator @Inject constructor() {
       /** All inputs SI; returns kg CO₂. */
       fun iceCo2Kg(distanceKm: Double, lPer100km: Double): Double =
           distanceKm / 100.0 * lPer100km * 2.31  // 2.31 kg CO₂ per L petrol (EPA)

       fun evCo2Kg(energyKwh: Double, gridGCo2PerKwh: Double): Double =
           energyKwh * gridGCo2PerKwh / 1000.0

       fun savedCo2Kg(distanceKm: Double, energyKwh: Double,
                      lPer100km: Double, gridGCo2PerKwh: Double): Double =
           iceCo2Kg(distanceKm, lPer100km) - evCo2Kg(energyKwh, gridGCo2PerKwh)
   }
   ```

3. Add an "Environmental Impact" card on the Dashboard (below the cost
   cards) showing:
   - CO₂ saved vs. ICE baseline for the selected period (kg, switching to
     tonnes when ≥ 1000 kg).
   - "Equivalent to driving X km in a petrol car" line.
   - **Hide** the card if the user has not yet configured `iceBaselineLPer100km`.

4. Add a "CO₂ Impact" bar chart in the Charts screen: monthly CO₂ savings
   stacked above the ICE-equivalent emissions, on a shared kgCO₂ axis.

5. Add `app/src/test/.../CO2CalculatorTest.kt` covering boundaries (zero
   distance → zero ICE CO₂, zero grid intensity → zero EV CO₂, Cyprus
   default with realistic monthly numbers).

6. Add a `METHODOLOGY.md` at the repo root documenting:
   - The 2.31 kg/L petrol factor (cite EPA / IPCC source).
   - The 600 gCO₂/kWh Cyprus default (cite TSOC or IEA data).
   - The grid intensity ranges for the EU / renewable presets.
   - Caveats: no embodied emissions, no upstream fuel-cycle CO₂ for petrol.

   This is essential for research transparency and any future publication
   that references the app's data.

7. Update `DESIGN.md §7` (formulas table) to include the CO₂ formulas, and
   reference `METHODOLOGY.md`.

---

## 🟢 TASK-21 — Android Baseline Profile for cold-start performance

App cold start currently traverses Room init + Hilt graph build + DataStore
reads + the wizard gate, all in the main-thread path. A Baseline Profile
pre-compiles the critical paths into AOT machine code on install, reducing
cold-start latency on user devices.

**Implementation:**

1. Add `androidx.profileinstaller:profileinstaller` to `app/build.gradle.kts`
   dependencies (use the version catalog).

2. Create a new Gradle module `baselineprofile/` (sibling of `app/`):
   - Apply `com.android.test` plugin and `androidx.baselineprofile` plugin.
   - Depend on `androidx.benchmark:benchmark-macro-junit4` and
     `androidx.test.uiautomator:uiautomator`.
   - Wire `targetProjectPath = ":app"` and `experimentalProperties["android.experimental.self-instrumenting"] = true`.

3. Write `BaselineProfileGenerator` covering the hot startup paths:
   - Cold start to **Dashboard** (wizard already completed) — the most
     common everyday case.
   - Cold start to **Wizard** (first launch) — important for first-impression
     latency.
   - Open `ChargeEditFragment` from the Dashboard FAB.
   - Open `ChartsFragment` (this triggers MPAndroidChart class loading,
     which is otherwise lazy and very expensive on first chart render).

4. Generate the profile on a connected device or managed AVD:
   `./gradlew :baselineprofile:generateBaselineProfile`. Commit the
   resulting `app/src/main/baseline-prof.txt`.

5. Add `StartupBenchmark.kt` measuring `measureRepeated { startActivityAndWait() }`
   with `CompilationMode.None()` vs `CompilationMode.Partial(BaselineProfileMode.Require)`.
   Document the cold-start delta in the README.

6. Wire profile generation into CI as a `workflow_dispatch`-only job in
   `.github/workflows/baselineprofile.yml` (do **not** add to PR/main runs —
   the job needs a managed AVD and is too slow). Refresh the committed
   profile manually before each tagged release.

7. Document in `../CLAUDE.md` the cadence: "Regenerate baseline profile when:
   adding/removing a major dependency, restructuring startup, or before
   each `v*` tag."

---

## 🔴 TASK-22 — Upgrade `targetSdk` and `compileSdk` to API 35 ☑ Done (2026-05-01)

> **Outcome:** `compileSdk` and `targetSdk` bumped to 35 in
> `app/build.gradle.kts`. AGP 8.2.0 + Gradle 8.4 accept the new SDK with
> no toolchain bump and no warnings (verified with
> `./gradlew :app:assembleDebug` — `android-35` is auto-downloaded under
> `$ANDROID_HOME/platforms/`). `MainActivity.onCreate` now calls
> `enableEdgeToEdge()` and applies `WindowInsetsCompat.Type.systemBars()
> or displayCutout()` as padding to the root `LinearLayout`, so the
> bottom nav and CoordinatorLayout Snackbars stay above the gesture-nav
> indicator on Android 15+. Lint baseline is unchanged (no new API-35
> issues surface). Step 6 in the original task body — bumping a CI
> `api-level` matrix — was moot: `.github/workflows/ci.yml` is
> static-analysis only and has no instrumented-test matrix to bump.
> Spec: `superpowers/specs/2026-05-01-task22-sdk35-upgrade-design.md`.
> Plan: `superpowers/plans/2026-05-01-task22-sdk35-upgrade.md`.
> The original task text is preserved below for historical context.

`app/build.gradle.kts:18,23` currently pin `compileSdk = 34` / `targetSdk = 34`
(Android 14). As of early 2026, Google Play requires `targetSdk ≥ 35` for new
submissions and updates; staying on 34 will block Play Store publishing.

1. In `app/build.gradle.kts`, change both:
   ```kotlin
   compileSdk = 35
   // inside defaultConfig:
   targetSdk = 35
   ```
2. If/when `gradle/libs.versions.toml` gains version aliases for these,
   keep them in sync.
3. Rebuild with `./gradlew :app:assembleDebug` and resolve API-35 deprecations.
   Pay particular attention to:
   - **Edge-to-edge by default** on API 35: `WindowInsets` handling in
     `MainActivity` and any Fragment that sets window flags.
   - `PendingIntent.FLAG_IMMUTABLE` — required since API 31 but newer Lint flags
     it more loudly.
   - Photo / media picker changes that landed in API 35 (relevant if a future
     task adds an image-picker for charge-event notes).
4. Run `./gradlew lint` and address new API-35 Lint errors.
5. Run the full instrumented suite on an API-35 emulator:
   `./gradlew connectedAndroidTest`.
6. Once TASK-16 (CI lint gate) is merged, bump the `api-level` in the matrix
   in `.github/workflows/ci.yml` from 34 to 35 so PRs are tested on the new
   target.

---

## 🔴 TASK-23 — Move startup `isLoading` state into `MainViewModel` ☑ Done (2026-04-30)

> **Outcome:** `MainViewModel` now owns the startup auto-recovery state.
> `MainActivity` is a thin presenter that observes `startupState` via
> `repeatOnLifecycle(STARTED)` and routes to `mountNavGraph(setupComplete)` or
> `showRecoveryFailureDialog(cause)`. `SettingsReader` gained
> `setupComplete: Flow<Boolean>` so the new VM consumes the narrow IF (one of
> the three TASK-24 violations resolved as a side effect; `EVTrackerApp` and
> `WizardViewModel` remain for TASK-24). Six new JVM tests in
> `MainViewModelTest`. Spec:
> `superpowers/specs/2026-04-30-task23-main-viewmodel-design.md`. Plan:
> `superpowers/plans/2026-04-30-task23-main-viewmodel.md`.
> The original task text is preserved below for historical context.

`MainActivity.kt:29` declares `private val isLoading = MutableStateFlow(true)`
directly inside the Activity, and `startupSequence()` (line 60) plus the two
`@Inject` fields (`settingsRepository`, `resetAllDataUseCase`, lines 26–27)
also live in the Activity. On every configuration change (rotation, locale,
dark/light mode) the Activity is recreated, `isLoading` resets to `true`, and
`startupSequence()` re-runs. This can trigger duplicate auto-recovery attempts
and produces a visible splash flicker on rotation during startup.

1. Create `app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt`:
   ```kotlin
   @HiltViewModel
   class MainViewModel @Inject constructor(
       private val settingsRepository: SettingsRepository,
       private val resetAllDataUseCase: ResetAllDataUseCase,
   ) : ViewModel() {

       sealed class StartupState {
           data object Loading : StartupState()
           data class Ready(val setupComplete: Boolean) : StartupState()
           data class RecoveryFailed(val cause: Throwable?) : StartupState()
       }

       private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
       val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

       init { runStartupSequence() }

       fun runStartupSequence() { /* move logic from MainActivity here */ }
   }
   ```
   (Once TASK-24 lands, swap the constructor to `SettingsReader`.)
2. In `MainActivity`, inject `MainViewModel` via `viewModels()` and remove the
   `isLoading` field, `startupSequence()`, and the two `@Inject` fields.
3. Observe `mainViewModel.startupState` in `onCreate` using
   `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`:
   - `Loading` → keep splash on screen (`setKeepOnScreenCondition { true }`).
   - `Ready(setupComplete)` → call `mountNavGraph(setupComplete)`; dismiss splash.
   - `RecoveryFailed(cause)` → call `showRecoveryFailureDialog(cause)`; dismiss splash.
4. Recovery-failure dialog's retry button must call
   `mainViewModel.runStartupSequence()`, not re-launch a coroutine in the Activity.
5. Update `isNavGraphMounted()` (`@VisibleForTesting`) to check
   `mainViewModel.startupState.value is StartupState.Ready` instead of the
   removed `isLoading` field.
6. Add `app/src/test/.../MainViewModelTest.kt` covering:
   - `resetInProgress = false` path → emits `Ready` immediately.
   - `resetInProgress = true`, success path → emits `Loading` then `Ready`.
   - `resetInProgress = true`, failure path → emits `Loading` then `RecoveryFailed`.
   Use `kotlinx-coroutines-test` `UnconfinedTestDispatcher` and existing fakes.

---

## 🔴 TASK-24 — Enforce ViewModel/Activity consumption of the existing narrow domain interfaces ☑ Done (2026-05-01)

> **Outcome:** the two remaining concrete-repository imports are gone.
> `EVTrackerApp` now `@Inject`s `SettingsReader` (for the launch-time theme
> read); `WizardViewModel` now `@Inject`s `SettingsWriter` (for the atomic
> wizard finish). `SettingsWriter` gained
> `suspend fun completeSetup(metric, unit, currency)` so the wizard's
> 4-key atomic write is preserved as part of the narrow interface, not
> the concrete class. The audit
> `grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"`
> now returns empty. CLAUDE.md Architecture section codifies the rule.
> Spec: `superpowers/specs/2026-05-01-task24-narrow-interface-enforcement-design.md`.
> Plan: `superpowers/plans/2026-05-01-task24-narrow-interface-enforcement.md`.
> The original task text is preserved below for historical context.

> **Note on premise.** The original framing was "introduce repository interfaces
> to break the data → domain dependency." Narrow IFs **already exist** in
> `domain/repository/`: `CarReader`, `CarWriter`, `ChargeEventQueries`,
> `ChargeEventWriter`, `LocationReader`, `LocationWriter`, `SettingsReader`,
> `SettingsWriter`, and `DataResetTransactionRunner`. The remaining work is
> narrower: **enforce** that consumers depend on those IFs and never on the
> concrete `data.repository.*` classes (with `di/` modules being the sole
> exception, where bindings legitimately reference the implementations).

A `grep -rn "data\.repository" app/src/main/java | grep import` shows three
violations on `main`:

- `EVTrackerApp.kt` imports `data.repository.SettingsRepository`.
- `MainActivity.kt` imports and `@Inject`s `data.repository.SettingsRepository`.
- `WizardViewModel.kt` imports `data.repository.SettingsRepository`.

(Plus expected references inside `di/DomainModule.kt` for the bindings — those
are correct and should stay.)

1. Audit and replace each violation:
   - `MainActivity` and `WizardViewModel` should depend on `SettingsReader` (and
     `SettingsWriter` only if they actually mutate settings — for the wizard,
     yes; for `MainActivity` startup gate, only `SettingsReader`).
   - `EVTrackerApp` (Hilt application class): if the import is just for a
     downstream binding, remove it; otherwise replace with the narrow IF.
2. Confirm `di/DomainModule.kt` already binds the narrow IFs to their concrete
   classes; if any IF is missing a `@Binds`, add it.
3. After the refactor, the only files matching
   `import org\.spsl\.evtracker\.data\.repository\.` should live under `di/`.
   Add a Lint or ktlint custom rule (TASK-16 follow-up) once that infrastructure
   exists.
4. Run `./gradlew test connectedAndroidTest` and verify all green.
5. Update `../CLAUDE.md` (Architecture section) to make the rule explicit:
   "ViewModels, Activities, and use cases depend only on `domain/repository/*`
   interfaces. Concrete implementations live in `data/repository/*` and are
   wired by Hilt in `di/`."
6. **Sequencing:** TASK-23 must land before TASK-24, because `MainViewModel`
   becomes the new `SettingsReader` consumer that the audit verifies.

---

## 🟡 TASK-25 — Replace `chargeType: String` with a TypeConverter-backed enum ☑ Done (2026-05-01)

> **Outcome:** new `core/model/ChargeType` enum (`AC`, `DC_FAST`,
> `DC_ULTRA`) with `isDc`, `displayLabel()`, and `parseLegacy(s)`.
> Stored on disk via `data/local/db/ChargeTypeConverter` (Room
> `@TypeConverter`); on the backup wire via
> `domain/service/ChargeTypeJsonAdapter` (Gson). `ChargeEventEntity`,
> `ChargeEditUiState`, `SaveChargeEventInput`, and
> `BackupData.ChargeEventDto` all flip to `ChargeType`. AppDatabase
> bumped from `version = 3` to `version = 4`; `MIGRATION_3_4` rewrites
> legacy `'DC'` cells to `'DC_FAST'`. `BackupData.CURRENT_VERSION` →
> 4 with `BackupSerializer.fromJson` accepting `{3, 4}` so backups in
> the wild still restore (legacy `"DC"` decoded to `DC_FAST` via the
> Gson adapter). `StatsCalculator`, `ObserveDashboardStatsUseCase`,
> `HistoryViewModel` filter via `ChargeType.AC` / `ChargeType.isDc`;
> `ChargeEditFragment` toggle buttons emit `ChargeType.AC` /
> `ChargeType.DC_FAST`; `HistoryAdapter` badge uses
> `chargeType.displayLabel()`. `ExportCsvUseCase` writes the enum
> `name`. New JVM tests cover the enum (3 cases), the converter (3
> cases), and the v3-backup compat path on the serializer (2 cases);
> ~74 stringly-typed `"AC"` / `"DC"` literals across 19 test files
> were flipped to enum literals. Instrumented `MigrationTest` gains
> `migrate_3_to_4_rewritesLegacyDcRows` and the existing
> `migrate_1_to_3_validatesSchema` becomes `migrate_1_to_4_validatesSchema`
> with `chargeType` asserted as `ChargeType.AC`. JVM unit-test count:
> 257 → 265. Spec:
> `superpowers/specs/2026-05-01-task25-charge-type-enum-design.md`.
> Plan: `superpowers/plans/2026-05-01-task25-charge-type-enum.md`.
> The original task text is preserved below for historical context.


`ChargeEventEntity.kt:30` has `val chargeType: String = "AC"`. Nothing prevents
the values `"ac"`, `"DC"`, `"dc_fast"`, or any arbitrary string from being
written, breaking filter queries and chart groupings silently.

1. Add a Kotlin enum to the core model layer:
   ```kotlin
   // app/src/main/java/org/spsl/evtracker/core/model/ChargeType.kt
   enum class ChargeType { AC, DC_FAST, DC_ULTRA }
   ```
2. Create a Room `TypeConverter`:
   ```kotlin
   // app/src/main/java/org/spsl/evtracker/data/local/db/ChargeTypeConverter.kt
   class ChargeTypeConverter {
       @TypeConverter fun fromChargeType(value: ChargeType): String = value.name
       @TypeConverter fun toChargeType(value: String): ChargeType =
           ChargeType.entries.firstOrNull { it.name == value } ?: ChargeType.AC
   }
   ```
   Register via `@TypeConverters(ChargeTypeConverter::class)` on `AppDatabase`.
3. Change `ChargeEventEntity.chargeType` to `val chargeType: ChargeType = ChargeType.AC`.
   The stored column is still `TEXT`, so the schema is binary-compatible.
   **Data migration:** existing rows have either `"AC"` or `"DC"`. Add a Room
   migration that updates `"DC"` → `"DC_FAST"` (the closest semantic match), so
   `toChargeType` doesn't silently fall back to `AC` for legacy rows.
4. Update `ChargeEditFragment`, its ViewModel, `ChartsViewModel`, and DAO `@Query`
   filters that match on `chargeType` to use `ChargeType.*` literals (e.g.
   `WHERE chargeType = :type` with `type: ChargeType` parameter — Room will use
   the converter).
5. Update `BackupData` DTO + Gson serialiser to map `ChargeType` ↔ `String`
   (custom `JsonDeserializer` + serialiser) so existing `evtracker_backup.json`
   files still restore. Bump `backup_version` to **4** and update DESIGN.md §8.
6. Add `ChargeTypeConverterTest.kt` covering round-trip, invalid input fallback,
   and the legacy `"DC"` rewrite path.

---

## 🟡 TASK-26 — Change Room primary-key and foreign-key fields from `Int` to `Long` ☑ Done (2026-05-01)

> **Outcome:** all three entities (`CarEntity`, `ChargeEventEntity`,
> `CustomLocationEntity`) widen `id: Int` → `id: Long` and
> `ChargeEventEntity.carId: Int` → `Long`. DAOs, narrow domain
> interfaces (`CarReader`/`CarWriter`/`ChargeEventQueries`/
> `ChargeEventWriter`), all use cases that take an id, and every
> ViewModel state field (`activeCarId`, `eventId`, `carId`,
> `pendingDeletes` map key) flip to `Long`. Navigation safe-args:
> `nav_graph.xml` `eventId` argument switches `app:argType="integer"
> default="-1"` → `app:argType="long" default="-1L"`. **DataStore
> `ACTIVE_CAR_ID` stays an `intPreferencesKey`** — switching the same
> key name from `intPreferencesKey` to `longPreferencesKey` would
> silently drop the existing value, and a single user with thousands
> of cars over decades won't exceed `Int.MAX_VALUE`. The repository
> widens to `Long` at the boundary (`Flow<Long>` reader / `setActiveCarId(id: Long)`
> writer with internal `id.toInt()`) so callers see the entity-PK type
> consistently. Room schema bump v4 → v5 with a **deliberately no-op**
> `MIGRATION_4_5`: SQLite `INTEGER` columns already hold 64-bit signed
> integers, so widening Kotlin `Int` → `Long` doesn't change DDL —
> diff between `5.json` and `4.json` is exactly the version field
> (verified). The migration is registered as a tripwire so future
> downgrades trip Room's schema validator instead of silently
> truncating. `BackupData.CURRENT_VERSION` 4 → 5; DTO ids widen to
> `Long`; `BackupSerializer.SUPPORTED_VERSIONS = {3, 4, 5}` so v3/v4
> backups still restore (Gson reads narrower JSON Int into wider
> Kotlin Long without coercion). New instrumented test
> `migrate_4_to_5_isNoOp_widenIntPksToLong` exercises the migration;
> `migrate_1_to_4_validatesSchema` renamed to
> `migrate_1_to_5_validatesSchema` and asserts ids round-trip as
> `Long`. All gates green: `:app:assembleDebug`, `:app:assembleRelease`
> (R8), `:app:assembleDebugAndroidTest`, `ktlintCheck`, `:app:lint`,
> `:app:testDebugUnitTest` (275 cases — count unchanged because the
> Long-widening is a refactor that doesn't add behavior).

`CarEntity.id`, `ChargeEventEntity.id`, `ChargeEventEntity.carId`, and
`CustomLocationEntity.id` are typed as `Int` (32-bit). Room's documentation and
Android best practices recommend `Long` for auto-generated primary keys to
align with SQLite's 64-bit `ROWID` and eliminate any overflow risk.

1. Change `@PrimaryKey(autoGenerate = true) val id: Int = 0` → `Long = 0L` in:
   - `CarEntity.kt:8`
   - `ChargeEventEntity.kt:25`
   - `CustomLocationEntity.kt:12`
2. Change the corresponding foreign-key columns from `Int` to `Long`:
   - `ChargeEventEntity.carId: Int` → `Long`
3. Write a Room migration. Because SQLite `INTEGER` columns are already 64-bit
   regardless of Kotlin type, the migration mainly fixes Kotlin type safety; it
   should be a copy-and-rename pattern:
   1. Create `<table>_new` with the corrected schema.
   2. `INSERT INTO <table>_new SELECT * FROM <table>`.
   3. `DROP TABLE <table>; ALTER TABLE <table>_new RENAME TO <table>`.
   Repeat for `cars`, `charge_events`, `custom_locations`. Recreate the
   composite indices (`charge_events(carId, eventDate)`, plus single-column
   indices on `chargeType` and `location`).
4. Update all DAOs, repositories, narrow IFs, use cases, and ViewModels that
   accept or return car/event/location IDs to use `Long`.
5. Run `./gradlew test connectedAndroidTest` and verify the Room migration test
   passes against the new schema version.

> **Sequencing note (also see "Notes for Agents" addendum below):** TASK-14
> (battery degradation tracker) and TASK-25 (charge-type enum) also bump the
> Room schema version. Whichever lands first claims the next version number;
> later tasks must increment again and update the migration list in
> `AppDatabase.getInstance`.

---

## 🟡 TASK-27 — Decouple bottom-nav visibility from the hardcoded `hideOn` set ☑ Done (2026-05-01)

> **Outcome:** the four full-screen destinations (`wizardFragment`,
> `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`)
> declare `hideBottomNav=true` as a destination argument in
> `app/src/main/res/navigation/nav_graph.xml`. `MainActivity.onCreate`
> now reads `args?.getBoolean("hideBottomNav") ?: false` from the
> `addOnDestinationChangedListener` callback and no longer references
> any specific destination ID for visibility decisions (acceptance grep
> `grep -n "R\.id\." app/src/main/java/.../MainActivity.kt` returns
> only `nav_host_fragment`, `wizardFragment` for the start-destination
> override, and `R.navigation.nav_graph`). New instrumented test
> `app/src/androidTest/java/org/spsl/evtracker/MainActivityBottomNavTest.kt`
> exercises the dashboard → chargeEdit → back round-trip; it compiles
> via `:app:assembleDebugAndroidTest` and is grouped with the rest of
> the emulator-only suite. CLAUDE.md §Architecture documents the
> convention so the next agent doesn't have to edit `MainActivity` to
> add a new full-screen destination. Spec:
> `superpowers/specs/2026-05-01-task27-bottom-nav-hide-arg-design.md`.
> Plan: `superpowers/plans/2026-05-01-task27-bottom-nav-hide-arg.md`.
> The original task text is preserved below for historical context.

`MainActivity.kt:47` declares `val hideOn = setOf(R.id.wizardFragment, …)` to
decide when to hide the `BottomNavigationView`. Every new full-screen
destination requires editing `MainActivity`, coupling the Activity to specific
Fragment IDs. This is easy to forget — the `manageLocationsFragment` and
`carsFragment` cases were both retrofits that had to chase down the omission.

1. In `app/src/main/res/navigation/nav_graph.xml`, declare a per-destination
   argument:
   ```xml
   <fragment android:id="@+id/wizardFragment" …>
       <argument
           android:name="hideBottomNav"
           app:argType="boolean"
           android:defaultValue="true" />
   </fragment>
   ```
   Set `android:defaultValue="true"` on `wizardFragment`, `chargeEditFragment`,
   `carsFragment`, `manageLocationsFragment`. Set `false` (or omit — the default
   is `false`) on `dashboardFragment`, `historyFragment`, `chartsFragment`,
   `settingsFragment`.
2. In `MainActivity`, replace the `hideOn` set with:
   ```kotlin
   navController.addOnDestinationChangedListener { _, dest, args ->
       val hide = args?.getBoolean("hideBottomNav") ?: false
       binding.bottomNav.isVisible = !hide
   }
   ```
3. Delete the `hideOn` `setOf(…)` declaration entirely.
4. Update or add an instrumented test in `app/src/androidTest/` that navigates
   to `chargeEditFragment` and asserts `bottomNav.visibility == View.GONE`,
   then back to `dashboardFragment` and asserts `View.VISIBLE`.
5. Document the `hideBottomNav` argument convention in `../CLAUDE.md`
   (Architecture / Navigation note) so new destinations get the argument set
   correctly without anyone having to edit `MainActivity` again.

---

## 🟡 TASK-28 — Consolidate time on the existing `NowProvider` abstraction ☑ Done (2026-05-01)

> **Outcome:** every production `System.currentTimeMillis()` call outside
> the canonical `DispatcherModule.provideNowProvider` binding is gone.
> `CarEntity.createdAt`, `ChargeEventEntity.createdAt`, and
> `CustomLocationEntity.lastUsed` no longer have wall-clock-evaluating
> defaults — call sites pass `now.nowMillis()` explicitly.
> `BackupData.fromEntities`, `LocationWriter.recordUsage`,
> `DateRangeResolver.resolve` / `resolveCharts`, and
> `ChargeEditUiState.eventDateMillis` lost their `= System.currentTimeMillis()`
> defaults too. `AddCarUseCase`, `SaveChargeEventUseCase`,
> `ObserveDashboardStatsUseCase`, `DriveBackupRepository`,
> `RestoreBackupUseCase`, `ChargeEditViewModel`, and
> `ManageLocationsFragment` all inject `NowProvider` (the Fragment
> forwards `nowProvider::nowMillis` to the adapter constructor — adapters
> aren't Hilt entry points). `WorkerModule.provideClock(): () -> Long`
> is deleted; `DriveBackupWorker` consumes `NowProvider` directly. New
> JVM fake `FakeNowProvider` lives next to the existing fakes; the
> deterministic-clock plumbing is exercised by two new behavioural cases
> (`AddCarUseCaseTest.createdAt_reflectsNowProviderValue` and
> `SaveChargeEventUseCaseTest.createdAtAndLocationLastUsed_reflectNowProviderValue`).
> JVM unit-test count: 245 → 247. Acceptance grep
> (`grep -rn "System.currentTimeMillis()" app/src/main/java | grep -v DispatcherModule.kt`)
> returns only the `NowProvider.kt` KDoc comment — no production calls.
> `:app:assembleDebug`, `:app:assembleRelease` (with R8), `:app:lint`,
> `ktlintCheck`, and `:app:assembleDebugAndroidTest` all green. Spec:
> `superpowers/specs/2026-05-01-task28-nowprovider-consolidation-design.md`.
> Plan: `superpowers/plans/2026-05-01-task28-nowprovider-consolidation.md`.
> The original task text is preserved below for historical context.


> **Note on premise.** A `NowProvider` (`fun interface NowProvider { fun nowMillis(): Long }`)
> already exists in `domain/usecase/NowProvider.kt` and is bound by
> `DispatcherModule.provideNowProvider`. `ObserveChartsModelsUseCase` already
> consumes it. Don't introduce a parallel `Clock` interface — extend usage of
> the existing one. There is also a duplicate `() -> Long` provider in
> `WorkerModule.provideClock` that should be removed.

A `grep -rn "System.currentTimeMillis()" app/src/main/java` (excluding tests
and the production `NowProvider` binding) finds these direct uses that should
go through `NowProvider` instead:

- `data/local/entity/CarEntity.kt:14` (`createdAt` default)
- `data/local/entity/ChargeEventEntity.kt:36` (`createdAt` default)
- `data/local/entity/CustomLocationEntity.kt:15` (`lastUsed` default)
- `core/model/BackupData.kt:23` (`now: Long` default arg)
- `core/model/ChargeEditUiState.kt:8` (`eventDateMillis` default)
- `domain/repository/LocationWriter.kt:6` (`now: Long` default arg)
- `domain/service/DateRangeResolver.kt:11,20` (two `nowMillis: Long` defaults)
- `ui/locations/ManageLocationsAdapter.kt:21`
- `di/WorkerModule.kt:23` (`provideClock`: a `() -> Long` that duplicates `NowProvider`)

1. Remove the `= System.currentTimeMillis()` default from all entity constructors.
   Set the timestamp at the call site in repositories / use cases by injecting
   `NowProvider` and calling `now.nowMillis()` before insert.
2. For service/use-case default args, drop the default and require the caller to
   pass `now.nowMillis()` from an injected `NowProvider`. (Default args that
   call `System.currentTimeMillis()` capture wall-clock time at function-default
   evaluation, which is fine in production but undermines deterministic tests.)
3. Delete `WorkerModule.provideClock` entirely; consumers should `@Inject NowProvider`
   instead.
4. The `BackupData.serialize(now: Long = System.currentTimeMillis())` default
   should also be removed; `BackupSerializer` already has the use-case context
   to resolve `now` via `NowProvider`.
5. In tests, replace ad-hoc time fakes with a fixed `NowProvider`:
   ```kotlin
   class FakeNowProvider(@Volatile var time: Long = 0L) : NowProvider {
       override fun nowMillis() = time
       fun advance(ms: Long) { time += ms }
   }
   ```
   Add it to `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`.
6. Update or add tests around entity creation to assert that `createdAt` /
   `lastUsed` equal the fake's value, not the real wall clock.

---

## 🟢 TASK-29 — Add an explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags ☑ Done (2026-05-01)

> **Outcome:** added a `debug { }` block to `app/build.gradle.kts`
> with `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`,
> and `isDebuggable = true`. Both `debug` and `release` declare three
> matched custom fields — `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`,
> `DRIVE_FOLDER_SUFFIX` — as scaffolding for future consumers (no
> production-code consumer wired in this task; `BuildConfig.DEBUG`
> still exists for the binary debug/release distinction). `buildFeatures`
> flips `buildConfig = true`, which unblocks TASK-10's About screen
> (`BuildConfig.VERSION_NAME` / `VERSION_CODE`). Debug and release
> APKs can now coexist on a device — verified via
> `aapt dump badging`: debug = `org.spsl.evtracker.debug` /
> `1.0.1-debug`; release = `org.spsl.evtracker` / `1.0.1`.
> `.github/workflows/ci.yml` gains `:app:assembleRelease` as a
> release-smoke step (the keystore is absent in CI so the APK is
> unsigned, but R8 + `lintVitalRelease` still run).
> `docs/GOOGLE_CLOUD_SETUP.md` Step 5b documents the third OAuth
> Android client required for `org.spsl.evtracker.debug`; until the
> user registers it, Drive sign-in fails on debug builds (release is
> unaffected). Spec:
> `superpowers/specs/2026-05-01-task29-debug-build-type-design.md`.
> Plan: `superpowers/plans/2026-05-01-task29-debug-build-type.md`.
> The original task text is preserved below for historical context.

`app/build.gradle.kts` defines only a `release` block; `debug` is left as
Gradle's implicit default. Consequences: (1) debug and release share the same
`applicationId` so they cannot coexist on a device; (2) no `BuildConfig`
booleans exist to guard development-only features; (3) `buildConfig` is not
enabled in `buildFeatures` (`viewBinding = true` is — `buildConfig` is missing
since AGP 8.0 disabled it by default).

1. Add an explicit `debug` block to `buildTypes`:
   ```kotlin
   debug {
       applicationIdSuffix = ".debug"
       versionNameSuffix = "-debug"
       isDebuggable = true
       buildConfigField("boolean", "ENABLE_SEED_DATA", "true")
       buildConfigField("boolean", "VERBOSE_LOGGING", "true")
       buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"_debug\"")
   }
   release {
       // existing config …
       buildConfigField("boolean", "ENABLE_SEED_DATA", "false")
       buildConfigField("boolean", "VERBOSE_LOGGING", "false")
       buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"\"")
   }
   ```
2. Enable `BuildConfig` generation:
   ```kotlin
   buildFeatures {
       viewBinding = true
       buildConfig = true   // add this line
   }
   ```
3. Use `BuildConfig.VERBOSE_LOGGING` (or appropriate flag) where finer-grained
   guards are useful; preserve `if (BuildConfig.DEBUG)` only where the binary
   debug/release distinction is genuinely the right gate.
4. **OAuth implication:** changing the debug `applicationId` to
   `org.spsl.evtracker.debug` invalidates the existing debug OAuth Android
   client (which is bound to `org.spsl.evtracker` + the debug keystore SHA-1).
   Either register a **second** debug OAuth client for `org.spsl.evtracker.debug`
   in the Google Cloud project (recommended), or document in
   `../docs/GOOGLE_CLOUD_SETUP.md` that Drive backup is unavailable on debug
   builds after this change.
5. Update `../CLAUDE.md` (Build & Test section) to document:
   - Debug + release can be installed side-by-side (different `applicationId`).
   - Each `BuildConfig` flag's purpose and when to toggle it.
6. Once TASK-16 lands, ensure `.github/workflows/ci.yml` runs both
   `assembleDebug` and `assembleRelease` so the build types stay in sync.

---

## 🟢 TASK-30 — Migrate from MPAndroidChart to Vico (line/bar) + custom `PieChartView` (pie tabs)

A `grep -rln "com.github.mikephil.charting"` confirmed exactly three importer
files: `ChartsMarkerView.kt`, `ChartStyling.kt`, `ChartsTabFragment.kt`. The
six tabs map to Vico like this:

| Tab | MPAndroidChart type | Replacement | Notes |
|---|---|---|---|
| Trend | `LineChart` (AC + DC) | Vico `CartesianChartView` + `LineCartesianLayer` | Direct port |
| Monthly kWh | `BarChart` | Vico `CartesianChartView` + `ColumnCartesianLayer` | Renamed in Vico, same semantics |
| Monthly cost | `BarChart` | Vico `CartesianChartView` + `ColumnCartesianLayer` | Same |
| AC vs DC split | `PieChart` | **Custom `Canvas` `PieChartView`** | Vico has no pie chart |
| Locations | `PieChart` | **Custom `Canvas` `PieChartView`** | Same |
| Degradation | `LineChart` + `LimitLine` | Vico `CartesianChartView` + `LineCartesianLayer` + Vico `HorizontalAxis.Line` for the dashed nominal-capacity reference | TASK-14; the dashed nominal-`battery_kwh` reference line maps to a Vico decoration |

> **Theme-awareness must be preserved.** The current MPAndroidChart wiring
> resolves `?attr/colorOnSurface` (axis labels, legend text, pie center-text)
> and `?attr/colorOutlineVariant` (gridlines, axis lines) from the active M3
> theme so charts stay readable in dark mode (introduced by `c677a2b` after
> a user report of unreadable dark-mode text). Vico's API has equivalent
> hooks (`Axis.label.color`, `Axis.line.color`, `LineLayer` line colors) and
> the custom `PieChartView` slice-label paint must do the same lookup. Any
> replacement that ships with hardcoded greys is a regression — the
> `docs/TEST_PLAN.md §5b` smoke matrix step 6b is the gate.

### Implementation

**Step 1 — Add Vico; keep MPAndroidChart in place during the migration.**

```toml
# gradle/libs.versions.toml
[versions]
vico = "2.0.0"   # check latest stable at github.com/patrykandpatrick/vico

[libraries]
vico-views = { group = "com.patrykandpatrick.vico", name = "views", version.ref = "vico" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.vico.views)
```

**Step 2 — Migrate the line `Trend` tab.**

1. Replace `configureLineChart(chart: LineChart)` in `ChartStyling.kt` with a
   helper that configures `CartesianChartView` + `LineCartesianLayer`.
2. In `ChartsTabFragment.renderTrend()` swap `LineChart`/`LineDataSet`/`LineData`
   for Vico's `CartesianChartView` + `LineCartesianLayerModel.build {}`.
3. Port x-axis date formatting to a Vico `CartesianValueFormatter`.
4. `Entry.data` (currently used to stash epoch millis for the marker) doesn't
   exist on Vico models — pass marker payload via the model's
   `extraStore` map instead.
5. Replace `ChartsMarkerView` (extends MPAndroidChart `MarkerView`, overrides
   `getOffset`) with a Vico `DefaultCartesianMarker` or a custom
   `CartesianMarker`. Vico positions markers automatically — the `getOffset`
   override is no longer needed.

**Step 3 — Migrate the two bar tabs (Monthly kWh, Monthly cost).**

1. Replace `configureBarChart(chart: BarChart)` with a Vico helper for
   `ColumnCartesianLayer`.
2. Replace `BarChart` instantiation with `CartesianChartView`; replace
   `BarDataSet` / `BarData` / `BarEntry` with `ColumnCartesianLayerModel.build {}`.
3. Port `ChartStyling.monthBucketFormatter` from MPAndroidChart `ValueFormatter`
   to a Vico `CartesianValueFormatter` lambda.
4. Reuse the same Vico marker wrapper introduced in Step 2 — do **not** port
   the marker implementation twice.

**Step 4 — Replace pie tabs with a custom `Canvas` view.**

Vico has no pie chart. Implement once, use twice:

1. Create `app/src/main/java/org/spsl/evtracker/ui/common/PieChartView.kt`:
   a custom `View` that:
   - Accepts `data class PieSlice(val label: String, val value: Float, val color: Int)`.
   - Draws segments via `canvas.drawArc()` with a configurable hole radius
     (donut style, matching `ChartStyling.configurePieChart` `setHoleColor`).
   - Draws an optional center text string (for AC/DC count).
   - Draws a legend below the circle (label + colour swatch) replacing
     MPAndroidChart's built-in legend.
   - Animates the sweep via `ValueAnimator` (0° → 360° over 400 ms),
     mirroring the existing `chart.animateY(400)`.
2. In `renderAcDc()` and `renderLocations()`, replace `PieChart(requireContext())`
   with `PieChartView(requireContext())`. Pass slices + center text.
3. Port `ChartStyling.locationPalette()` as-is — it's a colour-array helper with
   no MPAndroidChart dependency.
4. Add a JVM unit test for `PieChartView` slice-angle math: empty data → zero
   sweep, non-empty data → angles sum to exactly 360°.

**Step 5 — Remove MPAndroidChart.**

Once all six tabs render correctly with the new implementations:

1. Drop `implementation(libs.mpandroidchart)` and the `mpandroidchart` entry
   from `gradle/libs.versions.toml`.
2. Delete the MPAndroidChart keep rule added in TASK-17 (`-keep class
   com.github.mikephil.charting.** { *; }` and matching `-dontwarn`).
3. Run `./gradlew lint` and confirm no dangling `dontwarn` references remain.

**Step 6 — Tests and docs.**

1. Update or add Espresso tests in `app/src/androidTest/` for each of the six
   tabs: assert the chart `View` is non-empty when the ViewModel emits a
   `Loaded` state with mock data.
2. Update `TEST_PLAN.md` (Charts section): replace MPAndroidChart-specific
   interaction notes (pinch-zoom, tap-marker) with Vico equivalents, and note
   that `PieChartView` does not support pinch-zoom by design.
3. Update `../CLAUDE.md` (Architecture / Dependencies note): remove the
   MPAndroidChart entry; add `vico-views` with a note on which tabs use it and
   which use `PieChartView`.

---

## 🟡 TASK-31 — Manual Drive controls: "Back up now" and "Wipe remote backup"

Drive auto-backup runs through WorkManager after every committed local change
(`enqueueUniqueWork("drive_backup", REPLACE, …)`), but there is no way for the
user to (a) force an immediate sync without waiting for WorkManager, or
(b) explicitly delete the snapshot stored in the App Data folder. Both gaps
matter:

- **Force-push** is the standard user-trust affordance — "I just made changes,
  I want to confirm they reached the cloud right now." Today the only signal
  is the "Last backup at …" timestamp, which only updates after the worker
  completes and the user has no way to drive the worker.
- **Remote wipe** is needed to (1) recover from a corrupted or test snapshot
  written during early development; (2) start over cleanly when the user
  switches accounts or wants to re-test the first-time-enable replace-or-skip
  flow; (3) honour a "delete my data from the cloud" request without forcing
  the user to disable Drive globally and lose the local-write trigger.

Both are local-only actions in Settings — they do **not** change the
auto-backup contract elsewhere.

### Domain layer

1. Extend `app/src/main/java/org/spsl/evtracker/domain/backup/BackupRepository.kt`
   with a new method:

   ```kotlin
   /**
    * Deletes the remote snapshot file ("evtracker_backup.json") from the
    * App Data folder. No-op if the file does not exist. Drive must be
    * authorised before calling — callers verify that via SettingsReader.driveEnabled
    * and the auth state.
    */
   suspend fun deleteRemoteBackup()
   ```

2. Implement it on
   `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`:
   list `appDataFolder` for files named `evtracker_backup.json`, call
   `drive.files().delete(fileId)` for each match, and route IO errors
   through the existing `withRetry` + `runTranslating` helpers so the
   delete path picks up TASK-07's bounded retry for free. Return
   `BackupResult` (`Success` / `AuthRequired` / `Failure(reason, cause?)`)
   to match `backupCurrentData()`'s contract.

3. Add two new use cases under `app/src/main/java/org/spsl/evtracker/domain/usecase/`:

   ```kotlin
   class PushBackupNowUseCase @Inject constructor(
       private val backupRepository: BackupRepository,
       private val settingsWriter: SettingsWriter,
       private val now: NowProvider,
   ) {
       suspend operator fun invoke() {
           backupRepository.backupCurrentData()
           settingsWriter.setLastBackupAt(now.nowMillis())
       }
   }
   ```

   ```kotlin
   class WipeRemoteBackupUseCase @Inject constructor(
       private val backupRepository: BackupRepository,
       private val settingsWriter: SettingsWriter,
   ) {
       suspend operator fun invoke() {
           backupRepository.deleteRemoteBackup()
           // Wipe clears the "Last backup at …" hint so the UI does not
           // show a stale timestamp pointing at a snapshot that no longer exists.
           settingsWriter.setLastBackupAt(0L)
       }
   }
   ```

   `PushBackupNowUseCase` deliberately bypasses `BackupScheduler` so the
   user gets synchronous-feeling feedback: success or failure surfaces in
   the UI as soon as the upload completes. The auto-backup worker remains
   the only writer through WorkManager — manual push is one extra path,
   not a replacement.

   **Note on TASK-28 sequencing:** `PushBackupNowUseCase` injects `NowProvider`.
   If TASK-28 has not yet consolidated `lastBackupAt` writes onto `NowProvider`,
   that's fine — this task introduces the pattern for one new caller; TASK-28
   sweeps the rest.

### Settings UI

4. In `app/src/main/res/layout/fragment_settings.xml`, add two new rows in the
   Drive section, **only visible when `driveEnabled = true`**:

   - **"Back up now"** — `MaterialButton`, primary tone. Tapping triggers
     the use case and disables itself with a `CircularProgressIndicator`
     overlay until completion. On success, show a Snackbar `"Backup
     uploaded"`. On failure, show a Snackbar with the error message.
   - **"Wipe remote backup"** — `MaterialButton`, **destructive tone**
     (`?attr/colorError`). Tapping shows a `MaterialAlertDialog`:

     ```
     Title:   Delete remote backup?
     Body:    This permanently deletes "evtracker_backup.json" from your
              Google Drive App Data folder. Local data on this device is
              NOT affected. The next local change will create a fresh
              backup automatically.
     Buttons: [Cancel]  [Delete]
     ```

     Only on confirm, invoke `WipeRemoteBackupUseCase`. Snackbar feedback
     on both success and failure.

5. Both rows must be hidden (View.GONE) — not merely disabled — when
   `driveEnabled = false`, since neither makes sense without an authorised
   client.

### ViewModel + state

6. Extend `SettingsViewModel`:
   - Add use case parameters: `pushBackupNow: PushBackupNowUseCase`,
     `wipeRemoteBackup: WipeRemoteBackupUseCase`.
   - Add `isManualBackupRunning: StateFlow<Boolean>` (gates the "Back up
     now" button's progress overlay; also disables "Wipe" while a push is
     in flight).
   - Add `isManualWipeRunning: StateFlow<Boolean>` (analogous, for wipe).
   - Add `events` of a new `SettingsEvent` sealed type (or extend whatever
     event channel SettingsViewModel already uses) with:
     - `BackupNowSucceeded`
     - `BackupNowFailed(messageRes: Int, detail: String? = null)`
     - `WipeSucceeded`
     - `WipeFailed(messageRes: Int, detail: String? = null)`
   - Methods:
     - `fun onPushBackupClicked() { viewModelScope.launch { … } }`
     - `fun onConfirmWipeClicked() { viewModelScope.launch { … } }`

7. The two operations must be **mutually exclusive**: a push in flight
   disables the wipe button, and vice versa. A second tap on a running
   button is a no-op (do not stack two pushes).

### Strings (i18n-ready)

8. All user-visible strings go in `app/src/main/res/values/strings.xml` —
   never hardcoded — so TASK-15 can translate them without rework. Names
   follow the existing `drive_*` prefix convention, e.g.
   `drive_backup_now_button`, `drive_wipe_button`,
   `drive_wipe_confirm_title`, `drive_wipe_confirm_body`,
   `drive_backup_now_success`, `drive_wipe_success`,
   `drive_backup_now_failure`, `drive_wipe_failure`.

### Tests

9. Add JVM unit tests:

   - `app/src/test/.../domain/usecase/PushBackupNowUseCaseTest.kt`:
     a. Calls `backupCurrentData()` exactly once.
     b. Updates `lastBackupAt` to the fake `NowProvider`'s value on success.
     c. Propagates the exception when `backupCurrentData()` throws.
     d. Does **not** update `lastBackupAt` when the upload throws.

   - `app/src/test/.../domain/usecase/WipeRemoteBackupUseCaseTest.kt`:
     a. Calls `deleteRemoteBackup()` exactly once.
     b. Sets `lastBackupAt = 0L` on success.
     c. Propagates the exception when delete throws.
     d. Does **not** clear `lastBackupAt` when delete throws.

   Use the existing `FakeBackupRepository` (extend it with `deleteCount: Int`
   and `failNextDelete: Throwable?`) and `FakeSettingsWriter`.

   - `app/src/test/.../ui/settings/SettingsViewModelTest.kt` (new tests in
     the existing class):
     a. `onPushBackupClicked` toggles `isManualBackupRunning` true → false.
     b. On push success, emits `BackupNowSucceeded`.
     c. On push failure, emits `BackupNowFailed`.
     d. `onConfirmWipeClicked` analogous coverage for wipe.
     e. Push in flight → `onConfirmWipeClicked` is a no-op (running flags
        guard each other).

10. Add an instrumented Espresso test
    `app/src/androidTest/.../ui/settings/SettingsBackupControlsTest.kt`:
    a. With Drive disabled, neither row is visible.
    b. With Drive enabled (Hilt-bound fake `DriveAuthManager`), both rows
       are visible.
    c. Tapping "Wipe remote backup" shows the confirmation dialog and
       does **not** call the use case until "Delete" is tapped.

### Documentation

11. Update `docs/DESIGN.md §8` (Drive backup): document the two new manual
    actions and that they bypass WorkManager. Note that the wipe action
    clears `lastBackupAt` so the UI does not show a stale timestamp.

12. Update `../CLAUDE.md` "Google Drive backup" subsection: add a bullet
    that the backup model now includes manual force-push and remote wipe,
    and that the auto-backup worker contract is unchanged.

### Out of scope

- Per-entity selective wipe (e.g., "delete only my charge events from
  Drive"). The remote snapshot is a single file by design — partial wipe
  is not supported.
- A full backup history with revisions on Drive. Drive's built-in
  revision history is opaque to App Data scope; surfacing it is a separate
  feature.
- Showing a Drive-quota meter in Settings. Quota awareness belongs to
  TASK-07 (error handling), not this task.
- "Restore from backup" affordance on demand (the existing first-time
  replace-or-skip flow already covers it; an explicit "Restore now"
  button is a separate task if needed).

### Acceptance criteria

The change is complete when **all** of the following hold:

1. `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`
   succeeds.
2. New JVM tests for both use cases (4 + 4 cases) and the `SettingsViewModel`
   additions (≥ 5 cases) pass.
3. `:app:assembleDebugAndroidTest` compiles with the new instrumented test.
4. With Drive disabled in Settings, neither button is visible.
5. With Drive enabled, "Back up now" pushes a fresh snapshot; verifying via
   Drive `files.list?spaces=appDataFolder` shows the file's `modifiedTime`
   updated. "Wipe remote backup" then removes the file; the same
   `files.list` returns no `evtracker_backup.json`.
6. After "Wipe remote backup", `lastBackupAt` is cleared and the UI hint
   reverts to its empty state. The next local change re-enqueues the
   auto-backup worker, recreating the file (regression check).
7. Mutual-exclusion holds: kicking off a slow push and then tapping wipe
   leaves wipe disabled until push completes.

---

## 🟡 TASK-32 — Bump AGP + Gradle wrapper for official `compileSdk = 35` support ☑ Done (2026-05-01)

> **Outcome:** AGP bumped `8.2.0` → `8.7.3` in
> `gradle/libs.versions.toml`; Gradle wrapper bumped `8.4-bin` → `8.9-bin`
> in `gradle/wrapper/gradle-wrapper.properties` (AGP 8.7.x officially
> requires Gradle 8.9+ per the AGP↔Gradle compatibility table). The
> workaround block in `gradle.properties` —
> `android.suppressUnsupportedCompileSdk=35` plus its explanatory
> comment — was deleted; AGP 8.7.3 officially supports `compileSdk = 35`
> so neither the "tested up to compileSdk = 34" advisory nor the
> "SDK XML version 4" parser warning fires anymore (verified by
> `./gradlew :app:assembleDebug --warning-mode all 2>&1 | grep -iE
> "(warning|tested up to|SDK XML)"` returning empty). Kotlin 1.9.21 +
> KSP 1.9.21-1.0.16 + Hilt 2.50 are all compatible with AGP 8.7.3 — no
> language-version bump required, and the K2 / Kotlin 2.x migration
> stays out of scope as planned. After a `clean` rebuild on the new
> toolchain, all gates green: `:app:assembleDebug`,
> `:app:assembleRelease` (R8 minification), `:app:assembleDebugAndroidTest`
> (instrumented compile), `ktlintCheck`, `:app:lint` (no new lint
> issues; existing baseline still respected), and `:app:testDebugUnitTest`
> (265 cases). `README.md` toolchain line updated to "Gradle 8.9 · AGP
> 8.7.3 · Kotlin 1.9.21". Historical specs/plans under
> `docs/superpowers/` keep their original AGP 8.2.0 / Gradle 8.4
> references — those are snapshots in time, not live docs. The
> original task text is preserved below.

After TASK-22 merged (`compileSdk` and `targetSdk` set to 35), AGP 8.2.0
emits two warnings on every build:

```
Warning: SDK processing. This version only understands SDK XML versions
up to 3 but an SDK XML file of version 4 was encountered. …

WARNING: We recommend using a newer Android Gradle plugin to use
compileSdk = 35

This Android Gradle plugin (8.2.0) was tested up to compileSdk = 34.
```

Both are silenced today by `android.suppressUnsupportedCompileSdk=35` in
`gradle.properties` (added 2026-05-01). The build is functionally fine —
TASK-22's empirical probe verified `:app:assembleDebug`,
`:app:assembleRelease`, R8 minification, and `lintVitalRelease` all
succeed. The SDK XML v4 warning is parser noise; AGP falls back gracefully.

This task is the *proper* fix: bump AGP (and Gradle wrapper) to a version
that officially supports `compileSdk = 35` and remove the workaround.

### Scope

1. **`gradle/libs.versions.toml`:** bump `agp = "8.2.0"` to a version
   ≥ 8.6.0. AGP 8.7.x is the latest stable 8.7 line at time of writing;
   confirm Hilt 2.50, KSP 1.9.21-1.0.16, and Kotlin 1.9.21 compatibility
   before picking the exact version. AGP 8.6 needs Gradle 8.7+;
   AGP 8.7 needs Gradle 8.9+. The Android docs' AGP↔Gradle compatibility
   table is the source of truth.
2. **`gradle/wrapper/gradle-wrapper.properties`:** bump `distributionUrl`
   from `gradle-8.4-bin.zip` to a wrapper version that satisfies the AGP
   requirement (8.7 or 8.9 depending on AGP choice).
3. **`gradle.properties`:** remove the
   `android.suppressUnsupportedCompileSdk=35` line and the comment block
   above it; verify the build no longer emits either warning.
4. **CI smoke:** push the branch and confirm
   `.github/workflows/ci.yml` still passes (ktlintCheck, `:app:lint`,
   `:app:testDebugUnitTest`, `:app:assembleRelease`).
5. **Hilt / KSP regression check:** Hilt 2.50 + KSP 1.9.21-1.0.16 are
   known-good with AGP 8.2.0; AGP 8.6+ may surface KSP plugin order
   warnings or new lint rules. Triage anything that fires.
6. **Kotlin/KSP version coordination:** AGP 8.7+ may want Kotlin 1.9.24+
   or 2.0.x. Bumping Kotlin is its own risk surface — prefer staying on
   1.9.21 if the chosen AGP version permits it; otherwise bump together
   in this task and document the new Kotlin/KSP versions.
7. **Lint baseline:** if AGP's bundled Lint version changes, new lint
   issues may surface. Add to `app/lint-baseline.xml` only if they're
   pre-existing offenses unrelated to the AGP bump; fix anything new
   that the AGP bump itself introduced.

### Out of scope

- **Kotlin 2.x migration.** Even if AGP 8.7+ accepts Kotlin 2.x, the K2
  compiler is its own multi-day investigation. Stay on Kotlin 1.9.x for
  this task.
- **Removing the `android.useAndroidX=true` / `nonTransitiveRClass=true`
  flags.** Those are unrelated.

### Acceptance

- `./gradlew :app:assembleDebug :app:assembleRelease` runs with
  **zero** AGP-version warnings.
- `gradle.properties` no longer contains
  `android.suppressUnsupportedCompileSdk`.
- The 245 JVM unit tests still pass; `:app:assembleDebugAndroidTest`
  still compiles; CI workflow stays green.
- `CLAUDE.md` Build & Test section updates the AGP/Gradle versions in
  the same paragraph that mentions Build Tools 35.

---

## 🟢 TASK-33 — Audit Kotlin 2.x / K2 + KSP + Hilt compatibility

TASK-32 bumped AGP to 8.7.3 and Gradle to 8.9 but explicitly deferred any
Kotlin language-version change — the toolchain stays at Kotlin 1.9.21 +
KSP 1.9.21-1.0.16 + Hilt 2.50. There is no other tracking task for the
Kotlin 2.x / K2 migration, which means the deferral risks becoming
permanent through inertia rather than a deliberate "stay on 1.9" decision.
The audit half is cheap and worth doing now while the AGP bump is fresh.

The migration itself is a multi-day investigation (K2's compiler
behaviour differs from K1 around inline classes, sealed types, and
generic inference) and stays out of scope for this task — TASK-33 is the
audit, not the bump.

### Scope

1. **Compatibility matrix.** Pick a target Kotlin 2.x line (likely
   `2.0.21` or the latest stable at audit time) and confirm:
   - AGP 8.7.3 supports it without further bumps. Cross-reference the
     AGP↔Kotlin compatibility note in the Android docs.
   - KSP has a matching `<kotlin>-1.0.x` artifact published. KSP versions
     are tied 1:1 to Kotlin minor versions.
   - Hilt 2.50 supports Kotlin 2.x, or pin a Hilt version that does
     (Hilt 2.51+ added K2 support).
2. **Plugin and dependency check.** Run `./gradlew dependencies --configuration releaseRuntimeClasspath`
   and grep for any third-party Kotlin-plugin output (`kotlinx-*`,
   compiler plugins, kapt artifacts). The project uses KSP not kapt;
   confirm no transitive kapt artifact pulls a stale Kotlin runtime.
3. **K2 known-issue review.** Skim the `kotlinlang.org` K2 known-issues
   list for anything that touches: Hilt-generated code, Room-generated
   DAOs, sealed `BackupResult` exhaustiveness checks, or
   `MutableStateFlow`/`StateFlow` inference in ViewModels. Note any
   blockers in the audit doc.
4. **Trial branch (no merge).** Create a throwaway branch, bump
   `kotlin = "..."` and `ksp = "..."` in `gradle/libs.versions.toml`, run
   `./gradlew clean :app:assembleDebug :app:testDebugUnitTest
   :app:assembleDebugAndroidTest :app:lint`. Capture failures verbatim.
   Do **not** open a PR — this branch's only output is the audit findings.

### Out of scope

- Actually merging the Kotlin bump. If the audit finds the path is
  clean, file a fresh task (TASK-XX) for the migration with a concrete
  upgrade plan derived from these findings.
- Switching kapt → KSP on any module (already done).
- Bumping Hilt independently of the Kotlin question.

### Acceptance criteria

The task is complete when an audit document is committed under
`docs/superpowers/specs/YYYY-MM-DD-task33-kotlin-2x-audit.md` covering:

1. Confirmed compatible (or incompatible) Kotlin/KSP/Hilt/AGP version triple.
2. Build/test results from the trial branch.
3. A go/no-go recommendation with named blockers if no-go, or a sized
   follow-up task description if go.

The audit branch is then deleted; no production change lands in this task.

---

## 🟡 TASK-34 — Nightly managed-AVD job for `connectedAndroidTest`

The repository has accumulated 10+ instrumented test files that the PR
gate (`.github/workflows/ci.yml`) never executes. The current set
includes — at minimum — the migration suite (`MigrationTest` with cases
covering 1→2→3→4→5→6), `AboutFragmentTest`, `MainActivityBottomNavTest`,
the wizard flow tests, and several Charts/Settings Espresso tests. They
compile via `:app:assembleDebugAndroidTest` on every PR (the existing
gate does enforce that), but failure modes that need a running device —
schema validation, navigation transitions, edge-to-edge insets, real
DataStore round-trips — only surface locally or at release-tag time.

Adding instrumented tests to the PR gate is rejected up front: emulator
boot dwarfs the rest of the gate's runtime and would push every PR cycle
above the 10-minute mark. The fix is a separate **nightly** job using
GitHub Actions' managed Android emulator action.

### Scope

1. **New workflow file.** Create `.github/workflows/nightly-instrumented.yml`
   with `on: schedule: [cron: '0 2 * * *']` (02:00 UTC; tune to a
   low-runner-contention window) plus `workflow_dispatch:` for manual
   triggering. Do **not** add `pull_request` or `push` triggers.
2. **Managed AVD action.** Use `reactivecircus/android-emulator-runner@v2`
   (the de-facto action) with API 26 (matches `minSdk`) **and** API 35
   (matches `targetSdk`) in a `strategy.matrix` so both ends of the
   support range get exercised. Use the `default` system image with
   `arch: x86_64`.
3. **Test target.** Run `./gradlew connectedDebugAndroidTest --no-daemon`.
   Hilt-injected tests already work without further configuration; the
   project's `HiltTestApplication` is wired in `app/src/androidTest/`.
4. **Failure surface.** On failure, upload `app/build/reports/androidTests/connected/`
   as an Actions artifact (HTML report is the primary debugging surface)
   and `app/build/outputs/androidTest-results/connected/` for the raw
   XML. Do not fail the merge queue — this workflow is informational.
5. **Notification.** Post a failure comment on the most recent commit
   on `main` via the `actions/github-script` action so failures are
   visible without the maintainer needing to open the Actions tab.
   (Optional; can be deferred if the matrix proves too noisy initially.)

### Out of scope

- Adding instrumented tests to the PR gate. The whole point of this
  task is to keep the PR gate fast.
- Running the nightly job on every supported API level. API 26 + API 35
  is the cost/coverage sweet spot.
- Adding instrumented tests for new features. Coverage decisions belong
  to the per-feature task, not this one.

### Acceptance criteria

1. `.github/workflows/nightly-instrumented.yml` exists and is valid
   (run `gh workflow list` to confirm it registers).
2. Manual `workflow_dispatch` runs successfully end-to-end against
   `main` HEAD on both API 26 and API 35.
3. The PR gate's runtime is unchanged (no new step added to `ci.yml`).
4. `CLAUDE.md` Build & Test section gains one line under the existing
   "connectedAndroidTest — needs API 26+ device or emulator" note,
   pointing at the nightly workflow as the canonical CI execution.

---

## 🟢 TASK-35 — Roborazzi screenshot tests for Dashboard + Charts

The `#FB8C00` DC orange (M3 tertiary palette seed, `docs/DESIGN.md §6`)
ships a known-untested contrast risk on dark surfaces — TASK-18 (a11y
pass) calls it out as a manual review item. The 2026-05-01 dark-mode
chart text bug (`c677a2b` fix) is a recent, concrete instance of how
theme-coupled rendering regressions slip through both unit and Espresso
tests: both gates passed while the text was actually unreadable.

Roborazzi runs a JVM-side rendering pipeline using Robolectric so
screenshot diffs run in the existing `:app:testDebugUnitTest` job
without an emulator. The PR-gate cost is single-digit seconds per
screen.

This task is the **prerequisite** for TASK-30 (MPAndroidChart → Vico
migration). Without baseline screenshots locked in **before** the
rendering stack swap, TASK-30's "looks identical" acceptance criterion
becomes a manual eyeball comparison across six tabs × two themes ×
multiple data shapes — i.e., unverified.

### Scope

1. **Add Roborazzi.**
   ```toml
   # gradle/libs.versions.toml
   [versions]
   roborazzi = "1.x.x"   # check latest stable
   robolectric = "4.x.x" # may already be present
   [libraries]
   roborazzi              = { group = "io.github.takahirom.roborazzi", name = "roborazzi",              version.ref = "roborazzi" }
   roborazzi-junit-rule   = { group = "io.github.takahirom.roborazzi", name = "roborazzi-junit-rule",   version.ref = "roborazzi" }
   robolectric            = { group = "org.robolectric",               name = "robolectric",            version.ref = "robolectric" }
   ```
   Wire under `testImplementation` in `app/build.gradle.kts` and add the
   Roborazzi Gradle plugin per its README.

2. **Baseline screens.** Capture both light and dark variants of:
   - Dashboard with 0 events (empty-state path).
   - Dashboard with mixed AC + DC events, single currency.
   - Dashboard with multi-currency banner.
   - Charts → Trend (line chart, kWh primary metric).
   - Charts → AC vs DC (pie tab — high contrast risk on dark surfaces).
   - Charts → Locations (pie tab, ≥ 2 slices using `LOCATION_PALETTE`).
   - Charts → Degradation (TASK-14 line chart with the dashed nominal
     reference line — the dashed line's contrast on dark surfaces is
     the second known unverified risk).

   Total: ~14 baseline images (7 screens × 2 themes). Store under
   `app/src/test/screenshots/` (Roborazzi default).

3. **CI integration.** Add `:app:verifyRoborazziDebug` to the existing
   PR gate after `:app:testDebugUnitTest`. Diffs above the default
   threshold fail the build. Update `app/build.gradle.kts` with
   `roborazzi { outputDir = ... }` if the default isn't appropriate.

4. **Update flow.** Document in `CLAUDE.md` the recapture command
   (`./gradlew recordRoborazziDebug`) and the convention that recapture
   commits must be a separate PR with a one-line "screenshot baseline
   refresh" body — never bundled with feature changes — so reviewers
   can scan the diff for unintended regressions.

### Out of scope

- Screenshot tests for ChargeEdit / Cars / History / Settings /
  ManageLocations / Wizard. Adding them later is straightforward;
  Dashboard + Charts is the focus because it's where TASK-30 will land
  and where dark-mode regressions have already burned the project once.
- Pixel-perfect device-matrix coverage. A single Robolectric device
  config is enough to catch theming and palette regressions; full
  device-matrix screenshots belong to a managed-AVD nightly run
  (TASK-34 territory) and are not necessary here.

### Acceptance criteria

1. `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug` passes
   on `main`.
2. The 14 baseline images are committed and reviewable in the PR diff.
3. A deliberate-regression smoke test (e.g., temporarily change a
   primary palette color and re-run) **fails** the gate as expected.
4. `CLAUDE.md` documents the recapture command and PR convention.
5. The TASK-30 spec is updated to reference these baselines as the
   "looks identical" acceptance gate.

---

## 🟡 TASK-36 — Inline-comment the "no `Result.retry()`" invariant in `DriveBackupWorker` ☑ Done (2026-05-01)

> **Outcome:** folded into the TASK-19 commit since the same
> `DriveBackupWorker.doWork()` block was being touched. Both
> `BackupResult.AuthRequired` and `is BackupResult.Failure` branches
> now carry an inline `// Repository already exhausted its retry
> budget (TASK-07); returning Result.retry() would let WorkManager
> re-amplify it.` comment naming both TASK-07 (budget owner) and
> TASK-36 (this comment-source). The function-level KDoc block also
> picked up a TASK-19 cross-reference for the side-channel through
> `BackupOutcomeReporter`. The original task text is preserved below.

`DriveBackupWorker.doWork()` deliberately translates `BackupResult.Failure`
and `BackupResult.AuthRequired` to `Result.failure()` rather than
`Result.retry()`. The reasoning — that `DriveBackupRepository` already
exhausted a bounded retry budget (TASK-07: `MAX_ATTEMPTS = 3`,
exponential backoff) and returning `Result.retry()` would let
WorkManager amplify that budget exponentially — currently lives in:

- The function-level KDoc on `doWork()` (one paragraph).
- The TASK-07 outcome block in this BACKLOG.
- A bullet in `CLAUDE.md` "Google Drive backup".

None of those is visible at the **point of edit**. A future agent or
contributor opening the `when` block and noticing "we have transient
failures here, why don't we retry?" will plausibly add `Result.retry()`
without reading the function-level KDoc, breaking the invariant.

### Scope

1. **Edit `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`.**
   Add a single-line `// ` comment **inside each branch of the `when`**
   that resolves to `Result.failure()` (`AuthRequired` and
   `Failure(...)`), naming the invariant explicitly. Sample shape:

   ```kotlin
   override suspend fun doWork(): Result = when (val r = backupRepository.backupCurrentData()) {
       BackupResult.Success -> {
           settingsWriter.setLastBackupAt(now.nowMillis())
           Result.success()
       }
       // Repository already exhausted its retry budget (TASK-07);
       // returning Result.retry() would let WorkManager re-amplify it.
       BackupResult.AuthRequired -> Result.failure()
       // Same invariant — see KDoc above and TASK-07 / TASK-36.
       is BackupResult.Failure -> Result.failure()
   }
   ```

2. **Cross-reference TASK-07.** The TASK-07 outcome block already
   describes the no-retry decision; no edit needed there. The new
   inline comments name TASK-07 by number so a curious reader can
   trace the rationale.

3. **No behaviour change.** This is a comment-only edit — `git diff`
   should show only added comments.

### Out of scope

- Adding a unit test that asserts `Result.retry()` is never returned.
  Worker behaviour is already covered indirectly via
  `DriveBackupRepositoryTest` retry-exhaustion cases.
- A custom ktlint rule that rejects `Result.retry()` in this file.
  Mechanical enforcement for one symbol in one file is over-engineered.
- Generalising the comment style across other workers — the project
  has exactly one `CoroutineWorker`; revisit if a second one lands.

### Acceptance criteria

1. `git diff` on `DriveBackupWorker.kt` shows only added comment lines.
2. `./gradlew ktlintCheck :app:testDebugUnitTest :app:assembleDebug`
   stays green.
3. Both failure branches of the `when` carry an explanatory comment
   naming TASK-07 (the budget owner) and TASK-36 (this comment-source).

---

## 🔴 TASK-37 — Replace Google Drive backup with SAF-based backup ⏸ Under consideration (2026-05-01)

> **⏸ Deferred — do not start without an explicit go-ahead.** The task
> body below is preserved as a complete implementation sketch for when
> the decision flips, but the project is **not** committed to a SAF
> migration today. Reasons to revisit: (1) F-Droid distribution becomes
> a target (the body's premise); (2) Drive auth friction (separate
> OAuth client per keystore SHA-1, tester allow-list on the consent
> screen) becomes a blocker for new contributors; (3) Drive API
> deprecation. Until then, leave the Drive code path in place and
> route any new backup work (TASK-31, TASK-19) through it.

**Blocks F-Droid distribution.** `play-services-auth`,
`google-api-client-android`, and `google-api-services-drive` are flagged
`NonFreeNet` by the F-Droid scanner. Replace `DriveBackupRepository`
with a Storage Access Framework implementation. The domain layer
(`BackupRepository`, `BackupResult`, `BackupSerializer`,
`RestoreBackupUseCase`) is unchanged — only `data/backup/` and Settings
UI change.

> **Sequencing note.** This task obsoletes the Drive-specific UI in
> TASK-31 ("Back up now" / "Wipe remote backup"). If TASK-31 is still
> open when TASK-37 is scheduled, fold its acceptance criteria into
> TASK-37's Settings rework (the use-case names carry over;
> `WipeRemoteBackupUseCase` becomes `ClearBackupLocationUseCase`) rather
> than landing TASK-31 against the Drive code path that's about to be
> deleted.

### Scope

1. Remove `play-services-auth`, `google-api-client-android`, and
   `google-api-services-drive` from `gradle/libs.versions.toml` and
   `app/build.gradle.kts`. Remove the `packaging { resources { excludes += … } }`
   block and the Drive keep-rules from `proguard-rules.pro`.

2. Add `BACKUP_URI = stringPreferencesKey("backup_uri")` to
   `PreferenceKeys`. Extend `SettingsReader` with
   `val backupUri: Flow<Uri?>` and `SettingsWriter` with
   `suspend fun setBackupUri(uri: Uri?)`. Store as `uri.toString()` /
   `Uri.parse()`.

3. Create `data/backup/SafBackupRepository.kt` implementing
   `BackupRepository`. `backupCurrentData()` opens the stored URI with
   `contentResolver.openOutputStream(uri, "wt")`; returns
   `BackupResult.AuthRequired` when the URI is null or a
   `SecurityException` is thrown (permission revoked — also clears the
   stored URI); returns `BackupResult.Failure` on `IOException`.
   `readRemoteBackup()` opens the URI for reading.
   `deleteRemoteBackup()` calls `DocumentsContract.deleteDocument()`
   and clears the stored URI.

4. When the user picks a file for the first time, call
   `contentResolver.takePersistableUriPermission(uri, READ | WRITE)`
   then `settingsWriter.setBackupUri(uri)`. Subsequent backup runs
   write silently without a picker.

5. In `di/`, rebind `BackupRepository` to `SafBackupRepository`. Delete
   `DriveBackupRepository.kt`, `DriveAuthManager.kt`,
   `DriveAuthManagerImpl.kt`. Rename `DriveBackupWorker` →
   `BackupWorker`; replace the Drive repo injection with the
   `BackupRepository` interface.

6. In `SettingsFragment` / `SettingsViewModel`, replace the Drive
   sign-in row with: **"Choose backup file"** (`ACTION_CREATE_DOCUMENT`,
   `type = "application/json"`, `EXTRA_TITLE = "evtracker_backup.json"`);
   show the chosen filename as a summary. Keep **"Back up now"** and
   **"Restore from backup"** (`ACTION_OPEN_DOCUMENT`); replace
   **"Wipe remote backup"** with **"Clear backup location"** (calls
   `deleteRemoteBackup()` after a confirmation dialog). Remove the
   `driveEnabled` preference key and all references to it.

7. Replace `docs/GOOGLE_CLOUD_SETUP.md` with `docs/BACKUP_SETUP.md`
   documenting the SAF flow and recommended storage locations (local,
   Nextcloud, Syncthing). Update `DESIGN.md §8` and `CLAUDE.md`
   accordingly.

8. Add `SafBackupRepositoryTest` covering: `AuthRequired` when URI is
   null; successful write; `AuthRequired` + URI cleared on
   `SecurityException`; `Failure` on `IOException`; successful read.
   Use `MockK` to stub `ContentResolver`.

---

## Notes for Agents (TASK-22 to TASK-30 addendum)

> Sequencing notes for **TASK-07, TASK-16, TASK-22, TASK-23, TASK-24,
> TASK-25, TASK-28, TASK-29** are obsolete — all eight landed. The
> static-analysis CI gate (TASK-16) is in place; the SDK bump to 35
> (TASK-22) merged with no `connectedAndroidTest` matrix to coordinate;
> TASK-23 → TASK-24 ran in the prescribed order; TASK-25 claimed Room
> v3 → v4 + `MIGRATION_3_4` and bumped `backup_version` to 4;
> TASK-28 retired the parallel `() -> Long` clock; TASK-29's debug
> build type + `BuildConfig` enablement is merged with the OAuth-client
> implication documented in `GOOGLE_CLOUD_SETUP.md` Step 5b; TASK-07
> introduced the `BackupResult` sealed class so TASK-19 has a stable
> error model to consume. Sequencing notes below cover the remaining
> open work only.

- **TASK-30 marker reuse:** complete the Vico marker wrapper once in Step 2
  and reuse in Step 3. Do not port `ChartsMarkerView` twice.

---

## Notes for Agents

- The package root is `org.spsl.evtracker`.
- The project uses Kotlin DSL (`build.gradle.kts`), Hilt for DI, Room for
  local persistence (currently at schema version 6), and Kotlin Coroutines
  with Flow throughout.
- All new classes must follow the existing naming and packaging conventions
  documented in [`../CLAUDE.md`](../CLAUDE.md).
- Do not introduce new third-party dependencies without checking
  `app/build.gradle.kts` first. Prefer libraries already present.
- After any structural change, run `./gradlew test` (JVM) and
  `./gradlew connectedAndroidTest` (instrumented) to verify no regressions.
- Room schema version is currently **6**. Any migration must bump it to **7**
  and add a corresponding migration file under `app/schemas/`. The migration
  list in `AppDatabase.companion` and `DatabaseModule.provideDatabase` must
  both be updated; `BackupData.CURRENT_VERSION` and
  `BackupSerializer.SUPPORTED_VERSIONS` need a coordinated bump when the
  schema change touches a field that's serialized to the Drive backup.
