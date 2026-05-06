package org.spsl.evtracker.ui.about

import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.testing.launchFragmentInHiltContainer

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AboutFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Before fun setUp() {
        hiltRule.inject()
    }

    @Test fun versionLabel_isDisplayed_andNonEmpty() {
        launchFragmentInHiltContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.about_version_label))
                    .check(matches(allOf(isDisplayed(), not(withText("")))))
            }
    }

    @Test fun spsLabAcknowledgment_isVisible() {
        launchFragmentInHiltContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withSubstring("SPS-Lab"))
                    .check(matches(isDisplayed()))
            }
    }

    @Test fun spsLabUrlLink_isPresent() {
        launchFragmentInHiltContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.about_link_sps_lab))
                    .check(matches(allOf(isDisplayed(), withText("sps-lab.org"))))
            }
    }

    @Test fun licenseCard_containsGplIdentifier() {
        // TASK-51: relicensed from MIT to GPL-3.0-or-later. The card body
        // surfaces the SPDX identifier so a TalkBack user (and an
        // automated audit) can confirm the license without parsing
        // marketing prose.
        launchFragmentInHiltContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.about_license_body))
                    .check(matches(allOf(isDisplayed(), withSubstring("GPL-3.0-or-later"))))
            }
    }

    @Test fun disclaimerCard_containsLiability() {
        launchFragmentInHiltContainer<AboutFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                onView(withId(R.id.about_disclaimer_body))
                    .check(matches(allOf(isDisplayed(), withSubstring("liability"))))
            }
    }
}
