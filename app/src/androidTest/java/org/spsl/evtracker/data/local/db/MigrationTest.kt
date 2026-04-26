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
