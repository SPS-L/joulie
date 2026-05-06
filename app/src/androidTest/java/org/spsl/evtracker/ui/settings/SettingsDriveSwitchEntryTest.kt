package org.spsl.evtracker.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.R
import org.spsl.evtracker.data.preferences.PreferenceKeys
import org.spsl.evtracker.di.BackupModule
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource
import org.spsl.evtracker.testing.launchFragmentInHiltContainer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TASK-54 — Step 0 regression guard.
 *
 * Bare entry into [SettingsFragment] with `DRIVE_ENABLED = true` in DataStore
 * must NOT invoke [DriveAuthManager.authorize]. Pre-fix, the switch's
 * `OnCheckedChangeListener` was attached in `onViewCreated` before Android's
 * view-state restoration ran `setChecked(true)` to restore the saved switch
 * state, which synchronously fired the listener → `onUserToggledOn` →
 * `auth.authorize()`. Post-fix (Option A: lazy listener attach in the
 * StateFlow collector), the first `isChecked` write happens with no listener
 * attached, so the restoration call is silent.
 *
 * Two assertions cover the two reproduction triggers:
 *  - first launch with persisted `driveEnabled=true`
 *  - `FragmentScenario.recreate()` (full activity recreation, the canonical
 *    "navigate away and back" simulation that exercises view-state save/restore)
 */
@HiltAndroidTest
@UninstallModules(BackupModule::class)
@RunWith(AndroidJUnit4::class)
class SettingsDriveSwitchEntryTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var dataStore: DataStore<Preferences>

    @Inject lateinit var fakeAuth: FakeDriveAuthManager

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class TestBackupModule {
        @Binds @Singleton
        abstract fun bindAuth(impl: FakeDriveAuthManager): DriveAuthManager

        @Binds @Singleton
        abstract fun bindRemote(impl: FakeDriveRemoteSource): DriveRemoteSource
    }

    @Before fun setUp() {
        hiltRule.inject()
        seedDataStoreDriveOn()
    }

    private fun seedDataStoreDriveOn() = runBlocking {
        dataStore.edit {
            it.clear()
            it[PreferenceKeys.SETUP_COMPLETE] = true
            it[PreferenceKeys.ACTIVE_CAR_ID] = -1
            it[PreferenceKeys.DISTANCE_UNIT] = "km"
            it[PreferenceKeys.CURRENCY] = "EUR"
            it[PreferenceKeys.PRIMARY_METRIC] = "km_per_kwh"
            it[PreferenceKeys.THEME] = "system"
            it[PreferenceKeys.DRIVE_ENABLED] = true
        }
    }

    @Test fun firstEntry_withDriveEnabled_doesNotCallAuthorize() {
        val before = fakeAuth.authorizeCallCount
        launchFragmentInHiltContainer<SettingsFragment>(themeResId = R.style.Theme_EVTracker)
            .moveToState(Lifecycle.State.RESUMED).use {
                // Settle one frame so the StateFlow collector has run.
                Thread.sleep(50)
                assertEquals(
                    "first entry must not invoke auth.authorize() — the switch listener " +
                        "must not fire on the initial state-driven sync",
                    before,
                    fakeAuth.authorizeCallCount,
                )
            }
    }

    @Test fun reEntry_viaActivityRecreation_doesNotCallAuthorize() {
        // FragmentScenario.recreate() destroys and recreates the host
        // activity, exercising the same view-state save/restore path that
        // navigating away and back through the back stack does. This is the
        // exact reproduction trigger for the user-reported "every Settings
        // entry the switch toggles OFF→ON and the prompt appears" bug.
        val scenario = launchFragmentInHiltContainer<SettingsFragment>(
            themeResId = R.style.Theme_EVTracker,
        ).moveToState(Lifecycle.State.RESUMED)
        Thread.sleep(50)
        val afterFirstEntry = fakeAuth.authorizeCallCount

        scenario.recreate()
        scenario.moveToState(Lifecycle.State.RESUMED)
        Thread.sleep(50)

        assertEquals(
            "activity recreation must not re-invoke auth.authorize() — view-state " +
                "restoration calls setChecked() which must NOT fire the switch listener",
            afterFirstEntry,
            fakeAuth.authorizeCallCount,
        )
    }
}
