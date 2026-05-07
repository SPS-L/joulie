# TASK-39 — Room `@AutoMigration` Retrofit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retrofit `MIGRATION_5_6` (adds `socBefore`/`socAfter`) and `MIGRATION_6_7` (adds `kwhSource`) as `@AutoMigration` annotations on `AppDatabase`. Replace direct-call test cases with `MigrationTestHelper`-based equivalents. Add the convention "additive bumps from v8 onward default to `@AutoMigration`" to project docs.

**Architecture:** Annotation-driven retrofit; net `-30` LOC of manual migration boilerplate. Spec: `docs/superpowers/specs/2026-05-07-task39-auto-migration-retrofit-design.md`.

**Tech Stack:** Room 2.x + KSP, AndroidX `room-testing` (`MigrationTestHelper`, already a dep). No new Gradle deps.

---

## Branch setup

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feat/task39-auto-migration-retrofit
```

Verify: `git status` shows clean tree on `feat/task39-auto-migration-retrofit`.

---

### Task 1: Annotate `@Database` with `autoMigrations`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt`

- [ ] **Step 1: Add the import**

In the existing `import androidx.room.*` block of `AppDatabase.kt`, add:

```kotlin
import androidx.room.AutoMigration
```

(If imports are explicit per ktlint convention, place this alphabetically after `androidx.room.AutoMigration` if not already present.)

- [ ] **Step 2: Add `autoMigrations` to `@Database`**

Replace:

```kotlin
@Database(
    entities = [
        CarEntity::class,
        ChargeEventEntity::class,
        CustomLocationEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
```

With:

```kotlin
@Database(
    entities = [
        CarEntity::class,
        ChargeEventEntity::class,
        CustomLocationEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
```

- [ ] **Step 3: Verify compile (KSP generates auto-migration code)**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Two new classes appear under `app/build/generated/ksp/debug/java/.../db/`:
- `AppDatabase_AutoMigration_5_6_Impl.java`
- `AppDatabase_AutoMigration_6_7_Impl.java`

If the build fails with a Room/KSP error about schema mismatch or missing column, **stop and surface the error**. The schemas in `app/schemas/` are the source of truth Room compares against — a mismatch means the entity definitions and the exported schemas have drifted, which is a separate bug.

---

### Task 2: Delete the now-redundant manual migrations

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt`

- [ ] **Step 1: Delete the `MIGRATION_5_6` companion constant**

Remove the entire block:

```kotlin
/**
 * Add optional `socBefore` and `socAfter` REAL columns to
 * ...
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE charge_events ADD COLUMN socBefore REAL")
        db.execSQL("ALTER TABLE charge_events ADD COLUMN socAfter REAL")
    }
}
```

- [ ] **Step 2: Delete the `MIGRATION_6_7` companion constant**

Remove the entire block (KDoc + `Migration` declaration). The exported schema `app/schemas/.../7.json` remains the canonical record of what column was added.

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL — but `DatabaseModule.kt` and `MigrationTest.kt` still reference the deleted constants, which we'll fix in Tasks 3 and 4. If the compile is failing only due to those two files, that's expected; otherwise stop and inspect.

---

### Task 3: Update `DatabaseModule.provideDatabase`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt`

- [ ] **Step 1: Drop `MIGRATION_5_6` and `MIGRATION_6_7` from `addMigrations(...)`**

Replace:

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

With:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
)
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL after this step (only `MigrationTest.kt` left as a compile blocker).

---

### Task 4: Refactor `MigrationTest` to use `MigrationTestHelper`

**Files:**
- Modify: `app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt`

- [ ] **Step 1: Add the `MigrationTestHelper` field + the `@Rule` JUnit field if not already present**

Inspect the test file. Locate the class declaration and the existing `@get:Rule` (if any). If `MigrationTestHelper` is not already wired:

Add the imports near the top of `MigrationTest.kt`:

```kotlin
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Rule
```

Add the helper rule inside the class (top of body, before any `@Test`):

```kotlin
@get:Rule
val helper: MigrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory(),
)
```

(If a different `@get:Rule` instance already exists on the class, add `helper` as a second `@get:Rule` field — JUnit allows multiple.)

- [ ] **Step 2: Drop `MIGRATION_5_6` and `MIGRATION_6_7` from `openWithRoom()`**

In the `openWithRoom()` private function (around line 196), replace:

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

With:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
)
```

- [ ] **Step 3: Refactor `migrate_5_to_6_addsSocColumns`**

Replace the existing test body with a `MigrationTestHelper`-driven version:

```kotlin
@Test
fun migrate_5_to_6_addsSocColumns() = runBlocking {
    // MIGRATION_5_6 is now an @AutoMigration. The helper discovers it from
    // the @Database annotation and applies it during runMigrationsAndValidate.
    val v5 = helper.createDatabase(testDbName, 5)
    v5.execSQL("INSERT INTO cars (id, name, createdAt) VALUES (1, 'A', 1000)")
    v5.execSQL(
        "INSERT INTO charge_events " +
            "(id, carId, eventDate, odometerKm, kwhAdded, chargeType, note, createdAt) " +
            "VALUES (1, 1, 2000, 100.0, 10.0, 'AC', '', 2000)",
    )
    v5.close()

    val v6 = helper.runMigrationsAndValidate(testDbName, 6, true)
    v6.query("SELECT socBefore, socAfter FROM charge_events WHERE id = 1").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue("socBefore should default to NULL on legacy rows", cursor.isNull(0))
        assertTrue("socAfter should default to NULL on legacy rows", cursor.isNull(1))
    }
    v6.close()
}
```

- [ ] **Step 4: Refactor `migrate_6_to_7_addsKwhSourceColumn`**

Replace the existing test body:

```kotlin
@Test
fun migrate_6_to_7_addsKwhSourceColumn() = runBlocking {
    // MIGRATION_6_7 is now an @AutoMigration. Chain v5 → v6 → v7 via the
    // helper; both transitions run as auto-migrations.
    val v5 = helper.createDatabase(testDbName, 5)
    v5.execSQL("INSERT INTO cars (id, name, createdAt) VALUES (1, 'A', 1000)")
    v5.execSQL(
        "INSERT INTO charge_events " +
            "(id, carId, eventDate, odometerKm, kwhAdded, chargeType, note, createdAt) " +
            "VALUES (1, 1, 2000, 100.0, 10.0, 'AC', '', 2000)",
    )
    v5.close()

    val v7 = helper.runMigrationsAndValidate(testDbName, 7, true)
    v7.query("SELECT kwhSource FROM charge_events WHERE id = 1").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("MEASURED", cursor.getString(0))
    }
    v7.close()
}
```

- [ ] **Step 5: Verify compile**

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL. No more references to `AppDatabase.MIGRATION_5_6` or `AppDatabase.MIGRATION_6_7` anywhere in the codebase.

- [ ] **Step 6: Acceptance — no stale references**

```bash
grep -rn "MIGRATION_5_6\|MIGRATION_6_7" app/src
```

Expected: zero hits.

---

### Task 5: CLAUDE.md migration list update

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update the migration descriptions**

Find the section that enumerates `MIGRATION_1_2`..`MIGRATION_6_7` (under "Database, Room v7"). Replace the `MIGRATION_5_6` and `MIGRATION_6_7` bullets with:

```markdown
- `AutoMigration(from = 5, to = 6)`: adds nullable `socBefore` and `socAfter` REAL columns to `charge_events`. Pure additive ADD COLUMNs — Room synthesises the migration from the v5 ↔ v6 schema diff at compile time. Replaced the manual `MIGRATION_5_6` in v1.9.33 (TASK-39).
- `AutoMigration(from = 6, to = 7)`: adds `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` to `charge_events`. Round-tripped via `ChargeKwhSourceConverter` into the `ChargeKwhSource` enum. Replaced the manual `MIGRATION_6_7` in v1.9.33 (TASK-39).
```

- [ ] **Step 2: Append the going-forward convention**

After the migration list, append:

```markdown
**Auto-migration convention (TASK-39):** Additive schema bumps from v8 onward default to `@AutoMigration(from, to)` on `@Database` annotation. Manual `Migration` constants are reserved for non-additive changes: cell-value rewrites (e.g. `MIGRATION_3_4`'s `'DC'` → `'DC_FAST'` UPDATE), table renames or drops, column type changes that SQLite cannot reinterpret, and any migration that needs `AutoMigrationSpec` callbacks.
```

---

### Task 6: DESIGN.md §4.2 update

**Files:**
- Modify: `docs/DESIGN.md`

- [ ] **Step 1: Locate §4.2 (Room Database Version History)**

```bash
grep -n "^### 4.2" docs/DESIGN.md
```

- [ ] **Step 2: Append the auto-migration retrofit note**

Append a new sub-bullet at the end of the v6 → v7 section (or wherever the current "latest version" notes live):

```markdown
**Auto-migration retrofit (v1.9.33, TASK-39).** The v5 → v6 and v6 → v7 transitions run via `@AutoMigration(from, to)` annotations on `@Database`. Room's annotation processor reads the exported schemas (`app/schemas/{5,6,7}.json`) and synthesises the migration SQL at compile time — equivalent to the prior hand-written `ALTER TABLE … ADD COLUMN …` statements but boilerplate-free. The convention going forward: pure-additive bumps from v8 onward default to `@AutoMigration`; non-additive bumps (cell rewrites, renames, drops) keep their hand-written `Migration` form.
```

---

### Task 7: BACKLOG flip

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Replace the TASK-39 row**

Find:

```
| TASK-39 | 🟢 | Adopt Room `@AutoMigration` for additive schema bumps from v6 onward |  | ☐ |
```

Replace with:

```
| TASK-39 | 🟢 | Adopt Room `@AutoMigration` for additive schema bumps from v6 onward. **Done 2026-05-07** in `feat/task39-auto-migration-retrofit` (v1.9.33). `MIGRATION_5_6` (ADD socBefore + socAfter) and `MIGRATION_6_7` (ADD kwhSource) replaced with `@AutoMigration` annotations on `@Database`. Room's annotation processor synthesises the migration SQL at compile time from the exported schemas in `app/schemas/`. `DatabaseModule.provideDatabase` and `MigrationTest` updated to match. CLAUDE.md migration list + DESIGN.md §4.2 carry the going-forward convention: additive bumps from v8 onward default to `@AutoMigration`; non-additive bumps stay manual. |  | ☑ |
```

---

### Task 8: Mutation kill (acceptance criterion #4)

- [ ] **Step 1: Temporarily delete one auto-migration**

In `AppDatabase.kt`, change:

```kotlin
autoMigrations = [
    AutoMigration(from = 5, to = 6),
    AutoMigration(from = 6, to = 7),
],
```

To:

```kotlin
autoMigrations = [
    AutoMigration(from = 5, to = 6),
],
```

- [ ] **Step 2: Run `assembleDebug` and capture the KSP error**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -40
```

Expected: BUILD FAILED with a Room error of the form "A migration from 6 to 7 is necessary" or "Cannot find migration from 6 to 7" — the precise wording depends on Room's KSP processor version. Capture the message for the release commit body.

- [ ] **Step 3: Revert the mutation**

Restore both auto-migration entries:

```kotlin
autoMigrations = [
    AutoMigration(from = 5, to = 6),
    AutoMigration(from = 6, to = 7),
],
```

- [ ] **Step 4: Confirm green**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

### Task 9: Run full local CI gate

- [ ] **Step 1: Run all three gates**

```bash
./gradlew ktlintCheck
./gradlew :app:lint
./gradlew :app:testDebugUnitTest
```

All three must pass.

- [ ] **Step 2: Best-effort instrumented MigrationTest**

```bash
adb devices 2>&1 | tail -5
```

If an emulator/device is reachable:

```bash
scripts/run-instrumented.sh MigrationTest
```

Expected: PASS, including the v3→v7 chain test which now runs the auto-migrations transparently.

If no AVD is reachable: document that the instrumented MigrationTest is deferred to the nightly cron, in the release commit body.

---

### Task 10: Version bump + commits

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump versionCode + versionName**

```kotlin
versionCode = 49       // was 48
versionName = "1.9.33" // was "1.9.32"
```

- [ ] **Step 2: Stage feature changes**

```bash
git add app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt
git add app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt
git add app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt
git add CLAUDE.md
git add docs/DESIGN.md
git add docs/BACKLOG.md
```

- [ ] **Step 3: Commit feature**

```bash
git commit -m "$(cat <<'EOF'
feat(task-39): Room @AutoMigration retrofit for v5→v6 + v6→v7

Replace MIGRATION_5_6 (adds socBefore + socAfter) and MIGRATION_6_7
(adds kwhSource) with @AutoMigration annotations on @Database. Both
were pure ADD COLUMN; Room's annotation processor synthesises
equivalent migration SQL from the exported schemas in app/schemas/.

Convention going forward: additive bumps from v8 onward default to
@AutoMigration; non-additive (cell rewrites, renames, drops) stay
manual. Documented in CLAUDE.md + DESIGN.md §4.2.

Mutation kill verified pre-merge: dropping AutoMigration(6, 7) from
the annotation triggers a Room KSP build error citing the missing
6→7 migration; reverted before commit.

Instrumented MigrationTest verification: <local-result-or-deferred>.
EOF
)"
```

(Substitute `<local-result-or-deferred>` with the actual outcome from Task 9 Step 2.)

- [ ] **Step 4: Stage + commit version bump**

```bash
git add app/build.gradle.kts
```

```bash
git commit -m "chore(release): v1.9.33"
```

---

### Task 11: Merge, push, tag, cleanup

All git commands run separately per CLAUDE.md (no compound `&&`).

- [ ] **Step 1: Switch to main**

```bash
git checkout main
```

- [ ] **Step 2: Merge non-fast-forward**

```bash
git merge --no-ff feat/task39-auto-migration-retrofit -m "Merge branch 'feat/task39-auto-migration-retrofit'"
```

- [ ] **Step 3: Stage + commit spec + plan on main (TASK-76 / TASK-77 precedent)**

```bash
git add docs/superpowers/specs/2026-05-07-task39-auto-migration-retrofit-design.md
git add docs/superpowers/plans/2026-05-07-task39-auto-migration-retrofit.md
```

```bash
git commit -m "docs(task-39): file spec + plan for @AutoMigration retrofit"
```

- [ ] **Step 4: Push main**

```bash
git push origin main
```

- [ ] **Step 5: Tag**

```bash
git tag v1.9.33
```

- [ ] **Step 6: Push tag**

```bash
git push origin v1.9.33
```

- [ ] **Step 7: Delete the feature branch**

```bash
git branch -d feat/task39-auto-migration-retrofit
```

- [ ] **Step 8: Verify clean state**

```bash
git status
```

```bash
git log --oneline -6
```

```bash
git rev-parse HEAD origin/main
```

Expected: clean tree (sandbox-bound `??` entries OK), HEAD on main, HEAD == origin/main, last 6 commits include merge + chore + spec/plan + feature.

- [ ] **Step 9: Confirm release workflow**

```bash
gh run list --workflow=release.yml --limit 1
```

Expected: a queued / in-progress / completed run for tag `v1.9.33`.

---

## Done

Acceptance criteria from the spec:

1. `:app:assembleDebug` green (Task 1 Step 3 + Task 8 Step 4).
2. ktlint + lint + testDebugUnitTest green (Task 9 Step 1).
3. `MigrationTest` v5→v6 + v6→v7 use `runMigrationsAndValidate` (Task 4).
4. Mutation kill verified (Task 8).
5. DESIGN.md + CLAUDE.md updated (Tasks 5 + 6).
6. BACKLOG flipped (Task 7).
7. Instrumented MigrationTest verified or deferred (Task 9 Step 2).
