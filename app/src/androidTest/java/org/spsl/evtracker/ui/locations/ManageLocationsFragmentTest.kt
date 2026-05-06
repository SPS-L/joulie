package org.spsl.evtracker.ui.locations

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.testing.launchFragmentInHiltContainer
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ManageLocationsFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var customLocationDao: CustomLocationDao

    @Before fun setUp() {
        hiltRule.inject()
        runBlocking {
            customLocationDao.deleteAll()
            customLocationDao.insertIfMissing(
                CustomLocationEntity(label = "Office", useCount = 5, lastUsed = 1_700_000_000_000L),
            )
            customLocationDao.insertIfMissing(
                CustomLocationEntity(label = "Home", useCount = 3, lastUsed = 1_700_000_001_000L),
            )
        }
    }

    @Test fun swipe_showsSnackbar_undo_restoresRow() {
        launchFragmentInHiltContainer<ManageLocationsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Wait for the seeded "Office" row to propagate through
                // DAO Flow → ViewModel.StateFlow → RecyclerView adapter →
                // view hierarchy. Without this poll Espresso races the
                // async-collect path and intermittently sees an empty list.
                runBlocking {
                    withTimeout(10_000) {
                        customLocationDao.observeAll()
                            .first { rows -> rows.any { it.label == "Office" } }
                    }
                }
                onView(withText("Office")).check(matches(isDisplayed()))
                onView(withText("Office")).perform(
                    GeneralSwipeAction(
                        Swipe.FAST,
                        GeneralLocation.CENTER_RIGHT,
                        GeneralLocation.CENTER_LEFT,
                        Press.FINGER,
                    ),
                )
                onView(withText(R.string.common_undo)).perform(click())
                onView(withText("Office")).check(matches(isDisplayed()))
            }
    }

    @Test fun swipe_no_undo_after_5s_rowIsGone() {
        launchFragmentInHiltContainer<ManageLocationsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withText("Office")).perform(
                    GeneralSwipeAction(
                        Swipe.FAST,
                        GeneralLocation.CENTER_RIGHT,
                        GeneralLocation.CENTER_LEFT,
                        Press.FINGER,
                    ),
                )
                // Wait for the 5s job to commit by observing the DAO Flow.
                // 20 s gives a 15 s margin over the 5 s coroutine `delay` —
                // the previous 10 s budget left only 5 s headroom and was
                // racing against load spikes on shared CI runners.
                runBlocking {
                    withTimeout(20_000) {
                        customLocationDao.observeAll()
                            .first { list -> list.none { it.label == "Office" } }
                    }
                }
                onView(withText("Office")).check(doesNotExist())
                onView(withText("Home")).check(matches(isDisplayed()))
            }
    }
}
