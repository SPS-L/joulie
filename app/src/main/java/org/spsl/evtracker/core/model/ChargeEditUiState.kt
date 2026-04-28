package org.spsl.evtracker.core.model

import org.spsl.evtracker.domain.service.CostMode

data class ChargeEditUiState(
    val mode: Mode = Mode.Create,
    val carId: Int = -1,
    val eventDateMillis: Long = System.currentTimeMillis(),
    val odometer: String = "",
    val kwh: String = "",
    val chargeType: String = "AC",
    val location: String = "",
    val locationChips: LocationChips = LocationChips(),
    val costExpanded: Boolean = false,
    val costMode: CostMode = CostMode.TOTAL,
    val costValue: String = "",
    val note: String = "",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val odometerError: Int? = null,
    val kwhError: Int? = null,
    val saving: Boolean = false
) {
    sealed class Mode {
        object Create : Mode()
        data class Edit(val eventId: Int) : Mode()
    }
}

data class LocationChips(
    val fixed: List<String> = listOf("Home", "Work", "Public"),
    val custom: List<String> = emptyList()
)

sealed class ChargeEditEvent {
    object SavedAndExit : ChargeEditEvent()
}
