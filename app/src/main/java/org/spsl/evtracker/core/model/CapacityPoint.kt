// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

/**
 * One data point for the battery-degradation chart.
 *
 * @property eventDate epoch millis of the originating charge event
 * @property effectiveCapacityKwh estimated effective battery capacity in kWh.
 *   Computed exactly as `kwhAdded / (socAfter - socBefore)` when both SoC
 *   fields are present on the source event, or approximated by `kwhAdded`
 *   when only the heuristic ("kWh added ≥ 80% of nominal") qualifies it.
 * @property isExact `true` when the point came from explicit SoC values,
 *   `false` when it came from the heuristic. The Charts tab styles
 *   heuristic points differently so the distinction is visible.
 */
data class CapacityPoint(
    val eventDate: Long,
    val effectiveCapacityKwh: Double,
    val isExact: Boolean,
)
