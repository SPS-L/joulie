// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.service

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.spsl.evtracker.core.model.ChargeType
import java.lang.reflect.Type

/**
 * Gson adapter for [ChargeType]. The wire format on the backup JSON stays
 * a string (`"AC"` / `"DC_FAST"` / `"DC_ULTRA"`), with legacy
 * `backup_version = 3` files (which carry `"DC"`) handled transparently
 * by [ChargeType.parseLegacy] on read.
 */
class ChargeTypeJsonAdapter : JsonSerializer<ChargeType>, JsonDeserializer<ChargeType> {
    override fun serialize(
        src: ChargeType,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement = JsonPrimitive(src.name)

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): ChargeType = ChargeType.parseLegacy(json.asString)
}
