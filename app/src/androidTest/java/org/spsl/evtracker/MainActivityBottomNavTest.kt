package org.spsl.evtracker

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.repository.SettingsRepository
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityBottomNavTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsRepository: SettingsRepository

    @Before fun setUp() {
        hiltRule.inject()
        // Land on dashboard, not the wizard, so the start destination is a
        // bottom-nav-visible one. completeSetup writes setupComplete=true
        // along with the wizard's three other prefs in one atomic block.
        runBlocking {
            settingsRepository.completeSetup(
                metric = "kwh_per_100km",
                unit = "km",
                currency = "EUR",
            )
        }
    }

    /** Polls `MainActivity.isNavGraphMounted` until true. Mirrors the helper in
     *  MainActivityResetRecoveryTest so we don't depend on Thread.sleep. */
    private fun ActivityScenario<MainActivity>.awaitNavMounted(timeoutMs: Long = 10_000) = runBlocking {
        withTimeout(timeoutMs) {
            while (true) {
                var mounted = false
                onActivity { mounted = it.isNavGraphMounted() }
                if (mounted) return@withTimeout
                delay(100)
            }
        }
    }

    @Test fun bottomNav_visibleOnDashboard_hiddenOnChargeEdit_visibleAfterPop() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.awaitNavMounted()

            scenario.onActivity { activity ->
                val bottomNav = activity.findViewById<View>(R.id.bottom_nav)
                assertEquals(
                    "dashboard (no hideBottomNav arg) must show the nav",
                    View.VISIBLE,
                    bottomNav.visibility,
                )

                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHost.navController.navigate(
                    R.id.action_dashboard_to_chargeEdit,
                )
                assertEquals(
                    "chargeEditFragment declares hideBottomNav=true",
                    View.GONE,
                    bottomNav.visibility,
                )

                navHost.navController.popBackStack()
                assertEquals(
                    "popping back to dashboard must restore the nav",
                    View.VISIBLE,
                    bottomNav.visibility,
                )
            }
        }
    }
}
