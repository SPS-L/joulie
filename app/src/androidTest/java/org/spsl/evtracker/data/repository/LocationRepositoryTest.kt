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
