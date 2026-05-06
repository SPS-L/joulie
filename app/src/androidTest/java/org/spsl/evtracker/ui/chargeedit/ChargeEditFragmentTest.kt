package org.spsl.evtracker.ui.chargeedit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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
class ChargeEditFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            dataStore.edit {
                it.clear()
                it[PreferenceKeys.SETUP_COMPLETE] = true
                it[PreferenceKeys.ACTIVE_CAR_ID] = 1
                it[PreferenceKeys.DISTANCE_UNIT] = "km"
                it[PreferenceKeys.CURRENCY] = "EUR"
            }
        }
    }

    @Test
    fun homeChip_clickedFillsLocation() {
        launchFragmentInHiltContainer<ChargeEditFragment>(
            themeResId = org.spsl.evtracker.R.style.Theme_EVTracker,
        ).moveToState(Lifecycle.State.RESUMED).use {
            onView(withText(R.string.location_home)).perform(click())
            onView(withId(R.id.charge_edit_location)).check(matches(withText("Home")))
        }
    }

    @Test
    fun saveBlankOdometer_showsError() {
        launchFragmentInHiltContainer<ChargeEditFragment>(
            themeResId = org.spsl.evtracker.R.style.Theme_EVTracker,
        ).moveToState(Lifecycle.State.RESUMED).use {
            onView(withId(R.id.charge_edit_save)).perform(scrollTo(), click())
            val expected = InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(R.string.error_odometer_required)
            onView(withId(R.id.charge_edit_odometer_layout)).check(matches(hasErrorText(expected)))
        }
    }
}
