// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.screenshots.host

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.spsl.evtracker.ui.charts.ChartsViewModel

/**
 * Test-only parent fragment for hosting [org.spsl.evtracker.ui.charts.ChartsTabFragment]
 * during Roborazzi screenshot tests (TASK-79).
 *
 * The production tab fragment uses
 * `viewModels({ requireParentFragment() })`, so the parent's
 * `defaultViewModelProviderFactory` is what supplies the [ChartsViewModel].
 * Overriding it here lets us inject a Mockito-mocked VM with a hardcoded
 * `uiState`, avoiding the full Hilt graph wiring that would be needed to
 * exercise the real VM (and which is unchanged by TASK-30 anyway).
 *
 * Not annotated with `@AndroidEntryPoint`: Hilt's
 * `FragmentComponentManager.findActivity()` walks up the context chain
 * to the host activity (`HiltTestActivity` from the debug source set,
 * which IS `@AndroidEntryPoint`), so the child tab fragment's component
 * lookup still succeeds. Adding the annotation here would require its
 * own Hilt deps and add zero value.
 */
class FakeChartsParentFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext()).apply { id = CHILD_CONTAINER_ID }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val mocked = nextMockedVm
                    ?: error(
                        "FakeChartsParentFragment.nextMockedVm was not set before host(). " +
                            "Each test must assign it before launching the activity.",
                    )
                return mocked as T
            }
        }

    companion object {
        /**
         * View id of the [FrameLayout] container that the test attaches the
         * production [org.spsl.evtracker.ui.charts.ChartsTabFragment] into as
         * a child fragment. Generated once at class init so it survives
         * [View.generateViewId]'s monotonic counter across multiple tests
         * within the same JVM.
         */
        val CHILD_CONTAINER_ID: Int = View.generateViewId()

        /**
         * Mock-VM slot. Set by the test BEFORE the activity is created;
         * read once during [ChartsViewModel] resolution. A static slot is
         * pragmatic: Fragment cannot take constructor args without a
         * custom FragmentFactory, and we don't need that complexity for
         * one helper class used inside a single test class.
         */
        var nextMockedVm: ChartsViewModel? = null
    }
}
