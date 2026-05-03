package org.spsl.evtracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import java.io.StringWriter

class ExportCsvUseCaseTest {

    private val useCase = ExportCsvUseCase(
        carReader = org.spsl.evtracker.testing.FakeCarReader(),
        chargeEventQueries = org.spsl.evtracker.testing.FakeChargeEventQueries(),
        csvFileSink = object : org.spsl.evtracker.domain.backup.CsvFileSink {
            override suspend fun write(carName: String, body: (java.io.Writer) -> Unit) =
                throw NotImplementedError("not used in this test class")
        },
    )

    private val sampleEvents = listOf(
        ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1714044000000L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, costTotal = 5.0, currency = "EUR", note = "n", createdAt = 0L),
        ChargeEventEntity(id = 2L, carId = 1L, eventDate = 1714130400000L, odometerKm = 1100.0, kwhAdded = 12.0, chargeType = ChargeType.DC_FAST, createdAt = 0L),
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

    // -------------------------------------------------------------------------
    // TASK-52 — CSV escape hardening: \r, \t, formula-injection prefixes
    // -------------------------------------------------------------------------

    /**
     * Builds a single ChargeEvent with the given user-supplied fields and
     * returns the entire data row, including any embedded `\r` / `\n` that
     * legitimately live inside a quoted field. Naive `lineSequence().drop(1)`
     * splits on embedded line breaks and would truncate the row before the
     * closing quote, so we slice on the first `\n` (the one that terminates
     * the header) and trim the trailing record-terminator.
     */
    private fun rowFor(
        location: String? = null,
        note: String = "",
        currency: String? = "EUR",
    ): String {
        val event = ChargeEventEntity(
            id = 1L,
            carId = 1L,
            eventDate = 1714044000000L,
            odometerKm = 1000.0,
            kwhAdded = 10.0,
            chargeType = ChargeType.AC,
            costTotal = 5.0,
            currency = currency,
            location = location,
            note = note,
            createdAt = 0L,
        )
        val w = StringWriter()
        useCase.writeCsv(w, listOf(event), useKm = true)
        val output = w.toString()
        val headerEnd = output.indexOf('\n')
        return output.substring(headerEnd + 1).trimEnd('\n')
    }

    @Test
    fun note_containingCarriageReturn_isQuoted() {
        // RFC 4180 requires CR-bearing fields to be quoted just like LF-bearing
        // ones. Pre-TASK-52 csvEscape only quoted on \n; a bare \r in note
        // would corrupt downstream parsers that split on \r.
        val row = rowFor(note = "line1\rline2")
        assertTrue("expected quoted note in: $row", row.contains("\"line1\rline2\""))
    }

    @Test
    fun note_containingTab_isQuoted() {
        // Some Excel versions auto-detect TSV when a tab appears in the first
        // data row, which silently corrupts column alignment. Quoting forces
        // CSV interpretation.
        val row = rowFor(note = "col1\tcol2")
        assertTrue("expected quoted note in: $row", row.contains("\"col1\tcol2\""))
    }

    @Test
    fun note_startingWithEquals_getsFormulaPrefix() {
        // Canonical OWASP CSV-injection vector. Without escape, a researcher
        // opening the export in Excel from a malicious note field could
        // execute `=cmd|'/c calc'!A1`. The leading apostrophe neutralises the
        // interpretation while keeping the data round-trippable.
        val row = rowFor(note = "=SUM(A1:A10)")
        assertTrue(
            "expected leading-'\\u0027' prefix inside quoted field; got: $row",
            row.contains("\"'=SUM(A1:A10)\""),
        )
    }

    @Test
    fun location_startingWithPlus_getsFormulaPrefix() {
        // International phone numbers ("+44 7000 ...") are a legitimate
        // user-supplied value that nevertheless triggers Excel's formula
        // mode. Apostrophe-prefix protects without losing the data.
        val row = rowFor(location = "+44 7000")
        assertTrue(
            "expected leading-'\\u0027' prefix inside quoted field; got: $row",
            row.contains("\"'+44 7000\""),
        )
    }

    @Test
    fun note_startingWithMinus_getsFormulaPrefix() {
        val row = rowFor(note = "-Some negative note")
        assertTrue(
            "expected leading-'\\u0027' prefix inside quoted field; got: $row",
            row.contains("\"'-Some negative note\""),
        )
    }

    @Test
    fun note_startingWithAt_getsFormulaPrefix() {
        // `@` triggers Excel's formula mode and also expands to spreadsheet
        // function names in some locales (e.g. `@SUM`). Hardening covers all
        // four canonical OWASP triggers symmetrically.
        val row = rowFor(note = "@everyone")
        assertTrue(
            "expected leading-'\\u0027' prefix inside quoted field; got: $row",
            row.contains("\"'@everyone\""),
        )
    }

    @Test
    fun note_withFormulaPrefixAndEmbeddedQuote_doublesQuotesInsideField() {
        // Compound case: leading `=` triggers the prefix AND an embedded `"`
        // requires standard CSV doubling. Apostrophe goes in front, then the
        // wrapped field doubles the inner quote.
        val row = rowFor(note = "=HYPERLINK(\"http://evil\")")
        assertTrue(
            "expected '=HYPERLINK(\"\"http://evil\"\") inside outer quotes; got: $row",
            row.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""),
        )
    }

    @Test
    fun note_plainText_isNotQuoted() {
        // Regression guard: hardening must not over-quote benign text.
        // Pre-TASK-52 behaviour preserved for any field that is neither
        // quotable nor formula-prefixed.
        val row = rowFor(note = "Charged at home")
        // Plain ASCII text without commas / quotes / linebreaks / tabs and
        // without a formula trigger remains unquoted (note is the last
        // column, so the row ends with the field verbatim).
        assertTrue("expected unquoted note; got: $row", row.endsWith(",Charged at home"))
    }

    @Test
    fun note_existingCommaAndQuoteCases_stayGreen() {
        // Regression guard for the pre-TASK-52 behaviour: comma → quoted;
        // embedded `"` → doubled and quoted; \n → quoted.
        assertTrue(rowFor(note = "a,b").contains("\"a,b\""))
        assertTrue(rowFor(note = "she said \"hi\"").contains("\"she said \"\"hi\"\"\""))
        assertTrue(rowFor(note = "line1\nline2").contains("\"line1\nline2\""))
    }

    @Test
    fun currency_isAlsoEscaped() {
        // TASK-52 expanded the escape contract to cover the currency column —
        // free-form letter code stored verbatim from the wizard. A malicious
        // (or pasted-via-keyboard-app) value starting with `=` must be
        // neutralised here too.
        val row = rowFor(currency = "=USD")
        assertTrue(
            "expected escaped currency in: $row",
            row.contains(",\"'=USD\","),
        )
    }
}
