// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import com.google.gson.annotations.SerializedName
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import java.time.Instant

data class BackupData(
    @SerializedName("backup_version") val backupVersion: Int = CURRENT_VERSION,
    @SerializedName("exported_at") val exportedAt: String,
    @SerializedName("cars") val cars: List<CarDto>,
    @SerializedName("charge_events") val chargeEvents: List<ChargeEventDto>,
    @SerializedName("custom_locations") val customLocations: List<CustomLocationDto>,
) {
    companion object {
        const val CURRENT_VERSION = 7

        fun fromEntities(
            cars: List<CarEntity>,
            events: List<ChargeEventEntity>,
            locations: List<CustomLocationEntity>,
            now: Long,
        ): BackupData = BackupData(
            backupVersion = CURRENT_VERSION,
            exportedAt = Instant.ofEpochMilli(now).toString(),
            cars = cars.map { CarDto.fromEntity(it) },
            chargeEvents = events.map { ChargeEventDto.fromEntity(it) },
            customLocations = locations.map { CustomLocationDto.fromEntity(it) },
        )
    }

    fun toEntities(): Triple<List<CarEntity>, List<ChargeEventEntity>, List<CustomLocationEntity>> =
        Triple(
            cars.map { it.toEntity() },
            chargeEvents.map { it.toEntity() },
            customLocations.map { it.toEntity() },
        )
}

data class CarDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("make") val make: String,
    @SerializedName("model") val model: String,
    @SerializedName("year") val year: Int?,
    @SerializedName("battery_kwh") val batteryKwh: Double?,
    @SerializedName("created_at") val createdAt: Long,
) {
    fun toEntity() = CarEntity(id, name, make, model, year, batteryKwh, createdAt)

    companion object {
        fun fromEntity(e: CarEntity) =
            CarDto(e.id, e.name, e.make, e.model, e.year, e.batteryKwh, e.createdAt)
    }
}

data class ChargeEventDto(
    @SerializedName("id") val id: Long,
    @SerializedName("car_id") val carId: Long,
    @SerializedName("event_date") val eventDate: Long,
    @SerializedName("odometer_km") val odometerKm: Double,
    @SerializedName("kwh_added") val kwhAdded: Double,
    @SerializedName("charge_type") val chargeType: ChargeType,
    @SerializedName("cost_total") val costTotal: Double?,
    @SerializedName("cost_per_kwh") val costPerKwh: Double?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("note") val note: String,
    /** TASK-14: optional state-of-charge fields. Absent on v3/v4/v5 backups. */
    @SerializedName("soc_before") val socBefore: Double? = null,
    @SerializedName("soc_after") val socAfter: Double? = null,
    /**
     * TASK-43: provenance flag on `kwhAdded`. Absent on v3..v6 backups; the
     * field is declared **nullable** because Gson constructs Kotlin data
     * classes via `sun.misc.Unsafe.allocateInstance`, which bypasses
     * primary-constructor defaults — a non-null field with a Kotlin default
     * still ends up as `null` when the JSON key is missing. Coalesce in
     * [toEntity] so the entity's non-null contract holds. The right
     * backfill is `MEASURED` (legacy events predate the in-form calculator).
     */
    @SerializedName("kwh_source") val kwhSource: ChargeKwhSource? = null,
    @SerializedName("created_at") val createdAt: Long,
) {
    fun toEntity() = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        chargeType = chargeType,
        costTotal = costTotal,
        costPerKwh = costPerKwh,
        currency = currency,
        location = location,
        note = note,
        socBefore = socBefore,
        socAfter = socAfter,
        kwhSource = kwhSource ?: ChargeKwhSource.MEASURED,
        createdAt = createdAt,
    )

    companion object {
        fun fromEntity(e: ChargeEventEntity) = ChargeEventDto(
            id = e.id,
            carId = e.carId,
            eventDate = e.eventDate,
            odometerKm = e.odometerKm,
            kwhAdded = e.kwhAdded,
            chargeType = e.chargeType,
            costTotal = e.costTotal,
            costPerKwh = e.costPerKwh,
            currency = e.currency,
            location = e.location,
            note = e.note,
            socBefore = e.socBefore,
            socAfter = e.socAfter,
            kwhSource = e.kwhSource,
            createdAt = e.createdAt,
        )
    }
}

data class CustomLocationDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("label") val label: String,
    @SerializedName("use_count") val useCount: Int,
    @SerializedName("last_used") val lastUsed: Long,
) {
    fun toEntity() = CustomLocationEntity(id, label, useCount, lastUsed)

    companion object {
        fun fromEntity(e: CustomLocationEntity) =
            CustomLocationDto(e.id, e.label, e.useCount, e.lastUsed)
    }
}

class BackupVersionMismatch(val actual: Int) :
    RuntimeException("Backup version $actual is incompatible with current version ${BackupData.CURRENT_VERSION}")
