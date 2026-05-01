# EV Efficiency Tracker

> Native Android app for logging EV charging sessions and analysing real-world efficiency and cost.

[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](#license)
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
- **Charts** — efficiency trend, monthly kWh and cost, AC vs DC split, top locations.
- **Google Drive backup** — optional, opt-in. Stored in the hidden App Data folder. Replace-or-skip restore on first enable; auto-backup after every committed local change.
- **CSV export** — share via the Android share sheet.
- **Material 3 theming** — Light / Dark / System; full M3 token system seeded from `#1565C0` with a `#FB8C00` "DC orange" tertiary ramp.
- **Smart cost handling** — cost left at 0 or blank is stored as `NULL` and excluded from every cost statistic; mixed-currency periods hide cost stats with an explicit banner.

## Download

Signed release APKs are attached to every GitHub Release: see the [Releases page](https://github.com/SPS-L/EV-android-app/releases).

```bash
adb install evtracker-v1.0.1.apk
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
- **Room v3** with explicit migrations for the local database; **DataStore Preferences** for user settings.
- **MPAndroidChart** for the charts tab.
- **WorkManager** for Drive backup scheduling (uniqueness via `enqueueUniqueWork`).
- **Material 3** with light/dark token palettes.

Min SDK 26 · target / compile SDK 35 · JDK 17 · Gradle 8.4 · AGP 8.2.0 · Kotlin 1.9.21.

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
./gradlew test                  # JVM unit tests (~257)
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
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Post-v1 refactors and feature backlog |
| [`docs/AGENT_INSTRUCTIONS.md`](docs/AGENT_INSTRUCTIONS.md) | Original implementation walkthrough (historical) |
| [`docs/superpowers/`](docs/superpowers/) | Per-sub-project specs and plans (time-stamped, historical) |
| [`CLAUDE.md`](CLAUDE.md) | Architecture summary, invariants, and conventions for AI agents |

## Privacy

- The app does not collect analytics, telemetry, or crash reports.
- All charge data is stored locally in the app's private Room database.
- Google Drive backup is **opt-in**. When enabled, a JSON snapshot is written to the app's private **App Data folder** on your Drive, hidden from the Drive UI and accessible only to this app's signing certificate. The scope is `https://www.googleapis.com/auth/drive.appdata` — the app cannot read or modify any other files on your Drive.
- CSV export writes to the app's external-files directory and is shared only via the Android share sheet at your request.

## License

Released under the **MIT License**. © [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/).
