package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardPage2Binding

@AndroidEntryPoint
class WizardPage2Fragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private var _binding: FragmentWizardPage2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWizardPage2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.wizardPage2MetricGroup.setOnCheckedChangeListener { _, checkedId ->
            val metric = when (checkedId) {
                R.id.wizard_page2_metric_km_per_kwh    -> "km_per_kwh"
                R.id.wizard_page2_metric_kwh_per_100km -> "kwh_per_100km"
                R.id.wizard_page2_metric_mi_per_kwh    -> "mi_per_kwh"
                else -> return@setOnCheckedChangeListener
            }
            if (metric != viewModel.state.value.metric) {
                viewModel.selectMetric(metric)
            }
        }

        binding.wizardPage2UnitGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val unit = when (checkedId) {
                R.id.wizard_page2_unit_km    -> "km"
                R.id.wizard_page2_unit_miles -> "miles"
                else -> return@addOnButtonCheckedListener
            }
            if (unit != viewModel.state.value.unit) {
                viewModel.selectUnit(unit)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val expectedRadio = when (state.metric) {
                        "km_per_kwh"    -> R.id.wizard_page2_metric_km_per_kwh
                        "kwh_per_100km" -> R.id.wizard_page2_metric_kwh_per_100km
                        else            -> R.id.wizard_page2_metric_mi_per_kwh
                    }
                    if (binding.wizardPage2MetricGroup.checkedRadioButtonId != expectedRadio) {
                        binding.wizardPage2MetricGroup.check(expectedRadio)
                    }
                    val expectedToggle = if (state.unit == "miles")
                        R.id.wizard_page2_unit_miles
                    else
                        R.id.wizard_page2_unit_km
                    if (binding.wizardPage2UnitGroup.checkedButtonId != expectedToggle) {
                        binding.wizardPage2UnitGroup.check(expectedToggle)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
