package org.spsl.evtracker.domain.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import java.util.concurrent.TimeUnit

class LastChargeWidgetSnapshotTest {

    private fun car(id: Long = 1L, name: String = "Tesla Model 3"): CarEntity =
        CarEntity(
            id = id,
            name = name,
            make = "Tesla",
            model = "Model 3",
            year = 2024,
            batteryKwh = 75.0,
            createdAt = 0L,
        )

    private fun event(
        id: Long,
        carId: Long = 1L,
        eventDate: Long,
        odometerKm: Double,
        kwhAdded: Double,
        chargeType: ChargeType = ChargeType.AC,
        costTotal: Double? = null,
        currency: String? = null,
    ): ChargeEventEntity = ChargeEventEntity(
        id = id,
        carId = carId,
        eventDate = eventDate,
        odometerKm = odometerKm,
        kwhAdded = kwhAdded,
        chargeType = chargeType,
        costTotal = costTotal,
        costPerKwh = null,
        currency = currency,
        location = null,
        note = "",
        socBefore = null,
        socAfter = null,
        createdAt = 0L,
    )

    private fun nowAt(daysAgo: Int): Long =
        TimeUnit.DAYS.toMillis(365L * 50L) + TimeUnit.DAYS.toMillis(daysAgo.toLong())

    @Test
    fun nullCar_returnsEmpty() {
        val result = LastChargeWidgetSnapshot.compute(
            activeCar = null,
            events = listOf(event(1L, eventDate = 1000L, odometerKm = 100.0, kwhAdded = 30.0)),
            primaryMetric = "km_per_kwh",
            nowMillis = 2000L,
        )
        assertEquals(LastChargeWidgetSnapshot.Empty, result)
    }

    @Test
    fun emptyEvents_returnsEmpty() {
        val result = LastChargeWidgetSnapshot.compute(
            activeCar = car(),
            events = emptyList(),
            primaryMetric = "km_per_kwh",
            nowMillis = 2000L,
        )
        assertEquals(LastChargeWidgetSnapshot.Empty, result)
    }

    @Test
    fun singleEvent_loadedButEfficiencyNull() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now, odometerKm = 100.0, kwhAdded = 42.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now)
        assertTrue(r is LastChargeWidgetSnapshot.Loaded)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        assertEquals("Tesla Model 3", loaded.carName)
        assertEquals(42.0, loaded.kwhAdded, 0.0)
        assertNull("efficiency requires 2+ events", loaded.efficiencyValue)
        assertNull(loaded.efficiencyUnitLabel)
    }

    @Test
    fun twoEvents_kmPerKwh_correct() {
        val now = nowAt(0)
        val previous = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 1000.0, kwhAdded = 50.0)
        val latest = event(2L, eventDate = now, odometerKm = 1310.0, kwhAdded = 50.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(previous, latest), "km_per_kwh", now)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        assertEquals(6.2, loaded.efficiencyValue!!, 0.001)
        assertEquals("km/kWh", loaded.efficiencyUnitLabel)
    }

    @Test
    fun twoEvents_kwhPer100km_convertsCorrectly() {
        val now = nowAt(0)
        val previous = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 1000.0, kwhAdded = 50.0)
        val latest = event(2L, eventDate = now, odometerKm = 1310.0, kwhAdded = 50.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(previous, latest), "kwh_per_100km", now)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        // 6.2 km/kWh → 100 / 6.2 ≈ 16.13 kWh/100 km
        assertEquals(16.129, loaded.efficiencyValue!!, 0.01)
        assertEquals("kWh/100 km", loaded.efficiencyUnitLabel)
    }

    @Test
    fun twoEvents_miPerKwh_convertsCorrectly() {
        val now = nowAt(0)
        val previous = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 1000.0, kwhAdded = 50.0)
        val latest = event(2L, eventDate = now, odometerKm = 1310.0, kwhAdded = 50.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(previous, latest), "mi_per_kwh", now)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        // 6.2 km/kWh × 0.621371 ≈ 3.852 mi/kWh
        assertEquals(3.852, loaded.efficiencyValue!!, 0.01)
        assertEquals("mi/kWh", loaded.efficiencyUnitLabel)
    }

    @Test
    fun twoEvents_negativeOdometerDelta_efficiencyNull() {
        val now = nowAt(0)
        // Odometer goes backwards — invalid for efficiency.
        val previous = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 2000.0, kwhAdded = 50.0)
        val latest = event(2L, eventDate = now, odometerKm = 1900.0, kwhAdded = 50.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(previous, latest), "km_per_kwh", now)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        assertNull(loaded.efficiencyValue)
    }

    @Test
    fun twoEvents_zeroKwhAdded_efficiencyNull() {
        val now = nowAt(0)
        val previous = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 1000.0, kwhAdded = 50.0)
        val latest = event(2L, eventDate = now, odometerKm = 1100.0, kwhAdded = 0.0)
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(previous, latest), "km_per_kwh", now)
        assertNull((r as LastChargeWidgetSnapshot.Loaded).efficiencyValue)
    }

    @Test
    fun unsortedEvents_picksLatestByEventDate() {
        val now = nowAt(0)
        val newer = event(1L, eventDate = now, odometerKm = 1310.0, kwhAdded = 50.0, chargeType = ChargeType.DC_FAST)
        val older = event(2L, eventDate = now - TimeUnit.DAYS.toMillis(10), odometerKm = 1000.0, kwhAdded = 50.0)
        // Pass newer first to confirm the helper sorts internally.
        val r = LastChargeWidgetSnapshot.compute(car(), listOf(newer, older), "km_per_kwh", now)
        val loaded = r as LastChargeWidgetSnapshot.Loaded
        assertEquals(ChargeType.DC_FAST, loaded.chargeType)
        assertEquals(50.0, loaded.kwhAdded, 0.0)
    }

    @Test
    fun cost_passesThrough_whenSet() {
        val now = nowAt(0)
        val ev = event(
            1L,
            eventDate = now,
            odometerKm = 100.0,
            kwhAdded = 30.0,
            costTotal = 12.5,
            currency = "EUR",
        )
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals(12.5, loaded.costTotal!!, 0.0)
        assertEquals("EUR", loaded.currency)
    }

    @Test
    fun cost_null_whenNotSet() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now, odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertNull(loaded.costTotal)
        assertNull(loaded.currency)
    }

    @Test
    fun relativeDate_today() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now, odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals("Today", loaded.relativeDateLabel)
    }

    @Test
    fun relativeDate_yesterday() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(1), odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals("Yesterday", loaded.relativeDateLabel)
    }

    @Test
    fun relativeDate_threeDaysAgo() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(3), odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals("3 days ago", loaded.relativeDateLabel)
    }

    @Test
    fun relativeDate_oneWeekAgo_singular() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(7), odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals("1 week ago", loaded.relativeDateLabel)
    }

    @Test
    fun relativeDate_threeWeeksAgo() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(21), odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals("3 weeks ago", loaded.relativeDateLabel)
    }

    @Test
    fun relativeDate_overFourWeeks_fallsBackToAbsoluteDate() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now - TimeUnit.DAYS.toMillis(60), odometerKm = 100.0, kwhAdded = 30.0)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        // Don't assert exact format (locale-dependent) — just that it isn't a relative bucket.
        assertNotNull(loaded.relativeDateLabel)
        assertTrue(
            "expected absolute date but got '${loaded.relativeDateLabel}'",
            !loaded.relativeDateLabel.contains("ago") &&
                loaded.relativeDateLabel != "Today" &&
                loaded.relativeDateLabel != "Yesterday",
        )
    }

    @Test
    fun chargeType_AC_propagates() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now, odometerKm = 100.0, kwhAdded = 30.0, chargeType = ChargeType.AC)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals(ChargeType.AC, loaded.chargeType)
    }

    @Test
    fun chargeType_DC_FAST_propagates() {
        val now = nowAt(0)
        val ev = event(1L, eventDate = now, odometerKm = 100.0, kwhAdded = 30.0, chargeType = ChargeType.DC_FAST)
        val loaded = LastChargeWidgetSnapshot.compute(car(), listOf(ev), "km_per_kwh", now) as LastChargeWidgetSnapshot.Loaded
        assertEquals(ChargeType.DC_FAST, loaded.chargeType)
    }
}
