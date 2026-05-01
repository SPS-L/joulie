# TASK-28 — Consolidate time on `NowProvider`

> **Status:** spec for backlog item TASK-28 (🟡, no hard prerequisites).
> See `docs/BACKLOG.md` for the original task body. The "Notes on premise"
> there confirms a `NowProvider` already exists; this task extends its
> usage and removes the parallel scattered uses of
> `System.currentTimeMillis()`.

## Goal

Make wall-clock time a single, injectable seam (`NowProvider.nowMillis()`)
across production code so JVM tests can use a fixed `FakeNowProvider` and
get deterministic results. Remove every `System.currentTimeMillis()`
default arg from entity constructors, service/use-case parameters, and the
`ManageLocationsAdapter`. Delete the duplicate `WorkerModule.provideClock`
`() -> Long` provider; consumers switch to the existing `NowProvider`.

## Non-goals

- Adding a `Clock` abstraction parallel to `NowProvider`. The existing
  `domain/usecase/NowProvider.kt` is the seam; we extend it.
- Replacing `System.currentTimeMillis()` inside the production
  `DispatcherModule.provideNowProvider` binding — that's the lone ground
  truth and stays.
- Replacing `System.currentTimeMillis()` inside test helpers that don't
  contribute to behaviour under test (e.g. ad-hoc seed timestamps that are
  later overwritten or never observed).

## Production diff scope

**Drop `System.currentTimeMillis()` defaults — make the parameter required:**

| File | Symbol |
|------|--------|
| `data/local/entity/CarEntity.kt` | `createdAt` |
| `data/local/entity/ChargeEventEntity.kt` | `createdAt` |
| `data/local/entity/CustomLocationEntity.kt` | `lastUsed` |
| `core/model/BackupData.kt` | `fromEntities(now: Long = ...)` |
| `core/model/ChargeEditUiState.kt` | `eventDateMillis: Long = ...` |
| `domain/repository/LocationWriter.kt` | `recordUsage(label, now: Long = ...)` |
| `domain/service/DateRangeResolver.kt` | `resolve(period, nowMillis = ...)` and `resolveCharts(...)` |

**Inject `NowProvider` and pass `now.nowMillis()` at call site:**

| File | What it sets / passes |
|------|------------------------|
| `domain/usecase/AddCarUseCase.kt` | `CarEntity(createdAt = now.nowMillis())` |
| `domain/usecase/SaveChargeEventUseCase.kt` | `ChargeEventEntity(createdAt = now.nowMillis())` and `locationWriter.recordUsage(label, now.nowMillis())` |
| `domain/usecase/ObserveDashboardStatsUseCase.kt` | `dateRangeResolver.resolve(period, now.nowMillis())` |
| `data/backup/DriveBackupRepository.kt` | `BackupData.fromEntities(..., now = now.nowMillis())` |
| `domain/usecase/RestoreBackupUseCase.kt` | `BackupData.fromEntities(..., now = now.nowMillis())` |
| `ui/chargeedit/ChargeEditViewModel.kt` | initial `ChargeEditUiState(eventDateMillis = now.nowMillis(), ...)` |
| `ui/locations/ManageLocationsFragment.kt` | passes `now.nowMillis()` (or a `() -> Long` reference) to `ManageLocationsAdapter` |

**Constructor signature changes:**

- `ui/locations/ManageLocationsAdapter.kt` — gains a `nowProvider: () -> Long`
  constructor parameter. The adapter doesn't take a Hilt-injected
  `NowProvider` directly because RecyclerView adapters are not Hilt
  entry points; the Fragment owns the injection and forwards a function
  reference. (Alternative: pass `nowMillis: Long` per `submitList`, but
  that mis-models freshness when the same list is rendered twice.)
- `data/backup/DriveBackupWorker.kt` — `private val clock: () -> Long` →
  `private val now: NowProvider`. The lone call site changes from
  `clock()` to `now.nowMillis()`.

**Module deletion:**

- `di/WorkerModule.provideClock` is removed; nobody else binds or
  consumes `() -> Long`.

## Test diff scope

**New fake** in `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`:

```kotlin
class FakeNowProvider(@Volatile var time: Long = 0L) : NowProvider {
    override fun nowMillis() = time
    fun advance(ms: Long) { time += ms }
}
```

Wire it into `FakeSaveChargeEventGateway` so the `SaveChargeEventUseCase`
in fakes-driven tests gets a deterministic clock.

**Mechanical churn — explicit `createdAt = 0L` / `lastUsed = 0L`:** every
test entity constructor that previously relied on the default must pass
the value explicitly. Affected files (≈ 25 sites):

- JVM: `HistoryViewModelTest`, `ChargeEditViewModelTest`,
  `BackupSerializerTest`, `StatsCalculator*Test` (Trend, Cost, AcDcSplit,
  LocationDist, MixedCurrency), `ExportCsvUseCaseTest`,
  `ResetAllDataUseCaseTest`, `ResetActiveCarDataUseCaseTest`,
  `DeleteChargeEventUseCaseTest`, `ObserveChartsModelsUseCaseTest`,
  `SaveChargeEventUseCaseTest`, `ObserveDashboardStatsUseCaseTest`,
  `RestoreBackupUseCaseTest`, `ChartsViewModelTest`.
- androidTest: `ChargeEventDaoTest`, `CarRepositoryTest`,
  `RoomDataResetTransactionRunnerTest`, `ChargeEventRepositoryTest`,
  `ChartsFragmentTest`, `CustomLocationDaoTest`.

**Behavioural test additions:**

- `AddCarUseCaseTest` (new or extend existing): assert
  `entity.createdAt == fakeNow.time` after `invoke(...)`.
- `SaveChargeEventUseCaseTest`: assert `entity.createdAt == fakeNow.time`
  for the inserted event and that `recordUsage` is called with that
  same value.

## Out-of-scope

- The `(android)Test` files that compute `now = System.currentTimeMillis()`
  for purely timestamp-relative seed data (e.g. `daysAgo(20)`) are left
  as-is. The point of TASK-28 is determinism for `NowProvider`-driven
  branches; tests that explicitly want "wall-clock-ish" relative times
  for emulator/Room round-trips are not made worse by this task.
- `ManageLocationsAdapter` test coverage. The adapter's only behaviour
  change is forwarding a `() -> Long`; covered structurally by the
  Fragment-level instrumented tests.

## Acceptance

- `grep -rn "System.currentTimeMillis()" app/src/main/java | grep -v "DispatcherModule.kt"`
  returns **0 production hits**.
- `grep -n "() -> Long" app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`
  returns nothing; the worker's `now` is typed `NowProvider`.
- `grep -n "provideClock" app/src/main/java/org/spsl/evtracker/di/WorkerModule.kt`
  returns nothing.
- `:app:testDebugUnitTest` is green at the original count + 1–2 new
  cases (FakeNowProvider plumbing assertions). Total ≥ 246.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` build
  cleanly; `:app:lint` reports no new offenses against the baseline.

## Risks

- **Test churn volume.** ~25 file edits to add explicit `createdAt = 0L`.
  Mechanical, but easy to miss one — fail-fast at compile time means
  this is a build-time, not runtime, risk.
- **Adapter param vs. injection.** If we later need adapter-level
  injection for other reasons, the `() -> Long` constructor arg becomes
  the entry point — not a problem, just a stylistic note.
- **Backwards compatibility of Room schema.** None affected. SQLite
  column types don't depend on Kotlin nullability or Kotlin defaults;
  the entity defaults are purely a Kotlin-side concern.
