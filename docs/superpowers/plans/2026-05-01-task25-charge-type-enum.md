# TASK-25 Implementation Plan — `ChargeType` enum

**Goal:** Replace the stringly-typed `chargeType` field across the
entity, domain, UI state, and backup wire format with a Kotlin
`ChargeType` enum. Bump Room schema v3→v4 and `backup_version` 3→4
while keeping v3 backups restorable.

**Architecture:** Type-converter on Room, enum-aware Gson type adapter
on the backup serializer, `isDc` helper on the enum so the AC/DC UI
distinction stays a one-liner everywhere. No new domain abstraction.

**Tech Stack:** Kotlin 1.9.21, Room 2.6.x, Gson 2.10, Hilt, JUnit 4.

---

## Task 1 — Add `ChargeType` enum + JVM tests

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/model/ChargeType.kt`
- Create: `app/src/test/java/org/spsl/evtracker/core/model/ChargeTypeTest.kt`

- [ ] Write the enum with `isDc`, `displayLabel()`, `parseLegacy(s)`
  per the spec.
- [ ] Three JUnit 4 cases in `ChargeTypeTest`:
  - `parseLegacy_acAndDc_round_trip`
  - `isDc_returns_true_for_dcVariants`
  - `displayLabel_collapsesDcVariants`
- [ ] Run `:app:testDebugUnitTest --tests
  "org.spsl.evtracker.core.model.ChargeTypeTest"`. Expect green (no
  production code consumes the enum yet).

## Task 2 — Add `ChargeTypeConverter` + JVM tests

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/local/db/ChargeTypeConverter.kt`
- Create: `app/src/test/java/org/spsl/evtracker/data/local/db/ChargeTypeConverterTest.kt`

- [ ] Write the `@TypeConverter` class per the spec.
- [ ] Three JUnit 4 cases:
  - `roundTrip_acAndDcFast_preserved`
  - `legacyDcString_mapsToDcFast`
  - `unknownString_fallsBackToAc`
- [ ] Run the new tests. Expect green.

## Task 3 — Flip the entity + register the converter

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/entity/ChargeEventEntity.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt`

- [ ] In the entity, change `val chargeType: String = "AC"` to
  `val chargeType: ChargeType = ChargeType.AC`. Keep the
  `Index("chargeType")`.
- [ ] On `AppDatabase`, bump `version = 3` to `version = 4`. Add the
  class annotation `@TypeConverters(ChargeTypeConverter::class)` (above
  `abstract class AppDatabase`).

- [ ] **Do not run tests yet** — call sites won't compile until the
  rest of the diff lands. We absorb the compile-failure surface in one
  pass by editing all dependent files next.

## Task 4 — Add `MIGRATION_3_4` + register it

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt`

- [ ] In `AppDatabase` companion, add `MIGRATION_3_4` per the spec
  (single `UPDATE charge_events SET chargeType = 'DC_FAST' WHERE chargeType = 'DC'`).
- [ ] In `DatabaseModule.provideDatabase`, append
  `AppDatabase.MIGRATION_3_4` to the `addMigrations(...)` list.

## Task 5 — Thread `ChargeType` through domain models

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/SaveChargeEventInput.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/ChargeEditUiState.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/BackupData.kt`

- [ ] `SaveChargeEventInput.chargeType: String` → `ChargeType`.
- [ ] `ChargeEditUiState.chargeType: String = "AC"` →
  `ChargeType = ChargeType.AC`.
- [ ] `ChargeEventDto.chargeType: String` → `ChargeType` (still tagged
  `@SerializedName("charge_type")`). `BackupData.CURRENT_VERSION = 3`
  → `4`.

## Task 6 — Update `StatsCalculator` + use cases

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/StatsCalculator.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveDashboardStatsUseCase.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCase.kt`

- [ ] In `StatsCalculator.computeEfficiencyTrend`, replace
  `seriesFor(type: String)` with two direct calls (or a predicate
  lambda) that filter by `it.chargeType == ChargeType.AC` and
  `it.chargeType.isDc`.
- [ ] In `StatsCalculator.computeAcDcSplit`, filter the same way.
- [ ] In `ObserveDashboardStatsUseCase.buildUiState`, the
  `ChargeTypeFilter.AC|DC` branches use the same expressions.
- [ ] In `ExportCsvUseCase`, write `e.chargeType.name` so CSVs continue
  to carry an unambiguous string.

## Task 7 — Update UI Fragments / ViewModels

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModel.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditFragment.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/history/HistoryAdapter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/history/HistoryViewModel.kt`

- [ ] `ChargeEditViewModel.setChargeType(type: String)` →
  `setChargeType(type: ChargeType)`.
- [ ] `ChargeEditFragment` AC button passes `ChargeType.AC`, DC button
  passes `ChargeType.DC_FAST`. The render branch becomes
  `if (state.chargeType.isDc) check(R.id.charge_edit_type_dc) else check(R.id.charge_edit_type_ac)`.
- [ ] `HistoryAdapter`'s badge uses `row.event.chargeType.displayLabel()`.
- [ ] `HistoryViewModel.applyFilter` uses `it.chargeType == ChargeType.AC`
  / `it.chargeType.isDc`.

## Task 8 — Update `BackupSerializer` Gson registration + version check

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/domain/service/ChargeTypeJsonAdapter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/domain/service/BackupSerializer.kt`

- [ ] Implement `ChargeTypeJsonAdapter` (`JsonSerializer<ChargeType>`,
  `JsonDeserializer<ChargeType>`) — `serialize` writes
  `JsonPrimitive(src.name)`, `deserialize` calls
  `ChargeType.parseLegacy(json.asString)`.
- [ ] In `BackupSerializer`, register the adapter on the `GsonBuilder`.
- [ ] Loosen the version check: `if (parsed.backupVersion !in setOf(3, 4)) throw BackupVersionMismatch(parsed.backupVersion)`.

## Task 9 — Mechanical test fixups

**Files (JVM):**
- `app/src/test/java/org/spsl/evtracker/data/backup/DriveBackupRepositoryTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/BackupSerializerTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorAcDcSplitTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorLocationDistTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorMixedCurrencyTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/service/StatsCalculatorTrendTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ExportCsvUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetActiveCarDataUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/RestoreBackupUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/domain/usecase/SaveChargeEventUseCaseTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/charts/ChartsViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/dashboard/DashboardViewModelTest.kt`
- `app/src/test/java/org/spsl/evtracker/ui/history/HistoryViewModelTest.kt`

**Files (instrumented):**
- `app/src/androidTest/java/org/spsl/evtracker/ui/charts/ChartsFragmentTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt`
- `app/src/androidTest/java/org/spsl/evtracker/data/repository/RoomDataResetTransactionRunnerTest.kt`

- [ ] Replace `chargeType = "AC"` literals with `ChargeType.AC`,
  `chargeType = "DC"` with `ChargeType.DC_FAST`. Apply this everywhere
  in test fixtures and assertions.
- [ ] In `MigrationTest`:
  - Add `buildV3Database()` helper (clone of `buildV2Database` with
    the v2→v3 column additions baked in).
  - Add `migrate_3_to_4` test: insert `chargeType = 'DC'`, run
    `AppDatabase.MIGRATION_3_4.migrate(db)`, assert `chargeType` is now
    `'DC_FAST'`.
  - Update `openWithRoom()` to register all four migrations.
  - Update the schema-validation test to assert against v4 (the
    `event.chargeType` assertion becomes `ChargeType.AC`).

## Task 10 — Build + test gate

- [ ] `:app:testDebugUnitTest` — green, ≥ 263 cases.
- [ ] `:app:assembleDebug` — green.
- [ ] `:app:assembleRelease` — green (R8 still passes).
- [ ] `ktlintCheck` — green.
- [ ] `:app:lint` — no new offences.
- [ ] `:app:assembleDebugAndroidTest` — compiles.

## Task 11 — Update docs + commit

- [ ] `docs/BACKLOG.md` — flip TASK-25 to ☑ Done with an outcome block.
- [ ] `CLAUDE.md` Status — add a short TASK-25 note; bump JVM count
  ≥ 263; add a one-line note to the **Database — Room v3** subsection
  retitling it Room v4 with the new entity / converter / migration
  facts.
- [ ] `README.md` — bump `~257` → matching number.
- [ ] Commit, merge `--no-ff`, push, delete branch.
