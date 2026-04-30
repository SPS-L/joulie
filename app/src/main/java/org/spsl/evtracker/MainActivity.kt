package org.spsl.evtracker

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.databinding.ActivityMainBinding
import org.spsl.evtracker.ui.MainViewModel
import org.spsl.evtracker.ui.MainViewModel.StartupState

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var navGraph: NavGraph
    private var navMounted = false
    private var recoveryDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition {
            mainViewModel.startupState.value is StartupState.Loading
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        binding.bottomNav.setupWithNavController(navController)
        val hideOn = setOf(
            R.id.wizardFragment,
            R.id.chargeEditFragment,
            R.id.carsFragment,
            R.id.manageLocationsFragment,
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.isVisible = dest.id !in hideOn
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.startupState.collect { state ->
                    when (state) {
                        is StartupState.Loading -> Unit
                        is StartupState.Ready ->
                            if (!navMounted) mountNavGraph(state.setupComplete)
                        is StartupState.RecoveryFailed ->
                            if (!recoveryDialogShowing) showRecoveryFailureDialog(state.cause)
                    }
                }
            }
        }
    }

    private fun showRecoveryFailureDialog(cause: Throwable?) {
        android.util.Log.e("MainActivity", "Reset auto-recovery failed", cause)
        recoveryDialogShowing = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_failure_title)
            .setMessage(getString(R.string.recovery_failure_body, cause?.localizedMessage ?: ""))
            .setCancelable(false)
            .setPositiveButton(R.string.recovery_failure_retry) { _, _ ->
                recoveryDialogShowing = false
                mainViewModel.runStartupSequence()
            }
            .show()
    }

    private fun mountNavGraph(setupComplete: Boolean) {
        if (!setupComplete) navGraph.setStartDestination(R.id.wizardFragment)
        navController.graph = navGraph
        navMounted = true
    }

    /**
     * Test hook: instrumented tests use this to wait for "startup completed" without
     * relying on `Thread.sleep`. True iff the nav graph has been mounted, which only
     * happens after auto-recovery either ran successfully or was skipped.
     */
    @VisibleForTesting
    fun isNavGraphMounted(): Boolean = navMounted
}
