// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker

import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * TASK-58: Hilt-aware host activity for instrumented fragment tests.
 *
 * Lives in the **debug** source set (not androidTest, not main) because
 * the instrumentation runner launches activities under the *target APK's*
 * package — same precedent as `androidx.fragment.app.testing.EmptyFragmentActivity`,
 * whose manifest entry was promoted to `debugImplementation` in TASK-50 sub-fix A.
 *
 * `@AndroidEntryPoint` is load-bearing: any `@AndroidEntryPoint Fragment` (which
 * is most of the app's fragments under Hilt + KSP) asserts at runtime that its
 * host activity is also `@AndroidEntryPoint`. `EmptyFragmentActivity` is plain
 * `FragmentActivity`, so `launchFragmentInContainer` against any of our fragments
 * crashes the test process with `IllegalStateException`. Use
 * [org.spsl.evtracker.testing.launchFragmentInHiltContainer] instead.
 */
@AndroidEntryPoint
class HiltTestActivity : AppCompatActivity()
