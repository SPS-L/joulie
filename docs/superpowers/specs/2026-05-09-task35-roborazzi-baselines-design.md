# TASK-35 — Roborazzi screenshot baselines for Dashboard + Charts (design)

**Date:** 2026-05-09
**Backlog entry:** `docs/BACKLOG.md` TASK-35 (🟢)
**Prerequisite for:** TASK-30 (MPAndroidChart → Vico migration). TASK-30 cannot start until this lands.
**Out-of-scope screens:** ChargeEdit, Cars, History, Settings, ManageLocations, Wizard.

## 1. Goal

Lock the visual rendering of Dashboard + every Charts tab into a versioned set of PNGs that CI verifies on every push. After this lands, TASK-30's "looks identical" acceptance criterion is a verified pass/fail (`./gradlew :app:verifyRoborazziDebug`), not an eyeball walkthrough.

The two concrete regression classes this guards against are documented in TASK-35's BACKLOG preamble:

1. The 2026-05-01 dark-mode chart-text bug fixed by `c677a2b` (theme-coupled rendering, slipped past unit + Espresso gates).
2. The DC `#FB8C00` orange (M3 tertiary seed) contrast risk on dark surfaces flagged by TASK-18.

## 2. Scope decisions

| Decision | Choice | Rationale |
|---|---|---|
| Chart tabs to baseline | **All 7** (`Trend`, `Monthly kWh`, `Monthly cost`, `AC vs DC`, `Locations`, `Degradation`, `CO2`) | TASK-30 swaps every chart; partial coverage = partial verification. Adds 3 images vs. the original spec's 4-tab list. |
| Theme matrix | **Light + dark** only | Catches the regression class that motivated this task. Dynamic-color (Material You) and RTL are forward-work; Robolectric's dynamic-color support is brittle and Dashboard/Charts are mostly numeric. |
| Render path | **Stubbed ViewModel + Robolectric `ComponentActivity`** (Approach A) | Avoids the Hilt + Robolectric + WorkManager combo that took 17 follow-up tasks (TASK-58…74) to stabilise on the nightly suite. Render path is exactly what TASK-30 changes, so render-path tests are the right granularity. |

Total baseline count: **20 PNGs** (3 Dashboard + 7 Charts) × 2 themes.

## 3. Architecture

### 3.1 File layout

```
app/src/test/java/org/spsl/evtracker/screenshots/
  RoborazziSetup.kt                — common @Rule, theme switcher, fragment-host helper
  DashboardScreenshotTest.kt       — 3 states × 2 themes = 6 @Test methods
  ChartsScreenshotTest.kt          — 7 tabs × 2 themes = 14 @Test methods
  fixtures/
    DashboardFixtures.kt           — Stats / DashboardScreenState builders
    ChartsFixtures.kt              — 12-month dataset → ChartsScreenState builders

app/src/test/screenshots/          — committed baseline PNGs (20)
```

### 3.2 Test rule + theming

`RoborazziSetup.kt` exposes:

- `RoborazziRule` (Roborazzi's JUnit rule, configured with the project's M3 theme).
- `withTheme(NIGHT|DAY) { … }` block that flips `Configuration.uiMode` before laying out the fragment.
- `hostFragment(fragment, themedActivityScenario)` that attaches the fragment to a transient Robolectric `ComponentActivity` themed with `Theme.EVTracker`, lays out at a fixed `pixelDensity = 2.0f` and `screenWidthDp = 411` (matches Pixel 4a, the project's reference device).

### 3.3 Stubbed-ViewModel pattern

The fragment's `viewModels()` delegate is overridden via a thin per-test factory that returns a fake VM emitting a hardcoded `MutableStateFlow<XxxScreenState>` (already a project convention — see `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`). No Hilt graph is involved. Concretely for Charts, all 7 tab tests share **one** `ChartsScreenState.Loaded(...)` fixture; only `ARG_KIND` differs between them.

### 3.4 Determinism

- All fixtures use a frozen `Calendar` set to `2026-01-01T12:00Z` so month-bucket labels and date-range subtitles are stable across machines.
- Locale is forced to `en-US` for the test JVM (already the project default for unit tests).
- Roborazzi's `RoborazziOptions(recordOptions = RecordOptions(resizeScale = 1.0))` is left at default; `compareOptions.changeThreshold = 0.01` (Roborazzi default) is the gate.

## 4. Data fixtures

### 4.1 Dashboard (3 fixtures × 2 themes = 6 images)

| Fixture | Stats payload | Visible elements |
|---|---|---|
| `dashboard_empty` | `Stats()` defaults; no events | "No charges yet" empty state, all metric cards hidden |
| `dashboard_mixed_eur` | 5 events: 3 AC + 2 DC, all `EUR`, costs €8–€18, kWh 12–32, odo deltas 80–230 km, period = "Last 30 days" | Cost cards visible (€/kWh + total), efficiency cards, AC/DC count chips, CO₂ tracker hidden (no prefs set in fixture) |
| `dashboard_multi_currency` | 4 events: 2 EUR + 2 GBP, period = "Last 30 days" | Multi-currency banner replaces cost cards; non-cost stats remain |

### 4.2 Charts (7 fixtures × 2 themes = 14 images)

One shared 12-month dataset on `Car("Tesla Model 3", batteryKwh = 60.0)`:

- 24 events spread across 12 months
- Costs in EUR
- Locations: Home (12), Work (6), Public (4), Custom "Office" (2)
- AC/DC ratio 18:6
- Odometer monotonic 8 000 → 18 000 km

The fixture builds **one** `ChartsScreenState.Loaded(...)` with all six metric fields populated (`trend`, `monthly`, `acDc`, `locations`, `degradation`, `co2Cumulative`); each tab test instantiates `ChartsTabFragment` with a different `ARG_KIND` and the same fixture state.

| Fixture-tab | Notable visible content |
|---|---|
| `charts_trend_*`        | AC + DC line series, 12 monthly buckets, M3 `colorOnSurface` axis labels |
| `charts_monthly_kwh_*`  | 12 stacked AC/DC bars (TASK-30 risk: `ColumnCartesianLayer` port) |
| `charts_monthly_cost_*` | 12 EUR cost bars |
| `charts_ac_dc_*`        | Pie tab — 18 AC vs 6 DC, **DC `#FB8C00` orange on dark surface** (the explicit risk this task targets) |
| `charts_locations_*`    | Pie tab — 4 slices via `LOCATION_PALETTE` |
| `charts_degradation_*`  | Line + dashed `nominal_battery_kwh = 60 kWh` reference (the second documented risk) |
| `charts_co2_*`          | Two-series cumulative: solid EV + dashed ICE counterfactual (TASK-20 surface) |

## 5. Build wiring

### 5.1 `gradle/libs.versions.toml`

```toml
[versions]
roborazzi   = "1.46.0"   # current stable as of 2026-05-09; verify-latest at impl time
robolectric = "4.16"     # fresh add — not currently in the project

[libraries]
roborazzi              = { group = "io.github.takahirom.roborazzi", name = "roborazzi",            version.ref = "roborazzi" }
roborazzi-junit-rule   = { group = "io.github.takahirom.roborazzi", name = "roborazzi-junit-rule", version.ref = "roborazzi" }
robolectric            = { group = "org.robolectric",               name = "robolectric",          version.ref = "robolectric" }

[plugins]
roborazzi              = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

### 5.2 `app/build.gradle.kts`

- `plugins { id("io.github.takahirom.roborazzi") }`
- `android.testOptions { unitTests.isIncludeAndroidResources = true }` (Robolectric requirement)
- `dependencies { testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.junit.rule); testImplementation(libs.robolectric) }`
- `roborazzi { outputDir.set(file("src/test/screenshots")) }` (commits live next to test sources)

### 5.3 `.gitattributes` + `.gitignore`

- `.gitattributes`: `app/src/test/screenshots/*.png binary` — stops Git from text-diffing the PNGs.
- `.gitignore`: `app/src/test/screenshots/*.actual.png` and `*.diff.png` — Roborazzi's failure artefacts must never be committed; only the approved baselines are tracked.

## 6. CI integration

### 6.1 PR gate

Append `:app:verifyRoborazziDebug` to the `:app:testDebugUnitTest` step in `.github/workflows/ci.yml`. Single job, single cache, no new matrix node. The verify task is a no-op when there's no diff and a hard fail when there is.

The pre-existing job already runs `ktlintCheck` + `:app:lint` + `:app:testDebugUnitTest`; this adds one task to the same Gradle invocation.

### 6.2 Failure artefact upload

On failure, `actions/upload-artifact@v4` uploads `app/build/outputs/roborazzi/**` (Roborazzi's default output) so the diff PNG is available without needing the reviewer to run the suite locally. One YAML step.

### 6.3 Recapture path

`./gradlew recordRoborazziDebug` regenerates baselines locally. Per CLAUDE.md convention added in §7 below: a recapture commit is its own PR titled "screenshot baseline refresh", never bundled with feature changes.

## 7. Documentation updates

| File | Change |
|---|---|
| `CLAUDE.md` | Under "Static analysis gate", add `verifyRoborazziDebug` to the gate list. Add "Screenshot baselines" subsection: recapture command + the "recapture is its own PR" convention. |
| `docs/TEST_PLAN.md` | New "Screenshot baselines" subsection listing the 20 images and what each verifies. |
| `docs/BACKLOG.md` TASK-35 | Append "Done YYYY-MM-DD" outcome block on merge (consistent with TASK-39 / TASK-76 / TASK-77 style). |
| `docs/BACKLOG.md` TASK-30 | Add one paragraph at the top: "Acceptance gate: `:app:verifyRoborazziDebug` must pass after each tab migration; `recordRoborazziDebug` runs in a separate post-merge PR if a deliberate visual change was approved." |

## 8. Acceptance criteria

1. `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug` passes locally on `main`.
2. **20** baseline images are committed under `app/src/test/screenshots/` and visible in the PR diff.
3. A deliberate-regression smoke test (temporarily change `colorOnSurface` in `colors.xml`, re-run gate) **fails** with non-zero diff. Revert before commit.
4. The PR-gate runtime stays under +90 s vs `main` HEAD (Roborazzi + Robolectric init dominates; per-screenshot cost is ~50–150 ms).
5. `CLAUDE.md`, `docs/TEST_PLAN.md`, and the TASK-30 BACKLOG entry are updated per §7.

## 9. Workflow plan

Branch `feat/task35-roborazzi-baselines` off `main`. Three commits for digestible review:

1. **infra** — Gradle plugin + deps + empty `RoborazziSetup.kt` + `.gitattributes` + `.gitignore` updates. Build still passes (no tests yet).
2. **fixtures + Dashboard 6 baselines** — `DashboardFixtures.kt` + `DashboardScreenshotTest.kt` + 6 committed PNGs.
3. **Charts 14 baselines + docs** — `ChartsFixtures.kt` + `ChartsScreenshotTest.kt` + 14 committed PNGs + CLAUDE.md / TEST_PLAN.md / BACKLOG.md edits.

Merge to `main` via `--no-ff`, separate push, branch delete. No compound git commands per the global rule.

## 10. Version bump

Test infrastructure change with no shipped code or strings, but it ships verification gates that affect the build. Per the version-bump memory rule, the docs-only exemption does **not** apply (this touches `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.github/workflows/ci.yml`, and `app/src/test/`). The semver call is **patch** (z): no user-visible feature, no behaviour change, no breaking change. Commit + `vX.Y.Z` tag pushed separately.

## 11. Out of scope

- Screenshot tests for ChargeEdit / Cars / History / Settings / ManageLocations / Wizard (forward-work; trivially additive to this infrastructure once it lands).
- Pixel-perfect device-matrix coverage. Single Robolectric device config (Pixel 4a equivalent) is enough for theming + palette regressions; full device-matrix screenshots are TASK-34 nightly territory.
- Material You / dynamic-color, RTL.
- Migrating the existing MPAndroidChart code (that's TASK-30, blocked on this).

## 12. Risk register

| Risk | Mitigation |
|---|---|
| Roborazzi 1.46.0 changes API between spec date and impl date | Pin during impl, run the verify smoke as part of acceptance step 3 before considering done. |
| Robolectric font rendering differs across machines (CI vs developer macOS vs developer Linux) | `changeThreshold = 0.01` default tolerates sub-pixel font hinting differences; if flakes appear, raise to 0.02 documented in `CLAUDE.md`. |
| `+90 s` PR-gate budget exceeded | If exceeded, split off Charts baselines to nightly (`:app:verifyRoborazziDebug` keyed off `-PnightlyOnly`); Dashboard stays on PR gate. Decision made post-impl based on measured numbers. |
| Stubbed-VM pattern requires invasive changes to Fragment to override `viewModels()` | Use the existing `viewModels({ requireParentFragment() })` indirection (already in `ChartsTabFragment`) — pass a parent that supplies the fake. No production-code changes. |
