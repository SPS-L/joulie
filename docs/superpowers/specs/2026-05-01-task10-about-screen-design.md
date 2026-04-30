# TASK-10 — About / Info screen + launcher icon pack drop-in — Design

**Date:** 2026-05-01
**Branch:** `feat/task10-about-screen`
**Unblocked by:** TASK-29 (`buildConfig = true`)
**Bundles:** the icon-pack drop-in subsection added to TASK-10 by the user
on 2026-05-01.

## Goal

Two deliverables in one task, intentionally bundled because they ship the
"v1.0.2 branding" together:

1. **About / Info screen** — accessible from Settings, displays app
   metadata, SPS-Lab acknowledgment, MIT license, disclaimer, and
   open-source notices. Reads version info from `BuildConfig`.
2. **Launcher icon pack** — replace the placeholder vector adaptive
   icon (commit `e1958d7`) with the user-provided PNG mipmap pack
   (`exported-assets.zip` → inner `ev_tracker_icons.zip`).

## Architecture

### About screen

- Single Fragment + ViewBinding, **no ViewModel** — content is static.
  The only dynamic piece is `BuildConfig.VERSION_NAME` /
  `BuildConfig.VERSION_CODE`, which resolve at compile time.
- Layout: `CoordinatorLayout` → `MaterialToolbar` (back navigation) →
  `NestedScrollView` → vertical `LinearLayout` of M3 cards
  (`MaterialCardView`):
  - **App info** card (icon + name + version)
  - **Acknowledgment** card with two tappable URL rows (sps-lab.org,
    cut.ac.cy)
  - **License** card (MIT, full text in a scrollable monospace block)
  - **Disclaimer** card
  - **Open source libraries** card
- All static text in `strings.xml` (HardcodedText is in lint error
  mode).
- Click handlers open URLs via `Intent.ACTION_VIEW` wrapped in
  try/`ActivityNotFoundException`/Snackbar fallback.
- Navigation: new `aboutFragment` destination in `nav_graph.xml`, with
  `hideBottomNav=true` argument (TASK-27 convention — About is a
  full-screen detail view that should not show the bottom nav).
- Reachable from `SettingsFragment` via a new `row_about` at the bottom
  of the rows list, navigating via
  `findNavController().navigate(R.id.action_settings_to_about)`.

### Icon pack drop-in

- Extract `exported-assets.zip` → inner `ev_tracker_icons.zip` →
  `app/src/main/res/`. This populates `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`
  with `ic_launcher{,_round,_foreground,_background}.png` (4 files × 5
  buckets = 20 PNGs).
- Rewrite `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
  so the adaptive-icon `<background>` and `<foreground>` reference
  `@mipmap/...` instead of the existing `@drawable/...`.
- Delete `drawable/ic_launcher_foreground.xml` and
  `drawable/ic_launcher_background.xml` (orphaned vector layers — the
  adaptive icon now sources from the new PNG mipmaps; lint
  `UnusedResources` is in error mode and will fail the build if these
  stay).
- **Keep** `drawable/ic_spslab_badge.xml` — it's the SPS-Lab pill badge
  consumed by the About screen header (added in `e1958d7` for exactly
  this purpose).

## Test coverage

Per the backlog spec:

- **Instrumented test** (`AboutFragmentTest`) — 5 cases:
  1. Version name displayed and non-empty.
  2. "SPS-Lab" text visible.
  3. `sps-lab.org` URL link present.
  4. License card visible and contains the word "MIT".
  5. Disclaimer card visible and contains the word "liability".
- **JVM unit tests:** none. The Fragment has no extractable pure
  function — it's a wiring of static content + click → external Intent
  launch. Mocking the Intent launcher to JVM-test the click handler
  would test the mock, not the code (anti-pattern).
- **Icon pack:** no test surface. Acceptance is the build green +
  visual smoke on a device.

## Strings inventory

New entries in `app/src/main/res/values/strings.xml`:

| Key | Use |
|-----|-----|
| `about_title` | Toolbar title — "About" |
| `about_version_label` | Format string `"Version %1$s (build %2$d)"` |
| `about_acknowledgment_title` | "Developed by" |
| `about_acknowledgment_lab` | "Sustainable Power Systems Lab (SPS-Lab)" |
| `about_acknowledgment_cut` | "Cyprus University of Technology" |
| `about_acknowledgment_location` | "Limassol, Cyprus" |
| `about_link_sps_lab` | Display label "sps-lab.org" |
| `about_link_cut` | Display label "cut.ac.cy" |
| `about_url_sps_lab` | URL "https://sps-lab.org" |
| `about_url_cut` | URL "https://cut.ac.cy" |
| `about_license_title` | "License" |
| `about_license_body` | Full MIT text (multi-line) |
| `about_disclaimer_title` | "Disclaimer" |
| `about_disclaimer_body` | Disclaimer paragraph |
| `about_oss_title` | "Open Source Libraries" |
| `about_oss_body` | Five-line list (Room / Hilt / MPAndroidChart / Coroutines / Drive client, all "(Apache 2.0)") |
| `about_open_link_failed` | Snackbar fallback if no browser is installed |
| `settings_about` | Settings row title — "About" |
| `settings_about_summary` | Settings row summary — "Version, license, acknowledgments" |

URL strings are kept separate from display labels so a future
localisation pass (TASK-15) can translate the labels without touching
URLs.

## Out of scope

- **About screen ViewModel.** The current pure-static content does not
  need one. If a future feature reads runtime data into About (build
  date, last-backup time, debug-only diagnostic info), introduce a
  ViewModel then.
- **Localisation of the new strings.** TASK-15 covers i18n foundation
  globally; `MissingTranslation` is gated only when a `values-<lang>/`
  resource set lands.
- **Play Store icon (`play_store/ic_launcher_512.png`).** The 512×512
  PNG is for Play Console upload; not committed to the repo.
- **Animated splash logo** using the new icon. Splash screen behavior
  stays as it is (`Theme.EVTracker.SplashScreen` with `colorBackground`).

## Acceptance

```
$ find app/src/main/res -name "ic_launcher*.png" | wc -l
20

$ grep "@mipmap/ic_launcher_foreground" app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>

$ ls app/src/main/res/drawable/ic_launcher_*.xml 2>/dev/null
(empty — vector launcher drawables deleted)

$ ls app/src/main/res/drawable/ic_spslab_badge.xml
…/ic_spslab_badge.xml   # preserved

$ ./gradlew ktlintCheck :app:lint :app:testDebugUnitTest \
    :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest
BUILD SUCCESSFUL
```

JVM count stays at 243 (no JVM additions). Instrumented suite gains
one new test class (`AboutFragmentTest`, 5 cases) compile-only.
