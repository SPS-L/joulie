// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * TASK-21: Cold-start latency benchmark comparing the **uncompiled**
 * baseline against ART AOT compilation driven by the committed
 * Baseline Profile.
 *
 * Pair of test runs (parameterized):
 *
 * - [CompilationMode.None] — every method JITs at first use. This is
 *   the cold-start cost a user on a "first launch after install"
 *   would experience if the profile were stripped.
 * - [CompilationMode.Partial] with `BaselineProfileMode.Require` —
 *   methods listed in `app/src/main/baseline-prof.txt` are AOT-compiled
 *   at install time. The benchmark fails fast if no profile is bundled,
 *   which is what we want: a missing profile must not silently degrade
 *   to "None" and hide a regression.
 *
 * Run on a connected device with:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 * Report the median TimeToInitialDisplay delta in the release notes
 * when bumping `versionCode`.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(private val compilationMode: CompilationMode) {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        // 10 iterations balances noise reduction against the ~3-minute
        // per-run cost on a hot AVD. Lower iterations widen the
        // confidence interval; higher iterations add wall-clock cost
        // without meaningfully tightening the median.
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
        // Wait for either screen to render so the timing metric closes
        // on a meaningful frame. Cold start lands on the wizard for
        // first-launch installs and on the dashboard for everyone else.
        // Wait on either dashboard_content (post-wizard) or wizard_root
        // (first launch). The metric closes as soon as either renders;
        // both screens go through the same Hilt graph + DataStore reads
        // so the timing is comparable across StartupMode.COLD iterations.
        val dashboardUp =
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "dashboard_content")),
                COLD_START_TIMEOUT_MS,
            )
        if (!dashboardUp) {
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "wizard_root")),
                COLD_START_TIMEOUT_MS,
            )
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "org.spsl.evtracker"
        private const val COLD_START_TIMEOUT_MS = 10_000L

        @JvmStatic
        @Parameterized.Parameters(name = "compilation={0}")
        fun parameters(): List<Array<Any>> = listOf(
            arrayOf(CompilationMode.None()),
            arrayOf(CompilationMode.Partial(BaselineProfileMode.Require)),
        )
    }
}
