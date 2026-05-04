// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.charts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsPeriod
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.core.model.ChartsUiState
import org.spsl.evtracker.databinding.FragmentChartsBinding

@AndroidEntryPoint
class ChartsFragment : Fragment() {

    private val viewModel: ChartsViewModel by viewModels()

    private var _binding: FragmentChartsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pagerAdapter: ChartsPagerAdapter
    private lateinit var tabMediator: TabLayoutMediator

    /** Period to revert to when the user dismisses the Custom date-range picker
     *  via the negative button or system back. Mirrors DashboardFragment's
     *  selectedTabBeforePicker pattern. Updated only when a *concrete* (non-Custom)
     *  chip is tapped, so cancelling the Custom picker restores the prior choice. */
    private var lastConcretePeriod: ChartsPeriod = ChartsPeriod.Last12Months

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChartsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpPager()
        setUpPeriodChips()

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

    private fun setUpPager() {
        pagerAdapter = ChartsPagerAdapter(this)
        binding.chartsPager.adapter = pagerAdapter
        tabMediator = TabLayoutMediator(binding.chartsTabLayout, binding.chartsPager) { tab, pos ->
            tab.text = when (pagerAdapter.tabKindAt(pos)) {
                ChartsTabFragment.TabKind.TREND -> getString(R.string.charts_tab_trend)
                ChartsTabFragment.TabKind.MONTHLY_KWH -> getString(R.string.charts_tab_monthly_kwh)
                ChartsTabFragment.TabKind.MONTHLY_COST -> getString(R.string.charts_tab_monthly_cost)
                ChartsTabFragment.TabKind.AC_DC -> getString(R.string.charts_tab_ac_dc)
                ChartsTabFragment.TabKind.LOCATIONS -> getString(R.string.charts_tab_locations)
                ChartsTabFragment.TabKind.DEGRADATION -> getString(R.string.charts_tab_degradation)
                ChartsTabFragment.TabKind.CO2 -> getString(R.string.charts_tab_co2)
            }
        }
        tabMediator.attach()
    }

    private fun setUpPeriodChips() {
        binding.chipLast6Months.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.Last6Months
            viewModel.selectPeriod(ChartsPeriod.Last6Months)
        }
        binding.chipLast12Months.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.Last12Months
            viewModel.selectPeriod(ChartsPeriod.Last12Months)
        }
        binding.chipAllTime.setOnClickListener {
            lastConcretePeriod = ChartsPeriod.AllTime
            viewModel.selectPeriod(ChartsPeriod.AllTime)
        }
        binding.chipCustom.setOnClickListener {
            // Do NOT update lastConcretePeriod — we may need it to restore on cancel.
            viewModel.onCustomChipClicked()
        }
    }

    private fun render(state: ChartsScreenState) {
        when (state.charts) {
            ChartsUiState.Loading -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = false
            }
            ChartsUiState.NoCar -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = true
                binding.chartsEmptyHeadline.setText(R.string.empty_no_car_headline)
                binding.chartsEmptyCta.setText(R.string.empty_no_car_cta)
                binding.chartsEmptyCta.setOnClickListener { viewModel.onAddCarCta() }
            }
            ChartsUiState.NoEvents -> {
                binding.chartsContent.isVisible = false
                binding.chartsEmptyContainer.isVisible = true
                binding.chartsEmptyHeadline.setText(R.string.empty_no_events_headline)
                binding.chartsEmptyCta.setText(R.string.empty_no_events_cta)
                binding.chartsEmptyCta.setOnClickListener { viewModel.onLogChargeCta() }
            }
            is ChartsUiState.Loaded -> {
                binding.chartsContent.isVisible = true
                binding.chartsEmptyContainer.isVisible = false
                // Multi-currency banner is rendered *inside the cost tab body*
                // (see ChartsTabFragment.renderMonthlyCost), not screen-globally.
            }
        }
        // Reflect the current period selection on the chip group.
        when (val p = state.period) {
            ChartsPeriod.Last6Months -> binding.chipLast6Months.isChecked = true
            ChartsPeriod.Last12Months -> binding.chipLast12Months.isChecked = true
            ChartsPeriod.AllTime -> binding.chipAllTime.isChecked = true
            is ChartsPeriod.Custom -> {
                binding.chipCustom.isChecked = true
                // Spec §4: Custom chip exposes the selected range as its
                // contentDescription so screen readers announce the actual
                // window. DateUtils.formatDateRange chooses a locale-aware
                // representation; we include FORMAT_SHOW_YEAR to disambiguate
                // ranges that span calendar boundaries.
                binding.chipCustom.contentDescription = android.text.format.DateUtils
                    .formatDateRange(
                        requireContext(),
                        p.fromMillis,
                        p.toMillis,
                        android.text.format.DateUtils.FORMAT_SHOW_DATE
                            or android.text.format.DateUtils.FORMAT_SHOW_YEAR,
                    )
            }
        }
        // Reset the Custom chip's contentDescription back to its label when the
        // user picks a non-Custom period, so screen readers don't read stale
        // range text.
        if (state.period !is ChartsPeriod.Custom) {
            binding.chipCustom.contentDescription = getString(R.string.charts_period_custom)
        }
    }

    private fun handleEvent(event: ChartsEvent) {
        when (event) {
            ChartsEvent.OpenCustomRangePicker -> showCustomRangePicker()
            ChartsEvent.NavigateToCars ->
                findNavController().navigate(R.id.action_charts_to_cars)
            ChartsEvent.NavigateToChargeEdit ->
                findNavController().navigate(R.id.action_charts_to_chargeEdit)
        }
    }

    private fun showCustomRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.period_custom)
            .build()
        picker.addOnPositiveButtonClickListener { range ->
            val from = range.first ?: return@addOnPositiveButtonClickListener
            val to = range.second ?: return@addOnPositiveButtonClickListener
            // Custom *is* the new selection; record it so a future Cancel of a
            // re-opened picker would still restore *some* concrete prior choice.
            // We deliberately do NOT update lastConcretePeriod here.
            viewModel.selectCustomRange(from, to)
        }
        picker.addOnNegativeButtonClickListener { restorePreviousPeriodSelection() }
        picker.addOnCancelListener { restorePreviousPeriodSelection() }
        picker.show(parentFragmentManager, "chartsCustomRange")
    }

    private fun restorePreviousPeriodSelection() {
        // Driving via the VM (not via chip.isChecked) preserves single-source-of-truth:
        // the next uiState emission re-renders the chip group from state.period.
        viewModel.selectPeriod(lastConcretePeriod)
    }

    override fun onDestroyView() {
        if (this::tabMediator.isInitialized) tabMediator.detach()
        binding.chartsPager.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
