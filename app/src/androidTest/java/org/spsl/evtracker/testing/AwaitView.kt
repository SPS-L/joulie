// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.testing

/**
 * Poll an Espresso interaction until it succeeds or the budget runs
 * out. Used to bridge the gap between asynchronous state propagation chains
 * (DAO Flow → ViewModel.StateFlow → Fragment.collect → RecyclerView /
 * View visibility) and Espresso's synchronous assertion model.
 *
 * Espresso has IdlingResource for this in principle, but our coroutine-based
 * flows don't register one by default — Espresso runs the assertion the
 * moment the looper is idle, which is often before the StateFlow has had a
 * chance to emit its first state. This helper retries [block] every
 * [intervalMs] until it returns without throwing or the [timeoutMs] budget
 * is exceeded; on timeout it rethrows the *last* exception so the failure
 * report still carries Espresso's view-hierarchy dump.
 */
inline fun awaitView(
    timeoutMs: Long = 5_000,
    intervalMs: Long = 50,
    block: () -> Unit,
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            block()
            return
        } catch (t: Throwable) {
            last = t
            try {
                Thread.sleep(intervalMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw last
            }
        }
    }
    throw last ?: AssertionError("awaitView timed out after ${timeoutMs}ms with no failure recorded")
}
