package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter

class ResetActiveCarDataUseCaseTest {

    private fun event(id: Int, carId: Int): ChargeEventEntity = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = 1_700_000_000_000L + id,
        odometerKm = 100.0 + id,
        kwhAdded = 20.0,
        chargeType = ChargeType.AC,
        createdAt = 0L,
    )

    private fun build(): Triple<ResetActiveCarDataUseCase, FakeChargeEventQueries, FakeBackupScheduler> {
        val store = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val queries = FakeChargeEventQueries(store)
        val writer = FakeChargeEventWriter(store)
        val scheduler = FakeBackupScheduler()
        return Triple(ResetActiveCarDataUseCase(writer, scheduler), queries, scheduler)
    }

    @Test fun invoke_deletesEventsForGivenCarOnly() = runTest {
        val (useCase, queries, _) = build()
        queries.shareStore().value = listOf(event(1, 7), event(2, 7), event(3, 9))
        useCase(7)
        assertEquals(listOf(3), queries.current().map { it.id })
    }

    @Test fun invoke_doesNotTouchOtherCars() = runTest {
        val (useCase, queries, _) = build()
        queries.shareStore().value = listOf(event(1, 7), event(2, 9), event(3, 9))
        useCase(7)
        assertEquals(setOf(2, 3), queries.current().map { it.id }.toSet())
    }

    @Test fun invoke_enqueuesBackup() = runTest {
        val (useCase, _, scheduler) = build()
        useCase(7)
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test fun invoke_throwsForCarIdMinusOne() = runTest {
        val (useCase, _, _) = build()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase(-1) }
        }
        assertTrue(ex.message!!.contains("-1"))
    }
}
