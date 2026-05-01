package org.spsl.evtracker.domain.service

import com.google.gson.GsonBuilder
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.ChargeType
import javax.inject.Inject

class BackupSerializer @Inject constructor() {
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(ChargeType::class.java, ChargeTypeJsonAdapter())
        .create()

    fun toJson(data: BackupData): String = gson.toJson(data)

    /**
     * Accept `backup_version` 3 (pre-TASK-25), 4 (TASK-25 — `ChargeType` enum),
     * and 5 (current — TASK-26 widened DTO ids `Int` → `Long`). Gson reads
     * narrower JSON numbers (Int) into wider Kotlin fields (Long) without
     * coercion, so v3/v4 backups round-trip into the v5 DTOs cleanly.
     * The chargeType `JsonDeserializer` routes through [ChargeType.parseLegacy]
     * so v3's `"DC"` decodes to [ChargeType.DC_FAST] without a separate code path.
     */
    fun fromJson(json: String): BackupData {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed.backupVersion !in SUPPORTED_VERSIONS) {
            throw BackupVersionMismatch(parsed.backupVersion)
        }
        return parsed
    }

    companion object {
        private val SUPPORTED_VERSIONS = setOf(3, 4, 5)
    }
}
