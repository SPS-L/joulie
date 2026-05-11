// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.dashboard

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.DateUtils
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.databinding.WidgetCarbonIntensityBinding

/**
 * Maps a [CarbonIntensityUiState] onto the pill's child views (TASK-82,
 * TASK-90 reason text).
 *
 * The renderer is intentionally view-shaped — it knows nothing about the
 * ViewModel or DataStore. Pass it the binding and a state and it tints,
 * texts, and (when applicable) wires the tap-to-retry handler.
 */
object CarbonIntensityRenderer {

    fun render(
        binding: WidgetCarbonIntensityBinding,
        state: CarbonIntensityUiState,
        nowMs: Long,
        onRetry: () -> Unit,
    ) {
        val context = binding.root.context
        val card = binding.widgetCarbonIntensityCard
        when (state) {
            CarbonIntensityUiState.Hidden -> {
                card.visibility = View.GONE
                card.isClickable = false
                card.isFocusable = false
            }
            CarbonIntensityUiState.Loading -> {
                card.visibility = View.VISIBLE
                card.isClickable = false
                card.isFocusable = false
                tint(card, R.color.md_theme_light_surfaceVariant)
                paintText(binding, R.color.md_theme_light_onSurfaceVariant)
                binding.widgetCarbonValue.text = context.getString(R.string.carbon_intensity_fetching)
                binding.widgetCarbonBucket.text = ""
                binding.widgetCarbonSubtitle.text = ""
                card.contentDescription = context.getString(R.string.carbon_intensity_a11y_loading)
                card.setOnClickListener(null)
            }
            is CarbonIntensityUiState.Ready -> {
                card.visibility = View.VISIBLE
                card.isClickable = false
                card.isFocusable = false
                tint(card, state.bucket.backgroundColorRes)
                paintText(binding, state.bucket.textColorRes)
                val valueText = "%.0f".format(state.intensityGCo2PerKwh)
                binding.widgetCarbonValue.text = context.getString(R.string.carbon_intensity_value, valueText)
                binding.widgetCarbonBucket.text = context.getString(state.bucket.labelRes)
                val relative = relativeTime(context, state.fetchedAtMs, nowMs)
                binding.widgetCarbonSubtitle.text = context.getString(R.string.carbon_intensity_updated_ago, relative)
                card.contentDescription = context.getString(
                    R.string.carbon_intensity_a11y_description,
                    valueText,
                    context.getString(state.bucket.labelRes),
                    relative,
                )
                card.setOnClickListener(null)
            }
            is CarbonIntensityUiState.Error -> {
                card.visibility = View.VISIBLE
                card.isClickable = true
                card.isFocusable = true
                tint(card, state.reason.backgroundColorRes)
                paintText(binding, state.reason.textColorRes)
                binding.widgetCarbonValue.text = context.getString(state.reason.labelRes)
                binding.widgetCarbonBucket.text = ""
                binding.widgetCarbonSubtitle.text = context.getString(R.string.carbon_intensity_tap_to_retry)
                card.contentDescription = context.getString(state.reason.a11yRes)
                card.setOnClickListener { onRetry() }
            }
        }
    }

    private fun tint(card: MaterialCardView, colorRes: Int) {
        card.setCardBackgroundColor(
            ColorStateList.valueOf(ContextCompat.getColor(card.context, colorRes)),
        )
    }

    private fun paintText(binding: WidgetCarbonIntensityBinding, colorRes: Int) {
        val color = ContextCompat.getColor(binding.root.context, colorRes)
        binding.widgetCarbonTitle.setTextColor(color)
        binding.widgetCarbonValue.setTextColor(color)
        binding.widgetCarbonBucket.setTextColor(color)
        binding.widgetCarbonSubtitle.setTextColor(color)
    }

    private fun relativeTime(context: Context, fetchedAtMs: Long, nowMs: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            fetchedAtMs,
            nowMs,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
}
