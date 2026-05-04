package org.spsl.evtracker.ui.wizard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.testing.FakeLocaleApplier

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository
    private lateinit var localeApplier: FakeLocaleApplier
    private lateinit var vm: WizardViewModel

    @Before
    fun setUp() {
        // TASK-55: WizardViewModel.init now launches a viewModelScope
        // coroutine to collect SettingsReader.languageTag, so the Main
        // dispatcher must be set before the VM is constructed.
        Dispatchers.setMain(dispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(dispatcher),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
        localeApplier = FakeLocaleApplier()
        vm = WizardViewModel(repo, repo, localeApplier)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // TASK-55 — language picker (DataStore + LocaleApplier)
    // -------------------------------------------------------------------------

    @Test
    fun onLanguageSelected_persistsTagAndAppliesLocale() = runTest {
        vm.onLanguageSelected("el")
        assertEquals("el", repo.languageTag.first())
        assertEquals("el", localeApplier.lastAppliedTag)
        assertEquals(1, localeApplier.applyCallCount)
    }

    @Test
    fun onLanguageSelected_followSystem_writesEmptyString() = runTest {
        // First pick a non-default to verify the empty-string write replaces it.
        vm.onLanguageSelected("ru")
        assertEquals("ru", repo.languageTag.first())
        // "Follow system" round-trips as an empty string both in DataStore
        // and in the LocaleApplier call (LocaleListCompat.getEmptyLocaleList).
        vm.onLanguageSelected("")
        assertEquals("", repo.languageTag.first())
        assertEquals("", localeApplier.lastAppliedTag)
    }

    @Test
    fun onLanguageSelected_persistsTag_evenBeforeFinish() = runTest {
        // Mid-wizard kill survival: the tag must persist to DataStore
        // immediately, NOT wait for finish() to commit setupComplete.
        // Otherwise a user who picks Greek on page 0 then force-quits the
        // wizard would relaunch into the original system locale.
        vm.onLanguageSelected("tr")
        assertEquals("tr", repo.languageTag.first())
        // setupComplete is still false because finish() hasn't run.
        assertEquals(false, repo.setupComplete.first())
    }

    @Test
    fun finish_writesAllPrefs() = runTest {
        vm.selectMetric("mi_per_kwh") // forces unit to "miles"
        vm.selectCurrency("USD")
        vm.finish()
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
        assertEquals("USD", repo.currency.first())
        assertTrue(repo.setupComplete.first())
    }

    @Test
    fun coupling_miPerKwhForcesMiles() {
        vm.selectMetric("mi_per_kwh")
        assertEquals("miles", vm.state.value.unit)
    }

    @Test
    fun coupling_kmMetricForcesKm() {
        vm.selectMetric("mi_per_kwh") // unit = "miles"
        vm.selectMetric("km_per_kwh")
        assertEquals("km", vm.state.value.unit)
    }

    @Test
    fun coupling_kwhPer100kmForcesKm() {
        vm.selectMetric("kwh_per_100km")
        assertEquals("km", vm.state.value.unit)
    }

    @Test
    fun manualUnit_kmFlipsMetricFromMiles() {
        vm.selectMetric("mi_per_kwh")
        vm.selectUnit("km")
        assertEquals("km_per_kwh", vm.state.value.metric)
    }

    @Test
    fun manualUnit_milesFlipsKmMetric() {
        vm.selectMetric("km_per_kwh")
        vm.selectUnit("miles")
        assertEquals("mi_per_kwh", vm.state.value.metric)
    }

    @Test
    fun manualUnit_doesNotChangeKwhPer100km() {
        vm.selectMetric("kwh_per_100km")
        vm.selectUnit("miles")
        assertEquals("kwh_per_100km", vm.state.value.metric)
        assertEquals("miles", vm.state.value.unit)
    }

    @Test
    fun goNext_clampsAtPage3() {
        repeat(6) { vm.goNext() }
        assertEquals(3, vm.state.value.page)
    }

    @Test
    fun goBack_clampsAtPage0() {
        repeat(4) { vm.goBack() }
        assertEquals(0, vm.state.value.page)
    }

    @Test
    fun disclaimerAccepted_initialState_isFalse() {
        assertEquals(false, vm.state.value.disclaimerAccepted)
    }

    @Test
    fun setDisclaimerAccepted_togglesFlag() {
        vm.setDisclaimerAccepted(true)
        assertEquals(true, vm.state.value.disclaimerAccepted)
        vm.setDisclaimerAccepted(false)
        assertEquals(false, vm.state.value.disclaimerAccepted)
    }
}
