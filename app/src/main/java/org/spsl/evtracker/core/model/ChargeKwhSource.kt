// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

/**
 * Provenance flag on a `ChargeEventEntity.kwhAdded` value.
 *
 * `MEASURED` is the user-entered or charger-reported kWh — the only flavour
 * eligible for the TASK-14 capacity-degradation tracker.
 *
 * `DERIVED_FROM_SOC` is computed from `(socAfter - socBefore) × Car.nominalBatteryKwh`
 * via the in-form calculator. Capacity tracking must skip these events because
 * the math is tautological (the derived `kwhAdded` divided by the same SoC
 * delta returns `nominalBatteryKwh` exactly).
 */
enum class ChargeKwhSource {
    MEASURED,
    DERIVED_FROM_SOC,
    ;

    companion object {
        fun parseLegacy(s: String): ChargeKwhSource =
            when (s) {
                "MEASURED" -> MEASURED
                "DERIVED_FROM_SOC" -> DERIVED_FROM_SOC
                else -> MEASURED
            }
    }
}
