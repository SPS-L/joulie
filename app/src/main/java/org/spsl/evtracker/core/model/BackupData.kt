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
        const val CURRENT_VERSION = 3

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
    @SerializedName("id") val id: Int,
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
    @SerializedName("id") val id: Int,
    @SerializedName("car_id") val carId: Int,
    @SerializedName("event_date") val eventDate: Long,
    @SerializedName("odometer_km") val odometerKm: Double,
    @SerializedName("kwh_added") val kwhAdded: Double,
    @SerializedName("charge_type") val chargeType: String,
    @SerializedName("cost_total") val costTotal: Double?,
    @SerializedName("cost_per_kwh") val costPerKwh: Double?,
    @SerializedName("currency") val currency: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("note") val note: String,
    @SerializedName("created_at") val createdAt: Long,
) {
    fun toEntity() = ChargeEventEntity(
        id, carId, eventDate, odometerKm, kwhAdded, chargeType,
        costTotal, costPerKwh, currency, location, note, createdAt,
    )

    companion object {
        fun fromEntity(e: ChargeEventEntity) = ChargeEventDto(
            e.id, e.carId, e.eventDate, e.odometerKm, e.kwhAdded, e.chargeType,
            e.costTotal, e.costPerKwh, e.currency, e.location, e.note, e.createdAt,
        )
    }
}

data class CustomLocationDto(
    @SerializedName("id") val id: Int = 0,
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
