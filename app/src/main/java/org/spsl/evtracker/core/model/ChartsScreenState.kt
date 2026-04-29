package org.spsl.evtracker.core.model

data class ChartsScreenState(
    val period: ChartsPeriod = ChartsPeriod.Last12Months,
    /** "km" | "miles". Drives trend Y-axis label and km↔miles render-time conversion. */
    val distanceUnit: String = "km",
    val charts: ChartsUiState = ChartsUiState.Loading
)

sealed class ChartsEvent {
    object OpenCustomRangePicker : ChartsEvent()
    object NavigateToCars : ChartsEvent()
    object NavigateToChargeEdit : ChartsEvent()
}
