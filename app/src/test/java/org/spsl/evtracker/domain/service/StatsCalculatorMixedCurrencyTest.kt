package org.spsl.evtracker.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class StatsCalculatorMixedCurrencyTest {

    private val calc = StatsCalculator()

    private fun ev(currency: String? = null, costTotal: Double? = null): ChargeEventEntity =
        ChargeEventEntity(
            id = 0L, carId = 1L, eventDate = 0L, odometerKm = 0.0, kwhAdded = 1.0,
            chargeType = ChargeType.AC, costTotal = costTotal, costPerKwh = null,
            currency = currency, location = null, note = "", createdAt = 0L,
        )

    @Test fun emptyEvents_notMixed() {
        assertFalse(calc.detectMixedCurrency(emptyList()))
    }

    @Test fun singleCurrency_notMixed() {
        assertFalse(
            calc.detectMixedCurrency(
                listOf(
                    ev("EUR", 5.0),
                    ev("EUR", 7.0),
                    // uncosted events ignored
                    ev(null, null),
                ),
            ),
        )
    }

    @Test fun twoCurrencies_isMixed() {
        assertTrue(
            calc.detectMixedCurrency(
                listOf(
                    ev("EUR", 5.0),
                    ev("USD", 7.0),
                ),
            ),
        )
    }
}
