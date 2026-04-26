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
