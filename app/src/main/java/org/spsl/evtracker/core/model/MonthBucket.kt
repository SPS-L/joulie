// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class MonthBucket(
    val year: Int,
    val month: Int,
    val totalKwh: Double,
    val totalCost: Double?,
    val currency: String?,
)
