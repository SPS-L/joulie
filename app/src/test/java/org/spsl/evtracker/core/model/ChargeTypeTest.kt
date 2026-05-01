package org.spsl.evtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeTypeTest {

    @Test
    fun parseLegacy_acAndDc_round_trip() {
        assertEquals(ChargeType.AC, ChargeType.parseLegacy("AC"))
        assertEquals(ChargeType.DC_FAST, ChargeType.parseLegacy("DC"))
        assertEquals(ChargeType.DC_FAST, ChargeType.parseLegacy("DC_FAST"))
        assertEquals(ChargeType.DC_ULTRA, ChargeType.parseLegacy("DC_ULTRA"))
        // Defensive fallback: garbage input becomes AC, never throws.
        assertEquals(ChargeType.AC, ChargeType.parseLegacy("garbage"))
    }

    @Test
    fun isDc_returns_true_for_dcVariants() {
        assertFalse(ChargeType.AC.isDc)
        assertTrue(ChargeType.DC_FAST.isDc)
        assertTrue(ChargeType.DC_ULTRA.isDc)
    }

    @Test
    fun displayLabel_collapsesDcVariants() {
        assertEquals("AC", ChargeType.AC.displayLabel())
        assertEquals("DC", ChargeType.DC_FAST.displayLabel())
        assertEquals("DC", ChargeType.DC_ULTRA.displayLabel())
    }
}
