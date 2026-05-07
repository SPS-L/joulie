// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.widget

/**
 * Narrow domain interface use cases call after committing a
 * change that affects the home-screen widget's content.
 *
 * The Android implementation triggers an `AppWidgetManager` update via
 * a self-broadcast back into [LastChargeWidget]; the JVM-test fake just
 * counts invocations.
 *
 * Kept narrow (single method, no return value) so use cases stay in the
 * "commit, then schedule" pattern they already use for
 * [org.spsl.evtracker.domain.backup.BackupScheduler] — the actual
 * `RemoteViews` rebuild is fire-and-forget from the use case's
 * perspective.
 */
interface WidgetRefresher {
    fun refresh()
}
