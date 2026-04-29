package org.spsl.evtracker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSliceTest {

    @Test fun otherKey_setsIsOther() {
        val slice = LocationSlice(LocationSlice.OTHER_KEY, 7)
        assertTrue(slice.isOther)
        assertEquals(7, slice.count)
    }

    @Test fun normalLabel_isNotOther() {
        val slice = LocationSlice("Home", 3)
        assertFalse(slice.isOther)
    }

    @Test fun otherKey_startsWithNul_collisionProofAgainstAnyAndroidInput() {
        // Android EditText strips the NUL char from IME input, so no
        // user-typed location label can ever start with it. The sentinel
        // is therefore collision-proof regardless of any trim() invariant.
        assertEquals(Char(0), LocationSlice.OTHER_KEY[0])
    }
}
