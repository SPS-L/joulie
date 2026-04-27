package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter

class DeleteChargeEventUseCaseTest {

    @Test
    fun invoke_deletesAndEnqueuesBackup() = runTest {
        val event = ChargeEventEntity(id = 1, carId = 1, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0)
        val queries = FakeChargeEventQueries()
        queries.seed(listOf(event))
        val writer = FakeChargeEventWriter(queries.shareStore())
        val scheduler = FakeBackupScheduler()
        val useCase = DeleteChargeEventUseCase(writer, scheduler)

        useCase(event)

        assertEquals(0, queries.current().size)
        assertEquals(1, scheduler.enqueueCount)
    }
}
