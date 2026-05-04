// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.spsl.evtracker.domain.locale.LocaleApplier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-55: production [LocaleApplier] backed by androidx.appcompat.
 *
 * `setApplicationLocales` persists internally (per-app on Android 13+,
 * a SharedPreferences fallback on older versions) AND triggers an
 * Activity recreation on the same call — this fragment's `viewModelScope`
 * will be cancelled by the time it returns.
 */
@Singleton
class AndroidLocaleApplier @Inject constructor() : LocaleApplier {
    override fun apply(tag: String) {
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
