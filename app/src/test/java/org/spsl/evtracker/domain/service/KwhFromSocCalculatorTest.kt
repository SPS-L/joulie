package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KwhFromSocCalculatorTest {

    @Test
    fun typicalCharge_returnsDeltaTimesNominal() {
        // 60 kWh battery, 20% → 80% = 60% Δ × 60 kWh = 36 kWh.
        val kwh = KwhFromSocCalculator.compute(
            socBefore = 0.20,
            socAfter = 0.80,
            nominalBatteryKwh = 60.0,
        )
        assertEquals(36.0, kwh, 1e-9)
    }

    @Test
    fun fullCharge_zeroToOne_returnsNominal() {
        val kwh = KwhFromSocCalculator.compute(
            socBefore = 0.0,
            socAfter = 1.0,
            nominalBatteryKwh = 75.0,
        )
        assertEquals(75.0, kwh, 1e-9)
    }

    @Test
    fun zeroDelta_returnsZero() {
        val kwh = KwhFromSocCalculator.compute(
            socBefore = 0.50,
            socAfter = 0.50,
            nominalBatteryKwh = 40.0,
        )
        assertEquals(0.0, kwh, 1e-9)
    }

    @Test
    fun negativeDelta_clampsToZero() {
        // User mis-enters socBefore > socAfter (e.g., reading rounded up).
        // Don't fail the save — clamp to 0 so the kWh field is sensible.
        val kwh = KwhFromSocCalculator.compute(
            socBefore = 0.80,
            socAfter = 0.70,
            nominalBatteryKwh = 60.0,
        )
        assertEquals(0.0, kwh, 1e-9)
    }

    @Test
    fun socBefore_rawPercent_throws() {
        // 80 instead of 0.80 — a future caller passing percent values
        // would otherwise produce a kWh inflated by 100×.
        assertThrows(IllegalArgumentException::class.java) {
            KwhFromSocCalculator.compute(
                socBefore = 80.0,
                socAfter = 0.90,
                nominalBatteryKwh = 60.0,
            )
        }
    }

    @Test
    fun socAfter_negativeFraction_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            KwhFromSocCalculator.compute(
                socBefore = 0.20,
                socAfter = -0.1,
                nominalBatteryKwh = 60.0,
            )
        }
    }

    @Test
    fun nominalBatteryKwh_zero_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            KwhFromSocCalculator.compute(
                socBefore = 0.20,
                socAfter = 0.80,
                nominalBatteryKwh = 0.0,
            )
        }
    }

    @Test
    fun nominalBatteryKwh_negative_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            KwhFromSocCalculator.compute(
                socBefore = 0.20,
                socAfter = 0.80,
                nominalBatteryKwh = -10.0,
            )
        }
    }
}
