// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import android.net.Uri
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.CsvFileSink
import org.spsl.evtracker.domain.repository.CarReader
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import java.io.Writer
import java.time.Instant
import javax.inject.Inject

/**
 * Unified CSV exporter for the active car.
 *
 * Header schema (14 columns, identical for full-history and date-ranged
 * exports — research consumers anchor on a stable schema):
 *
 *   event_date_iso, car_name, odometer_km, kwh, kwh_source, charge_type,
 *   location, cost_total, cost_per_kwh, currency, km_per_kwh,
 *   soc_before, soc_after, note
 *
 * Distance is always emitted as canonical kilometres regardless of the
 * user's display preference (the "odometer is always stored in km"
 * invariant). Exports are locale-independent for cross-fleet research
 * analysis.
 *
 * `kwh_source` emits the `ChargeKwhSource` enum name (`MEASURED` /
 * `DERIVED_FROM_SOC`). `soc_before` / `soc_after` emit fractions in
 * `0.0..1.0`, blank when null. `km_per_kwh` is computed per-row using
 * the same delta-odometer convention as `StatsCalculator` (DESIGN.md §7)
 * — `(odo[i] - odo[i-1]) / kwh[i]`, blank when there is no previous
 * event in the *exported* slice or when `dist <= 0` / `kwh <= 0`. The
 * first row in any export therefore emits a blank efficiency cell even
 * when an earlier event for the same car exists outside the range —
 * keeping the "first row blank" invariant predictable for spreadsheet
 * consumers.
 *
 * Text-bearing columns (`car_name`, `kwh_source`, `charge_type`,
 * `location`, `currency`, `note`) all route through `csvEscape` (RFC
 * 4180 + OWASP formula-injection prefixes). Numeric / timestamp columns
 * deliberately bypass — `Double.toString()` cannot produce a destructive
 * formula prefix from a non-malicious value, and quoting them as text
 * would defeat researchers' pivot tables.
 */
class ExportCsvUseCase @Inject constructor(
    private val carReader: CarReader,
    private val chargeEventQueries: ChargeEventQueries,
    private val csvFileSink: CsvFileSink,
) {
    /**
     * Full-history export for the given car. Convenience overload of
     * the range-aware [export] for the existing Settings → "Export CSV"
     * action.
     */
    suspend fun export(carId: Long): Uri = export(carId, range = null)

    /**
     * Date-ranged export for the given car. Pass `null` for [range] to
     * export the full history. The range is treated as **inclusive on
     * both ends** in epoch-ms (`startMillis..endMillis` via `LongRange`).
     */
    suspend fun export(carId: Long, range: LongRange?): Uri {
        val car = carReader.getById(carId) ?: throw IllegalArgumentException("Unknown carId=$carId")
        val all = chargeEventQueries.getAllForCarSorted(carId)
        val events = if (range != null) all.filter { it.eventDate in range } else all
        return csvFileSink.write(car.name) { writer -> writeCsv(writer, events, car.name) }
    }

    /** Package-private for unit tests; do not call directly from production. */
    internal fun writeCsv(writer: Writer, events: List<ChargeEventEntity>, carName: String) {
        writer.append(HEADER)
        var prevOdo: Double? = null
        for (e in events) {
            val efficiency = computeEfficiency(prevOdo, e)
            writer.append(Instant.ofEpochMilli(e.eventDate).toString()).append(',')
                .append(csvEscape(carName)).append(',')
                .append(e.odometerKm.toString()).append(',')
                .append(e.kwhAdded.toString()).append(',')
                .append(csvEscape(e.kwhSource.name)).append(',')
                .append(csvEscape(e.chargeType.name)).append(',')
                .append(csvEscape(e.location ?: "")).append(',')
                .append(e.costTotal?.toString() ?: "").append(',')
                .append(e.costPerKwh?.toString() ?: "").append(',')
                .append(csvEscape(e.currency ?: "")).append(',')
                .append(efficiency?.toString() ?: "").append(',')
                .append(e.socBefore?.toString() ?: "").append(',')
                .append(e.socAfter?.toString() ?: "").append(',')
                .append(csvEscape(e.note)).append('\n')
            // Always update prevOdo regardless of validity — a transient
            // odometer rollback or zero-kwh row should not break the delta
            // chain for the next valid row. Mirrors `StatsCalculator`'s
            // pairwise convention.
            prevOdo = e.odometerKm
        }
    }

    private fun computeEfficiency(prevOdo: Double?, e: ChargeEventEntity): Double? {
        if (prevOdo == null) return null
        if (e.kwhAdded <= 0.0) return null
        val dist = e.odometerKm - prevOdo
        if (dist <= 0.0) return null
        return dist / e.kwhAdded
    }

    /**
     * RFC 4180 + spreadsheet-formula-injection hardening.
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
     * apostrophes (research pipelines do).
     *
     * Numeric / timestamp columns bypass `csvEscape` deliberately:
     * `Double.toString()` / `Instant.toString()` cannot produce a
     * formula-injection prefix from a non-malicious value (negative numbers
     * like `-3.5` are interpreted as the same number by Excel, not as a
     * destructive formula), and quoting them as text would defeat
     * researchers' pivot tables and charts.
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

        private const val HEADER =
            "event_date_iso,car_name,odometer_km,kwh,kwh_source,charge_type," +
                "location,cost_total,cost_per_kwh,currency,km_per_kwh," +
                "soc_before,soc_after,note\n"
    }
}
