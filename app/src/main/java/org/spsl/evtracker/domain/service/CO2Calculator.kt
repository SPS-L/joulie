// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import javax.inject.Inject

/**
 * Pure-domain CO₂ tracker.
 *
 * Surfaces two numbers side-by-side:
 * - **EV emissions** — actual grid-attributable CO₂ from the energy
 *   the user charged into the car.
 * - **ICE counterfactual** — CO₂ a comparable petrol car would have
 *   emitted over the same distance, using the user's editable
 *   `iceBaselineLPer100km` preference.
 * - **Saved** is derived as `iceCo2Kg − evCo2Kg` (may be negative on
 *   high-grid-intensity periods or short distances; the Dashboard
 *   labels both numbers rather than relying on this aggregate).
 *
 * Coefficients live in the companion. Methodology + sources documented
 * in `docs/METHODOLOGY.md`; do NOT change the coefficients without
 * updating that file.
 *
 * Static-grid caveat: this calculator reads `gridIntensityGCo2PerKwh`
 * from the user preference (default 577 = Cyprus 2025 average) and
 * applies it uniformly to every event in the period. Per-event live
 * grid-mix values are deferred until a free real-time data source is
 * available — see `docs/METHODOLOGY.md`.
 */
class CO2Calculator @Inject constructor() {

    /**
     * Total kg CO₂ emitted by the EV-side energy use across [events],
     * computed as `Σ kwhAdded × gridIntensityGCo2PerKwh / 1000`.
     *
     * Returns `0.0` for an empty list. Negative `kwhAdded` is treated
     * as 0 (defensive — a negative entry would make the total
     * misleadingly small).
     */
    fun evCo2Kg(events: List<ChargeEventEntity>, gridIntensityGCo2PerKwh: Double): Double {
        if (events.isEmpty()) return 0.0
        if (gridIntensityGCo2PerKwh <= 0.0) return 0.0
        val totalKwh = events.sumOf { e -> if (e.kwhAdded > 0.0) e.kwhAdded else 0.0 }
        return totalKwh * gridIntensityGCo2PerKwh / G_PER_KG
    }

    /**
     * Total kg CO₂ a comparable ICE petrol car would have emitted over
     * [distanceKm], computed as
     * `(distanceKm / 100) × iceBaselineLPer100km × PETROL_CO2_KG_PER_LITRE`.
     *
     * Returns `0.0` for non-positive distance or non-positive baseline.
     */
    fun iceCounterfactualCo2Kg(distanceKm: Double, iceBaselineLPer100km: Double): Double {
        if (distanceKm <= 0.0) return 0.0
        if (iceBaselineLPer100km <= 0.0) return 0.0
        return distanceKm / 100.0 * iceBaselineLPer100km * PETROL_CO2_KG_PER_LITRE
    }

    /**
     * Convenience: `iceCounterfactualCo2Kg − evCo2Kg`. May be negative
     * — the Dashboard should label both numbers side-by-side rather
     * than relying on this aggregate.
     */
    fun savedCo2Kg(
        events: List<ChargeEventEntity>,
        distanceKm: Double,
        iceBaselineLPer100km: Double,
        gridIntensityGCo2PerKwh: Double,
    ): Double {
        return iceCounterfactualCo2Kg(distanceKm, iceBaselineLPer100km) -
            evCo2Kg(events, gridIntensityGCo2PerKwh)
    }

    /**
     * Charts: cumulative EV CO₂ + cumulative ICE counterfactual,
     * indexed by event date. Used by the Charts CO₂ tab. Distance for
     * each event is the delta-odometer to the previous event in the
     * sorted-by-date sequence (matching `StatsCalculator.computeEfficiencyTrend`
     * convention from DESIGN.md §7); first event contributes nothing
     * to the ICE running total because there's no prior odometer to
     * delta against.
     */
    data class CumulativePoint(
        val eventTimeMillis: Long,
        val cumulativeEvCo2Kg: Double,
        val cumulativeIceCo2Kg: Double,
    )

    fun cumulativeTrend(
        events: List<ChargeEventEntity>,
        iceBaselineLPer100km: Double,
        gridIntensityGCo2PerKwh: Double,
    ): List<CumulativePoint> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.eventDate }
        var evRunning = 0.0
        var iceRunning = 0.0
        val out = ArrayList<CumulativePoint>(sorted.size)
        var prevOdo: Double? = null
        for (e in sorted) {
            // EV side: every event contributes (kwhAdded * intensity).
            if (e.kwhAdded > 0.0 && gridIntensityGCo2PerKwh > 0.0) {
                evRunning += e.kwhAdded * gridIntensityGCo2PerKwh / G_PER_KG
            }
            // ICE side: only delta-pairs contribute (no distance for the
            // first event). Skip rolled-back odometers (dist <= 0); the
            // chain advances unconditionally so a transient rollback
            // doesn't break the next valid delta.
            if (prevOdo != null && iceBaselineLPer100km > 0.0) {
                val dist = e.odometerKm - prevOdo!!
                if (dist > 0.0) {
                    iceRunning += dist / 100.0 * iceBaselineLPer100km * PETROL_CO2_KG_PER_LITRE
                }
            }
            prevOdo = e.odometerKm
            out += CumulativePoint(e.eventDate, evRunning, iceRunning)
        }
        return out
    }

    companion object {
        /**
         * EPA tank-to-wheel CO₂ emission factor for petrol (gasoline):
         * 8.887 kg CO₂ per gallon → 2.31 kg CO₂ per litre.
         * Source: US EPA "Greenhouse Gas Emissions from a Typical
         * Passenger Vehicle" (referenced in `docs/METHODOLOGY.md`).
         *
         * **Tank-to-wheel only** — does not include upstream refining /
         * transport emissions (well-to-wheel would add ~17%). The EV side
         * also uses tank-equivalent (grid intensity at point-of-charge),
         * keeping the comparison apples-to-apples.
         */
        const val PETROL_CO2_KG_PER_LITRE = 2.31

        /** Grams to kilograms. Pulled out as a constant so the formula reads cleanly. */
        const val G_PER_KG = 1000.0
    }
}
