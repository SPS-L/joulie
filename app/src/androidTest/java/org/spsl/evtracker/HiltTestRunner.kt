package org.spsl.evtracker

import android.app.Application
import android.content.Context
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }

    /**
     * TASK-18 Step 6: enable Espresso's accessibility checks once per test
     * process. `AccessibilityChecks.enable()` installs a per-thread
     * interceptor that runs the WCAG 2.1 AA rule set against the targeted
     * view on every `ViewAction` (click, type, scrollTo, …) in every
     * Espresso test, failing the test on any violation.
     *
     * Configured to scan from the root view (not just the targeted element)
     * so issues like undersized touch targets nested deep in a layout still
     * surface — pre-existing violations on adjacent views show up the first
     * time any test in their fragment runs.
     *
     * **No suppression matchers are wired today.** The intent of Step 6 is
     * to make the existing audit gap measurable: any violation already in
     * the codebase will fail
     * [`nightly-instrumented.yml`](../../../../../../.github/workflows/nightly-instrumented.yml)'s
     * API 26 + API 35 matrix on the next cron run, and those failures
     * become the input list for the remaining TASK-18 scope (steps 1–5,
     * 7, 8). The nightly job is informational only — never blocks PRs —
     * so the red signal is a discovery tool, not a regression.
     */
    override fun onStart() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
        super.onStart()
    }
}
