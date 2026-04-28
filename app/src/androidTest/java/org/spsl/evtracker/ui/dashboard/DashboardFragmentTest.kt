package org.spsl.evtracker.ui.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.MainActivity
import org.spsl.evtracker.R
import org.spsl.evtracker.data.preferences.PreferenceKeys

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DashboardFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            dataStore.edit {
                it.clear()
                it[PreferenceKeys.SETUP_COMPLETE] = true
            }
        }
    }

    @Test
    fun noCar_emptyStateShowsAddCarCta() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.empty_no_car_headline)).check(matches(isDisplayed()))
            onView(withText(R.string.empty_no_car_cta)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun fab_visible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.dashboard_fab)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun emptyStateCta_navigatesAwayFromDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText(R.string.empty_no_car_cta)).perform(click())
            onView(withId(R.id.cars_fab)).check(matches(isDisplayed()))
        }
    }
}
