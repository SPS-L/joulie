# Sub-project B: Data Layer — Design

**Date:** 2026-04-26
**Status:** Draft, awaiting user review
**Sources of truth this design defers to:** `DESIGN.md §4` (data model + version history), `AGENT_INSTRUCTIONS.md §3-4` (entities, DAOs, repositories), `TEST_PLAN.md §2` (DAO + migration tests), `CLAUDE.md` (column-naming and migration invariants). Where this design narrows scope or makes specific implementation choices, those choices override the broader docs *for Sub-project B only*.

---

## 1. Context — what landed in A, what B picks up

### 1.1 Prerequisites

This spec assumes Sub-project A is already merged into `main`. At the time of writing, that was commit `4ec1e88` ("Merge Sub-project A (Foundation) into main"). The acceptance criteria, file map, and "extends existing X" references throughout this document are **additive on top of that state** — they are not standalone instructions for a fresh repo.

If the workspace is at any state earlier than `4ec1e88` (for example, on a branch that hasn't merged A yet, or on a fork that started before A landed), this design cannot be applied as-is — you'll be missing `EVTrackerApp`, `MainActivity`, `SettingsRepository`, the wizard, and the Hilt scaffolding that B builds on.

### 1.2 What A delivered

Sub-project A shipped a buildable, launchable app with Hilt + DataStore + Navigation Component, a functional 3-page first-boot wizard, the `MainActivity` wizard gate, and 7 placeholder destination Fragments. **No Room code exists yet** — A deliberately deferred the entire data layer to this sub-project so the wizard could be designed and tested in isolation.

### 1.3 What B introduces

Sub-project B introduces the Room v3 database with three entities, three DAOs, two migrations, and three narrow repositories. It also extends `SettingsRepository` with the `activeCarId` accessor that was deferred from A's narrower repository surface.

Sub-projects C (domain core), D (Dashboard/ChargeEdit/Cars/History UI), E (Drive backup), and F (Charts/CSV/Settings) all depend on B.

---

## 2. Scope and acceptance criteria

### 2.1 In scope

- Three Room entities under `data/local/entity/`: `CarEntity`, `ChargeEventEntity`, `CustomLocationEntity` — verbatim from `AGENT_INSTRUCTIONS.md §3.1`.
- Three DAOs under `data/local/dao/`: `CarDao`, `ChargeEventDao`, `CustomLocationDao` — narrow query/insert/update/delete surfaces; all multi-row queries return `Flow<List<...>>`, all writes are `suspend`.
- `AppDatabase` at `data/local/db/AppDatabase.kt`: `@Database(version = 3, exportSchema = true)`, registered with `MIGRATION_1_2` and `MIGRATION_2_3`.
- Three repositories under `data/repository/`: `CarRepository`, `ChargeEventRepository`, `LocationRepository`. All carId-parameterized — **no `SettingsRepository` injection in any of them** (active-car coupling deferred to use cases in Sub-project C).
- `SettingsRepository` extension (in the existing file): `activeCarId: Flow<Int>` (default `-1`) and `setActiveCarId(id: Int)`.
- New Hilt module `di/DatabaseModule.kt` providing `AppDatabase` + the three DAOs.
- Schema export enabled to `app/schemas/` for IDE inspection and PR review of schema changes.

### 2.2 Out of scope (deferred)

| Concern | Lands in |
|---|---|
| `PagingSource` for History pagination | D (when the History screen wires pagination) |
| `DeleteCarUseCase` (orchestrating active-car reset on car delete) | C |
| Active-car coupling in any repository | C (use-case layer) |
| `driveEnabled` Flow accessor in `SettingsRepository` | E |
| JVM-side fake repositories | Whichever sub-project first wants them |
| Domain models distinct from entities for car/event/location | None of the planned sub-projects need them; entities double as domain types |
| Stats, cost parsing, unit conversion, backup serialization | C / E |
| Real Dashboard / ChargeEdit / Cars / History UI | D |
| `SettingsLocalDataSource` (mentioned in `AGENT_INSTRUCTIONS.md §3.3` but never introduced) | Not coming back in B. Sub-project A explicitly dropped it as YAGNI when `SettingsRepository` had only one DataStore consumer; B keeps the same posture — adding the `activeCarId` accessor still keeps `SettingsRepository` thin enough that an intermediate data source layer would be a no-op pass-through. If a second consumer appears later (e.g., a non-DataStore preferences backend), the sub-project that needs it can introduce the abstraction. |

### 2.3 Acceptance criteria

1. `./gradlew assembleDebug` succeeds.
2. `./gradlew test` passes — existing 13 JVM tests + 1 new = **14 JVM tests**.
3. `./gradlew connectedDebugAndroidTest` is **expected** to pass on a connected emulator (compile-only verification in this development environment) — **24 instrumented tests** total (3 from A's `WizardFlowTest` + 21 new from B; see §10.1 for the breakdown).
4. Manual smoke (carryover from A): wizard gate still works on first launch; dashboard placeholder still appears post-wizard. Room v3 DB is created automatically on first launch via `createAllTables` (no migration runs because v3 is the first version this app ever ships).

---

## 3. Architecture

```
app/src/main/java/org/spsl/evtracker/
  data/
    local/
      entity/
        CarEntity.kt                 7 fields; PK autoGenerate
        ChargeEventEntity.kt         12 fields; FK to cars(id) ON DELETE CASCADE; 3 indices
        CustomLocationEntity.kt      4 fields; unique index on label
      dao/
        CarDao.kt
        ChargeEventDao.kt
        CustomLocationDao.kt
      db/
        AppDatabase.kt               @Database(version = 3); MIGRATION_1_2, MIGRATION_2_3
    repository/
      CarRepository.kt               (new) CRUD; observe ordered list
      ChargeEventRepository.kt       (new) carId-parameterized queries; CRUD
      LocationRepository.kt          (new) top-5, full list, recordUsage, delete
      SettingsRepository.kt          (existing) + activeCarId Flow + setActiveCarId
  di/
    AppModule.kt                     (existing; unchanged)
    DatabaseModule.kt                (new) provides AppDatabase + 3 DAOs
```

Plus tests under `app/src/androidTest/java/org/spsl/evtracker/data/...` and an extension to `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`.

---

## 4. Entities

Verbatim from `AGENT_INSTRUCTIONS.md §3.1`. Reproduced here for self-containment.

### 4.1 `CarEntity`

```kotlin
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

Note that `make` and `model` are non-null `String = ""` in the entity, even though `DESIGN.md §4.1`'s readability SQL shows them as nullable `TEXT`. Per `DESIGN.md §4.2`, the entity is the source of truth for the actual schema. Room creates `make TEXT NOT NULL DEFAULT ''` from this declaration.

### 4.2 `ChargeEventEntity`

```kotlin
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

`note` is non-null `String = ""`. The migration (§5.2) must therefore use `NOT NULL DEFAULT ''` — see §5.2 risks.

### 4.3 `CustomLocationEntity`

```kotlin
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

No `@TypeConverter` is needed across all three entities — every field is a primitive, nullable primitive, or `String`.

---

## 5. AppDatabase + migrations

```kotlin
@Database(
    entities = [CarEntity::class, ChargeEventEntity::class, CustomLocationEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    abstract fun chargeEventDao(): ChargeEventDao
    abstract fun customLocationDao(): CustomLocationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) { ... }
        val MIGRATION_2_3 = object : Migration(2, 3) { ... }
    }
}
```

### 5.1 `MIGRATION_1_2`

One statement:

```sql
ALTER TABLE charge_events ADD COLUMN chargeType TEXT NOT NULL DEFAULT 'AC';
```

The default `'AC'` ensures pre-existing rows get a sensible value.

### 5.2 `MIGRATION_2_3`

In order:

**Schema changes to `charge_events`:**

1. `ALTER TABLE charge_events ADD COLUMN costTotal REAL` (nullable)
2. `ALTER TABLE charge_events ADD COLUMN costPerKwh REAL` (nullable)
3. `ALTER TABLE charge_events ADD COLUMN currency TEXT` (nullable)
4. `ALTER TABLE charge_events ADD COLUMN location TEXT` (nullable)
5. `ALTER TABLE charge_events ADD COLUMN note TEXT NOT NULL DEFAULT ''` — **the `NOT NULL DEFAULT ''` is critical.** A nullable `note TEXT` would not match the entity's non-null `String = ""` and Room would crash with a schema mismatch on first open.

**New `custom_locations` table:**

6. `CREATE TABLE IF NOT EXISTS custom_locations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, useCount INTEGER NOT NULL DEFAULT 1, lastUsed INTEGER NOT NULL)`
7. `CREATE UNIQUE INDEX IF NOT EXISTS index_custom_locations_label ON custom_locations(label)`

**Indices on `charge_events`** (see §5.3 for why all three are added here):

8. `CREATE INDEX IF NOT EXISTS index_charge_events_carId_eventDate ON charge_events(carId, eventDate)`
9. `CREATE INDEX IF NOT EXISTS index_charge_events_chargeType ON charge_events(chargeType)`
10. `CREATE INDEX IF NOT EXISTS index_charge_events_location ON charge_events(location)`

Room's auto-generated index name format is `index_<table>_<col1>[_<col2>...]`. Statement names must match exactly or schema validation fails.

### 5.3 Why all three `charge_events` indices are added by `MIGRATION_2_3`

Three indices are declared on `ChargeEventEntity`: `(carId, eventDate)`, `chargeType`, `location`. They must all exist on the post-migration schema or Room's `validateMigration()` step will fail when comparing the result to the v3 entity declarations.

The challenge: **we don't know what v1 and v2's entities looked like** because the app's first ever ship is at v3. They were never written. The migration path's job is to bring any *theoretical* v1 or v2 DB up to v3-equivalent regardless of which indices that hypothetical earlier version had created.

The defensive answer: `MIGRATION_2_3` issues `CREATE INDEX IF NOT EXISTS` for all three `charge_events` indices. `IF NOT EXISTS` makes each statement idempotent — if a hand-built v1 or v2 test fixture happened to have created the index already, the statement is a no-op. The migration ends in the same state regardless of fixture starting state.

This is overhead-free in production (the migrations never run) and gives `migrate_1_to_3_validatesSchema` a deterministic post-condition.

### 5.4 Migration test strategy

`MigrationTest` does **not** use `MigrationTestHelper` with schema JSON files (because the v1 and v2 schemas were never declared as Kotlin entities — there's no `@Entity` to export from). Instead, the test:

1. Builds a v1 DB via raw `SupportSQLiteOpenHelper` SQL `CREATE TABLE` statements (matching what Room would have produced for v1 entities).
2. Inserts representative rows.
3. Closes the helper.
4. Re-opens the DB via `Room.databaseBuilder(context, AppDatabase::class.java, dbName).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().openHelper.writableDatabase` — Room runs the migrations AND validates the post-migration schema against the v3 entity declarations.
5. If schema validation succeeds (no `IllegalStateException`), the migrations matched. The test then queries the migrated DB to confirm pre-existing rows survived.

This catches column-name casing drift, missing-index drift, wrong defaults — exactly what `TEST_PLAN §2.4`'s `migrate_1_to_3_validatesSchema` calls for.

---

## 6. DAOs

### 6.1 `CarDao`

```kotlin
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

### 6.2 `ChargeEventDao`

```kotlin
@Dao
interface ChargeEventDao {
    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    fun observeForCar(carId: Int): Flow<List<ChargeEventEntity>>

    @Query("""
        SELECT * FROM charge_events
        WHERE carId = :carId AND eventDate BETWEEN :from AND :to
        ORDER BY eventDate ASC
    """)
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

No `PagingSource<Int, ChargeEventEntity>` yet — Sub-project D will add a paged query when the History screen lands.

### 6.3 `CustomLocationDao`

```kotlin
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

`recordUsage` lives on the DAO (not the repository) because it must run inside a Room transaction. The DAO is `abstract class` (not `interface`) to allow the `@Transaction open suspend fun` body. The repository simply forwards to it.

The rowId-of-`-1L` guard ensures a brand-new label ends up at `useCount = 1` (from the insert), not `useCount = 2` (insert plus an unintended increment). This is the bug the spec's `getTopLocations_sortedByUseCount` test would catch, but the explicit `increment_existingLabel` test in `TEST_PLAN §2.3` makes the contract direct: insert + increment ⇒ useCount = 2, not 3.

---

## 7. Repositories

All three are `@Singleton class … @Inject constructor(dao)`. None inject `SettingsRepository`. Each is a thin façade over its DAO with no business rules — the only repository-layer logic is composition of DAO calls.

### 7.1 `CarRepository`

```kotlin
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

`delete(car)` triggers the FK cascade — all `charge_events` with `carId = car.id` are deleted by SQLite. The repo doesn't need to do anything extra. The `CarRepositoryTest.deleteCar_cascadesEvents` case verifies this end-to-end through the repo path.

### 7.2 `ChargeEventRepository`

```kotlin
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

### 7.3 `LocationRepository`

```kotlin
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

Default `now = System.currentTimeMillis()` keeps the call sites concise (`locationRepository.recordUsage("Home")`); tests and use cases can pass an explicit `now` for determinism.

### 7.4 `SettingsRepository` extension

Add to the existing `SettingsRepository.kt`:

```kotlin
val activeCarId: Flow<Int> =
    dataStore.data.map { it[PreferenceKeys.ACTIVE_CAR_ID] ?: -1 }

suspend fun setActiveCarId(id: Int) {
    dataStore.edit { it[PreferenceKeys.ACTIVE_CAR_ID] = id }
}
```

The KDoc on `PreferenceKeys.ACTIVE_CAR_ID` (added in A's final-review fix `3c2d627`) already documents the `-1` sentinel.

---

## 8. Hilt wiring

`di/DatabaseModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "evtracker.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides fun provideCarDao(db: AppDatabase): CarDao = db.carDao()
    @Provides fun provideChargeEventDao(db: AppDatabase): ChargeEventDao = db.chargeEventDao()
    @Provides fun provideCustomLocationDao(db: AppDatabase): CustomLocationDao = db.customLocationDao()
}
```

DAO providers are deliberately **not** `@Singleton` — Room internally caches DAO instances on the `RoomDatabase`, so an additional layer of singleton caching at the Hilt level would be wasted. The repositories are `@Singleton` themselves, so each repo gets one DAO instance for the process lifetime.

`AppModule.kt` is unchanged — it still provides only the DataStore.

---

## 9. Build configuration

### 9.1 Schema export

Add a **top-level** `ksp { … }` block to `app/build.gradle.kts` (NOT nested under `android` or `defaultConfig` — KSP's Gradle DSL is registered at the project level by the `com.google.devtools.ksp` plugin):

```kotlin
// app/build.gradle.kts — at the top level, alongside `android { … }` and `dependencies { … }`:
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

Generated schema JSONs land at `app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/3.json`.

### 9.2 `app/schemas/` is tracked in git

Schema JSONs ARE committed (this is the Room-recommended practice). Reviewers see schema diffs in PRs; future migrations have a versioned schema baseline to test against.

### 9.3 No new dependencies

Room runtime, room-ktx, and room-compiler (KSP) are already declared in `app/build.gradle.kts` from A. `room-testing` is also already present. No build.gradle.kts changes needed beyond the KSP arg.

---

## 10. Tests

### 10.1 Instrumented (in-memory Room)

Standard pattern for all 4 DAO + migration test classes:

```kotlin
@RunWith(AndroidJUnit4::class)
class CarDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var carDao: CarDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()  // simplifies test code; not needed in production
            .build()
        carDao = db.carDao()
    }

    @After
    fun tearDown() = db.close()

    // … test cases …
}
```

#### `CarDaoTest` (3 cases per `TEST_PLAN §2.2`)

- `insertCar_idIsAssigned` — insert returns rowId > 0; the inserted car has the same id back from `getById`.
- `updateCar_changesPersist` — insert, then update name, observe new value via `observeAll`.
- `deleteCar_removesFromList` — insert two, delete one, `observeAll` returns one.

#### `ChargeEventDaoTest` (5 cases per `TEST_PLAN §2.1`)

- `insertAndRetrieve_returnsAllForCar` — insert 3 events for one car, `observeForCar` emits all 3.
- `cascadeDelete_deletesEventsWhenCarDeleted` — insert car + 3 events, delete car, `observeForCar` emits empty.
- `getInRange_filtersOutOfRange` — insert events at t-7d, t-15d, t-45d; query last 30 days returns 2.
- `observeForCar_isSortedByEventDateAsc` — insert in random date order; `observeForCar` emits sorted ascending.
- `update_persistsChanges` — insert an event, update its `kwhAdded` and `note`, query by id; updated values are returned. (TEST_PLAN §2.1 listed `insertDuplicate_replaces` here. We deliberately diverge: `ChargeEventDao.insert` uses default `OnConflictStrategy.ABORT` because charge events are append-only-by-design — edits go through `@Update`, not a re-`@Insert`. Testing ABORT semantics would just exercise Room's defensive throw on `SQLiteConstraintException`, which is uninteresting domain behavior. Testing the `update` path is more valuable, and there's no other coverage of it in B.)

#### `CustomLocationDaoTest` (6 cases per `TEST_PLAN §2.3`)

- `insertIfMissing_newLabel_stored` — `insertIfMissing("Home")`, `observeAll` includes it with `useCount = 1`.
- `insertIfMissing_duplicate_returnsMinusOne` — second `insertIfMissing("Home")` returns `-1L`, `observeAll` count is still 1.
- `recordUsage_increments_existingLabel` — call `recordUsage("Home", now1)`, then `recordUsage("Home", now2)`. After both calls: useCount = 2 (NOT 3 — the rowId-of-`-1L` guard).
- `recordUsage_freshLabel_useCountIsOne` — single call to `recordUsage("Work", now)` produces useCount = 1.
- `getTopLocations_maxFive` — insert 8 distinct labels via `recordUsage`, `observeTop5` emits 5.
- `getTopLocations_sortedByUseCount` — 5 labels with varying useCounts (call `recordUsage` differently for each), `observeTop5` emits highest-useCount first.

#### `MigrationTest` (3 cases per `TEST_PLAN §2.4`)

Each test: build a v1 DB via raw `SupportSQLiteOpenHelper`, insert representative rows, close the helper, re-open via Room with the migrations registered.

- `migrate_1_to_2` — build v1 with 1 car + 2 charge events; open at version 2 with `MIGRATION_1_2`; query `chargeType` column on existing rows (should be `'AC'` from the migration default).
- `migrate_2_to_3` — build v2 (with `chargeType`) + 1 car + 2 events; open at version 3 with `MIGRATION_2_3`; query `costTotal` (should be `null`), `note` (should be `''`), and verify `custom_locations` table exists and is empty.
- `migrate_1_to_3_validatesSchema` — build v1, open via `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().openHelper.writableDatabase`. Room runs both migrations sequentially and validates the resulting schema against the v3 entity declarations. If validation fails, Room throws `IllegalStateException` with a column-name or index mismatch message — that's the regression signal. Test passes if validation succeeds and pre-existing rows are still queryable.

#### `CarRepositoryTest` (1 case)

- `deleteCar_cascadesEvents_throughRepository` — insert car + 3 events via `CarRepository.insert` and `ChargeEventRepository.insert`; call `CarRepository.delete(car)`; `ChargeEventRepository.observeForCar(carId).first()` is empty. Verifies the FK cascade fires through the repo path, not just at the SQL level.

#### `ChargeEventRepositoryTest` (2 cases)

- `observeForCar_emitsOnlyForGivenCar` — insert 2 cars + 3 events for car A + 2 events for car B; `observeForCar(carA.id).first()` returns 3.
- `getInRange_excludesOutOfRange` — insert 4 events spanning 60 days; `getInRange(carId, t-30d, t)` returns events ≥ t-30d.

#### `LocationRepositoryTest` (1 case)

- `recordUsage_isAtomicAndIncrementsOnSecondCall` — call `recordUsage("Home", now1)` and `recordUsage("Home", now2)`; `observeTop5().first()` contains a single entry with `useCount = 2` and `lastUsed = now2`. Validates the `@Transaction` is bound through the repo and the rowId guard works at the repo seam.

**Total instrumented tests added by B: 21** (3 + 5 + 6 + 3 + 1 + 2 + 1). Combined with A's 3 (`WizardFlowTest`), the project will have **24** instrumented tests after this PR.

### 10.2 JVM (extends existing `SettingsRepositoryTest`)

Add one new case to `app/src/test/java/org/spsl/evtracker/data/repository/SettingsRepositoryTest.kt`:

```kotlin
@Test
fun setActiveCarId_persistsAndDefaultsToMinusOne() = runTest {
    assertEquals(-1, repo.activeCarId.first())  // default
    repo.setActiveCarId(42)
    assertEquals(42, repo.activeCarId.first())
}
```

**Total JVM tests added by B: 1.** Combined with A's 13, the project will have 14 JVM tests after this PR.

### 10.3 Tests not added (deferred)

- No `PagingSource` test — paging deferred to D.
- No `DeleteCarUseCase` test — that use case is C's.
- No `LocationRepository.observeAll` ordering test — the underlying DAO query is identical to `observeTop5` minus the `LIMIT 5`, fully covered by `getTopLocations_sortedByUseCount`.

---

## 11. Risks and notes

- **`note` column NOT NULL DEFAULT ''**. Easy to forget. `migrate_2_to_3` and `migrate_1_to_3_validatesSchema` both catch it via Room schema validation: a nullable `note TEXT` would mismatch the entity's non-null `String = ""` and Room would refuse to open the DB.
- **Index name format**. Room auto-generates index names as `index_<table>_<column>` (or `index_<table>_<col1>_<col2>` for composite). `MIGRATION_2_3` must use exactly these names or schema validation fails. The `migrate_1_to_3_validatesSchema` test catches this.
- **`activeCarId` orphan when active car is deleted**. B doesn't fix this — that's C's `DeleteCarUseCase`. Until C ships, the orphan state is benign because no UI consumes `activeCarId` yet (D is when the dashboard reads it).
- **Schema export creates files on disk**. `app/schemas/org.spsl.evtracker.data.local.db.AppDatabase/3.json` will be committed. This is Room-idiomatic and helps PR review. The directory is small (single-digit kB).
- **No v0→v1 migration**. Room's `createAllTables` builds v1 on first install. Since this app's first ever ship is at v3, `createAllTables` builds v3 directly. The migrations exist defensively for any future schema bump (v3→v4 or beyond) and for the regression tests in `MigrationTest`.
- **Fresh-install path doesn't run migrations**. The acceptance criterion #4 (manual smoke) is unchanged from A: wizard → dashboard placeholder. A user installing this build for the first time will get a v3 schema directly with no migration logic in the path.

---

## 12. Coverage check (spec → tasks)

| Spec section | Implementation file(s) | Test file(s) |
|---|---|---|
| §3 Architecture | All `data/local/**`, `data/repository/**`, `di/DatabaseModule.kt` | — |
| §4.1 `CarEntity` | `data/local/entity/CarEntity.kt` | `CarDaoTest` |
| §4.2 `ChargeEventEntity` | `data/local/entity/ChargeEventEntity.kt` | `ChargeEventDaoTest` |
| §4.3 `CustomLocationEntity` | `data/local/entity/CustomLocationEntity.kt` | `CustomLocationDaoTest` |
| §5.1 MIGRATION_1_2 | `data/local/db/AppDatabase.kt` | `MigrationTest.migrate_1_to_2`, `migrate_1_to_3_validatesSchema` |
| §5.2 MIGRATION_2_3 | `data/local/db/AppDatabase.kt` | `MigrationTest.migrate_2_to_3`, `migrate_1_to_3_validatesSchema` |
| §6 DAOs | `data/local/dao/*Dao.kt` | three Dao test classes |
| §7.1 CarRepository | `data/repository/CarRepository.kt` | `CarRepositoryTest` |
| §7.2 ChargeEventRepository | `data/repository/ChargeEventRepository.kt` | `ChargeEventRepositoryTest` |
| §7.3 LocationRepository | `data/repository/LocationRepository.kt` | `LocationRepositoryTest` |
| §7.4 SettingsRepository extension | `data/repository/SettingsRepository.kt` (modified) | `SettingsRepositoryTest` (1 new case) |
| §8 DatabaseModule | `di/DatabaseModule.kt` | (exercised by every instrumented test that uses the production DI graph; otherwise not directly tested) |
| §9 Build config | `app/build.gradle.kts` (modified) | — |
