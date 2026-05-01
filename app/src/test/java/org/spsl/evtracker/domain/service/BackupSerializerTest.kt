package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

class BackupSerializerTest {

    private val serializer = BackupSerializer()

    @Test
    fun roundTrip_preservesAllFields() {
        val original = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1L, name = "Tesla", make = "T", model = "M3", year = 2024, batteryKwh = 60.0, createdAt = 1000L)),
            events = listOf(
                ChargeEventEntity(
                    id = 17L, carId = 1L, eventDate = 2000L, odometerKm = 12345.0, kwhAdded = 22.4,
                    chargeType = ChargeType.AC, costTotal = 5.5, costPerKwh = 0.245, currency = "EUR",
                    location = "Home", note = "first", createdAt = 3000L,
                ),
            ),
            locations = listOf(CustomLocationEntity(id = 5L, label = "Supercharger A6", useCount = 4, lastUsed = 4000L)),
            now = 5000L,
        )

        val json = serializer.toJson(original)
        val parsed = serializer.fromJson(json)

        assertEquals(original.backupVersion, parsed.backupVersion)
        assertEquals(original.exportedAt, parsed.exportedAt)
        assertEquals(original.cars, parsed.cars)
        assertEquals(original.chargeEvents, parsed.chargeEvents)
        assertEquals(original.customLocations, parsed.customLocations)
    }

    @Test
    fun fromJson_throwsOnVersionMismatch() {
        val v2Json = """{"backup_version":2,"exported_at":"x","cars":[],"charge_events":[],"custom_locations":[]}"""
        val ex = assertThrows(BackupVersionMismatch::class.java) { serializer.fromJson(v2Json) }
        assertEquals(2, ex.actual)
    }

    @Test
    fun toJson_isHtmlEscapeFree() {
        val data = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1L, name = "<&>", createdAt = 1L)),
            events = emptyList(),
            locations = emptyList(),
            now = 0L,
        )
        val json = serializer.toJson(data)
        assertFalse("HTML-escaped < should be absent", json.contains("\\u003c"))
        assertFalse("HTML-escaped > should be absent", json.contains("\\u003e"))
        assertFalse("HTML-escaped & should be absent", json.contains("\\u0026"))
    }

    @Test
    fun fromEntities_setsExportedAtToIso8601Utc() {
        val data = BackupData.fromEntities(
            cars = emptyList(),
            events = emptyList(),
            locations = emptyList(),
            now = 1714044000000L,
        )
        assertEquals("2024-04-25T11:20:00Z", data.exportedAt)
    }

    /**
     * TASK-25: backups written by pre-TASK-25 builds carry `backup_version = 3`
     * and `charge_type = "DC"`. After this task, both must still restore on a
     * v4-aware app: the version check accepts both 3 and 4, and the
     * `ChargeType` Gson adapter routes legacy `"DC"` through
     * `ChargeType.parseLegacy` to land at `DC_FAST`.
     */
    @Test
    fun fromJson_acceptsV3Backup_andMapsLegacyDcToDcFast() {
        val v3Json = """
            {
              "backup_version": 3,
              "exported_at": "2025-01-01T00:00:00Z",
              "cars": [],
              "charge_events": [
                {
                  "id": 1, "car_id": 1, "event_date": 1000,
                  "odometer_km": 100.0, "kwh_added": 10.0,
                  "charge_type": "DC", "cost_total": null,
                  "cost_per_kwh": null, "currency": null,
                  "location": null, "note": "", "created_at": 0
                }
              ],
              "custom_locations": []
            }
        """.trimIndent()

        val parsed = serializer.fromJson(v3Json)

        assertEquals(3, parsed.backupVersion)
        assertEquals(1, parsed.chargeEvents.size)
        assertEquals(ChargeType.DC_FAST, parsed.chargeEvents.single().chargeType)
    }

    @Test
    fun toJson_serializesEnumAsName() {
        val data = BackupData.fromEntities(
            cars = emptyList(),
            events = listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 0L,
                    odometerKm = 0.0,
                    kwhAdded = 0.0,
                    chargeType = ChargeType.DC_FAST,
                    createdAt = 0L,
                ),
            ),
            locations = emptyList(),
            now = 0L,
        )
        val json = serializer.toJson(data)
        assertTrue(
            "expected the canonical enum name on the wire; got $json",
            json.contains("\"charge_type\":\"DC_FAST\""),
        )
    }
}
