// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import android.net.Uri
import androidx.annotation.StringRes

data class SettingsUiState(
    // Drive backup:
    val driveEnabled: Boolean = false,
    val lastBackupAt: Long? = null,
    val isAuthInFlight: Boolean = false,
    /** Non-null while the restore prompt is on screen. */
    val pendingRestoreLabel: String? = null,
    /**
     * Raw `exported_at` value of the snapshot that drove the
     * pending prompt. Captured at prompt time so Skip / Confirm can
     * write the durable last-seen marker without re-reading the
     * remote backup. Always cleared together with [pendingRestoreLabel].
     */
    val pendingRestoreExportedAt: String? = null,

    // — manual Drive controls:
    /** True while [PushBackupNowUseCase] is in flight. Mutually exclusive with [isManualWipeRunning]. */
    val isManualBackupRunning: Boolean = false,
    /** True while [WipeRemoteBackupUseCase] is in flight. Mutually exclusive with [isManualBackupRunning]. */
    val isManualWipeRunning: Boolean = false,

    // Settings rows backing the Reset / Preferences flows.
    val primaryMetric: String = "kwh_per_100km",
    val distanceUnit: String = "km",
    val currency: String = "EUR",
    val theme: String = "system",
    /**
     * Persisted language tag (`""` = follow system; otherwise
     * `"en"` / `"el"` / `"tr"` / `"ru"`). Drives the Settings → Language
     * row's selected-option highlight in the picker dialog.
     */
    val languageTag: String = "",
    /** Petrol baseline (L/100km) for the CO₂ counterfactual. */
    val iceBaselineLPer100km: Double = 7.0,
    /** Grid carbon intensity (gCO₂/kWh) used for EV-side emissions. */
    val gridIntensityGCo2PerKwh: Double = 577.0,
    val activeCarId: Long = -1L,
    val activeCarName: String? = null,
    val customLocationCount: Int = 0,
)

sealed class SettingsEvent {
    // Drive:
    data class ShowRestorePrompt(val backupDateLabel: String) : SettingsEvent()
    object RestoreSucceeded : SettingsEvent()
    data class ShowError(@StringRes val msgRes: Int) : SettingsEvent()

    // Settings rows backing the Reset / Preferences flows:
    /**
     * Emitted after a metric→unit auto-flip. Carries a fully-localized
     * string-resource id; the Fragment shows the Snackbar via
     * `getString(msgRes)` with no format args. No display strings cross
     * the VM/UI boundary.
     */
    data class AutoFlipped(@StringRes val msgRes: Int) : SettingsEvent()
    data class LaunchCsvShareIntent(val uri: Uri) : SettingsEvent()
    object NavigateToWizard : SettingsEvent()

    // — manual Drive controls:
    object BackupNowSucceeded : SettingsEvent()
    data class BackupNowFailed(@StringRes val msgRes: Int) : SettingsEvent()
    object WipeSucceeded : SettingsEvent()
    data class WipeFailed(@StringRes val msgRes: Int) : SettingsEvent()
}
