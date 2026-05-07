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
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardBinding

@AndroidEntryPoint
class WizardFragment : Fragment() {

    private val viewModel: WizardViewModel by viewModels()

    private var _binding: FragmentWizardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.wizardPager.adapter = WizardPagerAdapter(this)
        binding.wizardPager.isUserInputEnabled = false

        TabLayoutMediator(binding.wizardDots, binding.wizardPager) { _, _ -> }.attach()

        // The dots are decorative-only — the wizard navigates via the
        // Back / Next buttons and `wizardPager.isUserInputEnabled = false`
        // disables swipe. Mark each tab view non-clickable / non-focusable so
        // Espresso's TouchTargetSizeCheck doesn't flag the
        // 24 dp dots as undersized clickable targets — they are not click
        // targets at all.
        repeat(binding.wizardDots.tabCount) { i ->
            binding.wizardDots.getTabAt(i)?.view?.apply {
                isClickable = false
                isFocusable = false
            }
        }

        binding.wizardButtonBack.setOnClickListener { viewModel.goBack() }
        binding.wizardButtonNext.setOnClickListener { onPrimaryButtonClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (binding.wizardPager.currentItem != state.page) {
                        binding.wizardPager.setCurrentItem(state.page, true)
                    }
                    binding.wizardButtonBack.visibility =
                        if (state.page == 0) View.INVISIBLE else View.VISIBLE
                    binding.wizardButtonNext.text = when (state.page) {
                        0 -> getString(R.string.wizard_button_get_started)
                        3 -> getString(R.string.wizard_button_finish)
                        else -> getString(R.string.wizard_button_next)
                    }
                    // On page 3 (About + Disclaimer) Finish is gated by acceptance.
                    binding.wizardButtonNext.isEnabled =
                        state.page != 3 || state.disclaimerAccepted
                }
            }
        }
    }

    private fun onPrimaryButtonClicked() {
        if (viewModel.state.value.page < 3) {
            viewModel.goNext()
        } else {
            binding.wizardButtonNext.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.finish()
                findNavController().navigate(R.id.action_wizard_to_dashboard)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.wizardPager.adapter = null
        _binding = null
    }
}
