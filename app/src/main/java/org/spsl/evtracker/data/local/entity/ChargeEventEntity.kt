// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.spsl.evtracker.core.model.ChargeKwhSource
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
     * `null` when the user did not enter SoC data on this event.
     */
    val socBefore: Double? = null,
    /**
     * State of charge after charging, stored as a fraction in `0.0..1.0`.
     * `null` when the user did not enter SoC data on this event.
     */
    val socAfter: Double? = null,
    /**
     * Provenance of [kwhAdded]. `MEASURED` is the user-entered or
     * charger-reported value; `DERIVED_FROM_SOC` is computed from
     * `(socAfter - socBefore) × Car.nominalBatteryKwh` via the in-form
     * calculator. Capacity-degradation tracking skips `DERIVED`
     * events because the math is tautological.
     *
     * `@ColumnInfo(defaultValue = "MEASURED")` is the SQL-level default
     * Room emits when synthesising the v6→v7 `@AutoMigration`'s
     * `ALTER TABLE … ADD COLUMN kwhSource TEXT NOT NULL DEFAULT 'MEASURED'`.
     * Matches the original manual `MIGRATION_6_7` SQL byte-for-byte.
     */
    @ColumnInfo(defaultValue = "MEASURED")
    val kwhSource: ChargeKwhSource = ChargeKwhSource.MEASURED,
    /**
     * Grid carbon intensity in gCO₂/kWh captured at the time of this
     * charge event. `null` when CO₂ tracking is disabled OR the
     * Electricity Maps fetch failed AND no manual fallback was set.
     *
     * Stored per-event so the historical CO₂ chart reflects the actual
     * grid mix at the time of charging — switching zones or watching the
     * grid decarbonise must not retroactively rewrite past emissions.
     * Added in v8 via Room `@AutoMigration` (no `@ColumnInfo` default
     * needed: the column is nullable, so legacy rows correctly migrate to
     * `NULL`).
     */
    @ColumnInfo(name = "grid_intensity_g_co2_per_kwh")
    val gridIntensityGCo2PerKwh: Double? = null,
    val createdAt: Long,
)
