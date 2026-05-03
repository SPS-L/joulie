// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class CarFormState(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val batteryKwh: String = "",
)
