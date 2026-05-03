// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.db

import androidx.room.TypeConverter
import org.spsl.evtracker.core.model.ChargeKwhSource

class ChargeKwhSourceConverter {
    @TypeConverter
    fun fromChargeKwhSource(value: ChargeKwhSource): String = value.name

    @TypeConverter
    fun toChargeKwhSource(value: String): ChargeKwhSource = ChargeKwhSource.parseLegacy(value)
}
