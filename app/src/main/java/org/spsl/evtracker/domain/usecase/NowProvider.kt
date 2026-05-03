// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

/**
 * Clock seam used by use cases that need to compute time-relative ranges (e.g. rolling
 * "Last 12 months"). JVM tests inject a fixed lambda so rolling-window tests are
 * deterministic; production binding (DispatcherModule) returns System.currentTimeMillis().
 */
fun interface NowProvider {
    fun nowMillis(): Long
}
