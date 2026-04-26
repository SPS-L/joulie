# EV Efficiency Tracker

**Author:** [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/)  
**License:** MIT  
**Repository:** https://github.com/SPS-L/EV-android-app

An Android app for recording and analyzing electric vehicle charging efficiency and cost. Log mileage, kWh added, AC/DC charge type, location, and optional cost after each charge. View multi-metric statistics over any period with rich charts and an optional Google Drive backup.

---

## Features

- **First-boot setup wizard** — one-time preference setup (efficiency metric, distance unit, currency)
- **Per-charge logging** — mileage, kWh, AC/DC toggle, location quick-chips + free text, optional cost
- **Smart cost handling** — cost left at 0 or blank is stored as NULL and excluded from all statistics
- **Multi-metric dashboard** — km/kWh, mi/kWh, kWh/100km, cost/km, cost/100km
- **Location quick-chips** — fixed: 🏠 Home · 💼 Work · ⚡ Public; plus top 5 learned custom labels
- **AC vs DC tracking** — separate chart series and filter chips for AC and DC charges
- **Multi-car support** — add any number of cars; switch via top spinner
- **Custom period analysis** — date-range picker for any arbitrary period
- **Google Drive backup** — optional; uses hidden App Data folder; auto-backup after each charge
- **CSV export** — share all data via Android share sheet
- **Material You theming** — Light / Dark / System, electric blue + teal palette

---

## Documentation

| File | Purpose |
|------|---------|
| `DESIGN.md` | Canonical product + technical design spec (v3) |
| `GOOGLE_CLOUD_SETUP.md` | Drive API OAuth client setup instructions |
| `AGENT_INSTRUCTIONS.md` | AI agent implementation steps — phase 1 (project scaffold, DB, basic UI) |
| `AGENT_INSTRUCTIONS_V2.md` | AI agent implementation steps — phase 2 (charts, Drive backup, CSV) |
| `AGENT_INSTRUCTIONS_V3.md` | AI agent implementation steps — phase 3 (wizard, location chips, cost rule) |
| `TEST_PLAN.md` | Test specification — phase 1 |
| `TEST_PLAN_V2.md` | Test specification — phase 2 |
| `TEST_PLAN_V3.md` | Test specification — phase 3 |

---

## Build

```bash
git clone https://github.com/SPS-L/EV-android-app.git
cd EV-android-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and Android SDK with Build Tools 34.

---

## Google Drive Setup

See `GOOGLE_CLOUD_SETUP.md` for step-by-step instructions to enable the Drive API, create an OAuth 2.0 Android client, and add the SHA-1 fingerprint of your debug keystore.
