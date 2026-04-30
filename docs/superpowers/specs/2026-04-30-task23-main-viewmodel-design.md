# TASK-23 — Move startup `isLoading` state into `MainViewModel`

**Date:** 2026-04-30
**Backlog item:** [BACKLOG.md TASK-23](../../BACKLOG.md) (🔴 high priority)
**Type:** Refactor / lifecycle correctness
**Risk:** Medium — touches the splash gate and the auto-recovery dialog flow; an existing instrumented test (`MainActivityResetRecoveryTest`) covers the contract end-to-end.

---

## 1. Context

`MainActivity.kt` currently owns three Activity-scoped fields that should belong to a `ViewModel`:

```kotlin
@Inject lateinit var settingsRepository: SettingsRepository       // line 26
@Inject lateinit var resetAllDataUseCase: ResetAllDataUseCase     // line 28
private val isLoading = MutableStateFlow(true)                    // line 30
```

The `startupSequence()` coroutine (lines 61–77) reads `settingsRepository.resetInProgress`, optionally invokes `resetAllDataUseCase()`, then mounts the navigation graph and flips `isLoading` to false. The splash screen is gated on `isLoading.value` via `splash.setKeepOnScreenCondition`.

On every configuration change (rotation, locale, dark/light theme switch), the Activity is recreated and `isLoading` resets to `true`. `startupSequence()` then **runs again**:

- The `ResetAllDataUseCase` is re-invoked if the flag is still true (it would be, if recovery is currently mid-flight).
- The splash flickers in for the duration of the second run.
- The recovery-failure dialog is dismissed and recreated.

This is incorrect: startup is a process-lifecycle concern, not an Activity-lifecycle concern. The fix is the standard Android Architecture pattern: lift the state into a `@HiltViewModel` whose lifetime is the Activity's `ViewModelStore` (survives configuration changes) and observe it from the Activity in a `repeatOnLifecycle(STARTED)` block.

**Consumers (verified via `grep -rn "data\\.repository\\.SettingsRepository"`):**

| File | Role |
|------|------|
| `data/repository/SettingsRepository.kt` | Implementation |
| `di/DomainModule.kt` | `@Binds` to `SettingsReader` and `SettingsWriter` |
| `EVTrackerApp.kt` | TASK-24 target (out of scope here) |
| `MainActivity.kt` | **Removed by this task** |
| `ui/wizard/WizardViewModel.kt` | TASK-24 target (out of scope here) |

Only two `SettingsReader` implementers exist (verified via `grep -rn ": SettingsReader"`):

1. `data/repository/SettingsRepository.kt` (production)
2. `app/src/test/.../testing/Fakes.kt` `FakeSettingsReader` (test)

## 2. Problem

Three concrete defects on `main`:

1. **Re-running startup on rotation.** `isLoading` and `startupSequence()` are Activity-scoped. Any configuration change retriggers them.
2. **Splash flicker on rotation.** Each new Activity instance starts at `isLoading = true`, so the splash re-shows for one frame even when startup has already completed.
3. **Recovery dialog identity loss.** The dialog is a `MaterialAlertDialogBuilder().show()` tied to the dying Activity. After rotation the new Activity has no record that recovery already failed; whether it re-runs and re-fails or accidentally proceeds depends on the timing of `startupSequence()` vs. the configuration change.

## 3. Decision

Introduce `app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt`, a `@HiltViewModel` that:

- Owns a `MutableStateFlow<StartupState>` with three states: `Loading`, `Ready(setupComplete: Boolean)`, `RecoveryFailed(cause: Throwable?)`.
- Runs the startup sequence once in `init { runStartupSequence() }`.
- Exposes a public `runStartupSequence()` that the Activity calls from the recovery-dialog "Retry" button.
- Depends on the **narrow domain interface** `SettingsReader` (not the concrete `SettingsRepository`) and on `ResetAllDataUseCase` (already a domain use case).

`MainActivity` becomes a thin presenter:

- Injects `MainViewModel` via `viewModels()`.
- Splash gate becomes `splash.setKeepOnScreenCondition { mainViewModel.startupState.value is StartupState.Loading }`.
- A `repeatOnLifecycle(STARTED)` collector on `mainViewModel.startupState` switches on the sealed variant and routes to either `mountNavGraph(setupComplete)` or `showRecoveryFailureDialog(cause)`.
- `isNavGraphMounted()` — the existing `@VisibleForTesting` hook — keeps its semantics by tracking a local `private var navMounted: Boolean` set immediately after `navController.graph = navGraph`. (We do not use `state is Ready` directly because the test polls in real time and we want a strict happens-after-mount signal.)

### 3.1 Why depend on `SettingsReader`, not `SettingsRepository`

The TASK-23 backlog text shows `SettingsRepository` in the constructor with a parenthetical: *"(Once TASK-24 lands, swap the constructor to `SettingsReader`.)"*. We choose to **do that swap as part of TASK-23** for two reasons:

1. **`SettingsReader` is missing `setupComplete`.** Every other settings flag in the production code (`primaryMetric`, `distanceUnit`, `currency`, `driveEnabled`, `lastBackupAt`, `theme`, `resetInProgress`) is already on the interface. `setupComplete` is a public `val` on the concrete class only — that is an oversight, not a deliberate choice. Fixing it as a one-line interface addition is in-scope for any task that touches startup-gate semantics.
2. **Project test infrastructure is already organised around the narrow IF.** The existing `FakeSettingsReader` lets `MainViewModelTest` follow the established "real domain wired through fakes" pattern with zero new test infrastructure. Depending on the concrete `SettingsRepository` instead would force either Robolectric or a tempfile-backed `DataStore<Preferences>` for what should be a plain JVM unit test.

This does **not** preempt TASK-24. TASK-24's remaining audit targets are unchanged: `EVTrackerApp.kt` and `WizardViewModel.kt`. Only the `MainActivity` row goes away naturally, because TASK-23 rewrites that file anyway.

### 3.2 Why a sealed class with three states

The original `Boolean isLoading` collapses two distinct "splash visible" cases:
- "Startup is still running" (transient, will resolve to a UI mount)
- "Recovery failed; the activity is showing a blocking dialog with no nav graph" (terminal until retry)

The Activity needs to distinguish these to render correctly. A sealed class makes the distinction type-safe and makes the test hook `isNavGraphMounted()` precise.

### 3.3 Rejected alternatives

| Option | Rejected because |
|--------|------------------|
| Keep state in Activity, suppress re-run with a `savedInstanceState` boolean | Doesn't survive process death cleanly; doesn't fix dialog identity. |
| Use a `@Singleton` "startup coordinator" injected by Hilt | Singleton lifetime ≠ Activity-VM lifetime; wrong scope and fights `viewModelScope` cancellation. |
| Take `SettingsRepository` directly per literal backlog text | See §3.1 — forces test-side workarounds for an interface oversight. |
| Add a fourth `Initial` state that distinguishes "VM constructed but `init` hasn't run yet" | `init` runs synchronously before any caller can observe; no observable difference from `Loading`. |

## 4. File Changes

### 4.1 New file — `app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt`

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
                    runCatching { resetAllDataUseCase() }
                        .onFailure {
                            android.util.Log.e("MainViewModel", "Reset auto-recovery failed", it)
                            _startupState.value = StartupState.RecoveryFailed(it)
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

### 4.2 Modify — `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsReader.kt`

Add `setupComplete` to the narrow IF:

```kotlin
interface SettingsReader {
    // … existing fields …

    /**
     * F1 wizard gate. False until the wizard's finish step writes primaryMetric,
     * distanceUnit, currency together with this flag. Settings → Reset preferences
     * sets it back to false.
     */
    val setupComplete: Flow<Boolean>
}
```

### 4.3 Modify — `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt`

Add `override` to the existing field:

```kotlin
override val setupComplete: Flow<Boolean> =
    dataStore.data.map { it[PreferenceKeys.SETUP_COMPLETE] ?: false }
```

### 4.4 Modify — `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`

Extend `FakeSettingsReader`:

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
    setupCompleteInit: Boolean = true,             // NEW
) : SettingsReader {
    // … existing flows …
    private val setupCompleteFlow = MutableStateFlow(setupCompleteInit)   // NEW
    override val setupComplete: Flow<Boolean> = setupCompleteFlow         // NEW
    // … existing setters …
    fun setSetupComplete(value: Boolean) { setupCompleteFlow.value = value }   // NEW
}
```

The constructor parameter has a default (`true`) so all existing test sites keep compiling without modification.

### 4.5 Modify — `app/src/main/java/org/spsl/evtracker/MainActivity.kt`

Final shape:

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
                        is StartupState.Loading -> Unit  // splash stays
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

    @VisibleForTesting
    fun isNavGraphMounted(): Boolean = navMounted
}
```

Notes:

- `mountNavGraph` becomes synchronous — it no longer reads `setupComplete` from a Flow. The VM hands the resolved `Boolean` directly through `Ready(setupComplete)`.
- The `if (!navMounted)` guard makes mounting idempotent across re-emissions and across `repeatOnLifecycle` re-collections (e.g., after `STARTED → STOPPED → STARTED`).
- `splash.setKeepOnScreenCondition` reads `mainViewModel.startupState.value` synchronously — it must NOT depend on `navMounted` directly, because the splash is queried by the system before our `STARTED`-phase collector runs.
- Recovery dialog: the splash is **not** explicitly dismissed because once the VM emits `RecoveryFailed`, `state.value is Loading` becomes false and `setKeepOnScreenCondition` returns false; the splash dismisses on the next system poll.

### 4.6 New file — `app/src/test/java/org/spsl/evtracker/ui/MainViewModelTest.kt`

JVM unit tests using `kotlinx-coroutines-test` `UnconfinedTestDispatcher`. Test rig wires a real `ResetAllDataUseCase` over the existing `FakeDataResetTransactionRunner` / `FakeSettingsWriter` / `FakeBackupScheduler` (mirroring `ResetAllDataUseCaseTest`). State is collected via a launched job (per `events` collection convention in CLAUDE.md).

Test cases:

| Case | Initial flags | Expected emissions | Expected side effects |
|------|---------------|--------------------|------------------------|
| 1. No recovery, setup complete | `resetInProgress=false`, `setupComplete=true` | `Loading → Ready(true)` | `runner.clearCallCount == 0` |
| 2. No recovery, setup incomplete | `resetInProgress=false`, `setupComplete=false` | `Loading → Ready(false)` | `runner.clearCallCount == 0` |
| 3. Recovery success | `resetInProgress=true`, `setupComplete=true` | `Loading → Ready(...)` | `runner.clearCallCount == 1`, `settingsWriter.resetInProgress == false` |
| 4. Recovery failure | `resetInProgress=true`, `failNext=…` | `Loading → RecoveryFailed(cause)` | `settingsWriter.resetInProgress == true` (flag stays for next launch) |
| 5. Retry after failure | start with case 4, clear `failNext`, call `runStartupSequence()` | `RecoveryFailed → Loading → Ready(...)` | `runner.clearCallCount == 2` |
| 6. Retry while in-flight is no-op | call `runStartupSequence()` twice without advancing the dispatcher | only one launch starts | `runner.clearCallCount == 1` after `runCurrent()` |

The dispatcher rule pattern: use `Dispatchers.setMain(UnconfinedTestDispatcher())` in `@Before`/`@After` (already used by other ViewModel tests in the project — verify against `ChargeEditViewModelTest.kt` if needed).

### 4.7 Backlog tick

In `docs/BACKLOG.md`, change the TASK-23 row's `☐` to `☑` and add a "Done (2026-04-30)" outcome paragraph at the head of the §TASK-23 section pointing to this spec and to the implementation plan.

## 5. Out of Scope

The following are explicitly **not** part of this task and must not be bundled in:

- Refactoring `EVTrackerApp.kt` or `WizardViewModel.kt` to depend on `SettingsReader`/`SettingsWriter` instead of `SettingsRepository`. **TASK-24** owns those.
- The `hideOn` `setOf(R.id.wizardFragment, …)` decoupling. **TASK-27** owns it.
- Replacing `System.currentTimeMillis()` calls with `NowProvider`. **TASK-28** owns it.
- Bumping `compileSdk` / `targetSdk`. **TASK-22** owns it.
- Touching the splash drawable, theme, or resource identifiers.
- Adding a "splash min duration" UX (e.g., to avoid sub-100ms flicker).

## 6. Acceptance Criteria

The change is complete when **all** of the following hold:

1. `./gradlew ktlintCheck` passes.
2. `./gradlew :app:lint` passes (no new lint-baseline drift).
3. `./gradlew :app:testDebugUnitTest` succeeds, with **at least 6 new tests** added in `MainViewModelTest.kt`. Total JVM test count is `~236 + 6 ≈ 242` (no regression in any existing test).
4. `./gradlew :app:assembleDebug` succeeds.
5. `./gradlew :app:assembleDebugAndroidTest` compiles (running requires an emulator and is not gated on this task; the existing `MainActivityResetRecoveryTest` is preserved verbatim — only its mechanism changes from `isLoading` to the VM-backed `navMounted`).
6. `./gradlew :app:assembleRelease` succeeds.
7. `git grep "isLoading" app/src/main/java/org/spsl/evtracker/MainActivity.kt` returns zero matches.
8. `git grep "@Inject lateinit var settingsRepository" app/src/main/java/org/spsl/evtracker/MainActivity.kt` returns zero matches.
9. `git grep "data\\.repository\\.SettingsRepository" app/src/main/java/org/spsl/evtracker/MainActivity.kt` returns zero matches.
10. `BACKLOG.md` TASK-23 row shows `☑` and the §TASK-23 section has a Done paragraph at the top.

## 7. Implementation Sequence (preview for the plan)

1. Branch `feat/task-23-main-viewmodel` off `main`.
2. Add `setupComplete: Flow<Boolean>` to `SettingsReader`; `override` in `SettingsRepository`; extend `FakeSettingsReader`. Verify `:app:assembleDebug` and `:app:testDebugUnitTest` still pass.
3. Write `MainViewModelTest` first (TDD): all 6 cases red against absent `MainViewModel`.
4. Create `MainViewModel.kt`. Run JVM tests; iterate until green.
5. Rewrite `MainActivity.kt`. Verify `:app:assembleDebug`, `:app:assembleDebugAndroidTest`.
6. Run full CI gate: `ktlintCheck`, `:app:lint`, `:app:testDebugUnitTest`, `:app:assembleRelease`.
7. Tick BACKLOG.md TASK-23.
8. Commit (one commit per logical step), merge `--no-ff`, push, delete branch.

## 8. Open Questions

None. The instrumented test contract is preserved; the JVM test infrastructure already supports the rig pattern; only two `SettingsReader` implementers exist, both updated atomically.
