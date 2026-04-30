package org.spsl.evtracker.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.DashboardEvent
import org.spsl.evtracker.core.model.DashboardPeriod
import org.spsl.evtracker.core.model.DashboardScreenState
import org.spsl.evtracker.core.model.EmptyState
import org.spsl.evtracker.core.model.Stats
import org.spsl.evtracker.databinding.FragmentDashboardBinding
import org.spsl.evtracker.ui.common.MoneyFormat

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private val viewModel: DashboardViewModel by viewModels()

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var selectedTabBeforePicker: Int = 2 // Last30Days

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpTabs()
        setUpFilterChips()
        setUpFab()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    private fun setUpTabs() {
        binding.dashboardPeriodTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        selectedTabBeforePicker = 0
                        viewModel.selectPeriod(DashboardPeriod.SincePreviousCharge)
                    }
                    1 -> {
                        selectedTabBeforePicker = 1
                        viewModel.selectPeriod(DashboardPeriod.Last7Days)
                    }
                    2 -> {
                        selectedTabBeforePicker = 2
                        viewModel.selectPeriod(DashboardPeriod.Last30Days)
                    }
                    3 -> {
                        selectedTabBeforePicker = 3
                        viewModel.selectPeriod(DashboardPeriod.Year)
                    }
                    4 -> showCustomDatePicker()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun showCustomDatePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.period_custom)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val from = range.first ?: return@addOnPositiveButtonClickListener
            val to = range.second ?: return@addOnPositiveButtonClickListener
            selectedTabBeforePicker = 4
            viewModel.selectCustomRange(from, to)
        }
        picker.addOnNegativeButtonClickListener {
            binding.dashboardPeriodTabs.getTabAt(selectedTabBeforePicker)?.select()
        }
        picker.addOnCancelListener {
            binding.dashboardPeriodTabs.getTabAt(selectedTabBeforePicker)?.select()
        }
        picker.show(parentFragmentManager, "dashboardCustomRange")
    }

    private fun setUpFilterChips() {
        binding.dashboardChipAll.setOnClickListener { viewModel.selectFilter(ChargeTypeFilter.ALL) }
        binding.dashboardChipAc.setOnClickListener { viewModel.selectFilter(ChargeTypeFilter.AC) }
        binding.dashboardChipDc.setOnClickListener { viewModel.selectFilter(ChargeTypeFilter.DC) }
    }

    private fun setUpFab() {
        binding.dashboardFab.setOnClickListener { viewModel.onFabClick() }
    }

    private fun render(state: DashboardScreenState) {
        renderCarSpinner(state)
        renderEmptyState(state)
        renderStatsCards(state)
        renderCostCard(state)
        renderBanner(state)
    }

    private fun renderCarSpinner(state: DashboardScreenState) {
        val adapter = DashboardCarSpinnerAdapter(requireContext(), state.cars)
        binding.dashboardCarSpinner.adapter = adapter
        val activeIndex = state.cars.indexOfFirst { it.id == state.activeCarId }
        if (activeIndex >= 0) {
            binding.dashboardCarSpinner.setSelection(activeIndex, false)
        }
        binding.dashboardCarSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == adapter.tailIndex) {
                    viewModel.onManageCarsClick()
                    if (activeIndex >= 0) binding.dashboardCarSpinner.setSelection(activeIndex, false)
                } else {
                    val carId = state.cars[position].id
                    if (carId != state.activeCarId) viewModel.selectCar(carId)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.dashboardCarSpinner.isVisible = state.cars.isNotEmpty()
    }

    private fun renderEmptyState(state: DashboardScreenState) {
        when (state.dashboard.emptyState) {
            EmptyState.NoCar -> {
                binding.dashboardEmptyContainer.isVisible = true
                binding.dashboardContent.isVisible = false
                binding.dashboardEmptyHeadline.setText(R.string.empty_no_car_headline)
                binding.dashboardEmptyCta.setText(R.string.empty_no_car_cta)
                binding.dashboardEmptyCta.setOnClickListener { viewModel.onAddCarCtaClick() }
                binding.dashboardFab.isEnabled = false
                binding.dashboardFab.alpha = 0.5f
            }
            EmptyState.NoEvents -> {
                binding.dashboardEmptyContainer.isVisible = true
                binding.dashboardContent.isVisible = false
                binding.dashboardEmptyHeadline.setText(R.string.empty_no_events_headline)
                binding.dashboardEmptyCta.setText(R.string.empty_no_events_cta)
                binding.dashboardEmptyCta.setOnClickListener { viewModel.onLogChargeCtaClick() }
                binding.dashboardFab.isEnabled = true
                binding.dashboardFab.alpha = 1.0f
            }
            null -> {
                binding.dashboardEmptyContainer.isVisible = false
                binding.dashboardContent.isVisible = true
                binding.dashboardFab.isEnabled = state.activeCarId != -1
                binding.dashboardFab.alpha = if (state.activeCarId != -1) 1.0f else 0.5f
            }
        }
    }

    private fun renderStatsCards(state: DashboardScreenState) {
        val stats = state.dashboard.stats ?: return
        val cardSet = pickMetrics(state.primaryMetric, stats)
        binding.dashboardPrimaryValue.text = cardSet.primaryValue ?: getString(R.string.metric_unavailable)
        binding.dashboardPrimaryLabel.text = cardSet.primaryLabel
        binding.dashboardPrimarySubtitle.isVisible = cardSet.primaryValue == null
        binding.dashboardPrimarySubtitle.setText(R.string.metric_need_two_charges)

        binding.dashboardSecondaryAValue.text = cardSet.secondaryAValue ?: getString(R.string.metric_unavailable)
        binding.dashboardSecondaryALabel.text = cardSet.secondaryALabel
        binding.dashboardSecondaryBValue.text = cardSet.secondaryBValue ?: getString(R.string.metric_unavailable)
        binding.dashboardSecondaryBLabel.text = cardSet.secondaryBLabel
    }

    private data class CardSet(
        val primaryValue: String?,
        val primaryLabel: String,
        val secondaryAValue: String?,
        val secondaryALabel: String,
        val secondaryBValue: String?,
        val secondaryBLabel: String,
    )

    private fun pickMetrics(primaryMetric: String, stats: Stats): CardSet {
        fun format(d: Double?): String? = d?.let { "%.2f".format(it) }
        val km = format(stats.avgKmPerKwh)
        val kwh100 = format(stats.avgKwhPer100Km)
        val mi = format(stats.avgMiPerKwh)
        return when (primaryMetric) {
            "kwh_per_100km" -> CardSet(kwh100, getString(R.string.metric_kwh_per_100km), km, getString(R.string.metric_km_per_kwh), mi, getString(R.string.metric_mi_per_kwh))
            "mi_per_kwh" -> CardSet(mi, getString(R.string.metric_mi_per_kwh), km, getString(R.string.metric_km_per_kwh), kwh100, getString(R.string.metric_kwh_per_100km))
            else -> CardSet(km, getString(R.string.metric_km_per_kwh), mi, getString(R.string.metric_mi_per_kwh), kwh100, getString(R.string.metric_kwh_per_100km))
        }
    }

    private fun renderCostCard(state: DashboardScreenState) {
        val stats = state.dashboard.stats
        val showCost = stats != null &&
            stats.totalCost != null &&
            stats.currency != null &&
            stats.costPerKm != null &&
            !state.dashboard.showMultiCurrencyBanner
        binding.dashboardCardCost.isVisible = showCost
        if (showCost && stats != null) {
            val ccy = stats.currency!!
            binding.dashboardCostTotal.text = MoneyFormat.format(stats.totalCost!!, ccy)
            binding.dashboardCostPerKm.text = MoneyFormat.format(stats.costPerKm!!, ccy) + " / km"
            val cp100 = stats.costPer100Km
            binding.dashboardCostPer100km.text = if (cp100 != null) MoneyFormat.format(cp100, ccy) + " / 100km" else ""
        }
    }

    private fun renderBanner(state: DashboardScreenState) {
        binding.dashboardMultiCurrencyBanner.isVisible = state.dashboard.showMultiCurrencyBanner
    }

    private fun handleEvent(event: DashboardEvent) {
        when (event) {
            DashboardEvent.NavigateToChargeEdit ->
                findNavController().navigate(R.id.action_dashboard_to_chargeEdit)
            DashboardEvent.NavigateToCars ->
                findNavController().navigate(R.id.action_dashboard_to_cars)
            DashboardEvent.NavigateToManageCars ->
                findNavController().navigate(R.id.action_dashboard_to_cars)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
