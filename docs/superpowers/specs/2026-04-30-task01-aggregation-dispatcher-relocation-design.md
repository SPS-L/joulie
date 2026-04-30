# TASK-01 — Relocate `AggregationDispatcher` Qualifier

**Date:** 2026-04-30
**Backlog item:** [BACKLOG.md TASK-01](../../../BACKLOG.md) (🔴 high priority)
**Type:** Refactor / package layering cleanup
**Risk:** Trivial — package rename of a single Hilt qualifier annotation.

---

## 1. Context

`app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt` currently lives in the `di/` package:

```kotlin
package org.spsl.evtracker.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AggregationDispatcher
```

It is **not a class with logic** — it is a Hilt `@Qualifier` annotation that tags an injected `CoroutineContext`. The binding lives in `di/DispatcherModule.kt`:

```kotlin
@Provides
@AggregationDispatcher
fun provideAggregationContext(): CoroutineContext = Dispatchers.Default
```

In production this resolves to `Dispatchers.Default`; in JVM unit tests, callers pass `EmptyCoroutineContext` directly so `flowOn(...)` becomes a no-op and the test scheduler stays in control.

**Consumers (verified via `grep -rln`):**

| File | Role |
|------|------|
| `di/DispatcherModule.kt` | Provides binding |
| `domain/usecase/ObserveChartsModelsUseCase.kt` | Sole production consumer (`@AggregationDispatcher private val aggregationContext: CoroutineContext`) |
| `test/.../ObserveChartsModelsUseCaseTest.kt` | Passes `aggregationContext = EmptyCoroutineContext` by name — does **not** import the qualifier |
| `test/.../ChartsViewModelTest.kt` | Same as above |

## 2. Problem

`ObserveChartsModelsUseCase` is a domain-layer class that imports from the `di/` package solely to reference this qualifier. Domain layer code should not depend on the DI infrastructure package; the dependency direction should be `di → domain`, not `domain → di`. The backlog flagged this as TASK-01.

> **Note on the backlog rationale:** TASK-01's text reads "a dispatcher is a domain or data concern, not a DI module," which assumed the file was a dispatcher *class*. Since it is actually a qualifier annotation, the stronger justification is layering hygiene: removing the `domain → di` import. The relocation is still worthwhile.

## 3. Decision

**Relocate the qualifier to a new `core/coroutines/` package**, following the convention used in Google's official Architecture Samples (e.g., now-in-android, architecture-samples). This:

1. Removes the `domain → di` import.
2. Creates a natural home for any future coroutine qualifiers (`IoDispatcher`, `MainDispatcher`) without cluttering `di/`.
3. Keeps the qualifier in a layer (`core/`) that both `domain/` and `di/` may legitimately depend on. (`core/model/` is already imported from `domain/`, so `core/` is established as a shared low-level layer.)

### Rejected alternatives

| Option | Rejected because |
|--------|------------------|
| `domain/service/` (literal backlog text) | Co-locates a DI marker with domain services — semantic mismatch (a qualifier isn't a "service"). |
| `domain/usecase/` (next to sole consumer) | Works today, but if a second use case adopts `@AggregationDispatcher`, the package home becomes arbitrary. |
| Leave in `di/`, mark TASK-01 obsolete | Preserves the `domain → di` import. The layering smell remains. |

## 4. File Changes

### 4.1 New file

`app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`

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

The KDoc is preserved verbatim.

### 4.2 Delete

`app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`

### 4.3 Update imports

**`app/src/main/java/org/spsl/evtracker/di/DispatcherModule.kt`** — change one import line:

```diff
- // (qualifier was in same package, no import needed previously)
+ import org.spsl.evtracker.core.coroutines.AggregationDispatcher
```

(The qualifier was previously in the same package as `DispatcherModule`, so an explicit import line is now required.)

**`app/src/main/java/org/spsl/evtracker/domain/usecase/ObserveChartsModelsUseCase.kt`** — change one import line:

```diff
- import org.spsl.evtracker.di.AggregationDispatcher
+ import org.spsl.evtracker.core.coroutines.AggregationDispatcher
```

### 4.4 No test changes

`ObserveChartsModelsUseCaseTest.kt` and `ChartsViewModelTest.kt` construct the use case via positional/named arguments and never import the qualifier annotation. They will continue to compile and pass without modification.

### 4.5 Backlog tick

In `BACKLOG.md`, change the TASK-01 row's `☐` to `☑`.

## 5. Out of Scope

The following are explicitly **not** part of this task and must not be bundled in:

- Renaming the qualifier (e.g., to `DefaultDispatcher` matching the Android samples convention) — defer until / unless additional dispatchers are introduced.
- Adding new qualifiers (`IoDispatcher`, `MainDispatcher`).
- Moving `NowProvider` — it is bound by `DispatcherModule` but already lives in `domain/usecase/` (correct layer).
- Auditing other DI artifacts for misplacement — TASK-02 covers `RoomDataResetTransactionRunner` separately.
- Refactoring `DispatcherModule` itself (e.g., splitting `NowProvider` provision into a separate `DomainModule`).

## 6. Acceptance Criteria

The change is complete when **all** of the following hold:

1. `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug` succeeds.
2. `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest` runs ~236 tests, all green (no count regression vs. `main`).
3. `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebugAndroidTest` compiles (running requires an emulator and is not gated on this task).
4. `git grep "org.spsl.evtracker.di.AggregationDispatcher"` returns **zero matches** in `app/src`.
5. `git grep "org.spsl.evtracker.core.coroutines.AggregationDispatcher"` returns matches in **exactly two files**: `DispatcherModule.kt` and `ObserveChartsModelsUseCase.kt`.
6. `git ls-files | grep "di/AggregationDispatcher.kt"` returns nothing (file is deleted, not orphaned).
7. `BACKLOG.md` TASK-01 row shows `☑` and the change is committed in the same commit as the move (atomic).

## 7. Implementation Sequence (preview for the plan)

The follow-up plan should sequence as:

1. Create the new file at `core/coroutines/AggregationDispatcher.kt` with the annotation + KDoc.
2. Update the two import sites (`DispatcherModule.kt`, `ObserveChartsModelsUseCase.kt`).
3. Delete the old file.
4. Run `./gradlew :app:assembleDebug` (Hilt code-gen verifies the qualifier wiring).
5. Run `./gradlew :app:testDebugUnitTest`.
6. Tick BACKLOG.md TASK-01.
7. Single commit on a `chore/task-01-relocate-aggregation-dispatcher` branch, merged via `--no-ff` per project workflow.

## 8. Open Questions

None. The qualifier has a single production consumer and no tests reference its FQN, so the move is mechanical.
