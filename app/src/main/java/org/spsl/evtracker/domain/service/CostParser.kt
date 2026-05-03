// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import javax.inject.Inject

enum class CostMode { TOTAL, PER_KWH }

class CostParser @Inject constructor() {
    fun parse(value: Double?, kwh: Double, mode: CostMode): Pair<Double?, Double?> {
        if (value == null || value <= 0.0 || kwh <= 0.0) return Pair(null, null)
        return when (mode) {
            CostMode.TOTAL -> Pair(value, value / kwh)
            CostMode.PER_KWH -> Pair(value * kwh, value)
        }
    }
}
