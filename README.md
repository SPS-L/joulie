# EV Efficiency Tracker

> Native Android app for logging EV charging sessions and analysing real-world efficiency and cost.

[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/SPS-L/EV-android-app)](https://github.com/SPS-L/EV-android-app/releases/latest)
[![CI](https://github.com/SPS-L/EV-android-app/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/SPS-L/EV-android-app/actions/workflows/ci.yml)
[![Release build](https://github.com/SPS-L/EV-android-app/actions/workflows/release.yml/badge.svg)](https://github.com/SPS-L/EV-android-app/actions/workflows/release.yml)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.21-blueviolet)](https://kotlinlang.org)

Log every charge — odometer, kWh added, AC/DC, location, optional cost — and see your numbers (km/kWh, kWh/100 km, mi/kWh, cost/km, cost/100 km) across any period, with AC vs DC breakdowns, per-car history, and optional Google Drive backup. Built and maintained by the [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/).

## Table of Contents

- [Features](#features)
- [Download](#download)
- [Architecture](#architecture)
- [Building from source](#building-from-source)
- [Contributing](#contributing)
- [Releasing](#releasing)
- [Documentation](#documentation)
- [Privacy](#privacy)
- [License](#license)

## Features

- **First-boot setup wizard** — pick efficiency metric, distance unit, and currency once.
- **Per-charge logging** — odometer, kWh, AC/DC toggle, location quick-chips with free-text fallback, optional cost.
- **Multi-metric dashboard** — km/kWh, mi/kWh, kWh/100 km, cost/km, cost/100 km. Cost rows hide when no cost data exists in the period.
- **Multi-car** — track any number of vehicles; switch via the top spinner; per-car or global resets.
- **AC vs DC** — separate chart series and filter chips.
- **Custom periods** — last 7 / 30 days, this year, since previous charge, or any custom range.
- **Charts** — efficiency trend, monthly kWh and cost, AC vs DC split, top locations, and a battery-degradation tab with a nominal-capacity reference line.
- **kWh-from-SoC calculator** — for cars and chargers that show only state-of-charge percentages, an opt-in calculator on the charge form derives `kwhAdded` from `Δsoc × nominal battery capacity`. Estimated events get an "Est." badge in History and are excluded from the degradation tracker (so the math doesn't fool itself).
- **Google Drive backup** — optional, opt-in. Stored in the hidden App Data folder. Replace-or-skip restore on first enable; auto-backup after every committed local change. Surfaces failures via two notification channels (chronic-failure sticky after 3 consecutive misses; auth-required higher-importance card) — `POST_NOTIFICATIONS` is requested only after a real failure, never on launch. Settings exposes manual **Back up now** and **Wipe remote backup** buttons for when you want to force a sync or scrub the cloud copy.
- **CSV export** — share via the Android share sheet.
- **Home-screen widget** — 2×2 tile shows the active car's most recent charge: car name, relative date, kWh, efficiency, optional cost. Tap to open the dashboard.
- **Material 3 theming** — Light / Dark / System; full M3 token system seeded from `#1565C0` with a `#FB8C00` "DC orange" tertiary ramp.
- **Localisation** — English (default), Greek, Turkish, and Russian translations covering Cyprus's resident populations, switchable in-app via Settings → Language or on the wizard's first page (autonyms always rendered in their own script). On Android 13+, the OS-level per-app language entry under System Settings is also wired automatically. First-pass translations are LLM-produced (TASK-15) and pending native-speaker review.
- **Smart cost handling** — cost left at 0 or blank is stored as `NULL` and excluded from every cost statistic; mixed-currency periods hide cost stats with an explicit banner.
- **CO₂ tracker** — Dashboard card and Charts tab show EV-side emissions side-by-side with a petrol-counterfactual baseline; defaults to Cyprus 2025 grid average (577 gCO₂/kWh, configurable in Settings). Methodology + coefficients documented in [`docs/METHODOLOGY.md`](docs/METHODOLOGY.md).

## Download

Signed release APKs are attached to every GitHub Release: see the [Releases page](https://github.com/SPS-L/EV-android-app/releases).

```bash
adb install evtracker-v1.7.2.apk
```

You can also open the APK on the device after enabling **Install from unknown sources** for your file manager or browser.

> **Drive backup on sideloaded builds.** The Authorization API ties the Drive sign-in to the APK's signing-cert SHA-1. The published release APK works out of the box; APKs you sign yourself need their SHA-1 registered with an OAuth Android client in your own Google Cloud project — see [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md).

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
git clone https://github.com/SPS-L/EV-android-app.git
cd EV-android-app
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Tests

```bash
./gradlew test                  # JVM unit tests (~425)
./gradlew connectedAndroidTest  # Espresso / Room — needs API 26+ device or emulator
```

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

If you want a release APK that passes Google's developer-verification check, also place your **Android Developer Verification (ADI) registration token** at `app/src/main/assets/adi-registration.properties` — one line containing the snippet from your registration page. The file is gitignored (sensitivity-class same as `keystore.properties`); each maintainer keeps their own copy. The CI release workflow doesn't bake this asset yet — see backlog TASK-56.

## Contributing

Contributions are welcome. PRs and pushes to `main` are gated by [`.github/workflows/ci.yml`](.github/workflows/ci.yml), which runs:

- `./gradlew ktlintCheck` — code style.
- `./gradlew :app:lint` — Android Lint, with `HardcodedText`, `MissingTranslation`, `TypographyDashes`, and `UnusedResources` promoted to errors.
- `./gradlew :app:testDebugUnitTest` — JVM unit tests.

Run the full gate locally before opening a PR:

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest
./gradlew ktlintFormat   # auto-fix style violations
```

Style is anchored by [`.editorconfig`](.editorconfig) (Kotlin official / IntelliJ style, 4-space indent), so the IDE's reformat output and `ktlintCheck` agree. Pre-existing lint offenses are absorbed by [`app/lint-baseline.xml`](app/lint-baseline.xml); **only new violations break the build**. Regenerate the baseline only when retiring a rule, never to "clean up" — it's append-by-omission by design.

New Kotlin files under `app/src/main/java/org/spsl/evtracker/` must carry the SPDX header (TASK-51 relicense convention):

```kotlin
// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later
```

Test sources, generated files, and third-party vendored code are explicitly out of scope.

If you're using an AI coding assistant (Claude Code, Cursor, etc.), [`CLAUDE.md`](CLAUDE.md) documents the project's invariants, ViewModel/event patterns, test infrastructure, and Drive-backup rules.

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

## Documentation

| File | Purpose |
|------|---------|
| [`docs/DESIGN.md`](docs/DESIGN.md) | Canonical product + technical design spec |
| [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) | Drive API + OAuth 2.0 Android client setup |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Test specification |
| [`docs/METHODOLOGY.md`](docs/METHODOLOGY.md) | CO₂ tracker methodology — coefficients, sources, and caveats (TASK-20) |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Post-v1 refactors and feature backlog |
| [`CLAUDE.md`](CLAUDE.md) | Architecture summary, invariants, and conventions for AI agents |

## Privacy

- The app does not collect analytics, telemetry, or crash reports.
- All charge data is stored locally in the app's private Room database.
- Google Drive backup is **opt-in**. When enabled, a JSON snapshot is written to the app's private **App Data folder** on your Drive, hidden from the Drive UI and accessible only to this app's signing certificate. The scope is `https://www.googleapis.com/auth/drive.appdata` — the app cannot read or modify any other files on your Drive.
- CSV export writes to the app's external-files directory and is shared only via the Android share sheet at your request.

## License

Released under the **GNU General Public License v3.0 or later** ([`GPL-3.0-or-later`](LICENSE)). © 2024–2026 [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/), Cyprus University of Technology.

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for the full text.
