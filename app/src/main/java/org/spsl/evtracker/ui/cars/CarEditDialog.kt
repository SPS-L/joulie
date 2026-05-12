// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.cars

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarFormState
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.evdb.EvModel
import org.spsl.evtracker.databinding.DialogEditCarBinding

/**
 * Add / Edit Car form (TASK-91).
 *
 * Renders suggestions as TextView rows inside a [LinearLayout] anchored
 * directly under each input field (v1.13.6 fix). v1.13.0..v1.13.5
 * cycled through every (widget × dialog host) combination of
 * `AutoCompleteTextView` / `MaterialAutoCompleteTextView` inside
 * `MaterialAlertDialog` / `BottomSheetDialog`; the autocomplete popup
 * window never rendered on the user's Pixel 6a in any combination,
 * even though the same widget renders correctly in
 * `fragment_wizard_page3.xml` (currency picker, hosted in a Fragment
 * rather than a dialog). Rather than chase another popup-window
 * variable, this revision removes the popup entirely: matches are
 * rendered as plain `TextView` children in the same scrollable
 * bottom-sheet content, so visibility is independent of dialog,
 * window-layer, IME, and PopupWindow behaviour.
 *
 * UX:
 *   - Typing into Make / Model filters its source list by case-insensitive
 *     prefix, up to 8 matches at a time.
 *   - Tapping a suggestion fills the field with the canonical value and
 *     hides the list.
 *   - Picking a Make reloads the Model suggestions and clears any stale
 *     model + WLTP. Picking a Model auto-fills Battery + Year and
 *     stashes WLTP onto [CarFormState].
 *   - All fields stay manually editable (free-typing a make outside
 *     the dataset is supported).
 *
 * Display format for the model suggestion is `"$model · $variant"`
 * when the variant is non-blank (Brand Guide §1 voice rule, no em-dash).
 * The underlying EditText receives the canonical `model` string only,
 * so the form submission stays clean.
 */
object CarEditDialog {

    private const val MAX_SUGGESTIONS = 8

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

        // Filtered model rows for the currently-picked make. Updated
        // whenever the user picks a new make (or eagerly on Edit when
        // the existing make matches a known make).
        var modelsForCurrentMake: List<EvModel> = emptyList()

        // wltpKwhPer100km is set when the user picks a model from the
        // suggestion list. On Edit the existing car's WLTP carries
        // through when the user doesn't touch the model field.
        var stashedWltp: Double? = existing?.wltpKwhPer100km

        existing?.let {
            binding.carDialogName.setText(it.name)
            binding.carDialogMake.setText(it.make)
            binding.carDialogModel.setText(it.model)
            binding.carDialogYear.setText(it.year?.toString().orEmpty())
            binding.carDialogBattery.setText(it.batteryKwh?.toString().orEmpty())
        }

        binding.carDialogMake.bindInlineSuggestions(
            container = binding.carDialogMakeSuggestions,
            itemsProvider = { makes },
            labelOf = { it },
        ) { picked ->
            // Reload model list for the new make. Run on the lifecycle
            // scope so a teardown mid-load cancels cleanly. Clear any
            // stale model + WLTP so the user sees the empty hint
            // instead of a value that doesn't belong to the new make.
            binding.carDialogModel.setText("")
            stashedWltp = null
            coroutineScope.launch {
                modelsForCurrentMake = modelsLoader(picked)
            }
        }

        binding.carDialogModel.bindInlineSuggestions(
            container = binding.carDialogModelSuggestions,
            itemsProvider = { modelsForCurrentMake },
            labelOf = { it.displayLabel() },
        ) { picked ->
            // Persist the canonical model name (not the "Model · Variant"
            // display string) into the underlying EditText so the form
            // submission stays clean.
            binding.carDialogModel.setText(picked.model)
            binding.carDialogModel.setSelection(picked.model.length)
            // Auto-fill battery + stash WLTP. The user can still
            // overwrite battery manually after this fires.
            binding.carDialogBattery.setText(picked.batteryKwh.toString())
            picked.year?.let { binding.carDialogYear.setText(it.toString()) }
            stashedWltp = picked.wltpKwhPer100km
        }

        // Eagerly populate the model list when opening Edit on a car
        // whose make already matches a known make in the dataset, so
        // typing in the model field surfaces suggestions immediately.
        existing?.let { car ->
            if (car.make.isNotBlank()) {
                coroutineScope.launch {
                    modelsForCurrentMake = modelsLoader(car.make)
                }
            }
        }

        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)
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

    private fun EvModel.displayLabel(): String =
        if (variant.isBlank()) model else "$model · $variant"

    /**
     * Wire an in-document suggestion list against a [TextInputEditText].
     *
     * As the user types, [container] is populated with up to
     * [MAX_SUGGESTIONS] TextView rows whose label is produced by
     * [labelOf] from each match. Picking a row sets [onPick] with the
     * canonical item value; the caller is responsible for updating the
     * EditText text (so the model picker can write the bare model name
     * rather than the "Model · Variant" display string).
     *
     * `userTyping` guards against the auto-recursion that would
     * otherwise fire when [onPick] writes back into the EditText: the
     * watcher re-enters with the canonical value, the suggestion list
     * matches itself, and the popup never closes.
     */
    private fun <T> TextInputEditText.bindInlineSuggestions(
        container: LinearLayout,
        itemsProvider: () -> List<T>,
        labelOf: (T) -> String,
        onPick: (T) -> Unit,
    ) {
        var userTyping = true
        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (!userTyping) return
                    val query = s?.toString().orEmpty().trim()
                    if (query.isEmpty()) {
                        container.hideSuggestions()
                        return
                    }
                    val matches = itemsProvider()
                        .filter { labelOf(it).startsWith(query, ignoreCase = true) }
                        .take(MAX_SUGGESTIONS)
                    if (matches.isEmpty()) {
                        container.hideSuggestions()
                        return
                    }
                    container.renderSuggestions(matches.map(labelOf)) { index ->
                        userTyping = false
                        onPick(matches[index])
                        container.hideSuggestions()
                        userTyping = true
                    }
                }
            },
        )
        // If the user re-focuses the field after picking, surface the
        // current suggestions again. Tap on the field itself (already
        // focused) is also covered by the text watcher firing on the
        // next keystroke, so no explicit click handler is needed.
        setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) container.hideSuggestions()
        }
    }

    private fun LinearLayout.hideSuggestions() {
        if (isVisible) {
            removeAllViews()
            visibility = View.GONE
        }
    }

    private fun LinearLayout.renderSuggestions(
        labels: List<String>,
        onClickIndex: (Int) -> Unit,
    ) {
        removeAllViews()
        labels.forEachIndexed { index, label ->
            val row = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(resolveColorAttr(android.R.attr.textColorPrimary))
                val padH = dp(16)
                val padV = dp(14)
                setPadding(padH, padV, padH, padV)
                background = ResourcesCompat.getDrawable(
                    resources,
                    resolveAttrResource(android.R.attr.selectableItemBackground),
                    context.theme,
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClickIndex(index) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            addView(row)
        }
        visibility = View.VISIBLE
    }

    private fun View.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun View.resolveAttrResource(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return tv.resourceId
    }

    private fun View.resolveColorAttr(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) {
            ResourcesCompat.getColor(resources, tv.resourceId, context.theme)
        } else {
            tv.data
        }
    }
}
