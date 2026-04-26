# EV Efficiency Tracker — Full Product & Technical Design

## 1. Overview

**App Name:** EV Efficiency Tracker  
**Package:** `org.spsl.evtracker`  
**Min SDK:** API 26 (Android 8.0)  
**Target SDK:** API 34 (Android 14)  
**Language:** Kotlin  
**Architecture:** MVVM + Repository pattern  
**Build system:** Gradle (Kotlin DSL)

### Core Goals
- Simple, fast charge-event logging (mileage + kWh)
- Per-car efficiency statistics over flexible time periods
- Local-first; optional Google Drive backup
- No login required
- Beautiful Material You charts

---

## 2. Feature List

| # | Feature | Notes |
|---|---------|-------|
| F1 | Add/edit/delete charge event | mileage (km or mi), kWh, date-time, optional note |
| F2 | Multi-car management | Add/rename/delete cars; quick switch via Spinner or bottom sheet |
| F3 | Unit toggle | km ↔ miles; stored in preferences, applied globally |
| F4 | Statistics dashboard | Last charge, last 7 days, last 30 days, last year, custom range |
| F5 | Charts | Bar (monthly kWh), Line (efficiency trend), Scatter (kWh vs distance) |
| F6 | Local SQLite storage | No account needed; data lives in app DB |
| F7 | Google Drive backup | Optional; toggle in Settings; JSON export per car |
| F8 | Drive restore | On first activation, pulls all `evtracker_*.json` from Drive app folder |
| F9 | Data reset | Per-car reset or full reset in Settings |
| F10 | CSV export | Export charge log for selected car to Downloads |
| F11 | Dark/Light theme | Material You dynamic colour + manual override |
| F12 | Custom period analysis | Date-range picker → instant statistics update |

---

## 3. Data Model

### 3.1 SQLite Tables

#### `cars`
```sql
CREATE TABLE cars (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    make        TEXT,
    model       TEXT,
    year        INTEGER,
    created_at  INTEGER NOT NULL   -- epoch millis
);
```

#### `charge_events`
```sql
CREATE TABLE charge_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id      INTEGER NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
    event_date  INTEGER NOT NULL,   -- epoch millis
    odometer_km REAL    NOT NULL,   -- always stored in km; displayed in user unit
    kwh_added   REAL    NOT NULL,
    note        TEXT,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_ce_car_date ON charge_events(car_id, event_date);
```

### 3.2 Derived Metrics

| Metric | Formula |
|--------|--------|
| Efficiency (km/kWh) | distance_since_last_charge_km / kwh_added |
| Efficiency (mi/kWh) | efficiency_km_per_kwh × 0.621371 |
| Energy consumption (kWh/100km) | (kwh_added / distance) × 100 |
| Average over period | SUM(kwh) / SUM(distance_km) aggregated |

Distance since last charge = current_odometer − previous_odometer (for same car, ordered by event_date).

### 3.3 Google Drive Backup Format

File name pattern: `evtracker_<car_id>_<car_name_slug>.json`  
Stored in Google Drive **App Data folder** (hidden from user's Drive UI, no extra permissions).

```json
{
  "version": 1,
  "car": { "id": 1, "name": "Tesla Model 3", "make": "Tesla", "model": "Model 3", "year": 2023 },
  "events": [
    { "id": 1, "event_date": 1714000000000, "odometer_km": 12345.6, "kwh_added": 42.5, "note": "" }
  ]
}
```

---

## 4. Architecture

```
ui/
  MainActivity              — single-activity host, NavController
  cars/
    CarListFragment         — list + FAB to add car
    CarEditFragment         — create/edit car form
  dashboard/
    DashboardFragment       — stats cards + period selector
  log/
    ChargeLogFragment       — RecyclerView of charge events
    ChargeEditFragment      — add/edit charge event form
  charts/
    ChartsFragment          — MPAndroidChart views
  settings/
    SettingsFragment        — Drive backup toggle, units, theme, reset

viewmodel/
  CarsViewModel
  DashboardViewModel
  ChargeLogViewModel
  ChartsViewModel
  SettingsViewModel

repository/
  CarRepository
  ChargeEventRepository
  BackupRepository          — Drive API calls

data/
  db/
    AppDatabase             — Room database
    CarDao
    ChargeEventDao
  model/
    Car.kt
    ChargeEvent.kt
    Stats.kt                — data class for aggregated stats
  prefs/
    AppPreferences          — DataStore (units, theme, active car id, drive enabled)

drive/
  DriveBackupManager        — encapsulates Drive REST calls
  DriveAuthManager          — Google Sign-In (silent; no UI login needed for Drive app folder)

util/
  UnitConverter.kt
  DateUtils.kt
  CsvExporter.kt
  Extensions.kt
```

---

## 5. Navigation Graph

```
StartDestination: DashboardFragment

DashboardFragment
  ├── [FAB] → ChargeEditFragment (add)
  ├── [Row tap] → ChargeEditFragment (edit)
  ├── [Charts tab] → ChartsFragment
  ├── [Car name tap] → CarListFragment
  └── [Settings icon] → SettingsFragment

CarListFragment
  ├── [FAB] → CarEditFragment (add)
  └── [Row tap] → CarEditFragment (edit)
```

---

## 6. UI Screens Detail

### 6.1 Dashboard (Main Screen)

**Top bar:** App name | Car selector (Spinner or chip) | Settings icon

**Stats Cards (horizontal scroll or 2×2 grid):**
- Last Charge: X km/kWh · Y kWh · Z km
- Last 7 days: avg efficiency, total kWh, total km, # charges
- Last 30 days: same
- This Year: same
- Custom Period: date-range picker → same card

**Recent Log:** last 5 events in a compact list with swipe-to-delete

**FAB:** ➕ Log Charge

### 6.2 Charts Screen

**Tab bar:** Efficiency Trend | Monthly Energy | Scatter

1. **Efficiency Trend (Line chart):** X = date, Y = km/kWh per charge, rolling 30-day avg line
2. **Monthly Energy (Bar chart):** X = month, Y = total kWh charged
3. **Scatter (Scatter chart):** X = kWh added, Y = distance driven

All charts: pinch-zoom, highlight on tap (shows values), legend, dark/light aware colours.

### 6.3 Add/Edit Charge Event Form

Fields:
- Date & time (DateTimePicker, default = now)
- Odometer reading (km or mi, based on setting)
- kWh added
- Note (optional, single line)

Validation:
- Odometer must be > previous event's odometer for the same car
- kWh > 0
- Real-time error hints below fields

### 6.4 Car Management

- List of cars with make/model/year subtitle
- Swipe to delete (with confirmation dialog)
- Active car highlighted

### 6.5 Settings

| Setting | Type | Default |
|---------|------|---------|
| Distance unit | Toggle km/miles | km |
| Theme | Radio: Light / Dark / System | System |
| Active car | (set from dashboard) | — |
| Google Drive backup | Switch | Off |
| Backup now | Button (visible when Drive on) | — |
| Restore from Drive | Button (visible when Drive on) | — |
| Export to CSV | Button | — |
| Reset car data | Destructive button | — |
| Reset all data | Destructive button | — |

---

## 7. Google Drive Integration

### 7.1 Auth Flow (no visible login)

- Use **Google Sign-In** with `DriveScopes.APPFOLDER` scope
- Silent sign-in attempted on app start if Drive is enabled
- If silent sign-in fails → show one-time consent dialog
- After consent, all backup operations are background (WorkManager)

### 7.2 Backup Logic

```
ON FIRST ENABLE:
  1. Pull all evtracker_*.json from Drive App Folder
  2. Merge into local DB (insert new events; skip duplicates by id+date)
  3. Push current local DB to Drive

PERIODIC SYNC (daily, WiFi preferred):
  1. For each car, serialize to JSON
  2. Overwrite matching file in Drive App Folder
  
ON DEMAND ("Backup now"):
  Same as periodic sync, immediate
```

### 7.3 Conflict Resolution

Last-write-wins at the event level. Events with the same `id` and `car_id` but different data → Drive version wins during restore.

---

## 8. Dependencies (build.gradle app)

See `app/build.gradle.kts` for the complete, pinned dependency list.

Key libraries:
- **Room 2.6.1** — SQLite ORM
- **MPAndroidChart v3.1.0** — charts (via JitPack)
- **Google Play Services Auth 20.7.0** — Drive sign-in
- **Google Drive API v3** — file backup
- **WorkManager 2.9.0** — background sync
- **DataStore 1.0.0** — preferences
- **Navigation Component 2.7.6** — fragment navigation
- **Material 3 (1.11.0)** — UI components and theming

---

## 9. Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- No storage permissions needed: Room uses internal storage; Drive uses App Folder -->
```

---

## 10. Error Handling & Edge Cases

| Case | Handling |
|------|----------|
| First charge event for a car | No efficiency computed (no previous odometer); show "First charge — efficiency not yet available" |
| Drive auth failure | Toast + retry button; local data unaffected |
| Odometer lower than previous | Validation error on form |
| Delete car with events | Cascade delete after confirmation dialog |
| No events in selected period | Empty state illustration + "No charges recorded" |
| Unit change mid-use | All stored values remain in km; conversion applied at display time only |

---

## 11. Theming

- Base: `Theme.Material3.DayNight.NoActionBar`
- Primary colour: Electric blue `#1565C0`
- Secondary: Teal `#00796B`
- Chart palette: `[#1565C0, #00796B, #F57F17, #AD1457, #6A1B9A]`
- Typography: Roboto (system default)

---

## 12. File Structure to Generate

```
EV-android-app/
├── README.md
├── DESIGN.md                        ← this file
├── AGENT_INSTRUCTIONS.md
├── TEST_PLAN.md
├── LICENSE
├── settings.gradle.kts
├── build.gradle.kts                 ← project-level
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts             ← app-level
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/org/spsl/evtracker/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── AppDatabase.kt
│       │   │   │   │   ├── CarDao.kt
│       │   │   │   │   └── ChargeEventDao.kt
│       │   │   │   ├── model/
│       │   │   │   │   ├── Car.kt
│       │   │   │   │   ├── ChargeEvent.kt
│       │   │   │   │   └── Stats.kt
│       │   │   │   └── prefs/
│       │   │   │       └── AppPreferences.kt
│       │   │   ├── repository/
│       │   │   │   ├── CarRepository.kt
│       │   │   │   ├── ChargeEventRepository.kt
│       │   │   │   └── BackupRepository.kt
│       │   │   ├── drive/
│       │   │   │   ├── DriveAuthManager.kt
│       │   │   │   └── DriveBackupManager.kt
│       │   │   ├── ui/
│       │   │   │   ├── cars/
│       │   │   │   │   ├── CarListFragment.kt
│       │   │   │   │   └── CarEditFragment.kt
│       │   │   │   ├── dashboard/
│       │   │   │   │   └── DashboardFragment.kt
│       │   │   │   ├── log/
│       │   │   │   │   ├── ChargeLogFragment.kt
│       │   │   │   │   └── ChargeEditFragment.kt
│       │   │   │   ├── charts/
│       │   │   │   │   └── ChartsFragment.kt
│       │   │   │   └── settings/
│       │   │   │       └── SettingsFragment.kt
│       │   │   ├── viewmodel/
│       │   │   │   ├── CarsViewModel.kt
│       │   │   │   ├── DashboardViewModel.kt
│       │   │   │   ├── ChargeLogViewModel.kt
│       │   │   │   ├── ChartsViewModel.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   └── util/
│       │   │       ├── UnitConverter.kt
│       │   │       ├── DateUtils.kt
│       │   │       ├── CsvExporter.kt
│       │   │       └── Extensions.kt
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── fragment_dashboard.xml
│       │       │   ├── fragment_charge_log.xml
│       │       │   ├── fragment_charge_edit.xml
│       │       │   ├── fragment_charts.xml
│       │       │   ├── fragment_car_list.xml
│       │       │   ├── fragment_car_edit.xml
│       │       │   ├── fragment_settings.xml
│       │       │   ├── item_charge_event.xml
│       │       │   ├── item_car.xml
│       │       │   └── card_stats.xml
│       │       ├── navigation/
│       │       │   └── nav_graph.xml
│       │       ├── menu/
│       │       │   └── bottom_nav_menu.xml
│       │       ├── values/
│       │       │   ├── strings.xml
│       │       │   ├── colors.xml
│       │       │   └── themes.xml
│       │       ├── values-night/
│       │       │   └── themes.xml
│       │       └── drawable/
│       │           └── ic_launcher_foreground.xml
│       ├── test/
│       │   └── java/org/spsl/evtracker/
│       │       ├── UnitConverterTest.kt
│       │       ├── StatsCalculatorTest.kt
│       │       └── ChargeEventDaoTest.kt
│       └── androidTest/
│           └── java/org/spsl/evtracker/
│               ├── DashboardFragmentTest.kt
│               └── ChargeEditFragmentTest.kt
```
