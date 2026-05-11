// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * TASK-21: Producer for the Joulie Android Baseline Profile.
 *
 * Runs against :app's auto-created `nonMinifiedRelease` variant via
 * macro-benchmark + UIAutomator and records ART method profiles for
 * the hot cold-startup journeys. The androidx baselineprofile gradle
 * plugin merges the captured profile into
 * `app/src/main/baseline-prof.txt`, which AGP bundles into release
 * APKs for AOT compilation at install time on user devices.
 *
 * Profile coverage (BACKLOG TASK-21 step 3):
 *
 * 1. Cold start to **Wizard** (first-launch path) — exercised by
 *    [generate] after `pm clear` wipes DataStore so `setupComplete`
 *    is false.
 * 2. Cold start to **Dashboard** — exercised on every subsequent
 *    `startActivityAndWait` iteration after the wizard has been
 *    walked through. `BaselineProfileRule.collect` does this
 *    automatically over its internal iteration loop.
 * 3. **ChartsFragment** navigation — tapped via the bottom-nav
 *    after dashboard renders. Vico chart class loading is the
 *    primary cost here.
 * 4. **ChargeEditFragment** open via the dashboard FAB — best-effort,
 *    only executed when a car has been seeded (FAB is otherwise a
 *    no-op per `DashboardViewModel.onFabClick`). Without a seeded
 *    car we still capture the FAB's onClick + the dashboard's empty
 *    state, which exercises most of the relevant code paths.
 *
 * Regenerate via `./gradlew :baselineprofile:generateBaselineProfile`
 * on a connected device or managed AVD running API 28+. See the
 * "Baseline profile cadence" section in `CLAUDE.md` for when to
 * refresh.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            clearAppData()
            pressHome()
            startActivityAndWait()

            // Either the wizard or the dashboard will appear depending on
            // DataStore state. After `clearAppData` it's the wizard; on
            // subsequent macro-benchmark iterations `setupComplete` may
            // still be `false` (BaselineProfileRule reinstalls per
            // iteration), so always start from the wizard branch and
            // walk to the dashboard.
            val wizardAppeared = device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "wizard_root")),
                UI_TIMEOUT_MS,
            )
            if (wizardAppeared) {
                walkThroughWizard()
            }

            // Wait for dashboard content; the toolbar's car-spinner is
            // guaranteed to render even with zero cars seeded.
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "dashboard_content")),
                UI_TIMEOUT_MS,
            )

            // Tap the FAB to exercise the ChargeEdit class graph. With
            // no car seeded the VM's onFabClick is a no-op, but the
            // listener path still runs and we capture the dashboard's
            // empty-state code path.
            device.findObject(By.res(TARGET_PACKAGE, "dashboard_fab"))?.click()
            device.waitForIdle(UI_TIMEOUT_MS)

            // Navigate to Charts via the bottom-nav. The bottom-nav menu
            // item exposes its title text ("Charts") as a UiAutomator
            // selector — the underlying view ids are NavigationView
            // internals which aren't stable across Material versions.
            device.findObject(By.text("Charts"))?.click()
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "charts_pager")),
                UI_TIMEOUT_MS,
            )

            // Back to dashboard so the next iteration starts from a
            // known state. Press back twice in case the system threw an
            // intermediate dialog (permission rationale, etc.).
            device.pressBack()
            device.waitForIdle(UI_TIMEOUT_MS)
        }
    }

    /** Walk the 4-page wizard end-to-end so cold start lands on the dashboard. */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.walkThroughWizard() {
        // Pages 0 → 1 → 2 → 3 via the persistent Next button.
        repeat(WIZARD_PAGE_COUNT - 1) {
            device.findObject(By.res(TARGET_PACKAGE, "wizard_button_next"))?.click()
            device.waitForIdle(UI_TIMEOUT_MS)
        }
        // Page 3 has the disclaimer accept switch which gates Finish.
        device.findObject(By.res(TARGET_PACKAGE, "wizard_page4_accept"))?.click()
        device.waitForIdle(UI_TIMEOUT_MS)
        // Finish reuses the Next button's slot once page 3 is reached.
        device.findObject(By.res(TARGET_PACKAGE, "wizard_button_next"))?.click()
        device.waitForIdle(UI_TIMEOUT_MS)
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.clearAppData() {
        // `pm clear` wipes /data/data/<pkg> including DataStore so
        // `setupComplete` defaults back to false. UIAutomator's
        // executeShellCommand is the only way to reach pm from the
        // producer process; the alternative (running `am force-stop` +
        // toggling preferences via instrumentation) doesn't survive
        // a cold start.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm clear $TARGET_PACKAGE")
            .close()
        device.waitForIdle(UI_TIMEOUT_MS)
    }

    companion object {
        /** `:app`'s release applicationId. Debug builds use `.debug`. */
        private const val TARGET_PACKAGE = "org.spsl.evtracker"

        /** Polling window for UiAutomator selectors during cold start. */
        private const val UI_TIMEOUT_MS = 10_000L

        /** Matches `WizardFragment` — Welcome, Metric, Currency, About. */
        private const val WIZARD_PAGE_COUNT = 4
    }
}
