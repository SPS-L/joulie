// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

object UnitConverter {
    private const val KM_PER_MI = 1.609344

    fun kmToMiles(km: Double): Double = km / KM_PER_MI
    fun milesToKm(mi: Double): Double = mi * KM_PER_MI
    fun kmPerKwhToMiPerKwh(kmPerKwh: Double): Double = kmToMiles(kmPerKwh)
}
