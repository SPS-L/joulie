package org.spsl.evtracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

    @Test
    fun kmToMiles_positive() {
        assertEquals(62.137, UnitConverter.kmToMiles(100.0), 0.001)
    }

    @Test
    fun milesToKm_positive() {
        assertEquals(100.0, UnitConverter.milesToKm(62.1371), 0.001)
    }

    @Test
    fun kmToMiles_zero() {
        assertEquals(0.0, UnitConverter.kmToMiles(0.0), 0.0)
    }

    @Test
    fun efficiency_kmPerKwh_to_miPerKwh() {
        assertEquals(3.107, UnitConverter.kmPerKwhToMiPerKwh(5.0), 0.001)
    }
}
