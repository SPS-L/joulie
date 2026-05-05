// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import org.junit.Assert.assertThrows
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

/**
 * TASK-53: locks in the single-car invariant on every aggregation in
 * [StatsCalculator] except [StatsCalculator.detectMixedCurrency], which
 * is intentionally exempt because it semantically does not depend on
 * car identity (see KDoc).
 *
 * Defense-in-depth — current call sites
 * ([ObserveDashboardStatsUseCase], [ObserveChartsModelsUseCase]) all
 * pass single-car lists, but the invariant was unenforced before this
 * task. A future cross-car comparison view that forgets to thread
 * `carId` correctly would now trip a clear `IllegalArgumentException`
 * with the offending carIds in the message rather than silently
 * producing nonsense `totalDistanceKm` / efficiency values across a
 * car-switch boundary.
 */
class StatsCalculatorInvariantTest {

    private val calc = StatsCalculator()

    private fun event(id: Long, carId: Long) = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = id,
        odometerKm = 1000.0 + id,
        kwhAdded = 10.0,
        chargeType = ChargeType.AC,
        createdAt = 0L,
    )

    private fun mixedCarEvents() = listOf(
        event(id = 1, carId = 1L),
        event(id = 2, carId = 2L),
    )

    // -------------------------------------------------------------------------
    // Mixed-car input: every guarded aggregation must throw with the carIds
    // surfaced in the message so the caller can see which call site was wrong.
    // -------------------------------------------------------------------------

    @Test
    fun computeStats_throws_onMixedCarIds() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            calc.computeStats(mixedCarEvents(), label = "x")
        }
        // The message should name the offending carIds so the call site is
        // easy to identify in a stack-trace bug report.
        require("[1, 2]" in (ex.message ?: ""))
    }

    @Test
    fun computeMonthlyBuckets_throws_onMixedCarIds() {
        assertThrows(IllegalArgumentException::class.java) {
            calc.computeMonthlyBuckets(mixedCarEvents())
        }
    }

    @Test
    fun computeEfficiencyTrend_throws_onMixedCarIds() {
        assertThrows(IllegalArgumentException::class.java) {
            calc.computeEfficiencyTrend(mixedCarEvents())
        }
    }

    @Test
    fun computeAcDcSplit_throws_onMixedCarIds() {
        assertThrows(IllegalArgumentException::class.java) {
            calc.computeAcDcSplit(mixedCarEvents())
        }
    }

    @Test
    fun computeLocationDistribution_throws_onMixedCarIds() {
        assertThrows(IllegalArgumentException::class.java) {
            calc.computeLocationDistribution(mixedCarEvents())
        }
    }

    // -------------------------------------------------------------------------
    // Empty list: passes the guard. `distinct().size == 0` is `<= 1`. This
    // matters because every aggregation in this class is reachable on an
    // empty list (period filter producing 0 events is a routine state, not
    // an error).
    // -------------------------------------------------------------------------

    @Test
    fun computeStats_succeeds_onEmptyList() {
        val result = calc.computeStats(emptyList(), label = "empty")
        // Existing semantics for empty input hold — the guard didn't
        // change them. Spot-check the chargeCount and totalKwh fields.
        require(result.chargeCount == 0)
        require(result.totalKwh == 0.0)
    }

    @Test
    fun allGuardedAggregations_succeed_onEmptyList() {
        // Single-call smoke per method to confirm the empty-list path
        // doesn't accidentally throw on the new guard. Specific
        // aggregation outputs are covered by the per-method tests
        // elsewhere.
        calc.computeMonthlyBuckets(emptyList())
        calc.computeEfficiencyTrend(emptyList())
        calc.computeAcDcSplit(emptyList())
        calc.computeLocationDistribution(emptyList())
    }

    @Test
    fun detectMixedCurrency_acceptsMixedCarIds_byDesign() {
        // detectMixedCurrency is intentionally exempt from the guard
        // (semantically asks about currency, not car identity). Pass
        // a mixed-car list and expect a clean boolean return rather
        // than a thrown exception.
        val result = calc.detectMixedCurrency(mixedCarEvents())
        // Default-constructed events have no costTotal → no costed
        // currencies → not mixed. The point of this test is the
        // absence of throw, not the return value.
        require(!result)
    }
}
