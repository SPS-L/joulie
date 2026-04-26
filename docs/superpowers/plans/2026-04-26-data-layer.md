# Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Sub-project B from `docs/superpowers/specs/2026-04-26-data-layer-design.md` — Room v3 with three entities, three DAOs, two migrations, three narrow repositories, and an `activeCarId` extension to `SettingsRepository`. After this plan lands, the app has a complete persistence layer ready for Sub-project C's domain code to build on.

**Architecture:** Single Activity, MVVM, Hilt-injected (from A). Adds Room v3 (KSP-processed) with three entities (`CarEntity`, `ChargeEventEntity`, `CustomLocationEntity`) under `data/local/entity/`, three DAOs under `data/local/dao/`, an `AppDatabase` under `data/local/db/` registering `MIGRATION_1_2` and `MIGRATION_2_3`. Three `@Singleton @Inject` repositories under `data/repository/` are thin façades over their DAOs — **no `SettingsRepository` injection in any of them** (active-car coupling deferred to use cases in Sub-project C). New `DatabaseModule` provides `AppDatabase` + DAOs.

**Tech Stack:** Kotlin 1.9.21 · AGP 8.2 · Hilt 2.50 (already wired in A) · Jetpack Room 2.6.1 with KSP processor · JUnit 4 with `androidx.test.runner.AndroidJUnit4` for instrumented tests · `androidx.test:core-ktx` for `ApplicationProvider` · `androidx.room:room-testing` for `MigrationTestHelper` (already in deps from A but we don't actually use the helper — see §10 in the spec) · existing in-memory DataStore JVM test setup for the new SettingsRepository test.

**Spec source:** `docs/superpowers/specs/2026-04-26-data-layer-design.md` (commit `0f9a344` or later).

**Prerequisites:** Sub-project A is merged on `main` at commit `4ec1e88` or later. Verify with `git log --oneline | head -5` — you should see `Merge Sub-project A (Foundation) into main` near the top.

---

## File map

### New files (production)

| Path | Purpose |
|---|---|
| `app/src/main/java/org/spsl/evtracker/data/local/entity/CarEntity.kt` | Room `@Entity(tableName = "cars")`. 7 fields. PK autoGenerate. |
| `app/src/main/java/org/spsl/evtracker/data/local/entity/ChargeEventEntity.kt` | Room `@Entity(tableName = "charge_events")`. 12 fields. FK to `cars(id)` ON DELETE CASCADE. 3 indices. |
| `app/src/main/java/org/spsl/evtracker/data/local/entity/CustomLocationEntity.kt` | Room `@Entity(tableName = "custom_locations")`. 4 fields. Unique index on `label`. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt` | `interface` with 5 methods: `observeAll`, `getById`, `insert`, `update`, `delete`. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt` | `interface` with 7 methods: `observeForCar`, `getInRange`, `getAllForCarSorted`, `getById`, `insert`, `update`, `delete`. |
| `app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt` | `abstract class` (so it can host `@Transaction` `recordUsage`). 5 abstract methods + 1 transactional `recordUsage` body. |
| `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt` | `@Database(version = 3, exportSchema = true)` with companion holding `MIGRATION_1_2` and `MIGRATION_2_3`. |
| `app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt` | Hilt module providing `@Singleton AppDatabase` + 3 DAO `@Provides`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt` | `@Singleton @Inject` thin façade over `CarDao`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt` | `@Singleton @Inject` thin façade over `ChargeEventDao`. |
| `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt` | `@Singleton @Inject` thin façade over `CustomLocationDao`; default `now = System.currentTimeMillis()`. |

### Modified files (production)

| Path | What changes |
|---|---|
| `app/build.gradle.kts` | Add **top-level** `ksp { arg("room.schemaLocation", …); arg("room.incremental", "true") }` block (NOT nested under `android` or `defaultConfig`). |
| `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt` | Add `activeCarId: Flow<Int>` accessor (default `-1`) and `suspend fun setActiveCarId(id: Int)` writer. |

### New files (tests)

| Path | Type | Cases |
|---|---|---|
| `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CarDaoTest.kt` | Instrumented (in-memory Room) | 3 |
| `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/ChargeEventDaoTest.kt` | Instrumented | 5 |
| `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CustomLocationDaoTest.kt` | Instrumented | 7 |
| `app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt` | Instrumented (raw SQLite + Room validate) | 3 |
| `app/src/androidTest/java/org/spsl/evtracker/data/repository/CarRepositoryTest.kt` | Instrumented | 1 |
| `app/src/androidTest/java/org/spsl/evtracker/data/repository/ChargeEventRepositoryTest.kt` | Instrumented | 2 |
| `app/src/androidTest/java/org/spsl/evtracker/data/repository/LocationRepositoryTest.kt` | Instrumented | 1 |

### Modified files (tests)

| Path | What changes |
|---|---|
| `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt` | Append 1 new test case: `setActiveCarId_persistsAndDefaultsToMinusOne`. |

### New non-source artifacts

| Path | Purpose |
|---|---|
| `app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/3.json` | Auto-generated Room schema export. **Tracked in git** (Room-recommended practice; gives reviewers schema diffs in PRs). |

---

## Notes for the worker

### Sandbox quirks (carryover from A)

- Gradle's default `~/.gradle` is on a read-only filesystem in the sandbox. ALWAYS use `GRADLE_USER_HOME=/tmp/gradle-home` and pass `dangerouslyDisableSandbox: true` to your Bash tool calls when running gradle.
- The Android SDK at `$ANDROID_HOME` is already installed and working from A's setup.
- Per CLAUDE.md: never compound git commands with `&&`/`||`/`;`. Run `git add` and `git commit` as separate Bash calls.

### Room generated-ID handling (critical)

`@Insert` on a `@PrimaryKey(autoGenerate = true)` entity returns the new rowId as a `Long` but **does not mutate the inserted entity's `id` field**. The original instance you passed in still has `id = 0`.

Tests that subsequently call `update(entity)`, `delete(entity)`, or use `entity.id` as a foreign key MUST capture the returned rowId and either re-fetch the persisted entity or reconstruct it with `.copy(id = rowId.toInt())`:

```kotlin
val rowId = carDao.insert(CarEntity(name = "T")).toInt()
val saved = carDao.getById(rowId)!!         // re-fetch…
// …or: val saved = original.copy(id = rowId)
carDao.delete(saved)                         // operates on the right row
chargeEventDao.insert(ChargeEventEntity(carId = rowId, …))   // valid FK
```

Calling `delete(originalLocal)` when the local still has `id = 0` would silently no-op (no row matches `WHERE id = 0`). The cascade FK would NOT fire. Tests would then erroneously pass while the production code path is broken.

This pattern is referenced repeatedly in the test tasks below. If you see `val rowId = …insert(…).toInt()` followed by a `getById` or `.copy(id = rowId)`, that's intentional — do not collapse it.

### KSP block placement (critical)

The schema-export `ksp { … }` configuration goes at the **top level** of `app/build.gradle.kts`, alongside `android { … }` and `dependencies { … }`. It does **not** go under `android.defaultConfig`. The `com.google.devtools.ksp` plugin (already applied in A's build) registers the `ksp` extension at the project level.

```kotlin
// CORRECT — top level
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

```kotlin
// WRONG — would not compile
android {
    defaultConfig {
        ksp { … }   // not a thing
    }
}
```

### Migration index naming

Room auto-generates index names as `index_<table>_<col1>[_<col2>...]`. `MIGRATION_2_3` must use exactly these names or schema validation fails on the next open. Specifically:

- `index_charge_events_carId_eventDate` (composite)
- `index_charge_events_chargeType`
- `index_charge_events_location`
- `index_custom_locations_label` (unique)

### Migration `note` column gotcha

`MIGRATION_2_3` adds the `note` column to `charge_events` with `NOT NULL DEFAULT ''`. A nullable `note TEXT` would not match the entity's non-null `note: String = ""` and Room would refuse to open the DB with a schema mismatch. The `migrate_2_to_3` and `migrate_1_to_3_validatesSchema` tests catch this regression directly.

---

## Task 1: Build config — schema export

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the top-level `ksp { … }` block to `app/build.gradle.kts`**

Open `app/build.gradle.kts`. Locate the existing top-level structure (after the `plugins { … }` block, alongside `android { … }` and `dependencies { … }`). Add this block at the top level (NOT inside any other block):

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

A placement that works: insert it between the closing `}` of the `android { … }` block and the opening of `dependencies { … }`.

- [ ] **Step 2: Verify build still passes**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`. The `app/schemas/` directory will only be created when Room actually generates schemas — which happens once `AppDatabase` exists (Task 4). For now, no `app/schemas/` directory is produced.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
```

```bash
git commit -m "build(data): enable Room schema export to app/schemas/"
```

---

## Task 2: Entities

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/local/entity/CarEntity.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/local/entity/ChargeEventEntity.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/local/entity/CustomLocationEntity.kt`

- [ ] **Step 1: Create `CarEntity.kt`**

```kotlin
package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val batteryKwh: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create `ChargeEventEntity.kt`**

```kotlin
package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charge_events",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["carId", "eventDate"]),
        Index("chargeType"),
        Index("location")
    ]
)
data class ChargeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val carId: Int,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: String = "AC",
    val costTotal: Double? = null,
    val costPerKwh: Double? = null,
    val currency: String? = null,
    val location: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Create `CustomLocationEntity.kt`**

```kotlin
package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_locations",
    indices = [Index(value = ["label"], unique = true)]
)
data class CustomLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
```

- [ ] **Step 4: Verify build**

Entities alone don't trigger Room codegen — that needs an `@Database` class to point at them. So `assembleDebug` will succeed, but no schema JSON is produced yet.

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/local/entity/CarEntity.kt app/src/main/java/org/spsl/evtracker/data/local/entity/ChargeEventEntity.kt app/src/main/java/org/spsl/evtracker/data/local/entity/CustomLocationEntity.kt
```

```bash
git commit -m "feat(data): add Room entities for cars, charge events, custom locations"
```

---

## Task 3: DAOs

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt`

- [ ] **Step 1: Create `CarDao.kt`**

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CarEntity

@Dao
interface CarDao {

    @Query("SELECT * FROM cars ORDER BY name")
    fun observeAll(): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getById(id: Int): CarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(car: CarEntity): Long

    @Update
    suspend fun update(car: CarEntity)

    @Delete
    suspend fun delete(car: CarEntity)
}
```

- [ ] **Step 2: Create `ChargeEventDao.kt`**

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@Dao
interface ChargeEventDao {

    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>>

    @Query(
        "SELECT * FROM charge_events " +
            "WHERE carId = :carId AND eventDate BETWEEN :from AND :to " +
            "ORDER BY eventDate ASC"
    )
    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity>

    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity>

    @Query("SELECT * FROM charge_events WHERE id = :id")
    suspend fun getById(id: Int): ChargeEventEntity?

    @Insert
    suspend fun insert(event: ChargeEventEntity): Long

    @Update
    suspend fun update(event: ChargeEventEntity)

    @Delete
    suspend fun delete(event: ChargeEventEntity)
}
```

Note: no `onConflict = REPLACE` on `insert` — `OnConflictStrategy.ABORT` is the default. Charge events are append-only-by-design; edits go through `@Update`.

- [ ] **Step 3: Create `CustomLocationDao.kt`**

This DAO is an `abstract class` (not `interface`) because it hosts a `@Transaction` suspend function with a body. Room generates the abstract methods but lets you write the transactional method directly.

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Dao
abstract class CustomLocationDao {

    @Query("SELECT * FROM custom_locations ORDER BY useCount DESC, lastUsed DESC LIMIT 5")
    abstract fun observeTop5(): Flow<List<CustomLocationEntity>>

    @Query("SELECT * FROM custom_locations ORDER BY useCount DESC, lastUsed DESC")
    abstract fun observeAll(): Flow<List<CustomLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(location: CustomLocationEntity): Long

    @Query("UPDATE custom_locations SET useCount = useCount + 1, lastUsed = :now WHERE label = :label")
    abstract suspend fun incrementUseCount(label: String, now: Long)

    @Delete
    abstract suspend fun delete(location: CustomLocationEntity)

    @Transaction
    open suspend fun recordUsage(label: String, now: Long) {
        val rowId = insertIfMissing(
            CustomLocationEntity(label = label, useCount = 1, lastUsed = now)
        )
        if (rowId == -1L) {
            // Insert was IGNORED because the label already exists — bump the counter.
            incrementUseCount(label, now)
        }
    }
}
```

The `rowId == -1L` guard is essential. Without it, a brand-new label would end up at `useCount = 2` after a single `recordUsage` call (insert sets useCount=1, then incrementUseCount bumps to 2). The CustomLocationDaoTest cases in Task 8 verify this behavior.

- [ ] **Step 4: Verify build**

Without an `@Database` referencing them, the DAOs compile but don't trigger Room codegen.

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/local/dao/CarDao.kt app/src/main/java/org/spsl/evtracker/data/local/dao/ChargeEventDao.kt app/src/main/java/org/spsl/evtracker/data/local/dao/CustomLocationDao.kt
```

```bash
git commit -m "feat(data): add CarDao, ChargeEventDao, CustomLocationDao"
```

---

## Task 4: AppDatabase + migrations

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt`

- [ ] **Step 1: Create `AppDatabase.kt`**

```kotlin
package org.spsl.evtracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Database(
    entities = [
        CarEntity::class,
        ChargeEventEntity::class,
        CustomLocationEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun chargeEventDao(): ChargeEventDao
    abstract fun customLocationDao(): CustomLocationDao

    companion object {

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_events " +
                        "ADD COLUMN chargeType TEXT NOT NULL DEFAULT 'AC'"
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema changes to charge_events.
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costTotal REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costPerKwh REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN currency TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN location TEXT")
                db.execSQL(
                    "ALTER TABLE charge_events " +
                        "ADD COLUMN note TEXT NOT NULL DEFAULT ''"
                )

                // New custom_locations table.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_locations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "useCount INTEGER NOT NULL DEFAULT 1, " +
                        "lastUsed INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_locations_label " +
                        "ON custom_locations(label)"
                )

                // Indices on charge_events. IF NOT EXISTS keeps each statement
                // idempotent regardless of which version's createAllTables produced
                // the existing table.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_carId_eventDate " +
                        "ON charge_events(carId, eventDate)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_chargeType " +
                        "ON charge_events(chargeType)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_location " +
                        "ON charge_events(location)"
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify build — Room codegen now runs**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`. Generated files appear under `app/build/generated/ksp/debug/kotlin/org/spsl/evtracker/data/local/db/AppDatabase_Impl.kt`.

- [ ] **Step 3: Verify schema export landed**

```bash
ls app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/
```

Expected: a file named `3.json` exists. Inspect it with `head -20 app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/3.json` — it should be valid JSON describing the v3 schema.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/local/db/AppDatabase.kt app/schemas/
```

```bash
git commit -m "feat(data): add AppDatabase v3 with MIGRATION_1_2 and MIGRATION_2_3"
```

---

## Task 5: DatabaseModule

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt`

- [ ] **Step 1: Create `DatabaseModule.kt`**

```kotlin
package org.spsl.evtracker.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.db.AppDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "evtracker.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideCarDao(db: AppDatabase): CarDao = db.carDao()

    @Provides
    fun provideChargeEventDao(db: AppDatabase): ChargeEventDao = db.chargeEventDao()

    @Provides
    fun provideCustomLocationDao(db: AppDatabase): CustomLocationDao = db.customLocationDao()
}
```

DAO providers are deliberately not `@Singleton` — Room internally caches DAO instances on the `RoomDatabase`.

- [ ] **Step 2: Verify build (Hilt component graph still resolves)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`. Hilt code generation now includes `AppDatabase`, `CarDao`, `ChargeEventDao`, `CustomLocationDao` as injectable types.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/di/DatabaseModule.kt
```

```bash
git commit -m "feat(data): add Hilt DatabaseModule providing AppDatabase + DAOs"
```

---

## Task 6: CarDaoTest

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CarDaoTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity

@RunWith(AndroidJUnit4::class)
class CarDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var carDao: CarDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        carDao = db.carDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertCar_idIsAssigned() = runBlocking {
        val rowId = carDao.insert(CarEntity(name = "T"))
        assertTrue("rowId must be > 0; got $rowId", rowId > 0)

        val saved = carDao.getById(rowId.toInt())
        assertNotNull(saved)
        assertEquals("T", saved!!.name)
    }

    @Test
    fun updateCar_changesPersist() = runBlocking {
        val rowId = carDao.insert(CarEntity(name = "T")).toInt()
        // Re-fetch — the original local still has id = 0 and would silently no-op on update.
        val saved = carDao.getById(rowId)!!

        carDao.update(saved.copy(name = "T2"))

        val list = carDao.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("T2", list.single().name)
    }

    @Test
    fun deleteCar_removesFromList() = runBlocking {
        val id1 = carDao.insert(CarEntity(name = "A")).toInt()
        val id2 = carDao.insert(CarEntity(name = "B")).toInt()
        // Re-fetch before delete; original local has id = 0.
        carDao.delete(carDao.getById(id1)!!)

        val remaining = carDao.observeAll().first()
        assertEquals(listOf(id2), remaining.map { it.id })
    }
}
```

- [ ] **Step 2: Verify the test compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: (Optional, only if an emulator is running) Execute the tests**

If a device or emulator is connected, run:

`GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:connectedDebugAndroidTest --tests "org.spsl.evtracker.data.local.dao.CarDaoTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.

Expected: `BUILD SUCCESSFUL`. Test report at `app/build/outputs/androidTest-results/connected/.../*.xml` should show `tests="3" failures="0" errors="0"`.

If no device is connected, skip this step — the controller will run the full suite at Task 14.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CarDaoTest.kt
```

```bash
git commit -m "test(data): add CarDaoTest covering insert, update, delete"
```

---

## Task 7: ChargeEventDaoTest

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/ChargeEventDaoTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@RunWith(AndroidJUnit4::class)
class ChargeEventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var carDao: CarDao
    private lateinit var chargeEventDao: ChargeEventDao
    private var carId: Int = 0

    private val now: Long get() = System.currentTimeMillis()
    private val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        carDao = db.carDao()
        chargeEventDao = db.chargeEventDao()
        carId = carDao.insert(CarEntity(name = "T")).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun event(
        eventDate: Long = now,
        odometerKm: Double = 1000.0,
        kwhAdded: Double = 10.0
    ) = ChargeEventEntity(
        carId = carId,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded
    )

    @Test
    fun insertAndRetrieve_returnsAllForCar() = runBlocking {
        chargeEventDao.insert(event(eventDate = now - 3 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = now - 2 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = now - 1 * MILLIS_PER_DAY))

        val list = chargeEventDao.observeForCar(carId).first()
        assertEquals(3, list.size)
    }

    @Test
    fun cascadeDelete_deletesEventsWhenCarDeleted() = runBlocking {
        chargeEventDao.insert(event())
        chargeEventDao.insert(event())
        chargeEventDao.insert(event())

        // Re-fetch the car before delete; the original local has id = 0.
        carDao.delete(carDao.getById(carId)!!)

        val list = chargeEventDao.observeForCar(carId).first()
        assertTrue("expected events cascade-deleted, got ${list.size}", list.isEmpty())
    }

    @Test
    fun getInRange_filtersOutOfRange() = runBlocking {
        val t = now
        chargeEventDao.insert(event(eventDate = t - 7 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = t - 15 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = t - 45 * MILLIS_PER_DAY))

        val last30 = chargeEventDao.getInRange(carId, t - 30 * MILLIS_PER_DAY, t)
        assertEquals(2, last30.size)
    }

    @Test
    fun observeForCar_isSortedByEventDateAsc() = runBlocking {
        val t = now
        chargeEventDao.insert(event(eventDate = t - 1 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = t - 5 * MILLIS_PER_DAY))
        chargeEventDao.insert(event(eventDate = t - 3 * MILLIS_PER_DAY))

        val list = chargeEventDao.observeForCar(carId).first()
        val dates = list.map { it.eventDate }
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val eventRowId = chargeEventDao.insert(
            event(odometerKm = 100.0, kwhAdded = 10.0)
        ).toInt()
        // Re-fetch — the original local has id = 0.
        val saved = chargeEventDao.getById(eventRowId)!!

        chargeEventDao.update(saved.copy(kwhAdded = 12.5, note = "topped up"))

        val refetched = chargeEventDao.getById(eventRowId)!!
        assertEquals(12.5, refetched.kwhAdded, 0.0)
        assertEquals("topped up", refetched.note)
    }
}
```

- [ ] **Step 2: Verify the test compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/local/dao/ChargeEventDaoTest.kt
```

```bash
git commit -m "test(data): add ChargeEventDaoTest covering insert/range/sort/update/cascade"
```

---

## Task 8: CustomLocationDaoTest

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CustomLocationDaoTest.kt`

- [ ] **Step 1: Create the test file**

```kotlin
package org.spsl.evtracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@RunWith(AndroidJUnit4::class)
class CustomLocationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CustomLocationDao

    private val now: Long get() = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.customLocationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertIfMissing_newLabel_stored() = runBlocking {
        dao.insertIfMissing(CustomLocationEntity(label = "Home", lastUsed = now))

        val list = dao.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("Home", list.single().label)
        assertEquals(1, list.single().useCount)
    }

    @Test
    fun insertIfMissing_duplicate_returnsMinusOne() = runBlocking {
        val first = dao.insertIfMissing(CustomLocationEntity(label = "Home", lastUsed = now))
        val second = dao.insertIfMissing(CustomLocationEntity(label = "Home", lastUsed = now + 1))

        assertTrue("first insert should produce a real rowId; got $first", first > 0)
        assertEquals(-1L, second)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun recordUsage_increments_existingLabel() = runBlocking {
        val now1 = now
        val now2 = now1 + 1000
        dao.recordUsage("Home", now1)
        dao.recordUsage("Home", now2)

        val entry = dao.observeAll().first().single()
        assertEquals(2, entry.useCount)
        assertEquals(now2, entry.lastUsed)
    }

    @Test
    fun recordUsage_freshLabel_useCountIsOne() = runBlocking {
        dao.recordUsage("Work", now)

        val entry = dao.observeAll().first().single()
        assertEquals(1, entry.useCount)
    }

    @Test
    fun getTopLocations_maxFive() = runBlocking {
        val labels = listOf("L1", "L2", "L3", "L4", "L5", "L6", "L7", "L8")
        labels.forEach { dao.recordUsage(it, now) }

        val top = dao.observeTop5().first()
        assertEquals(5, top.size)
    }

    @Test
    fun getTopLocations_sortedByUseCount() = runBlocking {
        repeat(5) { dao.recordUsage("A", now) }
        repeat(4) { dao.recordUsage("B", now) }
        repeat(3) { dao.recordUsage("C", now) }
        repeat(2) { dao.recordUsage("D", now) }
        repeat(1) { dao.recordUsage("E", now) }

        val top = dao.observeTop5().first()
        assertEquals(listOf("A", "B", "C", "D", "E"), top.map { it.label })
    }

    @Test
    fun delete_removesEntry() = runBlocking {
        dao.recordUsage("Home", now)
        // Re-fetch — the entity built inside recordUsage had id = 0 and is unreachable.
        val saved = dao.observeAll().first().single()

        dao.delete(saved)

        assertTrue(dao.observeAll().first().isEmpty())
    }
}
```

- [ ] **Step 2: Verify the test compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/local/dao/CustomLocationDaoTest.kt
```

```bash
git commit -m "test(data): add CustomLocationDaoTest covering recordUsage, top-5, delete"
```

---

## Task 9: MigrationTest

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt`

- [ ] **Step 1: Create the test file**

This test does NOT use `MigrationTestHelper` (which requires schema JSON files for each version, but v1 and v2 entities were never declared). Instead, it builds a v1 DB via raw SQL through `SupportSQLiteOpenHelper`, then re-opens the DB through `Room.databaseBuilder(...)` with the migrations registered. Room runs the migrations and validates the resulting schema against the v3 entity declarations — if validation fails, Room throws `IllegalStateException`.

```kotlin
package org.spsl.evtracker.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Always start clean.
        context.deleteDatabase(testDbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
    }

    /** Builds a v1 DB at [testDbName] using raw SQL matching the v1 entity schema. */
    private fun buildV1Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cars (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "make TEXT NOT NULL DEFAULT '', " +
                        "model TEXT NOT NULL DEFAULT '', " +
                        "year INTEGER, " +
                        "batteryKwh REAL, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS charge_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "carId INTEGER NOT NULL, " +
                        "eventDate INTEGER NOT NULL, " +
                        "odometerKm REAL NOT NULL, " +
                        "kwhAdded REAL NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE" +
                        ")"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // No upgrades; this helper only ever opens at v1.
            }
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(callback)
                .build()
        )
        return helper.writableDatabase
    }

    /** Builds a v2 DB at [testDbName] (= v1 + chargeType column). */
    private fun buildV2Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS cars (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "make TEXT NOT NULL DEFAULT '', " +
                        "model TEXT NOT NULL DEFAULT '', " +
                        "year INTEGER, " +
                        "batteryKwh REAL, " +
                        "createdAt INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS charge_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "carId INTEGER NOT NULL, " +
                        "eventDate INTEGER NOT NULL, " +
                        "odometerKm REAL NOT NULL, " +
                        "kwhAdded REAL NOT NULL, " +
                        "chargeType TEXT NOT NULL DEFAULT 'AC', " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE" +
                        ")"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // No upgrades; this helper only ever opens at v2.
            }
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(callback)
                .build()
        )
        return helper.writableDatabase
    }

    /** Opens [testDbName] via Room with the migrations registered, forcing schema validation. */
    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Test
    fun migrate_1_to_2() = runBlocking {
        val v1 = buildV1Database()
        v1.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v1.execSQL(
            "INSERT INTO charge_events (carId, eventDate, odometerKm, kwhAdded, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 2000)"
        )

        // Run MIGRATION_1_2 directly against the v1 raw SQLite DB — isolated, no
        // MIGRATION_2_3 and no Room schema validation.
        AppDatabase.MIGRATION_1_2.migrate(v1)

        // Verify chargeType column was added with the 'AC' default value applied to
        // pre-existing rows.
        v1.query("SELECT chargeType FROM charge_events WHERE id = 1").use { cursor ->
            assertTrue("expected one row in charge_events", cursor.moveToFirst())
            assertEquals("AC", cursor.getString(0))
        }
        v1.close()
    }

    @Test
    fun migrate_2_to_3() = runBlocking {
        val v2 = buildV2Database()
        v2.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v2.execSQL(
            "INSERT INTO charge_events " +
                "(carId, eventDate, odometerKm, kwhAdded, chargeType, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 'DC', 2000)"
        )

        // Run MIGRATION_2_3 directly — isolated, no Room schema validation.
        AppDatabase.MIGRATION_2_3.migrate(v2)

        // Verify new columns exist on pre-existing rows with their migration defaults.
        v2.query("SELECT chargeType, costTotal, note FROM charge_events WHERE id = 1")
            .use { cursor ->
                assertTrue("expected one row in charge_events", cursor.moveToFirst())
                assertEquals("DC", cursor.getString(0))     // unchanged from v2
                assertTrue("costTotal should be NULL", cursor.isNull(1))
                assertEquals("", cursor.getString(2))         // NOT NULL DEFAULT ''
            }

        // Verify the new custom_locations table exists and is empty.
        v2.query("SELECT COUNT(*) FROM custom_locations").use { cursor ->
            assertTrue("expected COUNT(*) row", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v2.close()
    }

    @Test
    fun migrate_1_to_3_validatesSchema() = runBlocking {
        val v1 = buildV1Database()
        v1.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v1.execSQL(
            "INSERT INTO charge_events (carId, eventDate, odometerKm, kwhAdded, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 2000)"
        )
        v1.close()

        // Open through Room with both migrations registered. Room runs MIGRATION_1_2
        // then MIGRATION_2_3, then validates the resulting schema against v3 entity
        // declarations. If column names, indices, or defaults drift, Room throws
        // IllegalStateException.
        val room = openWithRoom()
        try {
            val event = room.chargeEventDao().getAllForCarSorted(1).single()
            assertEquals("AC", event.chargeType)  // from MIGRATION_1_2 default
            assertEquals(null, event.costTotal)   // from MIGRATION_2_3 add column
            assertEquals("", event.note)          // from MIGRATION_2_3 add column NOT NULL DEFAULT ''
        } finally {
            room.close()
        }
    }
}
```

- [ ] **Step 2: Verify the test compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/local/db/MigrationTest.kt
```

```bash
git commit -m "test(data): add MigrationTest for v1→v2, v2→v3, and v1→v3 schema validation"
```

---

## Task 10: CarRepository (TDD)

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/repository/CarRepositoryTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt`

- [ ] **Step 1: Write the failing test first**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@RunWith(AndroidJUnit4::class)
class CarRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var carRepository: CarRepository
    private lateinit var chargeEventRepository: ChargeEventRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        carRepository = CarRepository(db.carDao())
        chargeEventRepository = ChargeEventRepository(db.chargeEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteCar_cascadesEvents_throughRepository() = runBlocking {
        val carId = carRepository.insert(CarEntity(name = "T")).toInt()
        repeat(3) {
            chargeEventRepository.insert(
                ChargeEventEntity(
                    carId = carId,
                    eventDate = System.currentTimeMillis(),
                    odometerKm = 100.0 * (it + 1),
                    kwhAdded = 10.0
                )
            )
        }

        // Re-fetch the car before delete; original local has id = 0.
        carRepository.delete(carRepository.getById(carId)!!)

        val remaining = chargeEventRepository.observeForCar(carId).first()
        assertTrue("expected events cascade-deleted, got ${remaining.size}", remaining.isEmpty())
    }
}
```

- [ ] **Step 2: Verify the test fails to compile (`CarRepository` and `ChargeEventRepository` don't exist yet)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: compilation failure — `CarRepository` and `ChargeEventRepository` are unresolved.

This is fine — both repos land within the next two tasks. To make the build pass progressively, this task adds `CarRepository`. Task 11 adds `ChargeEventRepository`. The test will compile after Task 11.

Actually for a single-task self-contained commit, we'll create `ChargeEventRepository` here too as a placeholder so the test compiles. Then Task 11 adds the rest of `ChargeEventRepository`'s methods (already complete in the placeholder, so Task 11 just adds tests).

- [ ] **Step 3: Implement `CarRepository`**

```kotlin
// app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt
package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.entity.CarEntity

@Singleton
class CarRepository @Inject constructor(
    private val carDao: CarDao
) {
    fun observeAll(): Flow<List<CarEntity>> = carDao.observeAll()
    suspend fun getById(id: Int): CarEntity? = carDao.getById(id)
    suspend fun insert(car: CarEntity): Long = carDao.insert(car)
    suspend fun update(car: CarEntity) = carDao.update(car)
    suspend fun delete(car: CarEntity) = carDao.delete(car)
}
```

- [ ] **Step 4: Implement `ChargeEventRepository` (full surface, even though only one method is exercised by Task 10's test — Task 11 just adds tests)**

```kotlin
// app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt
package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@Singleton
class ChargeEventRepository @Inject constructor(
    private val chargeEventDao: ChargeEventDao
) {
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>> =
        chargeEventDao.observeForCar(carId)

    suspend fun getInRange(carId: Int, from: Long, to: Long): List<ChargeEventEntity> =
        chargeEventDao.getInRange(carId, from, to)

    suspend fun getAllForCarSorted(carId: Int): List<ChargeEventEntity> =
        chargeEventDao.getAllForCarSorted(carId)

    suspend fun getById(id: Int): ChargeEventEntity? = chargeEventDao.getById(id)
    suspend fun insert(event: ChargeEventEntity): Long = chargeEventDao.insert(event)
    suspend fun update(event: ChargeEventEntity) = chargeEventDao.update(event)
    suspend fun delete(event: ChargeEventEntity) = chargeEventDao.delete(event)
}
```

- [ ] **Step 5: Verify the build now compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug compileDebugAndroidTestKotlin 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/CarRepository.kt app/src/main/java/org/spsl/evtracker/data/repository/ChargeEventRepository.kt app/src/androidTest/java/org/spsl/evtracker/data/repository/CarRepositoryTest.kt
```

```bash
git commit -m "feat(data): add CarRepository + ChargeEventRepository, with CarRepositoryTest"
```

---

## Task 11: ChargeEventRepository tests

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/repository/ChargeEventRepositoryTest.kt`

The repository itself was created in Task 10; this task only adds tests.

- [ ] **Step 1: Create the test file**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@RunWith(AndroidJUnit4::class)
class ChargeEventRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var carRepository: CarRepository
    private lateinit var chargeEventRepository: ChargeEventRepository

    private val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        carRepository = CarRepository(db.carDao())
        chargeEventRepository = ChargeEventRepository(db.chargeEventDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeForCar_emitsOnlyForGivenCar() = runBlocking {
        val carAId = carRepository.insert(CarEntity(name = "A")).toInt()
        val carBId = carRepository.insert(CarEntity(name = "B")).toInt()

        val now = System.currentTimeMillis()
        repeat(3) {
            chargeEventRepository.insert(
                ChargeEventEntity(
                    carId = carAId,
                    eventDate = now - it * MILLIS_PER_DAY,
                    odometerKm = 100.0,
                    kwhAdded = 10.0
                )
            )
        }
        repeat(2) {
            chargeEventRepository.insert(
                ChargeEventEntity(
                    carId = carBId,
                    eventDate = now - it * MILLIS_PER_DAY,
                    odometerKm = 200.0,
                    kwhAdded = 15.0
                )
            )
        }

        assertEquals(3, chargeEventRepository.observeForCar(carAId).first().size)
        assertEquals(2, chargeEventRepository.observeForCar(carBId).first().size)
    }

    @Test
    fun getInRange_excludesOutOfRange() = runBlocking {
        val carId = carRepository.insert(CarEntity(name = "C")).toInt()
        val t = System.currentTimeMillis()

        chargeEventRepository.insert(eventAt(carId, t - 5 * MILLIS_PER_DAY))
        chargeEventRepository.insert(eventAt(carId, t - 20 * MILLIS_PER_DAY))
        chargeEventRepository.insert(eventAt(carId, t - 40 * MILLIS_PER_DAY))
        chargeEventRepository.insert(eventAt(carId, t - 60 * MILLIS_PER_DAY))

        val last30 = chargeEventRepository.getInRange(
            carId,
            t - 30 * MILLIS_PER_DAY,
            t
        )
        assertEquals(2, last30.size)
    }

    private fun eventAt(carId: Int, eventDate: Long) = ChargeEventEntity(
        carId = carId,
        eventDate = eventDate,
        odometerKm = 100.0,
        kwhAdded = 10.0
    )
}
```

- [ ] **Step 2: Verify the test compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/org/spsl/evtracker/data/repository/ChargeEventRepositoryTest.kt
```

```bash
git commit -m "test(data): add ChargeEventRepositoryTest for observeForCar + getInRange"
```

---

## Task 12: LocationRepository (TDD)

**Files:**
- Create: `app/src/androidTest/java/org/spsl/evtracker/data/repository/LocationRepositoryTest.kt`
- Create: `app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt`

- [ ] **Step 1: Write the failing test first**

```kotlin
package org.spsl.evtracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase

@RunWith(AndroidJUnit4::class)
class LocationRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        locationRepository = LocationRepository(db.customLocationDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recordUsage_isAtomicAndIncrementsOnSecondCall() = runBlocking {
        val now1 = 1000L
        val now2 = 2000L

        locationRepository.recordUsage("Home", now1)
        locationRepository.recordUsage("Home", now2)

        val list = locationRepository.observeTop5().first()
        assertEquals(1, list.size)
        val entry = list.single()
        assertEquals("Home", entry.label)
        assertEquals(2, entry.useCount)
        assertEquals(now2, entry.lastUsed)
    }
}
```

- [ ] **Step 2: Verify the test fails to compile (`LocationRepository` doesn't exist yet)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: compilation failure — `LocationRepository` is unresolved.

- [ ] **Step 3: Implement `LocationRepository`**

```kotlin
// app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt
package org.spsl.evtracker.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Singleton
class LocationRepository @Inject constructor(
    private val customLocationDao: CustomLocationDao
) {
    fun observeTop5(): Flow<List<CustomLocationEntity>> = customLocationDao.observeTop5()
    fun observeAll(): Flow<List<CustomLocationEntity>> = customLocationDao.observeAll()

    suspend fun recordUsage(label: String, now: Long = System.currentTimeMillis()) =
        customLocationDao.recordUsage(label, now)

    suspend fun delete(location: CustomLocationEntity) = customLocationDao.delete(location)
}
```

- [ ] **Step 4: Verify the build now compiles cleanly**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/LocationRepository.kt app/src/androidTest/java/org/spsl/evtracker/data/repository/LocationRepositoryTest.kt
```

```bash
git commit -m "feat(data): add LocationRepository with recordUsage atomicity test"
```

---

## Task 13: SettingsRepository extension — `activeCarId`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`
- Modify: `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`

This task is TDD-style: first add the failing JVM test case, watch it fail because the accessor doesn't exist, then add the accessor + writer.

- [ ] **Step 1: Append the failing test case to `SettingsRepositoryTest.kt`**

In `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`, find the `@Test fun resetSetupComplete_flipsFlag_butLeavesOtherKeysAlone()` method. After its closing brace, append a new `@Test`:

```kotlin
    @Test
    fun setActiveCarId_persistsAndDefaultsToMinusOne() = runTest {
        // Default when key has never been written.
        assertEquals(-1, repo.activeCarId.first())

        // Round-trip a non-default value.
        repo.setActiveCarId(42)
        assertEquals(42, repo.activeCarId.first())
    }
```

- [ ] **Step 2: Verify the test fails to compile (`activeCarId` and `setActiveCarId` don't exist yet)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.data.repository.SettingsRepositoryTest" 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: compilation failure — `activeCarId` and `setActiveCarId` are unresolved on `SettingsRepository`.

- [ ] **Step 3: Add the accessor + writer to `SettingsRepository.kt`**

Open `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`. Add these two members anywhere inside the class (a sensible spot is after the existing `theme: Flow<String>` accessor and before the `completeSetup` writer; or after `setTheme` for the writer):

```kotlin
    val activeCarId: Flow<Int> =
        dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

    suspend fun setActiveCarId(id: Int) {
        dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id }
    }
```

The `PreferenceKeys.ACTIVE_CAR_ID` import is already in scope (the file imports `PreferenceKeys`).

- [ ] **Step 4: Run the JVM test suite — all 14 tests pass**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

Verify the count:
```bash
find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h "tests=" {} \; | head
```

Expected output includes `tests="5"` for `SettingsRepositoryTest` (4 from A + 1 new = 5) and `tests="9"` for `WizardViewModelTest`. Total: 14.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt
```

```bash
git commit -m "feat(data): add activeCarId accessor + setActiveCarId writer to SettingsRepository"
```

---

## Task 14: Final acceptance verification

- [ ] **Step 1: Build the debug APK**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew assembleDebug 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Run all JVM unit tests — should be 14 total (13 from A + 1 from B)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest 2>&1 | tail -10` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

Aggregate test count:
```bash
find app/build/test-results/testDebugUnitTest -name "*.xml" -exec grep -h "tests=" {} \;
```

Expected: `SettingsRepositoryTest tests="5"` and `WizardViewModelTest tests="9"`, all `failures="0"` `errors="0"`.

- [ ] **Step 3: Compile-check the full instrumented suite (no emulator needed)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5` with `dangerouslyDisableSandbox: true`.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: (Optional) Run the full instrumented suite on an emulator**

If a device or emulator is running, execute:

`GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:connectedDebugAndroidTest 2>&1 | tail -15` with `dangerouslyDisableSandbox: true`.

Expected: `BUILD SUCCESSFUL`. Test report at `app/build/outputs/androidTest-results/connected/.../*.xml` should show 25 tests, 0 failures, 0 errors:

- `WizardFlowTest` (3, from A)
- `CarDaoTest` (3)
- `ChargeEventDaoTest` (5)
- `CustomLocationDaoTest` (7)
- `MigrationTest` (3)
- `CarRepositoryTest` (1)
- `ChargeEventRepositoryTest` (2)
- `LocationRepositoryTest` (1)

Total: 3 + 3 + 5 + 7 + 3 + 1 + 2 + 1 = **25 instrumented tests**.

If no device is available, this step is skipped — the controller will run the suite when an emulator is available.

- [ ] **Step 5: Confirm Sub-project B is complete**

If steps 1–3 all pass, Sub-project B meets its acceptance criteria. No additional commit — this is verification only.

---

## Coverage check (spec → tasks)

| Spec section | Implemented in task |
|---|---|
| §3 Architecture (file map) | Tasks 2–5, 10, 11, 12, 13 |
| §4.1 CarEntity | Task 2 |
| §4.2 ChargeEventEntity | Task 2 |
| §4.3 CustomLocationEntity | Task 2 |
| §5.1 MIGRATION_1_2 | Task 4 |
| §5.2 MIGRATION_2_3 (with all 3 charge_events indices) | Task 4 |
| §5.3 Why all 3 indices in MIGRATION_2_3 | Task 4 (implementation matches the rationale) |
| §5.4 Migration test strategy | Task 9 |
| §6.1 CarDao | Task 3 |
| §6.2 ChargeEventDao | Task 3 |
| §6.3 CustomLocationDao with @Transaction recordUsage | Task 3 |
| §7.1 CarRepository | Task 10 |
| §7.2 ChargeEventRepository | Task 10 |
| §7.3 LocationRepository | Task 12 |
| §7.4 SettingsRepository extension | Task 13 |
| §8 DatabaseModule | Task 5 |
| §9 Build configuration (top-level ksp{}) | Task 1 |
| §10.1 CarDaoTest | Task 6 |
| §10.1 ChargeEventDaoTest | Task 7 |
| §10.1 CustomLocationDaoTest | Task 8 |
| §10.1 MigrationTest | Task 9 |
| §10.1 CarRepositoryTest | Task 10 |
| §10.1 ChargeEventRepositoryTest | Task 11 |
| §10.1 LocationRepositoryTest | Task 12 |
| §10.2 SettingsRepositoryTest extension | Task 13 |
| §2.3 Acceptance criteria 1–3 | Task 14 |
