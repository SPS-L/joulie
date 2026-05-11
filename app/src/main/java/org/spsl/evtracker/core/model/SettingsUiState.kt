// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import android.net.Uri
import androidx.annotation.StringRes

data class SettingsUiState(
    // EV-database refresh (TASK-91):
    /** Idle / Loading / Success / Failure of the most recent refresh. */
    val evDbState: EvDbUpdateState = EvDbUpdateState.Idle,
    /** Epoch-ms of the last successful refresh; 0 = bundled fallback. */
    val evDbLastUpdatedAt: Long = 0L,
    /** Cached `version` from the JSON root; "" = bundled fallback. */
    val evDbVersion: String = "",
    /** Cached vehicle count; 0 = bundled fallback. */
    val evDbVehicleCount: Int = 0,
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
    /** Opt-in master switch for CO₂ tracking surfaces. */
    val co2Enabled: Boolean = false,
    /** Electricity Maps API key. Empty string = unset. */
    val electricityMapsApiKey: String = "",
    /** Electricity Maps grid-zone code (uppercase, e.g. `"CY"`). */
    val electricityMapsZone: String = "CY",
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

    // — Electricity Maps API key probe (TASK-90):
    /** Probe is in flight — show "Testing…" in the dialog. */
    object ApiKeyTestStarted : SettingsEvent()

    /** Probe came back. [resultStringRes] is a pre-resolved string id;
     *  the Fragment renders it as-is. On Success, [zone] + [intensity]
     *  fill the format args so the user sees the actual value the API
     *  returned (a stronger sanity check than just "OK"). */
    data class ApiKeyTestFinished(
        @StringRes val resultStringRes: Int,
        val zone: String? = null,
        val intensity: Double? = null,
    ) : SettingsEvent()
}
