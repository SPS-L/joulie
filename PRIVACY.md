# Privacy Policy, Joulie

**Last updated: May 2026**
**Developed by:** [Sustainable Power Systems Lab (SPS-Lab)](https://sps-lab.org/),
Cyprus University of Technology

---

## Overview

Joulie is designed with privacy as a default, not an afterthought. The app does **not** collect analytics, telemetry, crash reports, or any personal data beyond what you explicitly enter and choose to store.

---

## Data You Enter

When you log a charging session, the following data is stored **locally on your device** in the app's private database:

- Odometer reading and distance unit
- kWh added (or state-of-charge values used to estimate it)
- Charging type (AC or DC)
- Location label (free text you enter or select from quick-chips; no GPS coordinates are collected)
- Optional cost and currency
- Date and time of the session
- Optional notes
- Per-car nominal battery capacity (used by the kWh-from-SoC calculator and battery-degradation tracker)

This data never leaves your device unless you explicitly enable one of the opt-in export features described below.


---

## Google Drive Backup (Opt-In)

Drive backup is **disabled by default**. If you choose to enable it:

- A JSON snapshot of your charge log is written to your Google Drive **App Data folder**, a private, hidden folder accessible only to this app's signing certificate. The file is not visible in the regular Drive UI.
- The OAuth scope used is `https://www.googleapis.com/auth/drive.appdata` only. Joulie cannot read, modify, or access any other files or folders on your Google Drive.
- You can disable backup, trigger a manual backup, or permanently wipe the remote copy at any time via **Settings → Google Drive backup** (toggle, **Back up now**, and **Wipe remote backup**).
- Drive backup operations are logged locally for failure diagnostics (`android.util.Log`) but are not transmitted anywhere.
- Google's own [Privacy Policy](https://policies.google.com/privacy) applies to data stored on Google Drive.

---

## CSV Export (Opt-In)

You can export your charge history as a CSV file at any time via **Settings → Export CSV** (full history) or **Settings → Export CSV (date range)**. The file is written to the app's external-files directory and shared only by your explicit action via the standard Android share sheet. No data is transmitted automatically. The CSV schema is documented in the project's [`docs/DESIGN.md`](docs/DESIGN.md).

---

## CO₂ Tracker

The CO₂ tracker computes emission estimates using two values you can configure in Settings (a grid CO₂ intensity in gCO₂/kWh and an ICE-counterfactual baseline in L/100 km). The defaults reflect the Cyprus 2025 grid average and the EU real-world fleet average. All computation happens locally on your device; no data leaves the app.

---

## Permissions

| Permission | Why it is needed |
|---|---|
| `INTERNET` | Google Drive backup only (when enabled) |
| `POST_NOTIFICATIONS` | Drive-backup failure alerts only, requested after the third consecutive failure, never on launch, never re-prompted after a denial |
| No location permission | Location labels are free text you type or pick from chips; the app does not access GPS, Wi-Fi-derived location, or any other location signal |
| No contacts / camera / microphone | The app does not request any of these |

---

## Data Retention and Deletion

All charge data is stored locally on your device. You can:

- Delete individual charge events from **History** (swipe to delete, with a 5-second undo)
- Reset the active car's history via **Settings → Reset active car**
- Wipe everything via **Settings → Reset all data**
- Uninstall the app, Android removes all local app data automatically

If Drive backup is enabled, use **Settings → Wipe remote backup** before uninstalling to remove the cloud copy. Toggling Drive backup OFF does not delete the remote snapshot; only the explicit wipe does.

---

## Children

Joulie is not directed at children under 13 and does not knowingly collect data from children.

---

## Open Source

Joulie is licensed under the GNU General Public License v3.0 or later. The full source code, including the data-handling paths described above, is available at [github.com/SPS-L/joulie](https://github.com/SPS-L/joulie) for independent review.

---

## Changes to This Policy

If the privacy practices change in a future release, this page will be updated and the "Last updated" date revised. Material changes will be noted in the app's release notes.

---

## Contact

Questions or concerns?
- 📧 [info@sps-lab.org](mailto:info@sps-lab.org)
- 🌐 [https://sps-lab.org](https://sps-lab.org)
- 🐛 Bug reports and feature requests: [github.com/SPS-L/joulie/issues](https://github.com/SPS-L/joulie/issues)
