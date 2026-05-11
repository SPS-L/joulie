// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.core.model.ChargeKwhSource
import org.spsl.evtracker.core.model.HistoryRow
import org.spsl.evtracker.databinding.ItemChargeEventBinding
import org.spsl.evtracker.ui.common.DateFormat
import org.spsl.evtracker.ui.common.MoneyFormat

class HistoryAdapter(
    private val onRowClick: (Long) -> Unit,
) : ListAdapter<HistoryRow, HistoryAdapter.ViewHolder>(Diff) {

    private var distanceUnit: String = "km"
    private var co2Enabled: Boolean = false

    fun setDistanceUnit(unit: String) {
        if (distanceUnit != unit) {
            distanceUnit = unit
            notifyDataSetChanged()
        }
    }

    /** TASK-82: drives the per-row "⚡ X kg CO₂ · Y g/kWh" line. */
    fun setCo2Enabled(enabled: Boolean) {
        if (co2Enabled != enabled) {
            co2Enabled = enabled
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChargeEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), distanceUnit, co2Enabled, onRowClick)

    inner class ViewHolder(private val binding: ItemChargeEventBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: HistoryRow, unit: String, co2Enabled: Boolean, onRowClick: (Long) -> Unit) {
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
            // surface DERIVED_FROM_SOC events with a small "Est."
            // badge so the user can spot which rows came from the in-form
            // SoC calculator and were excluded from degradation tracking.
            binding.itemChargeEstimatedBadge.isVisible =
                row.event.kwhSource == ChargeKwhSource.DERIVED_FROM_SOC
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
            // TASK-82 CO₂ line: gated on co2Enabled AND a captured live
            // intensity. Per-row kg = kwh × intensity / 1000; inline so we
            // don't drag a use case in for one multiplication.
            val intensity = row.event.gridIntensityGCo2PerKwh
            if (co2Enabled && intensity != null && row.event.kwhAdded > 0.0) {
                val kgText = "%.2f".format(row.event.kwhAdded * intensity / 1000.0)
                val gText = "%.0f".format(intensity)
                binding.itemChargeCo2.text = binding.root.context.getString(
                    org.spsl.evtracker.R.string.history_event_co2_line,
                    kgText,
                    gText,
                )
                binding.itemChargeCo2.isVisible = true
            } else {
                binding.itemChargeCo2.isVisible = false
            }
            binding.root.setOnClickListener { onRowClick(row.event.id) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<HistoryRow>() {
        override fun areItemsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem.event.id == newItem.event.id
        override fun areContentsTheSame(oldItem: HistoryRow, newItem: HistoryRow) = oldItem == newItem
    }
}
