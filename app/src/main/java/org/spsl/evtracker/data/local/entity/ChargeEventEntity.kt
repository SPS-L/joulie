package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.spsl.evtracker.core.model.ChargeType

@Entity(
    tableName = "charge_events",
    foreignKeys = [
        ForeignKey(
            entity = CarEntity::class,
            parentColumns = ["id"],
            childColumns = ["carId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["carId", "eventDate"]),
        Index("chargeType"),
        Index("location"),
    ],
)
data class ChargeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val carId: Long,
    val eventDate: Long,
    val odometerKm: Double,
    val kwhAdded: Double,
    val chargeType: ChargeType = ChargeType.AC,
    val costTotal: Double? = null,
    val costPerKwh: Double? = null,
    val currency: String? = null,
    val location: String? = null,
    val note: String = "",
    /**
     * State of charge before charging, stored as a fraction in `0.0..1.0`.
     * `null` when the user did not enter SoC data on this event (TASK-14).
     */
    val socBefore: Double? = null,
    /**
     * State of charge after charging, stored as a fraction in `0.0..1.0`.
     * `null` when the user did not enter SoC data on this event (TASK-14).
     */
    val socAfter: Double? = null,
    val createdAt: Long,
)
