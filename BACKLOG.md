# EV Tracker — Development Backlog

Generated from a senior Android developer code review of the `main` branch (April 2026).  
Each task is written as a self-contained instruction suitable for a coding agent.

---

## Priority Legend

| Symbol | Meaning |
|--------|---------|
| 🔴 | High priority — architecture correctness or data safety |
| 🟡 | Medium priority — robustness and test coverage |
| 🟢 | Low priority — UX improvement or new feature |

---

## 🔴 TASK-01 — Relocate `AggregationDispatcher` to the correct package

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

## Notes for Agents

- The package root is `org.spsl.evtracker`.
- The project uses Kotlin DSL (`build.gradle.kts`), Hilt for DI, Room for
  local persistence, and Kotlin Coroutines with Flow throughout.
- All new classes must follow the existing naming and packaging conventions
  documented in [`CLAUDE.md`](CLAUDE.md).
- Do not introduce new third-party dependencies without checking
  `app/build.gradle.kts` first. Prefer libraries already present.
- After any structural change, run `./gradlew test` (JVM) and
  `./gradlew connectedAndroidTest` (instrumented) to verify no regressions.
