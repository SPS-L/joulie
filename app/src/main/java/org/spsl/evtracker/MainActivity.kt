package org.spsl.evtracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.repository.SettingsRepository

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val isLoading = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { isLoading.value }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)

        lifecycleScope.launch {
            val complete = settingsRepository.setupComplete.first()
            if (!complete) {
                graph.setStartDestination(R.id.wizardFragment)
            }
            navController.graph = graph
            isLoading.value = false
        }
    }
}
