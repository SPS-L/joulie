/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker

import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.testing.ContrastRatio

/**
 * WCAG 2.1 AA contrast audit for the Joulie M3 ramps.
 *
 * Source of truth for tokens:
 *   app/src/main/res/values/colors.xml         (light)
 *   app/src/main/res/values-night/colors.xml   (dark)
 *
 * If a token is re-seeded (e.g. via Material Theme Builder regen), the
 * paired hex value below must be updated. Failure messages name the pair
 * and the observed ratio so the fix is unambiguous.
 *
 * Spec: docs/superpowers/specs/2026-05-07-task76-m3-contrast-audit-design.md
 */
class M3ContrastAuditTest {
    private data class Pair(
        val name: String,
        val fg: Int,
        val bg: Int,
        val threshold: Double,
    )

    @Test
    fun lightTheme_allPairsClearThreshold() {
        LIGHT_PAIRS.forEach { pair -> assertPair(pair) }
    }

    @Test
    fun darkTheme_allPairsClearThreshold() {
        DARK_PAIRS.forEach { pair -> assertPair(pair) }
    }

    private fun assertPair(pair: Pair) {
        val ratio = ContrastRatio.ratio(pair.fg, pair.bg)
        assertTrue(
            "${pair.name}: expected >= ${pair.threshold}:1, got %.2f:1 for (fg=#%06X, bg=#%06X)"
                .format(ratio, pair.fg and 0xFFFFFF, pair.bg and 0xFFFFFF),
            ratio >= pair.threshold,
        )
    }

    companion object {
        // WCAG 2.1 §1.4.3 normal-text threshold.
        private const val TEXT = 4.5

        // WCAG 2.1 §1.4.11 non-text-component threshold (interactive UI).
        private const val UI = 3.0

        // Decorative-visibility floor for M3 `outlineVariant` (settings
        // dividers, chart gridlines). M3's design intent is intentionally
        // subtle; WCAG 1.4.11 excludes purely decorative elements. The
        // 1.5:1 floor is a regression guard against a future re-seed that
        // would render the divider invisible (~1.0:1) against the surface.
        private const val DECO = 1.5

        private val LIGHT_PAIRS = listOf(
            Pair("light primary text", 0xFFFFFF, 0x1F3DCC, TEXT),
            Pair("light primary container text", 0x001255, 0xDCE1FF, TEXT),
            Pair("light secondary text", 0xFFFFFF, 0x006874, TEXT),
            Pair("light secondary container text", 0x001F24, 0x9EEFFD, TEXT),
            Pair("light tertiary text", 0xFFFFFF, 0x7F5700, TEXT),
            Pair("light tertiary container text", 0x281900, 0xFFE082, TEXT),
            Pair("light error text", 0xFFFFFF, 0xBA1A1A, TEXT),
            Pair("light error container text", 0x410002, 0xFFDAD6, TEXT),
            Pair("light body text", 0x1A1C1E, 0xFDFCFF, TEXT),
            Pair("light surface text", 0x1A1C1E, 0xFDFCFF, TEXT),
            Pair("light surface variant text", 0x43474E, 0xDFE2EB, TEXT),
            Pair("light inverse surface text", 0xF1F0F4, 0x2F3033, TEXT),
            Pair("light outline on surface", 0x73777F, 0xFDFCFF, UI),
            Pair("light outline variant on surface (decorative)", 0xC3C7CF, 0xFDFCFF, DECO),
            Pair("light brand wordmark on background", 0x0A1B5E, 0xFDFCFF, TEXT),
            Pair("light launcher tile glyph", 0xFFFFFF, 0x0D47FF, TEXT),
        )

        private val DARK_PAIRS = listOf(
            Pair("dark primary text", 0x001F87, 0xB7C4FF, TEXT),
            Pair("dark primary container text", 0xDCE1FF, 0x0034BC, TEXT),
            Pair("dark secondary text", 0x00363D, 0x82D3DF, TEXT),
            Pair("dark secondary container text", 0x9EEFFD, 0x004F58, TEXT),
            Pair("dark tertiary text", 0x412D00, 0xF4C100, TEXT),
            Pair("dark tertiary container text", 0xFFE082, 0x5D4200, TEXT),
            Pair("dark error text", 0x690005, 0xFFB4AB, TEXT),
            Pair("dark error container text", 0xFFDAD6, 0x93000A, TEXT),
            Pair("dark body text", 0xE2E2E6, 0x1A1C1E, TEXT),
            Pair("dark surface text", 0xE2E2E6, 0x1A1C1E, TEXT),
            Pair("dark surface variant text", 0xC3C7CF, 0x43474E, TEXT),
            Pair("dark inverse surface text", 0x2F3033, 0xE2E2E6, TEXT),
            Pair("dark outline on surface", 0x8D9199, 0x1A1C1E, UI),
            Pair("dark outline variant on surface (decorative)", 0x43474E, 0x1A1C1E, DECO),
            Pair("dark brand wordmark on background", 0xB7C4FF, 0x1A1C1E, TEXT),
        )
    }
}
