// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.testing

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.spsl.evtracker.HiltTestActivity
import org.spsl.evtracker.R

/**
 * Drop-in replacement for `androidx.fragment.app.testing.launchFragmentInContainer`
 * for fragments that depend on Hilt injection.
 *
 * `launchFragmentInContainer` hosts the fragment in the library's
 * `EmptyFragmentActivity`, which is *not* `@AndroidEntryPoint`. Hilt's runtime
 * preconditions check requires that any `@AndroidEntryPoint` fragment is hosted
 * in an `@AndroidEntryPoint` activity, so the empty host throws
 * `IllegalStateException` from `Preconditions.checkState` and crashes the test
 * process — taking the rest of the suite down with it. This helper hosts the
 * fragment in [HiltTestActivity] instead, which is `@AndroidEntryPoint` and
 * lives in the debug variant of the app manifest.
 *
 * Surface area mirrors `FragmentScenario` for the call sites we use:
 * [moveToState], [onFragment], [recreate], [close], plus [AutoCloseable] so
 * the existing `.use { }` blocks work unchanged.
 */
class HiltFragmentScenario<T : Fragment> @PublishedApi internal constructor(
    @PublishedApi internal val scenario: ActivityScenario<HiltTestActivity>,
) : AutoCloseable {

    fun moveToState(newState: Lifecycle.State): HiltFragmentScenario<T> = apply {
        scenario.moveToState(newState)
    }

    fun recreate(): HiltFragmentScenario<T> = apply {
        scenario.recreate()
    }

    fun onFragment(action: (T) -> Unit): HiltFragmentScenario<T> = apply {
        scenario.onActivity { activity ->
            @Suppress("UNCHECKED_CAST")
            val fragment = activity.supportFragmentManager
                .findFragmentByTag(FRAGMENT_TAG) as T
            action(fragment)
        }
    }

    override fun close() {
        scenario.close()
    }

    companion object {
        @PublishedApi
        internal const val FRAGMENT_TAG: String = "hilt_fragment_scenario_tag"
    }
}

/**
 * Launch [T] hosted in [HiltTestActivity] under the given [themeResId] (defaults
 * to the app theme used by every production fragment). The fragment is added
 * via `commitNow()` so it is fully resumed before the helper returns.
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    @StyleRes themeResId: Int = R.style.Theme_EVTracker,
): HiltFragmentScenario<T> {
    val intent = Intent.makeMainActivity(
        ComponentName(
            ApplicationProvider.getApplicationContext(),
            HiltTestActivity::class.java,
        ),
    ).putExtra(THEME_EXTRAS_BUNDLE_KEY, themeResId)

    val scenario = ActivityScenario.launch<HiltTestActivity>(intent)
    scenario.onActivity { activity ->
        activity.setTheme(themeResId)
        val fragment = activity.supportFragmentManager.fragmentFactory.instantiate(
            requireNotNull(T::class.java.classLoader),
            T::class.java.name,
        )
        fragment.arguments = fragmentArgs
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, HiltFragmentScenario.FRAGMENT_TAG)
            .commitNow()
    }
    return HiltFragmentScenario(scenario)
}

// String literal mirrors `EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY` so that
// HiltTestActivity could in principle honour a per-launch theme override the
// same way EmptyFragmentActivity does. Today HiltTestActivity sets the theme
// in onActivity { setTheme(...) } above; the extra is kept for parity in case
// we later move theming into HiltTestActivity.onCreate.
@PublishedApi
internal const val THEME_EXTRAS_BUNDLE_KEY: String =
    "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY"
