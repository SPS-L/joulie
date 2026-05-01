package org.spsl.evtracker.ui.wizard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.spsl.evtracker.data.repository.SettingsRepository

class WizardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository
    private lateinit var vm: WizardViewModel

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
        vm = WizardViewModel(repo)
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
