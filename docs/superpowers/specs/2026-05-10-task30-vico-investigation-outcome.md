# TASK-30 — Vico migration investigation outcome

**Date:** 2026-05-10
**Backlog entry:** `docs/BACKLOG.md` TASK-30 (now ⏸)
**Status:** Investigation done; migration **deferred** behind TASK-33 (Kotlin 2.x upgrade).

## 1. What this document is

The original session goal was a full MPAndroidChart → Vico migration. During the build-wiring step, an empirical blocker surfaced that the brainstormed design did not anticipate. This document captures:

- the blocker and its evidence,
- the dependency it creates between TASK-30 and TASK-33,
- what *did* land in the working session as forward progress (the custom `PieChartView` and its slice-math test),
- the precise remaining work that TASK-30 still needs to ship after the dependency clears.

A future maintainer should treat this as the design doc for TASK-30 itself — the rendering choices and tab-mapping table below are still the plan; only the "when" changed.

## 2. The blocker

### 2.1 Symptom

Adding `com.patrykandpatrick.vico:views:2.0.0` (the modern API the original design targeted) and running `./gradlew :app:assembleDebug` fails:

```
e: …/views-2.0.0-api.jar!/META-INF/views_release.kotlin_module
   Module was compiled with an incompatible version of Kotlin.
   The binary version of its metadata is 2.1.0, expected version is 1.9.0.
e: …/core-2.0.0-api.jar!/META-INF/core_release.kotlin_module
   Module was compiled with an incompatible version of Kotlin.
   The binary version of its metadata is 2.1.0, expected version is 1.9.0.
e: …/kotlin-stdlib-2.1.0.jar!/META-INF/kotlin-stdlib-jdk7.kotlin_module
   Module was compiled with an incompatible version of Kotlin.
   The binary version of its metadata is 2.1.0, expected version is 1.9.0.
…
BUILD FAILED in 41s
```

### 2.2 Root cause

Vico 2.x is published with Kotlin 2.1 metadata and pulls in `kotlin-stdlib:2.1.0` transitively. Our project is pinned at `kotlin = "1.9.21"` in `gradle/libs.versions.toml`. Kotlin's binary compatibility rules forbid a 1.9.x compiler from consuming 2.1.x metadata.

### 2.3 Workaround attempted

Falling back to **Vico 1.16.0** (the last 1.x stable, Kotlin 1.9-compatible) compiled cleanly. However:

- The 1.x view API is significantly older and more verbose than 2.x — single `ChartView` class with imperative configuration via `view.chart = lineChart()` / `columnChart()`, separate `ChartEntryModelProducer`, and a markedly different theming/marker model.
- Adopting 1.x means writing ~600 LOC against an unfamiliar deprecated API, then **rewriting it again** to 2.x once Kotlin 2.x lands — double work for a feature whose only meaningful difference is "rendering library swap."
- Vico 1.x is on a maintenance-only track; new features and bug-fixes ship to 2.x.

The cost-benefit of Vico 1.x is negative.

### 2.4 Conclusion

**TASK-30 is gated on a Kotlin 2.x upgrade**, which is the body of TASK-33 (currently scoped as "audit Kotlin 2.x / K2 + KSP + Hilt compatibility"). TASK-33's outcome should be expanded to either confirm-and-execute the upgrade in the same PR, or split into TASK-33a (audit) + TASK-33b (upgrade) so TASK-30 can land on 2.x cleanly.

## 3. What landed today

### 3.1 `app/src/main/java/org/spsl/evtracker/ui/common/PieChartView.kt`

A standalone custom `View` rendering a donut chart + slice labels + legend + center text + 0°→360° sweep animation. ~210 LOC. No external chart-library dependency; uses only `Canvas`, `Paint`, `ValueAnimator`, and Material's `colorSurface` attr lookup.

Designed for behavioural parity with the existing MPAndroidChart configuration on the AC/DC and Locations tabs (donut style with 0.55× hole, slice labels at centroid, legend below, optional centre text, 400 ms animation cadence). Currently has **no production call sites** — TASK-30 will wire it up to `ChartsTabFragment.renderAcDc` and `renderLocations` when the migration unblocks.

Resolving Vico's pie-chart gap was the migration's biggest unknown; landing the implementation now means a future TASK-30 PR is purely "swap call sites + drop MPAndroidChart" rather than "design a custom view from scratch under deadline."

### 3.2 `app/src/test/java/org/spsl/evtracker/ui/common/PieChartViewSliceMathTest.kt`

Five JVM unit-test cases exercising slice-angle math:

1. Empty slice list → total is zero (caller's empty-state path).
2. Two-slice (AC=18, DC=6) → sweep angles sum to 360°.
3. Four-slice (Locations) → sweep angles sum to 360°.
4. Single non-zero slice → full 360° sweep.
5. All-zero slices → total is zero (degenerate case, parent renders empty state).

Visual rendering of the view itself is covered when the AC/DC and Locations Roborazzi baselines (TASK-79) flip from MPAndroidChart pixels to PieChartView pixels — bundled with the eventual TASK-30 migration PR.

## 4. Plan for when TASK-30 unblocks

The following items remain to be done in the TASK-30 migration PR (which only opens after Kotlin upgrade lands):

1. **Build wiring**: add `com.patrykandpatrick.vico:views:2.x` (latest stable at that time) to `gradle/libs.versions.toml`; replace `implementation(libs.mpandroidchart)` in `app/build.gradle.kts`.
2. **`ChartStyling.kt`**: replace MPAndroidChart configuration helpers with Vico equivalents. Theme-aware colour helpers (`colorOnSurface`, `colorOutlineVariant`, AC/DC seed colours) stay.
3. **`ChartsMarkerView.kt`**: delete; replace with a Vico `CartesianMarker` factory in `ChartStyling.kt`.
4. **`ChartsTabFragment.kt`** — rewrite the 7 render functions:
   - `renderTrend` → `CartesianChartView` + `LineCartesianLayer` (2 series).
   - `renderMonthlyKwh` / `renderMonthlyCost` → `CartesianChartView` + `ColumnCartesianLayer`.
   - `renderDegradation` → `LineCartesianLayer` + horizontal-line decoration for the dashed nominal-`battery_kwh` reference.
   - `renderCo2` → `LineCartesianLayer` (2 series, second dashed via `LineCartesianLayer.LineSpec` style).
   - `renderAcDc` → `PieChartView` (already in tree; just wire slices + centre text).
   - `renderLocations` → `PieChartView` (reuses).
5. **Drop MPAndroidChart**: remove `mpandroidchart` from `libs.versions.toml`, the `implementation(libs.mpandroidchart)` line, the JitPack scoping in `settings.gradle.kts` (no other consumer), and the `-keep class com.github.mikephil.charting.**` block in `app/proguard-rules.pro` (TASK-17 keep rule).
6. **Regenerate 14 baselines + visual review**: `./gradlew :app:recordRoborazziDebug`. Bundle with the migration PR per the pre-approved policy decision (logged in §5 of this doc).
7. **Docs**: `CLAUDE.md` Architecture section (chart library mention), `docs/BACKLOG.md` outcome blocks for TASK-30 (☐/⏸ → ☑), TASK-33 dependency note, this spec linked from the merge commit.

The rendering choices, theme-awareness rules, animation determinism strategy, and tab-mapping table from the original design doc all remain valid and should be inherited by the future PR.

## 5. Pre-approved policy decisions to inherit

When the TASK-30 PR is opened, these decisions stand and don't need re-litigation:

- **Bundle baselines with the migration PR.** Documented exception to the "screenshot baseline refresh is its own PR" convention. Rationale: TASK-30 *is* the feature whose entire purpose is changing rendering; bundling is the opposite of stealth. Approved 2026-05-10.
- **Vico 2.x targeted, not 1.x.** Rationale in §2.3 above.
- **Custom `PieChartView` rather than a second chart library.** Already implemented in this PR.
- **Pinch-zoom out of scope.** MPAndroidChart had it; Vico 2.x doesn't by default; users haven't asked.
- **`PieChartView` is non-interactive** (no rotation / tap-to-explode). Matches MPAndroidChart pie behaviour.

## 6. BACKLOG impact

- TASK-30: **☐ → ⏸** "Under consideration — gated on TASK-33 Kotlin 2.x upgrade." Acceptance gate paragraph (added 2026-05-10) for `:app:verifyRoborazziDebug` stays — TASK-79's 14 baselines are the lock.
- TASK-33: priority bumps; the audit must produce a yes/no on Kotlin 2.x adoption, and if yes, deliver the upgrade itself (or file a TASK-33b) so TASK-30 can land.
- New row not needed; PieChartView lives in `ui/common/` alongside other shared views (`MoneyFormat`, `DateFormat` etc.) and is documented in CLAUDE.md.
