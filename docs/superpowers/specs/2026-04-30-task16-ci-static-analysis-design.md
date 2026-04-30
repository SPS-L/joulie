# TASK-16 — Static analysis & code-style gate in CI

**Status:** Draft → ready for plan
**Date:** 2026-04-30
**Source:** `docs/BACKLOG.md` §TASK-16

## 1. Context

Today, `.github/workflows/release.yml` is the only CI workflow and it only
fires on `v*` tag pushes. PRs and pushes to `main` are unchecked: no linter,
no style checker, no unit tests, no static analysis. With ~176 Kotlin files
in `app/src` and TASK-15 (i18n) and TASK-22 (API-35) on deck, regressions on
hardcoded strings, deprecated API calls, and the `ChargeType` rename
(TASK-25) cannot be caught at PR time. This task closes that gap with a
**read-only-on-`main`** workflow that runs ktlint, Android Lint (in error
mode for a curated rule set), and the JVM unit-test suite on every PR.

## 2. Goals & non-goals

**Goals**

- Every PR and every push to `main` runs `ktlintCheck`, `:app:lint`, and
  `:app:testDebugUnitTest` and fails on any new violation.
- Existing pre-existing offenses are grandfathered via `lint-baseline.xml`
  so the gate goes green on day one.
- The Kotlin codebase auto-formats to a single canonical style (Kotlin
  official, 4-space indent) anchored in `.editorconfig` so contributors and
  ktlint agree.
- The release workflow (`release.yml`) is **not** modified — it stays as a
  separate, tag-triggered concern.

**Non-goals**

- Connected (instrumented) Android tests in CI. Running them needs an
  emulator matrix; that is out of scope and tracked separately under
  TASK-22's CI-matrix note. The spec deliberately stops at compile-only
  validation for `:app:assembleDebugAndroidTest`.
- Detekt or other static analyzers beyond ktlint + Android Lint. Adding
  them is a follow-up; this gate is the minimum viable bar.
- Enabling `MissingTranslation` as a hard error today. There are no
  localized strings yet (TASK-15 has not landed); turning it on now would
  fire on every string. The rule is wired but **stays informational** until
  TASK-15 introduces a `values-<lang>/strings.xml`. The spec calls this out
  so TASK-15 only has to flip a flag.

## 3. Design

### 3.1 ktlint

- **Plugin:** `org.jlleitschuh.gradle.ktlint`, version **12.1.1**.
  This is the latest tagged release that supports the project's combo of
  Kotlin 1.9.21, Gradle 8.4, and AGP 8.2.0. It bundles ktlint engine 1.1.x
  which honors `.editorconfig` for `kotlin_code_style = official`.
- **Where applied:** the `app/` module only. The project has a single
  Kotlin-source module today, so applying via `subprojects { … }` at root
  is YAGNI. When/if a `:core` module appears, the apply block can move to
  `subprojects` in one line.
- **Plugin alias:** added to `gradle/libs.versions.toml` under
  `[versions] ktlint = "12.1.1"` and `[plugins] ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }`.
- **Configuration:** the plugin's defaults (Kotlin official style, all
  experimental rules off) are accepted as-is. No custom `ktlint { … }`
  block in `app/build.gradle.kts`. The single anchor is `.editorconfig`.

### 3.2 `.editorconfig`

A new file at the repo root pins the formatting contract:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
indent_style = space
indent_size = 4
ktlint_code_style = intellij_idea
```

`ktlint_code_style = intellij_idea` matches the Kotlin "official" style as
shipped by JetBrains and is what AGP / Android Studio defaults to. This
keeps the IDE's reformat output and ktlint's check agreement byte-for-byte.

### 3.3 Pre-flight: format the existing tree

Before flipping the gate on, run `./gradlew ktlintFormat` once to bring
the existing ~176 Kotlin files into compliance. The diff is committed in
the same PR as the gate. This is a one-time mechanical reformat — no
behavioral changes. After that, day-1 CI passes.

Risk acknowledgement: `ktlintFormat` can produce a large diff. The PR
description must call this out, and reviewers should focus on the gate
config files (build.gradle.kts, .github/workflows/ci.yml,
lint-baseline.xml) — the formatting churn is mechanical.

### 3.4 Android Lint

Add a `lint { … }` block to `android { … }` in `app/build.gradle.kts`:

```kotlin
android {
    // …existing config…
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        error += listOf(
            "HardcodedText",
            "MissingTranslation",
            "TypographyDashes",
            "UnusedResources",
        )
    }
}
```

Notes:
- `baseline = file("lint-baseline.xml")` grandfathers existing offenses.
  The baseline is generated locally (Step 5 of the plan) and committed.
- `MissingTranslation` is in the error list intentionally even though it
  cannot fire today — when TASK-15 lands its first translated
  `values-el/strings.xml` (or similar), the rule starts protecting
  coverage automatically. No follow-up edit needed in TASK-15.
- `HardcodedText` will fire on existing layouts; the baseline absorbs
  them. New violations break the build, which is the protection
  TASK-15 explicitly relies on.
- `warningsAsErrors = false` to keep noise out — the four `error`
  promotions are the only rules that escalate.

### 3.5 CI workflow

A new file `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  static-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x ./gradlew
      - name: ktlint
        run: ./gradlew ktlintCheck --no-daemon --stacktrace
      - name: Android Lint
        run: ./gradlew :app:lint --no-daemon --stacktrace
      - name: JVM unit tests
        run: ./gradlew :app:testDebugUnitTest --no-daemon --stacktrace
      - name: Upload lint report
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
          if-no-files-found: ignore
```

- **One job, three sequential steps.** Splitting into parallel jobs would
  pay the Gradle daemon warm-up tax three times for a project this small
  (~30 s × 3 vs. ~60 s sequential). Keep it one job until wall-clock
  budget actually hurts.
- **Unit tests bundled in.** The backlog only requires
  `ktlintCheck lint`, but the same workflow has the JDK and Gradle
  warmed up — adding `testDebugUnitTest` costs ~30 s and protects the
  ~236 JVM tests we already have. Recommended.
- **Lint report uploaded on failure** so contributors don't have to
  re-run locally to see what tripped.
- **No emulator job.** `connectedAndroidTest` and
  `:app:assembleDebugAndroidTest` are deferred (see Non-goals §2).

### 3.6 Documentation

Update `CLAUDE.md` Build & Test section:

- Add a "Static analysis gate" subsection listing the three Gradle tasks
  the CI runs and the local one-liner
  (`./gradlew ktlintCheck :app:lint :app:testDebugUnitTest`).
- Document the baseline rule: "If lint flags pre-existing code in
  `lint-baseline.xml`, leave it; only new violations should break the
  build. Regenerate the baseline only when an entire rule is being
  retired."
- Note the `.editorconfig` anchor and that `./gradlew ktlintFormat`
  is the canonical fixer.

## 4. Files touched

| Path | Change |
|------|--------|
| `gradle/libs.versions.toml` | + ktlint plugin alias |
| `build.gradle.kts` (root) | + ktlint plugin in `plugins { … apply false }` |
| `app/build.gradle.kts` | + ktlint plugin apply, + `lint { … }` block |
| `.editorconfig` (new) | Pin Kotlin official style |
| `lint-baseline.xml` (new) | Generated, committed |
| `.github/workflows/ci.yml` (new) | PR + main gate |
| `CLAUDE.md` | Document the gate |
| `docs/BACKLOG.md` | TASK-16 row → 🟢 done |
| Many `*.kt` files | One-time `ktlintFormat` reformat |

## 5. Acceptance criteria

1. `./gradlew ktlintCheck` exits 0 on a clean checkout.
2. `./gradlew :app:lint` exits 0 on a clean checkout (baseline absorbs
   pre-existing offenses).
3. `./gradlew :app:testDebugUnitTest` still passes (unchanged: ~236).
4. `./gradlew :app:assembleDebug` still produces the APK (unchanged).
5. `.github/workflows/ci.yml` exists and triggers on `pull_request` and
   `push: branches: [main]`.
6. `release.yml` is **byte-identical** to its current state (no edits).
7. A deliberately introduced `Text("Hardcoded")` in a Compose-style XML
   (or a misformatted `.kt` file) breaks the build locally — manually
   spot-checked once before merge, not committed.
8. `CLAUDE.md` Build & Test section mentions the gate command.
9. `docs/BACKLOG.md` TASK-16 row flips to 🟢 with a one-line "merged
   YYYY-MM-DD" annotation under the section heading.

## 6. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| `ktlintFormat` produces a sprawling diff that buries the real gate config | Two-commit PR: commit 1 = `ktlintFormat` mechanical reformat; commit 2 = gate wiring + baseline + workflow + docs |
| ktlint disagrees with Android Studio's reformat | `.editorconfig` `ktlint_code_style = intellij_idea` aligns them; verified by formatting one file in the IDE and confirming `ktlintCheck` clean |
| Gradle wrapper download stalls in GitHub Actions runner | `gradle/actions/setup-gradle@v4` caches `~/.gradle/caches`; first run pays the tax once |
| `MissingTranslation` accidentally promoted to error before TASK-15 | The rule is wired now but cannot fire (no `values-<lang>/`); confirmed by running lint locally before merge |
| Lint baseline drifts (entries unrelated to current violations remain after a fix) | Documented policy in CLAUDE.md: regenerate only when retiring a rule. Day-to-day, the baseline is append-only-by-omission |

## 7. Out-of-scope follow-ups

- Detekt: can layer on top of ktlint without disruption when there's appetite.
- Connected-test matrix: blocked on TASK-22's API-35 work (per BACKLOG addendum).
- ktlint custom rules (e.g., enforce `PreferenceKeys` for new DataStore
  keys): noted in BACKLOG TASK-04 — out of scope here.
- Reusable composite GitHub Action: only one workflow consumes the steps;
  extracting now is YAGNI.
