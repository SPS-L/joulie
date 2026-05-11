// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import javax.inject.Inject

/**
 * Pure-domain CO₂ tracker.
 *
 * Surfaces two numbers side-by-side, but only when at least one event in
 * the period carries a per-event live grid-intensity captured at save time
 * from the Electricity Maps API:
 * - **EV emissions** — actual grid-attributable CO₂ from the energy
 *   the user charged into the car, summed across contributing events.
 * - **ICE counterfactual** — CO₂ a comparable petrol car would have
 *   emitted over the same distance, using the user's editable
 *   `iceBaselineLPer100km` preference.
 * - **Saved** is derived as `iceCo2Kg − evCo2Kg` (may be negative on
 *   high-grid-intensity periods or short distances; the Dashboard
 *   labels both numbers rather than relying on this aggregate).
 *
 * No-fallback contract: when an event has `gridIntensityGCo2PerKwh = null`
 * (either CO₂ was disabled at save time, or no Electricity Maps key was
 * set, or the fetch failed), the event contributes nothing to the EV
 * side. When NO event in the period contributes, [evCo2Kg] returns null
 * and the consumer hides the CO₂ surface entirely. This is by design —
 * see issue #1 follow-up: guessing CO₂ from a typed grid-intensity
 * preference was misleading and is gone.
 *
 * Methodology + sources documented in `docs/METHODOLOGY.md`.
 */
class CO2Calculator @Inject constructor() {

    /**
     * Total kg CO₂ emitted by the EV-side energy use across [events],
     * computed as `Σ kwhAdded × event.gridIntensityGCo2PerKwh / 1000`
     * over events where the per-event intensity is non-null and positive
     * and `kwhAdded > 0`.
     *
     * Returns `null` when no event in [events] contributes — that's the
     * "no live grid data anywhere in this period" signal and the
     * Dashboard / Charts CO₂ surface stays hidden. Returns `0.0` only on
     * the pathological "every contributing event had `kwhAdded = 0`" path.
     */
    fun evCo2Kg(events: List<ChargeEventEntity>): Double? {
        var total = 0.0
        var contributed = false
        for (e in events) {
            val intensity = e.gridIntensityGCo2PerKwh ?: continue
            if (intensity <= 0.0) continue
            if (e.kwhAdded <= 0.0) {
                // Mark as a contributing-event-row even if kWh is zero; the
                // user explicitly captured grid data for it, so the
                // aggregate is still "computable" — just zero.
                contributed = true
                continue
            }
            total += e.kwhAdded * intensity / G_PER_KG
            contributed = true
        }
        return if (contributed) total else null
    }

    /**
     * Total kg CO₂ a comparable ICE petrol car would have emitted over
     * [distanceKm], computed as
     * `(distanceKm / 100) × iceBaselineLPer100km × PETROL_CO2_KG_PER_LITRE`.
     *
     * Returns `0.0` for non-positive distance or non-positive baseline.
     * Caller is responsible for gating this on whether the EV side
     * contributed at all — surfacing the ICE counterfactual alongside a
     * null EV would be misleading.
     */
    fun iceCounterfactualCo2Kg(distanceKm: Double, iceBaselineLPer100km: Double): Double {
        if (distanceKm <= 0.0) return 0.0
        if (iceBaselineLPer100km <= 0.0) return 0.0
        return distanceKm / 100.0 * iceBaselineLPer100km * PETROL_CO2_KG_PER_LITRE
    }

    /**
     * Convenience: `iceCounterfactualCo2Kg − evCo2Kg`. Returns null when
     * the EV side itself is null (no live grid data). May be negative —
     * the Dashboard should label both numbers side-by-side rather than
     * relying on this aggregate.
     */
    fun savedCo2Kg(
        events: List<ChargeEventEntity>,
        distanceKm: Double,
        iceBaselineLPer100km: Double,
    ): Double? {
        val ev = evCo2Kg(events) ?: return null
        return iceCounterfactualCo2Kg(distanceKm, iceBaselineLPer100km) - ev
    }

    /**
     * Charts: cumulative EV CO₂ + cumulative ICE counterfactual,
     * indexed by event date. Used by the Charts CO₂ tab.
     *
     * EV contribution per event: `kwhAdded × event.gridIntensityGCo2PerKwh / 1000`
     * if that intensity is non-null and positive; otherwise the EV running
     * total does not advance for this event.
     *
     * ICE contribution per event: delta-odometer to the previous event in
     * the sorted-by-date sequence (matching `StatsCalculator.computeEfficiencyTrend`
     * convention from DESIGN.md §7). First event contributes nothing to
     * the ICE running total because there's no prior odometer to delta
     * against; rolled-back odometers contribute nothing but the chain
     * advances unconditionally.
     *
     * Returns empty list when no event in [events] has a per-event
     * intensity — the Charts CO₂ tab then renders the "enable Electricity
     * Maps to track CO₂" empty state.
     */
    data class CumulativePoint(
        val eventTimeMillis: Long,
        val cumulativeEvCo2Kg: Double,
        val cumulativeIceCo2Kg: Double,
    )

    fun cumulativeTrend(
        events: List<ChargeEventEntity>,
        iceBaselineLPer100km: Double,
    ): List<CumulativePoint> {
        if (events.isEmpty()) return emptyList()
        val anyHasIntensity = events.any { it.gridIntensityGCo2PerKwh != null }
        if (!anyHasIntensity) return emptyList()
        val sorted = events.sortedBy { it.eventDate }
        var evRunning = 0.0
        var iceRunning = 0.0
        val out = ArrayList<CumulativePoint>(sorted.size)
        var prevOdo: Double? = null
        for (e in sorted) {
            val intensity = e.gridIntensityGCo2PerKwh
            if (intensity != null && intensity > 0.0 && e.kwhAdded > 0.0) {
                evRunning += e.kwhAdded * intensity / G_PER_KG
            }
            if (prevOdo != null && iceBaselineLPer100km > 0.0) {
                val dist = e.odometerKm - prevOdo
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
