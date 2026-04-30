package org.spsl.evtracker.domain.service

import com.google.gson.GsonBuilder
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import javax.inject.Inject

class BackupSerializer @Inject constructor() {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun toJson(data: BackupData): String = gson.toJson(data)

    fun fromJson(json: String): BackupData {
        val parsed = gson.fromJson(json, BackupData::class.java)
        if (parsed.backupVersion != BackupData.CURRENT_VERSION) {
            throw BackupVersionMismatch(parsed.backupVersion)
        }
        return parsed
    }
}
