package org.spsl.evtracker.domain.repository

interface SettingsWriter {
    suspend fun setActiveCarId(id: Int)
    suspend fun setDriveEnabled(enabled: Boolean)
}
