package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.core.model.CostInput
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.domain.service.CostParser
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeChargeEventQueries
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeLocationWriter
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeWidgetRefresher

class SaveChargeEventUseCaseTest {

    private fun build(
        initialEvents: List<ChargeEventEntity> = emptyList(),
        nowMs: Long = 0L,
    ): SaveSetup {
        val queries = FakeChargeEventQueries()
        queries.seed(initialEvents)
        val writer = FakeChargeEventWriter(queries.shareStore())
        val locationWriter = FakeLocationWriter()
        val scheduler = FakeBackupScheduler()
        val widgetRefresher = FakeWidgetRefresher()
        val now = FakeNowProvider(nowMs)
        val useCase = SaveChargeEventUseCase(queries, writer, locationWriter, scheduler, widgetRefresher, CostParser(), now)
        return SaveSetup(useCase, queries, locationWriter, scheduler, widgetRefresher, now)
    }

    private data class SaveSetup(
        val useCase: SaveChargeEventUseCase,
        val queries: FakeChargeEventQueries,
        val locationWriter: FakeLocationWriter,
        val scheduler: FakeBackupScheduler,
        val widgetRefresher: FakeWidgetRefresher,
        val now: FakeNowProvider,
    )

    @Test
    fun insert_success_recordsLocationAndEnqueuesBackup() = runTest {
        val s = build()
        val result = s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 100.0,
                kwhAdded = 10.0,
                chargeType = ChargeType.AC,
                location = "Home",
            ),
        )
        assertTrue(result is SaveChargeEventResult.Success)
        assertEquals(1, s.queries.current().size)
        assertEquals("Home", s.locationWriter.current().single().label)
        assertEquals(1, s.scheduler.enqueueCount)
        assertEquals(1, s.widgetRefresher.refreshCount)
    }

    @Test
    fun update_success_keepsId() = runTest {
        val existing = ChargeEventEntity(id = 5L, carId = 1L, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L)
        val s = build(initialEvents = listOf(existing))
        val result = s.useCase(
            SaveChargeEventInput(
                eventId = 5L,
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 110.0,
                kwhAdded = 12.0,
                chargeType = ChargeType.AC,
            ),
        )
        assertEquals(SaveChargeEventResult.Success(eventId = 5L), result)
        assertEquals(110.0, s.queries.current().single().odometerKm, 0.0)
    }

    @Test
    fun insertOdometerNotIncreasing_returnsResultAndPersistsNothing() = runTest {
        val previous = ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L)
        val s = build(initialEvents = listOf(previous))
        val result = s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 2000L,
                odometerKm = 150.0,
                kwhAdded = 10.0,
                chargeType = ChargeType.AC,
            ),
        )
        assertEquals(SaveChargeEventResult.OdometerNotIncreasing, result)
        assertEquals(1, s.queries.current().size)
        assertEquals(0, s.scheduler.enqueueCount)
    }

    @Test
    fun updateOdometerCheck_ignoresOwnId() = runTest {
        val existing = ChargeEventEntity(id = 5L, carId = 1L, eventDate = 2000L, odometerKm = 200.0, kwhAdded = 10.0, createdAt = 0L)
        val before = ChargeEventEntity(id = 4L, carId = 1L, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 10.0, createdAt = 0L)
        val s = build(initialEvents = listOf(before, existing))
        val result = s.useCase(
            SaveChargeEventInput(
                eventId = 5L,
                carId = 1L,
                eventDate = 2000L,
                odometerKm = 210.0,
                kwhAdded = 12.0,
                chargeType = ChargeType.AC,
            ),
        )
        assertTrue(result is SaveChargeEventResult.Success)
    }

    @Test
    fun costInputZero_costFieldsAreNull() = runTest {
        val s = build()
        s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 100.0,
                kwhAdded = 10.0,
                chargeType = ChargeType.AC,
                costInput = CostInput(value = 0.0, mode = CostMode.TOTAL, currency = "EUR"),
            ),
        )
        val saved = s.queries.current().single()
        assertNull(saved.costTotal)
        assertNull(saved.costPerKwh)
        assertNull(saved.currency)
    }

    @Test
    fun kwhSource_defaultsToMeasured_whenInputOmitsField() = runTest {
        val s = build()
        s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 100.0,
                kwhAdded = 10.0,
                chargeType = ChargeType.AC,
            ),
        )
        // The SaveChargeEventInput default for kwhSource is MEASURED, so a
        // persisted entity is never silently flipped to DERIVED on a normal
        // user flow when the caller omits the field.
        assertEquals(ChargeKwhSource.MEASURED, s.queries.current().single().kwhSource)
    }

    @Test
    fun kwhSource_persistsDerivedFromSoc_whenInputOptsIn() = runTest {
        val s = build()
        s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 100.0,
                kwhAdded = 18.0,
                chargeType = ChargeType.AC,
                socBefore = 0.20,
                socAfter = 0.50,
                kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
            ),
        )
        assertEquals(
            ChargeKwhSource.DERIVED_FROM_SOC,
            s.queries.current().single().kwhSource,
        )
    }

    @Test
    fun createdAtAndLocationLastUsed_reflectNowProviderValue() = runTest {
        val s = build(nowMs = 12_345L)
        s.useCase(
            SaveChargeEventInput(
                carId = 1L,
                eventDate = 1000L,
                odometerKm = 100.0,
                kwhAdded = 10.0,
                chargeType = ChargeType.AC,
                location = "Home",
            ),
        )
        assertEquals(12_345L, s.queries.current().single().createdAt)
        assertEquals(12_345L, s.locationWriter.current().single().lastUsed)
    }
}
