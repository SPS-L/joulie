package org.spsl.evtracker.core.model

import androidx.annotation.StringRes

data class SettingsUiState(
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    /** Non-null while the restore prompt is on screen. */
    val pendingRestoreLabel: String? = null
)

sealed class SettingsEvent {
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()
}
