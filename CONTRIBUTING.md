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
- **Room v8** with explicit migrations for the local database; **DataStore Preferences** for user settings.
- **Vico 2.0.0** (line / column charts) + custom `ui/common/PieChartView` (donut tabs, since Vico ships no pie primitive) for the charts tab.
- **WorkManager** for Drive backup scheduling (uniqueness via `enqueueUniqueWork`).
- **Material 3** with light/dark token palettes.

Min SDK 26 · target / compile SDK 35 · JDK 17 · Gradle 8.11.1 · AGP 8.9.2 · Kotlin 2.1.20.

The Gradle build is multi-module: `:app` ships the application APK; `:baselineprofile` is a sibling `com.android.test` module that drives a macro-benchmark over `:app`'s release variant to regenerate `app/src/main/baseline-prof.txt` for ART AOT compilation at install time. See [CLAUDE.md → Baseline profile cadence](CLAUDE.md) for when to refresh.

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
./gradlew test                  # JVM unit tests (~535)
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

If you want a release APK that passes Google's developer-verification check, also place your **Android Developer Verification (ADI) registration token** at `app/src/main/assets/adi-registration.properties`, one line containing the snippet from your registration page. The file is gitignored (sensitivity-class same as `keystore.properties`); each maintainer keeps their own copy. The CI release workflow writes the same asset from the `ADI_REGISTRATION_TOKEN` GitHub Secret before `assembleRelease` and verifies the signed APK contains it (`unzip -p`); if the secret is unset or the asset is empty, the workflow fails.

Drive backup on debug and release builds requires Google Cloud OAuth Android clients registered against your keystore SHA-1s. See [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) for the full walkthrough.

## CI gate

PRs and pushes to `main` are gated by [`.github/workflows/ci.yml`](.github/workflows/ci.yml), which runs:

- `./gradlew ktlintCheck`, code style.
- `./gradlew :app:lint`, Android Lint, with `HardcodedText`, `MissingTranslation`, `TypographyDashes`, `UnusedResources`, `ContentDescription`, `LabelFor`, and `KeyboardInaccessibleWidget` promoted to errors.
- `./gradlew :app:testDebugUnitTest`, JVM unit tests.
- `./gradlew :app:verifyRoborazziDebug`, Roborazzi screenshot baselines (TASK-79 / TASK-86); a diff fails the build.
- `./gradlew :app:assembleRelease`, release smoke build (unsigned in CI because `keystore.properties` is absent).

Run the full gate locally before opening a PR:

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleRelease
./gradlew ktlintFormat   # auto-fix style violations
```

Style is anchored by [`.editorconfig`](.editorconfig) (Kotlin official / IntelliJ style, 4-space indent), so the IDE's reformat output and `ktlintCheck` agree. Pre-existing lint offenses are absorbed by [`app/lint-baseline.xml`](app/lint-baseline.xml); **only new violations break the build**. Regenerate the baseline only when retiring a rule, never to "clean up", it's append-by-omission by design.

New Kotlin files under `app/src/main/java/org/spsl/evtracker/` must carry the SPDX header:

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

### Baseline profile regeneration

Profile-guided AOT compilation is fed by `app/src/main/baseline-prof.txt`. Regeneration is **manual** (it requires a device or AVD and adds ~15 min of wall-clock):

```bash
./gradlew :app:generateBaselineProfile
```

CI exposes the same job as a `workflow_dispatch`-only run in [`.github/workflows/baselineprofile.yml`](.github/workflows/baselineprofile.yml). Trigger it from the Actions tab, download the `baseline-prof-txt` artifact, replace the committed file, and push a commit on `main`. Cadence guidance (when to refresh) lives in [`CLAUDE.md`](CLAUDE.md) under *Baseline profile cadence*.

## Landing page (GitHub Pages)

The public landing page at [sps-l.github.io/joulie](https://sps-l.github.io/joulie/) is built and deployed by [`.github/workflows/pages.yml`](.github/workflows/pages.yml). The privacy page at [/privacy](https://sps-l.github.io/joulie/privacy) is rendered from `PRIVACY.md` at build time via pandoc, so editing the markdown auto-updates the live page within ~30 seconds of the push.

Files involved:

- [`docs/index.html`](docs/index.html), single-page layout (hero, About, Features, FAQ, Privacy, Footer).
- [`docs/site.css`](docs/site.css), single stylesheet. Brand palette as CSS custom properties; `prefers-color-scheme: dark` media query.
- [`docs/_privacy-template.html`](docs/_privacy-template.html), pandoc HTML template that wraps the rendered privacy markdown with the topbar / footer. The `_` prefix marks it as a build-only file (the workflow excludes anything matching `_*` from the deployed artifact).
- [`docs/favicon.png`](docs/favicon.png), 32x32 derivative of `branding/joulie_mark_only.png`.
- [`docs/.nojekyll`](docs/.nojekyll), empty file. Not strictly required under the Actions deploy path (the artifact is served as-is, no Jekyll), but kept so a fall-back to "Deploy from a branch" still works without surprises.
- [`PRIVACY.md`](PRIVACY.md) at the repo root, source of truth for the privacy policy. Edit this file; the live `/privacy` URL refreshes on the next push.

To turn deploys on the first time, set **Settings → Pages → Source to *GitHub Actions*** (not "Deploy from a branch"). The workflow handles everything from there. Triggers: any push to `main` that touches `docs/**`, `PRIVACY.md`, or the workflow itself; plus `workflow_dispatch` for manual reruns.

When editing the page, follow the Brand Guide voice rules: plain English, numbers always have units, never use the em-dash. Reuse the existing assets under `docs/branding/`; don't bake in new colours.

## EV reference dataset (TASK-92)

[`scripts/update_ev_db.py`](scripts/update_ev_db.py) pulls the latest [OpenEV Data](https://github.com/open-ev-data/open-ev-data-dataset) release, transforms each vehicle into Joulie's compact schema (`make`, `model`, `variant`, `year`, `battery_kwh`, `wltp_kwh_100km`), filters out rows missing a battery size or below 5 kWh, sorts by make → model → year, and publishes the result as the `ev_models.json` asset on the `ev-db-latest` GitHub release of this repo. The Android app downloads from that URL when the user taps **Settings → Update EV database** (TASK-91).

Scheduled via [`.github/workflows/ev-db-update.yml`](.github/workflows/ev-db-update.yml) on the first of every month at 03:00 UTC, plus `workflow_dispatch` for manual refreshes from the Actions tab. The auto-issued `secrets.GITHUB_TOKEN` already carries the `contents: write` scope, so no PAT lifecycle is involved.

To run manually from a contributor's laptop:

```bash
pip install -r scripts/requirements.txt
GITHUB_TOKEN=<pat-with-contents-write> python3 scripts/update_ev_db.py
```

The script is idempotent: a second consecutive run replaces the existing asset on `ev-db-latest` and leaves the repo in a clean state. It never creates a git tag or a commit, it only manages the release asset.

## Project documentation

| File | Purpose |
|------|---------|
| [`docs/DESIGN.md`](docs/DESIGN.md) | Canonical product + technical design spec |
| [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) | Drive API + OAuth 2.0 Android client setup |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Test specification |
| [`docs/METHODOLOGY.md`](docs/METHODOLOGY.md) | CO₂ tracker methodology, coefficients, sources, and caveats |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Post-v1 refactors and feature backlog |
| [`docs/branding/`](docs/branding/) | Joulie brand assets, the canonical `Joulie_Brand_Guide.pdf`, master logos / palette / SVG sources, and the regeneration scripts. Read `docs/branding/README.md` before changing any brand surface |
| [`PRIVACY.md`](PRIVACY.md) | Privacy policy (also reachable from the Play Store listing) |
| [`CLAUDE.md`](CLAUDE.md) | Architecture summary, invariants, and conventions for AI agents |

## AI coding assistants

If you're using an AI coding assistant (Claude Code, Cursor, etc.), [`CLAUDE.md`](CLAUDE.md) documents the project's invariants, ViewModel/event patterns, test infrastructure, and Drive-backup rules. Treat it as load-bearing context for any non-trivial change.
