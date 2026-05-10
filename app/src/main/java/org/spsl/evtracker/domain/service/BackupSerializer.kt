// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import com.google.gson.GsonBuilder
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.ChargeType
import javax.inject.Inject

class BackupSerializer @Inject constructor() {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(ChargeType::class.java, ChargeTypeJsonAdapter())
        .registerTypeAdapter(ChargeKwhSource::class.java, ChargeKwhSourceJsonAdapter())
        .create()

    fun toJson(data: BackupData): String = gson.toJson(data)

    /**
     * Accept `backup_version` 3 (legacy `"DC"` chargeType),
     * 4 (`ChargeType` enum), 5 (widened DTO ids `Int` → `Long`),
     * 6 (optional `socBefore`/`socAfter` fields on charge events),
     * 7 (`kwhSource` provenance flag), and 8 (current; per-event
     * `gridIntensityGCo2PerKwh` from the Electricity Maps live feed).
     *
     * Older backups simply leave new fields at their defaults — Gson
     * tolerates absent JSON keys for fields with Kotlin defaults. The
     * chargeType `JsonDeserializer` routes through [ChargeType.parseLegacy]
     * so v3's `"DC"` decodes to [ChargeType.DC_FAST]; the kwhSource
     * adapter similarly defaults to `MEASURED` for v3..v6 backups, which
     * is the correct backfill (those events predate the in-form calculator).
     * v3..v7 backups have no per-event grid intensity, which correctly
     * round-trips to `null` on the entity.
     */
    fun fromJson(json: String): BackupData {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed.backupVersion !in SUPPORTED_VERSIONS) {
            throw BackupVersionMismatch(parsed.backupVersion)
        }
        return parsed
    }

    companion object {
        private val SUPPORTED_VERSIONS = setOf(3, 4, 5, 6, 7, 8)
    }
}
