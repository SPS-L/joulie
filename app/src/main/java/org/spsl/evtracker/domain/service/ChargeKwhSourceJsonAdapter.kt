package org.spsl.evtracker.domain.service

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.spsl.evtracker.core.model.ChargeKwhSource
import java.lang.reflect.Type

/**
 * Gson adapter for [ChargeKwhSource]. Wire format is the enum name
 * (`"MEASURED"` / `"DERIVED_FROM_SOC"`); unknown / corrupted values fall
 * back to `MEASURED` via [ChargeKwhSource.parseLegacy] so legacy backups
 * (`backup_version` 3..6, all of which predate this field) deserialise
 * cleanly with the safe default. Mirrors [ChargeTypeJsonAdapter].
 */
class ChargeKwhSourceJsonAdapter :
    JsonSerializer<ChargeKwhSource>,
    JsonDeserializer<ChargeKwhSource> {
    override fun serialize(
        src: ChargeKwhSource,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement = JsonPrimitive(src.name)

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): ChargeKwhSource = ChargeKwhSource.parseLegacy(json.asString)
}
