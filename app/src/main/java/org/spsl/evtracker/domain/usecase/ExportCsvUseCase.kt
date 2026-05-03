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
                // TASK-52: enum is internally controlled so escape is a no-op
                // for current ChargeType values, but apply the same escape so a
                // future enum addition or rename can never bypass the contract.
                .append(csvEscape(e.chargeType.name)).append(',')
                .append(csvEscape(e.location ?: "")).append(',')
                .append(e.costTotal?.toString() ?: "").append(',')
                // TASK-52: currency is a free-form letter code (BackupSerializer
                // stores whatever the wizard recorded), so it is user-supplied
                // for hardening purposes even if the wizard validates it today.
                .append(csvEscape(e.currency ?: "")).append(',')
                .append(csvEscape(e.note)).append('\n')
        }
    }

    /**
     * RFC 4180 + spreadsheet-formula-injection hardening (TASK-52).
     *
     * Quoting is applied when the field contains any of `,`, `"`, `\n`, `\r`,
     * or `\t`. RFC 4180 only mandates quoting on `,`, `"`, and the line-end
     * sequence; we extend to lone `\r` (defensive — some parsers split rows
     * on bare CR) and `\t` (Excel auto-detects TSV when a tab appears in the
     * first row, which corrupts column alignment).
     *
     * Formula-injection mitigation: when the field's first character is in
     * `{ '=', '+', '-', '@' }`, prefix it with a single quote `'` BEFORE
     * applying the standard escape, then quote the result. Excel /
     * LibreOffice / Numbers all interpret a leading `=`, `+`, `-`, or `@` as
     * a formula; the leading `'` neutralises the interpretation while keeping
     * the data round-trippable for any consumer that strips leading
     * apostrophes (research pipelines do). The cells render as text; a
     * researcher casually opening the CSV in Excel cannot accidentally
     * execute `=cmd|'/c calc'!A1` from a `note` field.
     *
     * Numeric / timestamp columns (date, odometer, kwh, cost_total) bypass
     * `csvEscape` deliberately: `Double.toString()` / `Instant.toString()`
     * cannot produce a formula-injection prefix from a non-malicious value
     * (negative numbers like `-3.5` are interpreted as the same number by
     * Excel, not as a destructive formula), and quoting them as text would
     * defeat researchers' pivot tables and charts.
     */
    private fun csvEscape(s: String): String {
        if (s.isEmpty()) return s
        val needsFormulaPrefix = s[0] in FORMULA_PREFIX_TRIGGERS
        val needsQuoting = needsFormulaPrefix ||
            s.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == '\t' }
        if (!needsQuoting) return s
        val safe = if (needsFormulaPrefix) "'$s" else s
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private companion object {
        /**
         * Cell prefixes that Excel / LibreOffice / Numbers interpret as a
         * formula. Hardening against the canonical OWASP CSV-injection set.
         */
        private val FORMULA_PREFIX_TRIGGERS = setOf('=', '+', '-', '@')
    }
}
