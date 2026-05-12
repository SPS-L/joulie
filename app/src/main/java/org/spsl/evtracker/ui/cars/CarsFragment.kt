// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarsEvent
import org.spsl.evtracker.databinding.FragmentCarsBinding

@AndroidEntryPoint
class CarsFragment : Fragment() {

    private val viewModel: CarsViewModel by viewModels()

    private var _binding: FragmentCarsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CarsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCarsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.carsToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        adapter = CarsAdapter(
            onSetActive = { viewModel.onRowSetActiveClick(it) },
            onEdit = { viewModel.onRowEditClick(it) },
            onDelete = { viewModel.onRowDeleteClick(it) },
        )
        binding.carsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.carsRecycler.adapter = adapter
        binding.carsFab.setOnClickListener { viewModel.onFabClick() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { adapter.submitList(it.cars) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handleEvent(it) }
            }
        }
    }

    private fun handleEvent(event: CarsEvent) {
        when (event) {
            CarsEvent.ShowAddDialog ->
                showCarDialog(existing = null)
            is CarsEvent.ShowEditDialog ->
                showCarDialog(existing = event.car)
            is CarsEvent.ShowDeleteConfirm ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.car_delete_title)
                    .setMessage(R.string.car_delete_message)
                    .setPositiveButton(R.string.car_delete_confirm) { _, _ ->
                        viewModel.confirmDelete(event.car.id)
                    }
                    .setNegativeButton(R.string.car_dialog_cancel, null)
                    .show()
            is CarsEvent.ShowError ->
                Snackbar.make(binding.root, event.messageRes, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * Pre-loads the make list off-thread (TASK-91), then opens the
     * dialog wired with the EV-database autocomplete. Defer the
     * dropdown content load to here rather than constructing it inside
     * the dialog so the dialog stays a pure object — no Hilt entry,
     * no ViewModel lookup inside a static `object`.
     *
     * v1.13.7 stopped swallowing `loadMakes()` failures behind
     * `runCatching {}.getOrDefault(emptyList())`. The silent fallback
     * masked the v1.13.0..v1.13.6 dropdown bug for six iterations: the
     * popup looked broken because matches were never produced, but the
     * actual failure was upstream in the data path. Now any exception
     * surfaces as a Snackbar with the message, and the dialog still
     * opens with an empty `makes` list so the user can still type
     * freely.
     */
    private fun showCarDialog(existing: org.spsl.evtracker.data.local.entity.CarEntity?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { viewModel.loadMakes() }
            val makes = result.getOrNull().orEmpty()
            result.exceptionOrNull()?.let { e ->
                android.util.Log.e(
                    "CarsFragment",
                    "loadMakes() failed: ${e::class.java.simpleName}: ${e.message}",
                    e,
                )
                Snackbar.make(
                    binding.root,
                    "EV makes failed to load: ${e.message ?: e::class.java.simpleName}",
                    Snackbar.LENGTH_LONG,
                ).show()
            }
            if (existing == null) {
                CarEditDialog.showAdd(
                    context = requireContext(),
                    makes = makes,
                    coroutineScope = viewLifecycleOwner.lifecycleScope,
                    modelsLoader = viewModel::loadModelsForMake,
                    onSubmit = viewModel::submitAdd,
                )
            } else {
                CarEditDialog.showEdit(
                    context = requireContext(),
                    car = existing,
                    makes = makes,
                    coroutineScope = viewLifecycleOwner.lifecycleScope,
                    modelsLoader = viewModel::loadModelsForMake,
                ) { form -> viewModel.submitEdit(existing.id, form) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
