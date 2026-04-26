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
│           [Get Started]        │
│                                │
└────────────────────────────────┘
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

### 3.3 DataStore Keys (complete list)

| Key | Type | Default | Written by |
|-----|------|---------|------------|
| `setupComplete` | Boolean | `false` | Wizard — on Finish |
| `primaryMetric` | String | `"km_per_kwh"` | Wizard / Settings |
| `distanceUnit` | String | `"km"` | Wizard / Settings |
| `currency` | String | `"EUR"` | Wizard / Settings |
| `activeCarId` | Int | `-1` | Car selector |
| `driveEnabled` | Boolean | `false` | Settings |

All keys declared as `Preferences.Key<T>` constants in a `PreferenceKeys` object.

### 3.4 Edge Cases

- User kills app mid-wizard → `setupComplete` stays `false`; wizard shown again next launch
- User taps **Reset preferences** in Settings → set `setupComplete = false`; navigate to wizard
- Wizard page 2: selecting **mi/kWh** auto-selects **miles** unit (and vice versa for km)

---

## 4. Data Model

### 4.1 SQLite Tables

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
| 2 | Added `charge_type`, `location`, `note` columns to `charge_events` |
| 3 | Added `custom_locations` table; added cost columns |

Migration 2→3 must `CREATE TABLE custom_locations …` and `ALTER TABLE charge_events ADD COLUMN cost_total REAL` etc.

---

## 5. Architecture

```
┌─────────────────────────────────────────────────────┐
│  UI Layer (Fragments + ViewModels)                  │
│  WizardFragment · DashboardFragment                 │
│  ChargeEditFragment · CarsFragment · SettingsFragment│
│  ChartsFragment · HistoryFragment                   │
├─────────────────────────────────────────────────────┤
│  Repository Layer                                   │
│  CarRepository · ChargeRepository                  │
│  LocationRepository · StatsRepository               │
│  PrefsRepository · DriveRepository                 │
├─────────────────────────────────────────────────────┤
│  Data Layer                                        │
│  Room (CarDao, ChargeEventDao, CustomLocationDao)   │
│  Preferences DataStore (PreferenceKeys)             │
│  Drive API (AppDataFolder, JSON backup)             │
└─────────────────────────────────────────────────────┘
```

### Key ViewModels

| ViewModel | Owns |
|-----------|------|
| `WizardViewModel` | Wizard page state; writes all DataStore keys on finish |
| `DashboardViewModel` | Active car; period filter; stats StateFlow |
| `ChargeEditViewModel` | Form state; location chips Flow; cost parsing |
| `CarsViewModel` | Car list; add/rename/delete |
| `SettingsViewModel` | Prefs; reset; Drive toggle |
| `ChartsViewModel` | Chart data for selected period |

---

## 6. UI Screens Detail

### Dashboard
- Car spinner top-right (fast switch)
- Period tabs: Last charge / 7d / 30d / Year / Custom
- Filter chips row: All · AC · DC
- **Primary metric card** (large): value derived from `primaryMetric` pref
- Secondary metric cards (smaller): remaining 2 efficiency metrics
- Cost summary row (hidden when all `cost_total IS NULL` for that period)
- "No data yet" empty state with CTA to log first charge

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

### Settings
- Primary efficiency metric (RadioGroup)
- Distance unit (toggle)
- Currency (dropdown)
- Theme: Light / Dark / System
- **Google Drive backup** (Switch)
  - On enable: show Drive auth flow; on success pull backup, merge, confirm
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

**Location:** App Data folder (hidden from Drive UI; only this app can access it)

**File:** `evtracker_backup.json`

### Backup JSON structure (v3)
```json
{
  "backup_version": 3,
  "exported_at": "2026-04-26T10:00:00Z",
  "cars": [ { ...all car fields... } ],
  "charge_events": [ { ...all charge_event fields... } ],
  "custom_locations": [ { "label": "Supercharger A6", "use_count": 4 } ]
}
```

### Restore flow
1. User enables Drive in Settings
2. OAuth consent shown
3. App fetches `evtracker_backup.json` from App Data folder
4. If file exists: parse → show "Found backup from [date]. Restore?" dialog
5. On confirm: clear local DB, import backup, set `driveEnabled = true`
6. On skip: keep local data, continue with Drive backup enabled going forward
7. If no file exists: start backup schedule immediately

### Auto-backup trigger
- After every successful charge event save (WorkManager `OneTimeWorkRequest`, network NOT required)

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
| Drive backup fails | Silent retry via WorkManager; show last-backup timestamp in Settings |
| DB migration fails | Destructive fallback with user warning |
