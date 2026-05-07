package org.spsl.evtracker.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeKwhSource
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

    private fun writeAndGetLines(events: List<ChargeEventEntity>, carName: String = "Tesla"): List<String> {
        val w = StringWriter()
        useCase.writeCsv(w, events, carName)
        val output = w.toString()
        // Walk the output character-by-character honouring CSV quoting so
        // embedded \r / \n inside a quoted field don't split a single
        // logical record across multiple "lines". `lineSequence()` would
        // mis-split on those.
        val lines = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        for (c in output) {
            if (c == '"') inQuotes = !inQuotes
            if (c == '\n' && !inQuotes) {
                lines += buf.toString()
                buf.clear()
            } else {
                buf.append(c)
            }
        }
        if (buf.isNotEmpty()) lines += buf.toString()
        return lines
    }

    // -------------------------------------------------------------------------
    // — header schema (14 columns, canonical km, no useKm flip)
    // -------------------------------------------------------------------------

    @Test
    fun header_isCanonicalFourteenColumnSchema() {
        // Header is the same regardless of the user's distance-unit pref —
        // dropping the previous `useKm` flip for research-export consistency.
        val lines = writeAndGetLines(sampleEvents)
        val header = lines.first()
        val expected =
            "event_date_iso,car_name,odometer_km,kwh,kwh_source,charge_type," +
                "location,cost_total,cost_per_kwh,currency,km_per_kwh," +
                "soc_before,soc_after,note"
        assertEquals(expected, header)
    }

    @Test
    fun rowCountMatchesEventCount() {
        val lines = writeAndGetLines(sampleEvents)
        // header + 2 events = 3 lines (header counted, no trailing blank).
        assertEquals(3, lines.size)
    }

    // -------------------------------------------------------------------------
    // — CSV escape hardening: \r, \t, formula-injection prefixes
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
        useCase.writeCsv(w, listOf(event), carName = "Tesla")
        val output = w.toString()
        val headerEnd = output.indexOf('\n')
        return output.substring(headerEnd + 1).trimEnd('\n')
    }

    @Test
    fun note_containingCarriageReturn_isQuoted() {
        // RFC 4180 requires CR-bearing fields to be quoted just like LF-bearing
        // ones. Pre-csvEscape only quoted on \n; a bare \r in note
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
        // Pre-behaviour preserved for any field that is neither
        // quotable nor formula-prefixed.
        val row = rowFor(note = "Charged at home")
        // Plain ASCII text without commas / quotes / linebreaks / tabs and
        // without a formula trigger remains unquoted (note is the last
        // column, so the row ends with the field verbatim).
        assertTrue("expected unquoted note; got: $row", row.endsWith(",Charged at home"))
    }

    @Test
    fun note_existingCommaAndQuoteCases_stayGreen() {
        // RFC 4180 baseline: comma → quoted; embedded `"` → doubled and
        // quoted; `\n` → quoted.
        assertTrue(rowFor(note = "a,b").contains("\"a,b\""))
        assertTrue(rowFor(note = "she said \"hi\"").contains("\"she said \"\"hi\"\"\""))
        assertTrue(rowFor(note = "line1\nline2").contains("\"line1\nline2\""))
    }

    @Test
    fun currency_isAlsoEscaped() {
        // expanded the escape contract to cover the currency column —
        // free-form letter code stored verbatim from the wizard. A malicious
        // (or pasted-via-keyboard-app) value starting with `=` must be
        // neutralised here too.
        val row = rowFor(currency = "=USD")
        assertTrue(
            "expected escaped currency in: $row",
            row.contains(",\"'=USD\","),
        )
    }

    // -------------------------------------------------------------------------
    // — schema additions (kwh_source, soc_*, km_per_kwh, car_name,
    //           cost_per_kwh) + per-row efficiency derivation + range filter
    // -------------------------------------------------------------------------

    @Test
    fun emptyEvents_emitsHeaderOnly() {
        // Date-ranged exports may filter to zero events; the header must
        // still be present so consumers can introspect column names.
        val lines = writeAndGetLines(emptyList())
        assertEquals(1, lines.size)
        assertTrue(lines.first().startsWith("event_date_iso,"))
    }

    @Test
    fun singleEvent_efficiencyCellIsBlank() {
        // First event in the exported slice has no previous row to delta
        // against, so `km_per_kwh` is blank by contract.
        val event = ChargeEventEntity(
            id = 1L,
            carId = 1L,
            eventDate = 1L,
            odometerKm = 1000.0,
            kwhAdded = 10.0,
            chargeType = ChargeType.AC,
            createdAt = 0L,
        )
        val lines = writeAndGetLines(listOf(event))
        val row = lines[1].split(',')
        // The km_per_kwh column is index 10 in the 14-column schema.
        assertEquals("", row[10])
    }

    @Test
    fun multiEvent_efficiencyDerivedFromDeltaOdometer() {
        // 100 km gained, 12 kWh charged → 8.333... km/kWh on row 2.
        // Row 1 stays blank (no previous event).
        val lines = writeAndGetLines(sampleEvents)
        val row1 = lines[1].split(',')
        val row2 = lines[2].split(',')
        assertEquals("", row1[10])
        // 100.0 / 12.0 = 8.333333333333334 in IEEE-754 Double.
        assertEquals("8.333333333333334", row2[10])
    }

    @Test
    fun negativeOdometerDelta_efficiencyBlank() {
        // Odometer rolls back (rare but possible: car swap, reset). The
        // efficiency cell is blank for the rolled-back row, but the chain
        // continues — the NEXT row still computes its delta against this
        // one (mirrors StatsCalculator's pairwise convention).
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2L, odometerKm = 900.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 3L, carId = 1L, eventDate = 3L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
        )
        val lines = writeAndGetLines(events)
        val row2 = lines[2].split(',') // odometer went 1000 → 900: dist = -100
        val row3 = lines[3].split(',') // odometer went 900 → 1000: dist = +100
        assertEquals("", row2[10])
        assertEquals("10.0", row3[10])
    }

    @Test
    fun zeroKwh_efficiencyBlank() {
        // 0-kwh events would divide-by-zero. Defensive blank, not NaN.
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 0.0, chargeType = ChargeType.AC, createdAt = 0L),
        )
        val lines = writeAndGetLines(events)
        val row2 = lines[2].split(',')
        assertEquals("", row2[10])
    }

    @Test
    fun kwhSource_emitsEnumName() {
        val event = ChargeEventEntity(
            id = 1L,
            carId = 1L,
            eventDate = 1L,
            odometerKm = 1000.0,
            kwhAdded = 10.0,
            chargeType = ChargeType.AC,
            createdAt = 0L,
            kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
        )
        val lines = writeAndGetLines(listOf(event))
        val row = lines[1].split(',')
        // kwh_source is column 4.
        assertEquals("DERIVED_FROM_SOC", row[4])
    }

    @Test
    fun socFields_emitFractionsOrBlank() {
        // soc_before / soc_after are columns 11 / 12 in the schema.
        val withSoc = ChargeEventEntity(
            id = 1L, carId = 1L, eventDate = 1L, odometerKm = 1000.0,
            kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L,
            socBefore = 0.2, socAfter = 0.8,
        )
        val withoutSoc = ChargeEventEntity(
            id = 2L,
            carId = 1L,
            eventDate = 2L,
            odometerKm = 1100.0,
            kwhAdded = 10.0,
            chargeType = ChargeType.AC,
            createdAt = 0L,
        )
        val lines = writeAndGetLines(listOf(withSoc, withoutSoc))
        val row1 = lines[1].split(',')
        val row2 = lines[2].split(',')
        assertEquals("0.2", row1[11])
        assertEquals("0.8", row1[12])
        assertEquals("", row2[11])
        assertEquals("", row2[12])
    }

    @Test
    fun costPerKwh_emitsDoubleOrBlank_independentOfCostTotal() {
        // cost_per_kwh is column 8. Both costTotal (col 7) and costPerKwh
        // (col 8) are independently nullable per the CostParser contract.
        val both = ChargeEventEntity(
            id = 1L, carId = 1L, eventDate = 1L, odometerKm = 1000.0,
            kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L,
            costTotal = 5.0, costPerKwh = 0.5, currency = "EUR",
        )
        val neither = ChargeEventEntity(
            id = 2L,
            carId = 1L,
            eventDate = 2L,
            odometerKm = 1100.0,
            kwhAdded = 10.0,
            chargeType = ChargeType.AC,
            createdAt = 0L,
        )
        val lines = writeAndGetLines(listOf(both, neither))
        val row1 = lines[1].split(',')
        val row2 = lines[2].split(',')
        assertEquals("5.0", row1[7])
        assertEquals("0.5", row1[8])
        assertEquals("", row2[7])
        assertEquals("", row2[8])
    }

    @Test
    fun carName_appearsInEveryRow_andIsEscapedWhenContainsComma() {
        // car_name is column 1. Verify it lands in each row.
        val carName = "Tesla, Model 3"
        val lines = writeAndGetLines(sampleEvents, carName = carName)
        // Comma in name forces RFC 4180 quoting.
        val expected = "\"Tesla, Model 3\""
        // Both data rows must carry the same quoted name.
        assertTrue("row 1 should contain car name; got: ${lines[1]}", lines[1].contains(expected))
        assertTrue("row 2 should contain car name; got: ${lines[2]}", lines[2].contains(expected))
    }

    @Test
    fun mixedCurrencyRows_eachRowEmitsItsOwnCurrency() {
        // The exporter does NOT apply mixed-currency aggregation rules
        // (those live in StatsCalculator for the dashboard). Each row
        // emits its own currency verbatim — researchers handle aggregation
        // downstream.
        val events = listOf(
            ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, costTotal = 5.0, currency = "EUR", createdAt = 0L),
            ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2L, odometerKm = 1100.0, kwhAdded = 10.0, chargeType = ChargeType.AC, costTotal = 7.0, currency = "USD", createdAt = 0L),
        )
        val lines = writeAndGetLines(events)
        val row1 = lines[1].split(',')
        val row2 = lines[2].split(',')
        // currency is column 9.
        assertEquals("EUR", row1[9])
        assertEquals("USD", row2[9])
    }

    @Test
    fun rangeFilter_omitsEventsOutsideRange() {
        // The use case's range filter is exercised through a fake
        // ChargeEventQueries that returns the full list; the use case
        // applies the filter post-query. We test the equivalent behaviour
        // here at the use-case boundary by exercising the suspend export
        // method via a custom queries fake.
        val carEntity = org.spsl.evtracker.data.local.entity.CarEntity(
            id = 1L,
            name = "Tesla",
            make = "T",
            model = "M3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )
        val carReader = org.spsl.evtracker.testing.FakeCarReader(initial = listOf(carEntity))
        val store = kotlinx.coroutines.flow.MutableStateFlow(
            listOf(
                ChargeEventEntity(id = 1L, carId = 1L, eventDate = 1_000L, odometerKm = 1000.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
                ChargeEventEntity(id = 2L, carId = 1L, eventDate = 2_000L, odometerKm = 1100.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
                ChargeEventEntity(id = 3L, carId = 1L, eventDate = 3_000L, odometerKm = 1200.0, kwhAdded = 10.0, chargeType = ChargeType.AC, createdAt = 0L),
            ),
        )
        val queries = org.spsl.evtracker.testing.FakeChargeEventQueries(store)
        val captured = StringBuilder()
        val sink = object : org.spsl.evtracker.domain.backup.CsvFileSink {
            override suspend fun write(carName: String, body: (java.io.Writer) -> Unit): android.net.Uri {
                val w = StringWriter()
                body(w)
                captured.append(w.toString())
                return org.mockito.kotlin.mock()
            }
        }
        val rangedUseCase = ExportCsvUseCase(carReader, queries, sink)
        kotlinx.coroutines.runBlocking {
            rangedUseCase.export(carId = 1L, range = 1_500L..2_500L)
        }
        // The 1000-ms event is below the range, the 3000-ms event is above.
        // Only the 2000-ms event survives the filter, plus the header line.
        val output = captured.toString()
        val rowLines = output.split('\n').filter { it.isNotBlank() }
        assertEquals("expected header + 1 event row", 2, rowLines.size)
        assertTrue("row should be the 2000ms event; got: ${rowLines[1]}", rowLines[1].startsWith("1970-01-01T00:00:02Z,"))
        // Defensive — ensure the 1000ms and 3000ms events did NOT bleed in.
        assertFalse("1000ms event must be filtered out", output.contains("00:00:01Z"))
        assertFalse("3000ms event must be filtered out", output.contains("00:00:03Z"))
    }
}
