package org.spsl.evtracker.core.model

import android.net.Uri
import androidx.annotation.StringRes

data class SettingsUiState(
    // Drive (E):
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    /** Non-null while the restore prompt is on screen. */
    val pendingRestoreLabel: String? = null,

    // F1:
    val primaryMetric: String = "kwh_per_100km",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val theme: String = "system",
    val activeCarId: Int = -1,
    val activeCarName: String? = null,
    val customLocationCount: Int = 0,
)

sealed class SettingsEvent {
    // E:
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()

    // F1:
    /**
     * Emitted after a metric→unit auto-flip. Carries a fully-localized string-resource
     * id; the Fragment shows the Snackbar via `getString(msgRes)` with no format args.
     * Matches the E-era ShowError contract — no display strings cross the VM/UI boundary.
     */
    data class AutoFlipped(@StringRes val msgRes: Int) : SettingsEvent()
    data class LaunchCsvShareIntent(val uri: Uri) : SettingsEvent()
    object NavigateToWizard : SettingsEvent()
}
