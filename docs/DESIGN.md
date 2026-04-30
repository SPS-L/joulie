# EV Efficiency Tracker — Full Product & Technical Design (v3)

## 1. Overview

**App Name:** EV Efficiency Tracker  
**Package:** `org.spsl.evtracker`  
**Min SDK:** API 26 (Android 8.0)  
**Target SDK:** API 34 (Android 14)  
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
| F8 | Charts | Line (efficiency trend AC vs DC), Bar (monthly kWh), Bar (monthly cost), Pie (AC/DC split + location split) |
| F9 | Dashboard filter chips | All / AC / DC |
| F10 | Location quick-chips | Home, Work, Public (fixed) + up to 5 custom learned chips |
| F11 | Local SQLite storage | Room, no account needed |
| F12 | Google Drive backup | Optional; App Data folder (hidden from Drive UI); restore on first activation |
| F13 | Data reset | Per-car or global |
| F14 | CSV export | All fields included, sharing via FileProvider |
| F15 | Dark/Light/System theme | Material 3 DayNight |
| F16 | Custom period analysis | Date-range picker (MaterialDatePicker) |

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

Implemented as a `WizardFragment` backed by `ViewPager2` with 3 pages. Back/Next/Finish buttons are in the host fragment, not per-page. Progress dots shown via `TabLayout` or a custom indicator.

---

> Back/Next buttons live on the **host fragment**, not on each page. Page 1 hides the Back button; pages 2 and 3 show both. The host's Next button labels are: page 1 → "Get Started", pages 2/3 → "Next →", page 3 final state → "Finish ✓".

**Screen 1 of 3 — Welcome**

```
┌────────────────────────────────┐
│                                │
│  ⚡  EV Efficiency Tracker     │
│                                │
│  Let's set up your preferences │
│  — you can change these later  │
│  in Settings at any time.      │
│                                │
└────────────────────────────────┘
   (host buttons:  [Get Started])
```

---

**Screen 2 of 3 — Efficiency metric & units**

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

**Screen 3 of 3 — Currency**

```
┌────────────────────────────────┐
│  What currency do you use      │
│  for charging costs?           │
│                                │
│  [ ▼  EUR — Euro         ]     │
│                                │
│  Supported: EUR, USD, GBP,     │
│  CHF, JPY, CZK, PLN, HUF,     │
│  DKK, SEK, NOK, AUD, CAD      │
│                                │
│  ℹ  Cost entry is optional —  │
│  leave 0 to skip tracking.     │
│                                │
│  [← Back]        [Finish ✓]   │
└────────────────────────────────┘
```

Use an `AutoCompleteTextView` (ExposedDropdownMenu) populated from a `string-array` resource.

> Adding a currency = edit `res/values/currencies.xml` only — no code changes required.

### 3.3 DataStore Keys (complete list)

| Key | Type | Default | Written by |
|-----|------|---------|------------|
| `setupComplete` | Boolean | `false` | Wizard — on Finish |
| `primaryMetric` | String | `"km_per_kwh"` | Wizard / Settings |
| `distanceUnit` | String | `"km"` | Wizard / Settings |
| `currency` | String | `"EUR"` | Wizard / Settings |
| `activeCarId` | Int | `-1` | Car selector |
| `driveEnabled` | Boolean | `false` | Settings |
| `theme` | String | `"system"` | Settings only — **not** part of the wizard |

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

> The SQL below uses snake_case for readability. The actual database — generated by Room from entity declarations in `AGENT_INSTRUCTIONS.md` — uses **camelCase** column names (`costTotal`, `costPerKwh`, `useCount`, `lastUsed`). See §4.2.

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
    charge_type     TEXT    NOT NULL DEFAULT 'AC',  -- 'AC' | 'DC'
    cost_total      REAL,        -- NULL if user left cost blank/zero
    cost_per_kwh    REAL,        -- NULL if user left cost blank/zero
    currency        TEXT,        -- snapshot of currency at time of entry
    location        TEXT,        -- free text (chip label or typed)
    note            TEXT,
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

> **Column-naming note:** the SQL examples in §4.1 use snake_case for readability. The actual database — generated by Room from the entity declarations in `AGENT_INSTRUCTIONS.md` — uses **camelCase** column names (`costTotal`, `costPerKwh`, `useCount`, `lastUsed`, etc.). Migrations must match the entity casing exactly or Room will fail schema validation at startup.

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
- `ExportCsvUseCase`: maps stored km values to the selected display unit at export time and derives row-level metrics consistently.

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
- Cost summary row (hidden when all `costTotal IS NULL` for that period, **or** when the period contains more than one currency — in which case show a "Multi-currency period — cost stats hidden" banner instead)
- Empty states (mutually exclusive):
  - **No car** (`activeCarId == -1` or no `cars` rows) — copy "Add a car to get started", CTA "Add car" → CarsFragment
  - **No events for active car** — copy "Log your first charge to see stats here", CTA "Log charge" → ChargeEditFragment

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
  - Hint: "Leave blank to skip — won't affect statistics"
- Note field (optional, single line)
- Save → validates odometer > last entry, persists, updates `custom_locations`

### Charts
- Line chart: efficiency trend over time, AC series (blue) vs DC series (orange)
- Bar chart: monthly kWh consumed
- Bar chart: monthly cost (hidden if no cost data)
- Pie chart: AC vs DC split
- Pie chart: location distribution
- All charts use MPAndroidChart; support pinch-zoom and value markers

### History (HistoryFragment)
- Paginated `RecyclerView` of every `charge_event` for the active car, newest first
- Row shows: date, odometer (in display unit), kWh, AC/DC badge, location chip, cost (if non-null with currency)
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

First charge event for a car **cannot** compute efficiency (no prior odometer). Show "—" on card.

---

## 8. Google Drive Backup

**Scope:** `https://www.googleapis.com/auth/drive.appdata` (non-sensitive / non-restricted)

**Auth:** Authorization API (`Identity.getAuthorizationClient`) — no `google-services.json`, no Firebase. The OAuth client is bound to package + signing-cert SHA-1 in Google Cloud Console.

**Location:** App Data folder (hidden from Drive UI; only this app can access it)

**File:** `evtracker_backup.json`

> The Android manifest sets `android:allowBackup="false"` — system Auto Backup is disabled deliberately. Google Drive (this section) is the canonical and only backup channel.

### Backup model

- The Drive file is a **full snapshot** of app data, not an append-only log and not a merge source.
- Enabling Drive follows a **replace-or-skip** model when a remote snapshot already exists.
- "Replace" means local `cars`, `charge_events`, and `custom_locations` are overwritten by the remote snapshot in one transaction.
- "Skip" means local data remains authoritative and future backups upload that local state.
- The app never attempts field-level or row-level merge between local and remote data.

### Backup JSON structure (v3) — authoritative field list

All numeric timestamps are Unix epoch **milliseconds**; `exported_at` is ISO 8601 UTC.

```jsonc
{
  "backup_version": 3,
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
      "charge_type": "AC",          // "AC" | "DC"
      "cost_total": 5.5,            // nullable
      "cost_per_kwh": 0.245,        // nullable
      "currency": "EUR",            // nullable; required when cost_total is non-null
      "location": "Home",           // nullable
      "note": "",
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
- Constraints: `NetworkType.CONNECTED` — offline saves queue and run when the device reconnects
- Backoff: exponential, starting at 30 s

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
| Drive backup fails | Local data remains source of truth; retry via WorkManager and show last-backup timestamp in Settings |
| DB migration fails | Destructive fallback with user warning |
