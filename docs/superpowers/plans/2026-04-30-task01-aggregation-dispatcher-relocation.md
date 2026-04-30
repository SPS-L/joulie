# TASK-01: Relocate `AggregationDispatcher` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the `AggregationDispatcher` Hilt qualifier annotation from `org.spsl.evtracker.di` to `org.spsl.evtracker.core.coroutines`, removing the `domain → di` package import in `ObserveChartsModelsUseCase`.

**Architecture:** Pure package-rename refactor of a single file. (1) Create the qualifier annotation in its new package preserving the KDoc verbatim. (2) Update the two consuming files' imports — `di/DispatcherModule.kt` (newly needs an explicit import since it loses same-package access) and `domain/usecase/ObserveChartsModelsUseCase.kt` (FQN swap). (3) Delete the old file. No test edits — `ObserveChartsModelsUseCaseTest` and `ChartsViewModelTest` construct the use case via the named parameter `aggregationContext` and never import the qualifier annotation, so they continue to compile and pass unmodified.

**Tech Stack:** Kotlin 1.9, Hilt 2.51 (`@Qualifier`), Gradle Kotlin DSL, JUnit 4. Build env: `GRADLE_USER_HOME=/tmp/gradle-home` (sandbox quirk — gradle's default `~/.gradle` is read-only).

**Spec:** [`docs/superpowers/specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md`](../specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md)

---

## Task 1: Create the feat branch

**Files:** none

- [ ] **Step 1: Confirm clean working tree on main**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app status --short`
Expected: Output contains only sandbox-injected `??` device entries (e.g., `?? .bash_profile`, `?? .claude/agents`). No tracked files dirty. The newly-committed spec at `docs/superpowers/specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md` and this plan should already be committed on main.

- [ ] **Step 2: Confirm we're on main**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app branch --show-current`
Expected: `main`

- [ ] **Step 3: Create and check out the feat branch**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app checkout -b chore/task-01-relocate-aggregation-dispatcher`
Expected: `Switched to a new branch 'chore/task-01-relocate-aggregation-dispatcher'`

---

## Task 2: Baseline build/test green

Confirm the starting state is healthy before any edits, so failures later can be attributed to this change.

- [ ] **Step 1: Run JVM unit tests on the baseline**

Run:
```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`. ~236 tests pass, 0 failures. If anything red here, **stop** and report — do not proceed.

---

## Task 3: Create the new annotation file in `core/coroutines/`

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`

- [ ] **Step 1: Verify parent directory does not yet exist**

Run: `ls /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/core/coroutines/ 2>&1`
Expected: `No such file or directory` — the new package is being introduced.

- [ ] **Step 2: Create the file with the exact content below**

Path: `app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`

```kotlin
package org.spsl.evtracker.core.coroutines

import javax.inject.Qualifier

/**
 * Qualifier for the CoroutineContext used to perform off-main aggregation work
 * inside use cases (e.g. ObserveChartsModelsUseCase). Production binds this to
 * Dispatchers.Default; JVM tests pass EmptyCoroutineContext so flowOn becomes
 * a no-op and the test scheduler stays in control.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AggregationDispatcher
```

The KDoc is copied verbatim from the old file — do not paraphrase or reflow.

- [ ] **Step 3: Verify the file exists**

Run: `ls /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`
Expected: the path is printed back, no error.

---

## Task 4: Update import in `DispatcherModule.kt`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt`

The current file (relevant slice):

```kotlin
package org.spsl.evtracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import org.spsl.evtracker.domain.usecase.NowProvider
```

`DispatcherModule.kt` previously had same-package access to `AggregationDispatcher`, so no import was needed. After the move, an explicit import is required.

- [ ] **Step 1: Add the new import line**

Use Edit with `old_string`:
```
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import org.spsl.evtracker.domain.usecase.NowProvider
```
and `new_string`:
```
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import org.spsl.evtracker.core.coroutines.AggregationDispatcher
import org.spsl.evtracker.domain.usecase.NowProvider
```

The new line is placed alphabetically: `core.coroutines.AggregationDispatcher` sorts before `domain.usecase.NowProvider`.

- [ ] **Step 2: Verify**

Run: `grep -n "AggregationDispatcher\|NowProvider" /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt`
Expected: import line for `core.coroutines.AggregationDispatcher` appears immediately above the `NowProvider` import, plus the existing `@AggregationDispatcher` annotation use on the `provideAggregationContext` function.

---

## Task 5: Update import in `ObserveChartsModelsUseCase.kt`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt`

- [ ] **Step 1: Replace the import**

Use Edit with `old_string`:
```
import org.spsl.evtracker.di.AggregationDispatcher
```
and `new_string`:
```
import org.spsl.evtracker.core.coroutines.AggregationDispatcher
```

- [ ] **Step 2: Verify**

Run: `grep -n "AggregationDispatcher" /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt`
Expected:
- Line ~15: `import org.spsl.evtracker.core.coroutines.AggregationDispatcher`
- Line ~29: `@AggregationDispatcher private val aggregationContext: CoroutineContext`

No reference to `org.spsl.evtracker.di.AggregationDispatcher` remains.

---

## Task 6: Delete the old file

**Files:**
- Delete: `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`

- [ ] **Step 1: Delete with `git rm` (so the deletion is staged automatically)**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app rm app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`
Expected: `rm 'app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt'`

- [ ] **Step 2: Verify the file is gone**

Run: `ls /home/apetros/OneDriveCUT/Code/EV-android-app/app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt 2>&1`
Expected: `No such file or directory`.

---

## Task 7: Build verification

The Hilt annotation processor runs during the debug build and will fail compilation if the qualifier wiring is incoherent (e.g., `@Provides @AggregationDispatcher` referring to one annotation while `@Inject` consumers reference a different one). This is the primary safety net for the refactor.

- [ ] **Step 1: Assemble debug**

Run:
```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. If KSP/Hilt errors mention `AggregationDispatcher`, the import in `DispatcherModule.kt` (Task 4) or `ObserveChartsModelsUseCase.kt` (Task 5) is wrong — re-check the FQNs.

- [ ] **Step 2: Run JVM unit tests**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Test count unchanged from baseline (~236), 0 failures.

- [ ] **Step 3: Compile instrumented tests (running requires an emulator and is out of scope)**

Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: FQN audit — old path must be gone**

Run:
```bash
git -C /home/apetros/OneDriveCUT/Code/EV-android-app grep -n "org.spsl.evtracker.di.AggregationDispatcher" -- 'app/src/**/*.kt'
```
Expected: empty output (exit code 1). If anything is printed, fix that import to use `core.coroutines.AggregationDispatcher`.

- [ ] **Step 5: FQN audit — new path appears in exactly two production files**

Run:
```bash
git -C /home/apetros/OneDriveCUT/Code/EV-android-app grep -l "org.spsl.evtracker.core.coroutines.AggregationDispatcher" -- 'app/src/**/*.kt'
```
Expected exactly:
```
app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt
app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt
```
(The new file `core/coroutines/AggregationDispatcher.kt` declares the package but does not reference the FQN string itself, so it does not match.)

---

## Task 8: Tick the backlog

**Files:**
- Modify: `BACKLOG.md`

- [ ] **Step 1: Tick TASK-01 in the overview table**

Use Edit with `old_string`:
```
| TASK-01 | 🔴 | Relocate `AggregationDispatcher` out of `di/` | ☐ |
```
and `new_string`:
```
| TASK-01 | 🔴 | Relocate `AggregationDispatcher` out of `di/` | ☑ |
```

- [ ] **Step 2: Verify**

Run: `grep -n "TASK-01" /home/apetros/OneDriveCUT/Code/EV-android-app/BACKLOG.md`
Expected: line 12 shows `☑`; line 33 (the section header) is unchanged text.

---

## Task 9: Verify no living documentation references the old path

The historical F2 spec/plan at `docs/superpowers/specs/2026-04-29-sub-project-f2-design.md` and `docs/superpowers/plans/2026-04-29-sub-project-f2.md` mention `di/AggregationDispatcher.kt` — these are time-stamped artifacts and **must not be edited**; they correctly describe the state at the time they were written. Living docs (`README.md`, `CLAUDE.md`, `DESIGN.md`, `AGENT_INSTRUCTIONS.md`) currently make no reference to `AggregationDispatcher`, so no edits are needed.

- [ ] **Step 1: Confirm living docs are unaffected**

Run:
```bash
grep -l "AggregationDispatcher" \
  /home/apetros/OneDriveCUT/Code/EV-android-app/README.md \
  /home/apetros/OneDriveCUT/Code/EV-android-app/CLAUDE.md \
  /home/apetros/OneDriveCUT/Code/EV-android-app/DESIGN.md \
  /home/apetros/OneDriveCUT/Code/EV-android-app/AGENT_INSTRUCTIONS.md \
  /home/apetros/OneDriveCUT/Code/EV-android-app/TEST_PLAN.md \
  /home/apetros/OneDriveCUT/Code/EV-android-app/GOOGLE_CLOUD_SETUP.md \
  2>/dev/null
```
Expected: empty output. If any file matches, **stop and report** — the spec assumption was wrong.

---

## Task 10: Commit on the feat branch

- [ ] **Step 1: Stage all changes**

Run:
```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
git add app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt
git add app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt
git add app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt
git add BACKLOG.md
```

(`git rm` in Task 6 already staged the deletion of the old file.)

Run each `git add` as a separate command — the project's CLAUDE.md global rule forbids compound git commands.

- [ ] **Step 2: Confirm staged contents**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app diff --cached --name-status`
Expected exactly four entries:
```
A  app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt
M  app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt
M  app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt
D  app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt
M  BACKLOG.md
```
(Five lines — A, M, M, D, M — order may vary.)

- [ ] **Step 3: Commit**

Run:
```bash
git -C /home/apetros/OneDriveCUT/Code/EV-android-app commit -m "$(cat <<'EOF'
refactor(di): move AggregationDispatcher qualifier to core/coroutines

The qualifier is a cross-cutting coroutine marker, not DI module
plumbing. Relocating it removes the domain → di package import in
ObserveChartsModelsUseCase and creates a natural home for any
future coroutine qualifiers (e.g. IoDispatcher).

Closes BACKLOG.md TASK-01.
EOF
)"
```
Expected: a single commit on `chore/task-01-relocate-aggregation-dispatcher` with the four modifications + one creation + one deletion.

---

## Task 11: Merge to main and delete the branch

- [ ] **Step 1: Checkout main**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app checkout main`
Expected: `Switched to branch 'main'`

- [ ] **Step 2: Merge with --no-ff (per project workflow)**

Run:
```bash
git -C /home/apetros/OneDriveCUT/Code/EV-android-app merge --no-ff chore/task-01-relocate-aggregation-dispatcher -m "Merge branch 'chore/task-01-relocate-aggregation-dispatcher'"
```
Expected: a merge commit appears; `git log --oneline --decorate -5` shows the merge.

- [ ] **Step 3: Delete the feat branch**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app branch -d chore/task-01-relocate-aggregation-dispatcher`
Expected: `Deleted branch chore/task-01-relocate-aggregation-dispatcher (was <sha>).`

- [ ] **Step 4: Final sanity check**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app log --oneline --decorate -5`
Expected: HEAD → main, with the merge commit visible.

---

## Task 12: Post-merge verification

The `--no-ff` merge brings in the implementation commit. Re-run the test suite on `main` to be certain nothing slipped.

- [ ] **Step 1: Run JVM tests on the merged main**

Run:
```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 2: Final FQN audit on main**

Run: `git -C /home/apetros/OneDriveCUT/Code/EV-android-app grep "org.spsl.evtracker.di.AggregationDispatcher" -- 'app/src/**/*.kt'`
Expected: empty (exit 1). The relocation is complete.

---

## Self-Review Checklist (filled out by the plan author)

- **Spec coverage:** §1–8 of the spec all map onto tasks in this plan: §1–3 background → Task 1–2 setup; §4.1 new file → Task 3; §4.3 import updates → Tasks 4 & 5; §4.2 deletion → Task 6; §6 acceptance criteria → Task 7 (build/test/grep); §4.5 backlog tick → Task 8; §5 out-of-scope → respected (no rename, no new qualifiers, no NowProvider move). ✓
- **Placeholder scan:** Each step contains the exact command, exact import line, or exact code block. No "TBD"/"add appropriate" instances. ✓
- **Type consistency:** The annotation name `AggregationDispatcher`, the package `org.spsl.evtracker.core.coroutines`, and the parameter name `aggregationContext` are spelled identically in every task that references them. ✓
- **No tests added or modified:** Confirmed in Task 2 (baseline) and Task 7 (post-change) that the existing JVM unit tests stay unchanged and stay green. The qualifier annotation has no behavior of its own to test. ✓
