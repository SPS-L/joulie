// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.charts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.patrykandpatrick.vico.core.cartesian.CartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.views.cartesian.CartesianChartView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.databinding.FragmentChartsTabBinding
import org.spsl.evtracker.domain.service.UnitConverter
import org.spsl.evtracker.ui.common.PieChartView
import java.util.Calendar

@AndroidEntryPoint
class ChartsTabFragment : Fragment() {

    enum class TabKind { TREND, MONTHLY_KWH, MONTHLY_COST, AC_DC, LOCATIONS, DEGRADATION, CO2 }

    private var _binding: FragmentChartsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChartsViewModel by viewModels({ requireParentFragment() })

    private val kind: TabKind by lazy {
        TabKind.valueOf(requireArguments().getString(ARG_KIND)!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChartsTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: ChartsScreenState) {
        val container = binding.chartsTabChartContainer
        val empty = binding.chartsTabEmptyMessage
        val subtitle = binding.chartsTabSubtitle
        val banner = binding.chartsTabBanner
        container.removeAllViews()
        empty.isVisible = false
        subtitle.isVisible = false
        banner.isVisible = false

        val charts = state.charts
        if (charts !is ChartsUiState.Loaded) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        if (!charts.periodHasEvents) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }

        when (kind) {
            TabKind.TREND -> renderTrend(state, charts, container, empty)
            TabKind.MONTHLY_KWH -> renderMonthlyKwh(charts, container, empty)
            TabKind.MONTHLY_COST -> renderMonthlyCost(charts, container, empty)
            TabKind.AC_DC -> renderAcDc(charts, container, empty, subtitle)
            TabKind.LOCATIONS -> renderLocations(charts, container, empty)
            TabKind.DEGRADATION -> {
                val excluded = charts.derivedExcludedCount
                if (excluded > 0) {
                    banner.text = resources.getQuantityString(
                        R.plurals.charts_degradation_derived_excluded_banner,
                        excluded,
                        excluded,
                    )
                    banner.isVisible = true
                }
                renderDegradation(state, charts, container, empty)
            }
            TabKind.CO2 -> {
                state.currentCarbonReady?.let { ready ->
                    val bucketLabel = getString(ready.bucket.labelRes)
                    val valueText = "%.0f".format(ready.intensityGCo2PerKwh)
                    banner.text = getString(
                        R.string.charts_co2_current_intensity_banner,
                        valueText,
                        bucketLabel,
                    )
                    banner.setBackgroundColor(
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(),
                            ready.bucket.backgroundColorRes,
                        ),
                    )
                    banner.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            requireContext(),
                            ready.bucket.textColorRes,
                        ),
                    )
                    banner.isVisible = true
                }
                renderCo2(state, charts, container, empty)
            }
        }
    }

    /** Build a configured CartesianChartView and add it to [container] with
     *  match-parent layout params. Caller sets `chart = …` and pushes data
     *  via [pushData] before returning. */
    private fun newCartesianChartView(container: FrameLayout): CartesianChartView {
        val view = CartesianChartView(requireContext())
        container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return view
    }

    /**
     * Build a transient [CartesianChartModelProducer], push a single
     * transaction synchronously (the data is already in memory), and bind it
     * to the chart view. The producer is kept alive by the chart view via
     * its `modelProducer` field.
     */
    private fun pushData(
        view: CartesianChartView,
        block: CartesianChartModelProducer.Transaction.() -> Unit,
    ) {
        val producer = CartesianChartModelProducer()
        view.modelProducer = producer
        // The block is in-memory data assembly only — no I/O — so blocking
        // the main thread for one synchronous transaction is acceptable.
        runBlocking { producer.runTransaction(block) }
    }

    private fun axisLabel(): TextComponent {
        val color = ChartStyling.resolveAxisColors(requireContext()).text
        return TextComponent(color = color)
    }

    private fun axisLine(): LineComponent {
        val gridColor = ChartStyling.resolveAxisColors(requireContext()).grid
        return LineComponent(fill = Fill(gridColor), thicknessDp = 1f)
    }

    private fun startAxis() = VerticalAxis.start(
        label = axisLabel(),
        line = axisLine(),
        guideline = axisLine(),
        tick = axisLine(),
    )

    private fun bottomAxis(
        valueFormatter: com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter,
    ) = HorizontalAxis.bottom(
        label = axisLabel(),
        line = axisLine(),
        guideline = axisLine(),
        tick = axisLine(),
        valueFormatter = valueFormatter,
    )

    private fun renderCo2(
        state: ChartsScreenState,
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        val points = charts.co2Cumulative
        if (points.isEmpty()) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val windowStart = charts.periodStartMillis
        val xValues = points.map { (it.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY }
        val evY = points.map { it.cumulativeEvCo2Kg }
        val iceY = points.map { it.cumulativeIceCo2Kg }

        val view = newCartesianChartView(container)
        view.chart = CartesianChart(
            LineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(acColor)),
                    ),
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(dcColor)),
                    ),
                ),
            ),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(ChartStyling.dateLabelFormatter(windowStart, state.period)),
        )
        pushData(view) {
            lineSeries {
                series(x = xValues, y = evY)
                series(x = xValues, y = iceY)
            }
        }
    }

    private fun renderDegradation(
        state: ChartsScreenState,
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        val nominal = charts.nominalBatteryKwh
        val points = charts.capacity
        if (nominal == null || nominal <= 0.0) {
            empty.text = getString(R.string.charts_degradation_no_battery)
            empty.isVisible = true
            return
        }
        if (points.isEmpty()) {
            empty.text = getString(R.string.charts_degradation_need_three)
            empty.isVisible = true
            return
        }
        val (acColor, _) = ChartStyling.resolveSeriesColors(requireContext())
        val windowStart = charts.periodStartMillis
        val xValues = points.map { (it.eventDate - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY }
        val yValues = points.map { it.effectiveCapacityKwh }

        val view = newCartesianChartView(container)
        // Dashed reference line at nominal capacity as a HorizontalLine
        // decoration; Vico's preferred replacement for MPAndroidChart's
        // LimitLine.
        val nominalReference = HorizontalLine(
            y = { nominal },
            line = LineComponent(fill = Fill(acColor), thicknessDp = 1f),
            labelComponent = null,
        )
        view.chart = CartesianChart(
            LineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(acColor)),
                    ),
                ),
            ),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(ChartStyling.dateLabelFormatter(windowStart, state.period)),
            decorations = listOf(nominalReference),
        )
        pushData(view) {
            lineSeries {
                series(x = xValues, y = yValues)
            }
        }
    }

    private fun renderTrend(
        state: ChartsScreenState,
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        val ac = charts.trend.acPoints
        val dc = charts.trend.dcPoints
        if (ac.isEmpty() && dc.isEmpty()) {
            empty.text = getString(R.string.charts_trend_need_two)
            empty.isVisible = true
            return
        }
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val windowStart = charts.periodStartMillis

        // Y-axis transform follows primaryMetric.
        val yTransform: (Double) -> Double? = when (state.primaryMetric) {
            "kwh_per_100km" -> { kpkwh -> if (kpkwh > 0.0) 100.0 / kpkwh else null }
            "mi_per_kwh" -> { kpkwh -> UnitConverter.kmPerKwhToMiPerKwh(kpkwh) }
            else -> { kpkwh -> kpkwh }
        }

        fun toXY(points: List<org.spsl.evtracker.core.model.EfficiencyPoint>): Pair<List<Double>, List<Double>> {
            val xs = mutableListOf<Double>()
            val ys = mutableListOf<Double>()
            for (p in points) {
                val y = yTransform(p.kmPerKwh) ?: continue
                xs += (p.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY
                ys += y
            }
            return xs to ys
        }
        val (acX, acY) = toXY(ac)
        val (dcX, dcY) = toXY(dc)

        val lines = buildList {
            if (acX.isNotEmpty()) {
                add(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(acColor)),
                    ),
                )
            }
            if (dcX.isNotEmpty()) {
                add(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(dcColor)),
                    ),
                )
            }
        }

        val view = newCartesianChartView(container)
        view.chart = CartesianChart(
            LineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(lines),
            ),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(ChartStyling.dateLabelFormatter(windowStart, state.period)),
        )
        pushData(view) {
            lineSeries {
                if (acX.isNotEmpty()) series(x = acX, y = acY)
                if (dcX.isNotEmpty()) series(x = dcX, y = dcY)
            }
        }
    }

    private fun renderMonthlyKwh(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        if (charts.monthlyKwh.isEmpty()) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        val (primary, _) = ChartStyling.resolveSeriesColors(requireContext())
        val xs = charts.monthlyKwh.indices.map { it.toDouble() }
        val ys = charts.monthlyKwh.map { it.totalKwh }

        val view = newCartesianChartView(container)
        view.chart = CartesianChart(
            ColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    LineComponent(fill = Fill(primary), thicknessDp = 12f),
                ),
            ),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(ChartStyling.monthBucketFormatter(charts.monthlyKwh)),
        )
        pushData(view) {
            columnSeries { series(x = xs, y = ys) }
        }
    }

    private fun renderMonthlyCost(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        if (charts.mixedCurrency) {
            empty.text = getString(R.string.multi_currency_banner)
            empty.isVisible = true
            return
        }
        if (charts.monthlyCost.isEmpty()) {
            empty.text = getString(R.string.charts_no_cost_period)
            empty.isVisible = true
            return
        }
        val (_, tertiary) = ChartStyling.resolveSeriesColors(requireContext())
        val xs = charts.monthlyCost.indices.map { it.toDouble() }
        val ys = charts.monthlyCost.map { it.totalCost ?: 0.0 }

        val view = newCartesianChartView(container)
        view.chart = CartesianChart(
            ColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    LineComponent(fill = Fill(tertiary), thicknessDp = 12f),
                ),
            ),
            startAxis = startAxis(),
            bottomAxis = bottomAxis(ChartStyling.monthBucketFormatter(charts.monthlyCost)),
        )
        pushData(view) {
            columnSeries { series(x = xs, y = ys) }
        }
    }

    private fun renderAcDc(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
        subtitle: TextView,
    ) {
        val total = charts.acDc.acCount + charts.acDc.dcCount
        if (total == 0) {
            empty.text = getString(R.string.charts_no_data_period)
            empty.isVisible = true
            return
        }
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val pie = PieChartView(requireContext()).apply {
            labelColor = ChartStyling.resolveAxisColors(context).text
            slices = listOf(
                PieChartView.Slice(getString(R.string.charts_trend_legend_ac), charts.acDc.acCount.toFloat(), acColor),
                PieChartView.Slice(getString(R.string.charts_trend_legend_dc), charts.acDc.dcCount.toFloat(), dcColor),
            )
            centerText = resources.getQuantityString(R.plurals.charts_acdc_count_center, total, total)
        }
        container.addView(
            pie,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        subtitle.text = getString(
            R.string.charts_acdc_kwh_subtitle, charts.acDc.acKwh, charts.acDc.dcKwh,
        )
        subtitle.isVisible = true
    }

    private fun renderLocations(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        if (charts.locations.isEmpty()) {
            empty.text = getString(R.string.charts_no_locations_period)
            empty.isVisible = true
            return
        }
        val pie = PieChartView(requireContext()).apply {
            labelColor = ChartStyling.resolveAxisColors(context).text
            slices = charts.locations.mapIndexed { i, slice ->
                val label = if (slice.isOther) getString(R.string.charts_locations_other) else slice.label
                PieChartView.Slice(label, slice.count.toFloat(), ChartStyling.locationPalette(i))
            }
        }
        container.addView(
            pie,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    @Suppress("unused")
    private fun bucketMillis(b: MonthBucket): Long {
        val cal = Calendar.getInstance()
        cal.set(b.year, b.month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_KIND = "kind"
        fun newInstance(kind: TabKind): ChartsTabFragment = ChartsTabFragment().apply {
            arguments = Bundle().apply { putString(ARG_KIND, kind.name) }
        }
    }
}
