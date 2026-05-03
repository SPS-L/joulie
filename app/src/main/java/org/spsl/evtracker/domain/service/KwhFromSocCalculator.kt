package org.spsl.evtracker.domain.service

import kotlin.math.max

/**
 * TASK-43: derive `kwhAdded` from a state-of-charge delta when the user has
 * SoC % readings but no charger-reported kWh.
 *
 * Returns `(socAfter - socBefore) × nominalBatteryKwh`, clamped to ≥ 0.
 *
 * Negative deltas (user entered SoC fields out of order) clamp to 0 rather
 * than throw — the caller should treat zero as "calculator could not produce
 * a value" and leave the kWh field blank.
 *
 * Note: the result is *battery-side* kWh (energy that landed in the cells),
 * not *charger-delivered* kWh. AC charging loses ~10–15% to heat/conversion;
 * DC ~5%. The caller is responsible for tagging the resulting event with
 * `ChargeKwhSource.DERIVED_FROM_SOC` so capacity-degradation tracking skips it.
 */
object KwhFromSocCalculator {
    fun compute(socBefore: Double, socAfter: Double, nominalBatteryKwh: Double): Double =
        max(0.0, (socAfter - socBefore) * nominalBatteryKwh)
}
