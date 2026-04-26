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
