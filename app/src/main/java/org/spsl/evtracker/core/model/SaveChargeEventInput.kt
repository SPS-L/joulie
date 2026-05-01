package org.spsl.evtracker.core.model

import org.spsl.evtracker.domain.service.CostMode

data class SaveChargeEventInput(
    val eventId: Long? = null,
    val carId: Long,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: ChargeType,
    val costInput: CostInput? = null,
    val location: String? = null,
    val note: String = "",
    /** TASK-14: optional state-of-charge before charging, fraction `0.0..1.0`. */
    val socBefore: Double? = null,
    /** TASK-14: optional state-of-charge after charging, fraction `0.0..1.0`. */
    val socAfter: Double? = null,
)

data class CostInput(
    val value: Double,
    val mode: CostMode,
    val currency: String,
)

sealed class SaveChargeEventResult {
    data class Success(val eventId: Long) : SaveChargeEventResult()
    object OdometerNotIncreasing : SaveChargeEventResult()
}
