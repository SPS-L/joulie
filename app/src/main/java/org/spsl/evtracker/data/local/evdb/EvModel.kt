// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.evdb

import com.google.gson.annotations.SerializedName

/**
 * Curated EV reference row consumed by the Add/Edit Car dialog (TASK-91).
 *
 * The bundled `app/src/main/assets/ev_models.json` and the remote
 * release-asset counterpart (refreshed monthly by TASK-92's
 * `scripts/update_ev_db.py`) ship one of these per vehicle. JSON keys
 * are snake_case for compactness; the [SerializedName] annotations let
 * Gson round-trip them into camelCase Kotlin fields without enabling
 * a non-default field-naming policy.
 *
 * @property make Canonical make name (e.g. `"Tesla"`).
 * @property model Canonical model name (e.g. `"Model 3"`).
 * @property variant Optional trim / drivetrain qualifier
 *   (e.g. `"Long Range RWD"`). Empty string when absent.
 * @property year Vehicle model year. Null when the upstream row
 *   doesn't carry a year.
 * @property batteryKwh Usable battery capacity in kWh. Always
 *   present (the TASK-92 pipeline drops rows without a battery
 *   value).
 * @property wltpKwhPer100km Manufacturer WLTP reference in
 *   kWh per 100 km. Null when the upstream row doesn't ship one.
 */
data class EvModel(
    @SerializedName("make") val make: String,
    @SerializedName("model") val model: String,
    @SerializedName("variant") val variant: String = "",
    @SerializedName("year") val year: Int? = null,
    @SerializedName("battery_kwh") val batteryKwh: Double,
    @SerializedName("wltp_kwh_100km") val wltpKwhPer100km: Double? = null,
)

/**
 * JSON root structure of `ev_models.json` as produced by
 * `scripts/update_ev_db.py` (TASK-92) and consumed by
 * [org.spsl.evtracker.data.repository.EvModelRepository] (TASK-91).
 */
data class EvModelDatabase(
    @SerializedName("version") val version: String = "",
    @SerializedName("source") val source: String = "",
    @SerializedName("vehicle_count") val vehicleCount: Int = 0,
    @SerializedName("vehicles") val vehicles: List<EvModel> = emptyList(),
)
