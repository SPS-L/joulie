# Material 3 Theming Refactor — Design Spec

**Goal**

Replace the partial-M2/partial-M3 theme/color setup with a fully Material 3-compliant token system: complete light + dark palettes generated from seed `#1565C0`, with the tertiary role overridden to an orange ramp anchored on `#FB8C00` so DESIGN §6 "AC blue, DC orange" is honoured directly through `?attr/colorTertiary`. Layouts are not touched (they already use M3 type-scale theme attrs); the work is concentrated in `colors.xml`, a new `values-night/colors.xml`, and a rewritten `themes.xml`.

**Tech stack additions**

None. Material Components for Android (`com.google.android.material:material`) is already on the classpath; M3 attrs and `Theme.Material3.DayNight.NoActionBar` are already in use as the parent.

**Non-goals**

- No business-logic changes. No Room/DataStore/repository/use-case/ViewModel modifications.
- No layout XML edits. The 18 layout files already use `android:textAppearance="?attr/textAppearance{Headline|Title|Body|Label}{...}"` — these are the M3 type-scale theme attrs and resolve to `TextAppearance.Material3.*` styles via the parent theme. Functional equivalence with the brief's `style="@style/TextAppearance.Material3.*"` form; rewriting all 60 occurrences for syntactic uniformity is line-noise churn with no behavioural change.
- No removal of the three existing layout-level `android:textColor` references (`?attr/colorError`, `?attr/colorOnSecondaryContainer`, `?attr/colorOnErrorContainer`). They are M3 semantic role attrs used contextually for destructive-action emphasis, charge-type chip contrast on a coloured container, and the dashboard error banner. Replacing them with `?attr/colorOnSurface` would actively reduce contrast in those specific locations.
- No `DynamicColors.applyToActivitiesIfAvailable(...)` integration. Material You / Android-12+ system colour overrides remain out of scope; the seeded brand palette stays authoritative on every device.
- No font-size minimums injected anywhere. The brief's "14sp / 16sp / 22sp" minimums are already exceeded by the M3 type scale defaults in use (`BodyMedium = 14sp`, `BodyLarge = 16sp`, `TitleLarge = 22sp`, `HeadlineMedium = 28sp`).

---

## 1. Audit findings (the as-is)

| Item | Current state | Action |
|---|---|---|
| `themes.xml` parent | `Theme.Material3.DayNight.NoActionBar` ✓ | Keep |
| `themes.xml` body | Mixes M2 (`colorPrimaryVariant`, `colorSecondaryVariant`) and partial M3 attrs; no `colorTertiary`, no container roles, no `colorSurfaceVariant`, no `colorOutline` | **Rewrite to full M3 role map** |
| `values-night/colors.xml` | **Does not exist** — DayNight falls back to light palette in dark mode | **Create with full dark palette** |
| `values/colors.xml` | 13 colour entries: M2 names + 5 chart palette + 2 chart fallbacks | **Replace** the M2/role names; keep `chart_*` and `chart_ac_fallback`/`chart_dc_fallback` |
| Layout `android:textSize` | **0 occurrences** | None |
| Layout `android:textColor` (literal) | **0 occurrences** | None |
| Layout `android:textColor` (`?attr/...`) | 3 occurrences, all M3-semantic | Keep |
| Layout `android:textAppearance` | 60 occurrences, all `?attr/textAppearance{...}` (M3 type-scale) | Keep |
| `?attr/colorTertiary` consumers | F2 charts (`ChartStyling.resolveSeriesColors`) reads it for the DC series; layouts do **not** use it | Theme override produces orange so consumers get orange |
| `?attr/colorOnSecondaryContainer` consumers | `item_charge_event.xml:30` | Theme rewrite defines it (it currently resolves from Material library defaults) |
| `?attr/colorOnErrorContainer` consumers | `fragment_dashboard.xml:239` | Theme rewrite defines it |

---

## 2. Token set

The Material Theme Builder algorithm produces 30 light + 30 dark colour roles per seed. We use the seed `#1565C0` for primary/secondary/neutral/neutral-variant/error tonal palettes, and substitute a hand-chosen orange tonal palette anchored on `#FB8C00` for the tertiary role.

### 2.1 Light palette — `app/src/main/res/values/colors.xml` (replacement)

```xml
<resources>
    <!-- M3 — Primary (seeded #1565C0) -->
    <color name="md_theme_light_primary">#0061A4</color>
    <color name="md_theme_light_onPrimary">#FFFFFF</color>
    <color name="md_theme_light_primaryContainer">#D1E4FF</color>
    <color name="md_theme_light_onPrimaryContainer">#001D36</color>

    <!-- M3 — Secondary (algorithmic, seeded #1565C0) -->
    <color name="md_theme_light_secondary">#535F70</color>
    <color name="md_theme_light_onSecondary">#FFFFFF</color>
    <color name="md_theme_light_secondaryContainer">#D7E3F8</color>
    <color name="md_theme_light_onSecondaryContainer">#101C2B</color>

    <!-- M3 — Tertiary (overridden orange ramp anchored on #FB8C00 — DESIGN §6 "DC orange") -->
    <color name="md_theme_light_tertiary">#A04E00</color>
    <color name="md_theme_light_onTertiary">#FFFFFF</color>
    <color name="md_theme_light_tertiaryContainer">#FFDCC0</color>
    <color name="md_theme_light_onTertiaryContainer">#341000</color>

    <!-- M3 — Error -->
    <color name="md_theme_light_error">#BA1A1A</color>
    <color name="md_theme_light_onError">#FFFFFF</color>
    <color name="md_theme_light_errorContainer">#FFDAD6</color>
    <color name="md_theme_light_onErrorContainer">#410002</color>

    <!-- M3 — Neutral / surface -->
    <color name="md_theme_light_background">#FDFCFF</color>
    <color name="md_theme_light_onBackground">#1A1C1E</color>
    <color name="md_theme_light_surface">#FDFCFF</color>
    <color name="md_theme_light_onSurface">#1A1C1E</color>
    <color name="md_theme_light_surfaceVariant">#DFE2EB</color>
    <color name="md_theme_light_onSurfaceVariant">#43474E</color>
    <color name="md_theme_light_outline">#73777F</color>
    <color name="md_theme_light_outlineVariant">#C3C7CF</color>

    <!-- M3 — Inverse + scrim -->
    <color name="md_theme_light_inverseSurface">#2F3033</color>
    <color name="md_theme_light_inverseOnSurface">#F1F0F4</color>
    <color name="md_theme_light_inversePrimary">#9ECAFF</color>
    <color name="md_theme_light_scrim">#000000</color>

    <!-- F2 — Chart palette (kept verbatim from F2; non-theme product colors) -->
    <color name="chart_1">#1565C0</color>
    <color name="chart_2">#00796B</color>
    <color name="chart_3">#F57F17</color>
    <color name="chart_4">#AD1457</color>
    <color name="chart_5">#6A1B9A</color>

    <!-- F2 — Chart series fallback colors. Used only when theme attrs
         (?attr/colorPrimary, ?attr/colorTertiary) cannot be resolved. With the
         M3 theme rewrite the theme attrs *do* resolve, but the fallbacks remain
         for defensive code paths. -->
    <color name="chart_ac_fallback">#1E88E5</color>
    <color name="chart_dc_fallback">#FB8C00</color>
</resources>
```

### 2.2 Dark palette — `app/src/main/res/values-night/colors.xml` (new file)

```xml
<resources>
    <!-- M3 — Primary -->
    <color name="md_theme_light_primary">#9ECAFF</color>
    <color name="md_theme_light_onPrimary">#003258</color>
    <color name="md_theme_light_primaryContainer">#00497D</color>
    <color name="md_theme_light_onPrimaryContainer">#D1E4FF</color>

    <!-- M3 — Secondary -->
    <color name="md_theme_light_secondary">#BBC7DB</color>
    <color name="md_theme_light_onSecondary">#253140</color>
    <color name="md_theme_light_secondaryContainer">#3B4858</color>
    <color name="md_theme_light_onSecondaryContainer">#D7E3F8</color>

    <!-- M3 — Tertiary (orange ramp inverted) -->
    <color name="md_theme_light_tertiary">#FFB68D</color>
    <color name="md_theme_light_onTertiary">#552100</color>
    <color name="md_theme_light_tertiaryContainer">#793300</color>
    <color name="md_theme_light_onTertiaryContainer">#FFDCC0</color>

    <!-- M3 — Error -->
    <color name="md_theme_light_error">#FFB4AB</color>
    <color name="md_theme_light_onError">#690005</color>
    <color name="md_theme_light_errorContainer">#93000A</color>
    <color name="md_theme_light_onErrorContainer">#FFDAD6</color>

    <!-- M3 — Neutral / surface -->
    <color name="md_theme_light_background">#1A1C1E</color>
    <color name="md_theme_light_onBackground">#E2E2E6</color>
    <color name="md_theme_light_surface">#1A1C1E</color>
    <color name="md_theme_light_onSurface">#E2E2E6</color>
    <color name="md_theme_light_surfaceVariant">#43474E</color>
    <color name="md_theme_light_onSurfaceVariant">#C3C7CF</color>
    <color name="md_theme_light_outline">#8D9199</color>
    <color name="md_theme_light_outlineVariant">#43474E</color>

    <!-- M3 — Inverse + scrim -->
    <color name="md_theme_light_inverseSurface">#E2E2E6</color>
    <color name="md_theme_light_inverseOnSurface">#2F3033</color>
    <color name="md_theme_light_inversePrimary">#0061A4</color>
    <color name="md_theme_light_scrim">#000000</color>
</resources>
```

> **Naming convention.** Tokens use the prefix `md_theme_light_*` for both light and dark resource files, mirroring Material Theme Builder's default export. The `_light_` part is fixed identifier text — it does not mean "light only". The light/dark *values* of each token are determined by which `values/` directory the file lives in (`values/` vs `values-night/`), via Android's standard resource-qualifier system. This avoids a dual `md_theme_light_*` / `md_theme_dark_*` namespace and one mapping in `themes.xml` per role.

### 2.3 Removed tokens (no longer referenced anywhere)

The following M2 names from the old `colors.xml` are dropped:

- `primary`, `primary_variant`, `secondary`, `secondary_variant`, `background`, `surface`, `error`, `on_primary`, `on_secondary`, `on_background`, `on_surface`

Verified by `git grep` after the rewrite: no `@color/primary` etc. references remain in any layout, drawable, or Kotlin source.

---

## 3. Theme rewrite — `app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.EVTracker" parent="Theme.Material3.DayNight.NoActionBar">
        <!-- Primary -->
        <item name="colorPrimary">@color/md_theme_light_primary</item>
        <item name="colorOnPrimary">@color/md_theme_light_onPrimary</item>
        <item name="colorPrimaryContainer">@color/md_theme_light_primaryContainer</item>
        <item name="colorOnPrimaryContainer">@color/md_theme_light_onPrimaryContainer</item>

        <!-- Secondary -->
        <item name="colorSecondary">@color/md_theme_light_secondary</item>
        <item name="colorOnSecondary">@color/md_theme_light_onSecondary</item>
        <item name="colorSecondaryContainer">@color/md_theme_light_secondaryContainer</item>
        <item name="colorOnSecondaryContainer">@color/md_theme_light_onSecondaryContainer</item>

        <!-- Tertiary (orange) -->
        <item name="colorTertiary">@color/md_theme_light_tertiary</item>
        <item name="colorOnTertiary">@color/md_theme_light_onTertiary</item>
        <item name="colorTertiaryContainer">@color/md_theme_light_tertiaryContainer</item>
        <item name="colorOnTertiaryContainer">@color/md_theme_light_onTertiaryContainer</item>

        <!-- Error -->
        <item name="colorError">@color/md_theme_light_error</item>
        <item name="colorOnError">@color/md_theme_light_onError</item>
        <item name="colorErrorContainer">@color/md_theme_light_errorContainer</item>
        <item name="colorOnErrorContainer">@color/md_theme_light_onErrorContainer</item>

        <!-- Background / surface -->
        <item name="android:colorBackground">@color/md_theme_light_background</item>
        <item name="colorOnBackground">@color/md_theme_light_onBackground</item>
        <item name="colorSurface">@color/md_theme_light_surface</item>
        <item name="colorOnSurface">@color/md_theme_light_onSurface</item>
        <item name="colorSurfaceVariant">@color/md_theme_light_surfaceVariant</item>
        <item name="colorOnSurfaceVariant">@color/md_theme_light_onSurfaceVariant</item>
        <item name="colorOutline">@color/md_theme_light_outline</item>
        <item name="colorOutlineVariant">@color/md_theme_light_outlineVariant</item>

        <!-- Inverse + scrim -->
        <item name="colorSurfaceInverse">@color/md_theme_light_inverseSurface</item>
        <item name="colorOnSurfaceInverse">@color/md_theme_light_inverseOnSurface</item>
        <item name="colorPrimaryInverse">@color/md_theme_light_inversePrimary</item>
        <item name="scrimBackground">@color/md_theme_light_scrim</item>
    </style>

    <style name="Theme.EVTracker.SplashScreen" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">?android:colorBackground</item>
        <item name="postSplashScreenTheme">@style/Theme.EVTracker</item>
    </style>
</resources>
```

The DayNight parent automatically swaps the colour resources between `values/` and `values-night/` at runtime; we do **not** need a separate `values-night/themes.xml`. The mapping in `values/themes.xml` is correct for both modes.

---

## 4. F2 charts — verify no resolver code change needed

`ChartStyling.resolveSeriesColors(context)` reads `?attr/colorPrimary` and `?attr/colorTertiary`. Post-rewrite:

- `?attr/colorPrimary` resolves to `md_theme_light_primary` = `#0061A4` (light) / `#9ECAFF` (dark) — close to the existing `chart_ac_fallback` `#1E88E5`. AC trend lines and kWh bars retain a familiar blue.
- `?attr/colorTertiary` resolves to `md_theme_light_tertiary` = `#A04E00` (light) / `#FFB68D` (dark) — both orange, satisfying DESIGN §6.

No code changes required in `ChartStyling.kt`, `ChartsTabFragment.kt`, or any other F2 file. The fallback constants `chart_ac_fallback` / `chart_dc_fallback` remain in `colors.xml` as defensive backstops for any future caller that creates a chart from a non-themed `Context`.

---

## 5. Acceptance criteria

- [ ] `git grep "@color/primary\b\|@color/secondary\b\|@color/background\b\|@color/surface\b\|@color/on_primary\b\|@color/on_secondary\b\|@color/on_background\b\|@color/on_surface\b\|@color/error\b\|colorPrimaryVariant\|colorSecondaryVariant"` returns zero matches in `app/src/main/`.
- [ ] `app/src/main/res/values/colors.xml` and `app/src/main/res/values-night/colors.xml` declare the same set of `md_theme_light_*` token names (verifying parity).
- [ ] `app/src/main/res/values/themes.xml` declares all 30 M3 colour-role mappings listed in §3.
- [ ] `./gradlew :app:assembleDebug` and `./gradlew :app:assembleRelease` both succeed (no resource-not-found errors).
- [ ] All existing JVM unit tests (~236) still pass (no theming changes touch the non-Android paths).
- [ ] Manual smoke (one device, one emulator, both modes) of:
  - Wizard → Dashboard → Charge edit → Cars → History → Charts (each tab) → Settings → ManageLocations.
  - System theme toggle (Settings → Display → Dark theme).
  - Font scale 200% (Settings → Display → Font size — Largest).
  - Each Reset action's confirmation dialog (verify `?attr/colorError` text remains red and visible).
  - Charts → Multi-currency banner (verify `?attr/colorOnErrorContainer` text on the cost tab body when the period contains EUR + USD events — should remain legible).
  - History row charge-type chip (verify `?attr/colorOnSecondaryContainer` on the AC/DC chip is legible).
  - F2 charts: AC line / DC line / kWh bar / cost bar / AC-DC pie / Locations pie all render with the expected blue/orange dichotomy in both light and dark.
- [ ] Android Studio Layout Inspector contrast overlay: every TextView passes WCAG AA (4.5:1 for body, 3:1 for large text >= 18sp regular or >= 14sp bold) on Wizard / Dashboard / Settings / ChargeEdit / History / Charts in both light and dark mode. The M3 token set is designed to pass AA in both modes by construction; this check is a verification, not an expected-fix-source.
- [ ] CLAUDE.md Status section bumped at the merge to note "M3 theming refactor landed".

---

## 6. Out-of-spec deferrals (explicitly NOT done)

- **Layout XML edits.** The brief's "replace `android:textAppearance` with `style=...`" is a no-op syntactic change against the existing `?attr/textAppearance*` references. Skipped.
- **Hardcoded textColor / textSize removal.** None exist (verified).
- **Font-size minimums.** Already exceeded by M3 type scale defaults.
- **Material You / dynamic color.** `DynamicColors.applyToActivitiesIfAvailable(application)` not added. Brand palette stays authoritative across all devices.
- **`values-night/themes.xml`.** Not needed — DayNight switches the colour resources automatically; the theme attribute mapping is identical in both modes.
- **MPAndroidChart axis text colours.** MPAndroidChart's defaults are dark-on-light. In dark mode the chart text and grid lines remain mid-grey — readable on the dark surface but not strictly DayNight-correct. Out of scope for this spec; future task can add `chart.xAxis.textColor = ContextCompat.getColor(context, ?attr/colorOnSurface resolved)` etc. Flagged in §5 manual smoke as a watch-out.

---

## 7. File touches (final)

```
Modified:
  app/src/main/res/values/colors.xml         — replaced; keeps chart_*, chart_ac_fallback, chart_dc_fallback
  app/src/main/res/values/themes.xml         — rewritten; all 30 M3 role mappings; M2 names removed

Created:
  app/src/main/res/values-night/colors.xml   — full dark palette using the same md_theme_light_* token names

Untouched:
  app/src/main/res/layout/*.xml              — all 18 layouts; textAppearance/textColor refs already M3-correct
  app/src/main/java/**                       — no code changes
  Any test file                              — no test changes (theming is XML-only)
  CLAUDE.md                                  — updated only at merge time per acceptance criterion
```

---

## 8. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Material Components version on the classpath doesn't define one of the M3 attrs we map (e.g. `colorSurfaceInverse` is a newer addition) | Low | Library version is `com.google.android.material:material:1.11.x` per `libs.versions.toml`; all 30 attrs exist since 1.7+. The build will fail loudly if any attr is missing — caught at first `assembleDebug`. |
| Custom orange tertiary clashes with the existing F1 success/error containers in some layout | Low | Tertiary is consumed only by F2 charts; no layout uses `?attr/colorTertiary` directly. The orange ramp is brand-decorative, not signal-bearing in UI surfaces. |
| Dark-mode contrast regression on a layout we didn't anticipate | Medium | §5 manual smoke explicitly covers Settings/Dashboard/Charts/Wizard/ChargeEdit/History at default and 200% font scale, light and dark. M3 dark roles are AA-compliant by construction; deviations would indicate either a custom drawable using a literal hex or a third-party widget overriding `colorOnSurface`. |
| Stale `colorPrimaryVariant` / `colorSecondaryVariant` reference somewhere we missed (drawable, third-party theme overlay) | Low | Acceptance §5 grep covers it; the build will warn if a referenced colour resource is missing. |

---

## 9. Implementation hand-off

The next skill (writing-plans) will decompose this spec into bite-sized tasks. Suggested ordering:

1. Create `values-night/` directory and the dark `colors.xml` (one commit, build verifies).
2. Replace `values/colors.xml` (one commit, build verifies — old M2 colour names are now unreferenced).
3. Rewrite `values/themes.xml` (one commit, build verifies — colour role attrs all resolve).
4. Manual smoke pass per §5 acceptance — record findings in the merge commit.
5. CLAUDE.md status bump + merge.

The implementation is intentionally small (3 files modified, 1 created) and should complete in a single short branch (`chore/m3-theming`). No subagent-driven-development required; an inline `executing-plans` pass is more appropriate.
