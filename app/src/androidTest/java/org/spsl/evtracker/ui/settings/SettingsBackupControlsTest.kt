package org.spsl.evtracker.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/**
 * Instrumented coverage for the manual Drive controls in
 * Settings ("Back up now" / "Wipe remote backup"). Drive enable state is
 * driven through DataStore directly because the Drive sign-in path
 * itself isn't part of this test's surface — we only care about whether
 * the controls render and the wipe path goes through the confirmation
 * dialog before invoking the use case.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsBackupControlsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>

    private fun seedDataStore(driveEnabled: Boolean) = runBlocking {
        dataStore.edit {
            it.clear()
            it[PreferenceKeys.SETUP_COMPLETE] = true
            it[PreferenceKeys.ACTIVE_CAR_ID] = -1
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

    @Test fun bothButtons_areGone_whenDriveDisabled() {
        seedDataStore(driveEnabled = false)
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.button_backup_now)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.button_wipe_remote)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            }
    }

    @Test fun bothButtons_areVisible_whenDriveEnabled() {
        seedDataStore(driveEnabled = true)
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.button_backup_now)).check(matches(isDisplayed()))
                onView(withId(R.id.button_wipe_remote)).check(matches(isDisplayed()))
            }
    }

    @Test fun tappingWipe_showsConfirmationDialog_andCancelLeavesNoSideEffect() {
        seedDataStore(driveEnabled = true)
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.button_wipe_remote)).perform(click())

                // Dialog title is the wipe-confirm string and is in a dialog root
                onView(withText(R.string.drive_wipe_confirm_title))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))
                onView(withText(R.string.drive_wipe_confirm_body))
                    .inRoot(isDialog())
                    .check(matches(isDisplayed()))

                // Cancel — confirmation must NOT advance to the use case;
                // the only positive signal here is that the dialog dismisses
                // and the wipe button stays clickable for a fresh attempt.
                onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())
                onView(withText(R.string.drive_wipe_confirm_title)).check(doesNotExist())
                onView(withId(R.id.button_wipe_remote)).check(matches(isDisplayed()))
            }
    }
}
