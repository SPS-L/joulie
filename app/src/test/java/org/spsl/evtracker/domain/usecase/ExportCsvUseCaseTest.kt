package org.spsl.evtracker.domain.usecase

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class ExportCsvUseCaseTest {

    private val useCase = ExportCsvUseCase(
        carReader = org.spsl.evtracker.testing.FakeCarReader(),
        chargeEventQueries = org.spsl.evtracker.testing.FakeChargeEventQueries(),
        csvFileSink = object : org.spsl.evtracker.domain.backup.CsvFileSink {
            override suspend fun write(carName: String, body: (java.io.Writer) -> Unit) =
                throw NotImplementedError("not used in this test class")
        }
    )

    private val sampleEvents = listOf(
        ChargeEventEntity(id = 1, carId = 1, eventDate = 1714044000000L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = "AC", costTotal = 5.0, currency = "EUR", note = "n"),
        ChargeEventEntity(id = 2, carId = 1, eventDate = 1714130400000L, odometerKm = 1100.0, kwhAdded = 12.0, chargeType = "DC")
    )

    @Test
    fun headerLineUsesKmOrMilesPerFlag() {
        val writerKm = StringWriter()
        useCase.writeCsv(writerKm, sampleEvents, useKm = true)
        val firstLineKm = writerKm.toString().lineSequence().first()
        assertTrue("expected odometer_km in $firstLineKm", firstLineKm.contains("odometer_km"))

        val writerMi = StringWriter()
        useCase.writeCsv(writerMi, sampleEvents, useKm = false)
        val firstLineMi = writerMi.toString().lineSequence().first()
        assertTrue("expected odometer_miles in $firstLineMi", firstLineMi.contains("odometer_miles"))
    }

    @Test
    fun rowCountMatchesEventCount() {
        val w = StringWriter()
        useCase.writeCsv(w, sampleEvents, useKm = true)
        val nonEmpty = w.toString().lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(3, nonEmpty.size)
    }
}
