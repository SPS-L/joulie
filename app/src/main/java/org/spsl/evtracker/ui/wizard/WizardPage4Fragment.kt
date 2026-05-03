// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

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
import org.spsl.evtracker.databinding.FragmentWizardPage4Binding

@AndroidEntryPoint
class WizardPage4Fragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
    )

    private var _binding: FragmentWizardPage4Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWizardPage4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.wizardPage4Accept.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != viewModel.state.value.disclaimerAccepted) {
                viewModel.setDisclaimerAccepted(isChecked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (binding.wizardPage4Accept.isChecked != state.disclaimerAccepted) {
                        binding.wizardPage4Accept.setOnCheckedChangeListener(null)
                        binding.wizardPage4Accept.isChecked = state.disclaimerAccepted
                        binding.wizardPage4Accept.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked != viewModel.state.value.disclaimerAccepted) {
                                viewModel.setDisclaimerAccepted(isChecked)
                            }
                        }
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
