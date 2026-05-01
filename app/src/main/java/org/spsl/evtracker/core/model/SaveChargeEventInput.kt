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
