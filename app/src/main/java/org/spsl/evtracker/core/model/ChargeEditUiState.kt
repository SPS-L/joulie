package org.spsl.evtracker.core.model

import org.spsl.evtracker.domain.service.CostMode

data class ChargeEditUiState(
    val mode: Mode = Mode.Create,
    val carId: Long = -1L,
    val eventDateMillis: Long,
    val odometer: String = "",
    val kwh: String = "",
    val chargeType: ChargeType = ChargeType.AC,
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
    val saving: Boolean = false,
    /**
     * Previous event's odometer (km) for the inline regression check.
     * Create mode: the latest entry's odometer for the active car (null when no history).
     * Edit mode: the chronologically-immediately-prior entry (null when editing the first event).
     */
    val previousOdometerKm: Double? = null,
    /**
     * Next event's odometer (km) for the edit-mode upper-bound check.
     * Set only when editing a non-latest event. Null in Create mode and when editing the latest entry.
     */
    val nextOdometerKm: Double? = null,
    /** True when the typed odometer (converted to km) is ≤ [previousOdometerKm]. Drives inline error + Save gate. */
    val odometerBelowPrevious: Boolean = false,
    /** True when the typed odometer (converted to km) is ≥ [nextOdometerKm]. Drives inline error + Save gate. */
    val odometerAboveNext: Boolean = false,
) {
    sealed class Mode {
        object Create : Mode()
        data class Edit(val eventId: Long) : Mode()
    }
}

data class LocationChips(
    val fixed: List<String> = listOf("Home", "Work", "Public"),
    val custom: List<String> = emptyList(),
)

sealed class ChargeEditEvent {
    object SavedAndExit : ChargeEditEvent()
}
