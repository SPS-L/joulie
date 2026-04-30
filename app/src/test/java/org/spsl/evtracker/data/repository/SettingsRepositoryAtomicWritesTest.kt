package org.spsl.evtracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.spsl.evtracker.data.preferences.PreferenceKeys

class SettingsRepositoryAtomicWritesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("atomic-writes-test.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
    }

    @Test fun setPrimaryMetricAndDistanceUnit_writesBothKeys() = runTest {
        repo.setPrimaryMetricAndDistanceUnit("mi_per_kwh", "miles")
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
    }

    @Test fun markGlobalResetInProgress_writesAllThreeKeys() = runTest {
        repo.completeSetup("km_per_kwh", "km", "EUR") // setupComplete=true
        repo.setActiveCarId(7)
        // Sanity:
        val data = dataStore.data.first()
        assertTrue(data[PreferenceKeys.SETUP_COMPLETE]!!)
        assertEquals(7, data[PreferenceKeys.ACTIVE_CAR_ID])

        repo.markGlobalResetInProgress()

        val after = dataStore.data.first()
        assertFalse(after[PreferenceKeys.SETUP_COMPLETE]!!)
        assertEquals(-1, after[PreferenceKeys.ACTIVE_CAR_ID])
        assertTrue(after[PreferenceKeys.RESET_IN_PROGRESS]!!)
    }

    @Test fun resetInProgress_defaultsToFalse_whenKeyAbsent() = runTest {
        assertFalse(repo.resetInProgress.first())
    }

    @Test fun setResetInProgress_canBeToggledTrueThenFalse() = runTest {
        repo.setResetInProgress(true)
        assertTrue(repo.resetInProgress.first())
        repo.setResetInProgress(false)
        assertFalse(repo.resetInProgress.first())
    }
}
