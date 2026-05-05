# Contributing to Joulie

Thanks for your interest in working on Joulie. This document covers everything you need to build the app from source, run the tests, ship a release, and find your way around the codebase. The user-facing description lives in [`README.md`](README.md); the canonical product + technical design lives in [`docs/DESIGN.md`](docs/DESIGN.md).

Joulie is licensed under **GPL-3.0-or-later**, by submitting a contribution you agree to release it under the same terms.

## Table of Contents

- [Architecture](#architecture)
- [Building from source](#building-from-source)
  - [Tests](#tests)
  - [Release builds](#release-builds)
- [CI gate](#ci-gate)
- [Releasing](#releasing)
- [Project documentation](#project-documentation)
- [AI coding assistants](#ai-coding-assistants)

## Architecture

Native Android, written in **Kotlin** with a clean four-layer split:

```
UI (Fragments + ViewModels)  →  Domain (use cases + services)  →  Repositories  →  Data (Room, DataStore, Drive)
```

- Single-Activity host with the **Navigation Component**; ViewBinding for views.
- **Hilt** for dependency injection. **KSP** (not kapt) for Room and Hilt code generation.
- **Room v7** with explicit migrations for the local database; **DataStore Preferences** for user settings.
- **MPAndroidChart** for the charts tab.
- **WorkManager** for Drive backup scheduling (uniqueness via `enqueueUniqueWork`).
- **Material 3** with light/dark token palettes.

Min SDK 26 · target / compile SDK 35 · JDK 17 · Gradle 8.9 · AGP 8.7.3 · Kotlin 1.9.21.

The full technical design lives in [`docs/DESIGN.md`](docs/DESIGN.md).

## Building from source

You need **JDK 17** and the **Android SDK with Build Tools 35**, with `ANDROID_HOME` set. AGP will auto-download the API 35 platform on first build.

```bash
git clone https://github.com/SPS-L/joulie.git
cd joulie
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Tests

```bash
./gradlew test                  # JVM unit tests (~430)
./gradlew connectedAndroidTest  # Espresso / Room, needs API 26+ device or emulator
```

The full test specification is in [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md). The nightly managed-AVD job (API 26 + API 35 matrix) runs the instrumented suite from `.github/workflows/nightly-instrumented.yml`; failure is informational only and does not block PRs.

### Release builds

Release builds are signed via a gitignored `keystore.properties` at the repo root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then:

```bash
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties`, the build still runs but produces an unsigned APK (useful for inspection, not for installing).

If you want a release APK that passes Google's developer-verification check, also place your **Android Developer Verification (ADI) registration token** at `app/src/main/assets/adi-registration.properties`, one line containing the snippet from your registration page. The file is gitignored (sensitivity-class same as `keystore.properties`); each maintainer keeps their own copy. The CI release workflow doesn't bake this asset yet, see backlog TASK-56.

Drive backup on debug and release builds requires Google Cloud OAuth Android clients registered against your keystore SHA-1s. See [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) for the full walkthrough.

## CI gate

PRs and pushes to `main` are gated by [`.github/workflows/ci.yml`](.github/workflows/ci.yml), which runs:

- `./gradlew ktlintCheck`, code style.
- `./gradlew :app:lint`, Android Lint, with `HardcodedText`, `MissingTranslation`, `TypographyDashes`, and `UnusedResources` promoted to errors.
- `./gradlew :app:testDebugUnitTest`, JVM unit tests.

Run the full gate locally before opening a PR:

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest
./gradlew ktlintFormat   # auto-fix style violations
```

Style is anchored by [`.editorconfig`](.editorconfig) (Kotlin official / IntelliJ style, 4-space indent), so the IDE's reformat output and `ktlintCheck` agree. Pre-existing lint offenses are absorbed by [`app/lint-baseline.xml`](app/lint-baseline.xml); **only new violations break the build**. Regenerate the baseline only when retiring a rule, never to "clean up", it's append-by-omission by design.

New Kotlin files under `app/src/main/java/org/spsl/evtracker/` must carry the SPDX header (TASK-51 relicense convention):

```kotlin
// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later
```

Test sources, generated files, and third-party vendored code are explicitly out of scope for the SPDX header.

## Releasing

Pushing a tag matching `v*` triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which builds, signs, verifies (`apksigner verify`), and publishes the APK as a GitHub Release asset with auto-generated notes.

Required repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Each release-keystore SHA-1 also needs its own OAuth Android client in the Google Cloud project, or Drive sign-in will fail on that build.

To cut a release:

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit.
2. Tag and push the tag separately:

   ```bash
   git tag v1.2.3
   git push origin v1.2.3
   ```

The workflow can also be triggered manually from the **Actions** tab via `workflow_dispatch`.

## Project documentation

| File | Purpose |
|------|---------|
| [`docs/DESIGN.md`](docs/DESIGN.md) | Canonical product + technical design spec |
| [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) | Drive API + OAuth 2.0 Android client setup |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Test specification |
| [`docs/METHODOLOGY.md`](docs/METHODOLOGY.md) | CO₂ tracker methodology, coefficients, sources, and caveats (TASK-20) |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Post-v1 refactors and feature backlog |
| [`PRIVACY.md`](PRIVACY.md) | Privacy policy (also reachable from the Play Store listing) |
| [`CLAUDE.md`](CLAUDE.md) | Architecture summary, invariants, and conventions for AI agents |

## AI coding assistants

If you're using an AI coding assistant (Claude Code, Cursor, etc.), [`CLAUDE.md`](CLAUDE.md) documents the project's invariants, ViewModel/event patterns, test infrastructure, and Drive-backup rules. Treat it as load-bearing context for any non-trivial change.
