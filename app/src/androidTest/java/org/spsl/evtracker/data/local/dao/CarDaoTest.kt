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
        val rowId = carDao.insert(CarEntity(name = "T", createdAt = 0L))
        assertTrue("rowId must be > 0; got $rowId", rowId > 0)

        val saved = carDao.getById(rowId)
        assertNotNull(saved)
        assertEquals("T", saved!!.name)
    }

    @Test
    fun updateCar_changesPersist() = runBlocking {
        val rowId = carDao.insert(CarEntity(name = "T", createdAt = 0L))
        // Re-fetch — the original local still has id = 0 and would silently no-op on update.
        val saved = carDao.getById(rowId)!!

        carDao.update(saved.copy(name = "T2"))

        val list = carDao.observeAll().first()
        assertEquals(1, list.size)
        assertEquals("T2", list.single().name)
    }

    @Test
    fun deleteCar_removesFromList() = runBlocking {
        val id1 = carDao.insert(CarEntity(name = "A", createdAt = 0L))
        val id2 = carDao.insert(CarEntity(name = "B", createdAt = 0L))
        // Re-fetch before delete; original local has id = 0.
        carDao.delete(carDao.getById(id1)!!)

        val remaining = carDao.observeAll().first()
        assertEquals(listOf(id2), remaining.map { it.id })
    }
}
