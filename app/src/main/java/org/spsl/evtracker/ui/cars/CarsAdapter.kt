package org.spsl.evtracker.ui.cars

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarsUiState.CarRow
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.databinding.ItemCarBinding

class CarsAdapter(
    private val onSetActive: (Long) -> Unit,
    private val onEdit: (CarEntity) -> Unit,
    private val onDelete: (CarEntity) -> Unit,
) : ListAdapter<CarRow, CarsAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: CarRow) {
            binding.itemCarName.text = row.car.name
            val sub = listOfNotNull(
                row.car.make.takeIf { it.isNotBlank() },
                row.car.model.takeIf { it.isNotBlank() },
                row.car.year?.toString(),
            ).joinToString(" ")
            binding.itemCarSubtitle.text = sub
            binding.itemCarSubtitle.isVisible = sub.isNotEmpty()
            binding.itemCarActiveChip.isVisible = row.isActive
            binding.itemCarOverflow.setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                popup.inflate(R.menu.car_row_overflow)
                popup.menu.findItem(R.id.car_overflow_set_active).isVisible = !row.isActive
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.car_overflow_set_active -> {
                            onSetActive(row.car.id)
                            true
                        }
                        R.id.car_overflow_edit -> {
                            onEdit(row.car)
                            true
                        }
                        R.id.car_overflow_delete -> {
                            onDelete(row.car)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CarRow>() {
        override fun areItemsTheSame(oldItem: CarRow, newItem: CarRow) = oldItem.car.id == newItem.car.id
        override fun areContentsTheSame(oldItem: CarRow, newItem: CarRow) = oldItem == newItem
    }
}
