// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.locale

/**
 * TASK-55: narrow boundary around `AppCompatDelegate.setApplicationLocales`.
 *
 * The static framework call is hostile to JVM tests (it touches
 * `LocaleManager` on Android 13+ and `AppLocalesStorageHelper` on older
 * devices). Wrapping it in a domain interface lets ViewModels stay
 * JVM-testable — production binds to `AndroidLocaleApplier` via
 * `LocaleModule`; tests substitute `FakeLocaleApplier` and assert the
 * applied tag without booting any framework.
 *
 * Mirrors the TASK-12 `WidgetRefresher` pattern.
 */
interface LocaleApplier {
    /**
     * Apply the given IETF BCP-47 language tag to the app process.
     *
     * Empty string ("") means "follow system" — implementations should
     * pass an empty `LocaleListCompat` to the framework call so the OS
     * resolves the locale from device settings each time.
     *
     * Calling this triggers an Activity recreation on most Android
     * versions; callers should expect their `viewModelScope` to be
     * cancelled shortly after.
     */
    fun apply(tag: String)
}
