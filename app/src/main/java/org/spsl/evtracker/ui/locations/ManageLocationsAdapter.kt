// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.locations

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.R
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.databinding.ItemCustomLocationBinding

class ManageLocationsAdapter(
    private val nowProvider: () -> Long,
) : ListAdapter<CustomLocationEntity, ManageLocationsAdapter.VH>(DIFF) {

    inner class VH(val b: ItemCustomLocationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CustomLocationEntity) {
            b.textLabel.text = item.label
            val ctx = b.root.context
            val rel = DateUtils.getRelativeTimeSpanString(
                item.lastUsed,
                nowProvider(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
            b.textSubtitle.text = if (item.useCount == 0) {
                ctx.getString(R.string.manage_locations_row_count_zero, rel)
            } else {
                ctx.getString(R.string.manage_locations_row_count, item.useCount, rel)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCustomLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    /** Lookup label for a given adapter position (used by ItemTouchHelper.onSwiped). */
    fun labelAt(position: Int): String? = if (position in 0 until itemCount) getItem(position).label else null

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CustomLocationEntity>() {
            override fun areItemsTheSame(oldItem: CustomLocationEntity, newItem: CustomLocationEntity) =
                oldItem.label == newItem.label
            override fun areContentsTheSame(oldItem: CustomLocationEntity, newItem: CustomLocationEntity) =
                oldItem == newItem
        }
    }
}
