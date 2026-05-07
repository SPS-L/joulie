// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

sealed class ChartsUiState {

    object Loading : ChartsUiState()

    /** No rows in cars OR activeCarId == -1. Period chips and TabLayout are hidden. */
    object NoCar : ChartsUiState()

    /** Active car exists but the per-car charge_events stream is empty. Period chips
     *  and TabLayout are hidden; full-screen "Log charge" CTA shown. */
    object NoEvents : ChartsUiState()

    data class Loaded(
        val periodHasEvents: Boolean,
        val mixedCurrency: Boolean,
        val periodCurrency: String?,
        /** Start of the resolved period window, used by the trend tab to express the
         *  Line chart's x-axis as a day offset from this anchor. Storing raw
         *  epoch millis as a Float would alias because Float only has ~7 decimal
         *  digits of integer precision while modern timestamps need ~13. */
        val periodStartMillis: Long,
        val trend: EfficiencySeries,
        val monthlyKwh: List<MonthBucket>,
        val monthlyCost: List<MonthBucket>,
        val acDc: AcDcSplit,
        val locations: List<LocationSlice>,
        /** Capacity points for the active car, sorted by eventDate. Empty when the
         *  car has no nominal `battery_kwh` set OR fewer than 3 qualifying points exist. */
        val capacity: List<CapacityPoint>,
        /** Nominal battery capacity (kWh) for the active car; used to draw the
         *  reference line on the degradation chart. `null` when unset on the car. */
        val nominalBatteryKwh: Double?,
        /** Count of `DERIVED_FROM_SOC` events in the current period.
         *  Drives the degradation-tab banner ("N estimated events excluded …").
         *  These events are silently skipped by [CapacityEstimator]; the banner
         *  surfaces the exclusion so the user can reconcile event count with
         *  rendered points. */
        val derivedExcludedCount: Int,
        /** Cumulative CO₂ trend (EV-side + ICE counterfactual) across
         *  the period's events. Empty list when both prefs are 0 / unset —
         *  the CO₂ tab then shows the empty-state pointing the user to
         *  Settings → CO₂. */
        val co2Cumulative: List<org.spsl.evtracker.domain.service.CO2Calculator.CumulativePoint>,
    ) : ChartsUiState()
}
