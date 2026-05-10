// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots.fixtures

import org.spsl.evtracker.core.model.AcDcSplit
import org.spsl.evtracker.core.model.CapacityPoint
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.core.model.EfficiencyPoint
import org.spsl.evtracker.core.model.EfficiencySeries
import org.spsl.evtracker.core.model.LocationSlice
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.domain.service.CO2Calculator
import java.util.Calendar
import java.util.TimeZone

/**
 * Canonical Charts fixture for Roborazzi screenshot tests (TASK-79).
 *
 * Deliberately deterministic: all timestamps derive from a frozen
 * calendar at 2026-01-01T12:00Z so month-bucket labels and date
 * subtitles stabilise across machines.
 *
 * One [ChartsScreenState.Loaded] is shared across all 7 tab tests;
 * only the `ARG_KIND` argument on the tab fragment differs, so each
 * tab reads its own slice of the same fixture.
 */
object ChartsFixtures {

    private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L

    /** Frozen anchor: 2026-01-01T12:00Z. Drives all derived timestamps. */
    val ANCHOR_MILLIS: Long = run {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(2026, Calendar.JANUARY, 1, 12, 0, 0)
        c.timeInMillis
    }

    /** Period start = 12 months before anchor (2025-01-01T12:00Z). */
    private val PERIOD_START_MILLIS: Long = ANCHOR_MILLIS - 365L * MILLIS_PER_DAY

    /** Returns the canonical [ChartsScreenState] used by every tab test. */
    fun canonical(): ChartsScreenState = ChartsScreenState(
        period = ChartsPeriod.Last12Months,
        distanceUnit = "km",
        primaryMetric = "kwh_per_100km",
        charts = canonicalLoaded(),
    )

    private fun canonicalLoaded(): ChartsUiState.Loaded = ChartsUiState.Loaded(
        periodHasEvents = true,
        mixedCurrency = false,
        periodCurrency = "EUR",
        periodStartMillis = PERIOD_START_MILLIS,
        trend = trend(),
        monthlyKwh = monthlyKwh(),
        monthlyCost = monthlyCost(),
        acDc = AcDcSplit(acCount = 18, dcCount = 6, acKwh = 360.0, dcKwh = 180.0),
        locations = listOf(
            LocationSlice(label = "Home", count = 12),
            LocationSlice(label = "Work", count = 6),
            LocationSlice(label = "Public", count = 4),
            LocationSlice(label = "Office", count = 2),
        ),
        capacity = capacityPoints(),
        nominalBatteryKwh = 60.0,
        derivedExcludedCount = 1,
        co2Cumulative = co2Points(),
    )

    private fun trend(): EfficiencySeries {
        // 18 AC + 6 DC points spread across the 12-month window.
        // kmPerKwh oscillates 5.5..7.0 to make the line visually non-flat.
        val acPoints = (0 until 18).map { i ->
            val t = PERIOD_START_MILLIS + (i.toLong() * 20 * MILLIS_PER_DAY)
            val kpkwh = 5.8 + 0.4 * kotlin.math.sin(i * 0.7)
            EfficiencyPoint(eventTimeMillis = t, kmPerKwh = kpkwh)
        }
        val dcPoints = (0 until 6).map { i ->
            val t = PERIOD_START_MILLIS + ((i.toLong() * 60 + 30) * MILLIS_PER_DAY)
            val kpkwh = 5.2 + 0.3 * kotlin.math.cos(i * 1.1)
            EfficiencyPoint(eventTimeMillis = t, kmPerKwh = kpkwh)
        }
        return EfficiencySeries(acPoints = acPoints, dcPoints = dcPoints)
    }

    private fun monthlyKwh(): List<MonthBucket> = (0 until 12).map { i ->
        val month = ((Calendar.JANUARY + i) % 12) + 1
        val year = if (Calendar.JANUARY + i < 12) 2025 else 2026
        // Oscillate 60..90 kWh/month to make bars visually varied.
        val kwh = 75.0 + 15.0 * kotlin.math.sin(i * 0.6)
        MonthBucket(year = year, month = month, totalKwh = kwh, totalCost = null, currency = null)
    }

    private fun monthlyCost(): List<MonthBucket> = (0 until 12).map { i ->
        val month = ((Calendar.JANUARY + i) % 12) + 1
        val year = if (Calendar.JANUARY + i < 12) 2025 else 2026
        // Oscillate 12..35 EUR/month.
        val cost = 23.5 + 11.0 * kotlin.math.cos(i * 0.5)
        MonthBucket(year = year, month = month, totalKwh = 0.0, totalCost = cost, currency = "EUR")
    }

    private fun capacityPoints(): List<CapacityPoint> {
        // 5 points showing gradual degradation 60.0 -> 57.5 over the period.
        // Three exact, two heuristic, mirroring a realistic mix.
        val deltas = listOf(60.0, 59.5, 59.0, 58.2, 57.5)
        val isExactFlags = listOf(true, true, false, true, false)
        return deltas.indices.map { i ->
            val t = PERIOD_START_MILLIS + ((i.toLong() * 70 + 20) * MILLIS_PER_DAY)
            CapacityPoint(eventDate = t, effectiveCapacityKwh = deltas[i], isExact = isExactFlags[i])
        }
    }

    private fun co2Points(): List<CO2Calculator.CumulativePoint> {
        // 12 cumulative points: EV grows 0 -> 100 kg, ICE grows 0 -> 280 kg.
        // The visible "saved CO2" widens over time, the diagnostic the tab is for.
        return (0 until 12).map { i ->
            val t = PERIOD_START_MILLIS + (i.toLong() * 30 * MILLIS_PER_DAY)
            val ev = i * (100.0 / 11.0)
            val ice = i * (280.0 / 11.0)
            CO2Calculator.CumulativePoint(
                eventTimeMillis = t,
                cumulativeEvCo2Kg = ev,
                cumulativeIceCo2Kg = ice,
            )
        }
    }
}
