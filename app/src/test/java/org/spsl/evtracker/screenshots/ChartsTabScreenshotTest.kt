// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots

import android.view.View
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.spsl.evtracker.core.model.ChartsEvent
import org.spsl.evtracker.core.model.ChartsScreenState
import org.spsl.evtracker.screenshots.fixtures.ChartsFixtures
import org.spsl.evtracker.ui.charts.ChartsTabFragment
import org.spsl.evtracker.ui.charts.ChartsTabFragment.TabKind
import org.spsl.evtracker.ui.charts.ChartsViewModel

/**
 * Roborazzi screenshot baselines for the seven [ChartsTabFragment] tabs
 * across light + dark themes (TASK-79). 14 PNGs total.
 *
 * Each test:
 *   1. Builds the canonical [ChartsScreenState] from [ChartsFixtures].
 *   2. Wraps it in a Mockito-mocked [ChartsViewModel] (final class
 *      mocking works under Mockito 5's inline mock maker, which is
 *      the default in mockito-core 5.x).
 *   3. Hosts a [ChartsTabFragment] of the requested kind under a
 *      [FakeChartsParentFragment] that exposes the mocked VM via
 *      `defaultViewModelProviderFactory`. The tab fragment's
 *      `viewModels({ requireParentFragment() })` resolves to the mock.
 *   4. Idles the main looper past MPAndroidChart's `animateY(400)`
 *      so the captured frame is the chart's final state.
 *   5. Captures the rendered View via Roborazzi.
 *
 * Baselines committed under `app/src/test/screenshots/` per the
 * `roborazzi { outputDir = ... }` block in `app/build.gradle.kts`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartsTabScreenshotTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        // Build the Hilt graph BEFORE the activity is created so
        // HiltTestActivity.onCreate's inject() finds a SingletonComponent.
        hiltRule.inject()
    }

    @Test fun trend_light() = capture(TabKind.TREND, ThemeMode.LIGHT)

    @Test fun trend_dark() = capture(TabKind.TREND, ThemeMode.DARK)

    @Test fun monthlyKwh_light() = capture(TabKind.MONTHLY_KWH, ThemeMode.LIGHT)

    @Test fun monthlyKwh_dark() = capture(TabKind.MONTHLY_KWH, ThemeMode.DARK)

    @Test fun monthlyCost_light() = capture(TabKind.MONTHLY_COST, ThemeMode.LIGHT)

    @Test fun monthlyCost_dark() = capture(TabKind.MONTHLY_COST, ThemeMode.DARK)

    @Test fun acDc_light() = capture(TabKind.AC_DC, ThemeMode.LIGHT)

    @Test fun acDc_dark() = capture(TabKind.AC_DC, ThemeMode.DARK)

    @Test fun locations_light() = capture(TabKind.LOCATIONS, ThemeMode.LIGHT)

    @Test fun locations_dark() = capture(TabKind.LOCATIONS, ThemeMode.DARK)

    @Test fun degradation_light() = capture(TabKind.DEGRADATION, ThemeMode.LIGHT)

    @Test fun degradation_dark() = capture(TabKind.DEGRADATION, ThemeMode.DARK)

    @Test fun co2_light() = capture(TabKind.CO2, ThemeMode.LIGHT)

    @Test fun co2_dark() = capture(TabKind.CO2, ThemeMode.DARK)

    private fun capture(kind: TabKind, theme: ThemeMode) {
        val state = ChartsFixtures.canonical()
        val mockedVm: ChartsViewModel = mock {
            on { uiState } doReturn MutableStateFlow(state)
            on { events } doReturn MutableSharedFlow<ChartsEvent>()
        }
        val controller = RoborazziSetup.host(theme, kind, mockedVm)
        try {
            val rootView = controller.get().findViewById<View>(android.R.id.content)
            rootView.captureRoboImage("src/test/screenshots/charts_${kind.name.lowercase()}_${theme.suffix}.png")
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
