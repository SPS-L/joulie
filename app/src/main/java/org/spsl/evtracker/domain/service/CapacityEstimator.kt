package org.spsl.evtracker.domain.service

import org.spsl.evtracker.core.model.CapacityPoint
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import javax.inject.Inject

/**
 * TASK-14 — battery-capacity degradation estimator.
 *
 * Two estimation paths per event, in priority order:
 *
 * 1. **Exact** — when both `socBefore` and `socAfter` are present and
 *    `socAfter > socBefore`, the effective capacity is
 *    `kwhAdded / (socAfter - socBefore)`. The SoC fields are stored as
 *    fractions in `0.0..1.0`.
 *
 * 2. **Heuristic** — when SoC fields are absent but `kwhAdded ≥
 *    HEURISTIC_FULL_CHARGE_FRACTION × batteryKwh`, use `kwhAdded` as a
 *    proxy. This catches near-full charges where the user didn't record
 *    SoC but probably charged from a low state.
 *
 * Events that satisfy neither rule are skipped. Events with `kwhAdded
 * ≤ 0` are always skipped.
 */
class CapacityEstimator @Inject constructor() {

    fun estimate(
        events: List<ChargeEventEntity>,
        nominalBatteryKwh: Double?,
    ): List<CapacityPoint> = events
        .mapNotNull { event -> estimateOne(event, nominalBatteryKwh) }
        .sortedBy { it.eventDate }

    /**
     * Latest effective capacity in `points`, or `null` when the list is
     * empty. The Dashboard battery-health card uses this together with
     * the car's nominal capacity to compute a percentage.
     */
    fun latestCapacity(points: List<CapacityPoint>): Double? =
        points.maxByOrNull { it.eventDate }?.effectiveCapacityKwh

    /**
     * `(latestEffectiveCapacity / nominalCapacity) × 100`, or `null`
     * when either side is missing or non-positive. The result is NOT
     * clamped to `[0, 100]` — values above 100 indicate the heuristic
     * over-estimated, which is still useful diagnostic information for
     * the user.
     */
    fun batteryHealthPercent(
        points: List<CapacityPoint>,
        nominalBatteryKwh: Double?,
    ): Double? {
        if (nominalBatteryKwh == null || nominalBatteryKwh <= 0.0) return null
        val latest = latestCapacity(points) ?: return null
        return (latest / nominalBatteryKwh) * 100.0
    }

    private fun estimateOne(
        event: ChargeEventEntity,
        nominalBatteryKwh: Double?,
    ): CapacityPoint? {
        if (event.kwhAdded <= 0.0) return null
        val before = event.socBefore
        val after = event.socAfter
        if (before != null && after != null && after > before) {
            val capacity = event.kwhAdded / (after - before)
            return CapacityPoint(event.eventDate, capacity, isExact = true)
        }
        if (nominalBatteryKwh == null || nominalBatteryKwh <= 0.0) return null
        if (event.kwhAdded >= HEURISTIC_FULL_CHARGE_FRACTION * nominalBatteryKwh) {
            return CapacityPoint(event.eventDate, event.kwhAdded, isExact = false)
        }
        return null
    }

    companion object {
        const val HEURISTIC_FULL_CHARGE_FRACTION = 0.8

        /** Minimum point count required to render the Charts degradation tab. */
        const val MIN_POINTS_FOR_CHART = 3
    }
}
