/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.ui.common

import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Fire a TalkBack announcement on each check transition of a
 * `MaterialButtonToggleGroup`, using `templateRes` (a format string with
 * one `%1$s` placeholder for the now-checked button's text).
 *
 * Solves the WCAG 4.1.3 "status messages" gap on toggle groups: by
 * default `MaterialButtonToggleGroup` only emits the system click sound
 * on selection change, leaving blind users without spoken feedback for
 * the new state. The helper does not interfere with application-logic
 * listeners — `addOnButtonCheckedListener` is additive.
 */
fun MaterialButtonToggleGroup.announceCheckedStateOnChange(
    @StringRes templateRes: Int,
) {
    addOnButtonCheckedListener { group, checkedId, isChecked ->
        if (!isChecked) return@addOnButtonCheckedListener
        val button = group.findViewById<MaterialButton>(checkedId) ?: return@addOnButtonCheckedListener
        group.announceForAccessibility(group.context.getString(templateRes, button.text))
    }
}
