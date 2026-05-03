// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.db

import androidx.room.TypeConverter
import org.spsl.evtracker.core.model.ChargeType

/**
 * Room [TypeConverter] for [ChargeType]. Stored on disk as the enum's
 * `name` (`"AC"` / `"DC_FAST"` / `"DC_ULTRA"`); legacy v3 rows still
 * carry `"DC"` and are decoded to [ChargeType.DC_FAST] via
 * [ChargeType.parseLegacy] (in addition to the explicit `MIGRATION_3_4`
 * UPDATE that rewrites those rows the next time the DB opens).
 */
class ChargeTypeConverter {
    @TypeConverter
    fun fromChargeType(value: ChargeType): String = value.name

    @TypeConverter
    fun toChargeType(value: String): ChargeType = ChargeType.parseLegacy(value)
}
