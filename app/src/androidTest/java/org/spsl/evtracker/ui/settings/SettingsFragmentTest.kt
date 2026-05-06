package org.spsl.evtracker.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withAlpha
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.testing.launchFragmentInHiltContainer
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>

    private fun seedDataStore(activeCarId: Int = 5, driveEnabled: Boolean = false) = runBlocking {
        dataStore.edit {
            it.clear()
            it[PreferenceKeys.SETUP_COMPLETE] = true
            it[PreferenceKeys.ACTIVE_CAR_ID] = activeCarId
            it[PreferenceKeys.DISTANCE_UNIT] = "km"
            it[PreferenceKeys.CURRENCY] = "EUR"
            it[PreferenceKeys.PRIMARY_METRIC] = "km_per_kwh"
            it[PreferenceKeys.THEME] = "system"
            it[PreferenceKeys.DRIVE_ENABLED] = driveEnabled
        }
    }

    @Before fun setUp() {
        hiltRule.inject()
    }

    @Test fun themeRow_tap_opensDialog_select_dark_updatesSummary() {
        seedDataStore()
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.row_theme)).perform(click())
                onView(withText(R.string.settings_theme_dark))
                    .inRoot(isDialog())
                    .perform(click())
                onView(withId(R.id.summary_theme))
                    .check(matches(withText(R.string.settings_theme_dark)))
            }
    }

    @Test fun exportCsv_disabled_whenNoActiveCar() {
        seedDataStore(activeCarId = -1)
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.row_export_csv)).check(
                    matches(
                        allOf(not(isClickable()), withAlpha(0.5f)),
                    ),
                )
            }
    }

    @Test fun resetAll_confirm_navigatesToWizard() {
        seedDataStore()
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setGraph(R.navigation.nav_graph)
            setCurrentDestination(R.id.settingsFragment)
        }
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED)
            .onFragment { fragment ->
                Navigation.setViewNavController(fragment.requireView(), nav)
            }

        onView(withId(R.id.row_reset_all)).perform(click())
        onView(withText(R.string.common_confirm)).inRoot(isDialog()).perform(click())

        assertEquals(R.id.wizardFragment, nav.currentDestination?.id)
    }

    @Test fun resetAll_dialogText_includesDriveWarning_whenDriveEnabled() {
        seedDataStore(driveEnabled = true)
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.row_reset_all)).perform(click())
                onView(withText(R.string.settings_reset_all_confirm_drive_on))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))
            }
    }
}
