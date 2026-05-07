# Joulie, Full Product & Technical Design (v3)

## 1. Overview

**App Name:** Joulie  
**Package:** `org.spsl.evtracker`  
**Min SDK:** API 26 (Android 8.0)  
**Target SDK:** API 35 (Android 15)  
**Language:** Kotlin  
**Architecture:** MVVM + Repository pattern  
**Build system:** Gradle (Kotlin DSL)

### Core Goals
- Simple, fast charge-event logging (mileage + kWh + AC/DC + location + optional cost)
- Per-car efficiency and cost statistics over flexible time periods
- Multi-metric display (km/kWh, mi/kWh, kWh/100km, cost/km, cost/100km)
- Local-first; optional Google Drive backup
- No login required
- Beautiful Material You charts

---

## 2. Feature List

| # | Feature | Notes |
|---|---------|-------|
| F1 | Add/edit/delete charge event | mileage, kWh, AC/DC, location (chip+text), cost (optional), note |
| F2 | Multi-car management | Add/rename/delete; quick switch via Spinner |
| F3 | Unit toggle | km ↔ miles; global, display-only conversion |
| F4 | Currency setting | EUR default; user-selectable |
| F5 | **First-boot setup wizard** | One-time: primary metric + unit + currency |
| F6 | Statistics dashboard | Last charge, 7 days, 30 days, year, custom range |
| F7 | Multi-metric stats cards | km/kWh, mi/kWh, kWh/100km, cost/km, cost/100km (based on preference) |
| F8 | Charts | Line (efficiency trend AC vs DC), Bar (monthly kWh), Bar (monthly cost), Pie (AC/DC split + location split), Line (battery degradation with nominal-capacity reference) |
| F8b | Battery health card (Dashboard) | Latest effective battery capacity vs nominal `battery_kwh` as a percentage; shown only when the active car has nominal capacity set and ≥1 qualifying charge exists |
| F9 | Dashboard filter chips | All / AC / DC |
| F10 | Location quick-chips | Home, Work, Public (fixed) + up to 5 custom learned chips |
| F11 | Local SQLite storage | Room, no account needed |
| F12 | Google Drive backup | Optional; App Data folder (hidden from Drive UI); restore on first activation |
| F13 | Data reset | Per-car or global |
| F14 | CSV export | All fields included, sharing via FileProvider |
| F15 | Dark/Light/System theme | Material 3 DayNight |
| F16 | Custom period analysis | Date-range picker (MaterialDatePicker) |
| F17 | Home-screen widget | 2×2 tile showing the active car's most recent charge |
| F18 | kWh-from-SoC calculator | Opt-in calculator on ChargeEdit derives `kwhAdded` from `Δsoc × nominalBatteryKwh` for cars/chargers that report only SoC %. Events flagged `DERIVED_FROM_SOC` are excluded from the battery-degradation tracker |

---

## 3. First-Boot Setup Wizard

### 3.1 Trigger

Show on first app launch when DataStore key `setupComplete` is `false` (default). After the wizard completes successfully, write `setupComplete = true`. The wizard is **never shown again** unless the user explicitly taps **Reset preferences** in Settings.

In `MainActivity.onCreate()`:
```kotlin
lifecycleScope.launch {
    val complete = prefsDataStore.data.first()[SETUP_COMPLETE] ?: false
    if (!complete) {
        findNavController(R.id.nav_host).navigate(R.id.wizardFragment)
    }
}
```

### 3.2 Wizard Screens

Implemented as a `WizardFragment` backed by `ViewPager2` with **4 pages**. Back/Next/Finish buttons are in the host fragment, not per-page. Progress dots shown via `TabLayout` or a custom indicator.

---

> Back/Next buttons live on the **host fragment**, not on each page. Page 1 hides the Back button; pages 2, 3, and 4 show both. The host's Next button labels are: page 1 → "Get Started", pages 2/3 → "Next →", page 4 → "Finish ✓". On page 4 the Finish button is **disabled** until the disclaimer-acceptance switch is toggled (see Screen 4).

**Screen 1 of 4, Welcome**

```
┌────────────────────────────────┐
│                                │
│  ⚡  Joulie                    │
│                                │
│  Let's set up your preferences │
│ , you can change these later  │
│  in Settings at any time.      │
│                                │
└────────────────────────────────┘
   (host buttons:  [Get Started])
```

---

**Screen 2 of 4, Efficiency metric & units**

```
┌────────────────────────────────┐
│  How do you like to see        │
│  efficiency?                   │
│                                │
│  ◉ km / kWh  (distance/energy) │
│  ○ kWh / 100 km (energy/dist.) │
│  ○ mi / kWh  (miles)           │
│                                │
│  ─────────────────────         │
│  Distance unit                 │
│  [ km ]  [ miles ]  ← toggle   │
│                                │
│  [← Back]         [Next →]     │
└────────────────────────────────┘
```

Use a `RadioGroup` (or 3 `RadioButton`s) for metric; a `MaterialButtonToggleGroup` for km/miles.

---

**Screen 3 of 4, Currency**

```
┌────────────────────────────────┐
│  What currency do you use      │
│  for charging costs?           │
│                                │
│  [ ▼  EUR, Euro         ]     │
│                                │
│  Supported: EUR, USD, GBP,     │
│  CHF, JPY, CZK, PLN, HUF,     │
│  DKK, SEK, NOK, AUD, CAD      │
│                                │
│  ℹ  Cost entry is optional,  │
│  leave 0 to skip tracking.     │
│                                │
│  [← Back]         [Next →]    │
└────────────────────────────────┘
```

Use an `AutoCompleteTextView` (ExposedDropdownMenu) populated from a `string-array` resource.

> Adding a currency = edit `res/values/currencies.xml` only, no code changes required.

---

**Screen 4 of 4, About + Disclaimer acceptance**

```
┌────────────────────────────────┐
│  About this app & terms        │
│                                │
│  [ SPS-Lab badge ]             │
│  Sustainable Power Systems Lab │
│  Cyprus University of Tech.    │
│  Limassol, Cyprus              │
│                                │
│  Disclaimer                    │
│  This application is provided  │
│  for research and personal use │
│  only. Efficiency and cost     │
│  estimates are based on user-  │
│  entered data and do not …     │
│  …no liability for decisions   │
│  made based on data recorded   │
│  or displayed by this app.     │
│                                │
│  [○ I have read and accept the │
│      disclaimer]               │
│                                │
│  [← Back]      [Finish ✓ ⛔]  │
└────────────────────────────────┘
```

Reuses the existing `about_acknowledgment_*` and `about_disclaimer_*`
strings from the About screen, single source of truth for
the legal copy. The acceptance toggle is a `MaterialSwitch`
(`wizard_page4_accept`); `WizardViewModel.UiState.disclaimerAccepted`
mirrors its state. The host's Finish button observes the same flag and
stays disabled until the switch is on.

Reset preferences (Settings → Reset preferences) writes
`setupComplete=false` and re-routes to the wizard, which forces the
user to step through all four pages again, disclaimer acceptance is
not persisted independently of `setupComplete`.

### 3.3 DataStore Keys (complete list)

| Key | Type | Default | Written by |
|-----|------|---------|------------|
| `setupComplete` | Boolean | `false` | Wizard, on Finish |
| `primaryMetric` | String | `"kwh_per_100km"` | Wizard / Settings |
| `distanceUnit` | String | `"km"` | Wizard / Settings |
| `currency` | String | `"EUR"` | Wizard / Settings |
| `activeCarId` | Int | `-1` | Car selector |
| `driveEnabled` | Boolean | `false` | Settings |
| `theme` | String | `"system"` | Settings only, **not** part of the wizard |
| `lastBackupAt` | Long? | absent | `DriveBackupWorker` on `Result.success()` |
| `resetInProgress` | Boolean | `false` | `ResetAllDataUseCase` (durable interrupted-reset flag) |
| `consecutiveBackupFailures` | Int | `0` | `BackupOutcomeReporter`, reset to 0 on backup `Success`, +1 on `AuthRequired` / `Failure` |
| `notificationPermissionDenied` | Boolean | `false` | `MainViewModel.markNotificationPermissionDenied`, sticky once true |

All keys declared as `Preferences.Key<T>` constants in a `PreferenceKeys` object. `theme` accepts `"system"`, `"light"`, `"dark"`.

### 3.4 Edge Cases

- User kills app mid-wizard → `setupComplete` stays `false`; wizard shown again next launch
- User taps **Reset preferences** in Settings → set `setupComplete = false`; navigate to wizard
- Wizard page 2 metric ↔ unit coupling table:

| Metric chosen | Distance unit forced |
|---|---|
| `km_per_kwh` | `km` |
| `kwh_per_100km` | `km` |
| `mi_per_kwh` | `miles` |

- Manually toggling distance unit: `km` selects `km_per_kwh` if the previous metric was `mi_per_kwh`; `miles` selects `mi_per_kwh` if the previous metric was km-based. `kwh_per_100km` is left untouched if already selected.

---

## 4. Data Model

### 4.1 SQLite Tables

> The SQL below uses snake_case for readability. The actual database, generated by Room from the entity declarations under `app/src/main/java/org/spsl/evtracker/data/local/entity/`, uses **camelCase** column names (`costTotal`, `costPerKwh`, `useCount`, `lastUsed`). See §4.2.

#### `cars`
```sql
CREATE TABLE cars (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    make        TEXT,
    model       TEXT,
    year        INTEGER,
    battery_kwh REAL,
    created_at  INTEGER NOT NULL  -- Unix ms
);
```

#### `charge_events`
```sql
CREATE TABLE charge_events (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id          INTEGER NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
    event_date      INTEGER NOT NULL,  -- Unix ms
    odometer_km     REAL    NOT NULL,  -- always stored in km; display converts
    kwh_added       REAL    NOT NULL,
    charge_type     TEXT    NOT NULL DEFAULT 'AC',  -- 'AC' | 'DC_FAST' | 'DC_ULTRA' (round-tripped via ChargeTypeConverter)
    cost_total      REAL,        -- NULL if user left cost blank/zero
    cost_per_kwh    REAL,        -- NULL if user left cost blank/zero
    currency        TEXT,        -- snapshot of currency at time of entry
    location        TEXT,        -- free text (chip label or typed)
    note            TEXT,
    soc_before      REAL,        -- nullable; fraction 0.0..1.0
    soc_after       REAL,        -- nullable; fraction 0.0..1.0
    kwh_source      TEXT    NOT NULL DEFAULT 'MEASURED',  -- 'MEASURED' | 'DERIVED_FROM_SOC'
    created_at      INTEGER NOT NULL
);
CREATE INDEX idx_ce_car_date ON charge_events(car_id, event_date);
CREATE INDEX idx_ce_type     ON charge_events(charge_type);
CREATE INDEX idx_ce_location ON charge_events(location);
```

**Cost entry rules:**

| User action | `cost_total` stored | `cost_per_kwh` stored |
|-------------|---------------------|-----------------------|
| Blank or 0 in either field | `NULL` | `NULL` |
| Total cost only | `X` | `X / kwh_added` |
| Price per kWh only | `Y × kwh_added` | `Y` |
| Both entered | Total takes precedence; per-kWh = total / kWh | |

> Events with `cost_total IS NULL` are **excluded** from all cost statistics and cost chart series.

#### `custom_locations`
```sql
CREATE TABLE custom_locations (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    label       TEXT    NOT NULL UNIQUE,
    use_count   INTEGER NOT NULL DEFAULT 1,
    last_used   INTEGER NOT NULL  -- Unix ms
);
```

Top 5 by `use_count DESC, last_used DESC` are shown as quick chips in the charge form.

### 4.2 Room Database Version History

| Version | Changes |
|---------|--------|
| 1 | Initial: `cars`, `charge_events` |
| 2 | Added `chargeType TEXT NOT NULL DEFAULT 'AC'` to `charge_events` |
| 3 | Created `custom_locations` table (with unique index on `label`); added `costTotal`, `costPerKwh`, `currency`, `location`, `note` to `charge_events` |
| 4 | `MIGRATION_3_4` rewrites legacy `'DC'` cells in `charge_events.chargeType` to `'DC_FAST'`. Column type stays TEXT, `ChargeType` (`AC` / `DC_FAST` / `DC_ULTRA`) is round-tripped via `@TypeConverters(ChargeTypeConverter)` on `AppDatabase`. No DDL change. |
| 5 | `MIGRATION_4_5` is a no-op (no DDL). All entity primary keys (`Car.id`, `ChargeEvent.id`, `CustomLocation.id`) and `ChargeEvent.carId` widen from Kotlin `Int` to `Long`, SQLite `INTEGER` columns already hold 64-bit signed integers, so the on-disk representation is unchanged and Room's schema hash for these columns stays the same. The migration is registered as a tripwire so a future downgrade trips Room's schema validator instead of silently truncating values. (.) |
| 6 | `MIGRATION_5_6` adds nullable `socBefore` and `socAfter` REAL columns to `charge_events` for state-of-charge tracking. Both default to NULL on legacy rows. `CapacityEstimator` consumes them when present (else falls back to the 80%-of-nominal heuristic for Dashboard battery-health and the Charts degradation tab). (.) |
| 7 | `MIGRATION_6_7` adds `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` to `charge_events`. Round-tripped via `@TypeConverters(ChargeKwhSourceConverter)` (registered alongside `ChargeTypeConverter`) into the `ChargeKwhSource` enum (`MEASURED` / `DERIVED_FROM_SOC`). Legacy rows backfill cleanly to `MEASURED`. `CapacityEstimator` skips `DERIVED_FROM_SOC` events on both the exact and heuristic paths because the derived `kwhAdded` is tautological against `Δsoc × nominalBatteryKwh`. (.) |

> **Column-naming note:** the SQL examples in §4.1 use snake_case for readability. The actual database, generated by Room from the entity declarations under `data/local/entity/`, uses **camelCase** column names (`costTotal`, `costPerKwh`, `useCount`, `lastUsed`, etc.). Migrations must match the entity casing exactly or Room will fail schema validation at startup.

---

## 5. Architecture

```
┌──────────────────────────────────────────────────────┐
│  UI Layer (Fragments + ViewModels)                   │
│  Wizard · Dashboard · ChargeEdit · Cars · Settings   │
│  Charts · History · ManageLocations                  │
├──────────────────────────────────────────────────────┤
│  Domain Layer (Use Cases + Pure Services)            │
│  SaveChargeEvent · DeleteChargeEvent                 │
│  ObserveDashboardStats · RestoreBackup · ExportCsv   │
│  StatsCalculator · CostParser · UnitConverter        │
│  CapacityEstimator · DateRangeResolver     │
│  BackupSerializer · ChargeTypeJsonAdapter            │
├──────────────────────────────────────────────────────┤
│  Repository Layer                                    │
│  CarRepository · ChargeEventRepository               │
│  LocationRepository · SettingsRepository             │
│  BackupRepository                                    │
├──────────────────────────────────────────────────────┤
│  Data Sources / Infrastructure                       │
│  Room (CarDao, ChargeEventDao, CustomLocationDao)    │
│  Preferences DataStore (PreferenceKeys)              │
│  Google Drive AppData client                         │
│  WorkManager backup scheduler                        │
└──────────────────────────────────────────────────────┘
```

### Architecture Principles

- Keep Fragments thin: bind UI state, forward user actions, and handle navigation or other Android-only concerns.
- Keep ViewModels focused on UI state and intent handling; they orchestrate use cases but do not own persistence rules, stats math, or backup logic.
- Keep repositories narrow: they aggregate data sources and expose CRUD/query APIs, but they do not implement business workflows.
- Put cross-source workflows in use cases so validation, persistence, learned-location updates, and backup enqueueing happen in one place.
- Keep metric calculations, cost parsing, unit conversion, and backup serialization in pure Kotlin services with no Android dependencies.

### Recommended Composition

**UI layer**
- Fragments render state from `StateFlow`/`Flow`, collect one-off UI effects, and delegate all mutations to their ViewModel.
- Each screen has a dedicated ViewModel with a single screen-state model rather than multiple loosely related LiveData streams.

**Domain layer**
- `SaveChargeEventUseCase`: validates odometer ordering, normalizes cost input, persists the event, records location usage, and enqueues backup when Drive is enabled.
- `DeleteChargeEventUseCase`: deletes an event only after undo expiry or explicit confirmation, then schedules backup.
- `ObserveDashboardStatsUseCase`: combines active car, period, AC/DC filter, preferences, and events into one UI-ready stats model.
- `RestoreBackupUseCase`: downloads backup data, validates schema version, snapshots current local state for undo, replaces local state transactionally, and restores settings needed for a consistent post-restore app state.
- `ExportCsvUseCase`: emits a canonical 14-column CSV: `event_date_iso, car_name, odometer_km, kwh, kwh_source, charge_type, location, cost_total, cost_per_kwh, currency, km_per_kwh, soc_before, soc_after, note`. Same schema for full-history (`export(carId)`) and date-ranged (`export(carId, range: LongRange)`) exports. Distance is always canonical kilometres regardless of the user's display preference, so cross-fleet research analysis is locale-independent. `km_per_kwh` is derived per-row using the delta-odometer convention from §7, first row blank because no prior event exists in the exported slice; the chain advances unconditionally so transient rollbacks don't break subsequent deltas. User-supplied text columns (`car_name`, `kwh_source`, `chargeType.name`, `location`, `currency`, `note`) route through a hardened `csvEscape` that quotes on the full RFC 4180 set plus `\r` and `\t`, and prefixes a single quote `'` when the field starts with `=`, `+`, `-`, or `@`, neutralising spreadsheet formula injection without losing data round-trip. Numeric / timestamp columns bypass the escape so researchers' pivot tables and charts keep working.

**Repository layer**
- `CarRepository`: car CRUD plus active-car-safe delete helpers.
- `ChargeEventRepository`: charge-event CRUD and filtered queries only.
- `LocationRepository`: learned-location persistence and ranking only.
- `SettingsRepository`: DataStore-backed preferences only.
- `BackupRepository`: raw backup read/write operations only.

**Data / infrastructure layer**
- Room is the source of truth for cars, charge events, and custom locations.
- DataStore is the source of truth for setup completion, metric/unit preferences, currency, active car, Drive enabled state, and theme.
- Google Drive access is isolated behind a dedicated backup client; the rest of the app should not know about Drive SDK details.
- WorkManager is used only for scheduled backup execution, not for core business logic.

### Dependency Management

- Use a single composition root in `Application`.
- Preferred approach: Hilt for ViewModel injection, repository wiring, test replacements, and lifecycle-safe scoping.
- Acceptable fallback for a smaller build: a manual `AppContainer`, but object construction must remain centralized and testable.

### Key ViewModels

| ViewModel | Owns |
|-----------|------|
| `WizardViewModel` | Wizard page state and completion action via `SettingsRepository` |
| `DashboardViewModel` | Active car, period, AC/DC filter, empty state, and dashboard cards via `ObserveDashboardStatsUseCase` |
| `ChargeEditViewModel` | Charge form state, learned locations, validation errors, and save/edit intents via `SaveChargeEventUseCase` |
| `CarsViewModel` | Car list plus add/rename/delete intents via `CarRepository` and small car-management use cases when needed |
| `SettingsViewModel` | Preferences, theme, Drive enable/disable, restore prompts, and reset flows via `SettingsRepository` + backup use cases |
| `ChartsViewModel` | Chart models for the selected period built from the same stats/query rules used by the dashboard |
| `HistoryViewModel` | Paged event list, filters, edit targets, and delete-with-undo flow |
| `ManageLocationsViewModel` | Full custom-location list, delete actions, and empty state |

### Package Direction

Recommended package layout for long-term maintainability:

- `core/` for framework-free models and utilities shared across layers
- `data/local/` for Room entities, DAOs, and database wiring
- `data/preferences/` for DataStore access
- `data/backup/` for Drive backup client and serialization
- `domain/usecase/` for orchestration logic
- `domain/service/` for pure business rules such as stats and cost parsing
- `ui/<feature>/` for each screen's Fragment, ViewModel, adapters, and UI models

---

## 6. UI Screens Detail

### Dashboard
- Car spinner top-right (fast switch)
- Period tabs: Since previous charge / 7d / 30d / Year / Custom
  - "Since previous charge" requires ≥ 2 events to render the efficiency card; otherwise it falls back to the empty state for that period.
- Filter chips row: All · AC · DC
- **Primary metric card** (large): value derived from `primaryMetric` pref
- Secondary metric cards (smaller): remaining 2 efficiency metrics
- Cost summary row (hidden when all `costTotal IS NULL` for that period, **or** when the period contains more than one currency, in which case show a "Multi-currency period, cost stats hidden" banner instead)
- **Battery health card** ( hidden when the car has no nominal `battery_kwh` set or no qualifying charge exists): displays `Stats.batteryHealthPercent` as `NN%`. The percentage is computed from the FULL per-car history rather than the period subset, degradation is a long-term property. Values may exceed 100%, that surfaces heuristic over-estimation rather than being clamped.
- Empty states (mutually exclusive):
  - **No car** (`activeCarId == -1` or no `cars` rows), copy "Add a car to get started", CTA "Add car" → CarsFragment
  - **No events for active car**, copy "Log your first charge to see stats here", CTA "Log charge" → ChargeEditFragment

### Charge Edit
- Date/time picker (default: now)
- Odometer input (km or miles label per unit pref)
- kWh added input
- AC / DC segmented toggle (`MaterialButtonToggleGroup`)
- **Location row:**
  - Fixed chips: 🏠 Home · 💼 Work · ⚡ Public
  - Top 5 learned custom chips (from `custom_locations`)
  - `+ Add` chip → focuses free-text field
  - Tapping any chip fills the text field
- **Cost section (collapsed by default, tap to expand):**
  - Toggle: Total cost / Price per kWh
  - Currency label from pref
  - Hint: "Leave blank to skip, won't affect statistics"
- **SoC section ( collapsed by default, tap to expand):**
  - Two paired inputs: SoC before (%) · SoC after (%), each accepting `0..100`
  - Stored on the entity as fractions in `0.0..1.0` (after / 100)
  - Validation: both blank → drop them; both filled, parseable, in-range, with `after > before` → save; otherwise inline error
  - Powers the exact path of `CapacityEstimator` (Dashboard battery-health card + Charts degradation tab)
- **kWh-from-SoC calculator** — the calculator activates **automatically** the moment the user has typed both SoC fields with a valid range AND the kWh field is blank, provided the active car has a non-null `nominalBatteryKwh`. kWh auto-fills with `(socAfter - socBefore) × nominalBatteryKwh` (via `KwhFromSocCalculator`, clamped to ≥ 0); `kwhSource = DERIVED_FROM_SOC` is persisted on save. While the calculator is active an info banner inside the SoC card warns "kWh estimated from SoC change × nominal capacity. Excluded from battery-degradation tracking." Manually editing the kWh field flips `kwhSource` back to `MEASURED` and deactivates the calculator (existing `setKwh` echo-guard semantic). The explicit "Don't know kWh? Calculate from SoC %" link still appears below the kWh input when `nominalBatteryKwh != null` — it serves as a manual override that derives kWh from SoC even when kWh is already filled (auto-activation is a no-op when kWh is non-blank, by design, so the link is the only way to *overwrite* an existing kWh with the SoC-derived value). **Charging-loss caveat:** the derived value is *battery-side* kWh (`Δsoc × nominal`), not *charger-delivered* kWh. AC charging loses ~10–15% to heat/conversion; DC ~5%. This biases derived events: efficiency (km/kWh, mi/kWh) looks slightly *better* than reality, kWh/100 km looks slightly *better*, and cost-per-kWh looks slightly *worse*. Acceptable for the primary use case (logging events when no kWh reading is available); a charging-loss factor column was deliberately not added — the provenance flag plus the degradation-tracker exclusion is enough information for the user to interpret the data.
- Note field (optional, single line)
- Save → validates odometer > last entry, persists, updates `custom_locations`

### Charts
- Line chart: efficiency trend over time, AC series (Joulie blue `#0D47FF`) vs DC series (Joulie yellow `#FFD54D`, pre-rebrand: orange `#FB8C00`).
  The Y-axis follows `primaryMetric`: `km_per_kwh` plots raw `kmPerKwh`,
  `kwh_per_100km` plots `100 / kmPerKwh` (skipping rows with `kmPerKwh ≤ 0`),
  and `mi_per_kwh` plots `UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh)`.
- Bar chart: monthly kWh consumed
- Bar chart: monthly cost (hidden if no cost data)
- Pie chart: AC vs DC split
- Pie chart: location distribution
- **Line chart: battery degradation**. Y-axis = effective capacity (kWh) per qualifying charge event; X-axis = event date. A dashed `LimitLine` at the car's nominal `battery_kwh` provides the reference. Points are computed by `CapacityEstimator`, exact when both SoC fields are set, heuristic (using `kwhAdded` itself) when `kwhAdded ≥ 0.8 × nominalBatteryKwh`. The tab renders only when the car has nominal capacity set AND at least 3 qualifying points exist; otherwise an empty-state instructs the user to set the nominal capacity or log more SoC-tagged charges. **banner:** events flagged `DERIVED_FROM_SOC` are silently excluded from the chart on both code paths because the math is tautological. When at least one derived event sits in the visible period, a plurals-aware banner above the chart reads "N estimated events excluded from degradation tracking (calculated from SoC %)." so the user understands why the rendered point count is below the visible event count. The banner sources its count from `CapacityEstimator.countDerivedEvents(periodEvents)`, threaded onto `ChartsUiState.Loaded.derivedExcludedCount` by `ObserveChartsModelsUseCase`.
- All charts use MPAndroidChart; support pinch-zoom and value markers
- **Theme-aware text and gridlines.** MPAndroidChart's defaults are hardcoded greys; the M3 surface flips to dark in night mode and chart labels become unreadable unless we retint. `ChartStyling.resolveAxisColors(context)` resolves `?attr/colorOnSurface` (axis labels, legend text, pie center-text) and `?attr/colorOutlineVariant` (gridlines, axis lines) from the active theme. `applyThemeAwareAxisColors(BarLineChartBase<*>)` is called from `configureLineChart` / `configureBarChart`. PieCharts retint via `legend.textColor` directly + `applyPieCenterTextColor(chart)` which must run AFTER `chart.centerText = ...` so the PieChartRenderer paint cache is in a steady state. Pie slice labels and per-slice value text stay `Color.WHITE` because they sit on saturated palette colors and white reads correctly in both themes.

### History (HistoryFragment)
- Paginated `RecyclerView` of every `charge_event` for the active car, newest first
- Row shows: date, odometer (in display unit), kWh, AC/DC badge, an "Est." badge ( tertiary-container colour, visible only when `kwhSource == DERIVED_FROM_SOC`), location chip, cost (if non-null with currency)
- Tap row → opens `ChargeEditFragment` with the event prefilled (edit flow)
- Swipe-to-delete with Snackbar undo (5 s); deletion is only committed after the undo window expires, then re-runs stats and re-enqueues a Drive backup
- Filter chips at top mirror the Dashboard's All / AC / DC

### Manage Locations (ManageLocationsFragment)
- Reachable from Settings → "Manage custom locations"
- `RecyclerView` of every `custom_locations` row, sorted by `useCount DESC, lastUsed DESC`
- Each row shows label + last-used relative date + use count
- Swipe-to-delete with Snackbar undo (5 s); deletion is only committed after the undo window expires, does not affect existing `charge_events.location` strings, and re-enqueues a Drive backup because `custom_locations` is part of the backup payload
- Empty state: "Locations you save on charge events will appear here."

### Settings
- Primary efficiency metric (RadioGroup)
- Distance unit (toggle)
- Currency (dropdown)
- Theme: Light / Dark / System
- **Google Drive backup** (Switch)
  - On enable: show Drive auth flow; if a remote backup exists, ask whether to **replace** local data with that snapshot
  - "Skip" keeps local data unchanged, sets `driveEnabled = true`, and enables future backups from the current local state
  - Merge is **not** supported; the backup model is full-snapshot replace-or-skip only
  - If no remote backup exists, set `driveEnabled = true` and queue an initial backup from the current local state
  - Manual backup now button
  - Last backup timestamp
- **Manage custom locations** → list with delete swipe
- **Reset preferences** → re-shows wizard
- **Reset all data** → confirmation dialog; per-car or global
- **Export CSV** → share sheet

---

## 7. Derived Metrics Formulas

All efficiency uses the **delta odometer** method: subtract previous event's odometer.

Let `d_km` = odometer delta in km, `e` = kWh added.

| Metric | Formula |
|--------|---------|
| km/kWh | `d_km / e` |
| kWh/100km | `(e / d_km) × 100` |
| mi/kWh | `(d_km × 0.621371) / e` |
| cost/km | `cost_total / d_km` (NULL if no cost) |
| cost/100km | `(cost_total / d_km) × 100` (NULL if no cost) |

Aggregate stats use weighted averages: `Σ d_km / Σ e` for efficiency, `Σ cost / Σ d_km` for cost rate.

`Σ cost` covers **every** costed event in the period, including the first event in a multi-event period and including single-event periods. Earlier `StatsCalculator.computeStats` summed cost only inside the delta-pair odometer loop and silently dropped the first event's cost; the formula above is now what the code does. The mixed-currency override still wins, `Σ cost = null` whenever two distinct currencies appear among costed events in the period.

`Σ d_km` is the delta-odometer sum across consecutive event pairs and is `0` for single-event periods, so `cost/km = Σ cost / Σ d_km` stays `null` when only one event exists (the period correctly reports `totalCost` but no `costPerKm`).

First charge event for a car **cannot** compute efficiency (no prior odometer). Show "—" on card.

### 7.1 CO₂ tracker

Two numbers, surfaced side-by-side on the Dashboard CO₂ card and as cumulative series on the Charts CO₂ tab:

| Number | Formula |
|--------|---------|
| EV emissions (kg) | `Σ kwhAdded over period × gridIntensityGCo2PerKwh / 1000` |
| ICE counterfactual (kg) | `(periodTotalDistanceKm / 100) × iceBaselineLPer100km × 2.31` |
| Saved (kg, may be ±) | `iceCounterfactual − evEmissions` |

Coefficients live on `CO2Calculator.companion`. Defaults: `iceBaselineLPer100km = 7.0` (EU real-world fleet average), `gridIntensityGCo2PerKwh = 577.0` (Cyprus 2025 average per cyprusgrid.com), `PETROL_CO2_KG_PER_LITRE = 2.31` (EPA tank-to-wheel). Both prefs are user-editable in **Settings → CO₂ tracker**.

The card hides entirely when either pref is unset / 0 (never show one number without its companion). Saved can be negative on dirty-grid + short-distance periods; the card surfaces this honestly with a `±X.X kg vs petrol` line rather than hiding the result. Full methodology + caveats (tank-to-wheel vs well-to-wheel, average vs marginal grid intensity) live in [`docs/METHODOLOGY.md`](METHODOLOGY.md).

The Charts CO₂ tab renders cumulative running totals: solid EV line + dashed ICE-counterfactual line. The dashed style visually distinguishes "would have emitted" from "actually emitted". `prevOdo` chain advances unconditionally so a transient odometer rollback doesn't break the chain for subsequent valid deltas (mirrors the StatsCalculator pairwise convention above).

**(per-event live grid intensity) deferred.** No free real-time Cyprus carbon-intensity API is available today. The viable next path is ENTSO-E hourly mix + per-source IPCC AR6 emission factors. See `docs/METHODOLOGY.md` Open Issues for the data-source survey notes.

---

## 8. Google Drive Backup

**Scope:** `https://www.googleapis.com/auth/drive.appdata` (non-sensitive / non-restricted)

**Auth:** Authorization API (`Identity.getAuthorizationClient`), no `google-services.json`, no Firebase. The OAuth client is bound to package + signing-cert SHA-1 in Google Cloud Console.

**Location:** App Data folder (hidden from Drive UI; only this app can access it)

**File:** `evtracker_backup.json`

> The Android manifest sets `android:allowBackup="false"`, system Auto Backup is disabled deliberately. Google Drive (this section) is the canonical and only backup channel.

### Backup model

- The Drive file is a **full snapshot** of app data, not an append-only log and not a merge source.
- Enabling Drive follows a **replace-or-skip** model when a remote snapshot already exists.
- "Replace" means local `cars`, `charge_events`, and `custom_locations` are overwritten by the remote snapshot in one transaction.
- "Skip" means local data remains authoritative and future backups upload that local state.
- The app never attempts field-level or row-level merge between local and remote data.

### Backup JSON structure (v7), authoritative field list

All numeric timestamps are Unix epoch **milliseconds**; `exported_at` is ISO 8601 UTC.

`backup_version` is currently **7**. `BackupSerializer.fromJson` accepts `{3, 4, 5, 6, 7}`. v3's legacy `chargeType = "DC"` decodes to `ChargeType.DC_FAST` via `ChargeTypeJsonAdapter` + `ChargeType.parseLegacy`. v3/v4 Int ids narrow into v5+'s `Long` DTO fields automatically, Gson reads narrower JSON numbers into wider Kotlin fields without coercion. v3/v4/v5 backups simply leave the new optional `soc_before`/`soc_after` fields at `null`. v3..v6 backups likewise omit `kwh_source`; the DTO field is **nullable** (because Gson uses `Unsafe.allocateInstance` to construct Kotlin classes and bypasses primary-constructor defaults for absent JSON keys), and `ChargeEventDto.toEntity()` coalesces `null → MEASURED` so the entity's non-null contract holds. All older backups in the wild still restore.

```jsonc
{
  "backup_version": 7,
  "exported_at": "2026-04-26T10:00:00Z",
  "cars": [
    {
      "id": 1,
      "name": "Model 3",
      "make": "Tesla",
      "model": "M3",
      "year": 2024,                 // nullable
      "battery_kwh": 60.0,          // nullable
      "created_at": 1714044000000
    }
  ],
  "charge_events": [
    {
      "id": 17,
      "car_id": 1,
      "event_date": 1714043900000,
      "odometer_km": 12345.0,       // always km, never converted
      "kwh_added": 22.4,
      "charge_type": "AC",          // "AC" | "DC_FAST" | "DC_ULTRA" (legacy "DC" decoded to "DC_FAST" on read)
      "cost_total": 5.5,            // nullable
      "cost_per_kwh": 0.245,        // nullable
      "currency": "EUR",            // nullable; required when cost_total is non-null
      "location": "Home",           // nullable
      "note": "",
      "soc_before": 0.20,           // nullable; fraction 0.0..1.0
      "soc_after": 0.80,            // nullable; fraction 0.0..1.0
      "kwh_source": "MEASURED",     // "MEASURED" | "DERIVED_FROM_SOC"; absent on v3..v6 backups → MEASURED
      "created_at": 1714044000000
    }
  ],
  "custom_locations": [
    { "label": "Supercharger A6", "use_count": 4, "last_used": 1714000000000 }
  ]
}
```

A `BackupSchemaTest` round-trips a sample DB and asserts every field on every entity is present (catches forgotten-field regressions when the schema bumps).

### Restore flow
1. User enables Drive in Settings
2. OAuth consent shown (only on first authorize call; subsequent calls return tokens silently)
3. App fetches `evtracker_backup.json` from the App Data folder
4. If file exists: parse → show "Found backup from [date]. Restore?" dialog with explicit "This will replace any data already on this device." warning
5. On confirm:
  1. Export the **current** local DB to `cacheDir/last_overwritten_backup.json` before any destructive step
   2. Clear all rows from `cars`, `charge_events`, `custom_locations`
  3. Import the backup in the same database transaction as the clear step
   4. Set `driveEnabled = true`
  5. Surface a Settings entry "Undo restore" while `cacheDir/last_overwritten_backup.json` still exists; target retention is 24 h, but cache eviction may remove it sooner
6. On skip: keep local data unchanged, set `driveEnabled = true`, and continue with backup enabled going forward
7. If no file exists: set `driveEnabled = true` and queue an initial backup from the current local state

Restore notes:

- Restore is an explicit user action; the app must never auto-replace local data without confirmation.
- Restore and skip are the only two outcomes when a remote snapshot exists. Merge is not supported.
- After a successful restore, local data becomes the source of truth for all subsequent edits and backups.

#### Restore-prompt suppression

The destructive restore prompt is shown **at most once per remote snapshot identity.** Snapshot identity = the JSON `exported_at` ISO-8601 string of `evtracker_backup.json`.

- DataStore key `lastSeenRemoteBackupExportedAt: String` (default `""`) records the `exported_at` of the snapshot the user most recently **Skipped** or **Restored**.
- On every `SettingsViewModel.onDriveAuthGranted()` call, the VM compares the remote `exported_at` to the marker. When they match, Drive is silently re-enabled and `enqueueBackup()` runs without firing `ShowRestorePrompt`.
- `onSkipRestore()` writes the marker **before** flipping `driveEnabled` so a fast re-entry of Settings sees it populated.
- `onConfirmRestore()` captures `pendingExportedAt` from `SettingsUiState` **before** invoking `RestoreBackupUseCase`, then writes the marker on `RestoreResult.Success`. The semantic is "the local DB now equals this snapshot, never re-prompt to restore something the user already has."
- `onRestorePromptDismissed()` does **not** write the marker. Dismiss is neither accept nor decline; the next entry should still offer the snapshot.
- `WipeRemoteBackupUseCase` clears the marker (`setLastSeenRemoteBackupExportedAt("")`) on `BackupResult.Success`. After a wipe, the next committed local change creates a new snapshot with a different `exported_at`, and the next Drive re-toggle prompts exactly once for it.

The Drive switch listener in `SettingsFragment` is attached **lazily** by the `viewModel.uiState` collector after the first state-driven sync of `binding.switchDrive.isChecked`, never in `onViewCreated`. This prevents Android's view-state restoration (which calls `setChecked()` between `onCreateView` and `onStart`) from synchronously firing `onUserToggledOn()` and triggering an unwanted `auth.authorize()` round-trip on every Settings entry. Instrumented regression: `SettingsDriveSwitchEntryTest`.

### Manual controls

In addition to the WorkManager-driven auto-backup, Settings exposes two
manual actions, both visible only when `driveEnabled = true`:

| Action | Use case | Bypasses WorkManager? | Side effect on `lastBackupAt` |
|--------|----------|------------------------|-------------------------------|
| **Back up now** | `PushBackupNowUseCase` | Yes, invokes `BackupRepository.backupCurrentData()` directly so the user gets synchronous-feeling feedback | Updates to `now` on `Success`; untouched otherwise |
| **Wipe remote backup** | `WipeRemoteBackupUseCase` | Yes, invokes `BackupRepository.deleteRemoteBackup()` | Cleared to `0L` on `Success`; untouched otherwise |

Both wrap the same `BackupResult` contract from §Error model. Failure mapping in `SettingsViewModel`:

- `BackupResult.AuthRequired` → `R.string.drive_auth_failed`
- `BackupResult.Failure("Drive storage full")` → `R.string.drive_storage_full`
- Other `BackupResult.Failure` reasons → `R.string.drive_backup_now_failure` or `R.string.drive_wipe_failure` depending on the action

The auto-backup worker contract is **unchanged**, manual push is one extra path, not a replacement; WorkManager still triggers on every committed local change. Wipe is a point-in-time delete, not an opt-out: the next committed local change re-enqueues the worker, which creates a fresh remote snapshot.

`BackupRepository.deleteRemoteBackup()` short-circuits to `BackupResult.Success` when no remote file exists, the desired post-state ("no remote snapshot") is already true and a noisy error here would mask a clean outcome the user just asked for. Otherwise, it routes through the same `withRetry` + `runTranslating` machinery as the upload path, so transient failures on delete pick up 's retry budget.

The wipe button is gated behind a `MaterialAlertDialog` confirmation. The two operations are mutually exclusive, `isManualBackupRunning` and `isManualWipeRunning` flags on `SettingsUiState` disable the *other* button while one is in flight, and a duplicate tap on the same action is also a no-op (we don't stack uploads or deletes).

### Auto-backup trigger
- Queue a backup after every persisted change that affects the backup payload:
  - charge event create, edit, or committed delete
  - car create, edit, or delete
  - custom location committed delete
  - reset-all-data and per-car reset flows
  - successful restore
  - first-time Drive enable when no remote backup exists
- Do **not** queue backup for transient UI actions that are still undoable; enqueue only after the local state change is committed
- Implementation: WorkManager `OneTimeWorkRequest` enqueued via `enqueueUniqueWork("drive_backup", REPLACE, ...)` so rapid successive changes debounce to one upload
- Constraints: `NetworkType.CONNECTED`, offline saves queue and run when the device reconnects
- Backoff: WorkManager-level backoff is configured but rarely exercised, the repo's in-call retry loop (next subsection) absorbs all transient failures within a single worker invocation

### Error model and retry

`BackupRepository.backupCurrentData()` returns a terminal `BackupResult`:

| Variant | Trigger | UI / Worker reaction |
|---------|---------|----------------------|
| `Success` | upload completed | `Worker → Result.success()`; `lastBackupAt` written |
| `AuthRequired` | `silentToken()` failed, HTTP 401, HTTP 403 with auth-class reason (`appNotAuthorized`, `insufficientFilePermissions`, `insufficientPermissions`, `forbidden`), or HTTP 403 with unknown / unparseable body (conservative fallback) | `Worker → Result.failure()`; ****: also fires `BackupNotifier.notifyAuthRequired()` (channel `backup_auth`, IMPORTANCE_DEFAULT) |
| `Failure(reason, cause?)` | non-recoverable terminal outcome, currently `"Drive storage full"` (HTTP 403 `storageQuotaExceeded`), `"HTTP <n>"` if the retry budget exhausted on a 4xx/5xx, or `"Network failure: <ExceptionClass>"` if it exhausted on a transport `IOException` | `Worker → Result.failure()`; reason carries through to logs and (****) `BackupNotifier.notifyChronicFailure()` once `consecutiveBackupFailures` ≥ 3 |

`DriveBackupRepository` runs its own bounded retry loop inside `backupCurrentData()` and `readRemoteBackup()`:

- **Retry budget:** `MAX_ATTEMPTS = 3`, exponential backoff `250 ms × 2^attempt` (so 250 ms, 500 ms before the 2nd and 3rd tries; final attempt has no trailing delay).
- **Transient (retried):** transport `IOException` (incl. `UnknownHostException`), HTTP 429, HTTP 5xx, HTTP 403 with quota / rate reasons (`rateLimitExceeded`, `userRateLimitExceeded`, `quotaExceeded`).
- **Non-recoverable (short-circuits the loop):** auth errors, `storageQuotaExceeded` 403, unknown / unparseable 403 body.

The Worker's `doWork()` is therefore a thin `when (result)` translator that never returns `Result.retry()`, the repo has already exhausted its retry budget by the time it returns, and asking WorkManager to retry on top would amplify the backoff. WorkManager's `NetworkType.CONNECTED` constraint still gates the worker from running offline, which is the OS-level retry mechanism.

All non-recoverable paths log via `android.util.Log.e("DriveBackupRepository", reason, cause)`. JVM unit tests stub Android logging via `testOptions.unitTests.isReturnDefaultValues = true` in `app/build.gradle.kts`.

`readRemoteBackup()` keeps its existing exception contract (returns `String?`, throws `DriveAuthRequiredException` / `IOException`) because `SettingsViewModel.onDriveAuthGranted` and `RestoreBackupUseCase` already handle those, but it goes through the same `withRetry` wrapper, so transient blips on the read path retry too.

### Backup failure notifications

`BackupOutcomeReporter` (in `domain/notification/`) is a thin orchestrator the worker forwards each `BackupResult` through before translating to the WorkManager `Result` surface. It owns the persistent failure-streak counter and the threshold logic so both stay JVM-testable:

- **Counter:** `SettingsReader.consecutiveBackupFailures` (DataStore Int, default `0`). Reset to `0` on `Success`; incremented by `+1` on `AuthRequired` and `Failure`.
- **Threshold:** `BackupOutcomeReporter.CHRONIC_FAILURE_THRESHOLD = 3`. The first `Failure` that brings the counter to ≥ 3 (and every subsequent `Failure`) fires `notifyChronicFailure()`. `AuthRequired` is its own surface, it fires `notifyAuthRequired()` on every occurrence regardless of the counter.

`BackupNotifier` is a domain interface implemented by `AndroidBackupNotifier` (in `data/notification/`). It exposes three calls (`notifyChronicFailure`, `notifyAuthRequired`, `clearAll`) and posts via `NotificationManagerCompat.notify` wrapped in a `safeNotify` helper that gates on `areNotificationsEnabled()` (silent no-op when permission missing, also satisfies lint's `MissingPermission`).

Two channels are registered idempotently from `EVTrackerApp.onCreate` via `AndroidBackupNotifier.ensureChannels(this)`:

| Channel id | Importance | Purpose |
|------------|------------|---------|
| `backup_status` | LOW | Sticky chronic-failure card, failures ≥ 3 in a row. Cleared by `clearAll()` on the next successful backup. |
| `backup_auth` | DEFAULT | Auth-required card, fired whenever the worker returns `AuthRequired`. Higher importance because the user must actively re-consent; the auto-backup loop cannot recover on its own. |

Both notifications carry a `PendingIntent` built via `NavDeepLinkBuilder` that lands on `settingsFragment` so the user can tap directly into the Drive-status row.

**Permission flow (Android 13+).** The manifest declares `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`. `MainViewModel.shouldOfferNotificationPermission` is a `StateFlow<Boolean>` derived from `consecutiveBackupFailures >= CHRONIC_FAILURE_THRESHOLD && !notificationPermissionDenied`. `MainActivity` collects it and fires the rationale dialog only when:

1. `Build.VERSION.SDK_INT >= TIRAMISU` (pre-13 has no runtime gate); AND
2. `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS) != GRANTED`; AND
3. The dialog isn't already showing (`notificationRationaleShowing` re-entrancy guard).

Tapping **Allow** launches the system permission request. Tapping **Not now**, dismissing the dialog, or denying the system request all set the sticky `notificationPermissionDenied` flag, we **never** re-prompt. (If the user later enables notifications via system settings, the runtime check passes and notifications work; the in-app prompt just stays silent.)

The 8 JVM cases on `BackupOutcomeReporterTest` cover: success resets the counter and clears notifications; first/second failures don't fire chronic; third fires; fourth keeps firing; AuthRequired fires the auth surface every time and increments the counter without triggering chronic; success after a streak resets; AuthRequired at the threshold doesn't trigger the chronic surface.

---

## 8a. Home-screen widget

A 2×2 `AppWidgetProvider`-based tile (`widget/LastChargeWidget`) renders the most recent charge event for the active car at a glance. Layout uses `RemoteViews`-only views, no Glance / Compose-runtime dependency was introduced.

**Content (`Loaded` state):**

- **Car name**, top line, ⚡ icon prefix, single-line ellipsised.
- **Relative date**, "Today" / "Yesterday" / "N days ago" (2..6) / "N week(s) ago" (1 / 2..3) / `MMM d, yyyy` for events older than 28 days, via the platform formatter in the user's default locale.
- **kWh added**, pulled directly from the latest event.
- **Efficiency**, converted from canonical km/kWh (computed on the latest two events' odometer-delta, same convention as `StatsCalculator` in §7) to the user's `primaryMetric`: `km/kWh` / `kWh/100 km` / `mi/kWh`. Renders an em-dash when efficiency cannot be computed (single event, non-positive odometer delta, or zero kWh on the latest event).
- **Cost**, `NumberFormat.getCurrencyInstance` formatted, hidden when `costTotal` or `currency` is null.

**Empty state.** Active car unset OR no events for the active car → single centered TextView with `widget_empty_state`.

**Tap target.** `PendingIntent.getActivity(MainActivity)` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` so the widget always lands on the dashboard from the user's perspective.

**Refresh trigger.** A new narrow `domain/widget/WidgetRefresher` interface, bound to `data/widget/AndroidWidgetRefresher` in `WidgetModule`, is called from every snapshot-affecting use case alongside the existing `BackupScheduler.enqueueBackup()` call: `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`, `AddCarUseCase`, `DeleteCarUseCase`, `RenameCarUseCase`, `ResetActiveCarDataUseCase`, `ResetAllDataUseCase` (`runCatching` isolated, so a failure can't stick the reset-in-progress flag), and `RestoreBackupUseCase` (both branches). The Android impl re-enters `onUpdate` via a self-broadcast (`ACTION_APPWIDGET_UPDATE` with `EXTRA_APPWIDGET_IDS`) instead of calling `updateAppWidget` directly so the platform's normal lifecycle still applies.

**Hilt injection.** `AppWidgetProvider` is created by reflection by the platform and is not `@AndroidEntryPoint`-able. The provider grabs its `SettingsReader` / `CarReader` / `ChargeEventQueries` / `NowProvider` deps via `EntryPointAccessors.fromApplication(...)` from a new `LastChargeWidgetEntryPoint` interface.

**Manifest.** A `<receiver android:name=".widget.LastChargeWidget" android:exported="false">` with the `APPWIDGET_UPDATE` intent filter and metadata pointing at `res/xml/widget_last_charge_info.xml` (110×110 dp min, 2×2 cells, `updatePeriodMillis="0"` since refreshes are push-based, not polling).

**Pure-domain helper.** All math + relative-date bucketing lives in `domain/widget/LastChargeWidgetSnapshot.compute(activeCar, events, primaryMetric, nowMillis)`, kept JVM-testable. 18 cases on `LastChargeWidgetSnapshotTest` cover empty / single-event / two-event efficiency in all three metrics, negative-odometer / zero-kWh fallbacks, unsorted-input sorting, cost pass-through, every relative-date bucket, and `ChargeType` propagation.

---

## 9. Edge Cases

| Scenario | Handling |
|----------|----------|
| Cost = 0 | Stored as NULL; excluded from cost stats |
| First event for car | Efficiency = "—"; distance delta = 0 |
| Odometer regression | Validation error: "Odometer must be greater than previous entry" |
| Wizard killed mid-flow | `setupComplete` stays false; wizard re-shown on next launch |
| Custom locations > 5 | Only top 5 shown as chips; all accessible via Manage Locations |
| Unit change | Never rewrites stored km values; all display conversions are in-memory |
| Remote backup exists when Drive is enabled | Prompt user to replace or skip; never merge automatically |
| Drive backup fails (transient) | Repo retries up to 3 times with exponential backoff; if the budget exhausts the worker reports `Result.failure()` and the next committed local change re-enqueues a fresh worker. Local data is always source of truth; Settings shows the last-backup timestamp. |
| Drive backup fails (auth) | Repo returns `BackupResult.AuthRequired`; the worker fails fast (no retry) and will surface a re-auth notification once it lands. |
| Drive backup fails (storage full) | Repo returns `BackupResult.Failure("Drive storage full")`; no retry, retrying won't free Drive quota. |
| DB migration fails | Destructive fallback with user warning |

---

## 10. Localisation ( )

The app ships with four locales: English (`values/`, canonical), Greek (`values-el/`), Turkish (`values-tr/`), and Russian (`values-ru/`). The locale set targets Cyprus's resident populations, Greek and Turkish as the two communities, Russian for the significant immigrant community, plus English as the development source.

**Coverage contract.** `:app:lint` runs with `MissingTranslation` in **error** mode. Every `<string>` in `values/strings.xml` must either appear in all three locale files OR carry `translatable="false"`. CI fails on any drift. This is enforced by the existing static-analysis gate.

**`translatable="false"` policy.** Strings marked non-translatable fall into four categories:
- **Brand / proper names** that should not be localised (`app_name`, `about_acknowledgment_lab`, `about_acknowledgment_cut`).
- **URL display text** that mirrors the link target (`about_link_sps_lab`, `about_link_cut`).
- **Standardised unit abbreviations** that the global EV community uses in English regardless of locale (`metric_km_per_kwh`, `metric_kwh_per_100km`, `metric_mi_per_kwh`, and the wizard's compact-form variants).
- **International technical labels** that are English in every market (`filter_ac` / `filter_dc`, `charge_type_ac` / `charge_type_dc`, `charts_trend_legend_ac` / `charts_trend_legend_dc`).

**Plurals.** Localisation files use Android `<plurals>` with per-locale CLDR rules, `one`/`other` for English / Greek / Turkish; the full `one`/`few`/`many`/`other` set for Russian. The widget's "N week(s) ago" / "N days ago" fallbacks use these.

**Caveat.** The first-pass translations were LLM-produced. They cover every translatable string in lint-clean form, but require review by native speakers of each locale before any production release. Treat the locale files as a starting point, not finished prose.

### 10.1 Language picker

Two in-app entry points let users switch language without touching system settings:

- **Settings → Language** row (any time). Tap → `MaterialAlertDialog.Builder.setSingleChoiceItems` with five options: "Follow system" plus four autonyms. Persisted via the new `PreferenceKeys.LANGUAGE_TAG` DataStore key (default `""` = follow system; otherwise an IETF BCP-47 tag).
- **Wizard page 0** language row at the bottom of the welcome page. Same dialog. Selection persists immediately so a mid-wizard kill survives the next launch even though `setupComplete` is still false.

**Autonym rule.** Each language's name is shown in its own script: `English`, `Ελληνικά`, `Türkçe`, `Русский`. The autonym strings (`language_name_en` / `_el` / `_tr` / `_ru`) are deliberately marked `translatable="false"`, a Greek user looking for their language must see "Ελληνικά" written in Greek script regardless of the current app locale, otherwise the picker is useless to anyone who can't read English.

**Architectural boundary.** `AppCompatDelegate.setApplicationLocales` is a static framework call hostile to JVM tests. The new `domain/locale/LocaleApplier` interface (impl `data/locale/AndroidLocaleApplier`, bound via single-binding `LocaleModule`) wraps it; ViewModels depend on the interface. JVM tests substitute `FakeLocaleApplier` to assert the applied tag without booting AppCompat. Mirrors the `WidgetRefresher` pattern.

**Android 13+ system entry.** `res/xml/locales_config.xml` lists the four shipped locales and `AndroidManifest.xml` declares `android:localeConfig="@xml/locales_config"`. Android 13+ users get the OS-level per-app language entry at *System Settings → Apps → Joulie → Language* automatically, driven by the same `setApplicationLocales` call so it stays in sync with the in-app picker.

**Apply at start.** `EVTrackerApp.onCreate` reads the persisted tag asynchronously and calls `LocaleApplier.apply(...)`. AppCompat 1.6+ persists the value internally so subsequent app starts come up in the right locale before the coroutine even runs; the read is mainly a fail-safe for the first launch after a fresh install.

## 11. Accessibility (a11y)

**Target.** WCAG 2.1 AA. The app is intended for public use and must clear the AA bar on the surfaces a typical user touches: rendering, interaction, contrast on text and icons. AAA is aspirational and not gated. Cognitive accessibility, internationalised TalkBack vocabularies, and assistive-tech-specific testing harnesses are out of scope.

**Lint floor.** `app/build.gradle.kts` promotes three Android Lint rules from default-warning to PR-blocking error so future a11y drift cannot land silently:

| Rule | What it catches |
|------|-----------------|
| `ContentDescription` | `ImageView` / `ImageButton` / icon-only widgets without `android:contentDescription`. Decorative views must explicitly opt out via `android:contentDescription="@null"` or `android:importantForAccessibility="no"`. |
| `LabelFor` | `EditText` / `TextInputEditText` whose label lives in a separate `TextView` that lacks `android:labelFor`. TalkBack drops the label otherwise. |
| `KeyboardInaccessibleWidget` | View with an `OnClickListener` but `android:focusable="false"`, hidden from D-pad / keyboard / switch-access users. |

Touch-target sizing (WCAG 2.5.5) is enforced dynamically by Espresso's `AccessibilityChecks.enable().setRunChecksFromRootView(true)` interceptor wired in `HiltTestRunner.onStart()`: every Espresso `ViewAction` in every nightly instrumented test runs the `TouchTargetSizeCheck` validator against every view in the scanned root. Static-analysis lock-in is not available — `TouchTargetSizeCheck` is an Espresso `AccessibilityValidator` ID, not an Android Lint issue ID, and AGP's Lint rejects it as `UnknownIssueId`.

`app/lint-baseline.xml` is the registry of currently-known a11y debt. Existing entries are append-only-by-omission per CLAUDE.md (regenerate only when retiring a rule, never to "clean up"). New violations on the three promoted rules block PR merges.

The release-gating TalkBack smoke walkthrough lives in `docs/TEST_PLAN.md` §5c.
