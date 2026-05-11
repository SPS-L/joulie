// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.spsl.evtracker.HiltTestActivity
import org.spsl.evtracker.core.model.CarbonIntensityBucket
import org.spsl.evtracker.core.model.CarbonIntensityUiState
import org.spsl.evtracker.databinding.WidgetCarbonIntensityBinding
import org.spsl.evtracker.screenshots.fixtures.CarbonIntensityFixtures
import org.spsl.evtracker.ui.dashboard.CarbonIntensityRenderer

/**
 * Roborazzi screenshot baselines for the dashboard carbon-intensity pill
 * (TASK-86, forward-work from TASK-82 §8.2). 12 PNGs total:
 *
 *  - Five bucket [CarbonIntensityUiState.Ready] states (VERY_LOW → VERY_HIGH)
 *    captured in light + dark themes.
 *  - [CarbonIntensityUiState.Loading] captured once. The renderer paints
 *    Loading with fixed surface-variant colours so both DayNight themes
 *    produce visually-identical output.
 *  - [CarbonIntensityUiState.Error] captured once for the same reason.
 *
 * Each test:
 *  1. Sets the AppCompat night mode BEFORE activity creation so theme
 *     resolution happens with the right DayNight branch.
 *  2. Boots a [HiltTestActivity] (themed `Theme.EVTracker`).
 *  3. Inflates [WidgetCarbonIntensityBinding] inside the activity so the
 *     widget sees the activity context — that's what gives Material 3
 *     surfaces the right resolved colours during attribute lookup.
 *  4. Runs [CarbonIntensityRenderer.render] with a fixture state + a fixed
 *     `nowMs` so `DateUtils.getRelativeTimeSpanString` returns a stable
 *     "8 min. ago" subtitle.
 *  5. Drains the looper and captures the bound root view.
 *
 * Baselines land under `app/src/test/screenshots/dashboard_carbon_*.png`
 * via the `roborazzi { outputDir = ... }` block in `app/build.gradle.kts`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarbonIntensityPillScreenshotTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun veryLow_light() = captureReady(CarbonIntensityBucket.VERY_LOW, ThemeMode.LIGHT)

    @Test
    fun veryLow_dark() = captureReady(CarbonIntensityBucket.VERY_LOW, ThemeMode.DARK)

    @Test
    fun low_light() = captureReady(CarbonIntensityBucket.LOW, ThemeMode.LIGHT)

    @Test
    fun low_dark() = captureReady(CarbonIntensityBucket.LOW, ThemeMode.DARK)

    @Test
    fun moderate_light() = captureReady(CarbonIntensityBucket.MODERATE, ThemeMode.LIGHT)

    @Test
    fun moderate_dark() = captureReady(CarbonIntensityBucket.MODERATE, ThemeMode.DARK)

    @Test
    fun high_light() = captureReady(CarbonIntensityBucket.HIGH, ThemeMode.LIGHT)

    @Test
    fun high_dark() = captureReady(CarbonIntensityBucket.HIGH, ThemeMode.DARK)

    @Test
    fun veryHigh_light() = captureReady(CarbonIntensityBucket.VERY_HIGH, ThemeMode.LIGHT)

    @Test
    fun veryHigh_dark() = captureReady(CarbonIntensityBucket.VERY_HIGH, ThemeMode.DARK)

    @Test
    fun loading() = capture("loading", ThemeMode.LIGHT, CarbonIntensityUiState.Loading)

    @Test
    fun error() = capture("error", ThemeMode.LIGHT, CarbonIntensityUiState.Error)

    private fun captureReady(bucket: CarbonIntensityBucket, theme: ThemeMode) {
        capture(
            name = "${bucket.fileSuffix}_${theme.suffix}",
            theme = theme,
            state = CarbonIntensityFixtures.ready(bucket),
        )
    }

    private fun capture(name: String, theme: ThemeMode, state: CarbonIntensityUiState) {
        AppCompatDelegate.setDefaultNightMode(
            if (theme == ThemeMode.DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            },
        )
        val controller = Robolectric.buildActivity(HiltTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            val binding = WidgetCarbonIntensityBinding.inflate(activity.layoutInflater)
            activity.setContentView(
                binding.root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            CarbonIntensityRenderer.render(
                binding = binding,
                state = state,
                nowMs = CarbonIntensityFixtures.NOW_MS,
                onRetry = {},
            )
            ShadowLooper.idleMainLooper()
            binding.root.captureRoboImage("src/test/screenshots/dashboard_carbon_$name.png")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private val CarbonIntensityBucket.fileSuffix: String
        get() = when (this) {
            CarbonIntensityBucket.VERY_LOW -> "very_low"
            CarbonIntensityBucket.LOW -> "low"
            CarbonIntensityBucket.MODERATE -> "moderate"
            CarbonIntensityBucket.HIGH -> "high"
            CarbonIntensityBucket.VERY_HIGH -> "very_high"
        }
}
