/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastRatioTest {
    @Test
    fun whiteOnBlack_isMaximumRatio() {
        val ratio = ContrastRatio.ratio(fg = 0xFFFFFF, bg = 0x000000)
        assertEquals(21.0, ratio, 0.01)
    }

    @Test
    fun whiteOnMidGrey_matchesWcagFloor() {
        // #767676 is the WCAG 2.1 AA-floor reference grey for normal text on
        // white: the canonical example used to illustrate the 4.5:1 threshold.
        // External reference: https://webaim.org/articles/contrast/ ≈ 4.54:1.
        val ratio = ContrastRatio.ratio(fg = 0xFFFFFF, bg = 0x767676)
        assertEquals(4.54, ratio, 0.05)
    }
}
