// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var notificationRationaleShowing = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) mainViewModel.markNotificationPermissionDenied()
        }

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
        navController.addOnDestinationChangedListener { _, _, args ->
            val hide = args?.getBoolean("hideBottomNav") ?: false
            binding.bottomNav.isVisible = !hide
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.shouldOfferNotificationPermission.collect { offer ->
                    if (offer) maybeShowNotificationRationale()
                }
            }
        }
    }

    private fun maybeShowNotificationRationale() {
        // Pre-13 doesn't gate notifications behind a runtime permission, and
        // the channel is already created at startup — nothing to ask.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationRationaleShowing) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return

        notificationRationaleShowing = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_notif_permission_rationale_title)
            .setMessage(R.string.backup_notif_permission_rationale_body)
            .setPositiveButton(R.string.backup_notif_permission_rationale_allow) { _, _ ->
                notificationRationaleShowing = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.backup_notif_permission_rationale_deny) { _, _ ->
                notificationRationaleShowing = false
                mainViewModel.markNotificationPermissionDenied()
            }
            .setOnCancelListener {
                notificationRationaleShowing = false
                mainViewModel.markNotificationPermissionDenied()
            }
            .show()
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
