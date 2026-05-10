// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.common

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure slice-angle math, isolated from the View lifecycle so we can exercise
 * it without Robolectric. The actual rendering is covered by Roborazzi
 * baselines for the AC/DC and Locations tabs (TASK-79).
 */
class PieChartViewSliceMathTest {

    @Test
    fun `empty slices total is zero`() {
        val slices = emptyList<PieChartView.Slice>()
        assertEquals(0f, slices.sumOf { it.value.toDouble() }.toFloat(), 0f)
    }

    @Test
    fun `non-empty slices angles sum to 360 degrees`() {
        val slices = listOf(
            PieChartView.Slice("AC", value = 18f, color = Color.BLUE),
            PieChartView.Slice("DC", value = 6f, color = Color.RED),
        )
        val total = slices.fold(0f) { acc, s -> acc + s.value }
        val sumOfSweeps = slices.fold(0f) { acc, s -> acc + 360f * (s.value / total) }
        assertEquals(360f, sumOfSweeps, 0.001f)
    }

    @Test
    fun `four slices angles sum to 360 degrees`() {
        val slices = listOf(
            PieChartView.Slice("Home", value = 12f, color = Color.BLUE),
            PieChartView.Slice("Work", value = 6f, color = Color.RED),
            PieChartView.Slice("Public", value = 4f, color = Color.GREEN),
            PieChartView.Slice("Office", value = 2f, color = Color.MAGENTA),
        )
        val total = slices.fold(0f) { acc, s -> acc + s.value }
        val sumOfSweeps = slices.fold(0f) { acc, s -> acc + 360f * (s.value / total) }
        assertEquals(360f, sumOfSweeps, 0.001f)
    }

    @Test
    fun `single non-zero slice gets full sweep`() {
        val slices = listOf(PieChartView.Slice("Only", value = 5f, color = Color.GRAY))
        val total = slices.fold(0f) { acc, s -> acc + s.value }
        val sweep = 360f * (slices.first().value / total)
        assertEquals(360f, sweep, 0f)
    }

    @Test
    fun `all-zero slices total is zero (renders nothing)`() {
        val slices = listOf(
            PieChartView.Slice("A", value = 0f, color = Color.BLACK),
            PieChartView.Slice("B", value = 0f, color = Color.BLACK),
        )
        val total = slices.fold(0f) { acc, s -> acc + s.value }
        assertEquals(0f, total, 0f)
    }
}
