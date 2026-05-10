// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.charts

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure (Context-only) helpers for Vico chart styling. Replaces the
 * MPAndroidChart-flavoured helpers in TASK-30. Keeps Fragment code thin
 * and JVM-testable for everything except colour resolution which
 * intrinsically needs a Context.
 */
object ChartStyling {

    /** Used by the trend / degradation / CO2 tabs to express the line chart's
     *  x-axis as a day offset from the period start. Storing raw epoch millis
     *  as a Float aliases because Float has only ~7 decimal digits of integer
     *  precision while modern timestamps need ~13. Day offsets stay well within
     *  Float precision (a 20-year window is ~7300 days). */
    const val MILLIS_PER_DAY = 86_400_000L

    private val LOCATION_PALETTE = intArrayOf(
        0xFF1E88E5.toInt(),
        0xFFFB8C00.toInt(),
        0xFF43A047.toInt(),
        0xFF8E24AA.toInt(),
        0xFF00ACC1.toInt(),
        0xFFE53935.toInt(),
        0xFFFDD835.toInt(),
        0xFF6D4C41.toInt(),
        // "Other" slot — neutral grey
        0xFF757575.toInt(),
    )

    fun resolveSeriesColors(context: Context): Pair<Int, Int> {
        val ac = resolveAttr(context, com.google.android.material.R.attr.colorPrimary, R.color.chart_ac_fallback)
        val dc = resolveAttr(context, com.google.android.material.R.attr.colorTertiary, R.color.chart_dc_fallback)
        return ac to dc
    }

    /**
     * Theme-aware axis / legend / gridline colours. Vico's stock defaults are
     * not theme-coupled either; we resolve the M3 `colorOnSurface` for text
     * and `colorOutlineVariant` for gridlines so charts follow the system
     * theme on both Light and Dark surfaces.
     */
    data class AxisColors(val text: Int, val grid: Int)

    fun resolveAxisColors(context: Context): AxisColors = AxisColors(
        text = resolveAttr(context, com.google.android.material.R.attr.colorOnSurface, R.color.chart_axis_text_fallback),
        grid = resolveAttr(context, com.google.android.material.R.attr.colorOutlineVariant, R.color.chart_axis_grid_fallback),
    )

    private fun resolveAttr(context: Context, attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) {
            tv.data
        } else {
            ContextCompat.getColor(context, fallback)
        }
    }

    /** Formatter for monthly bar charts. The x value is a *bucket index*
     *  (0f, 1f, 2f, …); the formatter looks up the bucket and renders its
     *  calendar month/year. Calling code passes the bucket list once per
     *  chart build; the closure captures it. */
    fun monthBucketFormatter(
        buckets: List<org.spsl.evtracker.core.model.MonthBucket>,
    ): CartesianValueFormatter {
        val fmt = SimpleDateFormat("MMM yy", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        return CartesianValueFormatter { _, value, _ ->
            if (buckets.isEmpty()) return@CartesianValueFormatter ""
            val i = value.toInt().coerceIn(0, buckets.lastIndex)
            val b = buckets[i]
            cal.clear()
            cal.set(b.year, b.month - 1, 1, 0, 0, 0)
            fmt.format(Date(cal.timeInMillis))
        }
    }

    /** The x value passed in is a *day offset from windowStartMillis*, not an
     *  epoch millis. The formatter reconstructs the absolute date for
     *  labelling. Pattern adapts to the period: "MMM yy" for AllTime,
     *  "d MMM" for narrower windows. */
    fun dateLabelFormatter(
        windowStartMillis: Long,
        period: ChartsPeriod,
    ): CartesianValueFormatter {
        val pattern = if (period is ChartsPeriod.AllTime) "MMM yy" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return CartesianValueFormatter { _, value, _ ->
            val millis = windowStartMillis + (value.toDouble() * MILLIS_PER_DAY).toLong()
            fmt.format(Date(millis))
        }
    }

    fun locationPalette(slot: Int): Int =
        LOCATION_PALETTE[slot.coerceIn(0, LOCATION_PALETTE.size - 1)]
}
