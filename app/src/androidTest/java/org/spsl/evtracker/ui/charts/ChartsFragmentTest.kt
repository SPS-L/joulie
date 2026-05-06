package org.spsl.evtracker.ui.charts

import android.view.View
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
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.testing.launchFragmentInHiltContainer
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChartsFragmentTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var carDao: CarDao

    @Inject lateinit var chargeEventDao: ChargeEventDao

    private fun seedDataStore(activeCarId: Int = 1) = runBlocking {
        dataStore.edit {
            it.clear()
            it[PreferenceKeys.SETUP_COMPLETE] = true
            it[PreferenceKeys.ACTIVE_CAR_ID] = activeCarId
            it[PreferenceKeys.DISTANCE_UNIT] = "km"
            it[PreferenceKeys.CURRENCY] = "EUR"
            it[PreferenceKeys.PRIMARY_METRIC] = "km_per_kwh"
            it[PreferenceKeys.THEME] = "system"
            it[PreferenceKeys.DRIVE_ENABLED] = false
        }
    }

    private suspend fun seedDb(events: List<ChargeEventEntity>, withCar: Boolean = true) {
        chargeEventDao.deleteAll()
        carDao.deleteAll()
        if (withCar) carDao.insert(CarEntity(id = 1L, name = "Car", createdAt = 0L))
        events.forEach { chargeEventDao.insert(it) }
    }

    private fun ev(
        date: Long,
        odo: Double,
        type: ChargeType = ChargeType.AC,
        cost: Double? = null,
        currency: String? = null,
    ) = ChargeEventEntity(
        id = 0L, carId = 1L, eventDate = date, odometerKm = odo, kwhAdded = 10.0,
        chargeType = type, costTotal = cost, costPerKwh = null,
        currency = currency, location = null, note = "", createdAt = 0L,
    )

    /**
     * Espresso scoping helper for ViewPager2 tab tests.
     *
     * `ViewPager2` keeps neighbouring page Fragments attached for prefetch, so
     * IDs from the shared `fragment_charts_tab.xml` (`charts_tab_empty_message`,
     * `charts_tab_subtitle`, `charts_tab_chart_root`) are NOT unique in the view
     * hierarchy when tests run. A bare `withId(...)` matches every page's copy
     * and Espresso throws AmbiguousViewMatcherException.
     *
     * The active page is the only one whose `charts_tab_chart_root` is
     * `isDisplayed()` (offscreen pages have isDisplayed = false because their
     * fragment view is detached or off-screen). We scope every per-tab assertion
     * to the descendant chain of that displayed root.
     */
    private fun inActivePage(matcher: org.hamcrest.Matcher<View>): org.hamcrest.Matcher<View> =
        org.hamcrest.Matchers.allOf(
            matcher,
            androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA(
                org.hamcrest.Matchers.allOf(
                    androidx.test.espresso.matcher.ViewMatchers.withId(R.id.charts_tab_chart_root),
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed(),
                ),
            ),
        )

    @Before fun setUp() {
        hiltRule.inject()
        runBlocking {
            chargeEventDao.deleteAll()
            carDao.deleteAll()
        }
    }

    @Test fun tabSwitch_showsCorrectChart() = runBlocking {
        // Seed a shape that produces a different visible signal per tab so the
        // assertions can distinguish them via Espresso (MPAndroidChart legends and
        // axis labels are canvas-drawn and are NOT visible to Espresso). We use:
        //  - 2 AC events spanning two months, mono-currency EUR-costed → Trend &
        //    Monthly kWh & Monthly cost have data (chart container populated;
        //    per-tab empty message GONE)
        //  - 1 DC event → AC/DC tab has data → subtitle visible with kWh substring
        //  - All location fields null → Locations tab → empty message visible with
        //    "No location data..."
        seedDataStore()
        val now = System.currentTimeMillis()
        val d = 24L * 60 * 60 * 1000
        seedDb(
            listOf(
                ev(now - 60 * d, 0.0, ChargeType.AC, cost = 5.0, currency = "EUR"),
                ev(now - 30 * d, 100.0, ChargeType.AC, cost = 7.5, currency = "EUR"),
                ev(now - 5 * d, 200.0, ChargeType.DC_FAST, cost = 4.0, currency = "EUR"),
            ),
        )
        launchFragmentInHiltContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Default tab is TREND. Empty message is GONE → chart populated.
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // MONTHLY_KWH: same — chart populated.
                onView(withText(R.string.charts_tab_monthly_kwh)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // MONTHLY_COST: mono-currency EUR data → chart populated.
                onView(withText(R.string.charts_tab_monthly_cost)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // AC_DC: subtitle is visible with a "kWh" substring.
                onView(withText(R.string.charts_tab_ac_dc)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_subtitle)))
                    .check(matches(isDisplayed()))
                    .check(matches(withSubstring("kWh")))

                // LOCATIONS: no location data → empty message shows the locations string.
                onView(withText(R.string.charts_tab_locations)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.charts_no_locations_period)))
            }
    }

    @Test fun noData_emptyState_perPeriod() = runBlocking {
        seedDataStore()
        val twoYearsAgo = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
        seedDb(listOf(ev(twoYearsAgo, 0.0)))
        launchFragmentInHiltContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Period chips and tab layout still visible
                onView(withId(R.id.charts_period_chips)).check(matches(isDisplayed()))
                onView(withId(R.id.charts_tab_layout)).check(matches(isDisplayed()))
                // Tab body shows the per-period empty message. Scope to the
                // active page — every offscreen page also shows this string,
                // so a bare withText would be ambiguous.
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.charts_no_data_period)))
            }
    }

    @Test fun noCar_showsAddCarCta_andNavigates() = runBlocking {
        seedDataStore(activeCarId = -1)
        seedDb(emptyList(), withCar = false)
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setGraph(R.navigation.nav_graph)
            setCurrentDestination(R.id.chartsFragment)
        }
        launchFragmentInHiltContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED)
            .onFragment { Navigation.setViewNavController(it.requireView(), nav) }

        onView(withId(R.id.charts_empty_container)).check(matches(isDisplayed()))
        onView(withId(R.id.charts_empty_cta)).perform(click())

        assertEquals(R.id.carsFragment, nav.currentDestination?.id)
    }

    @Test fun noEvents_showsLogChargeCta_andNavigates() = runBlocking {
        seedDataStore(activeCarId = 1)
        seedDb(emptyList(), withCar = true)
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            setGraph(R.navigation.nav_graph)
            setCurrentDestination(R.id.chartsFragment)
        }
        launchFragmentInHiltContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED)
            .onFragment { Navigation.setViewNavController(it.requireView(), nav) }

        onView(withId(R.id.charts_empty_container)).check(matches(isDisplayed()))
        onView(withId(R.id.charts_empty_cta)).perform(click())

        assertEquals(R.id.chargeEditFragment, nav.currentDestination?.id)
    }

    @Test fun multiCurrencyPeriod_costTabShowsBanner_locally() = runBlocking {
        // Spec §6.3: when mixedCurrency is true, the *Monthly cost* tab body is
        // replaced by the multi_currency_banner string. The four other tabs
        // render normally — there is intentionally no screen-global banner.
        seedDataStore()
        val now = System.currentTimeMillis()
        val d = 24L * 60 * 60 * 1000
        seedDb(
            listOf(
                ev(now - 60 * d, 0.0, ChargeType.AC, cost = 5.0, currency = "EUR"),
                ev(now - 30 * d, 100.0, ChargeType.AC, cost = 7.5, currency = "USD"),
            ),
        )
        launchFragmentInHiltContainer<ChartsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Default TREND tab does NOT show the multi-currency banner string.
                onView(inActivePage(withId(R.id.charts_tab_empty_message))).check(matches(not(isDisplayed())))

                // Click MONTHLY_COST tab → tab-body empty TextView shows the banner.
                onView(withText(R.string.charts_tab_monthly_cost)).perform(click())
                onView(inActivePage(withId(R.id.charts_tab_empty_message)))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(R.string.multi_currency_banner)))
            }
    }
}
