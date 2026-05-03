// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

sealed class DashboardPeriod {
    object SincePreviousCharge : DashboardPeriod()
    object Last7Days : DashboardPeriod()
    object Last30Days : DashboardPeriod()
    object Year : DashboardPeriod()
    data class Custom(val fromMillis: Long, val toMillis: Long) : DashboardPeriod()
}
