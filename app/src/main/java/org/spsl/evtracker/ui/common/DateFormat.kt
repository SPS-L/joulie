package org.spsl.evtracker.ui.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormat {
    private const val PATTERN = "d MMM yyyy, HH:mm"

    fun formatEpochMs(
        ms: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String =
        DateTimeFormatter.ofPattern(PATTERN, locale)
            .withZone(zone)
            .format(Instant.ofEpochMilli(ms))
}
