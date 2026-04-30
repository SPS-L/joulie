# TASK-22 — Upgrade `targetSdk` and `compileSdk` to 35 — Design

**Date:** 2026-05-01
**Branch:** `feat/task22-sdk35-upgrade`

## Problem

Google Play requires `targetSdk ≥ 35` for new submissions and updates from
mid-2025 onward. The app pins both `compileSdk` and `targetSdk` to 34
(`app/build.gradle.kts:19,24`). Without this bump, the next release-tag push
would still produce a Play-rejected APK.

## Empirical probe (2026-05-01)

I temporarily bumped both knobs to 35 on the current AGP 8.2.0 / Gradle 8.4
toolchain and ran:

- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**, no AGP-version
  warnings about `compileSdk = 35`. AGP 8.2 silently auto-downloaded the
  `android-35` platform under `$ANDROID_HOME/platforms/`.
- `./gradlew :app:lint` — **BUILD SUCCESSFUL**, the lint XML report shows
  only the existing baseline marker. No new API-35-specific issues.

This rules out the contingencies the original backlog text anticipated:
no AGP bump, no Gradle wrapper bump, no `android.suppressUnsupportedCompileSdk`
flag, no manifest changes. The bump is mostly a build-script edit.

## What still needs design work

**Edge-to-edge by default (the only behavioral change).** Starting at
`targetSdk = 35`, the system bars (status bar at top, gesture nav at
bottom) become transparent and the activity window draws under them. With
the current `activity_main.xml` (a vertical `LinearLayout` of
`FragmentContainerView` + `BottomNavigationView`) and a theme that does
not set `android:fitsSystemWindows="true"`, the bottom-nav row would be
drawn under the gesture-nav bar on Android 15+ devices, and Snackbars in
the bottom CoordinatorLayouts would similarly land under the gesture
inset.

A `grep -rn` for `fitsSystemWindows`, `setOnApplyWindowInsetsListener`,
`enableEdgeToEdge`, `WindowInsetsControllerCompat` across `app/src/main`
returned no matches. The codebase has zero existing insets handling.

## Design

### 1. Bump `compileSdk` and `targetSdk` to 35

```diff
- compileSdk = 34
+ compileSdk = 35

- targetSdk = 34
+ targetSdk = 35
```

`gradle/libs.versions.toml` has no SDK aliases, so no version-catalog edit.

### 2. Wire system-bar + display-cutout insets in `MainActivity`

The cleanest minimal pattern that's correct on both API ≤ 34 (where it
becomes a no-op because the bars aren't transparent) and API 35+:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()                 // explicit opt-in across versions
    val splash = installSplashScreen()
    splash.setKeepOnScreenCondition { ... }
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout(),
        )
        v.updatePadding(
            top = bars.top,
            left = bars.left,
            right = bars.right,
            bottom = bars.bottom,
        )
        WindowInsetsCompat.CONSUMED
    }
    // ... existing nav/setup code
}
```

The padding is applied to the root `LinearLayout` of `activity_main.xml`,
which means:

- **Top inset** (status bar / camera cutout): the `FragmentContainerView`
  starts below the status bar. Each Fragment's `CoordinatorLayout` then
  fills exactly the visible content area.
- **Bottom inset** (3-button bar / gesture nav indicator): the
  `BottomNavigationView` sits above the inset when visible. When it's
  hidden via `binding.bottomNav.isVisible = false` on
  Wizard/ChargeEdit/Cars/ManageLocations, the `FragmentContainerView`
  expands and is also padded by the root, so its content stops above the
  gesture-nav indicator.
- **IME**: `windowSoftInputMode="adjustResize"` continues to handle
  keyboard insets. We deliberately do **not** add `Type.ime()` to the
  bitmask — combining `adjustResize` with manual IME padding produces
  double-padding on focus.
- **Left/right insets**: matter only for landscape gesture nav and
  display cutouts; padding all four sides is the conservative choice.

`enableEdgeToEdge()` is from `androidx.activity:activity` 1.8.0
(transitively pulled by AppCompat 1.6.1 and Lifecycle 2.7.0). No new
explicit dependency required — verified with
`./gradlew :app:dependencies | grep androidx.activity`.

### 3. CI workflow (no change)

TASK-22 step 6 in the backlog says to bump an `api-level` matrix in
`.github/workflows/ci.yml`. **This step is moot.** The current CI workflow
runs only the static-analysis job (ktlint + Android Lint + JVM unit
tests). It has no `api-level` matrix because there's no instrumented-test
job — running Espresso on a CI emulator was deferred when TASK-16 landed.
No edit needed.

The `release.yml` workflow uses `compileSdk` indirectly through the
project's Gradle build. Since the build-script bump is the source of
truth, no workflow change is required there either.

### 4. CLAUDE.md and BACKLOG.md

- `CLAUDE.md` Project paragraph mentions "Min SDK 26, target/compile SDK 34" — bump to 35.
- `CLAUDE.md` Build & Test mentions "Build Tools 34" — bump to 35.
- `CLAUDE.md` Status paragraph: append the TASK-22 entry alongside the
  other post-v1 backlog completions.
- `BACKLOG.md` overview row TASK-22 ☐ → ☑; add an `> **Outcome:** …`
  blockquote.

## Out of scope

- **AGP / Gradle wrapper / Kotlin upgrade.** AGP 8.2.0 + Gradle 8.4 build
  cleanly against compileSdk 35. A future upgrade can move us to AGP 8.6+
  for officially-supported tooling, but that's a separate task.
- **API-35 deprecations and new APIs.** The probe lint pass surfaced none.
  If a future feature touches photo/media pickers, foreground services, or
  notification posting, the corresponding API-35 changes will be addressed
  there.
- **R8 keep-rule audit (TASK-17).** The release APK already builds with R8
  minify enabled; chart-library smoke is its own task.
- **Real-device API-35 verification.** This sandbox has no API-35
  emulator. The build, lint, and full JVM unit suite (243 tests) verify
  correctness up to the point a JVM can reach. The edge-to-edge insets
  wiring is defensive and validated by reading the `WindowInsetsCompat`
  contract; Snackbar/FAB visibility on a real Android 15 device is a
  follow-up smoke item the user can run from the installed APK.

## Acceptance

```
$ grep -E "compileSdk|targetSdk" app/build.gradle.kts
    compileSdk = 35
        targetSdk = 35
```

Plus: `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` all green;
JVM test count unchanged at 243; `:app:assembleDebugAndroidTest` still
compiles.
