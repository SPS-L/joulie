package org.spsl.evtracker.ui.common

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun knownCurrency_formatsWithSymbolOrCode() {
        val s = MoneyFormat.format(amount = 12.34, currencyCode = "EUR")
        assertTrue("formatted=$s", s.contains("12") && s.contains("34"))
    }

    @Test
    fun unknownCurrency_doesNotThrow() {
        val s = MoneyFormat.format(amount = 5.0, currencyCode = "XYZ_NOT_A_CURRENCY")
        assertNotNull(s)
    }

    @Test
    fun zeroAmount_formats() {
        val s = MoneyFormat.format(amount = 0.0, currencyCode = "EUR")
        assertTrue("formatted=$s", s.contains("0"))
    }
}
