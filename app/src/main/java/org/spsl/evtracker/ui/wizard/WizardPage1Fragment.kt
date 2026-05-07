// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.databinding.FragmentWizardPage1Binding

@AndroidEntryPoint
class WizardPage1Fragment : Fragment() {

    private var _binding: FragmentWizardPage1Binding? = null
    private val binding get() = _binding!!

    // page 0 needs to talk to the wizard's shared VM so the language
    // pick persists immediately (DataStore + AppCompatDelegate). Activity-
    // scoped because WizardFragment hosts this Fragment via a ViewPager2.
    private val viewModel: WizardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWizardPage1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.wizardPage1LanguageRow.setOnClickListener { showLanguageDialog() }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.wizardPage1LanguageValue.text = languageLabelFor(state.languageTag)
                }
            }
        }
    }

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(R.string.settings_language_follow_system),
            getString(R.string.language_name_en),
            getString(R.string.language_name_el),
            getString(R.string.language_name_tr),
            getString(R.string.language_name_ru),
        )
        val tokens = arrayOf("", "en", "el", "tr", "ru")
        val current = viewModel.state.value.languageTag
        val checked = tokens.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language_dialog_title)
            .setSingleChoiceItems(labels, checked) { d, which ->
                viewModel.onLanguageSelected(tokens[which])
                d.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun languageLabelFor(tag: String): String = when (tag) {
        "en" -> getString(R.string.language_name_en)
        "el" -> getString(R.string.language_name_el)
        "tr" -> getString(R.string.language_name_tr)
        "ru" -> getString(R.string.language_name_ru)
        else -> getString(R.string.settings_language_follow_system)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
