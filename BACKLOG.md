# EV Tracker — Development Backlog

Tasks 1–15 were generated from a senior Android developer code review of the `main` branch (April 2026). Tasks 16–21 are follow-up improvements identified during a 2026-04-30 verification pass against `main` (CI/release pipeline, R8 keep-rules, a11y posture, and SPS-Lab research relevance). Each task is written as a self-contained instruction suitable for a coding agent.

---

## Task Overview

| Task | Priority | Description | Done |
|------|----------|-------------|------|
| TASK-01 | 🔴 | Relocate `AggregationDispatcher` out of `di/` | ☑ |
| TASK-02 | 🔴 | Enforce `ResetAllDataUseCase` as sole caller of `RoomDataResetTransactionRunner` | ☐ |
| TASK-03 | 🔴 | Unify `UiState` vs `ScreenState` naming in `core/model` | ☐ |
| TASK-04 | 🟡 | JVM unit tests for `CostParser` | ☐ |
| TASK-05 | 🟡 | JVM unit tests for `EfficiencyPoint` | ☐ |
| TASK-06 | 🟡 | JVM unit tests for use cases | ☐ |
| TASK-07 | 🟡 | Drive backup error handling & retry logic | ☐ |
| TASK-08 | 🟢 | Migrate `CarEditDialog` to Compose `AlertDialog` | ☐ |
| TASK-09 | 🟢 | CSV export for `EfficiencyPoint` data with date-range picker | ☐ |
| TASK-10 | 🟢 | In-app About / Info screen with SPS-Lab acknowledgment | ☐ |
| TASK-11 | 🟡 | Odometer regression detection UX improvement | ☐ |
| TASK-12 | 🟡 | Widget: last-charge summary on home screen | ☐ |
| TASK-13 | 🟢 | Charging session timer / live session mode | ☐ |
| TASK-14 | 🟡 | Battery capacity degradation tracker | ☐ |
| TASK-15 | 🟢 | Localisation (i18n) foundation | ☐ |
| TASK-16 | 🟡 | Static analysis & code style gate in CI (ktlint + Android Lint) | ☐ |
| TASK-17 | 🟡 | R8/ProGuard follow-up audit: MPAndroidChart keep rule + release smoke test | ☐ |
| TASK-18 | 🟡 | Accessibility (a11y) pass — TalkBack, contentDescription, contrast, touch targets | ☐ |
| TASK-19 | 🟡 | Backup failure notification channel + Android 13+ `POST_NOTIFICATIONS` handling | ☐ |
| TASK-20 | 🟢 | CO₂ savings tracker (ICE baseline, Cyprus grid intensity, methodology doc) | ☐ |
| TASK-21 | 🟢 | Android Baseline Profile module for cold-start performance | ☐ |

**Priority legend:** 🔴 High (architecture/data safety) · 🟡 Medium (robustness/UX) · 🟢 Low (new feature)  
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
> `docs/superpowers/specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md`
> and plan `docs/superpowers/plans/2026-04-30-task01-aggregation-dispatcher-relocation.md`.
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

## 🔴 TASK-02 — Enforce that `RoomDataResetTransactionRunner` is only called from `ResetAllDataUseCase`

The class `RoomDataResetTransactionRunner` performs a destructive database
transaction. It must never be called directly from a ViewModel or Fragment.

1. Audit all usages of `RoomDataResetTransactionRunner` across the codebase.
2. If it is called from anywhere other than `ResetAllDataUseCase`, refactor
   so that the ViewModel calls `ResetAllDataUseCase` and that use case
   delegates to `RoomDataResetTransactionRunner`.
3. Add a KDoc comment to `RoomDataResetTransactionRunner` explicitly stating:
   `"This class must only be called from ResetAllDataUseCase."`
4. If no violation is found, add the KDoc comment and confirm in a code comment.

---

## 🔴 TASK-03 — Unify `UiState` vs `ScreenState` naming convention in `core/model`

The `core/model` package contains both `*UiState` and `*ScreenState` naming
patterns (e.g., `ChartsUiState.kt` and `ChartsScreenState.kt`), which is
inconsistent and confusing.

1. Audit all files in:
   `app/src/main/java/org/spsl/evtracker/core/model/`
2. Rename all `*ScreenState` classes to `*UiState` to match the Android
   ViewModel/StateFlow convention.
3. Update all references across ViewModels, Fragments, and tests.
4. Ensure no duplicate state classes exist for the same screen (merge if
   `ChartsUiState` and `ChartsScreenState` represent the same concept).
5. Verify the project builds and all tests pass.

---

## 🟡 TASK-04 — Add JVM unit tests for `CostParser`

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

## 🟡 TASK-05 — Add JVM unit tests for `EfficiencyPoint`

The class `app/src/main/java/org/spsl/evtracker/core/model/EfficiencyPoint.kt`
models energy efficiency data (likely kWh/km or Wh/km). It has no unit tests.

Create the file:
`app/src/test/java/org/spsl/evtracker/core/model/EfficiencyPointTest.kt`

Write unit tests covering:
1. Normal efficiency calculation with valid energy (kWh) and distance (km).
2. Zero distance — expect an exception or a defined sentinel value (not `NaN`
   or `Infinity`). Document the chosen behavior.
3. Zero energy consumed — confirm output is `0.0` efficiency.
4. Very large distances or energy values — check for overflow.
5. If `EfficiencyPoint` is a data class, test `equals()` and `copy()` for
   correctness.
6. If it derives cost-per-km, combine with a mock cost value and test the
   combined computation.

Use JUnit 4 or JUnit 5 consistent with the existing test setup. No Android
dependencies.

---

## 🟡 TASK-06 — Add JVM unit tests for `RenameCarUseCase` and `ResetAllDataUseCase`

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

## 🟡 TASK-07 — Add error handling and retry logic to `DriveBackupRepository`

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

## 🟢 TASK-08 — Migrate `CarEditDialog` to a Compose `AlertDialog`

The class `app/src/main/java/org/spsl/evtracker/ui/cars/CarEditDialog.kt`
is a `DialogFragment` managing its own lifecycle. Replace it with a Compose
`AlertDialog` rendered from within the Cars screen.

Pre-conditions: Confirm that `app/build.gradle.kts` already has Compose
dependencies. If not, add:
- `androidx.compose.ui:ui`
- `androidx.compose.material3:material3`
- `androidx.activity:activity-compose`

Steps:
1. Create a new Composable function `CarEditDialog` in a new file:
   `app/src/main/java/org/spsl/evtracker/ui/cars/CarEditDialogCompose.kt`

   The composable must accept:
   - `isVisible: Boolean`
   - `initialName: String`
   - `onConfirm: (String) -> Unit`
   - `onDismiss: () -> Unit`

2. Implement a Material3 `AlertDialog` with a single `TextField` for the
   car name, a Confirm button (disabled if name is blank), and a Cancel button.

3. Replace all usages of the old `CarEditDialog` DialogFragment with this
   composable, driven by a boolean state in the host ViewModel or Fragment.

4. Delete the old `CarEditDialog.kt` once all usages are removed.

5. Update or add a UI test in `app/src/androidTest/` to verify the dialog
   appears, accepts input, and confirms correctly.

---

## 🟢 TASK-09 — Add CSV export for `EfficiencyPoint` data

The SPS-Lab uses EV charging and efficiency data for smart grid research.
Add a CSV export feature so aggregated efficiency data can be extracted
for offline analysis.

1. Create a new use case:
   `app/src/main/java/org/spsl/evtracker/domain/usecase/ExportEfficiencyDataUseCase.kt`

   This use case must:
   - Accept a date range (`startDate: LocalDate`, `endDate: LocalDate`).
   - Query all `EfficiencyPoint` records within that range from the repository.
   - Serialize them to CSV with header:
     `timestamp,distance_km,energy_kwh,efficiency_wh_per_km,cost_eur`
   - Write the CSV to the app's external files directory using
     `Context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`.
   - Return the `File` reference on success, or throw on failure.

2. Add an "Export Data" button in the Settings screen that:
   - Prompts the user for a date range (use `MaterialDatePicker`).
   - Calls the use case via the `SettingsViewModel`.
   - On success, triggers an Android share intent (`ACTION_SEND`) with the
     CSV file so it can be sent via email or saved to Drive.

3. Add unit tests for `ExportEfficiencyDataUseCase` in:
   `app/src/test/java/org/spsl/evtracker/domain/usecase/ExportEfficiencyDataUseCaseTest.kt`

   Test: correct CSV header, correct row count, correct handling of empty
   result set, and correct file naming (e.g., `ev_efficiency_YYYY-MM-DD.csv`).

---

## 🟢 TASK-10 — Add In-App "About / Info" Screen

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

---

## 🟡 TASK-11 — Odometer Regression UX Improvement

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

## 🟡 TASK-14 — Battery Capacity Degradation Tracker

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

4. Create a Greek translation file as a first target locale (relevant for
   Cyprus University of Technology context):
   `app/src/main/res/values-el/strings.xml`
   Translate at minimum: all navigation labels, screen titles, button
   labels, empty state messages, and error messages. Domain-specific
   technical terms (kWh, km/kWh, AC, DC) should remain in their
   internationally recognised forms.

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

## 🟡 TASK-16 — Static analysis & code-style gate in CI

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

6. Document the new gate in `CLAUDE.md` (Build & Test section): "PRs must
   pass `:app:ktlintCheck :app:lint` before merge."

---

## 🟡 TASK-17 — R8/ProGuard follow-up audit: chart library + release smoke test

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
     c. Open Charts; verify all five tabs (Trend / Monthly kWh / Monthly cost / AC vs DC / Locations) render without crash.
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

There is no mention of accessibility in `DESIGN.md`, `CLAUDE.md`, or
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

## 🟡 TASK-19 — Backup failure notification channel

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

7. Document in `CLAUDE.md` the cadence: "Regenerate baseline profile when:
   adding/removing a major dependency, restructuring startup, or before
   each `v*` tag."

---

## Notes for Agents

- The package root is `org.spsl.evtracker`.
- The project uses Kotlin DSL (`build.gradle.kts`), Hilt for DI, Room for
  local persistence (currently at schema version 3), and Kotlin Coroutines
  with Flow throughout.
- All new classes must follow the existing naming and packaging conventions
  documented in [`CLAUDE.md`](CLAUDE.md).
- Do not introduce new third-party dependencies without checking
  `app/build.gradle.kts` first. Prefer libraries already present.
- After any structural change, run `./gradlew test` (JVM) and
  `./gradlew connectedAndroidTest` (instrumented) to verify no regressions.
- Room schema version is currently **3**. Any migration must bump it to **4**
  and add a corresponding migration file under `app/schemas/`.
