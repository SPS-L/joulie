// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.history

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

class SwipeToDeleteCallback(
    private val onSwipe: (ChargeEventEntity) -> Unit,
    private val rowAt: (Int) -> ChargeEventEntity?,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        @Suppress("DEPRECATION")
        val event = rowAt(viewHolder.adapterPosition) ?: return
        onSwipe(event)
    }
}
