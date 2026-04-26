package org.spsl.evtracker.ui.chargeedit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.spsl.evtracker.databinding.FragmentChargeEditBinding

@AndroidEntryPoint
class ChargeEditFragment : Fragment() {

    @Suppress("unused")
    private val viewModel: ChargeEditViewModel by viewModels()

    private var _binding: FragmentChargeEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChargeEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
