# EV Efficiency Tracker

An Android application for recording and analyzing electric vehicle charging efficiency. Track energy consumption, mileage, and efficiency metrics per charge event, with beautiful charts and flexible reporting periods.

## Features

- **Multi-car support** — add multiple EVs and switch between them easily
- **Per-charge logging** — record mileage (km or miles) and energy added (kWh) after each charging session
- **Efficiency statistics** — last charge, 7-day, monthly, yearly, and custom period views
- **Rich charts** — bar, line, and scatter plots via MPAndroidChart
- **Local-first storage** — all data stored locally in SQLite; no account needed
- **Google Drive backup** — optional; pulls all existing data on first activation, then syncs incrementally
- **Reset & export** — full data reset per car or global; CSV export available
- **Dark/Light theme** — Material You adaptive theming

## Repository Layout

```
EV-android-app/
├── README.md                  # This file
├── DESIGN.md                  # Full product & technical design
├── AGENT_INSTRUCTIONS.md      # Step-by-step guide for AI coding agent to build & package APK
├── TEST_PLAN.md               # Unit, integration, and UI test descriptions
├── app/
│   ├── build.gradle           # App-level Gradle config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/org/spsl/evtracker/   # Source stubs (see AGENT_INSTRUCTIONS.md)
│       └── res/
│           ├── layout/        # XML layout stubs
│           ├── values/        # strings, colors, themes
│           └── drawable/      # icons
├── build.gradle               # Project-level Gradle
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
└── settings.gradle
```

## Quick Start (Developer)

1. Clone the repo: `git clone git@github.com:SPS-L/EV-android-app.git`
2. Open in Android Studio Hedgehog (2023.1.1) or later
3. Sync Gradle
4. Run on emulator (API 26+) or physical device
5. See `AGENT_INSTRUCTIONS.md` for automated build instructions

## License

MIT
