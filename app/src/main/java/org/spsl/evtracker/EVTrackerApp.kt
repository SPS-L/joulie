// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.notification.AndroidBackupNotifier
import org.spsl.evtracker.domain.locale.LocaleApplier
import org.spsl.evtracker.domain.repository.SettingsReader
import javax.inject.Inject

@HiltAndroidApp
class EVTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var settingsReader: SettingsReader

    @Inject lateinit var localeApplier: LocaleApplier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        AndroidBackupNotifier.ensureChannels(this)
        // Default to follow-system synchronously; update once DataStore yields the persisted value.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        CoroutineScope(Dispatchers.Main).launch {
            val theme = settingsReader.theme.first()
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                },
            )
        }
        // TASK-55: apply the persisted language tag at app start. Async per
        // the same trade-off as the theme branch above — DataStore reads are
        // sub-50ms on a cold start. AppCompat 1.6+ persists the value
        // internally, so subsequent app starts come up in the right locale
        // before this coroutine even runs; the read is mainly a fail-safe
        // for the first launch after a fresh install.
        CoroutineScope(Dispatchers.Main).launch {
            val tag = settingsReader.languageTag.first()
            localeApplier.apply(tag)
        }
    }
}
