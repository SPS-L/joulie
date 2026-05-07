// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import kotlin.math.max

/**
 * Derive `kwhAdded` from a state-of-charge delta when the user has
 * SoC % readings but no charger-reported kWh.
 *
 * Returns `(socAfter - socBefore) × nominalBatteryKwh`, clamped to ≥ 0.
 *
 * **Inputs are fractions in `[0.0, 1.0]`, not percentages.** A caller
 * holding a UI percent value must divide by 100 first; see
 * `ChargeEditViewModel.recomputeKwhFromSoc` for the canonical pattern.
 * `socBefore` or `socAfter` outside `[0, 1]`, or `nominalBatteryKwh ≤ 0`,
 * fail fast with `IllegalArgumentException` — passing raw percent values
 * would otherwise silently produce a kWh inflated by 100×.
 *
 * Negative deltas (in-range fractions where the user entered the SoC
 * fields out of order, e.g. `socBefore = 0.80`, `socAfter = 0.70`)
 * clamp to 0 rather than throw — the caller should treat zero as
 * "calculator could not produce a value" and leave the kWh field blank.
 *
 * Note: the result is *battery-side* kWh (energy that landed in the cells),
 * not *charger-delivered* kWh. AC charging loses ~10–15% to heat/conversion;
 * DC ~5%. The caller is responsible for tagging the resulting event with
 * `ChargeKwhSource.DERIVED_FROM_SOC` so capacity-degradation tracking skips it.
 */
object KwhFromSocCalculator {
    fun compute(socBefore: Double, socAfter: Double, nominalBatteryKwh: Double): Double {
        require(socBefore in 0.0..1.0 && socAfter in 0.0..1.0) {
            "SoC values must be fractions in [0,1]; got socBefore=$socBefore socAfter=$socAfter"
        }
        require(nominalBatteryKwh > 0.0) {
            "nominalBatteryKwh must be > 0; got $nominalBatteryKwh"
        }
        return max(0.0, (socAfter - socBefore) * nominalBatteryKwh)
    }
}
