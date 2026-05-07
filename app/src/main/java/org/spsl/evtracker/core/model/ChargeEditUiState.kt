// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

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
    /** SoC card collapsed by default; expand toggles via "+ Add SoC data". */
    val socExpanded: Boolean = false,
    /** Raw user input for SoC-before, expressed as a percentage string (`"0".."100"`). */
    val socBeforeText: String = "",
    /** Raw user input for SoC-after, expressed as a percentage string (`"0".."100"`). */
    val socAfterText: String = "",
    /** Localized error string-res for the SoC pair, or `null` when valid / both blank. */
    val socError: Int? = null,
    /**
     * Provenance flag persisted with the saved event. Set to
     * MEASURED on user-typed kWh, DERIVED_FROM_SOC by the in-form calculator.
     * In edit mode this is loaded from the existing entity so re-saves
     * preserve the original provenance unless the user edits.
     */
    val kwhSource: ChargeKwhSource = ChargeKwhSource.MEASURED,
    /**
     * True while the SoC-based kWh calculator is "active" — i.e.,
     * after the user tapped "Calculate kWh from SoC %" and before they
     * manually edited the kWh field. While active, the SoC banner is shown
     * and SoC-field changes auto-recompute the kWh field.
     */
    val kwhCalculatorActive: Boolean = false,
    /**
     * Nominal battery capacity (kWh) of the active car, used to
     * gate calculator-link visibility and as the multiplier for the kWh
     * derivation. `null` when the active car has no nominal capacity set
     * — the calculator is hidden in that case.
     */
    val nominalBatteryKwh: Double? = null,
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
