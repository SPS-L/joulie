# TASK-16 Implementation Plan — CI Static Analysis Gate

**Spec:** `docs/superpowers/specs/2026-04-30-task16-ci-static-analysis-design.md`
**Branch:** `main` (single-developer task; small surface; no parallel work needed)
**Date:** 2026-04-30

## Step order

The order matters: each step's verification depends on the previous step's
output. Don't reorder.

### Step 1 — Wire ktlint plugin to the version catalog

- Edit `gradle/libs.versions.toml`:
  - Under `[versions]` add: `ktlint = "12.1.1"`
  - Under `[plugins]` add: `ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }`
- Edit `build.gradle.kts` (root): add `alias(libs.plugins.ktlint) apply false`.
- Edit `app/build.gradle.kts`: add `alias(libs.plugins.ktlint)` in the `plugins { … }` block.

**Verify:** `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:tasks --group "verification" -q | grep -i ktlint` lists `ktlintCheck`.

### Step 2 — Add `.editorconfig`

Create `.editorconfig` at repo root with the contents from spec §3.2.

**Verify:** `cat .editorconfig` shows the file. No Gradle command needed yet.

### Step 3 — One-time `ktlintFormat` of the existing tree

- Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintFormat`
- Inspect `git diff --stat` — expect a churn-style diff across `app/src`.
- Stage and commit as **commit 1** with subject:
  `chore(format): apply ktlint to existing tree (TASK-16 prep)`

**Verify:** `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck` exits 0.

### Step 4 — Configure Android Lint

- Edit `app/build.gradle.kts`: add the `lint { … }` block from spec §3.4 inside `android { … }`.
- The `baseline = file("lint-baseline.xml")` line is added now; the file does not yet exist — that's fine, lint creates it on the next run.

**Verify:** `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint --no-daemon` runs (it may fail with violations — that's expected and what Step 5 fixes).

### Step 5 — Generate the lint baseline

- Run: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:updateLintBaseline`
  (with `baseline =` set, the standard `:app:lint` task writes the file too,
  but `updateLintBaseline` is the explicit AGP 7+ task and won't fail if
  there are no offenses).
- Confirm `lint-baseline.xml` was written at the repo root (relative to `app/`,
  so the path is `app/lint-baseline.xml` — wait, AGP resolves `file("lint-baseline.xml")`
  relative to the **module** dir. Spec §4 lists it at repo root; that's wrong.
  Correction: the file lands in `app/lint-baseline.xml` because `file(…)` in
  `app/build.gradle.kts` is module-relative. Update the spec path table accordingly).
- Inspect: open `app/lint-baseline.xml` and skim the issue ids — confirm only
  the four error-promoted rules and no surprises.

**Verify:** `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint` now exits 0.

> **Plan correction (vs. spec §4):** the baseline is at `app/lint-baseline.xml`
> not the repo root. This is the only path correction; spec content otherwise
> stands. Will fold the correction into the spec's "Files touched" table when
> updating CLAUDE.md (Step 7).

### Step 6 — Add the CI workflow

- Create `.github/workflows/ci.yml` with the contents from spec §3.5.
- Confirm `release.yml` is untouched: `git diff .github/workflows/release.yml` empty.

**Verify (local):** `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
exits 0 (syntactic). End-to-end verification happens on the first PR.

### Step 7 — Update CLAUDE.md

- In the "Build & Test" section, add the static-analysis subsection per spec §3.6.
- Mention the `app/lint-baseline.xml` path and the `./gradlew ktlintFormat` fixer.
- Mention the new `.editorconfig`.

**Verify:** `git diff CLAUDE.md` shows only additive edits to that section.

### Step 8 — Mark TASK-16 done in BACKLOG.md

- Flip the table row at line 26 from 🔴 to 🟢.
- Under the `## 🔴 TASK-16` heading, change the priority emoji to 🟢 and add a
  one-line "Merged YYYY-MM-DD on `main`. CI workflow: `.github/workflows/ci.yml`."
  callout below the existing intro paragraph.

**Verify:** `grep -n "TASK-16" docs/BACKLOG.md` shows the green row.

### Step 9 — Final verification (per superpowers:verification-before-completion)

Run **all** of the following and confirm exit code 0 / success output before
claiming done:

```bash
cd /home/apetros/OneDriveCUT/Code/EV-android-app
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew ktlintCheck
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lint
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:assembleDebug
```

A failure on any of the four blocks completion — fix the underlying issue,
do not relax the gate.

### Step 10 — Commit & summarize

- **Commit 1** (already made in Step 3): `chore(format): apply ktlint to existing tree (TASK-16 prep)`
- **Commit 2**: `ci(task-16): add ktlint + Android Lint gate workflow`
  - Includes: build.gradle.kts edits (root + app), libs.versions.toml,
    .editorconfig, app/lint-baseline.xml, .github/workflows/ci.yml,
    CLAUDE.md, docs/BACKLOG.md, and the spec/plan docs themselves.
- Do **not** push; user controls when to push.

## Decision log

- **ktlint plugin version 12.1.1**: latest stable as of 2026-01; supports
  Kotlin 1.9.21 + Gradle 8.4 + AGP 8.2.0. No newer line is required for
  this scope.
- **Apply ktlint at app module only, not subprojects:** project is
  single-Kotlin-module; spec §3.1 has the migration line documented for
  the day a `:core` module is added.
- **Bundle `:app:testDebugUnitTest` into `ci.yml`:** ~30 s extra; protects
  ~236 existing tests for free. The backlog text only required
  `ktlintCheck lint`; this is the recommended addition (user said "accept
  recommendations").
- **No emulator / connectedAndroidTest job:** explicitly out of scope per
  spec §2. Tracked under TASK-22 addendum in BACKLOG.
- **Two-commit PR (format + gate):** keeps the meaningful diff readable
  by separating mechanical reformat from gate wiring.
- **Lint baseline path:** `app/lint-baseline.xml` (module-relative), not
  repo root. Spec §4 corrected via Step 5 callout.

## Rollback

If the gate proves too noisy in practice:
1. Drop a rule from the `error` list in `app/build.gradle.kts` (e.g.,
   demote `TypographyDashes` back to warning).
2. If the workflow times out, split into two jobs (ktlint || lint).
3. Worst case, `git revert` the ci-task-16 commit; the format commit
   stands on its own and can stay.
