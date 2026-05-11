// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.databinding.DialogEditCarBinding

/**
 * Add / Edit Car form (TASK-91).
 *
 * Layout: [MaterialAutoCompleteTextView] inside `TextInputLayout` with
 * the `Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu`
 * style — the canonical Material 3 autocomplete pattern, same as the
 * working wizard currency picker (`fragment_wizard_page3.xml`).
 *
 * Host: [BottomSheetDialog] (v1.13.4 layer change kept). The popup
 * window does not render reliably inside `MaterialAlertDialog` on real
 * devices.
 *
 * Fix history:
 *   - v1.13.0–v1.13.2: Material widget in `MaterialAlertDialog` →
 *     popup clipped by the alert-dialog window layer.
 *   - v1.13.3–v1.13.4: switched to plain `android.widget.AutoCompleteTextView`
 *     to avoid Material's popup heuristics, then moved the host to
 *     `BottomSheetDialog`. The popup still did not appear because
 *     `TextInputLayout`'s box / floating-label integration is only
 *     defined for `TextInputEditText` and `MaterialAutoCompleteTextView`
 *     — a plain `AutoCompleteTextView` child made the label position
 *     and popup anchor unreliable.
 *   - v1.13.5 (this revision): the canonical Material pattern inside
 *     `BottomSheetDialog`. This combination was not tried in any
 *     earlier attempt.
 *
 * Adapter: plain [ArrayAdapter] (default prefix-match `ArrayFilter`),
 * which is the supported interop point with `MaterialAutoCompleteTextView`.
 * The custom substring `Filter` used in v1.13.0 interacted badly with
 * Material's internal popup-show heuristic (see v1.13.2 commit).
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
        binding.carDialogTitle.setText(titleRes)

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

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)
        // Start fully expanded so the user sees all form fields
        // immediately, rather than a small peek the user has to drag
        // up. EXPANDED state also gives the AutoCompleteTextView
        // popup the full vertical space below the field.
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        binding.carDialogCancel.setOnClickListener { dialog.dismiss() }
        binding.carDialogSave.setOnClickListener {
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
            dialog.dismiss()
        }

        dialog.show()
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
     * Installs a plain [ArrayAdapter] with the default prefix-match
     * `ArrayFilter` so typing filters the dropdown by the typed prefix.
     *
     * Tap-to-show is handled natively by the `ExposedDropdownMenu`
     * style's trailing arrow icon — no explicit `OnClickListener` /
     * `OnFocusChangeListener` workaround is needed (and earlier
     * workarounds were the symptom-fix half of the v1.13.3 bug, not
     * the root cause).
     */
    private fun MaterialAutoCompleteTextView.bindAutocomplete(
        context: Context,
        items: List<String>,
    ) {
        setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                items,
            ),
        )
    }
}
