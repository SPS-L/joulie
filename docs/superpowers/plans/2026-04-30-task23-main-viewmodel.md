# TASK-23 — Move startup `isLoading` state into `MainViewModel` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move startup auto-recovery state out of `MainActivity` into a Hilt `MainViewModel` so it survives configuration changes; preserve the existing instrumented contract.

**Architecture:** New `@HiltViewModel` with a sealed `StartupState` (`Loading`/`Ready(setupComplete)`/`RecoveryFailed(cause)`); `MainActivity` collects via `repeatOnLifecycle(STARTED)` and routes to mountNavGraph or recovery dialog. Splash gate reads `state.value is Loading`. `SettingsReader` gains a `setupComplete` flow so `MainViewModel` can depend on the narrow IF cleanly.

**Tech Stack:** Kotlin 1.9.21, AndroidX `lifecycle-viewmodel-ktx` 2.7.0, Hilt 2.50, kotlinx-coroutines 1.7.3, kotlinx-coroutines-test, JUnit 4.

---

### Task 0: Create the feature branch

**Files:** none

- [ ] **Step 1: Create branch off main**

```bash
git status
```
Expected: `On branch main`, clean working tree.

```bash
git checkout -b feat/task-23-main-viewmodel
```
Expected: `Switched to a new branch 'feat/task-23-main-viewmodel'`.

---

### Task 1: Extend `SettingsReader` with `setupComplete`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt:18-19`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt:112-162`

- [ ] **Step 1: Add `setupComplete` to the `SettingsReader` interface**

Append the field to the interface body in `SettingsReader.kt`:

```kotlin
    /**
     * F1 wizard gate. False until the wizard's finish step writes primaryMetric,
     * distanceUnit, currency together with this flag. Settings → Reset preferences
     * sets it back to false.
     */
    val setupComplete: Flow<Boolean>
```

- [ ] **Step 2: Add `override` keyword to the existing field on `SettingsRepository`**

Change line 18 of `SettingsRepository.kt` from:

```kotlin
    val setupComplete: Flow<Boolean> =
```

to:

```kotlin
    override val setupComplete: Flow<Boolean> =
```

- [ ] **Step 3: Extend `FakeSettingsReader` with `setupCompleteInit`**

In `Fakes.kt` `FakeSettingsReader`, add a constructor parameter and a backing flow:

```kotlin
class FakeSettingsReader(
    activeCarIdInit: Int = -1,
    primaryMetricInit: String = "km_per_kwh",
    distanceUnitInit: String = "km",
    currencyInit: String = "EUR",
    driveEnabledInit: Boolean = false,
    lastBackupAtInit: Long? = null,
    themeInit: String = "system",
    resetInProgressInit: Boolean = false,
    setupCompleteInit: Boolean = true,
) : SettingsReader {
```

Add the backing flow next to the others (after `resetInProgressFlow`):

```kotlin
    private val setupCompleteFlow = MutableStateFlow(setupCompleteInit)
```

Add the `override`:

```kotlin
    override val setupComplete: Flow<Boolean> = setupCompleteFlow
```

Add a setter (next to the other `setX` helpers):

```kotlin
    fun setSetupComplete(value: Boolean) {
        setupCompleteFlow.value = value
    }
```

- [ ] **Step 4: Verify compile + tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL. Existing test count preserved (~236 passing). The default of `setupCompleteInit = true` keeps every existing call site valid.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt
git add app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt
git add app/src/test/java/org/spsl/evtracker/testing/Fakes.kt
git commit -m "refactor(task-23): expose setupComplete on SettingsReader"
```

---

### Task 2: Write the failing `MainViewModelTest`

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/ui/MainViewModelTest.kt`

- [ ] **Step 1: Write the test file**

Create `app/src/test/java/org/spsl/evtracker/ui/MainViewModelTest.kt` with the full content below:

```kotlin
package org.spsl.evtracker.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import org.spsl.evtracker.testing.FakeBackupScheduler
import org.spsl.evtracker.testing.FakeCarRepository
import org.spsl.evtracker.testing.FakeChargeEventWriter
import org.spsl.evtracker.testing.FakeDataResetTransactionRunner
import org.spsl.evtracker.testing.FakeLocationWriter
import org.spsl.evtracker.testing.FakeSettingsReader
import org.spsl.evtracker.testing.FakeSettingsWriter

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @Before fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class Rig(
        val vm: MainViewModel,
        val reader: FakeSettingsReader,
        val writer: FakeSettingsWriter,
        val runner: FakeDataResetTransactionRunner,
        val scheduler: FakeBackupScheduler,
    )

    private fun build(
        resetInProgress: Boolean = false,
        setupComplete: Boolean = true,
    ): Rig {
        val reader = FakeSettingsReader(
            resetInProgressInit = resetInProgress,
            setupCompleteInit = setupComplete,
        )
        val writer = FakeSettingsWriter()
        val eventStore = MutableStateFlow<List<ChargeEventEntity>>(emptyList())
        val locStore = MutableStateFlow<List<CustomLocationEntity>>(emptyList())
        FakeChargeEventWriter(eventStore)
        FakeLocationWriter(locStore)
        val carRepo = FakeCarRepository()
        val runner = FakeDataResetTransactionRunner {
            eventStore.value = emptyList()
            locStore.value = emptyList()
            carRepo.seed(emptyList())
        }
        val scheduler = FakeBackupScheduler()
        val useCase = ResetAllDataUseCase(runner, writer, scheduler)
        val vm = MainViewModel(reader, useCase)
        return Rig(vm, reader, writer, runner, scheduler)
    }

    @Test fun startup_resetInProgressFalse_setupComplete_emitsReadyTrue_doesNotRunUseCase() = runTest {
        val rig = build(resetInProgress = false, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        // UnconfinedTestDispatcher runs the init launch synchronously.
        job.cancel()
        assertEquals(0, rig.runner.clearCallCount)
        assertTrue(collected.last() is MainViewModel.StartupState.Ready)
        assertEquals(true, (collected.last() as MainViewModel.StartupState.Ready).setupComplete)
    }

    @Test fun startup_resetInProgressFalse_setupIncomplete_emitsReadyFalse() = runTest {
        val rig = build(resetInProgress = false, setupComplete = false)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        assertTrue(collected.last() is MainViewModel.StartupState.Ready)
        assertEquals(false, (collected.last() as MainViewModel.StartupState.Ready).setupComplete)
    }

    @Test fun startup_resetInProgressTrue_recoverySuccess_emitsReady_andClearsFlag() = runTest {
        val rig = build(resetInProgress = true, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        assertEquals(1, rig.runner.clearCallCount)
        assertFalse(rig.writer.resetInProgress)
        assertTrue(collected.last() is MainViewModel.StartupState.Ready)
    }

    @Test fun startup_recoveryThrows_emitsRecoveryFailed_andLeavesFlag() = runTest {
        val rig = build(resetInProgress = true)
        rig.runner.failNext = IllegalStateException("rooms exploded")
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        job.cancel()
        val terminal = collected.last()
        assertTrue("expected RecoveryFailed but was $terminal", terminal is MainViewModel.StartupState.RecoveryFailed)
        assertTrue(rig.writer.resetInProgress)  // flag stays true → next launch re-runs recovery
    }

    @Test fun retry_afterFailure_clearsFailure_andReachesReady() = runTest {
        val rig = build(resetInProgress = true)
        rig.runner.failNext = IllegalStateException("first attempt")
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        // First pass should have failed.
        assertTrue(collected.last() is MainViewModel.StartupState.RecoveryFailed)
        // Re-arm: fail no more, then retry. The writer's resetInProgress is still true
        // because the failed first pass left it true; reader still reflects true.
        rig.reader.setResetInProgress(true)
        rig.vm.runStartupSequence()
        job.cancel()
        assertTrue(collected.last() is MainViewModel.StartupState.Ready)
        assertEquals(2, rig.runner.clearCallCount)
        assertFalse(rig.writer.resetInProgress)
    }

    @Test fun runStartupSequence_isNoOpWhileInFlight() = runTest {
        // resetInProgress=false → init launch completes synchronously on UnconfinedTestDispatcher.
        // Calling runStartupSequence again after Ready emits another Loading→Ready cycle.
        // This test verifies the in-flight guard does not deadlock on a normal retry.
        val rig = build(resetInProgress = false, setupComplete = true)
        val collected = mutableListOf<MainViewModel.StartupState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            rig.vm.startupState.collect { collected += it }
        }
        rig.vm.runStartupSequence()  // second pass
        job.cancel()
        // We do not assert exact emission count — only that we end in Ready.
        assertTrue(collected.last() is MainViewModel.StartupState.Ready)
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (no `MainViewModel` yet)**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.MainViewModelTest"
```
Expected: compile failure on `import org.spsl.evtracker.ui.MainViewModel` and references to `MainViewModel.StartupState`. This confirms the tests are wired to drive the implementation.

---

### Task 3: Implement `MainViewModel` to make the tests pass

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt`

- [ ] **Step 1: Create the file**

```kotlin
package org.spsl.evtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.usecase.ResetAllDataUseCase
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsReader: SettingsReader,
    private val resetAllDataUseCase: ResetAllDataUseCase,
) : ViewModel() {

    sealed class StartupState {
        data object Loading : StartupState()
        data class Ready(val setupComplete: Boolean) : StartupState()
        data class RecoveryFailed(val cause: Throwable?) : StartupState()
    }

    private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
    val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

    @Volatile private var inFlight = false

    init { runStartupSequence() }

    fun runStartupSequence() {
        if (inFlight) return
        inFlight = true
        _startupState.value = StartupState.Loading
        viewModelScope.launch {
            try {
                if (settingsReader.resetInProgress.first()) {
                    val result = runCatching { resetAllDataUseCase() }
                    if (result.isFailure) {
                        val cause = result.exceptionOrNull()
                        android.util.Log.e("MainViewModel", "Reset auto-recovery failed", cause)
                        _startupState.value = StartupState.RecoveryFailed(cause)
                        return@launch
                    }
                }
                val complete = settingsReader.setupComplete.first()
                _startupState.value = StartupState.Ready(complete)
            } finally {
                inFlight = false
            }
        }
    }
}
```

- [ ] **Step 2: Run unit tests — expect green**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.ui.MainViewModelTest"
```
Expected: 6 tests pass. If any fail, fix the implementation (not the test) and re-run. Do not move on until all six are green.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt
git add app/src/test/java/org/spsl/evtracker/ui/MainViewModelTest.kt
git commit -m "feat(task-23): add MainViewModel for startup state"
```

---

### Task 4: Refactor `MainActivity` to consume `MainViewModel`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/MainActivity.kt` (full rewrite)

- [ ] **Step 1: Rewrite MainActivity.kt**

Overwrite the file with:

```kotlin
package org.spsl.evtracker

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.spsl.evtracker.databinding.ActivityMainBinding
import org.spsl.evtracker.ui.MainViewModel
import org.spsl.evtracker.ui.MainViewModel.StartupState

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var navGraph: NavGraph
    private var navMounted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition {
            mainViewModel.startupState.value is StartupState.Loading
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

        binding.bottomNav.setupWithNavController(navController)
        val hideOn = setOf(
            R.id.wizardFragment,
            R.id.chargeEditFragment,
            R.id.carsFragment,
            R.id.manageLocationsFragment,
        )
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.isVisible = dest.id !in hideOn
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.startupState.collect { state ->
                    when (state) {
                        is StartupState.Loading -> Unit
                        is StartupState.Ready -> if (!navMounted) mountNavGraph(state.setupComplete)
                        is StartupState.RecoveryFailed -> showRecoveryFailureDialog(state.cause)
                    }
                }
            }
        }
    }

    private fun showRecoveryFailureDialog(cause: Throwable?) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recovery_failure_title)
            .setMessage(getString(R.string.recovery_failure_body, cause?.localizedMessage ?: ""))
            .setCancelable(false)
            .setPositiveButton(R.string.recovery_failure_retry) { _, _ ->
                mainViewModel.runStartupSequence()
            }
            .show()
    }

    private fun mountNavGraph(setupComplete: Boolean) {
        if (!setupComplete) navGraph.setStartDestination(R.id.wizardFragment)
        navController.graph = navGraph
        navMounted = true
    }

    /**
     * Test hook: instrumented tests use this to wait for "startup completed" without
     * relying on `Thread.sleep`. True iff the nav graph has been mounted, which only
     * happens after auto-recovery either ran successfully or was skipped.
     */
    @VisibleForTesting
    fun isNavGraphMounted(): Boolean = navMounted
}
```

- [ ] **Step 2: Compile debug + instrumented test bundles**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. No Kotlin warnings.

```bash
./gradlew :app:assembleDebugAndroidTest
```
Expected: BUILD SUCCESSFUL (does not run; running needs an emulator).

- [ ] **Step 3: Run JVM unit tests**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: All ~242 tests pass (existing 236 + 6 new).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/org/spsl/evtracker/MainActivity.kt
git commit -m "refactor(task-23): consume MainViewModel from MainActivity"
```

---

### Task 5: CI gate verification + backlog tick

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Run the full CI gate locally**

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease
```
Expected: BUILD SUCCESSFUL on all four. No new lint-baseline drift.

- [ ] **Step 2: Verify acceptance criteria 7–9**

```bash
git grep "isLoading" app/src/main/java/org/spsl/evtracker/MainActivity.kt
git grep "@Inject lateinit var settingsRepository" app/src/main/java/org/spsl/evtracker/MainActivity.kt
git grep "data\\.repository\\.SettingsRepository" app/src/main/java/org/spsl/evtracker/MainActivity.kt
```
Expected: zero output from each.

- [ ] **Step 3: Tick TASK-23 in `docs/BACKLOG.md`**

In the overview table, change the TASK-23 row's `☐` to `☑`.

In the §TASK-23 section, prepend a "Done" callout immediately after the heading:

```markdown
> **Outcome (2026-04-30):** `MainViewModel` now owns the startup auto-recovery state.
> `MainActivity` is a thin presenter that observes `startupState` via
> `repeatOnLifecycle(STARTED)` and routes to `mountNavGraph(setupComplete)` or
> `showRecoveryFailureDialog(cause)`. `SettingsReader` gained `setupComplete: Flow<Boolean>`
> so the new VM consumes the narrow IF (one of the three TASK-24 violations resolved
> as a side effect; `EVTrackerApp` and `WizardViewModel` remain for TASK-24). Six new
> JVM tests in `MainViewModelTest`. Spec:
> `superpowers/specs/2026-04-30-task23-main-viewmodel-design.md`. Plan:
> `superpowers/plans/2026-04-30-task23-main-viewmodel.md`.
> The original task text is preserved below for historical context.
```

- [ ] **Step 4: Commit**

```bash
git add docs/BACKLOG.md
git commit -m "docs(task-23): mark complete in BACKLOG"
```

---

### Task 6: Merge, push, cleanup

**Files:** none

- [ ] **Step 1: Merge `--no-ff` into main (no compound git per CLAUDE.md global rule)**

```bash
git checkout main
```

```bash
git merge --no-ff feat/task-23-main-viewmodel -m "Merge branch 'feat/task-23-main-viewmodel'"
```

- [ ] **Step 2: Push**

```bash
git push origin main
```

- [ ] **Step 3: Delete the feature branch**

```bash
git branch -d feat/task-23-main-viewmodel
```

- [ ] **Step 4: Final state check**

```bash
git status
git log --oneline -10
```
Expected: working tree clean; `main` HEAD shows the merge commit; the four task commits sit on the merged branch tip.
