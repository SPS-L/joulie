// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

sealed class ChartsPeriod {
    object Last6Months : ChartsPeriod()
    object Last12Months : ChartsPeriod()
    object AllTime : ChartsPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : ChartsPeriod()
}
