package org.spsl.evtracker.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.core.model.HistoryRow
import org.spsl.evtracker.databinding.ItemChargeEventBinding
import org.spsl.evtracker.ui.common.DateFormat
import org.spsl.evtracker.ui.common.MoneyFormat

class HistoryAdapter(
    private val onRowClick: (Int) -> Unit,
) : ListAdapter<HistoryRow, HistoryAdapter.ViewHolder>(Diff) {

    private var distanceUnit: String = "km"

    fun setDistanceUnit(unit: String) {
        if (distanceUnit != unit) {
            distanceUnit = unit
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChargeEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), distanceUnit, onRowClick)

    inner class ViewHolder(private val binding: ItemChargeEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HistoryRow, unit: String, onRowClick: (Int) -> Unit) {
            // Hide pending-delete rows entirely; the data is still present so Undo can find it.
            if (row.isPendingDelete) {
                binding.root.visibility = android.view.View.GONE
                binding.root.layoutParams = binding.root.layoutParams.apply {
                    height = 0
                    width = 0
                }
                return
            }
            binding.root.visibility = android.view.View.VISIBLE
            binding.root.layoutParams = binding.root.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            binding.itemChargeDate.text = DateFormat.formatEpochMs(row.event.eventDate)
            binding.itemChargeTypeBadge.text = row.event.chargeType.displayLabel()
            val unitSuffix = if (unit == "miles") "mi" else "km"
            binding.itemChargeSummary.text = "%.1f %s · %.2f kWh".format(row.displayOdometer, unitSuffix, row.event.kwhAdded)
            binding.itemChargeLocation.isVisible = !row.event.location.isNullOrBlank()
            binding.itemChargeLocation.text = row.event.location.orEmpty()
            binding.itemChargeCost.isVisible = row.showCost
            if (row.showCost) {
                binding.itemChargeCost.text = MoneyFormat.format(row.event.costTotal!!, row.event.currency!!)
            }
            binding.itemChargeNote.isVisible = row.event.note.isNotBlank()
            binding.itemChargeNote.text = row.event.note
            binding.root.setOnClickListener { onRowClick(row.event.id) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem.event.id == newItem.event.id
        override fun areContentsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem == newItem
    }
}
