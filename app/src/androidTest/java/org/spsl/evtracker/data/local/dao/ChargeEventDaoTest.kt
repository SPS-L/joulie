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

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        carDao = db.carDao()
        chargeEventDao = db.chargeEventDao()
        carId = carDao.insert(CarEntity(name = "T", createdAt = 0L)).toInt()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun event(
        eventDate: Long = now,
        odometerKm: Double = 1000.0,
        kwhAdded: Double = 10.0,
    ) = ChargeEventEntity(
        carId = carId,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        createdAt = 0L,
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
            event(odometerKm = 100.0, kwhAdded = 10.0),
        ).toInt()
        // Re-fetch — the original local has id = 0.
        val saved = chargeEventDao.getById(eventRowId)!!

        chargeEventDao.update(saved.copy(kwhAdded = 12.5, note = "topped up"))

        val refetched = chargeEventDao.getById(eventRowId)!!
        assertEquals(12.5, refetched.kwhAdded, 0.0)
        assertEquals("topped up", refetched.note)
    }
}
