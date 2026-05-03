// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class AcDcSplit(
    val acCount: Int = 0,
    val dcCount: Int = 0,
    val acKwh: Double = 0.0,
    val dcKwh: Double = 0.0,
)
