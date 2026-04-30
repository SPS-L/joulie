# Default `primaryMetric` → `kwh_per_100km` (with Trend chart following)

**Date:** 2026-04-30
**Backlog item:** none — direct user request, sized for a single PR.
**Type:** Default flip + small Charts UI extension
**Risk:** Low. No migration, no Room schema change, no formula change. The only computed transform is `100.0 / kmPerKwh` for the Trend chart's Y series, applied only when `primaryMetric == "kwh_per_100km"`.

---

## 1. Context

`primaryMetric` is a DataStore-backed user preference with three legal values:

| token            | meaning                | distanceUnit coupling |
|------------------|------------------------|------------------------|
| `km_per_kwh`     | kilometres per kWh     | `km`                   |
| `kwh_per_100km`  | kilowatt-hours per 100 km | `km`               |
| `mi_per_kwh`     | miles per kWh          | `miles`                |

Today the **fresh-install fallback** is `km_per_kwh` (set in
`data/repository/SettingsRepository.kt:22`). It also seeds
`WizardViewModel.UiState.metric`, so the wizard's metric radio group opens
with km/kWh pre-selected. Three other state classes default to the same
string for VM-construction safety.

The user-visible defects:

1. **Cyprus context.** SPS-Lab is a Cyprus university lab and the entire
   target user pool is metric-system-native. The European convention for
   EV efficiency is **kWh / 100 km** (and L/100 km for ICE), not km/kWh.
   The current default optimises for an audience the app does not have.
2. **Charts inconsistency.** `ui/charts/` has zero references to
   `primaryMetric`. The Trend chart's Y axis switches only on
   `distanceUnit` ("km" ↔ "miles"). For users on `kwh_per_100km`, the
   dashboard cards show kWh/100 km but the Trend chart still plots
   km/kWh. This is a pre-existing inconsistency that flipping the default
   makes the **default** UX rather than an opt-in edge case, so we fix it
   in the same change.

## 2. Decision

Change the fresh-install default to `kwh_per_100km`, and extend the Trend
chart to honour `primaryMetric` so the default UX is internally
consistent.

### 2.1 Default flip (4 production sites + state-class defaults)

| File                                                            | Change                                                  |
|-----------------------------------------------------------------|---------------------------------------------------------|
| `data/repository/SettingsRepository.kt:22`                      | DataStore fallback `"km_per_kwh"` → `"kwh_per_100km"`   |
| `ui/wizard/WizardViewModel.kt:19` (`UiState.metric`)            | default `"km_per_kwh"` → `"kwh_per_100km"`              |
| `core/model/SettingsUiState.kt:15`                              | default `"km_per_kwh"` → `"kwh_per_100km"`              |
| `core/model/DashboardScreenState.kt:10`                         | default `"km_per_kwh"` → `"kwh_per_100km"`              |
| `core/model/ChartsScreenState.kt`                               | **new field** `primaryMetric: String = "kwh_per_100km"` |

The wizard's `selectMetric()` already enforces the metric ↔ unit pairing
(`mi_per_kwh ⇒ miles`; `km_per_kwh` and `kwh_per_100km ⇒ km`), so the
default pair `(kwh_per_100km, km)` is already legal — no coupling-rule
change needed.

### 2.2 No migration on existing installs

Users who finished the wizard already have an explicit `primaryMetric`
written to DataStore by `SettingsRepository.completeSetup`. The
`?: "kwh_per_100km"` fallback never triggers for them; their stored
choice is preserved. Settings → Reset preferences wipes
`setupComplete=false` and re-runs the wizard, at which point the new
default takes effect on the wizard's first paint — same as a fresh
install. No migration code, no banner, no opt-in dialog.

### 2.3 Trend chart respects `primaryMetric`

`ChartsScreenState` gains `primaryMetric: String`. `ChartsViewModel`
combines `settingsReader.primaryMetric` into the existing 3-flow combine
(making it a 4-flow combine).

In `ChartsTabFragment.renderTrend`, the Y-series transform and the
marker's unit suffix branch on `primaryMetric` first, then `distanceUnit`:

```kotlin
val (yTransform, unitSuffix) = when (state.primaryMetric) {
    "kwh_per_100km" -> Pair<(Double) -> Double?, String>(
        { kmPerKwh -> if (kmPerKwh > 0.0) 100.0 / kmPerKwh else null },
        getString(R.string.charts_trend_y_kwh100),
    )
    "mi_per_kwh" -> Pair(
        { kmPerKwh -> UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh) },
        getString(R.string.charts_trend_y_mi),
    )
    else -> Pair(
        { kmPerKwh -> kmPerKwh },
        getString(R.string.charts_trend_y_kmh),
    )
}
fun toEntries(points: List<EfficiencyPoint>): List<Entry> =
    points.mapNotNull {
        val y = yTransform(it.kmPerKwh) ?: return@mapNotNull null
        val xDays = ((it.eventTimeMillis - windowStart).toDouble() / ChartStyling.MILLIS_PER_DAY).toFloat()
        Entry(xDays, y.toFloat(), it.eventTimeMillis as Any)
    }
```

Notes:

- The `(kmPerKwh) -> Double?` lambda lets the `kwh_per_100km` branch drop
  zero/negative `kmPerKwh` rows that would otherwise produce ∞ or
  negative kWh/100 km. The other two branches never return null. This
  preserves the existing skip-invalid behaviour at the producer level
  (`StatsCalculator` already skips `dist <= 0`), so in practice the new
  filter only fires on edge data the producer accepts but division
  cannot.
- `unitToMi` is removed; its only effect (Y transform + suffix) is
  subsumed by the branch above.
- The miles branch is now reached only when `primaryMetric ==
  "mi_per_kwh"` rather than when `distanceUnit == "miles"`. These
  conditions are equivalent in production because of the wizard's
  coupling rule; the new code matches the metric directly so the Trend
  chart is driven by the user's *primary metric* signal, not the unit it
  derives from.

### 2.4 New string resource

`app/src/main/res/values/strings.xml`:

```xml
<string name="charts_trend_y_kwh100">kWh/100km</string>
```

(Shorthand matching the existing `metric_kwh_per_100km` short label.)

### 2.5 Doc updates

- `docs/DESIGN.md:139` — update the defaults table cell from
  `"km_per_kwh"` to `"kwh_per_100km"`.
- `docs/DESIGN.md` Charts section — note that the Trend Y-axis follows
  `primaryMetric` and lists the three rendering modes.

`CLAUDE.md` already lists the three legal tokens neutrally without
declaring a default, so no edit needed there.

### 2.6 Test updates

| File                                                       | Change                                                                       |
|------------------------------------------------------------|------------------------------------------------------------------------------|
| `test/.../testing/Fakes.kt:114`                            | `FakeSettingsReader.primaryMetricInit` default → `"kwh_per_100km"`           |
| `test/.../testing/Fakes.kt:181`                            | `FakeSettingsWriter.primaryMetric` initial value → `"kwh_per_100km"`         |
| `test/.../data/repository/SettingsRepositoryTest.kt:38`    | assertion → `"kwh_per_100km"`                                                |
| `test/.../ui/wizard/WizardViewModelTest.kt:71,76`          | comment + assertion → `"kwh_per_100km"`                                      |
| `test/.../ui/dashboard/DashboardViewModelTest.kt:49`       | parameter default → `"kwh_per_100km"`                                        |
| `test/.../ui/charts/ChartsViewModelTest.kt`                | **new test:** `primaryMetric` flows from `SettingsReader` into screen state. |

Existing test sites that pass `"km_per_kwh"` *explicitly* (e.g.
`WizardViewModelTest:57` `selectMetric("km_per_kwh")`,
`SettingsViewModelTest:298` `onPrimaryMetricSelected("km_per_kwh")`,
`SettingsRepositoryAtomicWritesTest:42` `completeSetup("km_per_kwh", …)`,
`ChargeEditViewModelTest:52` `primaryMetricInit = "km_per_kwh"`) keep
their explicit values — they assert behaviour for the *old* token, which
is still legal. The default-flip changes only the value the system
chooses when nothing is specified.

## 3. Out of scope

- Removing the `km_per_kwh` option from the wizard / Settings dialog.
  It remains a legal user choice.
- Locale-driven defaults (e.g., infer from `Locale.US` to keep
  `mi_per_kwh` for US devices). Could be a follow-up; not needed for
  Cyprus context, which is the explicit driver.
- A "metric default changed" banner for upgrading users. Existing
  installs are not affected, so there is no upgrading-user UX problem.
- Changing the *Monthly kWh*, *Monthly cost*, *AC vs DC*, or
  *Locations* tabs. Those plot kWh totals or costs, not efficiency, and
  are independent of `primaryMetric`.
- A toggle on the Trend chart to override `primaryMetric` per session.
  Out of scope; the user's preference *is* the override.
- Moving to a Kotlin enum for `primaryMetric` (string tokens stay).
  Could be a future task; orthogonal to this change.

## 4. Acceptance Criteria

The change is complete when **all** of the following hold:

1. `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`
   succeeds.
2. JVM test count goes up by exactly 1 (the new `ChartsViewModelTest`
   case asserting `primaryMetric` flows into `ChartsScreenState`). No
   existing test regresses.
3. `:app:assembleDebugAndroidTest` compiles.
4. `git grep "= \"km_per_kwh\"" app/src/main/java` returns **zero** matches.
5. `git grep "kwh_per_100km" app/src/main/java/org/spsl/evtracker/ui/charts`
   returns at least one match (proving the chart now references the
   token).
6. Manual smoke (one debug install, fresh `clearAppData`):
   a. Wizard opens with **kWh / 100 km** pre-selected on Page 2.
   b. Finishing the wizard with the default selection lands on a
      Dashboard whose primary metric card shows kWh/100 km.
   c. Charts → Trend tab Y-axis labels show `kWh/100km` and the line
      values are within 0.1 of `100 / km_per_kwh` for a couple of
      hand-checked events.
   d. Settings → Primary metric → switch to "km / kWh", return to
      Charts → Trend: axis labels switch to `km/kWh`, values switch to
      raw `kmPerKwh`.

## 5. Implementation Sequence (preview for the plan)

1. Branch `feat/default-metric-kwh-per-100km` off `main`.
2. Flip the four production defaults + add `primaryMetric` to
   `ChartsScreenState`. Run `:app:assembleDebug` and full JVM tests;
   expect ~7 failing tests caused by the flipped token. Update Fakes
   and assertions per §2.6 until green.
3. Wire `primaryMetric` through `ChartsViewModel.combine`. Verify with
   the new `ChartsViewModelTest` case.
4. Update `ChartsTabFragment.renderTrend` to branch on `primaryMetric`,
   add the `charts_trend_y_kwh100` string. Verify on debug install
   (manual smoke per §4.6).
5. Update `docs/DESIGN.md`. Run full CI gate
   (`ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`).
6. Commit (one commit per logical step), merge `--no-ff`, push, delete
   branch.

## 6. Open Questions

None. The user explicitly opted into bundling the Trend chart fix
("Option B") so the default flip ships with consistent UX.
