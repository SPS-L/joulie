package org.spsl.evtracker

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.databinding.ActivityMainBinding
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var resetAllDataUseCase: ResetAllDataUseCase

    private val isLoading = MutableStateFlow(true)
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var navGraph: NavGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { isLoading.value }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        binding.bottomNav.setupWithNavController(navController)
        val hideOn = setOf(
            R.id.wizardFragment,
            R.id.chargeEditFragment,
            R.id.carsFragment,
            R.id.manageLocationsFragment
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.isVisible = dest.id !in hideOn
        }

        startupSequence()
    }

    private fun startupSequence() {
        lifecycleScope.launch {
            // F1 — startup auto-recovery for an interrupted global reset (§9.2).
            // Splash stays on screen while this runs (isLoading is still true).
            if (settingsRepository.resetInProgress.first()) {
                val result = runCatching { resetAllDataUseCase() }
                if (result.isFailure) {
                    val cause = result.exceptionOrNull()
                    android.util.Log.e("MainActivity", "Reset auto-recovery failed", cause)
                    // BLOCKING dialog: user cannot reach normal UI with resetInProgress=true.
                    showRecoveryFailureDialog(cause)
                    return@launch
                }
            }
            mountNavGraph()
        }
    }

    private fun showRecoveryFailureDialog(cause: Throwable?) {
        // Dismiss the splash so the dialog appears on a blank Activity surface.
        // The nav host stays unmounted — there is no Fragment for the user to interact with.
        isLoading.value = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_failure_title)
            .setMessage(getString(R.string.recovery_failure_body, cause?.localizedMessage ?: ""))
            .setCancelable(false)
            .setPositiveButton(R.string.recovery_failure_retry) { _, _ ->
                isLoading.value = true
                startupSequence()
            }
            .show()
    }

    private suspend fun mountNavGraph() {
        val complete = settingsRepository.setupComplete.first()
        if (!complete) navGraph.setStartDestination(R.id.wizardFragment)
        navController.graph = navGraph
        isLoading.value = false
    }

    /**
     * Test hook: instrumented tests use this to wait for "startup completed" without
     * relying on `Thread.sleep`. True iff the nav graph has been mounted, which only
     * happens after auto-recovery either ran successfully or was skipped (flag was false).
     */
    @VisibleForTesting
    fun isNavGraphMounted(): Boolean =
        ::navController.isInitialized && navController.graph != null && !isLoading.value
}
