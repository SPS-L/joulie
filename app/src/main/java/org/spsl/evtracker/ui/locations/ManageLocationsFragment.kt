package org.spsl.evtracker.ui.locations

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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ManageLocationsEvent
import org.spsl.evtracker.databinding.FragmentManageLocationsBinding

@AndroidEntryPoint
class ManageLocationsFragment : Fragment() {

    private val vm: ManageLocationsViewModel by viewModels()
    private var _binding: FragmentManageLocationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ManageLocationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentManageLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ManageLocationsAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL),
        )

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                adapter.labelAt(vh.adapterPosition)?.let { vm.onSwipeDelete(it) }
            }
        }).attachToRecyclerView(binding.recycler)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.uiState.collect { state ->
                        adapter.submitList(state.visibleLocations)
                        binding.emptyState.isVisible = state.visibleLocations.isEmpty()
                        binding.recycler.isVisible = state.visibleLocations.isNotEmpty()
                    }
                }
                launch {
                    vm.events.collect { event ->
                        when (event) {
                            is ManageLocationsEvent.ShowUndoSnackbar ->
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.manage_locations_undo_snackbar, event.label),
                                    Snackbar.LENGTH_LONG,
                                )
                                    .setAction(R.string.common_undo) { vm.onUndoDelete(event.label) }
                                    .show()
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
