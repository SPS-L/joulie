# TASK-39 — Room `@AutoMigration` Retrofit (Design Spec)

**Filed:** 2026-05-07
**Backlog row:** TASK-39, 🟢, no dependencies
**Goal:** Retrofit two pure ADD-COLUMN migrations (`MIGRATION_5_6`, `MIGRATION_6_7`) as `@AutoMigration` annotations on `@Database`. Establish the convention that future additive schema bumps from v8 onward default to `@AutoMigration` unless the change is non-additive. No user-facing change; net `-30` lines of boilerplate.

## 1. Background

`AppDatabase` is at `version = 7` with `exportSchema = true` and schemas exported to `app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/{3..7}.json`. Six manual migrations live in the `AppDatabase` companion:

| Pair | Kind | Auto-migratable? |
|------|------|------------------|
| `MIGRATION_1_2` | ADD COLUMN `chargeType` (NOT NULL DEFAULT) | yes, but skipped (too old) |
| `MIGRATION_2_3` | ADD several columns + CREATE TABLE `custom_locations` + indices | yes for ADD COLUMNs; no for CREATE TABLE statements with the manual `IF NOT EXISTS` idempotency we added; skipped |
| `MIGRATION_3_4` | UPDATE cell values (`'DC'` → `'DC_FAST'`) | **no** — `@AutoMigration` cannot mutate cell values |
| `MIGRATION_4_5` | no-op tripwire (Int → Long PK widening; SQLite-side INTEGER unchanged) | nominally yes; skipped (Room may complain about identical schemas) |
| `MIGRATION_5_6` | ADD COLUMN `socBefore` REAL, ADD COLUMN `socAfter` REAL | **yes** ✓ |
| `MIGRATION_6_7` | ADD COLUMN `kwhSource` TEXT NOT NULL DEFAULT 'MEASURED' | **yes** ✓ |

`@AutoMigration` is preferred for additive changes because Room reads the exported schemas, diffs them at compile time, and synthesises the migration SQL — eliminating the human-error surface (forgotten column, wrong default, typo'd table name). For pure ADDs the synthesised SQL is byte-for-byte equivalent to a hand-written `ALTER TABLE … ADD COLUMN …`.

## 2. Scope

**In scope.**

- Annotate `@Database` with `autoMigrations = [AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7)]`.
- Delete the now-dead `MIGRATION_5_6` and `MIGRATION_6_7` companion constants from `AppDatabase`.
- Drop those two from `DatabaseModule.provideDatabase`'s `addMigrations(...)` chain.
- Refactor `MigrationTest` v5→v6 and v6→v7 cases to drive the migration via `MigrationTestHelper.runMigrationsAndValidate(...)` (which discovers auto-migrations from the `@Database` annotation) instead of calling `MIGRATION_X_Y.migrate(db)` directly.
- Update `CLAUDE.md`'s migration list to mark v5→v6 and v6→v7 as auto-migrations.
- Update `DESIGN.md §4.2` (Room version history) with a short note about the retrofit + the going-forward convention.
- Add the convention to `CLAUDE.md`: "Additive schema bumps from v8 onward default to `@AutoMigration` unless the change rewrites cell values or restructures tables."

**Out of scope.**

- Retrofit of MIGRATION_1_2 / MIGRATION_2_3 / MIGRATION_3_4 / MIGRATION_4_5 (rationale per §1 table).
- `AutoMigrationSpec` callbacks (only needed for renames / drops; both candidates are pure adds).
- Schema validation tooling beyond what Room and the existing `MigrationTest` already provide.

## 3. Architecture

### 3.1 `@Database` annotation change

Before:

```kotlin
@Database(
    entities = [CarEntity::class, ChargeEventEntity::class, CustomLocationEntity::class],
    version = 7,
    exportSchema = true,
)
@TypeConverters(ChargeTypeConverter::class, ChargeKwhSourceConverter::class)
abstract class AppDatabase : RoomDatabase()
```

After:

```kotlin
@Database(
    entities = [CarEntity::class, ChargeEventEntity::class, CustomLocationEntity::class],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
@TypeConverters(ChargeTypeConverter::class, ChargeKwhSourceConverter::class)
abstract class AppDatabase : RoomDatabase()
```

### 3.2 Companion-object cleanup

Delete the `MIGRATION_5_6` and `MIGRATION_6_7` `Migration` constants from the companion. KDoc explaining what each migration does survives as the `AutoMigration(from, to)` annotation entry — the canonical record is the diff between schema JSON files (`5.json` vs `6.json`, `6.json` vs `7.json`), which Room uses as the source of truth.

The four kept migrations (1_2, 2_3, 3_4, 4_5) keep their existing form unchanged.

### 3.3 `DatabaseModule.provideDatabase` change

Before:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
    AppDatabase.MIGRATION_5_6,
    AppDatabase.MIGRATION_6_7,
)
```

After:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
)
```

Auto-migrations declared on the `@Database` annotation are discovered by Room automatically; `addMigrations(...)` is for manual `Migration` instances only.

### 3.4 `MigrationTest` refactor

Existing pattern uses `helper.createDatabase(name, fromVersion)`, then calls `MIGRATION_X_Y.migrate(db)` directly. After the retrofit, the v5→v6 and v6→v7 cases call `helper.runMigrationsAndValidate(name, toVersion, validateDroppedTables = true)` instead. The helper discovers auto-migrations from the `AppDatabase` class annotation and applies them in order.

The v3→v7 end-to-end chain test (around line 200 today) currently passes the manual migration list explicitly. After the retrofit it stops passing `MIGRATION_5_6, MIGRATION_6_7` (those become auto-discovered); it keeps passing the four still-manual entries.

The v3→v7 final-row assertions (line 380s) for `socBefore = null`, `socAfter = null`, `kwhSource = MEASURED` still hold — auto-migration emits `ALTER TABLE … ADD COLUMN socBefore REAL` (nullable, defaults to NULL) and `ALTER TABLE … ADD COLUMN kwhSource TEXT NOT NULL DEFAULT 'MEASURED'`. Behaviour is unchanged.

## 4. Verification path

1. **Compile-time gate.** `./gradlew :app:assembleDebug` invokes KSP; KSP delegates to Room's annotation processor; Room reads `app/schemas/{5,6,7}.json`, diffs them, and generates `AppDatabase_AutoMigration_5_6_Impl.java` + `AppDatabase_AutoMigration_6_7_Impl.java` under `build/generated/...`. If the schemas don't match the entity definitions or Room can't synthesise the migration, the build fails with a precise error. **A green `assembleDebug` is the strongest guarantee that the migration is well-formed.**
2. **JVM CI gate.** `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest`. None directly exercise migration but cover everything else.
3. **Instrumented `MigrationTest`** (`:app:connectedDebugAndroidTest --tests "*MigrationTest"`). The canonical proof. Best-effort locally if AVD is reachable; deferred to the nightly cron otherwise (per established TASK-77 / TASK-45 pattern).
4. **Mutation kill** (acceptance criterion #4). Pre-merge: delete one of the two `AutoMigration` entries (e.g. drop `AutoMigration(from = 6, to = 7)`), run `./gradlew :app:assembleDebug`, observe Room's KSP error naming the missing migration with version 6→7. Revert. Record the observed error in the release commit body.

## 5. Acceptance criteria

1. **`./gradlew :app:assembleDebug` green** — Room generates both auto-migrations.
2. **`./gradlew ktlintCheck :app:lint :app:testDebugUnitTest` green.**
3. **`MigrationTest` v5→v6 and v6→v7 cases use `runMigrationsAndValidate(...)`** — direct `MIGRATION_5_6.migrate(db)` / `MIGRATION_6_7.migrate(db)` calls in the test source no longer compile (and no longer exist).
4. **Mutation kill verified pre-merge** — release commit body records the KSP error message observed when one auto-migration is removed.
5. **`docs/DESIGN.md §4.2`** documents the retrofit + the future-bumps convention.
6. **`CLAUDE.md`** migration list reflects the new state (5_6 + 6_7 marked as auto) and adds the going-forward convention.
7. **`docs/BACKLOG.md`** TASK-39 → ☑ Done with outcome banner.
8. **Instrumented `MigrationTest`** — verified locally if AVD reachable, deferred to nightly cron otherwise. Either path documented in the release commit body.

## 6. Risks + mitigations

| Risk | Mitigation |
|------|------------|
| Room synthesises a migration that differs subtly from the manual one (e.g. column-order, default-value formatting). | The candidate migrations are pure ADDs of the simplest possible kind. The exported schemas (`5.json` / `6.json` / `7.json`) are the source of truth Room consulted when generating the manual migration's expected end state, so the synthesised migration converges on the same shape. The instrumented `MigrationTest` chain is the empirical check. |
| Existing v6 installs that already migrated through manual `MIGRATION_6_7` are at v7. After the retrofit, opening the v7 DB invokes neither auto nor manual migration (no version bump). | No regression: the on-disk schema matches `7.json` either way. Auto-migrations run only on a version bump. |
| A user on v5 (somehow stuck pre-v1.9.27) updates straight to v1.9.33+. | The auto-migration chain `AutoMigration(5,6) → AutoMigration(6,7)` runs in order, identical to the current manual chain. Verified by the v3→v7 end-to-end `MigrationTest` case. |
| `MigrationTestHelper.runMigrationsAndValidate(name, version=7, validateDroppedTables = true)` requires the helper to discover auto-migrations. The helper's constructor signature must include the `AppDatabase::class.java` argument so it can read the annotation. | The existing helper instantiation already passes `AppDatabase::class.java`. No constructor change needed. |
| Room's KSP processor + AGP 8.7.3 + KSP 2.x compatibility. | The codebase already uses KSP for Room (per `build.gradle.kts`); no new tooling added. If KSP fails, the existing `:app:kspDebugKotlin` task emits the error pre-test. |

## 7. Out of scope (forward-work)

- Retrofitting MIGRATION_4_5 to `@AutoMigration` (currently a no-op tripwire — Room may warn or refuse if the schemas are byte-identical between v4 and v5; not worth investigating until a future bump forces the question).
- Schema-diff CI tooling (e.g. `room-migrations-diff`) — out of scope until the codebase has more than one developer regularly bumping schemas.
- Retrofitting MIGRATION_2_3 (creates the `custom_locations` table; `@AutoMigration` *can* synthesise CREATE TABLE, but the existing manual migration's `IF NOT EXISTS` idempotency was specifically added to handle a pre-Room debug-build workaround documented in commit history; risk of breaking that with a generated CREATE TABLE).
