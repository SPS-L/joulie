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
)

sealed class ChartsEvent {
    object OpenCustomRangePicker : ChartsEvent()
    object NavigateToCars : ChartsEvent()
    object NavigateToChargeEdit : ChartsEvent()
}
