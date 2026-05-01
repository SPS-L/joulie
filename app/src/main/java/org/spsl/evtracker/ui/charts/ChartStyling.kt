package org.spsl.evtracker.ui.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.ValueFormatter
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure (Context-only) helpers for MPAndroidChart configuration. Keeps Fragment
 * code thin and JVM-testable for everything except color resolution which
 * intrinsically needs a Context.
 */
object ChartStyling {

    /** Used by the trend tab to express the line chart's x-axis as a day offset
     *  from the period start. Storing raw epoch millis as a Float in Entry.x
     *  aliases because Float has only ~7 decimal digits of integer precision
     *  while modern timestamps need ~13. Day offsets stay well within Float
     *  precision (a 20-year window is ~7300 days). */
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
     * Theme-aware axis / legend / gridline colors. MPAndroidChart's defaults
     * are hardcoded grey/black, which becomes invisible on a dark surface —
     * we resolve the M3 `colorOnSurface` for text and `colorOutlineVariant`
     * for gridlines so charts follow the system theme.
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

    /**
     * Apply theme-aware axis and legend colors to any MPAndroidChart so its
     * labels stay readable under both Light and Dark M3 themes. Called by
     * each `configureXxxChart` helper after the basic chart shape is set.
     */
    private fun applyThemeAwareAxisColors(chart: com.github.mikephil.charting.charts.Chart<*>) {
        val colors = resolveAxisColors(chart.context)
        chart.legend.textColor = colors.text
        chart.xAxis.textColor = colors.text
        chart.xAxis.gridColor = colors.grid
        chart.xAxis.axisLineColor = colors.grid
        if (chart is com.github.mikephil.charting.charts.BarLineChartBase<*>) {
            chart.axisLeft.textColor = colors.text
            chart.axisLeft.gridColor = colors.grid
            chart.axisLeft.axisLineColor = colors.grid
            chart.axisRight.textColor = colors.text
            chart.axisRight.gridColor = colors.grid
            chart.axisRight.axisLineColor = colors.grid
        }
    }

    fun configureLineChart(chart: LineChart) {
        // Distance unit is handled by the caller (km↔miles conversion happens
        // when building Entry y-values in ChartsTabFragment.renderTrend, and the
        // y-axis label suffix is passed to ChartsMarkerView). This helper is
        // pure axis/legend/zoom configuration.
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setAvoidFirstLastClipping(true)
        applyThemeAwareAxisColors(chart)
    }

    fun configureBarChart(chart: BarChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.setFitBars(true)
        applyThemeAwareAxisColors(chart)
    }

    fun configurePieChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.legend.isEnabled = true
        chart.setUsePercentValues(false)
        // Slice labels are drawn ON the saturated palette colors — white is
        // correct in both themes regardless of system surface colors.
        chart.setEntryLabelColor(Color.WHITE)
        chart.isRotationEnabled = false
        chart.setHoleColor(Color.TRANSPARENT)
        // Hole-text (PieChart.centerText) sits on the surface, not on a slice
        // — must follow the theme so it's readable in dark mode.
        chart.setCenterTextColor(resolveAxisColors(chart.context).text)
        applyThemeAwareAxisColors(chart)
    }

    /** Formatter for monthly bar charts. Bars store the bucket *index* in
     *  Entry.x (values are 0f, 1f, 2f, ... — no Float aliasing). The
     *  formatter looks up the bucket at that index and renders its calendar
     *  month/year. Calling code passes the bucket list once per chart build;
     *  the closure captures it. */
    fun monthBucketFormatter(buckets: List<org.spsl.evtracker.core.model.MonthBucket>): ValueFormatter {
        val fmt = SimpleDateFormat("MMM yy", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        return object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                if (buckets.isEmpty()) return ""
                val i = value.toInt().coerceIn(0, buckets.lastIndex)
                val b = buckets[i]
                cal.clear()
                cal.set(b.year, b.month - 1, 1, 0, 0, 0)
                return fmt.format(Date(cal.timeInMillis))
            }
        }
    }

    /** The x value passed in is a *day offset from windowStartMillis*, not an epoch
     *  millis. The formatter reconstructs the absolute date for labelling. */
    fun dateLabelFormatter(windowStartMillis: Long, period: ChartsPeriod): ValueFormatter {
        val pattern = if (period is ChartsPeriod.AllTime) "MMM yy" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val millis = windowStartMillis + (value.toDouble() * MILLIS_PER_DAY).toLong()
                return fmt.format(Date(millis))
            }
        }
    }

    fun locationPalette(slot: Int): Int =
        LOCATION_PALETTE[slot.coerceIn(0, LOCATION_PALETTE.size - 1)]
}
