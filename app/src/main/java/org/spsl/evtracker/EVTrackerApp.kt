package org.spsl.evtracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.repository.SettingsRepository

@HiltAndroidApp
class EVTrackerApp : Application() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        // Default to follow-system synchronously; update once DataStore yields the persisted value.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        CoroutineScope(Dispatchers.Main).launch {
            val theme = settingsRepository.theme.first()
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
