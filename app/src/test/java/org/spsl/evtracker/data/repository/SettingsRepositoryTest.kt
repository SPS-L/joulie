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

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        repo = SettingsRepository(dataStore)
    }

    @Test
    fun defaults_areExpected() = runTest {
        assertFalse(repo.setupComplete.first())
        assertEquals("km_per_kwh", repo.primaryMetric.first())
        assertEquals("km", repo.distanceUnit.first())
        assertEquals("EUR", repo.currency.first())
        assertEquals("system", repo.theme.first())
    }

    @Test
    fun completeSetup_writesAllFourKeysAtomically() = runTest {
        repo.completeSetup(metric = "mi_per_kwh", unit = "miles", currency = "USD")
        assertEquals("mi_per_kwh", repo.primaryMetric.first())
        assertEquals("miles", repo.distanceUnit.first())
        assertEquals("USD", repo.currency.first())
        assertTrue(repo.setupComplete.first())
    }

    @Test
    fun setTheme_persists() = runTest {
        repo.setTheme("dark")
        assertEquals("dark", repo.theme.first())
    }

    @Test
    fun resetSetupComplete_flipsFlag_butLeavesOtherKeysAlone() = runTest {
        repo.completeSetup(metric = "kwh_per_100km", unit = "km", currency = "GBP")
        repo.resetSetupComplete()
        assertFalse(repo.setupComplete.first())
        assertEquals("kwh_per_100km", repo.primaryMetric.first())
        assertEquals("km", repo.distanceUnit.first())
        assertEquals("GBP", repo.currency.first())
    }

    @Test
    fun setActiveCarId_persistsAndDefaultsToMinusOne() = runTest {
        // Default when key has never been written.
        assertEquals(-1, repo.activeCarId.first())

        // Round-trip a non-default value.
        repo.setActiveCarId(42)
        assertEquals(42, repo.activeCarId.first())
    }

    @Test
    fun driveEnabled_defaultsFalse_andRoundTrips() = runTest {
        assertFalse(repo.driveEnabled.first())
        repo.setDriveEnabled(true)
        assertTrue(repo.driveEnabled.first())
        repo.setDriveEnabled(false)
        assertFalse(repo.driveEnabled.first())
    }

    @Test
    fun lastBackupAt_defaultsNull_andRoundTrips() = runTest {
        assertEquals(null, repo.lastBackupAt.first())
        repo.setLastBackupAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, repo.lastBackupAt.first())
    }

    @Test
    fun setLastBackupAt_doesNotAffectDriveEnabled() = runTest {
        repo.setLastBackupAt(42L)
        assertFalse(repo.driveEnabled.first())
        assertEquals(42L, repo.lastBackupAt.first())
    }
}
