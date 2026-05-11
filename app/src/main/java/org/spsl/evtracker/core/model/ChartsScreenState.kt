// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class ChartsScreenState(
    val period: ChartsPeriod = ChartsPeriod.Last12Months,
    /** "km" | "miles". Drives km↔miles render-time conversion when primaryMetric == "mi_per_kwh". */
    val distanceUnit: String = "km",
    /** "km_per_kwh" | "kwh_per_100km" | "mi_per_kwh". Drives the Trend Y-axis transform. */
    val primaryMetric: String = "kwh_per_100km",
    val charts: ChartsUiState = ChartsUiState.Loading,
    /** Snapshot of the current "right now" grid carbon intensity (TASK-87).
     *  Non-null only when CO₂ tracking is on, an API key is set, and the
     *  persistent Electricity Maps cache is fresh (< 1 h, current zone).
     *  The Charts CO₂ tab renders this as a bucket-tinted banner above the
     *  cumulative-kg chart — the chart's y-axis is kg CO₂ so we can't
     *  overlay a g/kWh value as a horizontal reference line, but surfacing
     *  it as a banner alongside the chart gives the user the same context. */
    val currentCarbonReady: CarbonIntensityUiState.Ready? = null,
)

sealed class ChartsEvent {
    object OpenCustomRangePicker : ChartsEvent()
    object NavigateToCars : ChartsEvent()
    object NavigateToChargeEdit : ChartsEvent()
}
