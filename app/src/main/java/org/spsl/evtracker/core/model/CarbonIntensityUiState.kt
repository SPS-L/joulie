// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import org.spsl.evtracker.R

/**
 * Render state for the dashboard carbon-intensity pill (TASK-82).
 *
 * Derived from the persistent Electricity Maps cache + the live Settings
 * flags by [org.spsl.evtracker.domain.service.CarbonIntensityFormatter].
 * The Fragment renders one of four shapes:
 *
 *  - [Hidden]: pill is `View.GONE`. Either CO₂ tracking is off, or the
 *    user has not entered an API key. No fetch will be attempted.
 *  - [Loading]: spinner / skeleton state while the first refresh is in
 *    flight. The user has CO₂ on AND a key set, but no valid cache yet.
 *  - [Ready]: the cached value is for the current zone and within the
 *    1-hour TTL. Show value + bucket label + "updated X ago".
 *  - [Error]: refresh failed. The pill becomes tappable with a
 *    reason-specific message and a "tap to retry" affordance.
 */
sealed class CarbonIntensityUiState {
    data object Hidden : CarbonIntensityUiState()
    data object Loading : CarbonIntensityUiState()
    data class Ready(
        val intensityGCo2PerKwh: Double,
        val bucket: CarbonIntensityBucket,
        val fetchedAtMs: Long,
    ) : CarbonIntensityUiState()
    data class Error(val reason: CarbonIntensityErrorReason) : CarbonIntensityUiState()
}

/**
 * Reasons the carbon-intensity pill can be in [CarbonIntensityUiState.Error].
 *
 * Each value owns the localized string resource the renderer should
 * display in the pill's value slot. Keep this enum stable — the
 * Roborazzi baselines reference each member by file-suffix name.
 */
enum class CarbonIntensityErrorReason(
    @StringRes val labelRes: Int,
    @StringRes val a11yRes: Int,
) {
    /** HTTP 401/403. Wrong API key, or the free-tier key was issued for
     *  a different zone than the one Joulie is requesting. */
    AUTH(
        labelRes = R.string.carbon_intensity_error_auth,
        a11yRes = R.string.carbon_intensity_a11y_error_auth,
    ),

    /** Transport failure: no internet, DNS, TLS, timeout. */
    NETWORK(
        labelRes = R.string.carbon_intensity_error_network,
        a11yRes = R.string.carbon_intensity_a11y_error_network,
    ),

    /** HTTP 429. Free tier shares 50 req/h across all clients using
     *  this key, so it's possible to trip this even with our throttle. */
    RATE_LIMITED(
        labelRes = R.string.carbon_intensity_error_rate_limited,
        a11yRes = R.string.carbon_intensity_a11y_error_rate_limited,
    ),

    /** HTTP 5xx — Electricity Maps unavailable, retry later. */
    SERVER(
        labelRes = R.string.carbon_intensity_error_server,
        a11yRes = R.string.carbon_intensity_a11y_error_server,
    ),

    /** Catch-all for "something unexpected went wrong" — kept as a real
     *  enum value rather than a nullable Error.reason so the formatter
     *  always has a renderable state when it doesn't know what failed. */
    UNKNOWN(
        labelRes = R.string.carbon_intensity_error_unknown,
        a11yRes = R.string.carbon_intensity_a11y_error_unknown,
    ),
    ;

    @get:ColorRes
    val backgroundColorRes: Int = R.color.md_theme_light_surfaceVariant

    @get:ColorRes
    val textColorRes: Int = R.color.md_theme_light_onSurfaceVariant
}

/**
 * Five-band bucket scheme sampled from the Electricity Maps gradient
 * (0 → 1500 gCO₂eq/kWh). The colour IS the action signal; the bucket
 * label name and contentDescription back it up for colour-blind users.
 *
 * Boundary convention: `< upperExclusive`. So 150 → [LOW], 400 → [MODERATE],
 * etc. The very-high band has no upper bound; any value ≥ 900 lands there.
 */
enum class CarbonIntensityBucket(
    val upperExclusive: Double,
    @ColorRes val backgroundColorRes: Int,
    @ColorRes val textColorRes: Int,
    @StringRes val labelRes: Int,
) {
    VERY_LOW(
        upperExclusive = 150.0,
        backgroundColorRes = R.color.carbon_very_low,
        textColorRes = R.color.carbon_text_on_light,
        labelRes = R.string.carbon_bucket_very_low,
    ),
    LOW(
        upperExclusive = 400.0,
        backgroundColorRes = R.color.carbon_low,
        textColorRes = R.color.carbon_text_on_light,
        labelRes = R.string.carbon_bucket_low,
    ),
    MODERATE(
        upperExclusive = 650.0,
        backgroundColorRes = R.color.carbon_moderate,
        textColorRes = R.color.carbon_text_on_light,
        labelRes = R.string.carbon_bucket_moderate,
    ),
    HIGH(
        upperExclusive = 900.0,
        backgroundColorRes = R.color.carbon_high,
        textColorRes = R.color.carbon_text_on_dark,
        labelRes = R.string.carbon_bucket_high,
    ),
    VERY_HIGH(
        upperExclusive = Double.POSITIVE_INFINITY,
        backgroundColorRes = R.color.carbon_very_high,
        textColorRes = R.color.carbon_text_on_dark,
        labelRes = R.string.carbon_bucket_very_high,
    ),
    ;

    companion object {
        fun forValue(intensityGCo2PerKwh: Double): CarbonIntensityBucket =
            entries.first { intensityGCo2PerKwh < it.upperExclusive }
    }
}
