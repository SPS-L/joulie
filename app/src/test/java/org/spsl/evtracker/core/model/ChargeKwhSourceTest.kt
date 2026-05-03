package org.spsl.evtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargeKwhSourceTest {

    @Test
    fun parseLegacy_knownValues_roundTrip() {
        assertEquals(ChargeKwhSource.MEASURED, ChargeKwhSource.parseLegacy("MEASURED"))
        assertEquals(
            ChargeKwhSource.DERIVED_FROM_SOC,
            ChargeKwhSource.parseLegacy("DERIVED_FROM_SOC"),
        )
    }

    @Test
    fun parseLegacy_unknownValues_fallBackToMeasured() {
        // Defensive fallback so a corrupted row or a future variant we don't
        // recognise yet never throws — the value defaults to MEASURED so the
        // event still appears in degradation tracking. Mirrors the
        // ChargeType.parseLegacy fallback behaviour.
        assertEquals(ChargeKwhSource.MEASURED, ChargeKwhSource.parseLegacy(""))
        assertEquals(ChargeKwhSource.MEASURED, ChargeKwhSource.parseLegacy("nonsense"))
    }
}
