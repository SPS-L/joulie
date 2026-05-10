// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots

import androidx.appcompat.app.AppCompatDelegate
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper
import org.spsl.evtracker.HiltTestActivity
import org.spsl.evtracker.screenshots.host.FakeChartsParentFragment
import org.spsl.evtracker.ui.charts.ChartsTabFragment
import org.spsl.evtracker.ui.charts.ChartsViewModel
import java.time.Duration

/** Light vs dark theme axis for the screenshot matrix. */
enum class ThemeMode(val suffix: String) {
    LIGHT("light"),
    DARK("dark"),
}

/**
 * Builds a [HiltTestActivity] with a [FakeChartsParentFragment] hosting a
 * [ChartsTabFragment] of the requested kind, applies the requested theme,
 * idles the looper long enough for Vico's chart entry animation (and
 * [PieChartView]'s 400 ms sweep on the AC/DC + Locations tabs) to complete,
 * and returns the activity controller for screenshot capture.
 *
 * Caller must set [FakeChartsParentFragment.nextMockedVm] BEFORE calling
 * [host] — the parent fragment reads the slot during VM resolution, which
 * happens once the child tab fragment hits `onViewCreated`.
 */
object RoborazziSetup {

    /** Idle budget after fragment commit. Covers Vico's default chart
     *  enter animation + [PieChartView]'s 400 ms sweep + a safety margin
     *  for post-animation invalidation passes. Bump to 1500 ms if CI
     *  flakes. */
    private val ANIMATION_IDLE = Duration.ofMillis(800)

    fun host(
        theme: ThemeMode,
        kind: ChartsTabFragment.TabKind,
        mockedVm: ChartsViewModel,
    ): ActivityController<HiltTestActivity> {
        // Apply theme BEFORE activity creation so AppCompat picks up the
        // right DayNight resolution at theme.applyStyle() time.
        AppCompatDelegate.setDefaultNightMode(
            if (theme == ThemeMode.DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            },
        )

        FakeChartsParentFragment.nextMockedVm = mockedVm

        val controller = Robolectric.buildActivity(HiltTestActivity::class.java)
            .setup() // create -> start -> resume

        val activity = controller.get()
        val parent = FakeChartsParentFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, parent, PARENT_TAG)
            .commitNow()

        val tab = ChartsTabFragment.newInstance(kind)
        parent.childFragmentManager.beginTransaction()
            .add(FakeChartsParentFragment.CHILD_CONTAINER_ID, tab, TAB_TAG)
            .commitNow()

        // Settle: drain pending main-looper work first, then advance the
        // clock past the chart animation duration so the captured frame
        // shows the final y-scale rather than a partially-animated chart.
        ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper(ANIMATION_IDLE.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        // One more drain in case animation completion enqueued an
        // invalidation callback we want to render before the screenshot.
        ShadowLooper.idleMainLooper()

        return controller
    }

    private const val PARENT_TAG = "fake_charts_parent"
    private const val TAB_TAG = "tab_under_test"
}
