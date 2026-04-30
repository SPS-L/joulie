# TASK-24 — Narrow domain-interface enforcement — Implementation plan

> **For agentic workers:** small, surgical refactor. TDD where the test would
> actually catch a regression (atomic `completeSetup`). Imports-only swaps do
> not need a fresh failing test — the existing JVM suite already exercises
> the touched paths.

**Goal:** Remove the two remaining `import org.spsl.evtracker.data.repository.SettingsRepository`
lines outside `di/` (one in `EVTrackerApp`, one in `WizardViewModel`),
extend `SettingsWriter` with the atomic `completeSetup` method, and codify
the architecture rule in CLAUDE.md.

**Architecture:** No new files; one method added to an existing interface;
two import + one constructor parameter swap; one test fake updated;
one docs paragraph.

**Tech Stack:** Kotlin · Hilt · DataStore (no new deps).

---

## Files

- Modify: `app/src/main/java/org/spsl/evtracker/domain/repository/SettingsWriter.kt`
- Modify: `app/src/main/java/org/spsl/evtracker/data/repository/SettingsRepository.kt:45`
- Modify: `app/src/main/java/org/spsl/evtracker/EVTrackerApp.kt:12,20,33`
- Modify: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardViewModel.kt:9,14,63`
- Modify: `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt` — add `completeSetup` to `FakeSettingsWriter`
- Touch: `CLAUDE.md` (Architecture section paragraph)
- Touch: `docs/BACKLOG.md` (mark TASK-24 done with outcome blockquote)

---

## Task 1 — Extend `SettingsWriter` with atomic `completeSetup`

- [ ] **Step 1: Add the method to `SettingsWriter`**

```kotlin
// in domain/repository/SettingsWriter.kt
/**
 * Wizard finish: writes primaryMetric, distanceUnit, currency, and
 * setupComplete=true together inside a single dataStore.edit { ... } block.
 * Atomicity is required by the wizard gate invariant.
 */
suspend fun completeSetup(metric: String, unit: String, currency: String)
```

- [ ] **Step 2: Mark `SettingsRepository.completeSetup` as `override`**

```kotlin
// in data/repository/SettingsRepository.kt:45
override suspend fun completeSetup(metric: String, unit: String, currency: String) {
    dataStore.edit { prefs ->
        prefs[PreferenceKeys.PRIMARY_METRIC] = metric
        prefs[PreferenceKeys.DISTANCE_UNIT] = unit
        prefs[PreferenceKeys.CURRENCY] = currency
        prefs[PreferenceKeys.SETUP_COMPLETE] = true
    }
}
```

- [ ] **Step 3: Add impl to `FakeSettingsWriter`**

```kotlin
// in testing/Fakes.kt — inside FakeSettingsWriter
override suspend fun completeSetup(metric: String, unit: String, currency: String) {
    callRecorder?.add("completeSetup($metric,$unit,$currency)")
    this.primaryMetric = metric
    this.distanceUnit = unit
    this.currency = code.let { currency }   // direct assignment; no temp
    this.setupComplete = true
}
```

(Use plain assignments; no `code` shadowing — the parameter is `currency`.)

- [ ] **Step 4: Build and confirm clean compile**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The existing `SettingsRepositoryTest.completeSetup_writesAllFourKeysAtomically` continues to pass — its call site is identical.

- [ ] **Step 5: Run the existing repository + wizard tests as a pre-swap baseline**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest \
  --tests "org.spsl.evtracker.data.repository.SettingsRepositoryTest" \
  --tests "org.spsl.evtracker.data.repository.SettingsRepositoryAtomicWritesTest" \
  --tests "org.spsl.evtracker.ui.wizard.WizardViewModelTest"
```

Expected: all pass.

---

## Task 2 — Swap `EVTrackerApp` to `SettingsReader`

- [ ] **Step 1: Update import + field type**

```kotlin
// EVTrackerApp.kt:12
- import org.spsl.evtracker.data.repository.SettingsRepository
+ import org.spsl.evtracker.domain.repository.SettingsReader

// EVTrackerApp.kt:20
- @Inject lateinit var settingsRepository: SettingsRepository
+ @Inject lateinit var settingsReader: SettingsReader
```

- [ ] **Step 2: Update the read site**

```kotlin
// EVTrackerApp.kt:33
- val theme = settingsRepository.theme.first()
+ val theme = settingsReader.theme.first()
```

- [ ] **Step 3: Build**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

## Task 3 — Swap `WizardViewModel` to `SettingsWriter`

- [ ] **Step 1: Update import + constructor param**

```kotlin
// WizardViewModel.kt:9
- import org.spsl.evtracker.data.repository.SettingsRepository
+ import org.spsl.evtracker.domain.repository.SettingsWriter

// WizardViewModel.kt:14
- private val settingsRepository: SettingsRepository,
+ private val settingsWriter: SettingsWriter,
```

- [ ] **Step 2: Update the call site in `finish()`**

```kotlin
// WizardViewModel.kt:63
- settingsRepository.completeSetup(
+ settingsWriter.completeSetup(
    metric = s.metric,
    unit = s.unit,
    currency = s.currency,
)
```

- [ ] **Step 3: Confirm `WizardViewModelTest` still type-checks**

The existing test passes a real `SettingsRepository` to the VM constructor.
Because `SettingsRepository` implements `SettingsWriter`, the same line
type-checks unchanged. No test edits required.

- [ ] **Step 4: Build + run wizard tests**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest \
  --tests "org.spsl.evtracker.ui.wizard.WizardViewModelTest"
```

Expected: all 9 wizard tests pass.

---

## Task 4 — Verify the audit acceptance condition

- [ ] **Step 1: Re-grep for violations**

```bash
grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"
```

Expected: empty output.

- [ ] **Step 2: Confirm only `di/` files reference concrete repos**

```bash
grep -rn "data\.repository" app/src/main/java | grep import
```

Expected: only lines under `app/src/main/java/org/spsl/evtracker/di/`.

---

## Task 5 — Run full CI gate locally

- [ ] **Step 1: ktlint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Android Lint**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint
```

Expected: BUILD SUCCESSFUL (no new violations beyond baseline).

- [ ] **Step 3: Full JVM unit suite**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```

Expected: all green; count unchanged at 243.

- [ ] **Step 4: Release assembly (smoke)**

```
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleRelease
```

Expected: BUILD SUCCESSFUL.

---

## Task 6 — Update CLAUDE.md and BACKLOG.md

- [ ] **Step 1: Add architecture rule paragraph in `CLAUDE.md`**

Append (or merge into the Architecture section):

> **Narrow domain-interface rule:** ViewModels, Activities, Fragments, and
> use cases depend only on `domain/repository/*` interfaces (`CarReader`,
> `SettingsWriter`, etc.). Concrete `data.repository.*` classes are
> referenced only inside `di/` modules where Hilt binds them. Any new
> `import org.spsl.evtracker.data.repository.*` line outside `di/` is an
> architecture violation.

- [ ] **Step 2: Mark TASK-24 done in `docs/BACKLOG.md`**

Flip ☐ to ☑ in the overview table; add an `> **Outcome:** …` blockquote at
the top of the TASK-24 section pointing to this spec/plan and listing the
two refactored files plus the new `completeSetup` IF method.

---

## Task 7 — Commit, merge, push, cleanup

- [ ] **Step 1: Stage + commit on `feat/task24-narrow-interface-enforcement`**

Per repo convention: separate `git add`, `git commit`, `git push` steps —
no compound git commands.

Conventional message: `refactor(task-24): enforce narrow domain interfaces in EVTrackerApp + WizardViewModel`.

- [ ] **Step 2: `--no-ff` merge into `main`**

```
git checkout main
git merge --no-ff feat/task24-narrow-interface-enforcement
```

- [ ] **Step 3: Push**

```
git push origin main
```

- [ ] **Step 4: Delete feature branch**

```
git branch -d feat/task24-narrow-interface-enforcement
```

---

## Self-review

- Spec coverage: every requirement in the spec maps to a task above.
- Placeholders: none.
- Type consistency: the new `completeSetup` signature is identical
  (`metric: String, unit: String, currency: String`) across IF, impl, and
  fake.
- Atomicity: only the IF method is added; the impl body is unchanged, so
  the existing `completeSetup_writesAllFourKeysAtomically` test continues
  to validate the guarantee.
