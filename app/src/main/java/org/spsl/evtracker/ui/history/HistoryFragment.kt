package org.spsl.evtracker.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeTypeFilter
import org.spsl.evtracker.core.model.HistoryEvent
import org.spsl.evtracker.databinding.FragmentHistoryBinding

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private val viewModel: HistoryViewModel by viewModels()

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = HistoryAdapter(onRowClick = { viewModel.onRowClick(it) })
        binding.historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecycler.adapter = adapter

        val touchHelper = ItemTouchHelper(
            SwipeToDeleteCallback(
                onSwipe = { viewModel.onSwipeDelete(it) },
                rowAt = { pos -> adapter.currentList.getOrNull(pos)?.event },
            ),
        )
        touchHelper.attachToRecyclerView(binding.historyRecycler)

        binding.historyChipAll.setOnClickListener { viewModel.setFilter(ChargeTypeFilter.ALL) }
        binding.historyChipAc.setOnClickListener { viewModel.setFilter(ChargeTypeFilter.AC) }
        binding.historyChipDc.setOnClickListener { viewModel.setFilter(ChargeTypeFilter.DC) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.setDistanceUnit(state.distanceUnit)
                    adapter.submitList(state.rows)
                    val visibleEmpty = state.isEmpty && state.activeCarId != -1
                    binding.historyEmpty.isVisible = visibleEmpty
                    binding.historyRecycler.isVisible = !visibleEmpty
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is HistoryEvent.NavigateToEdit -> {
                            findNavController().navigate(
                                R.id.action_history_to_chargeEdit,
                                bundleOf("eventId" to event.eventId),
                            )
                        }
                        is HistoryEvent.ShowUndoSnackbar -> {
                            Snackbar.make(binding.root, R.string.snackbar_charge_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo) { viewModel.onUndoDelete(event.eventId) }
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
