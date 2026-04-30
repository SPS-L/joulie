package org.spsl.evtracker.ui.cars

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.databinding.DialogEditCarBinding

object CarEditDialog {

    fun showAdd(context: Context, onSubmit: (CarFormState) -> Unit) {
        showInternal(context, existing = null, titleRes = R.string.car_dialog_add_title, onSubmit = onSubmit)
    }

    fun showEdit(context: Context, car: CarEntity, onSubmit: (CarFormState) -> Unit) {
        showInternal(context, existing = car, titleRes = R.string.car_dialog_edit_title, onSubmit = onSubmit)
    }

    private fun showInternal(
        context: Context,
        existing: CarEntity?,
        titleRes: Int,
        onSubmit: (CarFormState) -> Unit,
    ) {
        val binding = DialogEditCarBinding.inflate(LayoutInflater.from(context))
        existing?.let {
            binding.carDialogName.setText(it.name)
            binding.carDialogMake.setText(it.make)
            binding.carDialogModel.setText(it.model)
            binding.carDialogYear.setText(it.year?.toString().orEmpty())
            binding.carDialogBattery.setText(it.batteryKwh?.toString().orEmpty())
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(binding.root)
            .setPositiveButton(R.string.car_dialog_save) { _, _ ->
                onSubmit(
                    CarFormState(
                        name = binding.carDialogName.text?.toString().orEmpty(),
                        make = binding.carDialogMake.text?.toString().orEmpty(),
                        model = binding.carDialogModel.text?.toString().orEmpty(),
                        year = binding.carDialogYear.text?.toString().orEmpty(),
                        batteryKwh = binding.carDialogBattery.text?.toString().orEmpty(),
                    ),
                )
            }
            .setNegativeButton(R.string.car_dialog_cancel, null)
            .show()
    }
}
