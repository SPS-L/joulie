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
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.core.model.MonthBucket
import org.spsl.evtracker.databinding.FragmentChartsTabBinding
import org.spsl.evtracker.domain.service.UnitConverter
import java.util.Calendar

@AndroidEntryPoint
class ChartsTabFragment : Fragment() {

    enum class TabKind { TREND, MONTHLY_KWH, MONTHLY_COST, AC_DC, LOCATIONS }

    private var _binding: FragmentChartsTabBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChartsViewModel by viewModels({ requireParentFragment() })

    private val kind: TabKind by lazy {
        TabKind.valueOf(requireArguments().getString(ARG_KIND)!!)
    }

    private var firstRenderConsumed = false

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
        container.removeAllViews()
        empty.isVisible = false
        subtitle.isVisible = false // reset; only AC/DC turns this on

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
        val chart = LineChart(requireContext())
        ChartStyling.configureLineChart(chart)
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val windowStart = charts.periodStartMillis

        // Y-axis mode follows primaryMetric. The kwh_per_100km branch returns null
        // for kmPerKwh <= 0 so the resulting Entry list skips invalid points instead
        // of plotting +/-Infinity. The other two branches always produce a value.
        val (yTransform, unitSuffix) = when (state.primaryMetric) {
            "kwh_per_100km" -> Pair<(Double) -> Double?, String>(
                { kmPerKwh -> if (kmPerKwh > 0.0) 100.0 / kmPerKwh else null },
                getString(R.string.charts_trend_y_kwh100),
            )
            "mi_per_kwh" -> Pair<(Double) -> Double?, String>(
                { kmPerKwh -> UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh) },
                getString(R.string.charts_trend_y_mi),
            )
            else -> Pair<(Double) -> Double?, String>(
                { kmPerKwh -> kmPerKwh },
                getString(R.string.charts_trend_y_kmh),
            )
        }

        // x = day offset from windowStart (Float-safe). Real millis stays in Entry.data
        // so the marker view shows the exact date.
        fun toEntries(points: List<org.spsl.evtracker.core.model.EfficiencyPoint>): List<Entry> =
            points.mapNotNull {
                val y = yTransform(it.kmPerKwh) ?: return@mapNotNull null
                val xDays = ((it.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY).toFloat()
                Entry(xDays, y.toFloat(), it.eventTimeMillis as Any)
            }
        val sets = mutableListOf<LineDataSet>()
        if (ac.isNotEmpty()) {
            sets += LineDataSet(toEntries(ac), getString(R.string.charts_trend_legend_ac)).apply {
                color = acColor
                setCircleColor(acColor)
                valueTextSize = 0f
            }
        }
        if (dc.isNotEmpty()) {
            sets += LineDataSet(toEntries(dc), getString(R.string.charts_trend_legend_dc)).apply {
                color = dcColor
                setCircleColor(dcColor)
                valueTextSize = 0f
            }
        }
        chart.data = LineData(sets.toList())
        chart.xAxis.valueFormatter = ChartStyling.dateLabelFormatter(windowStart, state.period)
        chart.marker = ChartsMarkerView(requireContext(), unitSuffix)
        if (!firstRenderConsumed) {
            chart.animateY(400)
            firstRenderConsumed = true
        }
        container.addView(
            chart,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
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
        val chart = BarChart(requireContext())
        ChartStyling.configureBarChart(chart)
        val (primary, _) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = charts.monthlyKwh.mapIndexed { i, b ->
            BarEntry(i.toFloat(), b.totalKwh.toFloat(), bucketMillis(b) as Any)
        }
        val ds = BarDataSet(entries, "kWh").apply {
            color = primary
            valueTextSize = 0f
        }
        chart.data = BarData(ds)
        chart.xAxis.valueFormatter = ChartStyling.monthBucketFormatter(charts.monthlyKwh)
        chart.marker = ChartsMarkerView(requireContext(), "kWh")
        if (!firstRenderConsumed) {
            chart.animateY(400)
            firstRenderConsumed = true
        }
        container.addView(
            chart,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun renderMonthlyCost(
        charts: ChartsUiState.Loaded,
        container: FrameLayout,
        empty: TextView,
    ) {
        if (charts.mixedCurrency) {
            // Spec §6.3: when mixedCurrency is true, the cost tab body is replaced
            // by the multi_currency_banner string. The other four tabs render
            // normally — there is intentionally no screen-global banner.
            empty.text = getString(R.string.multi_currency_banner)
            empty.isVisible = true
            return
        }
        if (charts.monthlyCost.isEmpty()) {
            empty.text = getString(R.string.charts_no_cost_period)
            empty.isVisible = true
            return
        }
        val chart = BarChart(requireContext())
        ChartStyling.configureBarChart(chart)
        val (_, tertiary) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = charts.monthlyCost.mapIndexed { i, b ->
            BarEntry(i.toFloat(), (b.totalCost ?: 0.0).toFloat(), bucketMillis(b) as Any)
        }
        val currency = charts.periodCurrency ?: ""
        val ds = BarDataSet(entries, currency).apply {
            color = tertiary
            valueTextSize = 0f
        }
        chart.data = BarData(ds)
        chart.xAxis.valueFormatter = ChartStyling.monthBucketFormatter(charts.monthlyCost)
        chart.marker = ChartsMarkerView(requireContext(), currency)
        if (!firstRenderConsumed) {
            chart.animateY(400)
            firstRenderConsumed = true
        }
        container.addView(
            chart,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
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
        val chart = PieChart(requireContext())
        ChartStyling.configurePieChart(chart)
        val (acColor, dcColor) = ChartStyling.resolveSeriesColors(requireContext())
        val entries = listOf(
            PieEntry(charts.acDc.acCount.toFloat(), getString(R.string.charts_trend_legend_ac)),
            PieEntry(charts.acDc.dcCount.toFloat(), getString(R.string.charts_trend_legend_dc)),
        )
        val ds = PieDataSet(entries, "").apply {
            colors = listOf(acColor, dcColor)
            valueTextSize = 12f
        }
        chart.data = PieData(ds)
        // Spec §6.4: centered hole text = total event count; sub-label below = kWh.
        chart.centerText = resources.getQuantityString(
            R.plurals.charts_acdc_count_center, total, total,
        )
        subtitle.text = getString(
            R.string.charts_acdc_kwh_subtitle, charts.acDc.acKwh, charts.acDc.dcKwh,
        )
        subtitle.isVisible = true
        if (!firstRenderConsumed) {
            chart.animateY(400)
            firstRenderConsumed = true
        }
        container.addView(
            chart,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
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
        val chart = PieChart(requireContext())
        ChartStyling.configurePieChart(chart)
        val entries = charts.locations.map { slice ->
            val label = if (slice.isOther) getString(R.string.charts_locations_other) else slice.label
            PieEntry(slice.count.toFloat(), label)
        }
        val ds = PieDataSet(entries, "").apply {
            colors = charts.locations.indices.map { ChartStyling.locationPalette(it) }
            valueTextSize = 12f
        }
        chart.data = PieData(ds)
        if (!firstRenderConsumed) {
            chart.animateY(400)
            firstRenderConsumed = true
        }
        container.addView(
            chart,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun bucketMillis(b: MonthBucket): Long {
        val cal = Calendar.getInstance()
        cal.set(b.year, b.month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // (Monthly x-axis formatting now lives in ChartStyling.monthBucketFormatter.)

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
