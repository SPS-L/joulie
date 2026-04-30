package org.spsl.evtracker.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DateFormatTest {

    @Test
    fun fixedEpoch_formatsDeterministicallyInUtcWithEnglishLocale() {
        val ms = ZonedDateTime.of(2026, 4, 27, 12, 34, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val s = DateFormat.formatEpochMs(ms = ms, zone = ZoneId.of("UTC"), locale = Locale.ENGLISH)
        assertEquals("27 Apr 2026, 12:34", s)
    }

    @Test
    fun zoneIsRespected() {
        val ms = ZonedDateTime.of(2026, 4, 27, 12, 34, 0, 0, ZoneId.of("UTC"))
            .toInstant().toEpochMilli()
        val s = DateFormat.formatEpochMs(ms = ms, zone = ZoneId.of("Asia/Tokyo"), locale = Locale.ENGLISH)
        assertEquals("27 Apr 2026, 21:34", s)
    }
}
