package org.spsl.evtracker.ui.chargeedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeEditEvent
import org.spsl.evtracker.core.model.ChargeEditUiState
import org.spsl.evtracker.databinding.FragmentChargeEditBinding
import org.spsl.evtracker.domain.service.CostMode
import org.spsl.evtracker.ui.common.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@AndroidEntryPoint
class ChargeEditFragment : Fragment() {

    private val viewModel: ChargeEditViewModel by viewModels()

    private var _binding: FragmentChargeEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentChargeEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.chargeEditToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.chargeEditDateButton.setOnClickListener { showDateTimePicker() }
        binding.chargeEditOdometer.doAfterTextChanged { viewModel.setOdometer(it?.toString().orEmpty()) }
        binding.chargeEditKwh.doAfterTextChanged { viewModel.setKwh(it?.toString().orEmpty()) }
        binding.chargeEditLocation.doAfterTextChanged { viewModel.setLocation(it?.toString().orEmpty()) }
        binding.chargeEditNote.doAfterTextChanged { viewModel.setNote(it?.toString().orEmpty()) }
        binding.chargeEditCost.doAfterTextChanged { viewModel.setCostValue(it?.toString().orEmpty()) }
        binding.chargeEditTypeAc.setOnClickListener { viewModel.setChargeType("AC") }
        binding.chargeEditTypeDc.setOnClickListener { viewModel.setChargeType("DC") }
        binding.chargeEditCostModeTotal.setOnClickListener { viewModel.setCostMode(CostMode.TOTAL) }
        binding.chargeEditCostModePerKwh.setOnClickListener { viewModel.setCostMode(CostMode.PER_KWH) }
        binding.chargeEditCostToggle.setOnClickListener { viewModel.toggleCostExpanded() }
        binding.chargeEditSave.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    if (event is ChargeEditEvent.SavedAndExit) findNavController().popBackStack()
                }
            }
        }
    }

    private fun render(state: ChargeEditUiState) {
        binding.chargeEditToolbar.setTitle(
            if (state.mode is ChargeEditUiState.Mode.Edit) {
                R.string.charge_edit_edit_title
            } else {
                R.string.charge_edit_create_title
            },
        )
        binding.chargeEditDateButton.text =
            getString(R.string.hint_date_time) + ": " + DateFormat.formatEpochMs(state.eventDateMillis)
        binding.chargeEditOdometerLayout.suffixText = if (state.distanceUnit == "miles") "mi" else "km"
        if (binding.chargeEditOdometer.text?.toString() != state.odometer) {
            binding.chargeEditOdometer.setText(state.odometer)
        }
        binding.chargeEditOdometerLayout.error = state.odometerError?.let { getString(it) }
        if (binding.chargeEditKwh.text?.toString() != state.kwh) {
            binding.chargeEditKwh.setText(state.kwh)
        }
        binding.chargeEditKwhLayout.error = state.kwhError?.let { getString(it) }
        if (state.chargeType == "DC") {
            binding.chargeEditTypeGroup.check(R.id.charge_edit_type_dc)
        } else {
            binding.chargeEditTypeGroup.check(R.id.charge_edit_type_ac)
        }
        LocationChipBinder.bind(
            chipGroup = binding.chargeEditLocationChips,
            custom = state.locationChips.custom,
            onChipClick = { label -> viewModel.selectLocationChip(label) },
            onAddClick = { binding.chargeEditLocation.requestFocus() },
        )
        if (binding.chargeEditLocation.text?.toString() != state.location) {
            binding.chargeEditLocation.setText(state.location)
        }
        binding.chargeEditCostSection.isVisible = state.costExpanded
        if (state.costMode == CostMode.PER_KWH) {
            binding.chargeEditCostModeGroup.check(R.id.charge_edit_cost_mode_per_kwh)
        } else {
            binding.chargeEditCostModeGroup.check(R.id.charge_edit_cost_mode_total)
        }
        binding.chargeEditCostLayout.suffixText = state.currency
        if (binding.chargeEditCost.text?.toString() != state.costValue) {
            binding.chargeEditCost.setText(state.costValue)
        }
        if (binding.chargeEditNote.text?.toString() != state.note) {
            binding.chargeEditNote.setText(state.note)
        }
        binding.chargeEditSave.isEnabled = !state.saving
    }

    private fun showDateTimePicker() {
        val current = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(viewModel.uiState.value.eventDateMillis),
            ZoneId.systemDefault(),
        )
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(viewModel.uiState.value.eventDateMillis)
            .build()
        datePicker.addOnPositiveButtonClickListener { dateMs ->
            val timePicker = MaterialTimePicker.Builder()
                .setHour(current.hour)
                .setMinute(current.minute)
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .build()
            timePicker.addOnPositiveButtonClickListener {
                val zoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateMs), ZoneId.systemDefault())
                    .withHour(timePicker.hour)
                    .withMinute(timePicker.minute)
                    .withSecond(0)
                    .withNano(0)
                viewModel.setEventDate(zoned.toInstant().toEpochMilli())
            }
            timePicker.show(parentFragmentManager, "chargeEditTime")
        }
        datePicker.show(parentFragmentManager, "chargeEditDate")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
