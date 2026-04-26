# EV Efficiency Tracker

**Author:** [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/)  
**License:** MIT  
**Repository:** https://github.com/SPS-L/EV-android-app

An Android application for recording and analyzing electric vehicle charging efficiency. Track energy consumption, mileage, and efficiency metrics per charge event, with beautiful charts and flexible reporting periods.

## Features

- **Multi-car support** — add multiple EVs and switch between them easily
- **Per-charge logging** — record mileage (km or miles) and energy added (kWh) after each charging session
- **Efficiency statistics** — last charge, 7-day, monthly, yearly, and custom period views
- **Rich charts** — bar, line, and scatter plots via MPAndroidChart
- **Local-first storage** — all data stored locally in SQLite via Room; no account needed
- **Google Drive backup** — optional; pulls all existing data on first activation, then syncs incrementally
- **Reset & export** — full data reset per car or global; CSV export available
- **Dark/Light theme** — Material 3 adaptive theming

## Repository Layout

```
EV-android-app/
├── README.md                         # This file
├── LICENSE                           # MIT License
├── DESIGN.md                         # Full product & technical design
├── AGENT_INSTRUCTIONS.md             # Step-by-step guide for AI coding agent to build & package APK
├── TEST_PLAN.md                      # Unit, integration, and UI test descriptions
├── settings.gradle.kts
├── build.gradle.kts                  # Project-level Gradle config
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts              # App-level Gradle config
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/org/spsl/evtracker/   # Source stubs (see AGENT_INSTRUCTIONS.md)
        │   └── res/
        │       ├── layout/               # XML layout stubs
        │       ├── navigation/           # Nav graph
        │       ├── values/               # strings, colors, themes
        │       ├── xml/                  # file_paths for FileProvider
        │       └── drawable/
        ├── test/                         # Unit + Room integration tests
        └── androidTest/                  # Espresso UI tests
```

## Quick Start (Developer)

1. Clone the repo: `git clone git@github.com:SPS-L/EV-android-app.git`
2. Open in Android Studio Iguana (2023.2.1) or later
3. Sync Gradle
4. Run on emulator (API 26+) or physical device

## Build APK

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

## AI Agent Build

See `AGENT_INSTRUCTIONS.md` for the complete step-by-step implementation guide targeting an AI coding agent.

## Tests

```bash
./gradlew test                  # unit tests
./gradlew connectedAndroidTest  # instrumented tests (device/emulator required)
```

See `TEST_PLAN.md` for full test descriptions.

## Architecture

- **MVVM** with ViewModels exposing StateFlow/LiveData
- **Room** (SQLite) for local persistence
- **DataStore** for preferences
- **Navigation Component** (single-activity, multiple fragments)
- **MPAndroidChart** for charts
- **Google Drive App Folder** for optional backup (no visible Drive storage used)
- **WorkManager** for background sync

## License

MIT — © 2026 [Sustainable Power Systems Lab](https://sps-lab.org/)
