// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class Stats(
    val label: String,
    val totalKwh: Double,
    val totalDistanceKm: Double,
    val avgKmPerKwh: Double?,
    val avgKwhPer100Km: Double?,
    val avgMiPerKwh: Double?,
    val chargeCount: Int,
    // sum of costTotal across costed events; null when no cost data or mixedCurrency
    val totalCost: Double?,
    // the single currency of the costed events; null when no cost data or mixedCurrency
    val currency: String?,
    val costPerKm: Double?,
    val costPer100Km: Double?,
    val mixedCurrency: Boolean,
    /**
     * Latest effective battery capacity as a percentage of the
     * car's nominal `battery_kwh`. `null` when nominal capacity is unset
     * or no qualifying capacity points exist in the period. Values may
     * exceed 100% — over-estimation by the heuristic is information,
     * not a clamp target.
     */
    val batteryHealthPercent: Double? = null,
    /**
     * True when the chronologically latest capacity point that
     * fed [batteryHealthPercent] came from the heuristic full-charge
     * path (`CapacityPoint.isExact = false`) rather than the exact
     * SoC-delta path. Lets the Dashboard distinguish "92% confirmed by
     * SoC" from "92% inferred from a near-full charge" — different
     * confidence levels for the same number.
     */
    val batteryHealthIsHeuristic: Boolean = false,
    /**
     * True when both [batteryHealthIsHeuristic] holds AND
     * [batteryHealthPercent] is at or above
     * `CapacityEstimator.HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT`
     * (105%). Drives the "Estimated — heuristic may overestimate"
     * warning chip on the Dashboard battery-health card. Splitting
     * heuristic-vs-overestimated into two flags (rather than collapsing
     * into one) keeps the option open for future UX that wants to show
     * a softer "Estimated" tag without the overestimation warning.
     */
    val batteryHealthIsOverestimated: Boolean = false,
    /**
     * Kg CO₂ from EV-side energy use across the period
     * (`Σ kwhAdded × gridIntensityGCo2PerKwh / 1000`). `null` when
     * the user has not configured the grid-intensity preference (or
     * set it to 0) — the Dashboard CO₂ card hides entirely in that
     * case.
     */
    val evCo2Kg: Double? = null,
    /**
     * Kg CO₂ a comparable petrol car would have emitted over
     * the same period distance, computed as
     * `(distanceKm / 100) × iceBaselineLPer100km × 2.31`. `null` when
     * the user has not configured the ICE-baseline preference (or set
     * it to 0).
     */
    val iceCo2Kg: Double? = null,
)
