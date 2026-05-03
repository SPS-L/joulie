package org.spsl.evtracker.data.local.db

import androidx.room.TypeConverter
import org.spsl.evtracker.core.model.ChargeKwhSource

class ChargeKwhSourceConverter {
    @TypeConverter
    fun fromChargeKwhSource(value: ChargeKwhSource): String = value.name

    @TypeConverter
    fun toChargeKwhSource(value: String): ChargeKwhSource = ChargeKwhSource.parseLegacy(value)
}
