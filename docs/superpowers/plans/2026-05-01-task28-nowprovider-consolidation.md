# TASK-28 Implementation Plan — `NowProvider` Consolidation

**Goal:** Remove every production `System.currentTimeMillis()` outside the
single `DispatcherModule.provideNowProvider` binding; thread `NowProvider`
through use cases, the backup repository, the worker, and the
`ChargeEditViewModel`; delete the duplicate `WorkerModule.provideClock`.

**Architecture:** Use the existing `domain/usecase/NowProvider.kt` seam.
Production binding stays as `NowProvider { System.currentTimeMillis() }` in
`DispatcherModule`. Fakes use `FakeNowProvider` for deterministic tests.

**Tech Stack:** Hilt 2.50, Kotlin 1.9.21, Room v3, JUnit 4 / coroutines test.

---

## Task 1 — Add `FakeNowProvider`

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

- [ ] Add the fake class right after the `FakeCarReader` block (top of file is fine):

```kotlin
import org.spsl.evtracker.domain.usecase.NowProvider

class FakeNowProvider(@Volatile var time: Long = 0L) : NowProvider {
    override fun nowMillis() = time
    fun advance(ms: Long) { time += ms }
}
```

(No standalone test for the fake — it's exercised by every test that uses it.)

## Task 2 — Drop entity defaults

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/entity/CarEntity.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/entity/ChargeEventEntity.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/entity/CustomLocationEntity.kt`

- [ ] Replace `val createdAt: Long = System.currentTimeMillis(),` with
  `val createdAt: Long,` in `CarEntity` and `ChargeEventEntity`.
- [ ] Replace `val lastUsed: Long = System.currentTimeMillis(),` with
  `val lastUsed: Long,` in `CustomLocationEntity`.
- [ ] **Do not run tests yet** — call sites will fail compile until Tasks 3–8
  land. All edits must land in one push.

## Task 3 — Wire `NowProvider` into `AddCarUseCase`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/AddCarUseCase.kt`

- [ ] Add `private val now: NowProvider` constructor parameter.
- [ ] Pass `createdAt = now.nowMillis()` to the `CarEntity(...)` constructor.

## Task 4 — Wire `NowProvider` into `SaveChargeEventUseCase`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCase.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/LocationWriter.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`
  (`FakeSaveChargeEventGateway` now wires the new constructor parameter)

- [ ] In `LocationWriter.kt`, drop the `= System.currentTimeMillis()` default
  on `recordUsage(label, now: Long)`. Required arg.
- [ ] In `SaveChargeEventUseCase.kt`, add `private val now: NowProvider` to
  the constructor.
- [ ] Pass `createdAt = now.nowMillis()` into the `ChargeEventEntity(...)`
  constructor.
- [ ] Replace `locationWriter.recordUsage(it)` with
  `locationWriter.recordUsage(it, now.nowMillis())`.
- [ ] In `Fakes.kt`, update `FakeSaveChargeEventGateway` to own a
  `val nowProvider = FakeNowProvider()` and pass it as `now = nowProvider`
  into `SaveChargeEventUseCase(...)`.

## Task 5 — Wire `NowProvider` into `ObserveDashboardStatsUseCase`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCase.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/DateRangeResolver.kt`

- [ ] In `DateRangeResolver.kt`, drop both `= System.currentTimeMillis()`
  defaults on `resolve` and `resolveCharts`. Required arg.
- [ ] In `ObserveDashboardStatsUseCase.kt`, add `private val now: NowProvider`
  to the constructor.
- [ ] Replace `dateRangeResolver.resolve(period)` with
  `dateRangeResolver.resolve(period, now.nowMillis())`.

## Task 6 — Wire `NowProvider` into `BackupData.fromEntities` callers

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/BackupData.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCase.kt`

- [ ] In `BackupData.kt`, drop the `= System.currentTimeMillis()` default on
  `fromEntities(now: Long)`. Required arg.
- [ ] In `DriveBackupRepository.kt`, add `private val now: NowProvider` to
  the constructor; pass `now = now.nowMillis()` to `BackupData.fromEntities`.
- [ ] In `RestoreBackupUseCase.kt`, add `private val now: NowProvider` to
  the constructor; pass `now = now.nowMillis()` to `BackupData.fromEntities`.

## Task 7 — Wire `NowProvider` into `ChargeEditViewModel`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/ChargeEditUiState.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModel.kt`

- [ ] In `ChargeEditUiState.kt`, drop the `= System.currentTimeMillis()`
  default on `eventDateMillis`. Required arg.
- [ ] In `ChargeEditViewModel.kt`, add `private val now: NowProvider` to
  the constructor.
- [ ] Replace `MutableStateFlow(ChargeEditUiState())` with
  `MutableStateFlow(ChargeEditUiState(eventDateMillis = now.nowMillis()))`.
- [ ] Pass `eventDateMillis = now.nowMillis()` to the two `Create`-mode
  branches in `init { ... }`. The `Edit`-mode branch still uses
  `event.eventDate`.

## Task 8 — Wire `NowProvider` into `ManageLocationsAdapter`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsAdapter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/locations/ManageLocationsFragment.kt`

- [ ] Add a `nowProvider: () -> Long` constructor parameter on the adapter.
  Replace `System.currentTimeMillis()` inside `bind` with `nowProvider()`.
- [ ] In `ManageLocationsFragment.kt`, `@Inject lateinit var nowProvider: NowProvider`
  on the Fragment, and instantiate the adapter as
  `ManageLocationsAdapter(nowProvider::nowMillis)`.

## Task 9 — Replace `() -> Long` clock with `NowProvider` in the worker

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`

- [ ] In `DriveBackupWorker.kt`, change `private val clock: () -> Long` to
  `private val now: NowProvider`. Replace `clock()` with `now.nowMillis()`.
- [ ] In `WorkerModule.kt`, delete the `provideClock` `@Provides` method
  entirely. The module now only provides `WorkManager`.

## Task 10 — Mechanical test fixup: explicit `createdAt = 0L` / `lastUsed = 0L`

**Files (JVM unit tests):**
- `app/src/test/java/org/spsl/evtracker/ui/history/HistoryViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/BackupSerializerTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTrendTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorCostTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorAcDcSplitTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorLocationDistTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorMixedCurrencyTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/DeleteChargeEventUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/settings/SettingsViewModelTest.kt`

**Files (instrumented tests):**
- `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CustomLocationDaoTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/ChargeEventDaoTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/repository/CarRepositoryTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/repository/ChargeEventRepositoryTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunnerTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/ui/charts/ChartsFragmentTest.kt`

- [ ] For each entity constructor call missing `createdAt`/`lastUsed`,
  add `createdAt = 0L` (or a meaningful constant where the test reads
  back). Mechanical edit per file.
- [ ] For ViewModel-tests that previously consumed the production
  `ObserveDashboardStatsUseCase` / `SaveChargeEventUseCase` constructors,
  add a `FakeNowProvider` instance and pass it as `now = ...`.
- [ ] Run `:app:testDebugUnitTest` after this task. All previously-passing
  tests must remain green.

## Task 11 — New behavioural test: NowProvider value flows through

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt`
- Possibly modify: `app/src/test/java/org/spsl/evtracker/domain/usecase/AddCarUseCaseTest.kt`
  (create if absent)

- [ ] Add a JUnit case that:
  1. Sets `FakeNowProvider(time = 12_345L)`.
  2. Calls `saveChargeEvent.invoke(...)` with a non-blank location.
  3. Asserts the inserted `ChargeEventEntity.createdAt == 12_345L`.
  4. Asserts the recorded location row has `lastUsed == 12_345L`.
- [ ] If `AddCarUseCaseTest.kt` exists, add an analogous case for
  `CarEntity.createdAt`. If it doesn't, do not create one — the use case
  is small and a test for it is out of scope (the value flow is asserted
  by the SaveChargeEvent path).

## Task 12 — Verify acceptance grep

- [ ] Run `grep -rn "System.currentTimeMillis()" app/src/main/java`. Allowed
  hits: only `di/DispatcherModule.kt` (the canonical binding).
- [ ] Run `grep -n "() -> Long" app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`.
  Must return nothing.
- [ ] Run `grep -n "provideClock" app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`.
  Must return nothing.

## Task 13 — Build + test gate + commit

- [ ] `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest` — green.
- [ ] `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug` — green.
- [ ] `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest` — green
  (compile-only; running needs an emulator).
- [ ] `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck :app:lint` — green.
- [ ] Update `docs/BACKLOG.md`: flip TASK-28 to ☑ Done with an outcome
  block similar to TASK-27/TASK-29.
- [ ] Update `CLAUDE.md` Status section to mention TASK-28 + new test count.
- [ ] Commit on feat branch, merge `--no-ff` to main, push, delete branch.
