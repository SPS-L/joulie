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
     * Accept both `backup_version = 3` (pre-TASK-25) and `backup_version = 4`
     * (current). The chargeType `JsonDeserializer` routes through
     * [ChargeType.parseLegacy] so v3's `"DC"` decodes to
     * [ChargeType.DC_FAST] without a separate code path.
     */
    fun fromJson(json: String): BackupData {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed.backupVersion !in SUPPORTED_VERSIONS) {
            throw BackupVersionMismatch(parsed.backupVersion)
        }
        return parsed
    }

    companion object {
        private val SUPPORTED_VERSIONS = setOf(3, 4)
    }
}
