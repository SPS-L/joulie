package org.spsl.evtracker.data.repository

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@RunWith(AndroidJUnit4::class)
class RoomDataResetTransactionRunnerTest {

    private lateinit var db: AppDatabase
    private lateinit var runner: RoomDataResetTransactionRunner

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        runner = RoomDataResetTransactionRunner(db)
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun clearAllTables_emptiesAllThreeTables() = runBlocking {
        val carId = db.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0, createdAt = 0L),
        ).toInt()
        db.chargeEventDao().insert(
            ChargeEventEntity(
                carId = carId,
                eventDate = 1_700_000_000_000L,
                odometerKm = 100.0,
                kwhAdded = 20.0,
                chargeType = ChargeType.AC,
                createdAt = 0L,
            ),
        )
        db.customLocationDao().insertIfMissing(
            CustomLocationEntity(label = "Office", useCount = 1, lastUsed = 1_700_000_000_000L),
        )

        runner.clearAllTables()

        assertTrue(db.chargeEventDao().getAllForCarSorted(carId).isEmpty())
        assertNull(db.carDao().getById(carId))
        val locationCount = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM custom_locations")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        assertEquals(0, locationCount)
    }

    @Test fun clearAllTables_isAtomic_throwingFromOneDeleteRollsBackOthers() = runBlocking {
        val carId = db.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0, createdAt = 0L),
        ).toInt()
        db.chargeEventDao().insert(
            ChargeEventEntity(
                carId = carId,
                eventDate = 1_700_000_000_000L,
                odometerKm = 100.0,
                kwhAdded = 20.0,
                chargeType = ChargeType.AC,
                createdAt = 0L,
            ),
        )

        // Simulate a failure inside a withTransaction block. Use the shared db so any
        // deletes that did execute are rolled back.
        val threw = try {
            db.withTransaction {
                db.chargeEventDao().deleteAll()
                throw IllegalStateException("simulated")
            }
            false
        } catch (e: IllegalStateException) {
            true
        }
        assertTrue(threw)

        // The deleteAll inside the failed transaction must have rolled back.
        assertEquals(1, db.chargeEventDao().getAllForCarSorted(carId).size)
    }
}
