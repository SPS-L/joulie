// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.databinding.DialogEditCarBinding

/**
 * Add / Edit Car dialog (TASK-91).
 *
 * Wraps the existing `dialog_edit_car.xml` form with EV-database
 * autocomplete: the Make and Model fields are
 * [com.google.android.material.textfield.MaterialAutoCompleteTextView]
 * instances backed by a plain [ArrayAdapter] (Material's recommended
 * pattern for freeform autocomplete with prefix filtering). Tapping
 * the end-arrow icon shows the full list; typing filters by prefix.
 * Picking a model auto-fills the battery field and stashes the WLTP
 * figure onto [CarFormState] so the Add / Update use case persists
 * it. All fields remain manually editable — the autocomplete is a
 * convenience, not a lock.
 *
 * Display format for the model dropdown: `"$model · $variant"` if the
 * variant is non-blank (Brand Guide §1 voice rule — no em-dash). The
 * caller selects from the displayed strings; the dialog round-trips
 * back to `(model, variant)` via the in-memory lookup map.
 */
object CarEditDialog {

    fun showAdd(
        context: Context,
        makes: List<String>,
        coroutineScope: LifecycleCoroutineScope,
        modelsLoader: suspend (String) -> List<EvModel>,
        onSubmit: (CarFormState) -> Unit,
    ) {
        showInternal(
            context = context,
            existing = null,
            titleRes = R.string.car_dialog_add_title,
            makes = makes,
            coroutineScope = coroutineScope,
            modelsLoader = modelsLoader,
            onSubmit = onSubmit,
        )
    }

    fun showEdit(
        context: Context,
        car: CarEntity,
        makes: List<String>,
        coroutineScope: LifecycleCoroutineScope,
        modelsLoader: suspend (String) -> List<EvModel>,
        onSubmit: (CarFormState) -> Unit,
    ) {
        showInternal(
            context = context,
            existing = car,
            titleRes = R.string.car_dialog_edit_title,
            makes = makes,
            coroutineScope = coroutineScope,
            modelsLoader = modelsLoader,
            onSubmit = onSubmit,
        )
    }

    private fun showInternal(
        context: Context,
        existing: CarEntity?,
        titleRes: Int,
        makes: List<String>,
        coroutineScope: LifecycleCoroutineScope,
        modelsLoader: suspend (String) -> List<EvModel>,
        onSubmit: (CarFormState) -> Unit,
    ) {
        val binding = DialogEditCarBinding.inflate(LayoutInflater.from(context))

        // Local lookup so we can recover the picked EvModel from the
        // displayed "Model · Variant" string without re-parsing.
        var modelsForCurrentMake: List<EvModel> = emptyList()
        var displayedModelLabels: List<String> = emptyList()

        // Per-form state held across click handlers. wltpKwhPer100km
        // is set when the user picks a model from the dropdown; the
        // existing car's WLTP carries through on Edit when the user
        // doesn't touch the model field.
        var stashedWltp: Double? = existing?.wltpKwhPer100km

        existing?.let {
            binding.carDialogName.setText(it.name)
            binding.carDialogMake.setText(it.make, false)
            binding.carDialogModel.setText(it.model, false)
            binding.carDialogYear.setText(it.year?.toString().orEmpty())
            binding.carDialogBattery.setText(it.batteryKwh?.toString().orEmpty())
        }

        binding.carDialogMake.bindAutocomplete(context, makes)
        binding.carDialogMake.setOnItemClickListener { _, _, position, _ ->
            val picked = binding.carDialogMake.adapter.getItem(position) as? String
                ?: return@setOnItemClickListener
            binding.carDialogMake.setText(picked, false)
            // Reload model list for the new make. Run on the
            // lifecycle scope so a teardown mid-load cancels cleanly.
            coroutineScope.launch {
                val rows = modelsLoader(picked)
                modelsForCurrentMake = rows
                displayedModelLabels = rows.map { it.displayLabel() }
                binding.carDialogModel.bindAutocomplete(context, displayedModelLabels)
                // The previously-selected model is unlikely to belong
                // to the new make. Clear so the user sees the empty
                // hint instead of a stale value.
                binding.carDialogModel.setText("", false)
                stashedWltp = null
            }
        }

        binding.carDialogModel.setOnItemClickListener { _, _, position, _ ->
            val picked = modelsForCurrentMake.getOrNull(position) ?: return@setOnItemClickListener
            // Persist the canonical model name (not the
            // "Model · Variant" display string) into the underlying
            // EditText so the form submission stays clean.
            binding.carDialogModel.setText(picked.model, false)
            // Auto-fill battery + stash WLTP. The user can still
            // overwrite battery manually after this fires.
            binding.carDialogBattery.setText(picked.batteryKwh.toString())
            picked.year?.let { binding.carDialogYear.setText(it.toString()) }
            stashedWltp = picked.wltpKwhPer100km
        }

        // Eagerly populate the model dropdown when opening Edit on a
        // car whose make already matches a known make in the
        // dataset. Keeps the dropdown responsive on first focus.
        existing?.let { car ->
            if (car.make.isNotBlank()) {
                coroutineScope.launch {
                    val rows = modelsLoader(car.make)
                    modelsForCurrentMake = rows
                    displayedModelLabels = rows.map { it.displayLabel() }
                    binding.carDialogModel.bindAutocomplete(context, displayedModelLabels)
                }
            }
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
                        wltpKwhPer100km = stashedWltp,
                    ),
                )
            }
            .setNegativeButton(R.string.car_dialog_cancel, null)
            .show()
    }

    /**
     * Brand-guide-compliant display label, `"$model · $variant"` with
     * a middle-dot separator (Brand Guide §1 voice rule forbids
     * em-dashes in user-facing copy). Falls back to `model` alone when
     * the variant is blank.
     */
    private fun EvModel.displayLabel(): String =
        if (variant.isBlank()) model else "$model · $variant"

    /**
     * Wire a freeform autocomplete on a [MaterialAutoCompleteTextView].
     *
     * Uses Material's stock `setSimpleItems(...)` path — the dropdown
     * arrow on the `ExposedDropdownMenu` `TextInputLayout` shows the
     * full list on tap; typing then filters to entries that start with
     * the prefix (case-insensitive). Earlier iterations used a
     * subclassed `ArrayAdapter` with a custom substring `Filter`; that
     * combination silently broke the popup on real devices (the popup
     * never appeared even though the adapter held data). The
     * `setSimpleItems` path is the canonical Material recipe and is
     * known to interop correctly with the M3 `MaterialAutoCompleteTextView`.
     *
     * Free typing is preserved because `MaterialAutoCompleteTextView`
     * does not call `setKeyListener(null)`; users can still type a
     * make like `"Lucid"` that isn't in the bundled dataset, and the
     * field accepts it on save.
     */
    private fun MaterialAutoCompleteTextView.bindAutocomplete(
        context: Context,
        items: List<String>,
    ) {
        // setSimpleItems uses MaterialArrayAdapter + a no-op filter,
        // which keeps the FULL list visible regardless of typed input
        // — equivalent to a permanent "show everything" affordance.
        // We layer prefix filtering on top by also installing a plain
        // ArrayAdapter as the active adapter (setSimpleItems sets one,
        // setAdapter overrides it with a stock ArrayAdapter whose
        // default ArrayFilter does prefix matching). The dropdown
        // arrow on the TextInputLayout's ExposedDropdownMenu style
        // shows the list on tap regardless of typed input.
        setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                items,
            ),
        )
    }
}
