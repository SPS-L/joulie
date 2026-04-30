package org.spsl.evtracker.ui.wizard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoActivityResumedException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.MainActivity
import org.spsl.evtracker.R
import org.spsl.evtracker.data.preferences.PreferenceKeys
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WizardFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        hiltRule.inject()
        // Each test starts from a known-empty DataStore.
        runBlocking { dataStore.edit { it.clear() } }
    }

    @Test
    fun firstLaunch_showsWizard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.wizard_root)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun completedSetup_skipsWizard() {
        runBlocking {
            dataStore.edit { it[PreferenceKeys.SETUP_COMPLETE] = true }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.dashboard_fab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun finishWizard_landsOnDashboard_andBackPressExitsApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Page 1 → Get Started
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Page 2 → Next (default selections are valid)
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Page 3 → Finish
            onView(withId(R.id.wizard_button_next)).perform(click())
            // Dashboard FAB visible
            onView(withId(R.id.dashboard_fab)).check(matches(isDisplayed()))
            // Back press exits the app — wizard must NOT be on the back stack
            // (regression guard for losing popUpToInclusive on action_wizard_to_dashboard).
            try {
                Espresso.pressBack()
                throw AssertionError(
                    "Expected NoActivityResumedException — wizard is still on the back stack",
                )
            } catch (expected: NoActivityResumedException) {
                // Pass: there is no destination to pop, so the activity is finishing.
            }
        }
    }
}
