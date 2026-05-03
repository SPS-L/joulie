// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.spsl.evtracker.domain.widget.WidgetRefresher
import org.spsl.evtracker.widget.LastChargeWidget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-12: thin Android wrapper around [LastChargeWidget.refreshAll]. The
 * production binding for [WidgetRefresher] — JVM tests use the
 * `FakeWidgetRefresher` from `Fakes.kt` instead.
 */
@Singleton
class AndroidWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefresher {
    override fun refresh() {
        LastChargeWidget.refreshAll(context)
    }
}
