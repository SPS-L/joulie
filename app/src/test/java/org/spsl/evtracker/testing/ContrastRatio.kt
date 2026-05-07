/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.testing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure WCAG 2.1 §1.4.3 relative-luminance / contrast-ratio computation.
 *
 * Source of truth: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 *                   https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 *
 * Used by `M3ContrastAuditTest` to lock in the Joulie M3 ramp against
 * WCAG 2.1 AA thresholds (4.5 normal text, 3.0 large text + non-text UI).
 */
object ContrastRatio {
    fun ratio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(rgb: Int): Double {
        val r = channelLinear(((rgb shr 16) and 0xFF) / 255.0)
        val g = channelLinear(((rgb shr 8) and 0xFF) / 255.0)
        val b = channelLinear((rgb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channelLinear(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}
