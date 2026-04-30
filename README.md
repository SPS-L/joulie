# EV Efficiency Tracker

**Author:** [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/)
**License:** MIT
**Repository:** https://github.com/SPS-L/EV-android-app

An Android app for recording and analyzing electric vehicle charging efficiency and cost. Log mileage, kWh added, AC/DC charge type, location, and optional cost after each charge. View multi-metric statistics over any period with rich charts and an optional Google Drive backup.

> **Status:** v1.0.1 released. All planned features are implemented and merged on `main`. Signed release APKs are produced automatically by [`.github/workflows/release.yml`](.github/workflows/release.yml) on every `v*` tag push and attached to the corresponding GitHub Release. Every PR and push to `main` is gated by [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (ktlint + Android Lint + JVM unit tests).

---

## Features

- **First-boot setup wizard** — one-time preference setup (efficiency metric, distance unit, currency)
- **Per-charge logging** — mileage, kWh, AC/DC toggle, location quick-chips + free text, optional cost
- **Smart cost handling** — cost left at 0 or blank is stored as NULL and excluded from all statistics
- **Multi-metric dashboard** — km/kWh, mi/kWh, kWh/100km, cost/km, cost/100km
- **Location quick-chips** — fixed: Home / Work / Public; plus top 5 learned custom labels
- **AC vs DC tracking** — separate chart series and filter chips for AC and DC charges
- **Multi-car support** — add any number of cars; switch via top spinner
- **Custom period analysis** — date-range picker for any arbitrary period
- **Google Drive backup** — optional; uses hidden App Data folder; replace-or-skip restore on first enable if a remote snapshot exists; auto-backup after committed local data changes
- **CSV export** — share all data via Android share sheet
- **Material 3 theming** — Light / Dark / System, full M3 token system seeded from #1565C0 with a #FB8C00 "DC orange" tertiary ramp

---

## Documentation

| File | Purpose |
|------|---------|
| [`CLAUDE.md`](CLAUDE.md) | Guide for AI agents (architecture summary, invariants, conventions) |
| [`docs/DESIGN.md`](docs/DESIGN.md) | Canonical product + technical design spec (v3) |
| [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) | Drive API + OAuth 2.0 Android client setup |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Future-work tracker (post-v1 refactors and new features) |
| [`docs/TEST_PLAN.md`](docs/TEST_PLAN.md) | Test specification (all phases) |
| [`docs/AGENT_INSTRUCTIONS.md`](docs/AGENT_INSTRUCTIONS.md) | Original implementation walkthrough used to build the app — historical reference |
| [`docs/superpowers/specs/`](docs/superpowers/) · [`docs/superpowers/plans/`](docs/superpowers/) | Per-sub-project design specs and implementation plans (time-stamped, historical) |

---

## Install

Download the latest signed APK from the [GitHub Releases](https://github.com/SPS-L/EV-android-app/releases) page and install via `adb install` or by opening the APK on your device (you may need to enable "Install from unknown sources").

```bash
adb install evtracker-v1.0.1.apk
```

> Drive backup will not work on a sideloaded APK unless its signing-cert SHA-1 is registered with an OAuth Android client in your own Google Cloud project. See [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md).

---

## Build from source

Requires JDK 17 and Android SDK with Build Tools 34. Set `ANDROID_HOME`.

```bash
git clone https://github.com/SPS-L/EV-android-app.git
cd EV-android-app

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

./gradlew test                      # JVM unit tests (~236)
./gradlew connectedAndroidTest      # Espresso / Room — needs API 26+ device or emulator
```

### Static analysis gate

PRs and pushes to `main` must pass `ktlintCheck` and `:app:lint` before merge. Run the same gate locally before opening a PR:

```bash
./gradlew ktlintCheck :app:lint :app:testDebugUnitTest
./gradlew ktlintFormat              # auto-fix style violations
```

Style is anchored by [`.editorconfig`](.editorconfig) (Kotlin official / IntelliJ style, 4-space indent). Pre-existing lint offenses are absorbed by [`app/lint-baseline.xml`](app/lint-baseline.xml); only new violations break the build.

### Release builds

Release builds are signed via a gitignored `keystore.properties` at the repo root. Create a release keystore and a `keystore.properties` populated like:

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

If `keystore.properties` is absent, `assembleRelease` produces an unsigned APK — useful for inspecting the build but not installable.

### Cutting a release

The `release.yml` GitHub Actions workflow builds, signs, verifies, and publishes the APK to a GitHub Release whenever a tag matching `v*` is pushed. Required repo secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. To cut a release:

```bash
# bump versionCode and versionName in app/build.gradle.kts first
git tag v1.2.3
git push origin v1.2.3
```

The workflow can also be re-run manually from the Actions tab via `workflow_dispatch`.

---

## Google Drive Setup

See [`docs/GOOGLE_CLOUD_SETUP.md`](docs/GOOGLE_CLOUD_SETUP.md) for step-by-step instructions to enable the Drive API, create an OAuth 2.0 Android client, and register the SHA-1 fingerprints of your debug **and** release keystores. Each keystore SHA-1 needs its own OAuth client.
