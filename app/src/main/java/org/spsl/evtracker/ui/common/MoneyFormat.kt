// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

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
