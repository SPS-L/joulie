# TASK-24 — Enforce narrow domain-interface consumption — Design

**Date:** 2026-05-01
**Branch:** `feat/task24-narrow-interface-enforcement`
**Sequenced after:** TASK-23 (which left only two violations)

## Problem

The architecture rule is: ViewModels, Activities, Fragments, and use cases depend
on `domain/repository/*` interfaces; concrete `data.repository.*` classes are
referenced only inside `di/` modules where Hilt binds them.

Two import violations remain on `main` after TASK-23:

```
EVTrackerApp.kt:12          import org.spsl.evtracker.data.repository.SettingsRepository
ui/wizard/WizardViewModel.kt:9  import org.spsl.evtracker.data.repository.SettingsRepository
```

Each `@Inject`s the concrete `SettingsRepository`. `MainActivity`'s former
violation was removed by TASK-23 when `MainViewModel` was introduced.

## Goal

Both files depend only on narrow `domain/repository/*` interfaces.
After the refactor, the only files matching
`^import org\.spsl\.evtracker\.data\.repository\.` should live under `di/`.

## Coverage gap

`WizardViewModel.finish()` calls `settingsRepository.completeSetup(metric, unit, currency)`
— this writes four DataStore keys (`primaryMetric`, `distanceUnit`, `currency`,
`setupComplete=true`) inside a single `dataStore.edit { ... }` block. The
narrow `SettingsWriter` interface does not expose `completeSetup`, only the
per-key setters. Sequencing four `set*` calls would lose the atomicity that the
wizard gate invariant depends on (DESIGN §3.4: a partially-applied wizard
finish must never leave `setupComplete=true` while the other three keys are
unset).

`SettingsWriter` already documents two analogous atomic operations:
`setPrimaryMetricAndDistanceUnit` and `markGlobalResetInProgress`. Adding
`completeSetup` follows the same pattern.

## Design

### 1. Extend `SettingsWriter`

Add to `domain/repository/SettingsWriter.kt`:

```kotlin
/**
 * Wizard finish: writes primaryMetric, distanceUnit, currency, and
 * setupComplete=true together inside a single dataStore.edit { ... } block.
 * Atomicity is required by the wizard gate invariant — a partially-applied
 * finish must never leave setupComplete=true while the other three keys
 * are unset.
 */
suspend fun completeSetup(metric: String, unit: String, currency: String)
```

`SettingsRepository.completeSetup` becomes an `override`. The body is
unchanged.

### 2. WizardViewModel swap

```kotlin
@HiltViewModel
class WizardViewModel @Inject constructor(
    private val settingsWriter: SettingsWriter,   // was: SettingsRepository
) : ViewModel() {
    ...
    suspend fun finish() {
        val s = state.value
        settingsWriter.completeSetup(s.metric, s.unit, s.currency)
    }
}
```

The wizard never reads settings — `SettingsWriter` alone is enough. No need
to inject `SettingsReader`.

### 3. EVTrackerApp swap

`EVTrackerApp` reads only `settingsRepository.theme.first()` for the
launch-time night-mode toggle. `SettingsReader.theme: Flow<String>` already
exists. Direct swap to `SettingsReader`.

### 4. Test coverage

- `WizardViewModelTest` constructs `vm = WizardViewModel(repo)` where `repo`
  is a real `SettingsRepository` over a `PreferenceDataStoreFactory` temp
  store. After the refactor, `vm = WizardViewModel(repo)` still type-checks
  because `SettingsRepository` implements `SettingsWriter`. The existing
  `finish_writesAllPrefs` test (which reads back via `repo.primaryMetric`
  etc.) keeps validating atomicity end-to-end.
- `FakeSettingsWriter.completeSetup` impl mirrors the real writer's
  semantics (set all four fields at once); add a `callRecorder` line for
  parity with the other writes.

### 5. CLAUDE.md

Add a short paragraph in the Architecture section codifying:
"ViewModels, Activities, Fragments, and use cases depend only on
`domain/repository/*` interfaces. Concrete implementations live in
`data/repository/*` and are wired by Hilt in `di/`. Any new `import
org.spsl.evtracker.data.repository.*` line outside `di/` is an
architecture violation."

## Out of scope

- The lint/ktlint custom-rule mechanical enforcement (see TASK-16 follow-up).
  This spec only covers the import audit and the narrow-IF guarantee.
- Removing the orphaned `SettingsRepository.resetSetupComplete()` (its only
  caller is its own JVM test). File a separate cleanup task if desired.
- TASK-22 (SDK 35 upgrade) and TASK-02 (KDoc safeguard) — independent.

## Acceptance

```
$ grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"
(empty)
```

Plus: `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest` all green;
existing wizard tests pass unchanged.
