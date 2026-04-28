package org.spsl.evtracker.ui.common

import java.text.NumberFormat
import java.util.Currency

object MoneyFormat {
    fun format(amount: Double, currencyCode: String): String {
        val nf = NumberFormat.getCurrencyInstance().apply {
            try {
                currency = Currency.getInstance(currencyCode)
            } catch (_: IllegalArgumentException) {
                // Fall back to the locale's default currency
            }
            maximumFractionDigits = 2
        }
        return nf.format(amount)
    }
}
