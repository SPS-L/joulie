# TASK-79 — ChartsTab screenshot baselines (design)

**Date:** 2026-05-10
**Backlog entry:** `docs/BACKLOG.md` TASK-79 (🟢)
**Builds on:** TASK-35 Phase 1 (build wiring, merged in v1.9.34)
**Acceptance gate for:** TASK-30 (MPAndroidChart → Vico migration)

## 1. Goal

Lock the visual rendering of the seven `ChartsTabFragment` tabs (Trend, Monthly kWh, Monthly cost, AC vs DC, Locations, Degradation, CO₂) into 14 versioned PNGs (× light + dark) that CI verifies on every push. Once green, TASK-30's "looks identical after Vico swap" criterion is `./gradlew :app:verifyRoborazziDebug` pass/fail, not an eyeball walkthrough.

## 2. Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Tabs covered | All 7 (`TREND`, `MONTHLY_KWH`, `MONTHLY_COST`, `AC_DC`, `LOCATIONS`, `DEGRADATION`, `CO2`) | Matches the production `TabKind` enum; TASK-30 swaps every chart so partial coverage = partial verification. |
| Theme matrix | Light + dark only (× 7 = 14 PNGs) | Catches the regression class that motivated this task (2026-05-01 dark-mode bug `c677a2b`). Material You / RTL is forward-work. |
| VM strategy | Mocked `ChartsViewModel` via fake parent fragment | Lower-fidelity than real-VM-with-`@BindValue`-fakes, but render path is exactly what TASK-30 changes. ~70 LOC vs. ~250 LOC for the real-VM path. Avoids the 8-collaborator binding surface and keeps the test focused on the render. |
| Host activity | `HiltTestActivity` (existing, debug sourceset) | Already `@AndroidEntryPoint` and registered in `app/src/debug/AndroidManifest.xml`. JVM unit tests for the debug variant include debug sources on classpath. |
| CI gate | Same PR (`continue-on-error: false`) | Per TASK-79 brief: gate must be live for it to serve TASK-30. `changeThreshold = 0.01` default tolerates sub-pixel font hinting between dev and CI machines. |
| Animation handling | `ShadowLooper.idleFor(800ms)` after fragment commit | Covers MPAndroidChart's `animateY(400)` + safety margin. Bumpable to 1500 ms in a follow-up if CI flakes. |

## 3. File layout

```
app/src/test/java/org/spsl/evtracker/screenshots/
  RoborazziSetup.kt              — common @Rule, theme switcher, host helper, looper-idle helper
  ChartsTabScreenshotTest.kt     — 14 @Test methods (one per tab × theme)
  fixtures/
    ChartsFixtures.kt            — single canonical ChartsScreenState.Loaded(...) builder
  host/
    FakeChartsParentFragment.kt  — non-Hilt parent, overrides defaultViewModelProviderFactory

app/src/test/screenshots/        — 14 committed baseline PNGs
```

## 4. VM wiring

`ChartsTabFragment` uses `viewModels({ requireParentFragment() })`. The fake parent fragment exposes a Mockito-mocked `ChartsViewModel` via `getDefaultViewModelProviderFactory()`:

```kotlin
class FakeChartsParentFragment : Fragment() {
    override fun onCreateView(...) = FrameLayout(requireContext()).apply { id = CHILD_CONTAINER_ID }

    override fun getDefaultViewModelProviderFactory() =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = nextMockedVm as T
        }

    companion object {
        val CHILD_CONTAINER_ID = View.generateViewId()
        var nextMockedVm: ChartsViewModel? = null
    }
}
```

The mock:
```kotlin
fun mockChartsViewModel(state: ChartsScreenState): ChartsViewModel = mock {
    on { uiState } doReturn MutableStateFlow(state)
    on { events } doReturn MutableSharedFlow<ChartsEvent>()
}
```

The fake parent is NOT `@AndroidEntryPoint`, so Hilt's `FragmentComponentManager.findActivity()` walks up to `HiltTestActivity` (which IS `@AndroidEntryPoint`) for the child tab fragment's component lookup. This is the documented Hilt fallback behavior.

## 5. Fixture (canonical state, shared across all 7 tab tests)

One `ChartsScreenState.Loaded(...)` populated for all six metric fields:

- **Car**: Tesla Model 3, `batteryKwh = 60.0`
- **Period**: `Last12Months`, `periodStartMillis` anchored to `2026-01-01T00:00Z`
- **Events**: 24 spread across 12 months (2 per month), AC/DC ratio 18:6
- **Costs**: all EUR, `mixedCurrency = false`, costs in €8–€18 range
- **Trend**: `EfficiencySeries(acPoints = 18 entries, dcPoints = 6 entries)`, kmPerKwh distributed 5.5–7.0
- **Monthly kWh**: 12 buckets, totalKwh 60–90 each
- **Monthly cost**: 12 buckets, totalCost 12–35 each
- **AC vs DC**: `AcDcSplit(acCount = 18, dcCount = 6, acKwh = 360.0, dcKwh = 180.0)`
- **Locations**: `[Home (12), Work (6), Public (4), Office (2)]` via `LocationSlice`
- **Capacity**: 5 `CapacityPoint`s degrading 60.0 → 57.5 over the period, `isExact = true` for 3, false for 2
- **`nominalBatteryKwh`**: 60.0
- **`derivedExcludedCount`**: 1 (drives the degradation banner)
- **CO₂ cumulative**: 12 `CumulativePoint`s with EV growing 0 → 100 kg, ICE growing 0 → 280 kg

All timestamps derived deterministically from a frozen calendar at `2026-01-01T12:00Z`. Locale is `en-US` (project default for unit tests).

## 6. Animation determinism

MPAndroidChart's `animateY(400)` runs against `ValueAnimator` → `Choreographer`. Robolectric's PAUSED looper mode does not auto-advance frames. After fragment commit:

```kotlin
shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
```

`idleFor` advances Robolectric's clock by 800 ms, which fires all pending Choreographer callbacks and completes the chart's y-scale animation. 800 ms = 400 ms animation + 400 ms slack for any post-animation invalidation passes. If CI runs flake on this, bump to 1500 ms.

The `firstRenderConsumed` flag inside `ChartsTabFragment` prevents re-animation on subsequent renders within a session — irrelevant here since each test creates a fresh fragment.

## 7. CI integration

**`.github/workflows/ci.yml`** — append `:app:verifyRoborazziDebug` to the existing test job:

```yaml
- name: Run JVM tests + screenshot verify
  run: ./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:verifyRoborazziDebug

- name: Upload Roborazzi diff artefacts on failure
  if: failure()
  uses: actions/upload-artifact@v4
  with:
    name: roborazzi-diffs
    path: app/build/outputs/roborazzi/**
    retention-days: 7
```

The verify task is a no-op when there's no diff and a hard fail when there is.

## 8. Documentation updates

| File | Change |
|---|---|
| `CLAUDE.md` "Static analysis gate" | Add `verifyRoborazziDebug` to the local-run command list. Add "Screenshot baselines" subsection: 14 PNGs, recapture via `./gradlew :app:recordRoborazziDebug`, recapture commits must be their own PR titled "screenshot baseline refresh" (per the existing baseline-PR convention). |
| `docs/TEST_PLAN.md` | New "Screenshot baselines" subsection listing the 14 ChartsTab images and what each verifies. |
| `docs/BACKLOG.md` TASK-35 | Flip ◐ → ☑ in the overview table; the body section's Phase 2/3 deferred note gets a "Resolved by TASK-79" line. |
| `docs/BACKLOG.md` TASK-79 | Append "Done 2026-05-10" outcome block consistent with TASK-39 / TASK-76 / TASK-77 style. |
| `docs/BACKLOG.md` TASK-30 | Add one paragraph at the top: "Acceptance gate: `./gradlew :app:verifyRoborazziDebug` must pass after each tab migration; `recordRoborazziDebug` runs in a separate post-merge PR if a deliberate visual change was approved." |

## 9. Acceptance criteria

1. `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug` passes locally on `main`.
2. **14** baseline images committed under `app/src/test/screenshots/` and visible in the PR diff. Each PNG visually reviewed before commit.
3. Deliberate-regression smoke test: temporarily flip a chart-related color in `colors.xml`, re-run gate, confirm fail with non-zero diff. Revert before commit.
4. PR-gate runtime stays under +90 s vs `main` HEAD.
5. CLAUDE.md, `docs/TEST_PLAN.md`, and the TASK-30 BACKLOG entry are updated per §8.

## 10. Workflow plan

Branch `feat/task79-chartstab-baselines` off `main`. Three commits for digestible review:

1. **infra + helpers** — `RoborazziSetup.kt`, `FakeChartsParentFragment.kt`, `ChartsFixtures.kt`. Compile-only check; no PNGs yet.
2. **test class + 14 baselines** — `ChartsTabScreenshotTest.kt` + 14 committed PNGs.
3. **CI gate + docs** — `.github/workflows/ci.yml` step, `CLAUDE.md`, `docs/TEST_PLAN.md`, `docs/BACKLOG.md` outcome blocks.

Merge to `main` via `--no-ff`, separate push, branch delete (per CLAUDE.md no-compound-git rule).

## 11. Version bump

Test infra + CI gate change. Touches `gradle/libs.versions.toml` (already there from Phase 1), `app/build.gradle.kts` (no change expected), `.github/workflows/ci.yml`, and `app/src/test/`. Per the version-bump memory rule, the docs-only exemption does **not** apply (CI YAML and test code aren't docs). Semver call: **patch** (z) — no user-visible feature, no behaviour change, no breaking change. v1.9.34 → v1.9.35. Commit + tag pushed separately.

## 12. Out of scope

- Dashboard + outer ChartsFragment baselines (would need a different VM-injection strategy).
- Material You / dynamic-color, RTL.
- Pixel-perfect device-matrix coverage (single Pixel-4a-equivalent config).
- The MPAndroidChart → Vico migration itself (that's TASK-30, blocked on this).

## 13. Risk register

| Risk | Mitigation |
|---|---|
| Robolectric font rendering differs between dev and CI | `changeThreshold = 0.01` default tolerates sub-pixel hinting. If the first CI run flakes, bump threshold to 0.02 documented in CLAUDE.md. |
| MPAndroidChart animation doesn't fully settle in 800 ms | Bump `idleFor` to 1500 ms. |
| Hilt's `findActivity()` doesn't walk up correctly when the parent fragment isn't `@AndroidEntryPoint` | Documented Hilt behaviour — falls back to host activity. If breaks, make `FakeChartsParentFragment` `@AndroidEntryPoint` (one annotation; harmless). |
| Mockito can't mock final Kotlin classes | `mockito-kotlin` (already a `testImplementation`) handles this via inline-mock-maker. Confirmed working in the existing `Fakes.kt`-driven JVM tests. |
| Locking incorrect baselines | Acceptance step 2 mandates visual review before commit; deliberate-regression smoke (step 3) confirms the gate fires. |
