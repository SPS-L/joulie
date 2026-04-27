package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.spsl.evtracker.core.model.BackupData
import org.spsl.evtracker.core.model.BackupVersionMismatch
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

class BackupSerializerTest {

    private val serializer = BackupSerializer()

    @Test
    fun roundTrip_preservesAllFields() {
        val original = BackupData.fromEntities(
            cars = listOf(CarEntity(id = 1, name = "Tesla", make = "T", model = "M3", year = 2024, batteryKwh = 60.0, createdAt = 1000L)),
            events = listOf(ChargeEventEntity(
                id = 17, carId = 1, eventDate = 2000L, odometerKm = 12345.0, kwhAdded = 22.4,
                chargeType = "AC", costTotal = 5.5, costPerKwh = 0.245, currency = "EUR",
                location = "Home", note = "first", createdAt = 3000L
            )),
            locations = listOf(CustomLocationEntity(id = 5, label = "Supercharger A6", useCount = 4, lastUsed = 4000L)),
            now = 5000L
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
            cars = listOf(CarEntity(id = 1, name = "<&>",   createdAt = 1L)),
            events = emptyList(),
            locations = emptyList(),
            now = 0L
        )
        val json = serializer.toJson(data)
        assertFalse("HTML-escaped < should be absent", json.contains("\\u003c"))
        assertFalse("HTML-escaped > should be absent", json.contains("\\u003e"))
        assertFalse("HTML-escaped & should be absent", json.contains("\\u0026"))
    }

    @Test
    fun fromEntities_setsExportedAtToIso8601Utc() {
        val data = BackupData.fromEntities(
            cars = emptyList(), events = emptyList(), locations = emptyList(),
            now = 1714044000000L
        )
        assertEquals("2024-04-25T11:20:00Z", data.exportedAt)
    }
}
