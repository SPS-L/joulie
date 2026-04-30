package org.spsl.evtracker.core.model

import org.spsl.evtracker.data.local.entity.CarEntity

data class DashboardScreenState(
    val cars: List<CarEntity> = emptyList(),
    val activeCarId: Int = -1,
    val period: DashboardPeriod = DashboardPeriod.Last30Days,
    val filter: ChargeTypeFilter = ChargeTypeFilter.ALL,
    val primaryMetric: String = "kwh_per_100km",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val dashboard: DashboardUiState = DashboardUiState(),
)

sealed class DashboardEvent {
    object NavigateToChargeEdit : DashboardEvent()
    object NavigateToCars : DashboardEvent()
    object NavigateToManageCars : DashboardEvent()
}
