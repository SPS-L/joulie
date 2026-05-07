package org.spsl.evtracker

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.data.local.db.AppDatabase
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.repository.SettingsRepository
import org.spsl.evtracker.di.DataResetModule
import org.spsl.evtracker.domain.repository.DataResetTransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `@UninstallModules(DataResetModule::class)` removes the production
 * `@Binds DataResetTransactionRunner` from the Hilt graph for this
 * test class. The local `TestResetModule` re-binds the interface to a
 * `Singleton` `TestableResetRunner` so `ResetAllDataUseCase` injects
 * the spy. The test's own `@Inject testRunner` field receives the same
 * singleton so assertions on `clearCalls` / `failNext` see the same
 * instance the use case mutated.
 *
 * Note: `@BindValue val testRunner: TestableResetRunner` does NOT work
 * here — that binds the concrete class, not the interface, so the use
 * case keeps resolving the production `RoomDataResetTransactionRunner`
 * via [DataResetModule]. Binding to the interface via `@BindValue`
 * conflicts with `DataResetModule`'s own binding (parallel, not
 * replacement), which is why [DataResetModule] is extracted to its own
 * focused module so this test can `@UninstallModules` it cleanly.
 */
@HiltAndroidTest
@UninstallModules(DataResetModule::class)
@RunWith(AndroidJUnit4::class)
class MainActivityResetRecoveryTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var testRunner: TestableResetRunner

    @Singleton
    class TestableResetRunner @Inject constructor() : DataResetTransactionRunner {
        @Volatile var failNext: Throwable? = null

        @Volatile var realDelegate: DataResetTransactionRunner? = null

        @Volatile var clearCalls: Int = 0
        override suspend fun clearAllTables() {
            clearCalls++
            failNext?.let {
                failNext = null
                throw it
            }
            realDelegate?.clearAllTables()
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class TestResetModule {
        @Binds
        @Singleton
        abstract fun bindRunner(impl: TestableResetRunner): DataResetTransactionRunner
    }

    @Before fun setUp() {
        hiltRule.inject()
        // Wire the real Room delegate so the success path actually clears tables.
        testRunner.realDelegate =
            org.spsl.evtracker.data.repository.RoomDataResetTransactionRunner(database)
    }

    /** Polls `MainActivity.isNavGraphMounted` until true. Avoids fixed Thread.sleep. */
    private fun ActivityScenario<MainActivity>.awaitNavMounted(timeoutMs: Long = 10_000) = runBlocking {
        withTimeout(timeoutMs) {
            while (true) {
                var mounted = false
                onActivity { mounted = it.isNavGraphMounted() }
                if (mounted) return@withTimeout
                delay(100)
            }
        }
    }

    @Test fun startup_resetInProgressTrue_runsUseCase_clearsFlag_beforeUiVisible() = runBlocking {
        val carId = database.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0, createdAt = 0L),
        )
        settingsRepository.setResetInProgress(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            withTimeout(10_000) {
                settingsRepository.resetInProgress.first { !it }
            }
            scenario.awaitNavMounted()
        }

        assertEquals(null, database.carDao().getById(carId))
        assertFalse(settingsRepository.resetInProgress.first())
        assertEquals(1, testRunner.clearCalls)
    }

    @Test fun startup_resetInProgressFalse_doesNotRunUseCase() = runBlocking {
        val carId = database.carDao().insert(
            CarEntity(name = "Test", make = "M", model = "X", year = 2024, batteryKwh = 75.0, createdAt = 0L),
        )
        settingsRepository.setResetInProgress(false)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.awaitNavMounted()
        }

        assertEquals(0, testRunner.clearCalls)
        val car = database.carDao().getById(carId)
        assertNotNull(car)
        assertEquals("Test", car!!.name)
    }

    @Test fun startup_resetRecoveryThrows_showsRetryDialog_doesNotMountNavGraph() = runBlocking {
        testRunner.failNext = IllegalStateException("simulated room failure")
        settingsRepository.setResetInProgress(true)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withText(R.string.recovery_failure_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertFalse(
                    "nav graph must not be mounted while recovery dialog is showing",
                    activity.isNavGraphMounted(),
                )
            }
        }

        assertTrue(
            "resetInProgress must remain true when recovery throws",
            settingsRepository.resetInProgress.first(),
        )
    }
}
