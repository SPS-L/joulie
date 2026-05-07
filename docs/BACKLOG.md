# Joulie — Development Backlog

Open follow-up work and under-consideration ideas. Items merged to `main` are dropped from this list once they ship; their history lives in `git log`.

**Priority legend:** 🔴 High (architecture/data safety) · 🟡 Medium (robustness/UX) · 🟢 Low (new feature)
**Status:** ☐ open · ⏸ under consideration (do not start without explicit go-ahead)

---

## Open

### 🟡 Accessibility (a11y) pass

Audit TalkBack semantics, `contentDescription` coverage, colour contrast (WCAG 2.1 AA), and touch-target sizing across all fragments. Espresso `AccessibilityChecks.enable()` already runs in nightly instrumented runs (informational, does not block PRs); the open work is to actually clear the surfaced violations rather than baseline them.

### 🟢 Compose `CarEditDialog`

Replace the current `CarEditDialog` with a Compose `AlertDialog`. Requires pulling Compose into the build for the first time, scoped to a single dialog so the build-tooling impact is contained.

### 🟢 Android Baseline Profile

Add a `:baseline-profile` module that records a Macrobenchmark startup profile (`androidx.baselineprofile`). The profile gets bundled into the APK so cold-start AOT-compiles the hottest paths. Improves first-launch responsiveness on lower-tier devices.

### 🟢 Migrate MPAndroidChart → Vico (+ custom pie)

MPAndroidChart is unmaintained and pulls a large reflection-heavy dependency that needs explicit R8 keep rules. Vico is the modern Compose-friendly replacement for line/bar; pie charts are not a Vico target, so a custom `Canvas`-based `PieChartView` is required for the AC vs DC and Locations tabs. Roborazzi screenshot coverage (below) must land first so the migration can be verified pixel-for-pixel.

### 🟢 Roborazzi screenshot tests for Dashboard + Charts

Add Roborazzi-driven JVM screenshot tests for the Dashboard and Charts fragments at canonical breakpoints (small phone, large phone, 7" tablet) in light and dark themes. Must land before the MPAndroidChart → Vico migration so the chart-rendering swap is a verifiable diff.

### 🟢 Audit Kotlin 2.x / K2 + KSP + Hilt compatibility

AGP 8.7.3 is in place; the build still uses Kotlin 1.9.21 against K1. Audit the upgrade path to Kotlin 2.x with the K2 compiler — KSP version pin, Hilt processor compatibility, gradle plugin compatibility, and any KMP-adjacent friction.

### 🟢 Multi-vehicle comparative analytics

Charts trend tab gains an "Overlay second car" affordance — pick another car from the same household, render its line series in a second colour, optional shaded difference between the two. Useful for households running an EV alongside a PHEV or two EVs.

### 🟢 Adopt Room `@AutoMigration` for additive schema bumps

For schema bumps that only add columns, switch from hand-rolled `Migration` objects to Room's `@AutoMigration`. Requires `exportSchema = true` and committing the JSON schema files under `app/schemas/`. Migrations that touch existing data still need to be hand-rolled.

### 🟢 Anonymised research-export pipeline

A separate export path for SPS-Lab research, stripping PII (location free-text, notes, currency where it identifies a country) and reducing odometer to delta-km. Output schema versioned and documented so analyses across releases stay reproducible.

### 🟢 Charging power profile fields

Add `peakPowerKw: Double?` and `chargingDurationMinutes: Int?` columns to `charge_events` (Room migration + backup-version bump). Surfaces in ChargeEdit as optional fields. Enables a "Slow vs DC fast" charging-power distribution chart and pricing analyses against demand-response tariffs.

### 🟢 Time-of-use (ToU) tariff classification

Classify each charge event into a tariff zone (off-peak / mid-peak / on-peak) based on event start time + a per-currency tariff schedule the user enters in Settings. Enables a "Cost by tariff zone" chart and "How much would I save shifting all charging to off-peak?" analysis.

### 🟢 Per-event grid carbon intensity

Replace the static `gridIntensityGCo2PerKwh` preference with a per-event live value fetched at save time. Blocked on a free real-time Cyprus grid-mix data source — see `docs/METHODOLOGY.md` Open Issues for the survey of candidates (Electricity Maps paid, ENTSO-E hourly mix viable but requires per-source IPCC AR6 emission factors).

### 🟢 Defensive SoC range guard in `KwhFromSocCalculator.compute`

Add a `require(socBefore in 0.0..1.0 && socAfter in 0.0..1.0)` at the top of `compute(...)`. Today the helper clamps a negative delta to zero but does not validate the inputs. Defensive — the call sites all validate first, but a public pure helper should not trust callers.

---

## Under consideration

### 🔴 Replace Drive backup with Storage Access Framework (SAF)

F-Droid blocker — Google Play Services is a non-free dependency. SAF (`ACTION_OPEN_DOCUMENT_TREE`) lets the user pick any cloud or local destination (Nextcloud, Syncthing, internal storage) and stores backup files there. Bigger rework than it sounds — auto-backup scheduling, restore prompt, replace-or-skip flow, and the whole `BackupRepository` surface need re-thinking against the SAF lifecycle (URI permission grants are revocable, document-tree URIs don't survive uninstall, etc.).

### 🟢 JSON-LD / OCPP-compatible export format

Research interoperability — emit charge events in a format that OCPP-aware platforms can ingest. Likely a JSON-LD profile rather than OCPP wire-format, since OCPP messages assume a charging-station context the app does not have.

### 🟢 Open Charge Map / OCPI station lookup

When the user taps a location chip in ChargeEdit, optionally suggest nearby public chargers from Open Charge Map (free API). Privacy-sensitive — the lookup is opt-in per-event and the request is anonymous (no account, no charge-event payload sent). Blocked on the SAF migration because pulling OCPI / Open Charge Map into the build alongside Google Play Services bloats the APK.
