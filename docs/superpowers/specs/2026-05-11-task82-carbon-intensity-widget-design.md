# TASK-82 — Carbon-intensity dashboard widget + boot refresh + History CO₂ row

**Filed:** 2026-05-11
**Branch:** `feat/task82-carbon-intensity-widget`
**Builds on:** TASK-80 (Electricity Maps integration), TASK-81 (drop static grid pref + persistent throttle)
**Version bump:** `1.11.3 → 1.11.4` (z-patch — additive UI feature, no schema change)
**Status:** spec approved 2026-05-11

---

## 1. Overview

Surface the live grid carbon intensity to the user so they know whether now is a good time to charge. Three deliverables in one PR:

1. **Boot refresh** — when the app process starts, kick off `RefreshCarbonIntensityUseCase` so the cached intensity is up-to-date by the time the user lands on the Dashboard.
2. **Dashboard pill widget** — a top-of-screen card showing the current intensity value, a 5-band colour, a one-word bucket label, and "Updated X ago". Includes skeleton + tap-to-retry for missing or stale data.
3. **History row CO₂ line** — each charge event in the History list renders a third metadata line `"⚡ X kg CO₂ · Y g/kWh"` when the event has a captured intensity and CO₂ tracking is on.

The existing persistent 1-hour throttle from TASK-81 is unchanged — every fetch path (boot / save / dashboard tap) goes through `CarbonIntensitySource.fetchCarbonIntensity(zone, apiKey)`, which serves from cache when within an hour.

---

## 2. Behaviour

### 2.1 Boot refresh

- `MainViewModel.init` adds `viewModelScope.launch { refreshCarbonIntensityUseCase() }` alongside the existing wizard / reset-recovery startup logic.
- `RefreshCarbonIntensityUseCase` is a thin wrapper: reads `co2Enabled` / `apiKey` / `zone` from `SettingsReader.first()`, returns `false` if either gate is off, otherwise calls `carbonIntensitySource.fetchCarbonIntensity(zone, apiKey)` and returns `true` on a non-null result.
- The repo's `Mutex` + persistent cache make the call cheap if data was fetched within the last hour. After 1h or zone change → network call → cache updated.

### 2.2 Dashboard pill widget

**Placement:** new `<include>` at the top of `fragment_dashboard.xml`'s scroll content, above the period filter chips. The pill is a `MaterialCardView` with a tintable background, a single-line value row, and a single-line "updated X ago" subtitle.

**State machine** (one `CarbonIntensityUiState` sealed class):

| State | Condition |
|---|---|
| `Hidden` | `co2Enabled == false` OR `apiKey.isBlank()` |
| `Loading` | `co2Enabled && apiKey set && isRefreshing && no fresh cache` |
| `Ready(value, bucket, fetchedAtMs)` | `co2Enabled && apiKey set && cacheZone == currentZone && now - cacheFetchedAtMs < CACHE_TTL_MS` |
| `Error` | `co2Enabled && apiKey set && !isRefreshing && (cache missing OR stale OR zone mismatch)` |

`Hidden` makes the widget `View.GONE` (no layout space). The other three are visible with different content + a state-specific tap behaviour (only `Error` is tappable).

**Trigger sites for `fetchCarbonIntensity`** (all serialised by the repo's `Mutex`, all throttled to once-per-zone-per-hour):

| # | Site | Purpose |
|---|---|---|
| 1 | `MainViewModel.init` | Cold-start warm-up. Fires before any fragment attaches. |
| 2 | `DashboardViewModel.init` | First-Dashboard-attach catch-all. Fires even if MainViewModel's fetch failed silently. |
| 3 | `SaveChargeEventUseCase` | Per-save (unchanged from TASK-80). |
| 4 | `DashboardViewModel.onRefreshTapped` | User-initiated retry from the `Error` state. |

#1 and #2 are belt-and-suspenders. The `Mutex` makes overlapping calls safe; the persistent cache makes the second call essentially free (cache-check, no HTTP).

### 2.3 History row CO₂ line

A third metadata line below the existing cost/location line on each `HistoryFragment` row, format `"⚡ {kg} kg CO₂ · {g/kWh} g/kWh"`. Visible only when:

- `event.gridIntensityGCo2PerKwh != null` AND
- `settingsReader.co2Enabled.first() == true`.

When CO₂ is toggled off, the line disappears across the History list. Re-enabling brings it back without data migration — the per-event intensity stays on the entity.

Per-row kg = `event.kwhAdded × event.gridIntensityGCo2PerKwh / 1000`, computed inline in the binder.

---

## 3. Architecture & components

### 3.1 New code

| Layer | File | Purpose |
|---|---|---|
| Domain use case | `domain/usecase/RefreshCarbonIntensityUseCase.kt` | Reads `co2Enabled`/`apiKey`/`zone`, calls `CarbonIntensitySource.fetchCarbonIntensity`. Returns `Boolean`. |
| Domain helper | `domain/service/CarbonIntensityFormatter.kt` | Pure function `(co2Enabled, apiKey, currentZone, cacheZone, cacheIntensity, cacheFetchedAtMs, now, isRefreshing) → CarbonIntensityUiState`. JVM-testable, no Android types. |
| Core model | `core/model/CarbonIntensityUiState.kt` | Sealed class + `CarbonIntensityBucket` enum with `@ColorRes bucketColorRes`, `@ColorRes textColorRes`, `@StringRes labelRes`. |
| UI helper | `ui/dashboard/CarbonIntensityRenderer.kt` | Maps a `CarbonIntensityUiState` onto the pill's child views (tint, value text, bucket label, "Updated X ago", visibility, tap handler). Keeps `DashboardFragment` thin. |
| Layout | `res/layout/widget_carbon_intensity.xml` | The pill. Included from `fragment_dashboard.xml`. |
| Res | `values/colors.xml`, `values-night/colors.xml` | 5 bucket-background colours + 5 paired text colours (white or black per WCAG). |
| Strings | `values/strings.xml` + el/tr/ru | Bucket labels, widget title, "Fetching…", "Tap to retry", "Updated %s ago", "%1$s kg CO₂ · %2$s g/kWh" history line. |

### 3.2 Modified code

- `domain/usecase/NowProvider.kt` — used as-is via Hilt for the formatter's `now` parameter.
- `DashboardViewModel` — adds a `combine` of six `SettingsReader` flows + `_isRefreshing: MutableStateFlow<Boolean>` into `carbonIntensity: StateFlow<CarbonIntensityUiState>` via `CarbonIntensityFormatter`. Adds `onRefreshTapped()`. Init kicks off a refresh.
- `MainViewModel.init` — adds the fire-and-forget refresh call.
- `DashboardFragment` — observes `viewModel.carbonIntensity` in the existing `repeatOnLifecycle(STARTED)` block, calls `CarbonIntensityRenderer.render(...)`. Tap forwards to `viewModel.onRefreshTapped()`.
- `fragment_dashboard.xml` — `<include layout="@layout/widget_carbon_intensity"/>` at the top of the scroll content.
- `HistoryListAdapter` (or equivalent ViewHolder) — adds the third metadata line bound from the event's `gridIntensityGCo2PerKwh` + a `co2Enabled` flag passed from `HistoryViewModel`.
- `HistoryViewModel` — exposes `co2Enabled: StateFlow<Boolean>` so the adapter can gate the new line.
- `fragment_history.xml` (or the row layout) — adds a third `TextView` (id `text_co2_line`, GONE by default).

### 3.3 Hilt wiring

`CarbonIntensitySource` and `SettingsReader` are already bound. `RefreshCarbonIntensityUseCase` and `CarbonIntensityFormatter` are simple `@Inject constructor`'d classes — no module changes needed.

---

## 4. Bucket math + colour tokens

```
Range (g/kWh)   Bucket        Background          Text
─────────────   ───────────   ─────────────────   ────────
   < 150        VERY_LOW      #3DC047 (green)     #000000
   150 – 399    LOW           #9CC747 (lime)      #000000
   400 – 649    MODERATE      #E29A2C (orange)    #000000
   650 – 899    HIGH          #A53A26 (red)       #FFFFFF
   ≥ 900        VERY_HIGH     #1A1A1A (near-black) #FFFFFF
```

Colours are sampled from the gradient image the user referenced (`0 → 1500 gCO₂eq/kWh`). The exact hex values above are the **intent**; the impl pass MUST verify each `(bg, text)` pair against `M3ContrastAuditTest` (WCAG AA 4.5:1) and adjust by one or two notches if the pair fails. `HIGH` is deliberately slightly darker than the gradient's mid-red so the white text passes 4.5:1.

Night-mode tokens stay identical — the bucket colour IS the signal; dimming it for dark mode would weaken the affordance.

Cyprus average (~577 g/kWh) lands in `MODERATE` (orange).

---

## 5. Strings (translatable: en/el/tr/ru)

| Key | English |
|---|---|
| `dashboard_carbon_intensity_title` | Carbon intensity |
| `carbon_bucket_very_low` | Very low |
| `carbon_bucket_low` | Low |
| `carbon_bucket_moderate` | Moderate |
| `carbon_bucket_high` | High |
| `carbon_bucket_very_high` | Very high |
| `carbon_intensity_value` | `%1$s gCO₂eq/kWh` (translatable=false; unit shared across all locales) |
| `carbon_intensity_fetching` | Fetching… |
| `carbon_intensity_tap_to_retry` | Tap to retry |
| `carbon_intensity_updated_ago` | `Updated %1$s ago` |
| `carbon_intensity_a11y_description` | `Carbon intensity %1$s grams CO₂ per kilowatt-hour, %2$s, updated %3$s ago. Tap to refresh.` |
| `history_event_co2_line` | `%1$s kg CO₂  ·  %2$s g/kWh` (translatable=false; matches CSV's mixed-locale numeric style) |

The "Updated X ago" uses `DateUtils.getRelativeTimeSpanString(...)` from `android.text.format` — already in use elsewhere for relative dates, so it picks up the system locale automatically.

---

## 6. Threading

- All fetches run on `Dispatchers.IO` inside `ElectricityMapsRepository` (existing).
- `MainViewModel` and `DashboardViewModel` launch refreshes via `viewModelScope`. Cancellation on VM clear is correct — a half-done fetch can be abandoned; the persistent cache is only written on success.
- DataStore reads from `SettingsReader` are already `Flow<>` and survive coroutine cancellation safely.

---

## 7. Error handling

- Refresh failure (network down, blank key, etc.): `_isRefreshing` flips back to false; the state combiner evaluates to `Error`; pill shows "Fetching… (Tap to retry)".
- No notification, no toast — refresh failures are silent. The Drive backup notification channel already carries enough sticky failure UX; another channel would be noise.
- A zone change while the app is running falls through to the `Error` state on next observation (cacheZone ≠ currentZone), prompting a tap-to-retry. Zone changes are rare; we don't auto-fire a refresh when the zone setting changes. Documented limitation.

---

## 8. Tests

### 8.1 JVM unit tests

| File | Coverage |
|---|---|
| `CarbonIntensityFormatterTest` | One test per state transition (`Hidden` × 2 reasons, `Loading`, `Ready` × 5 buckets at boundary values, `Error` × 3 sub-reasons). |
| `RefreshCarbonIntensityUseCaseTest` | `co2Enabled=false → false, no fetch`; `apiKey blank → false, no fetch`; `co2Enabled=true + key set + fetch returns value → true`; `co2Enabled=true + key set + fetch returns null → false`. Uses `FakeSettingsReader` + `FakeCarbonIntensitySource`. |
| `DashboardViewModelCo2WidgetTest` | `init fires refresh`; `onRefreshTapped fires refresh`; state derivation against canned `FakeSettingsReader` + `FakeCarbonIntensitySource` configurations. |
| `HistoryRowCo2FormatterTest` | Static helper test: `(intensity=null, co2Enabled=true) → empty`; `(intensity=412, co2Enabled=false) → empty`; `(intensity=412, kwh=10, co2Enabled=true) → "4.12 kg CO₂ · 412 g/kWh"`. |

### 8.2 Roborazzi

**Out of scope** for this PR. Dashboard has no baselines today (TASK-79 covered ChartsTab only). Adding 5 buckets × 2 themes × the new widget = 10 new baselines is forward-work and would need a fresh `HiltTestActivity` + `FakeDashboardParentFragment` rig. Filed as **TASK-86** in `docs/BACKLOG.md`.

### 8.3 Manual QA

Acceptance walkthrough before merge:

- [ ] Fresh install → CO₂ off → Dashboard shows no widget.
- [ ] Enable CO₂ + set Electricity Maps key + return to Dashboard → widget shows "Fetching…" briefly, then the value + bucket colour.
- [ ] Kill app, re-open within 5 min → widget shows cached value (no network call; `airplane mode + re-open` makes this visible).
- [ ] Kill app, re-open after >1h → widget shows "Fetching…", then the new value.
- [ ] Toggle CO₂ off → widget disappears.
- [ ] History list shows "X kg CO₂ · Y g/kWh" on events saved with intensity captured.
- [ ] Disable CO₂ → History line disappears across all rows.
- [ ] Re-enable CO₂ → History line reappears without re-saving any events.

---

## 9. Accessibility

- Pill `contentDescription`: built from `R.string.carbon_intensity_a11y_description` with `(value, bucket label, relative-time)`.
- Tap state: `android:focusable="true"` + `android:clickable="true"` only when in `Error`. In `Ready`/`Loading`, the pill is informational, not interactive.
- All 5 bucket `(background, text)` pairs pass WCAG AA 4.5:1 (verified by extending `M3ContrastAuditTest` with 5 new rows). If any pair fails, adjust the hex by one or two steps until it passes — the bucket distinctions only need to be perceptible, not pixel-perfect.
- TalkBack announcement on retry: `Snackbar` "Updated" / "Update failed" depending on `RefreshCarbonIntensityUseCase` return.

---

## 10. Out of scope (forward work)

| Item | Where filed |
|---|---|
| Periodic in-foreground refresh (every N minutes while app is open). | TASK-84 (filed in `docs/BACKLOG.md`). |
| Auto-refresh on zone-setting change. | TASK-85. |
| Dashboard Roborazzi baselines (5 buckets × 2 themes). | TASK-86. |
| Charts CO₂ tab: render the live "right now" value as a horizontal reference line on the cumulative-trend chart. | TASK-87. |
| Geolocation-based zone autodetect — explicitly rejected by the user (no new permission requests). | n/a (closed at design time). |

---

## 11. Acceptance criteria

A merged PR satisfies TASK-82 if:

1. `:app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:verifyRoborazziDebug` all green locally.
2. All four JVM test files from §8.1 are present and green.
3. `M3ContrastAuditTest` has five new rows covering each bucket's (bg, text) pair, all passing 4.5:1.
4. Strings exist in `values/`, `values-el/`, `values-tr/`, `values-ru/` (MissingTranslation gate stays green).
5. Manual QA walkthrough §8.3 passes on a debug build.
6. `CLAUDE.md` updated with: new `CarbonIntensityBucket` enum, new `RefreshCarbonIntensityUseCase`, new file paths.
7. `docs/BACKLOG.md` entry for TASK-82 closed with the "Done YYYY-MM-DD" outcome block; entries for TASK-83..TASK-87 filed.
8. `versionCode 58 → 59` / `versionName 1.11.3 → 1.11.4`.
9. Branch merged `--no-ff` to `main`; tag `v1.11.4` pushed; feature branch deleted.

---

## 12. Follow-up work in the same iteration

After TASK-82 merges, **TASK-83** updates the marketing surface:

- `sps-l.github.io/joulie` landing page (deployed by `.github/workflows/pages.yml` on push to main).
- README feature list.
- Recapture the Dashboard screenshot to include the new pill.
- `PRIVACY.md` line about the once-per-hour Electricity Maps fetch (opt-in, no PII transmitted beyond the API key + zone).
- v1.11.x changelog entry.

TASK-83 is filed as a separate spec to keep the diff focused.
