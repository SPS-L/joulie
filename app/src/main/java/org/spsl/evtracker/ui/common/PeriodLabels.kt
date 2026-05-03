// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.common

import androidx.annotation.StringRes
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.DashboardPeriod

object PeriodLabels {
    @StringRes
    fun resource(period: DashboardPeriod): Int = when (period) {
        DashboardPeriod.SincePreviousCharge -> R.string.period_since_previous
        DashboardPeriod.Last7Days -> R.string.period_7d
        DashboardPeriod.Last30Days -> R.string.period_30d
        DashboardPeriod.Year -> R.string.period_year
        is DashboardPeriod.Custom -> R.string.period_custom
    }
}
