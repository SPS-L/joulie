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
