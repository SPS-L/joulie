package org.spsl.evtracker.ui.chargeedit

import android.content.Context
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.spsl.evtracker.R

/**
 * Pure helper that builds the chip set: 3 fixed labels (Home/Work/Public),
 * the custom labels (top 5 from LocationReader), and a "+ Add" tail chip.
 */
object LocationChipBinder {

    fun bind(
        chipGroup: ChipGroup,
        custom: List<String>,
        onChipClick: (String) -> Unit,
        onAddClick: () -> Unit
    ) {
        chipGroup.removeAllViews()
        val ctx: Context = chipGroup.context
        val fixed = listOf(
            ctx.getString(R.string.location_home),
            ctx.getString(R.string.location_work),
            ctx.getString(R.string.location_public)
        )
        (fixed + custom).forEach { label ->
            val chip = newChip(ctx, label)
            chip.setOnClickListener { onChipClick(label) }
            chipGroup.addView(chip)
        }
        val addChip = newChip(ctx, ctx.getString(R.string.chip_add_location))
        addChip.setOnClickListener { onAddClick() }
        chipGroup.addView(addChip)
    }

    private fun newChip(ctx: Context, text: String): Chip {
        val chip = Chip(ctx)
        chip.text = text
        chip.isCheckable = false
        return chip
    }
}
