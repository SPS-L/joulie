package org.spsl.evtracker.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeKwhSource

class ChargeKwhSourceConverterTest {

    private val converter = ChargeKwhSourceConverter()

    @Test
    fun roundTrip_allValues_preserved() {
        ChargeKwhSource.entries.forEach { source ->
            assertEquals(source, converter.toChargeKwhSource(converter.fromChargeKwhSource(source)))
        }
    }

    @Test
    fun unknownString_fallsBackToMeasured() {
        // Same defensive contract as ChargeTypeConverter — never throw on a
        // corrupted row; degrade to MEASURED so the event still participates
        // in degradation tracking (worst case = a derived event over-counts
        // once, not a crashed Room read).
        assertEquals(ChargeKwhSource.MEASURED, converter.toChargeKwhSource(""))
        assertEquals(ChargeKwhSource.MEASURED, converter.toChargeKwhSource("nonsense"))
    }
}
