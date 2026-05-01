package org.spsl.evtracker.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType

class ChargeTypeConverterTest {

    private val converter = ChargeTypeConverter()

    @Test
    fun roundTrip_acAndDcFast_preserved() {
        ChargeType.entries.forEach { type ->
            assertEquals(type, converter.toChargeType(converter.fromChargeType(type)))
        }
    }

    @Test
    fun legacyDcString_mapsToDcFast() {
        // v3 rows that survive on a fresh install before MIGRATION_3_4 fires
        // still need the converter to coerce "DC" → DC_FAST so reads don't
        // silently fall back to AC.
        assertEquals(ChargeType.DC_FAST, converter.toChargeType("DC"))
    }

    @Test
    fun unknownString_fallsBackToAc() {
        assertEquals(ChargeType.AC, converter.toChargeType(""))
        assertEquals(ChargeType.AC, converter.toChargeType("nonsense"))
    }
}
