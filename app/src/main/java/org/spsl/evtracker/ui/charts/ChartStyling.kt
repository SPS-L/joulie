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
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsPeriod

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
        0xFF757575.toInt()      // "Other" slot — neutral grey
    )

    fun resolveSeriesColors(context: Context): Pair<Int, Int> {
        fun resolve(attr: Int, fallback: Int): Int {
            val tv = TypedValue()
            return if (context.theme.resolveAttribute(attr, tv, true)) tv.data
                   else ContextCompat.getColor(context, fallback)
        }
        val ac = resolve(com.google.android.material.R.attr.colorPrimary, R.color.chart_ac_fallback)
        val dc = resolve(com.google.android.material.R.attr.colorTertiary, R.color.chart_dc_fallback)
        return ac to dc
    }

    fun configureLineChart(chart: LineChart, distanceUnit: String) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.granularity = 1f
        chart.xAxis.setAvoidFirstLastClipping(true)
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
    }

    fun configurePieChart(chart: PieChart) {
        chart.description.isEnabled = false
        chart.setNoDataText("")
        chart.legend.isEnabled = true
        chart.setUsePercentValues(false)
        chart.setEntryLabelColor(Color.WHITE)
        chart.isRotationEnabled = false
        chart.setHoleColor(Color.TRANSPARENT)
    }

    /** Formatter for monthly bar charts. Bars store the bucket *index* in
     *  Entry.x (values are 0f, 1f, 2f, ... — no Float aliasing). The
     *  formatter looks up the bucket at that index and renders its calendar
     *  month/year. Calling code passes the bucket list once per chart build;
     *  the closure captures it. */
    fun monthBucketFormatter(buckets: List<org.spsl.evtracker.core.model.MonthBucket>): IAxisValueFormatter {
        val fmt = SimpleDateFormat("MMM yy", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        return object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float, axis: AxisBase?): String {
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
    fun dateLabelFormatter(windowStartMillis: Long, period: ChartsPeriod): IAxisValueFormatter {
        val pattern = if (period is ChartsPeriod.AllTime) "MMM yy" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        return object : IAxisValueFormatter {
            override fun getFormattedValue(value: Float, axis: AxisBase?): String {
                val millis = windowStartMillis + (value.toDouble() * MILLIS_PER_DAY).toLong()
                return fmt.format(Date(millis))
            }
        }
    }

    fun locationPalette(slot: Int): Int =
        LOCATION_PALETTE[slot.coerceIn(0, LOCATION_PALETTE.size - 1)]
}
