package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.core.model.ChargeKwhSource
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

    /**
     * TASK-43: a v7 backup carrying `"kwh_source": "DERIVED_FROM_SOC"`
     * round-trips through Gson preserving the enum value. The default for
     * the field is `MEASURED`, so this test only passes if the
     * serializer + deserializer are wired (otherwise Gson would either
     * treat the enum as a string and fall back to MEASURED on read, or
     * fail outright).
     */
    @Test
    fun roundTrip_preservesKwhSourceFlag() {
        val original = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1L, name = "Zoe", createdAt = 1L)),
            events = listOf(
                ChargeEventEntity(
                    id = 1L, carId = 1L, eventDate = 0L, odometerKm = 0.0, kwhAdded = 18.0,
                    chargeType = ChargeType.AC, socBefore = 0.20, socAfter = 0.50,
                    kwhSource = ChargeKwhSource.DERIVED_FROM_SOC, createdAt = 0L,
                ),
            ),
            locations = emptyList(),
            now = 0L,
        )

        val json = serializer.toJson(original)
        val parsed = serializer.fromJson(json)

        assertEquals(
            ChargeKwhSource.DERIVED_FROM_SOC,
            parsed.chargeEvents.single().kwhSource,
        )
    }

    /**
     * TASK-43: a v6 backup file (which predates the field entirely) must
     * still restore on a v7-aware app. The kwh_source key is absent from
     * the JSON; Gson should leave the Kotlin default in place, and that
     * default is `MEASURED` — the safe backfill since legacy events came
     * from charger readings, not the in-form calculator.
     */
    @Test
    fun fromJson_legacyV6Backup_kwhSourceDefaultsToMeasured() {
        val v6Json = """
            {
              "backup_version": 6,
              "exported_at": "2025-01-01T00:00:00Z",
              "cars": [],
              "charge_events": [
                {
                  "id": 1, "car_id": 1, "event_date": 1000,
                  "odometer_km": 100.0, "kwh_added": 10.0,
                  "charge_type": "AC", "cost_total": null,
                  "cost_per_kwh": null, "currency": null,
                  "location": null, "note": "",
                  "soc_before": 0.30, "soc_after": 0.60,
                  "created_at": 0
                }
              ],
              "custom_locations": []
            }
        """.trimIndent()

        val parsed = serializer.fromJson(v6Json)

        assertEquals(6, parsed.backupVersion)
        // The DTO carries a nullable kwhSource (Gson bypasses Kotlin
        // constructor defaults for missing JSON keys), but the entity
        // mapping coalesces null → MEASURED — the user-facing contract.
        assertEquals(
            ChargeKwhSource.MEASURED,
            parsed.chargeEvents.single().toEntity().kwhSource,
        )
    }

    @Test
    fun toJson_serializesKwhSourceAsName() {
        val data = BackupData.fromEntities(
            cars = emptyList(),
            events = listOf(
                ChargeEventEntity(
                    id = 1L,
                    carId = 1L,
                    eventDate = 0L,
                    odometerKm = 0.0,
                    kwhAdded = 0.0,
                    chargeType = ChargeType.AC,
                    kwhSource = ChargeKwhSource.DERIVED_FROM_SOC,
                    createdAt = 0L,
                ),
            ),
            locations = emptyList(),
            now = 0L,
        )
        val json = serializer.toJson(data)
        assertTrue(
            "expected the canonical enum name on the wire; got $json",
            json.contains("\"kwh_source\":\"DERIVED_FROM_SOC\""),
        )
    }

    @Test
    fun currentVersion_isSeven() {
        // Sanity check so a future bump that forgets to update the version
        // constant gets caught.
        assertEquals(7, BackupData.CURRENT_VERSION)
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
