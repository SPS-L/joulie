// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.databinding.DialogEditCarBinding

/**
 * Add / Edit Car form (TASK-91).
 *
 * Hosted in a `BottomSheetDialog` (v1.13.4 fix). Earlier iterations
 * used `MaterialAlertDialog`, but the AutoCompleteTextView popup
 * window does not render reliably inside MaterialAlertDialog on real
 * devices — neither Material's `MaterialAutoCompleteTextView`
 * (v1.13.0–v1.13.2) nor the stock `android.widget.AutoCompleteTextView`
 * (v1.13.3) showed the dropdown despite the adapter holding the full
 * 1,189-row dataset. BottomSheetDialog uses a different window layer
 * where popups render correctly.
 *
 * The Make and Model fields are stock `AutoCompleteTextView` instances
 * backed by a plain [ArrayAdapter] (default prefix-match `ArrayFilter`).
 * Tapping a field calls `showDropDown()` via the explicit
 * OnFocusChangeListener / OnClickListener wired below; typing filters
 * by prefix. Picking a model auto-fills the battery field and stashes
 * the WLTP figure onto [CarFormState] so the Add / Update use case
 * persists it. All fields remain manually editable — the autocomplete
 * is a convenience, not a lock.
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
     * Wire a freeform autocomplete on a plain [AutoCompleteTextView].
     *
     * Installs:
     *   - A stock [ArrayAdapter] with default prefix-match `ArrayFilter`
     *     so typing filters the dropdown by the typed prefix.
     *   - An [AutoCompleteTextView.setOnFocusChangeListener] that
     *     defers `showDropDown()` to the next frame (`view.post {…}`),
     *     so the view is fully attached + windowToken'd by the time
     *     the popup window is requested.
     *   - An [AutoCompleteTextView.setOnClickListener] that does the
     *     same, covering the case where the field already has focus
     *     and a tap is expected to re-open the popup.
     */
    private fun AutoCompleteTextView.bindAutocomplete(
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
        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) view.post { showDropDown() }
        }
        setOnClickListener { view ->
            view.post { showDropDown() }
        }
    }
}
