package org.spsl.evtracker.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test.db"
    private lateinit var context: Context

    /**
     * Discovers `@AutoMigration(from=5, to=6)` and `@AutoMigration(from=6, to=7)`
     * declared on the `@Database` annotation. Runs them via
     * [MigrationTestHelper.runMigrationsAndValidate] so the v5→v6 and v6→v7
     * tests exercise the same Room-synthesised path that ships in production.
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

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
                        ")",
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
                        ")",
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
                .build(),
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
                        ")",
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
                        ")",
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
                .build(),
        )
        return helper.writableDatabase
    }

    /** Builds a v3 DB at [testDbName] (= v2 + cost columns + custom_locations table + indices). */
    private fun buildV3Database(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
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
                        ")",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS charge_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "carId INTEGER NOT NULL, " +
                        "eventDate INTEGER NOT NULL, " +
                        "odometerKm REAL NOT NULL, " +
                        "kwhAdded REAL NOT NULL, " +
                        "chargeType TEXT NOT NULL DEFAULT 'AC', " +
                        "costTotal REAL, " +
                        "costPerKwh REAL, " +
                        "currency TEXT, " +
                        "location TEXT, " +
                        "note TEXT NOT NULL DEFAULT '', " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(carId) REFERENCES cars(id) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_locations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "useCount INTEGER NOT NULL DEFAULT 1, " +
                        "lastUsed INTEGER NOT NULL" +
                        ")",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_locations_label " +
                        "ON custom_locations(label)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_carId_eventDate " +
                        "ON charge_events(carId, eventDate)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_chargeType " +
                        "ON charge_events(chargeType)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_location " +
                        "ON charge_events(location)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // No upgrades; this helper only ever opens at v3.
            }
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDbName)
                .callback(callback)
                .build(),
        )
        return helper.writableDatabase
    }

    /** Opens [testDbName] via Room with all migrations registered, forcing schema validation. */
    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                // v5→v6 and v6→v7 are auto-discovered from the @Database
                // annotation; no addMigrations(...) entry needed.
            )
            .build()

    @Test
    fun migrate_1_to_2() = runBlocking {
        val v1 = buildV1Database()
        v1.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v1.execSQL(
            "INSERT INTO charge_events (carId, eventDate, odometerKm, kwhAdded, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 2000)",
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
                "VALUES (1, 2000, 100.0, 10.0, 'DC', 2000)",
        )

        // Run MIGRATION_2_3 directly — isolated, no Room schema validation.
        AppDatabase.MIGRATION_2_3.migrate(v2)

        // Verify new columns exist on pre-existing rows with their migration defaults.
        v2.query("SELECT chargeType, costTotal, note FROM charge_events WHERE id = 1")
            .use { cursor ->
                assertTrue("expected one row in charge_events", cursor.moveToFirst())
                assertEquals("DC", cursor.getString(0)) // unchanged from v2
                assertTrue("costTotal should be NULL", cursor.isNull(1))
                assertEquals("", cursor.getString(2)) // NOT NULL DEFAULT ''
            }

        // Verify the new custom_locations table exists and is empty.
        v2.query("SELECT COUNT(*) FROM custom_locations").use { cursor ->
            assertTrue("expected COUNT(*) row", cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        v2.close()
    }

    @Test
    fun migrate_3_to_4_rewritesLegacyDcRows() = runBlocking {
        val v3 = buildV3Database()
        v3.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v3.execSQL(
            "INSERT INTO charge_events " +
                "(carId, eventDate, odometerKm, kwhAdded, chargeType, note, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 'DC', '', 2000), " +
                "(1, 3000, 200.0, 10.0, 'AC', '', 3000)",
        )

        AppDatabase.MIGRATION_3_4.migrate(v3)

        v3.query("SELECT chargeType FROM charge_events ORDER BY eventDate ASC")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("DC_FAST", cursor.getString(0)) // legacy "DC" rewritten
                assertTrue(cursor.moveToNext())
                assertEquals("AC", cursor.getString(0)) // unchanged
            }
        v3.close()
    }

    @Test
    fun migrate_4_to_5_isNoOp_widenIntPksToLong() = runBlocking {
        // MIGRATION_4_5: widening Kotlin Int → Long PKs is a no-op
        // at the SQLite level — INTEGER columns already hold 64-bit signed
        // integers. Verify the migration runs cleanly and existing rows
        // survive untouched.
        val v3 = buildV3Database()
        v3.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v3.execSQL(
            "INSERT INTO charge_events " +
                "(carId, eventDate, odometerKm, kwhAdded, chargeType, note, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 'AC', '', 2000)",
        )
        // Push v3 → v4 first, then v4 → v5.
        AppDatabase.MIGRATION_3_4.migrate(v3)
        AppDatabase.MIGRATION_4_5.migrate(v3)

        v3.query("SELECT id, carId FROM charge_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0)) // PK survives as 64-bit value
            assertEquals(1L, cursor.getLong(1))
        }
        v3.close()
    }

    @Test
    fun migrate_5_to_6_addsSocColumns() = runBlocking {
        // @AutoMigration(from = 5, to = 6) adds nullable socBefore + socAfter
        // REAL columns to charge_events. Existing rows leave both at NULL.
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

    @Test
    fun migrate_6_to_7_addsKwhSourceColumn() = runBlocking {
        // @AutoMigration(from = 6, to = 7) adds a NOT NULL `kwhSource` TEXT
        // column to charge_events with `DEFAULT 'MEASURED'`. Pre-migration
        // rows backfill to MEASURED — exactly the right behaviour, since
        // legacy events were entered before the in-form calculator existed
        // and so cannot be DERIVED_FROM_SOC.
        val v5 = helper.createDatabase(testDbName, 5)
        v5.execSQL("INSERT INTO cars (id, name, createdAt) VALUES (1, 'A', 1000)")
        v5.execSQL(
            "INSERT INTO charge_events " +
                "(id, carId, eventDate, odometerKm, kwhAdded, chargeType, note, createdAt) " +
                "VALUES (1, 1, 2000, 100.0, 10.0, 'AC', '', 2000)",
        )
        v5.close()

        // Chain v5 → v6 → v7 in one runMigrationsAndValidate call; both
        // transitions execute as auto-migrations.
        val v7 = helper.runMigrationsAndValidate(testDbName, 7, true)
        v7.query("SELECT kwhSource FROM charge_events WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("MEASURED", cursor.getString(0))
        }
        v7.close()
    }

    @Test
    fun migrate_1_to_7_validatesSchema() = runBlocking {
        val v1 = buildV1Database()
        v1.execSQL("INSERT INTO cars (name, createdAt) VALUES ('A', 1000)")
        v1.execSQL(
            "INSERT INTO charge_events (carId, eventDate, odometerKm, kwhAdded, createdAt) " +
                "VALUES (1, 2000, 100.0, 10.0, 2000)",
        )
        v1.close()

        // Open through Room with all six migrations registered. Room runs them
        // in order and then validates the resulting schema against v7 entity
        // declarations. Schema validation exercises the ChargeTypeConverter
        // wiring on the chargeType column, the Long-PK widening, the
        // optional SoC columns, and the new ChargeKwhSourceConverter on
        // the kwhSource column.
        val room = openWithRoom()
        try {
            val event = room.chargeEventDao().getAllForCarSorted(1L).single()
            assertEquals(1L, event.id)
            assertEquals(1L, event.carId)
            assertEquals(ChargeType.AC, event.chargeType) // MIGRATION_1_2 default → enum AC
            assertEquals(null, event.costTotal) // MIGRATION_2_3 add column
            assertEquals("", event.note) // MIGRATION_2_3 add column NOT NULL DEFAULT ''
            assertEquals(null, event.socBefore) // MIGRATION_5_6 add column
            assertEquals(null, event.socAfter) // MIGRATION_5_6 add column
            assertEquals(ChargeKwhSource.MEASURED, event.kwhSource) // MIGRATION_6_7 default
        } finally {
            room.close()
        }
    }
}
