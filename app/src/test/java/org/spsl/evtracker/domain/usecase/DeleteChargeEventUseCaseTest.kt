package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeWidgetRefresher

class DeleteChargeEventUseCaseTest {

    @Test
    fun invoke_deletesAndEnqueuesBackup_andRefreshesWidget() = runTest {
        val event = ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L)
        val queries = FakeChargeEventQueries()
        queries.seed(listOf(event))
        val writer = FakeChargeEventWriter(queries.shareStore())
        val scheduler = FakeBackupScheduler()
        val widgetRefresher = FakeWidgetRefresher()
        val useCase = DeleteChargeEventUseCase(writer, scheduler, widgetRefresher)

        useCase(event)

        assertEquals(0, queries.current().size)
        assertEquals(1, scheduler.enqueueCount)
        assertEquals(1, widgetRefresher.refreshCount)
    }
}
