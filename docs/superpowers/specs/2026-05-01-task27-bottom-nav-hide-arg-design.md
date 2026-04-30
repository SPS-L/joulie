# TASK-27 — Decouple bottom-nav visibility from `MainActivity.hideOn` — Design

**Date:** 2026-05-01
**Branch:** `feat/task27-bottom-nav-hide-arg`

## Problem

`MainActivity.kt:68-76` declares an explicit set of fragment IDs to hide
the `BottomNavigationView` over:

```kotlin
val hideOn = setOf(
    R.id.wizardFragment,
    R.id.chargeEditFragment,
    R.id.carsFragment,
    R.id.manageLocationsFragment,
)
navController.addOnDestinationChangedListener { _, dest, _ ->
    binding.bottomNav.isVisible = dest.id !in hideOn
}
```

Every new full-screen destination requires editing `MainActivity` to add
its ID to this set. The `manageLocationsFragment` and `carsFragment`
cases were both retrofits — the omission shipped first and had to be
chased down. The Activity should not know which destinations are
full-screen; the destination should declare the fact itself.

## Goal

Move the "hide bottom nav?" decision into the navigation graph as a
per-destination boolean argument. The Activity reads it generically via
the `args: Bundle?` parameter passed to
`addOnDestinationChangedListener`. New destinations get the bit by
declaring an `<argument>` element in `nav_graph.xml`; nothing in
`MainActivity` needs to change.

## Design

### 1. Per-destination boolean argument in `nav_graph.xml`

```xml
<fragment android:id="@+id/wizardFragment" …>
    <argument
        android:name="hideBottomNav"
        app:argType="boolean"
        android:defaultValue="true" />
    <action … />
</fragment>
```

Add the same `<argument>` block (default `true`) to:
`wizardFragment`, `chargeEditFragment`, `carsFragment`,
`manageLocationsFragment`. Bottom-nav destinations
(`dashboardFragment`, `historyFragment`, `chartsFragment`,
`settingsFragment`) **omit** the argument; `Bundle.getBoolean(key)`
returns `false` for missing keys, which is exactly what we want
for "show the nav."

### 2. Generic listener in `MainActivity`

```kotlin
navController.addOnDestinationChangedListener { _, _, args ->
    val hide = args?.getBoolean("hideBottomNav") ?: false
    binding.bottomNav.isVisible = !hide
}
```

The `hideOn` `setOf(…)` declaration is deleted entirely. The Activity
no longer knows any specific destination IDs.

### 3. Why a per-destination argument and not, say, a Fragment annotation

Three options were on the table:

- **A — argument on each `<fragment>`** (the chosen design). Declarative,
  visible right next to the destination, navigation-graph-native, and
  surfaces in the Navigation Editor preview. Default `false` keeps the
  bottom-nav visible without any opt-out boilerplate.
- **B — interface implemented by Fragment classes.** Requires the
  Activity to cast `Fragment.findNavController().currentDestination`'s
  attached fragment, which is awkward. Couples the visibility decision
  to a runtime fragment reference rather than the navigation metadata.
- **C — a tag on `R.id.…` resource declarations.** Brittle — string
  tags are easy to typo and not type-checked.

A is the path of least surprise for a Navigation-Component codebase.

### 4. Default-value semantics

Navigation Component populates the `args` bundle with each declared
argument's `defaultValue` when entering the destination. So:

- Entering `wizardFragment` → `args = {hideBottomNav=true, …}` → nav hides.
- Entering `dashboardFragment` (no `hideBottomNav` declared) →
  `args.getBoolean("hideBottomNav")` returns `false` → nav shows.

Note: `chargeEditFragment` already has an `eventId: integer` argument.
Adding `hideBottomNav` alongside it does not interfere — destination
arguments compose freely.

### 5. Test coverage

- **JVM:** the listener is a one-liner against `Bundle.getBoolean`. The
  decision lives entirely in XML and a generic Activity callback; there
  is no extractable pure function to unit-test on the JVM.
- **Instrumented (`androidTest/`):** add `MainActivityBottomNavTest.kt`
  that:
  1. Launches `MainActivity` (uses the existing Hilt+activity scenario
     pattern from `MainActivityResetRecoveryTest`).
  2. Waits for `isNavGraphMounted()` and the nav graph to settle on
     `dashboardFragment` (setup is complete via Hilt fakes that
     pre-populate the wizard prefs).
  3. Asserts `R.id.bottom_nav` is `View.VISIBLE`.
  4. Navigates to `R.id.chargeEditFragment` (using
     `navController.navigate(R.id.action_dashboard_to_chargeEdit)` from
     the activity scenario).
  5. Asserts `R.id.bottom_nav` is `View.GONE`.
  6. Pops back and re-asserts `View.VISIBLE`.

  This needs an emulator to run, but it compiles via
  `:app:assembleDebugAndroidTest` and is the right shape for the
  TASK-22 follow-up emulator smoke pass.

### 6. CLAUDE.md note

Add a one-paragraph note in the Architecture section codifying the
convention so the next agent doesn't go back to editing `MainActivity`:

> **Bottom-nav visibility (TASK-27):** Each navigation destination that
> should hide the global `BottomNavigationView` declares
> `<argument android:name="hideBottomNav" app:argType="boolean"
> android:defaultValue="true"/>`. `MainActivity` reads this argument
> generically from the `addOnDestinationChangedListener` callback;
> destinations that omit the argument default to `false` (nav visible).
> Adding a new full-screen destination is now a nav-graph edit only —
> never edit `MainActivity` for this.

## Out of scope

- Animating the bottom-nav transition (slide vs. fade) — currently a
  hard `View.GONE` flip and that behavior is preserved.
- Generalising the `hideBottomNav` arg into a "destination flags"
  bundle. Overkill for one bit.
- Custom ktlint rule that detects `R.id.<destination>` references
  inside `MainActivity` (TASK-16 follow-up territory).

## Acceptance

- `nav_graph.xml` declares `hideBottomNav=true` on the four
  full-screen destinations and only those.
- `MainActivity.kt` no longer references any destination ID
  (`grep -n "R.id." app/src/main/java/org/spsl/evtracker/MainActivity.kt`
  returns only `R.id.nav_host_fragment`, `R.id.wizardFragment` for the
  start-destination override in `mountNavGraph`, and `R.navigation.nav_graph`).
- `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest
  :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`
  — all green.
- The new instrumented test compiles.
