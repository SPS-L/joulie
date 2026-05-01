package org.spsl.evtracker.domain.usecase

import android.net.Uri
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.CsvFileSink
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.service.UnitConverter
import java.io.Writer
import java.time.Instant
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val csvFileSink: CsvFileSink,
) {
    suspend fun export(carId: Long, useKm: Boolean): Uri {
        val car = carReader.getById(carId) ?: throw IllegalArgumentException("Unknown carId=$carId")
        val events = chargeEventQueries.getAllForCarSorted(carId)
        return csvFileSink.write(car.name) { writer -> writeCsv(writer, events, useKm) }
    }

    /** Package-private for unit tests; do not call directly from production. */
    internal fun writeCsv(writer: Writer, events: List<ChargeEventEntity>, useKm: Boolean) {
        writer.append("date,odometer_${if (useKm) "km" else "miles"},kwh,charge_type,location,cost_total,currency,note\n")
        for (e in events) {
            val odo = if (useKm) e.odometerKm else UnitConverter.kmToMiles(e.odometerKm)
            writer.append(Instant.ofEpochMilli(e.eventDate).toString()).append(',')
                .append(odo.toString()).append(',')
                .append(e.kwhAdded.toString()).append(',')
                .append(e.chargeType.name).append(',')
                .append(csvEscape(e.location ?: "")).append(',')
                .append(e.costTotal?.toString() ?: "").append(',')
                .append(e.currency ?: "").append(',')
                .append(csvEscape(e.note)).append('\n')
        }
    }

    private fun csvEscape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) {
            "\"${s.replace("\"", "\"\"")}\""
        } else {
            s
        }
}
