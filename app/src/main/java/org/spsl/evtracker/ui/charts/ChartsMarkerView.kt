// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.charts

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import org.spsl.evtracker.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tap-to-inspect marker for Line and Bar charts. Anchors above the data point.
 * Bar markers receive an Entry whose x is the bucket index — not a millis value
 * — so callers must provide an Entry with `data` set to the millis when needed.
 */
class ChartsMarkerView(context: Context, valueSuffix: String) : MarkerView(context, R.layout.view_chart_marker) {

    private val dateLabel: TextView = findViewById(R.id.marker_date)
    private val valueLabel: TextView = findViewById(R.id.marker_value)
    private val dateFmt = SimpleDateFormat("d MMM yy", Locale.getDefault())
    private val suffix = valueSuffix

    override fun refreshContent(e: Entry, highlight: Highlight) {
        val millis = (e.data as? Long) ?: e.x.toLong()
        dateLabel.text = dateFmt.format(Date(millis))
        valueLabel.text = String.format(Locale.getDefault(), "%.2f %s", e.y, suffix)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF =
        MPPointF.getInstance(-(width / 2f), -height.toFloat())
}
