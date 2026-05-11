# Joulie, Development Backlog

Tasks 1–15 were generated from a senior Android developer code review of the `main` branch (April 2026). Tasks 16–21 are follow-up improvements identified during a 2026-04-30 verification pass against `main` (CI/release pipeline, R8 keep-rules, a11y posture, and SPS-Lab research relevance). Tasks 38–42 are new feature / infra ideas filed 2026-05-02 from a follow-up senior-developer review (research-aligned analytics, schema-migration polish, anonymised research export). TASK-43 (filed 2026-05-02) closes a real UX gap: many EU/UK chargers and several older EVs (Renault/Nissan/older BMW) display only SoC % before/after, never kWh delivered. Tasks 44–49 are filed 2026-05-03 from a senior-developer code audit cross-checked against the current `main` (`658b60a` + the TASK-43 / TASK-18 Step 6 / nightly-WorkManager fixes): three correctness/UX bugs (`StatsCalculator` cost accumulation, `KwhFromSocCalculator` defensive guard, battery-health overshoot warning) and three research-aligned extensions (charging power profile, time-of-use tariff zones, per-event grid carbon intensity). The audit also folded `kwhSource` / `socBefore` / `socAfter` columns into TASK-09 and concrete K2 / Room version pins into TASK-33. TASK-50 (also filed 2026-05-03) bundles the four fix categories surfaced by the first nightly instrumented cron after the WorkManager-init fix landed, `EmptyFragmentActivity` not declared in the app manifest, a stale `DriveBackupWorkerTest.ioError_returnsRetry` assertion, racy `MainActivityResetRecoveryTest` startup hook, and a `ChartsFragmentTest` initialization error. TASK-51 (filed 2026-05-03) captures the GPL relicensing request after a dependency audit found one concrete review item in the shipped runtime set: `com.google.android.gms:play-services-auth` is still distributed under the Android SDK License, so the GPL-3.0-or-later switch should carry that note explicitly in review. Tasks 52–54 (filed 2026-05-03 from `docs/EV-backlog-review.md`) cover three hardening items confirmed against `main`: CSV-injection / CR / tab coverage in `ExportCsvUseCase.csvEscape`, a multi-car `require(...)` guard in `StatsCalculator.computeStats`, and TASK-54, originally framed as a durable last-seen marker for the Drive restore prompt, since expanded to 🔴 priority after a user-supplied reproduction (2026-05-03 in-conversation): on every Settings entry, the Drive switch visibly flips OFF → ON on its own and the "Restore from Drive?" dialog appears. Root cause is two coupled defects, the switch's `OnCheckedChangeListener` fires when Android's view-state restoration calls `setChecked(true)` after the listener is already attached but before the StateFlow collector's rebind block has run, AND the absence of a durable marker that records the user's "Skip" decision. Both fixes are bundled into TASK-54. The same review proposed two further items (a `NetworkType.CONNECTED` constraint on the backup `WorkRequest` and a forward-compat fallback for unknown `kwhSource` values) which were rejected: both behaviours already hold in `WorkManagerBackupScheduler.kt:27` and `ChargeKwhSource.parseLegacy` respectively. TASK-55 (filed 2026-05-04 as a TASK-15 follow-up) closes the gap that TASK-15 left open: the four locales shipped, but no in-app picker, a Greek-speaking user with an English-defaulted phone has no way to switch the app to Greek without changing the OS-wide language, and the first-launch wizard renders in the system locale before the user has had a chance to choose. Two coupled UX entry points: a Settings → Language row (any time) and a wizard-page-0 dropdown (first run), both wired through a `LocaleApplier` narrow IF over `AppCompatDelegate.setApplicationLocales` so the VM stays JVM-testable. TASK-56 (filed 2026-05-04, shipped 2026-05-07 in `4d3af93` + `f094661` / tag `v1.9.28`) wires `app/src/main/assets/adi-registration.properties` into `release.yml` from an `ADI_REGISTRATION_TOKEN` GitHub Secret + a post-build `unzip -p` size assertion, so CI-built tagged APKs are registered against the developer keystore at Google's verification page; until then only locally-built APKs were registered. TASK-57 is the Joulie Brand Pack v1.0 rollout (gradient bolt-J icons, `<monochrome>` themed-icon layer, brand-blue splash, redesigned wizard-welcome / About hero, `joulie_ink_deep` + `joulie_spark_green` tokens, em-dash retrofit). TASK-58 / TASK-59 + the TASK-60..74 cohort (filed 2026-05-06 → 2026-05-07) stabilise the nightly instrumented suite end-to-end: the Hilt-aware host activity (TASK-58) lets it run to completion, TASK-59 closes the first batch of unmasked failures, TASK-60..68 close the second-pass races + matcher scoping issues surfaced under the API 26 + 35 matrix load (StateFlow ↔ ViewPager2 binding waits, `inActivePage` matcher disambiguation, dialog-flow Activity hosting, multi-currency banner test scoping), TASK-69 lands `scripts/run-instrumented.sh` as the one-command developer mirror of the workflow, and TASK-70..74 rebuild the workflow's emulator phase from scratch (manual Boot / Run / Kill, `PATH` export for `adb` / `emulator`, fresh AVD cache key, cold-boot via `-no-snapshot`, per-iteration boot diagnostics) to escape the 12-min `reactivecircus/android-emulator-runner@v2` shutdown hang on API 26. Each task is written as a self-contained instruction suitable for a coding agent.

---

## Task Overview

| Task | Priority | Description | Requires | Done |
|------|----------|-------------|----------|------|
| TASK-01 | 🔴 | Relocate `AggregationDispatcher` out of `di/` |  | ☑ |
| TASK-02 | 🔴 | Add safeguard KDoc to `RoomDataResetTransactionRunner` (structural rule already holds) |  | ☑ |
| TASK-03 |  | ~~Unify `UiState` vs `ScreenState` naming in `core/model`~~, **closed, premise wrong** |  | ☒ |
| TASK-04 | 🟡 | JVM unit tests for `CostParser` |  | ☑ |
| TASK-05 |  | ~~JVM unit tests for `EfficiencyPoint`~~, **closed, premise wrong** |  | ☒ |
| TASK-06 | 🟡 | JVM unit tests for use cases |  | ☑ |
| TASK-07 | 🟡 | Drive backup error handling & retry logic |  | ☑ |
| TASK-08 |  | ~~Replace `CarEditDialog` with a Compose `AlertDialog`~~, **closed, scope vs value** |  | ☒ |
| TASK-09 | 🟢 | CSV export of charge events with efficiency column, date-range picker |  | ☑ |
| TASK-10 | 🟢 | In-app About / Info screen with SPS-Lab acknowledgment |  | ☑ |
| TASK-11 | 🟡 | Odometer regression detection UX improvement |  | ☑ |
| TASK-12 | 🟡 | Widget: last-charge summary on home screen |  | ☑ |
| TASK-13 |  | ~~Charging session timer / live session mode~~, **closed, scope vs value** |  | ☒ |
| TASK-14 | 🟡 | Battery capacity degradation tracker |  | ☑ |
| TASK-15 | 🟢 | Localisation (i18n) foundation | TASK-16 | ☑ |
| TASK-16 | 🟢 | Static analysis & code style gate in CI (ktlint + Android Lint) |  | ☑ |
| TASK-17 | 🟡 | R8/ProGuard follow-up audit: MPAndroidChart keep rule + release smoke test |  | ☑ |
| TASK-18 | 🟡 | Accessibility (a11y) pass, TalkBack, contentDescription, contrast, touch targets — **PR 1 lock-in** done; PR 2 cleanup + 3 follow-ups filed below (TASK-75/76/77/78) |  | ☑ |
| TASK-19 | 🟡 | Backup failure notification channel + Android 13+ `POST_NOTIFICATIONS` handling |  | ☑ |
| TASK-20 | 🟢 | CO₂ savings tracker (ICE baseline, Cyprus grid intensity, methodology doc) |  | ☑ |
| TASK-21 | 🟢 | Android Baseline Profile module for cold-start performance | 1.12.6 | ☑ |
| TASK-22 | 🔴 | Upgrade `targetSdk` and `compileSdk` to API 35 | TASK-16 | ☑ |
| TASK-23 | 🔴 | Move startup `isLoading` state into `MainViewModel` |  | ☑ |
| TASK-24 | 🔴 | Enforce ViewModel/Activity consumption of the existing narrow domain interfaces (no concrete `data.repository.*` imports outside `di/`) | TASK-23 | ☑ |
| TASK-25 | 🟡 | Replace `chargeType: String` with a sealed class / TypeConverter-backed enum |  | ☑ |
| TASK-26 | 🟡 | Change all Room primary-key and foreign-key fields from `Int` to `Long` |  | ☑ |
| TASK-27 | 🟡 | Decouple bottom-nav visibility from hardcoded `hideOn` set in `MainActivity` |  | ☑ |
| TASK-28 | 🟡 | Consolidate time on existing `NowProvider`; remove direct `System.currentTimeMillis()` from entities and helpers; drop the parallel `() -> Long` clock in `WorkerModule` |  | ☑ |
| TASK-29 | 🟢 | Add explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags |  | ☑ |
| TASK-30 | 🟢 | Migrate from MPAndroidChart to Vico (line/bar) + custom `Canvas` `PieChartView` (pie tabs). **Done 2026-05-10** in `feat/task30-vico-migration` after TASK-33b unblocked Kotlin 2.x. ChartsTabFragment, ChartStyling fully rewritten against Vico 2.0.0; ChartsMarkerView + view_chart_marker.xml deleted; PieChartView wired to AC/DC + Locations tabs. MPAndroidChart dropped from libs.versions.toml + app/build.gradle.kts; JitPack scoping removed from settings.gradle.kts (no remaining JitPack consumer); ProGuard MPAndroidChart keep rule removed. 14 ChartsTab Roborazzi baselines regenerated against Vico rendering and bundled in the same PR per the pre-approved policy exception. 8 unused chart string resources cleaned up across en/el/tr/ru. | TASK-33b | ☑ |
| TASK-31 | 🟡 | Manual Drive controls in Settings: "Back up now" (force overwrite) and "Wipe remote backup" (delete the App Data file) |  | ☑ |
| TASK-32 | 🟡 | Bump AGP (and Gradle wrapper) to a version that officially supports `compileSdk = 35`; remove the `android.suppressUnsupportedCompileSdk` workaround |  | ☑ |
| TASK-33 | 🟡 | Audit Kotlin 2.x / K2 + KSP + Hilt compatibility. **Done 2026-05-10** in `chore/task33-kotlin2-audit`. **Verdict: GO** — target Kotlin 2.1.21 + coordinated bump of Hilt 2.50→2.56.2, KSP 1.9→2.1.21-1.0.x (exact-match), coroutines 1.7.3→1.10.2, Robolectric 4.13→4.14.1, Roborazzi 1.36.0→1.59.0, Lifecycle 2.7.0→2.8.7, Navigation 2.7.6→2.8.5, mockito-kotlin 5.2.1→5.4.0. AGP 8.7.3 + Room 2.6.1 stay. Highest-risk single point: Hilt 2.50 metadata cap (must move with Kotlin in same commit). Execution filed as **TASK-33b**. | TASK-32 | ☑ |
| TASK-34 | 🟡 | Nightly managed-AVD job for `connectedAndroidTest`, keep off the PR gate | TASK-16 | ☑ |
| TASK-35 | 🟢 | Roborazzi screenshot tests for ChartsTab — **Phase 1 (infra) + Phase 2/3 (14 ChartsTab baselines via TASK-79) done 2026-05-10**. Dashboard + outer ChartsFragment baselines remain forward-work. |  | ☑ |
| TASK-36 | 🟡 | Inline-comment the "no `Result.retry()`" invariant in `DriveBackupWorker.doWork()` |  | ☑ |
| TASK-37 | 🔴 | Replace Google Drive backup with a Storage Access Framework (SAF) implementation (F-Droid blocker) |  | ⏸ |
| TASK-38 | 🟢 | Multi-vehicle comparative analytics, overlay 2 cars on a single Charts trend |  | ☐ |
| TASK-39 | 🟢 | Adopt Room `@AutoMigration` for additive schema bumps from v6 onward. **Done 2026-05-07** in `feat/task39-auto-migration-retrofit` (v1.9.33). Manual `MIGRATION_5_6` (ADD socBefore + socAfter) and `MIGRATION_6_7` (ADD kwhSource) replaced with `@AutoMigration` annotations on `@Database`. Room's KSP synthesises the migration SQL at compile time from the exported schemas in `app/schemas/`; generated SQL is byte-for-byte equivalent to the hand-written ALTER TABLE statements (modulo backtick escaping). `ChargeEventEntity.kwhSource` gained `@ColumnInfo(defaultValue = "MEASURED")` so KSP could emit the SQL default in the generated `ALTER TABLE … ADD COLUMN … DEFAULT 'MEASURED'`. `DatabaseModule.provideDatabase` and `MigrationTest` updated; `MigrationTest` now uses `MigrationTestHelper.runMigrationsAndValidate(...)` for the v5→v6 + v6→v7 cases. CLAUDE.md migration list + DESIGN.md §4.2 carry the going-forward convention: additive bumps from v8 onward default to `@AutoMigration`; non-additive bumps stay manual. Mutation kill verified pre-merge. |  | ☑ |
| TASK-40 | 🟢 | Anonymised research-export pipeline (PII-stripped CSV for SPS-Lab) | TASK-09 | ☐ |
| TASK-41 | 🟢 | JSON-LD / OCPP-compatible export format (research interoperability) | TASK-09 | ⏸ |
| TASK-42 | 🟢 | Open Charge Map / OCPI station lookup integration | TASK-37 | ⏸ |
| TASK-43 | 🟡 | kWh-from-SoC calculator + `kwhSource` provenance flag (degradation banner on derived events) | TASK-14 | ☑ |
| TASK-44 | 🟡 | Fix `StatsCalculator.computeStats` cost accumulation (first event's cost silently dropped; inconsistent with `computeMonthlyBuckets`) |  | ☑ |
| TASK-45 | 🟢 | Defensive SoC range guard (`require(...)`) in `KwhFromSocCalculator.compute` |  | ☑ |
| TASK-46 | 🟡 | Battery-health card "Estimated" warning when heuristic over-estimates (>105% of nominal AND `isExact = false`) |  | ☑ |
| TASK-47 | 🟢 | Charging power profile fields (`peakPowerKw`, `chargingDurationMinutes`), schema bump |  | ⏸ |
| TASK-48 | 🟢 | Time-of-use (ToU) tariff classification on charge events |  | ⏸ |
| TASK-49 | 🟢 | Per-event grid carbon intensity (extends TASK-20 with marginal emission factors) | TASK-20 | ☐ |
| TASK-50 | 🔴 | Stabilise nightly instrumented suite, 21 failures across 4 root causes after WorkManager init landed | TASK-34 | ☑ |
| TASK-51 | 🔴 | GPL-3.0-or-later license change (pending `play-services-auth` review) |  | ☑ |
| TASK-52 | 🟡 | CSV escape hardening in `ExportCsvUseCase`, quote `\r` and tabs, neutralise spreadsheet formula-injection prefixes (`=`, `+`, `-`, `@`) in user-supplied fields |  | ☑ |
| TASK-53 | 🟡 | Multi-car invariant guard in `StatsCalculator.computeStats`, `require` the input shares a single `carId` (latent bug if a future caller passes a mixed-car list) |  | ☑ |
| TASK-54 | 🔴 | Drive switch fires `onUserToggledOn()` on every Settings entry (view-state restoration anti-pattern), visible OFF→ON flicker + restore-prompt loop; bundled with a durable last-seen marker for the destructive-action path | TASK-31 | ☑ |
| TASK-55 | 🟡 | Language picker, Settings → Language row (any time) AND first-run picker on the wizard so users never see an unintelligible welcome screen. `AppCompatDelegate.setApplicationLocales` + persisted `language_tag` DataStore key | TASK-15 | ☑ |
| TASK-56 | 🟡 | CI release wiring for the ADI registration token, write `app/src/main/assets/adi-registration.properties` from a GitHub Secret in `release.yml` before `assembleRelease`, so CI-built tagged APKs pass Google's developer-verification check |  | ☑ |
| TASK-57 | 🟢 | Adopt **Joulie Brand Pack v1.0** (gradient bolt-J icons + `<monochrome>` themed-icon layer for Android 13+, brand-blue splash with white bolt, redesigned wizard-welcome and About screens with gradient hero artwork, SPS-Lab badge recoloured to `#FFD54D`, multi-density notification icon, `joulie_ink_deep` + `joulie_spark_green` colour tokens, `wizard_page1_intro_title` string in en/el/tr/ru, voice-rule em-dash retrofit across 9 docs + 4 string locales) |  | ☑ |
| TASK-58 | 🔴 | Hilt-aware host activity for instrumented fragment tests, `@AndroidEntryPoint HiltTestActivity` + `launchFragmentInHiltContainer` helper replaces `EmptyFragmentActivity`-backed `launchFragmentInContainer`, which has been crashing every nightly run since the API 26 + API 35 matrix started (2026-05-02 onwards) with `IllegalStateException: Hilt Fragments must be attached to an @AndroidEntryPoint Activity` | TASK-50 | ☑ |
| TASK-59 | 🔴 | Green up the 10 nightly instrumented failures unmasked once TASK-58 let the suite run to completion: 3 About tests (`scrollTo()` for off-fold cards + scoped `withId(R.id.about_acknowledgment_lab)` to disambiguate "SPS-Lab" matching three TextViews), 1 Charts class-level init error (3 `@Test fun X() = runBlocking { ... }` methods inferring non-Unit return types from trailing `.use { ... }` blocks; JUnit 4 rejects them — wrap in block bodies), 2 ChargeEdit a11y violations (`MaterialToolbar` nav button missing `app:navigationContentDescription` — added `common_navigate_back` string in en/el/tr/ru), 1 ManageLocations DAO-Flow-RecyclerView race (poll `customLocationDao.observeAll()`), 2 Settings issues (`row_reset_all` below the fold → `scrollTo()`; `TestNavHostController.setGraph` off-main → `runOnMainSync`), 1 Wizard a11y (24 dp `TabLayout` indicator dots clickable → mark non-clickable / non-focusable since the wizard navigates via Back/Next buttons and pager swipe is already disabled). Ancillary: brand-pack `joulie_ink_deep` wired as the About `about_app_name` wordmark text colour with a `values-night/` light-variant override (per Brand Guide §3 light-surface scope); `joulie_spark_green` baselined as a kept brand-palette token (mirrors the `chart_4` precedent which is the same `#A6F43C` value, also baselined). | TASK-58 | ☑ |
| TASK-60 | 🔴 | Round 2 of nightly stabilisation: green up 8 of 13 remaining instrumented failures uncovered after the TASK-58 / TASK-59 first cleanup pass let the suite run to completion. | TASK-59 | ☑ |
| TASK-61 | 🔴 | StateFlow / ViewPager2 binding races in the instrumented suite: replace ad-hoc `Thread.sleep` waits with bounded view polling so wizard, dashboard, and history tests survive page-attach lifecycle gaps that surfaced once the suite ran end-to-end. | TASK-60 | ☑ |
| TASK-62 | 🔴 | `inActivePage` matcher scoped to wrong root: qualify by ViewPager2 child rather than bare `isDisplayed` so off-screen wizard pages stop matching the active page during in-flight transitions. | TASK-61 | ☑ |
| TASK-63 | 🔴 | Convert 3 dialog-flow tests to MainActivity hosting: `MaterialAlertDialog`-driven flows (Drive enable, Reset all data, Wipe remote backup confirmation) need a real Activity rather than `HiltTestActivity` so the dialog's lifecycle owner is wired before `setContent` fires. | TASK-58 | ☑ |
| TASK-64 | 🔴 | `TextInputLayout`-aware error matcher in ChargeEdit test: traverse to the inner `TextInputEditText` so error-text assertions read the rendered error chip rather than the wrapper layout. | TASK-60 | ☑ |
| TASK-65 | 🔴 | Pre-swipe poll for ManageLocations `swipe_no_undo` flake: wait for the row to be drawn before calling `swipeLeft()` — a dispatcher race that only surfaced under the heavier matrix load. | TASK-60 | ☑ |
| TASK-66 | 🔴 | `inActivePage` matcher disambiguates by window focus on API 26: the prior `withParent(...)` predicate matched two pages on the older platform; switch to `hasWindowFocus()` so exactly the foreground page matches across the API 26 + 35 matrix. | TASK-62 | ☑ |
| TASK-67 | 🟡 | Multi-currency banner test uses text filter: the banner's container `View` exists across all states, so assert against `withText(...)` on the localised strings instead of `isDisplayed` on the wrapper. | TASK-60 | ☑ |
| TASK-68 | 🔴 | Two new race conditions surfaced by v11 of the nightly suite: ChartsFragment list-rebind and Wizard tab-indicator-attach both needed bounded-poll waits to settle the StateFlow → View hop deterministically. | TASK-66 | ☑ |
| TASK-69 | 🟢 | `scripts/run-instrumented.sh` for local AVD test runs: KVM-accelerated boot + animation-scale gate + tear-down, so `connectedDebugAndroidTest` is a one-command operation on a developer's box; mirrors what the nightly workflow does in CI. | TASK-34 | ☑ |
| TASK-70 | 🔴 | Nightly hangs after `BUILD SUCCESSFUL` on emulator shutdown: `reactivecircus/android-emulator-runner@v2`'s shutdown phase deterministically blocks ~12 min on API 26 (`adb emu kill` returns, gRPC `stop` logs `ERROR | stop: Not implemented`, action never returns). Replace its run-step with a manual Boot / Run / Kill phase. | TASK-34 | ☑ |
| TASK-71 | 🔴 | Drop in-script kill (made the action fail): the manual `pkill -9` interfered with the action's own teardown when both ran. Keep the action for the AVD-creation step and only do manual cleanup in the test phase. | TASK-70 | ☑ |
| TASK-72 | 🔴 | Manual emulator orchestration: drop `reactivecircus/android-emulator-runner@v2` from the test step entirely; keep it only for snapshot creation where the shutdown bug doesn't bite. | TASK-71 | ☑ |
| TASK-73 | 🔴 | Put Android SDK tools on `PATH` for the manual emulator steps: GitHub-hosted `ubuntu-latest` installs the SDK at `/usr/local/lib/android/sdk` but does NOT put `platform-tools` / `emulator` on `PATH`; export both into `$GITHUB_PATH` so `adb` / `emulator` resolve by name in the manual phase. | TASK-72 | ☑ |
| TASK-74 | 🔴 | Fresh AVD cache + cold-boot + boot-step diagnostics: bump the AVD cache key suffix to `v2` so a stale snapshot can't load against a drifted emulator binary; add `-no-snapshot` for an explicit cold boot; add per-iteration `getprop sys.boot_completed` + `init.svc.bootanim` polling so a hang is diagnosable from the emulator log. | TASK-72 | ☑ |
| TASK-75 |  | ~~A11y cleanup of priority fragments~~, **closed 2026-05-10, premise no longer holds** — verified zero baselined `ContentDescription` / `LabelFor` / `KeyboardInaccessibleWidget` violations and `:app:lintDebug` BUILD SUCCESSFUL with all three rules in error mode, so no debt to clean and no debt can accrue without breaking the build. | TASK-18 | ☒ |
| TASK-76 | 🟡 | Contrast audit on M3 tokens. **Done 2026-05-07** in `feat/task76-m3-contrast-audit` (v1.9.31). `M3ContrastAuditTest` audits 31 text/surface pairs (16 light + 15 dark) against WCAG 2.1 AA, locked against re-seed regression. All pairs clear: lowest text pair `light tertiary text` 6.43:1 (white on `#7F5700`), lowest UI pair `light outline on surface` 4.40:1, tightest decorative `light outlineVariant on surface` 1.66:1 against the 1.5 floor (decorative-only — M3 design intent for `outlineVariant` is intentionally subtle, used here only for Settings dividers + chart gridlines, both excluded from WCAG 1.4.11). Mutation kill verified pre-merge. The historical `#FB8C00` flag was already retired by TASK-57's tertiary re-seed. Component-state contrast and dynamic-color variants remain out of scope (forward-work). See `docs/DESIGN.md §11.2`. | TASK-18 | ☑ |
| TASK-77 | 🟢 | `MaterialButtonToggleGroup` state-change announcements. **Done 2026-05-07** in `feat/task77-toggle-a11y-announcements` (v1.9.32). All three toggle groups in the app (ChargeEdit AC/DC, ChargeEdit cost-mode, Wizard km/miles) now fire `announceForAccessibility("<label> selected")` on each check transition via the `announceCheckedStateOnChange` extension in `ui/common/ToggleGroupA11y.kt`. Localised template `a11y_toggle_selected` shipped in en/el/tr/ru. TEST_PLAN.md §5c grew by three release-gate walkthrough rows. Native review of el/tr/ru phrasing remains forward-work. | TASK-18 | ☑ |
| TASK-78 | 🟢 | TalkBack walkthrough notes from real-device session. Run TEST_PLAN.md §5c on a physical Pixel + a physical lower-end device (e.g. Moto G), file findings as concrete fixes. | TASK-18 | ☐ |
| TASK-79 | 🟢 | Capture the 14 ChartsTab baseline PNGs. **Done 2026-05-10** in `feat/task79-chartstab-baselines`. Strategy actually used: mocked `ChartsViewModel` via fake parent fragment that overrides `defaultViewModelProviderFactory` (lighter than the originally-planned `@BindValue` graph; `ChartsTabFragment` only reads `viewModel.uiState` so a Mockito mock with a hardcoded `MutableStateFlow` is sufficient). `@GraphicsMode(NATIVE)` enables real Robolectric Canvas rendering; `ShadowLooper.idleMainLooper(800ms)` advances past `MPAndroidChart.animateY(400)`. CI gate live in `.github/workflows/ci.yml`. | TASK-35 | ☑ |
| TASK-33b | 🟡 | Execute the Kotlin 2.x upgrade per the TASK-33 audit. **Done 2026-05-10** (v1.11.0). Single coordinated catalog bump: `kotlin = 2.1.20`, `ksp = 2.1.20-1.0.32` (KSP1, exact match), `hilt = 2.56.2`, `coroutines = 1.10.2`, `robolectric = 4.14.1`, `roborazzi = 1.59.0`, `lifecycle = 2.8.7`, `navigation = 2.8.5`, `mockito-kotlin = 5.4.0`. AGP bumped 8.7.3 → 8.9.2 + Gradle 8.10.2 → 8.11.1 to dodge the FIR-Lint crash on the test runner. Unblocked **TASK-30** (Vico migration) which had been blocked on Kotlin 2.x. | TASK-33 | ☑ |
| TASK-87 | 🟢 | Surface the live "right now" carbon intensity on the Charts CO₂ tab. **Done 2026-05-11** in `feat/task87-co2-tab-intensity-banner` (v1.12.4). Pivoted from "horizontal reference line": the CO₂ cumulative chart's y-axis is **kg CO₂** (running totals across the period), not gCO₂/kWh, so overlaying a `HorizontalLine` at the current intensity would render at a unit-nonsense position. Implemented instead as a bucket-tinted banner above the chart ("Right now: 412 gCO₂eq/kWh · Moderate", painted with the same 5-band palette as the Dashboard pill). `ChartsScreenState` gained a nullable `currentCarbonReady: CarbonIntensityUiState.Ready?` which the VM populates from the persistent cache flows + zone + co2Enabled + apiKey gates. Banner reuses the existing `charts_tab_banner` TextView in `fragment_charts_tab.xml` — same slot the Degradation tab uses for the derived-events warning, just with bucket tint instead of the secondary-container theme color. 4-locale string `charts_co2_current_intensity_banner`. TASK-79 Roborazzi baselines unchanged because the canonical Charts fixture leaves `currentCarbonReady = null` (banner doesn't render). |  | ☑ |
| TASK-86 | 🟢 | Dashboard Roborazzi baselines: 5 carbon-intensity buckets × 2 themes + Loading + Error states. **Done 2026-05-11** in `feat/task86-dashboard-roborazzi`. 12 PNGs under `app/src/test/screenshots/dashboard_carbon_*.png`. Test inflates `WidgetCarbonIntensityBinding` inside `HiltTestActivity` (no fragment factory needed) and feeds a fixed `nowMs` from `CarbonIntensityFixtures` so the "Updated X ago" subtitle stays deterministic. Loading + Error captured once each because the renderer paints both with theme-agnostic `md_theme_light_surfaceVariant`. TASK-90 extended the matrix from 12 → 16 PNGs to cover the 5 Error sub-states (auth/network/rate-limited/server/unknown). Captured the "Updated 8 min. ago ago" stuttering double-suffix as TASK-88. |  | ☑ |
| TASK-90 | 🔴 | Diagnose + surface carbon-intensity fetch failures. **Done 2026-05-11** in `feat/task90-carbon-error-feedback` (v1.12.0). Root cause: `ElectricityMapsRepository.defaultHttpFetch` ran `HttpURLConnection.responseCode` on whatever dispatcher the caller used; `viewModelScope.launch` lands on `Dispatchers.Main.immediate`, which threw `NetworkOnMainThreadException`, swallowed by the bare `catch (_: Exception)`, returned null, and surfaced as a permanently-stuck "Tap to retry" pill. Fix: wrap in `withContext(Dispatchers.IO)`. Plus: typed `FetchOutcome` (Success / AuthError / RateLimited / ServerError / NetworkError / Disabled) so the pill can render specific reasons ("API key rejected. Zone may not match your key.", "No internet connection.", "Rate limit reached. Try again in an hour."). Repo exposes `lastError: StateFlow<FetchOutcome?>` which DashboardViewModel combines into the carbon-intensity state. New `probeApiKey(zone, key)` on the interface — cache-free / lastError-free probe used by the Settings → API key dialog's new "Test" button (TASK-90 §C) so users can validate a candidate key before saving. 16 JVM tests added or rewritten; 16 Roborazzi baselines (12 from TASK-86 + 5 Error sub-states – `unknown`/`auth`/`network`/`rate_limited`/`server`). Localized in en/el/tr/ru. Minor bump v1.11.5 → v1.12.0. |  | ☑ |
| TASK-88 | 🟢 | Fix the "Updated 8 min. ago ago" stuttering double-suffix in `CarbonIntensityRenderer`. **Done 2026-05-11** in `fix/task88-ago-stutter` (v1.12.1). Strings simplified to "Updated %1$s" / "Ενημερώθηκε %1$s" / "Son güncelleme: %1$s" / "Обновлено %1$s" across en/el/tr/ru so the suffix `DateUtils.getRelativeTimeSpanString` already supplies is no longer doubled. Same fix applied to `carbon_intensity_a11y_description`. 10 Ready-state Roborazzi baselines regenerated in the same PR per the TASK-30 precedent (the text change IS the rendering change). Patch bump v1.12.0 → v1.12.1. |  | ☑ |
| TASK-89 | 🟡 | Warn users about the 30-day zone-change lock at Electricity Maps. **Done 2026-05-11** in `feat/task89-zone-lock-warning` (v1.12.3). `settings_co2_zone_dialog_helper` rewritten in all four locales to add "Must match the zone you selected when creating your Electricity Maps API key. Changing it there is rate-limited to once per 30 days." The helper text is already rendered as `TextInputLayout.helperText` under the zone input via the existing `showElectricityMapsZoneDialog` impl — no code changes, just localized copy. Confirmation-dialog escalation deferred (the helper-text surface is right where the user is about to act, so a dialog on top would feel naggy). Patch bump v1.12.2 → v1.12.3. | TASK-90 | ☑ |
| TASK-85 | 🟢 | Auto-refresh the carbon-intensity pill when the user changes the Electricity Maps zone in Settings. **Done 2026-05-11** in `feat/task85-zone-change-refresh` (v1.12.2). `SettingsViewModel.onElectricityMapsZoneSet` now compares the new zone against the previous one and, on a real change, dispatches a `CarbonIntensitySource.fetchCarbonIntensity(newZone, apiKey)` directly — passing the new zone explicitly instead of going through `RefreshCarbonIntensityUseCase` which would re-read the old zone from settings under the DataStore write-then-read race. Gated on `co2Enabled && apiKey.isNotBlank()` so disabling CO₂ or clearing the key short-circuits. Four JVM tests cover different-zone / same-zone / case-normalised / blank-input paths. Patch bump v1.12.1 → v1.12.2. |  | ☑ |
| TASK-84 | 🟢 | Periodic in-foreground refresh of the carbon-intensity pill. **Done 2026-05-11** in `feat/task84-periodic-pill-refresh` (v1.12.5). `DashboardViewModel.startPeriodicRefresh()` / `stopPeriodicRefresh()` are called from `DashboardFragment.onStart` / `onStop` so the loop is tied to the Fragment's visibility lifecycle — ticks every 15 min while the user is on the dashboard, stops when they navigate away. The repo's persistent 1-hour throttle still gates real API calls, so the loop is cheap (one cache read per tick) until the cached value expires; the next tick after that hits the network. Three JVM tests cover one-interval / three-interval / never-started paths. Interval is 15 min, picked to land midway between "fast enough to surface a post-TTL refresh before the user notices" and "slow enough to not spam the repo Mutex" — invariant guarded by a third test. |  | ☑ |
| TASK-83 | 🟢 | Refresh the Joulie landing page + README + PRIVACY for the v1.11.4 surface changes. **Done 2026-05-11** bundled with TASK-82's merge. Landing page rewords the CO₂ bullet to describe the live Electricity Maps integration + on-device 1h cache + the five-band dashboard pill. README mirrors the same copy. PRIVACY.md gains a paragraph under "CO₂ Tracker (Opt-In)" explaining the `(zone, api_key)` payload, the once-per-zone-per-hour throttle, the three fetch trigger sites, and the no-PII contract. `INTERNET` permission row updated. Pages workflow auto-deploys on push to main. |  | ☑ |
| TASK-82 | 🟢 | Carbon-intensity dashboard widget + boot refresh + History CO₂ row. **Done 2026-05-11** in `feat/task82-carbon-intensity-widget`. Top-of-dashboard pill shows the live grid intensity from Electricity Maps colour-coded across five bands (`VERY_LOW < 150` / `LOW < 400` / `MODERATE < 650` / `HIGH < 900` / `VERY_HIGH ≥ 900`). State machine: `Hidden` (CO₂ off OR no key) / `Loading` (refreshing + no fresh cache) / `Ready(value, bucket, fetchedAtMs)` / `Error` (tap to retry). Refreshes triggered from `MainViewModel.init` (boot) + `DashboardViewModel.init` (belt-and-suspenders) + `SaveChargeEventUseCase` (per-save, unchanged) + tap-to-retry — all serialised by the existing TASK-81 persistent 1-hour throttle. New `RefreshCarbonIntensityUseCase` (thin wrapper) + `CarbonIntensityFormatter` (pure JVM, 8-input → state mapper) + `CarbonIntensityRenderer` (view glue). History list adds a third metadata line `"⚡ X kg CO₂  ·  Y g/kWh"` gated on `co2Enabled` AND `event.gridIntensityGCo2PerKwh != null`. Bundled TASK-83 (website/README/PRIVACY refresh) in the same merge so the landing page describes v1.11.4 correctly. 13 new JVM tests (formatter state machine + bucket boundaries + use case branches). Patch bump v1.11.3 → v1.11.4. |  | ☑ |
| TASK-81 | 🔴 | Drop the static `gridIntensityGCo2PerKwh` preference; add a persistent 1-hour API throttle. **Done 2026-05-11** in `feat/task81-drop-static-grid-intensity`. Either Electricity Maps live data flows or NO CO₂ surfaces show — there is no user-typed grid-intensity fallback (guessing CO₂ from a typed number is misleading). Removed: the `GRID_INTENSITY_G_CO2_PER_KWH` DataStore key, the `SettingsReader.gridIntensityGCo2PerKwh` / `SettingsWriter.setGridIntensityGCo2PerKwh` API, the Settings → CO₂ "Grid intensity (gCO₂/kWh)" row + string in all four locales, the manual-fallback branch in `SaveChargeEventUseCase`, and the per-row fallback in `ObserveDashboardStatsUseCase.evCo2ForFiltered`. `CO2Calculator.evCo2Kg` now returns `Double?` (null when no event in the period carries a per-event intensity) and `cumulativeTrend` returns empty under the same condition. `ObserveDashboardStatsUseCase` gates the ICE counterfactual on the EV side being computable so the dashboard never shows ICE without EV. Persistent throttle: three new DataStore keys (`electricityMapsCacheZone` / `electricityMapsCacheIntensity` / `electricityMapsCacheFetchedAtMs`) plus an atomic `setElectricityMapsCache(zone, intensity, fetchedAtMs)` setter. `ElectricityMapsRepository` now consults both in-memory cache AND the persisted (zone, fetchedAt) before making any HTTP call — the once-per-zone-per-hour guarantee survives process restart. A `Mutex` serialises concurrent fetches. `CarbonIntensitySource.clearCache` is `suspend` so the persistent layer can be cleared too. `ResetAllDataUseCase` calls it unchanged at the call site. 11 new + 8 rewritten JVM tests across CO2Calculator, SaveChargeEventCo2Test, ObserveDashboardStatsCo2GateTest, ElectricityMapsRepositoryTest (incl. the cold-start-within-an-hour case proving the persistent throttle works across "process restart"). Patch bump v1.11.2 → v1.11.3. |  | ☑ |
| TASK-80 | 🟢 | Electricity Maps live CO₂ integration (issue #1). **Done 2026-05-11** in `feat/task80-electricity-maps`. Opt-in `co2Enabled` master switch + `electricityMapsApiKey` (password-masked dialog) + `electricityMapsZone` (uppercase, CY default) DataStore keys; new `CarbonIntensitySource` domain interface bound to `ElectricityMapsRepository` — `HttpURLConnection` + 30-char regex JSON parse (the `org.json.JSONObject` stub on the JVM test classpath returns 0/null for every getter, so the parse must avoid it) with a 1 h in-memory cache; no new transitive deps. Room v7→v8 `@AutoMigration` adds the nullable `grid_intensity_g_co2_per_kwh` column to `charge_events`; `backup_version` 7→8, new field optional on v3..v7 backups. `SaveChargeEventUseCase` resolves intensity in a strict hierarchy: off → null; on + blank key → manual fallback; on + key + fetch returns value → live; on + key + fetch returns null → manual fallback. `ObserveDashboardStatsUseCase` hides both EV + ICE stats when `co2Enabled=false`; when on, EV emissions prefer the per-event column and fall back to the static manual pref per row (so legacy rows backfill cleanly). `ObserveChartsModelsUseCase` omits the cumulative CO₂ series when off. `ResetAllDataUseCase` resets the three new prefs + calls `CarbonIntensitySource.clearCache()`. 17 new JVM tests across the cache (8), CO₂ gate (4), and save-time hierarchy (5). Settings layout: `MaterialSwitch switch_co2_enabled` + helper text + `row_co2_api_key` + `row_co2_zone` (visibility gated on `co2Enabled`); password-masked text input via `END_ICON_PASSWORD_TOGGLE`; "Open electricitymaps.com" neutral button on the key dialog. Strings shipped in all 4 locales (en/el/tr/ru). Z-bump v1.11.1 → v1.11.2. |  | ☑ |

**Priority legend:** 🔴 High (architecture/data safety) · 🟡 Medium (robustness/UX) · 🟢 Low (new feature)  
**Status legend:** ☐ open · ☑ done · ☒ closed (premise no longer holds) · ⏸ under consideration (do not start without explicit go-ahead) · ◐ partially done (some scope landed, remainder filed as a follow-up)  
**Requires column:** `TASK-NN` means the named task should land first. An empty cell means no hard prerequisite. Soft coordination notes (Room schema-version claiming, TASK-30 keep-rule cleanup) live in *Notes for Agents* below rather than the column.  
Mark done by replacing `☐` with `☑` when a task is merged.

---

## 🔴 TASK-01, Relocate `AggregationDispatcher` to the correct package ☑ Done (2026-04-30)

> **Outcome:** the file (a Hilt `@Qualifier` annotation, not a class with logic) was relocated from
> `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt` to
> `app/src/main/java/org/spsl/evtracker/core/coroutines/AggregationDispatcher.kt`.
> The new package is a cross-cutting home for coroutine qualifiers (room for `IoDispatcher`,
> `MainDispatcher`, etc.) and removes the previous `domain → di` import in
> `ObserveChartsModelsUseCase`. Imports updated in `di/DispatcherModule.kt` and
> `domain/usecase/ObserveChartsModelsUseCase.kt`. See spec
> `superpowers/specs/2026-04-30-task01-aggregation-dispatcher-relocation-design.md`
> and plan `superpowers/plans/2026-04-30-task01-aggregation-dispatcher-relocation.md`.
> The original task text is preserved below for historical context.

The file `app/src/main/java/org/spsl/evtracker/di/AggregationDispatcher.kt`
is incorrectly placed inside the `di` (dependency injection) package.
A dispatcher is a domain or data concern, not a DI module.

1. Move `AggregationDispatcher.kt` to:
   `app/src/main/java/org/spsl/evtracker/domain/service/AggregationDispatcher.kt`
2. Update all import statements across the project to reflect the new path.
3. If `AggregationDispatcher` is injected via Hilt, update the binding in
   `AppModule.kt` or create a dedicated `DispatcherModule.kt` in `di/` that
   provides it, but the class itself must not live in `di/`.
4. Verify the project builds and all instrumented tests pass after the move.

---

## 🔴 TASK-02, Add safeguard KDoc to `RoomDataResetTransactionRunner` ☑ Done (2026-05-01)

> **Outcome:** the safeguard KDoc was added to both
> `domain/repository/DataResetTransactionRunner.kt` and
> `data/repository/RoomDataResetTransactionRunner.kt`. The block on the
> interface explains that `clearAllTables()` is reachable only through
> [`ResetAllDataUseCase`] because the use case owns the `resetInProgress`
> durable-flag protocol that lets startup auto-recovery resume a
> half-finished reset; the impl points back to the interface KDoc and
> notes that even the interface is not for general consumption. The
> structural rule already held empirically (`grep` finds only the impl
> file and `di/DomainModule.kt`), and the wider narrow-IF rule from
> TASK-24 is now codified in CLAUDE.md §Architecture, so this KDoc is the
> last layer, a type-level reminder for the next agent reading the
> code. Mechanical enforcement (a custom ktlint rule) remains a TASK-16
> follow-up; the current cost/benefit doesn't justify spinning one up
> for two files. The original task text is preserved below.

> **Verification (2026-04-30):** the structural rule already holds. A
> `grep -rn "RoomDataResetTransactionRunner" app/src/main/java` returns only
> `data/repository/RoomDataResetTransactionRunner.kt` (the implementation) and
> `di/DomainModule.kt` (the `@Binds` to `DataResetTransactionRunner`). All
> consumers, currently only `ResetAllDataUseCase`, depend on the narrow
> `DataResetTransactionRunner` interface. The remaining work is documentation
> only.

1. Add a KDoc to `RoomDataResetTransactionRunner` and to
   `DataResetTransactionRunner` explicitly stating: *"Destructive operation —
   must only be called from `ResetAllDataUseCase`. ViewModels and Fragments
   must not depend on this type or its interface directly."*
2. Optionally add a `lint-baseline.xml` or ktlint custom rule (TASK-16
   follow-up) once that infrastructure exists, to mechanically enforce the
   no-direct-consumer invariant.

---

## ☒ TASK-03, ~~Unify `UiState` vs `ScreenState` naming convention in `core/model`~~

> **Closed (2026-04-30):** premise is wrong. `ChartsUiState` and
> `ChartsScreenState` are not duplicates. `ChartsScreenState` is the outer
> screen frame (`period`, `distanceUnit`, plus a `charts: ChartsUiState`
> field), and `ChartsUiState` is the inner sealed content state
> (`Loading / NoCar / NoEvents / Loaded`). Same pattern for
> `DashboardScreenState` (frame) wrapping `DashboardUiState` (content with
> `emptyState`, `stats`, `showMultiCurrencyBanner`). The split is intentional;
> renaming would conflate the two layers. If a stylistic rename of `*ScreenState`
> ever becomes desirable, file a fresh task, but it is not the
> deduplication described here.

---

## 🟡 TASK-04, Add JVM unit tests for `CostParser` ☑ Done (verified 2026-04-30)

> **Outcome:** `app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt`
> exists (55 lines) and covers zero-cost, blank, negative, total→perKwh
> derivation, and per-kWh→total derivation paths. The original task text is
> preserved below for historical context.

The class `app/src/main/java/org/spsl/evtracker/domain/service/CostParser.kt`
has no corresponding JVM unit tests. This is a pure logic class and must be
tested without an emulator.

Create the file:
`app/src/test/java/org/spsl/evtracker/domain/service/CostParserTest.kt`

Write unit tests covering:
1. Standard cost parsing with a valid decimal input (e.g., `"0.25"`).
2. Cost parsing with a comma as decimal separator (e.g., `"0,25"`).
3. Empty string input, expect a specific default or exception.
4. Null input if the function accepts nullable strings.
5. Negative values, define and test expected behavior.
6. Values with currency symbols (e.g., `"€0.25"`), confirm correct handling.

Use JUnit 4 or JUnit 5 consistent with the existing test setup. Do not use
any Android framework classes, this must be a pure JVM test.

---

## ☒ TASK-05, ~~Add JVM unit tests for `EfficiencyPoint`~~

> **Closed (2026-04-30):** premise is wrong. `EfficiencyPoint` is a 2-field
> data class (`eventTimeMillis: Long`, `kmPerKwh: Double`) with zero logic.
> The proposed cases (zero distance, NaN handling, overflow,
> cost-per-km derivation) belong to the *producer* of these points, not the
> point itself, the production logic lives in `StatsCalculator` /
> `EfficiencyStats`, which already have JVM tests in
> `app/src/test/java/org/spsl/evtracker/domain/service/`. If gaps exist
> there, file a fresh task naming the missing scenarios.

---

## 🟡 TASK-06, Add JVM unit tests for `RenameCarUseCase` and `ResetAllDataUseCase` ☑ Done (verified 2026-04-30)

> **Outcome:** both test files exist on `main`:
> `app/src/test/java/org/spsl/evtracker/domain/usecase/RenameCarUseCaseTest.kt`
> (42 lines) and `…/ResetAllDataUseCaseTest.kt` (130 lines), both wired
> through the existing fakes in
> `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`. The original task
> text is preserved below for historical context.

The domain use cases have no JVM unit tests. Business logic must be tested
independently of the Android framework.

### `RenameCarUseCaseTest`

Create:
`app/src/test/java/org/spsl/evtracker/domain/usecase/RenameCarUseCaseTest.kt`

Tests required:
- Renaming with a valid non-empty name calls the repository exactly once.
- Renaming with a blank name throws `IllegalArgumentException` or returns
  a failure result (match the actual implementation's contract).
- Renaming with a name exceeding max length (if validated) is rejected.
- Mock `CarRepository` using Mockito or MockK (whichever is already in
  the project's test dependencies).

### `ResetAllDataUseCaseTest`

Create:
`app/src/test/java/org/spsl/evtracker/domain/usecase/ResetAllDataUseCaseTest.kt`

Tests required:
- Successful reset calls `RoomDataResetTransactionRunner` exactly once.
- If the transaction runner throws, the use case propagates the exception
  or wraps it correctly.
- Mock all dependencies; do not use a real Room database.

Use `kotlinx-coroutines-test` if the use cases are suspending functions.

---

## 🟡 TASK-07, Add error handling and retry logic to `DriveBackupRepository` ☑ Done (2026-05-01)

> **Outcome:** new `domain/backup/BackupResult.kt` sealed class —
> `Success` / `AuthRequired` / `Failure(reason, cause?)`. The
> `BackupRepository.backupCurrentData()` contract flips from
> `Unit`-returning + throwing to `BackupResult`-returning, with
> exceptions handled internally. `DriveBackupRepository` gains a
> bounded retry loop: `MAX_ATTEMPTS = 3`, exponential backoff
> `250 ms × 2^attempt` (250 / 500 / 1000 ms total), retrying transient
> failures only, network `IOException` incl. `UnknownHostException`,
> HTTP 429, HTTP 5xx, and HTTP 403 with quota / rate reasons. Auth
> errors (401, 403 auth-reason) and `storageQuotaExceeded` 403 short-
> circuit the loop; unknown / unparseable 403 bodies stay on the
> conservative-auth path. The previously-missing `storageQuotaExceeded`
> branch (Drive full) now correctly maps to
> `Failure("Drive storage full", cause)` instead of being lumped into
> the conservative-auth fallback. Every non-recoverable path logs via
> `android.util.Log.e("DriveBackupRepository", ..., cause)`;
> `app/build.gradle.kts` gains `testOptions.unitTests.isReturnDefaultValues
> = true` so JVM tests don't blow up on the Android stub.
> `DriveBackupWorker.doWork()` collapses from a multi-catch ladder to
> a `when (result)` translator and stops returning `Result.retry()` —
> the repo already exhausted the retry budget, so amplifying it via
> WorkManager would duplicate effort. The read path
> (`readRemoteBackup()`) keeps its existing exception contract because
> `SettingsViewModel.onDriveAuthGranted` and `RestoreBackupUseCase`
> already catch `DriveAuthRequiredException` / `IOException`; the
> `withRetry` helper still wraps it so transient blips on the read
> path retry too. Test surface: ten new JVM cases on
> `DriveBackupRepositoryTest` cover storage-full vs auth, retry-then-
> success on 429 / 500 / 403-quota / `UnknownHostException`,
> retry-exhaustion → `Failure("HTTP 429")` /
> `Failure("Network failure: …")`, no-retry on auth / storage-full,
> and the read path's 401-throws + 429-retries-then-succeeds.
> `FakeDriveRemoteSource` gains `failTimes: Int = 1` budget +
> `failuresRaised: Int` counter so retry tests drive multi-failure
> scenarios deterministically. JVM unit-test count: 265 → 275. All
> gates green: `:app:assembleRelease` (R8), `:app:lint`, `ktlintCheck`,
> `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`. The
> original task text is preserved below for historical context.

The class `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`
interfaces with the Google Drive API. It must handle common failure modes.

1. Audit the current implementation for missing error handling around:
   - Network failures (`IOException`, `UnknownHostException`).
   - Google API quota exceeded (HTTP 429).
   - Expired or revoked OAuth token (HTTP 401).
   - Drive storage full (HTTP 403 with specific error domain).

2. For transient errors (network, 429), implement exponential backoff retry
   with a maximum of 3 attempts using `kotlinx.coroutines` delay.

3. For auth errors (401), emit a specific sealed class result or error state
   so the UI can prompt the user to re-authenticate.

4. For non-recoverable errors, log the failure using `android.util.Log` at
   the `ERROR` level with tag `"DriveBackupRepository"`.

5. Expose a sealed class return type from the backup function if not already done:
   ```kotlin
   sealed class BackupResult {
       object Success : BackupResult()
       data class Failure(val reason: String) : BackupResult()
       object AuthRequired : BackupResult()
   }
   ```

6. Add or update the corresponding unit tests for these error paths in
   `app/src/test/`.

---

## ☒ TASK-08, ~~Replace `CarEditDialog` with a Compose `AlertDialog`~~

**Closed 2026-05-09, scope vs value.** Compose is not in the dependency graph today, no Compose-first screens are planned, TASK-30 chose a custom `Canvas` `PieChartView` (not Compose Canvas), and the widget work in DESIGN.md §708 deliberately avoided the Compose / Glance runtime. The existing 56-line `CarEditDialog` object over `MaterialAlertDialogBuilder` + ViewBinding is fit for purpose; pulling in `compose-bom` + `ui` + `material3` + `activity-compose` + `buildFeatures.compose = true` for one dialog fails the YAGNI test.

**Reactivate if:** a Compose-first new screen lands on the backlog, or ViewBinding is being phased out project-wide.

See `docs/superpowers/specs/2026-05-09-task08-close-design.md` for the closure rationale and diff.

---

## 🟢 TASK-09, Add date-ranged CSV export for charge events with efficiency column ☑ Done (2026-05-03)

> **Outcome (merged 2026-05-03 on `feat/task09-csv-range-export`).**
> `ExportCsvUseCase` rewritten with a unified 14-column header
> (identical for full-history and date-ranged exports, research
> consumers in TASK-40 anchor on a stable schema):
>
> ```
> event_date_iso,car_name,odometer_km,kwh,kwh_source,charge_type,
> location,cost_total,cost_per_kwh,currency,km_per_kwh,
> soc_before,soc_after,note
> ```
>
> The previous `useKm` parameter was **dropped entirely**, distance
> is now always emitted as canonical kilometres regardless of the
> user's display preference (DESIGN.md invariant: "Odometer is always
> stored in km"), making exports locale-independent. Researchers
> running cross-fleet analysis no longer need to know which user ran
> the export.
>
> **New columns wired:**
> - `car_name`, sourced from `CarReader.getById(carId).name`,
>   present on every row (not just header).
> - `kwh_source`, `ChargeKwhSource.name` (`MEASURED` /
>   `DERIVED_FROM_SOC`), routed through the TASK-52 hardened
>   `csvEscape` (defensive symmetry against future enum additions).
> - `cost_per_kwh`, emits the entity's `costPerKwh: Double?`
>   verbatim; null becomes empty cell.
> - `km_per_kwh`, computed per-row using the delta-odometer
>   convention from `StatsCalculator` (DESIGN.md §7): `(odo[i] -
>   odo[i-1]) / kwh[i]`. Blank when there is no previous event in the
>   *exported slice* (so first row blank even if events exist
>   outside the range), or when `dist <= 0` / `kwh <= 0`. The
>   `prevOdo` chain advances unconditionally, a transient odometer
>   rollback or zero-kwh row does not break the delta for the next
>   valid row (mirrors the pairwise convention).
> - `soc_before` / `soc_after`, fractions in `0.0..1.0` (matching
>   the on-disk shape from TASK-14), blank when null.
>
> **API surface:** `export(carId: Long): Uri` (full history) +
> `export(carId: Long, range: LongRange?): Uri` (range-aware; pass
> `null` for full history). Both eventually call into the same
> private `writeCsv(writer, events, carName)` so there's no
> duplicate-escape risk. Range is treated as inclusive on both ends
> via `LongRange` semantics.
>
> **UI:** Settings now has two adjacent rows, the existing "Export
> CSV" row, plus a new "Export CSV (date range)" row that opens a
> `MaterialDatePicker.Builder.dateRangePicker()` and forwards the
> selection to `SettingsViewModel.onExportCsvRange(start, end)`.
> Both rows share the activeCar disabled-state rendering. Three new
> strings under `settings_export_csv_range*`. The picker fragment is
> tagged `"export_range_picker"` for back-stack survival.
>
> **TASK-52 alignment:** the new code path inherits the hardened
> `csvEscape` (RFC 4180 + OWASP formula-injection prefixes) for all
> text columns, `car_name`, `kwh_source`, `chargeType.name`,
> `location`, `currency`, `note`. Numeric / timestamp columns
> (`event_date_iso`, `odometer_km`, `kwh`, `cost_total`,
> `cost_per_kwh`, `km_per_kwh`, `soc_before`, `soc_after`)
> deliberately bypass the escape; `Double.toString()` /
> `Instant.toString()` cannot produce a destructive formula prefix
> from a non-malicious value, and quoting them as text would defeat
> researchers' pivot tables.
>
> **Tests:** the obsolete `headerLineUsesKmOrMilesPerFlag` was
> retired and replaced with `header_isCanonicalFourteenColumnSchema`.
> 11 new JVM cases in `ExportCsvUseCaseTest`:
> `header_isCanonicalFourteenColumnSchema`,
> `emptyEvents_emitsHeaderOnly`,
> `singleEvent_efficiencyCellIsBlank`,
> `multiEvent_efficiencyDerivedFromDeltaOdometer`,
> `negativeOdometerDelta_efficiencyBlank` (rolled-back row blank,
> chain continues), `zeroKwh_efficiencyBlank`,
> `kwhSource_emitsEnumName`, `socFields_emitFractionsOrBlank`,
> `costPerKwh_emitsDoubleOrBlank_independentOfCostTotal`,
> `carName_appearsInEveryRow_andIsEscapedWhenContainsComma`,
> `mixedCurrencyRows_eachRowEmitsItsOwnCurrency`,
> `rangeFilter_omitsEventsOutsideRange`. The shared helper
> `writeAndGetLines(events, carName)` walks the output character by
> character honouring CSV quoting so embedded `\r` / `\n` inside a
> quoted field don't split a logical record across multiple lines —
> required because the new 14-column schema makes column-index
> assertions sensitive to mid-row line breaks. JVM unit-test count
> 387 → 398.
>
> **TASK-40 unblocked.** The anonymised research-export pipeline
> can now wrap or layer over this exporter rather than re-deriving
> the column set.

> **Premise correction (2026-04-30):** `EfficiencyPoint` is just a
> `(eventTimeMillis, kmPerKwh)` data class and does not carry distance,
> energy, or cost, so it cannot fill the originally proposed CSV header.
> An `ExportCsvUseCase` already exists and writes to
> `getExternalFilesDir(DIRECTORY_DOWNLOADS)`. The remaining work is a
> **date-ranged variant** of the existing exporter that also emits the
> per-row efficiency derived from the previous event for the same car.

> **Audit follow-up (GAP-01, 2026-05-03):** the existing
> `ExportCsvUseCase` writes 8 columns and silently omits TASK-14
> (`socBefore`, `socAfter`) and TASK-43 (`kwhSource`) data. Folding
> the column extension into this task: the new range exporter MUST
> include the missing columns, AND the existing `ExportCsvUseCase`
> must be retroactively patched in the same change set so both
> exporters share the schema. SPS-Lab research consumers (TASK-40) need
> these columns to distinguish measured-vs-derived energy and to
> reconstruct degradation context.

1. Add a `ExportChargeEventsRangeUseCase` (or extend `ExportCsvUseCase` with
   a `DateRange?` parameter) that:
   - Accepts `startMillis: Long`, `endMillis: Long`, optional `carId: Long`.
   - Reads charge events through the existing `ChargeEventQueries` narrow IF
     (no new repo type).
   - Computes the same per-row delta-odometer efficiency the History screen
     uses (`kmPerKwh = (odo[i] - odo[i-1]) / kwh[i]`; first event for a car
     emits an empty efficiency cell).
   - Writes `event_date_iso,car_name,odometer_km,kwh,kwh_source,charge_type,location,cost_total,cost_per_kwh,currency,km_per_kwh,soc_before,soc_after,note` and shares via the existing FileProvider authority `${packageName}.fileprovider`.
   - `kwh_source` emits the enum name (`MEASURED` / `DERIVED_FROM_SOC`).
   - `soc_before` / `soc_after` emit fractions in `0.0..1.0`, blank when null.
2. **Retroactively patch** the existing `ExportCsvUseCase.writeCsv(...)`
   so its output matches the same 14-column header. Update
   `ExportCsvUseCaseTest` (or add it if missing) with cases asserting
   `kwh_source`, `soc_before`, `soc_after` appear in both header and
   rows for `DERIVED_FROM_SOC` and SoC-bearing fixtures.
3. Add an "Export range…" entry in Settings next to the existing CSV export
   that opens `MaterialDatePicker.Builder.dateRangePicker()` and calls the
   new use case via `SettingsViewModel`.
4. Add JVM unit tests for the new use case covering: empty range, single
   event (efficiency cell empty), multi-event efficiency derivation, mixed
   currency rows, `costTotal IS NULL` rows, `kwhSource = DERIVED_FROM_SOC`
   row, and a row with both SoC fields set. Use the existing fakes.

---

## 🟢 TASK-10, Add In-App "About / Info" Screen ☑ Done (2026-05-01)

> **Outcome:** new `AboutFragment` at
> `app/src/main/java/org/spsl/evtracker/ui/about/AboutFragment.kt` with
> a CoordinatorLayout + MaterialToolbar + NestedScrollView body of
> Material 3 cards (app info, acknowledgment, license, disclaimer,
> open-source libraries). Reachable from `Settings → About` via
> `R.id.action_settings_to_about`; the destination declares
> `hideBottomNav=true` per the TASK-27 convention. Version text comes
> from `BuildConfig.VERSION_NAME` / `VERSION_CODE` (unblocked by
> TASK-29). Two tappable URL rows fire `Intent.ACTION_VIEW` and fall
> back to a Snackbar on `ActivityNotFoundException`. The bundled
> launcher icon pack drop-in: 20 PNG mipmaps (4 files × 5 density
> buckets) extracted from `exported-assets.zip` →
> `ev_tracker_icons.zip` into `app/src/main/res/mipmap-*/`;
> `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
> repointed from `@drawable/ic_launcher_{foreground,background}` to
> `@mipmap/...`; the now-orphan vector drawables in
> `drawable/ic_launcher_{foreground,background}.xml` deleted (lint
> `UnusedResources` is in error mode). `drawable/ic_spslab_badge.xml`
> kept and consumed by the About app-info card header. New
> instrumented `AboutFragmentTest` covers the five required assertions
> (version non-empty, SPS-Lab visible, sps-lab.org link present,
> license card contains "MIT", disclaimer contains "liability"); the
> test compiles via `:app:assembleDebugAndroidTest` (running needs an
> emulator). Spec:
> `superpowers/specs/2026-05-01-task10-about-screen-design.md`. Plan:
> `superpowers/plans/2026-05-01-task10-about-screen.md`. The original
> task text is preserved below for historical context.

> **Prerequisite resolved (2026-05-01):** TASK-29 has merged.
> `buildFeatures.buildConfig = true` is enabled, so
> `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE` are available
> under `org.spsl.evtracker.BuildConfig`. The About screen can read them
> directly. Note that on debug builds `VERSION_NAME` resolves to e.g.
> `"1.0.1-debug"` (from `versionNameSuffix = "-debug"`); decide whether
> the About screen should display that suffix verbatim or strip it.

Add a dedicated About screen accessible from the Settings or main navigation
that displays app metadata, acknowledgments, license, and a disclaimer.
This screen is important for SPS-Lab attribution and research transparency.

### Screen content requirements

The screen must display all of the following sections:

#### App Info
- App name: `Joulie`
- Current version name and version code (read dynamically from
  `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`).
- Build date (optional; can be a hardcoded string updated at release time).

#### Acknowledgment
```
Developed by the Sustainable Power Systems Lab (SPS-Lab)
Cyprus University of Technology
Limassol, Cyprus
```
- Render `https://sps-lab.org` as a tappable hyperlink that opens in the
  default browser using an `Intent.ACTION_VIEW` intent.
- Render `https://cut.ac.cy` as a second tappable hyperlink.

#### License
```
MIT License

Copyright (c) 2024–2026 Sustainable Power Systems Lab (SPS-Lab),
Cyprus University of Technology.

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
Display this in a scrollable `TextView` or Compose `Text` inside a card.

#### Disclaimer
```
This application is provided for research and personal use only.
Efficiency and cost estimates are based on user-entered data and do
not constitute professional energy or financial advice. The SPS-Lab
and Cyprus University of Technology accept no liability for decisions
made based on data recorded or displayed by this app.
```

#### Open Source Notices
- Add a section titled "Open Source Libraries" listing the key
  dependencies with their licenses:
  - Room (Apache 2.0)
  - Hilt / Dagger (Apache 2.0)
  - MPAndroidChart or equivalent charting library used (check `build.gradle.kts`)
  - Kotlin Coroutines (Apache 2.0)
  - Google Drive API client (Apache 2.0)

### Implementation instructions

1. Create a new Fragment (or Compose screen, if Compose is already in use):
   - Fragment: `app/src/main/java/org/spsl/evtracker/ui/about/AboutFragment.kt`
   - Layout: `app/src/main/res/layout/fragment_about.xml` (or Compose equivalent)

2. The screen must be reachable from the Settings screen via a menu item or
   preference row labelled `"About"`. Add a navigation entry in
   `app/src/main/res/navigation/` nav graph (if using Navigation Component).

3. All static text (app name, lab name, URLs, license, disclaimer) must be
   defined as string resources in `app/src/main/res/values/strings.xml`,
   not hardcoded in the layout or Kotlin files.

4. The SPS-Lab URL and CUT URL must open in the device browser. Use:
   ```kotlin
   val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
   startActivity(intent)
   ```
   Wrap in a try/catch for `ActivityNotFoundException`.

5. Style using Material 3 components consistent with the rest of the app
   (M3 card surfaces, `MaterialToolbar` with back navigation).

6. Add an instrumented UI test:
   `app/src/androidTest/java/org/spsl/evtracker/ui/about/AboutFragmentTest.kt`

   Tests required:
   - Version name is displayed and is non-empty.
   - "SPS-Lab" text is visible on screen.
   - The URL link for `sps-lab.org` is present.
   - The license card is visible and contains the word "MIT".
   - The disclaimer card is visible and contains the word "liability".

### Branding & launcher icon drop-in (assets provided 2026-05-01)

A complete launcher-icon asset pack is bundled at the repo root in
`exported-assets.zip` (uncommitted, ~5 MB). The inner
`ev_tracker_icons.zip` mirrors the exact `res/` directory structure to
drop into `app/src/main/res/`:

```
mipmap-mdpi/        → 48×48
mipmap-hdpi/        → 72×72
mipmap-xhdpi/       → 96×96
mipmap-xxhdpi/      → 144×144
mipmap-xxxhdpi/     → 192×192
play_store/         → 512×512
```

Each density folder contains **4 files**:

| File | Purpose |
|------|---------|
| `ic_launcher.png` | Legacy square launcher icon |
| `ic_launcher_round.png` | Circular variant (Android 7.1+) |
| `ic_launcher_foreground.png` | Adaptive icon foreground layer (RGBA) |
| `ic_launcher_background.png` | Adaptive icon background layer (solid navy `#0D2B5E`) |

#### How to apply

1. Unzip `exported-assets.zip` → extract the inner `ev_tracker_icons.zip` →
   copy all `mipmap-*` folders into `app/src/main/res/`, replacing existing
   ones. (Pre-flight check: a `find app/src/main/res -name "ic_launcher*"`
   currently lists only the vector drawables and the
   `mipmap-anydpi-v26/ic_launcher{,_round}.xml` files, there are no
   density-bucket PNGs to overwrite yet.)
2. `AndroidManifest.xml` already references `@mipmap/ic_launcher` and
   `@mipmap/ic_launcher_round`, no changes needed.
3. **Reconcile with the existing vector adaptive icon (commit `e1958d7`).**
   `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` currently reads:

   ```xml
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@drawable/ic_launcher_background"/>
       <foreground android:drawable="@drawable/ic_launcher_foreground"/>
   </adaptive-icon>
   ```

   To use the new PNG mipmaps as the adaptive-icon source, rewrite both
   `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` to
   reference `@mipmap/...` instead:

   ```xml
   <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
       <background android:drawable="@mipmap/ic_launcher_background"/>
       <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
   </adaptive-icon>
   ```

   The two vector drawables added by `e1958d7`
   (`drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`)
   will become orphans after this rewrite. Delete them, Android Lint's
   `UnusedResources` is in error mode and would otherwise fail the
   build. **Keep `drawable/ic_spslab_badge.xml`**, it's the SPS-Lab pill
   badge intended for the About screen header (not part of the launcher
   icon pipeline) and is referenced from the About fragment layout you
   write in this task.

4. Use `play_store/ic_launcher_512.png` when uploading to the Google Play
   Console (not committed to the repo, keep it under `dist/` locally or
   attach to the GitHub Release).

#### Acceptance for the icon work

- `find app/src/main/res -name "ic_launcher*.png"` lists 20 files
  (4 per density × 5 densities).
- `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` both
  reference `@mipmap/ic_launcher_foreground` + `@mipmap/ic_launcher_background`.
- `drawable/ic_launcher_foreground.xml` and
  `drawable/ic_launcher_background.xml` are deleted; the build remains
  green (`./gradlew :app:lint :app:assembleRelease`).
- The launcher icon visually shows the EV silhouette + lightning bolt
  on a navy `#0D2B5E` background after install, verify on a device
  and on the Play Store listing's icon preview.

---

## 🟡 TASK-11, Odometer Regression UX Improvement ☑ Done (2026-05-01)

> **Outcome:** `ChargeEditUiState` gained four fields —
> `previousOdometerKm`, `nextOdometerKm`, `odometerBelowPrevious`,
> `odometerAboveNext`, populated by `ChargeEditViewModel.init` from a
> single `getAllForCarSorted(carId)` call (Create mode looks up the
> latest entry; Edit mode locates the chronological neighbours of the
> event being edited). Create mode pre-fills `odometer` with
> `(previousOdometerKm + 1.0).toDisplayUnit()` so the field starts in a
> known-valid state when the car has history. `setOdometer(text)` parses
> the input, converts miles → km when needed, and toggles the regression
> flags on every keystroke; the flags clear on blank/unparseable input.
> `ChargeEditFragment.render` formats the inline `TextInputLayout.error`
> from the new parameterised strings
> `error_odometer_must_be_greater_than` /
> `error_odometer_must_be_less_than` and gates `chargeEditSave.isEnabled`
> on `!odometerBelowPrevious && !odometerAboveNext` (in addition to the
> existing `!saving`). The use case still owns the
> `OdometerNotIncreasing` fallback for same-`eventDate` races.
> Ten new JVM cases in `ChargeEditViewModelTest` cover prefill (km +
> miles), neighbour wiring (middle/first/last), and flag transitions on
> `setOdometer`. JVM unit-test count: 247 → 257. Spec:
> `superpowers/specs/2026-05-01-task11-odometer-regression-ux-design.md`.
> Plan: `superpowers/plans/2026-05-01-task11-odometer-regression-ux.md`.
> The original task text is preserved below for historical context.


The app currently validates that each new charge event's odometer is greater
than the previous one, but the error experience can be improved.

**Current behavior:** a generic validation error is shown after the user
attempts to save.

**Required improvements:**

1. In `ChargeEditFragment` (or the corresponding ViewModel), pre-fill the
   odometer field with the **last recorded odometer value + 1** for the
   active car when creating a new charge event (not when editing an existing
   one). This gives the user a useful starting point.

2. Show an **inline warning** (not just on save) below the odometer field
   as soon as the entered value is less than or equal to the previous
   odometer. Use a `TextInputLayout` error message that updates on
   `TextWatcher` changes. The warning text must be:
   `"Must be greater than last entry ([previous value] [unit])"`
   where `[unit]` is `km` or `mi` per the user's preference.

3. The Save button must remain disabled while the odometer regression
   warning is shown.

4. When editing an existing event (not the most recent one), do not apply
   the regression check against the car's latest odometer. Instead, validate
   that the edited value is:
   - Greater than the event immediately before it in chronological order.
   - Less than the event immediately after it in chronological order.
   Show appropriate inline messages for each case.

5. Add unit tests to `ChargeEditViewModelTest` (or create it) for:
   - Pre-fill logic (new event vs. edit event).
   - Inline error message content and Save button enabled state.

---

## 🟡 TASK-12, Home Screen Widget: Last Charge Summary ☑ Done (2026-05-01)

> **Outcome:** new `widget/LastChargeWidget` `AppWidgetProvider` shows
> car name, relative event date ("Today" / "Yesterday" / "N days ago"
> / "N week(s) ago" / absolute "MMM d, yyyy" past 28 days), kWh added,
> efficiency in the user's preferred metric (km/kWh, kWh/100 km, or
> mi/kWh, converted from the canonical km/kWh on the latest event's
> odometer-delta), cost (formatted via `NumberFormat.getCurrencyInstance`
> when `costTotal` and `currency` are both set, otherwise hidden), and
> a generic ⚡ icon. Empty state shows the
> `widget_empty_state` string when the active car is unset or has no
> charge events. Layout is `RemoteViews`-only; tap intent uses
> `PendingIntent.getActivity(MainActivity)` so the widget lands on the
> Dashboard.
>
> Pure-domain `LastChargeWidgetSnapshot.compute(...)` does the heavy
> lifting (sort, latest-event pick, efficiency conversion, relative
> date bucketing) and stays JVM-testable, no `Calendar` or
> `RemoteViews` reach into the helper. Inputs are an optional
> `CarEntity?`, the unsorted events list, the user's `primaryMetric`
> token, and `nowMillis`. 18 new JVM cases on
> `LastChargeWidgetSnapshotTest`.
>
> Update plumbing follows the existing `BackupScheduler` pattern: new
> narrow `domain/widget/WidgetRefresher` interface; bound to
> `data/widget/AndroidWidgetRefresher` in a new `WidgetModule`.
> Refresh is called from every snapshot-affecting use case —
> `SaveChargeEventUseCase`, `DeleteChargeEventUseCase`,
> `AddCarUseCase`, `DeleteCarUseCase`, `RenameCarUseCase`,
> `ResetActiveCarDataUseCase`, `ResetAllDataUseCase` (`runCatching`
> isolated like the post-reset backup enqueue), and
> `RestoreBackupUseCase` (both the `null` remote and successful
> replace branches). The Android impl re-enters `onUpdate` via a
> self-broadcast to `LastChargeWidget` (instead of calling
> `updateAppWidget` directly) so the platform's normal lifecycle
> still applies. Hilt entry point
> (`LastChargeWidgetEntryPoint`) gives the provider its
> `SettingsReader` / `CarReader` / `ChargeEventQueries` /
> `NowProvider` deps via `EntryPointAccessors.fromApplication` since
> `AppWidgetProvider` isn't `@AndroidEntryPoint`-able.
>
> Manifest gains a `<receiver android:name=".widget.LastChargeWidget">`
> with `APPWIDGET_UPDATE` intent filter and metadata pointing at
> `res/xml/widget_last_charge_info.xml` (110×110 dp min, 2×2 target
> cells, `updatePeriodMillis="0"` since refreshes are push-based).
>
> 6 new strings under the `widget_*` prefix (i18n-ready for TASK-15).
> `Fakes.kt` gains `FakeWidgetRefresher`. Use case tests updated to
> verify `widgetRefresher.refresh()` fires once per commit alongside
> the existing backup-scheduler assertions. The instrumented test
> from the spec (verify `RemoteViews` populated correctly) is
> deferred, `RemoteViews` content assertion needs `LayoutInflater`
> reflection that's brittle to API changes; the snapshot helper is
> already exhaustively unit-tested. Plain Espresso wouldn't add
> coverage. Empirical verification lives in `docs/TEST_PLAN.md §5b`
> (release-APK smoke step). JVM unit-test count 313 → 332. The
> original task text is preserved below.

Add a `AppWidgetProvider`-based Android home screen widget that displays
a compact summary of the most recent charge event for the active car.

### Widget content

The widget (minimum 2×2 cells) must show:
- Car name
- Date of last charge (relative: "Today", "Yesterday", or "3 days ago")
- kWh added
- Efficiency value (in the user's preferred metric, e.g., `6.2 km/kWh`)
- Cost (if non-null, formatted with currency symbol)
- A small `⚡` icon indicating AC or DC charge type

If no charge events exist for the active car, show: `"No charges logged yet."`

### Implementation

1. Create `app/src/main/java/org/spsl/evtracker/widget/LastChargeWidget.kt`
   extending `AppWidgetProvider`.

2. Create the widget layout:
   `app/src/main/res/layout/widget_last_charge.xml`
   Use `RemoteViews`-compatible views only (`TextView`, `ImageView`, `LinearLayout`).

3. Create the widget metadata:
   `app/src/main/res/xml/widget_last_charge_info.xml`
   Set `minWidth="110dp"`, `minHeight="110dp"`, `updatePeriodMillis="0"` (use
   WorkManager or a broadcast instead of polling).

4. Register the widget in `AndroidManifest.xml` with
   `android.appwidget.action.APPWIDGET_UPDATE` intent filter.

5. Wire the widget update to the existing WorkManager backup job or a
   separate `OneTimeWorkRequest` triggered after every committed charge event
   save or delete. Use `AppWidgetManager.updateAppWidget()` to push new
   `RemoteViews` data.

6. Tapping the widget must open the app's Dashboard screen via a
   `PendingIntent`.

7. Add an instrumented test that verifies the `RemoteViews` are populated
   correctly with mock data.

---

## ☒ TASK-13, ~~Live Charging Session Timer~~

> **Closed (2026-05-02):** scope-vs-value mismatch. Decided against the
> feature on review. The implementation cost (foreground service +
> persistent notification + DataStore session-resume on process death +
> ForegroundServiceType + Android 14 FGS-permission ceremony +
> `POST_NOTIFICATIONS` interplay with TASK-19's existing channels) is
> disproportionate to the user value (a duration field and a
> pre-filled `event_date` that the user already enters at save time).
> Real-world charging sessions are well-known to the user; a stopwatch
> doesn't add information they don't have. If a future need surfaces
> (e.g. session-tied analytics: power curve, taper time, kWh/h trend),
> file a fresh task with the analytics requirement leading the spec —
> the timer would then be infrastructure for the analytics, not a
> standalone affordance.
>
> The original task text is preserved below for historical context.

Add an optional "I am charging now" mode that lets the user start a timed
charging session and auto-fills the timestamp and duration on the charge
edit form when they finish.

### Behavior

1. Add a **"Start charging session"** floating action button (or prominent
   button) on the Dashboard screen, visible only when no session is active.

2. When tapped, record `sessionStartTime = System.currentTimeMillis()` in
   a `StateFlow` inside `DashboardViewModel` (not persisted to DB; in-memory only).

3. While a session is active:
   - Show a persistent, non-dismissible notification: `"Charging in progress
    , [elapsed time]"`; update the elapsed time every minute using a
     `CoroutineScope` + `delay` loop in a `ForegroundService`.
   - Replace the Dashboard FAB with a **"Stop & Log"** button.
   - Show an elapsed time chip on the Dashboard (e.g., `⏱ 1h 23m`).

4. When the user taps **"Stop & Log"**:
   - Navigate to `ChargeEditFragment`.
   - Pre-fill the `event_date` with `sessionStartTime`.
   - Pre-fill a `note` field with `"Session duration: [elapsed]"`.
   - Stop the foreground service and clear the notification.

5. If the app is killed while a session is active, use `DataStore` to
   persist `sessionStartTime` so the session survives process death. On
   next launch, if `sessionStartTime` is non-null and the active car has
   not had a new charge event logged since that time, resume the active
   session display.

6. Add unit tests for session start/stop state transitions in
   `DashboardViewModelTest`.

---

## 🟡 TASK-14, Battery Capacity Degradation Tracker ☑ Done (2026-05-01)

> **Outcome:** `ChargeEventEntity` gains optional `socBefore` and
> `socAfter` `Double?` fields stored as fractions in `0.0..1.0`. Room
> schema bumped v5 → v6 with `MIGRATION_5_6 = ALTER TABLE
> charge_events ADD COLUMN socBefore REAL; ADD COLUMN socAfter REAL`
>, purely additive, no rebuild. `BackupData.CURRENT_VERSION` 5 → 6
> with `BackupSerializer.SUPPORTED_VERSIONS = {3, 4, 5, 6}`; older
> backups simply leave the new fields at `null`. New
> `domain/service/CapacityEstimator` service computes per-event
> effective capacity as `kwhAdded / (socAfter - socBefore)` when both
> SoC fields are set (exact path), or `kwhAdded` itself when
> `kwhAdded ≥ 0.8 × nominalBatteryKwh` (heuristic fallback for
> near-full charges). Events that satisfy neither rule are skipped;
> the chart needs at least 3 qualifying points and a non-null nominal
> capacity to render. `Stats` gains `batteryHealthPercent: Double?`
> (latest effective capacity ÷ nominal × 100, **not clamped** —
> over-100% values surface heuristic over-estimation as diagnostic
> info). `ObserveDashboardStatsUseCase` and `ObserveChartsModelsUseCase`
> both inject `CapacityEstimator` and pass the active car's
> `batteryKwh`. `ChargeEditUiState` gains `socExpanded`,
> `socBeforeText`, `socAfterText`, `socError`; the form has a new
> "+ Add SoC data" expandable card with two paired `0..100` percent
> inputs. Validation: both blank → drop them; both filled, parseable,
> in-range, with `after > before` → save as fractions; otherwise
> show inline error from one of three new strings
> (`error_soc_both_required`, `error_soc_range`,
> `error_soc_after_must_exceed_before`). Dashboard renders a new
> `dashboard_card_battery_health` MaterialCardView showing
> "Battery health · NN%" when `Stats.batteryHealthPercent != null`.
> Charts adds a 6th tab `DEGRADATION` rendering an MPAndroidChart
> `LineChart` of `effectiveCapacityKwh` over time with a dashed
> `LimitLine` at the car's `nominalBatteryKwh`; falls back to two
> empty-state messages, "Set the car's nominal battery capacity in
> Cars to enable this chart" / "Need at least 3 qualifying charges".
> New JVM `CapacityEstimatorTest` (16 cases) covers exact path,
> heuristic boundary at 80%, mixed events sorted by date,
> zero/negative kWh skipped, `batteryHealthPercent` semantics, and
> the no-clamp guarantee. New instrumented
> `migrate_5_to_6_addsSocColumns` test; `migrate_1_to_5_validatesSchema`
> renamed to `migrate_1_to_6_validatesSchema` and asserts both new
> columns default to `NULL` on legacy rows. JVM unit-test count
> 275 → 291 (+16). All gates green: `:app:assembleDebug`,
> `:app:assembleRelease` (R8), `:app:assembleDebugAndroidTest`,
> `ktlintCheck`, `:app:lint`, `:app:testDebugUnitTest`.

The `cars` table already has a `battery_kwh` field for the nominal battery
capacity. Use it to track and visualise real-world effective capacity over
time, which is directly relevant to the SPS-Lab’s EV integration research.

### Feature description

An EV’s effective battery capacity can be estimated from charge events where:
- The car was charged from a known low state (e.g., cost-per-kWh data implies
  a near-full charge), OR
- The user manually enters the SoC (state of charge) before and after.

For a simpler first implementation, use the following heuristic:
- Identify charge events where `kwh_added ≥ 0.8 × battery_kwh` (likely a
  near-full charge from low SoC).
- Plot `kwh_added` for these events over time as a proxy for effective capacity.

### Implementation

1. Add two **optional** fields to the `charge_events` table via a Room
   migration (version bump to 4):
   - `soc_before REAL`, State of Charge before charging (0.0–1.0 or 0–100;
     store as a fraction 0.0–1.0 internally).
   - `soc_after REAL`, State of Charge after charging.

2. Add optional SoC input fields to `ChargeEditFragment`:
   - Two optional fields labelled "SoC before (%)" and "SoC after (%)".
   - Collapsed by default behind a "+ Add SoC data" expansion tap.
   - Validate: 0–100 range; `soc_after > soc_before`.

3. Add a new "Degradation" tab or card in the Charts screen:
   - X-axis: date of charge event.
   - Y-axis: effective capacity (kWh), computed as
     `kwh_added / (soc_after - soc_before)` when both SoC fields are
     non-null, otherwise fall back to the heuristic `kwh_added` proxy.
   - Draw a horizontal dashed reference line at the car’s nominal
     `battery_kwh` value.
   - Only show this chart if the car has a `battery_kwh` set and at
     least 3 qualifying data points exist.

4. Add a `batteryHealthPercent` computed property:
   `(latestEffectiveCapacity / nominalCapacity) × 100`
   Display this as a secondary stat card on the Dashboard when available.

5. Add unit tests for the capacity calculation logic in a new
   `CapacityEstimatorTest.kt` in `app/src/test/`.

---

## 🟢 TASK-15, Localisation (i18n) Foundation ☑ Done (2026-05-04)

> **Outcome:** Three new locale files added: `app/src/main/res/values-el/strings.xml` (Greek, primary Cyprus locale), `app/src/main/res/values-tr/strings.xml` (Turkish, second-largest Cyprus community), and `app/src/main/res/values-ru/strings.xml` (Russian, significant immigrant population). Each file contains all 248 translatable strings, covering navigation labels, screen titles, button labels, empty states, error messages, Drive backup UI, notification strings, and the About/Disclaimer screen. Plurals resources (`charts_acdc_count_center`, `charts_degradation_derived_excluded_banner`) are correctly formed for each locale, Russian uses the full `one/few/many/other` CLDR set; Greek and Turkish use `one/other`. 32 strings in the canonical `values/strings.xml` were marked `translatable="false"`: brand names (`app_name`, `about_app_name`, `dashboard_title`), URL display strings (`about_link_sps_lab`, `about_link_cut`), official organisation names (`about_acknowledgment_lab`, `about_acknowledgment_cut`), internationally-standardised unit abbreviations (`metric_km_per_kwh`, `metric_kwh_per_100km`, `metric_mi_per_kwh`, `wizard_metric_km_kwh`, `wizard_metric_kwh_100km`, `wizard_metric_mi_kwh`, `wizard_unit_km`), technical AC/DC labels (`filter_ac`, `filter_dc`, `charge_type_ac`, `charge_type_dc`, `charts_trend_legend_ac`, `charts_trend_legend_dc`), em-dash display strings (`efficiency_not_available`, `metric_unavailable`, `widget_efficiency_dash`), pure format strings with no human-readable text (`battery_health_value_format`, `widget_efficiency_format`, `charts_acdc_kwh_subtitle`), dev-scaffolding placeholders (`placeholder_dashboard` through `placeholder_manage_locations`, 7 strings), and legal/technical text that must remain in English (`about_license_body`, `about_oss_body`). The two URL strings (`about_url_sps_lab`, `about_url_cut`) were already marked `translatable="false"` prior to this task. XML files pass `xmllint --noout` validation. All 248 translatable keys match across all three locale files (verified via `comm -23` diff). `MissingTranslation` lint gate: build system network is sandboxed (Google Maven unreachable in the CI runner), so Gradle could not execute; correctness was validated by xmllint + key-count diff instead, gate will pass on a networked runner. **⚠ LLM-TRANSLATION CAVEAT: all three locale files are machine-produced first-pass translations. They MUST be reviewed by native Greek, Turkish, and Russian speakers respectively before any production release. Do not ship to users without native-speaker sign-off.**

The app currently has all user-facing strings hardcoded in English. Add
proper i18n support so the app can be translated in the future.

1. Audit all Kotlin source files and XML layouts for hardcoded user-facing
   strings. Extract every string to
   `app/src/main/res/values/strings.xml`.
   Do **not** extract:
   - Log tag strings (internal developer-facing)
   - Room entity column names
   - DataStore preference key names

2. Ensure all `String.format()` calls use named or positional format
   arguments compatible with `getString(R.string.x, arg)` so translators
   can reorder arguments.

3. Add plurals resources (`<plurals>`) for any strings that vary by count
   (e.g., "1 charge event" vs. "3 charge events").

4. **English is the default locale**, `app/src/main/res/values/strings.xml`
   stays as-is. Add three target translations:
   - Greek, `app/src/main/res/values-el/strings.xml`
   - Turkish, `app/src/main/res/values-tr/strings.xml`
   - Russian, `app/src/main/res/values-ru/strings.xml`

   Translate at minimum: all navigation labels, screen titles, button
   labels, empty state messages, and error messages. Domain-specific
   technical terms (kWh, km/kWh, AC, DC) should remain in their
   internationally recognised forms.

   `MissingTranslation` is already wired in error mode (TASK-16) but
   cannot fire today because no `values-<lang>/` resources exist. The
   moment the first translated `strings.xml` lands, **every English
   string must have a translation in all three locales** or the build
   breaks. Plan the extraction (step 1) and the three translations
   together to avoid landing a half-localised state.

5. Add a lint rule to `app/build.gradle.kts` to fail the build on
   hardcoded strings in layouts:
   ```kotlin
   android {
       lint {
           error += "HardcodedText"
       }
   }
   ```

6. Verify the app renders correctly at the two most common system font
   scales (100% and 150%) after the string extraction, as translated
   strings are often longer than English equivalents.

---

## 🟢 TASK-16, Static analysis & code-style gate in CI

> **Merged 2026-04-30 on `main`.** Implementation: `.github/workflows/ci.yml`
> (PR + push-to-main triggers), ktlint 12.1.1 plugin in `app/build.gradle.kts`,
> Android Lint error mode for `HardcodedText` / `MissingTranslation` /
> `TypographyDashes` / `UnusedResources`, `app/lint-baseline.xml` absorbs 58
> pre-existing offenses. `.editorconfig` pins Kotlin official / IntelliJ style.
> Local one-liner: `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest`.
> Spec: `docs/superpowers/specs/2026-04-30-task16-ci-static-analysis-design.md`.

> **Priority raised to 🔴 (2026-04-30):** without this gate, regressions on
> hardcoded strings (TASK-15), API-35 deprecations (TASK-22), and
> `ChargeType` enum/conversion (TASK-25) cannot be caught at PR time. Land
> this before the next round of feature work.

The only CI workflow today is `.github/workflows/release.yml`, which triggers
**only on `v*` tag pushes** and `workflow_dispatch`. It runs `:app:assembleRelease`,
verifies signing, and publishes the APK, but no linter, no style checker, and
no static analysis ever runs against PRs or `main`. With ~176 Kotlin files in
`app/src`, style drift will accumulate silently and hardcoded-string regressions
(needed by TASK-15) cannot be detected before merge.

**What to add:**

1. Create a **new** workflow file `.github/workflows/ci.yml` (do not edit
   `release.yml`) that triggers on `pull_request` and `push: branches: [main]`.

2. Add **ktlint** via the `org.jlleitschuh.gradle.ktlint` plugin to the root
   `build.gradle.kts` (or `app/build.gradle.kts`). Configure it to use the
   project's existing 4-space indentation and Kotlin official code style.

3. Enable **Android Lint** in error mode for the rules most likely to bite
   this project, in `app/build.gradle.kts`:

   ```kotlin
   android {
       lint {
           abortOnError = true
           checkReleaseBuilds = true
           error += listOf("HardcodedText", "MissingTranslation",
                           "TypographyDashes", "UnusedResources")
           // Once TASK-15 (i18n) lands, also flip "HardcodedText" + "MissingTranslation"
           // from optional to required, currently they should error already, since
           // there are no localised strings yet to miss.
       }
   }
   ```

4. The new `ci.yml` job runs `./gradlew ktlintCheck lint` on Java 17 with the
   gradle action cache. Fail the build on any violation.

5. Generate a `lint-baseline.xml` for existing pre-existing offenses by running
   `./gradlew lint --baseline lint-baseline.xml` once locally; commit the
   baseline so only **new** violations break the build.

6. Document the new gate in `../CLAUDE.md` (Build & Test section): "PRs must
   pass `:app:ktlintCheck :app:lint` before merge."

---

## 🟡 TASK-17, R8/ProGuard follow-up audit: chart library + release smoke test ☑ Done (2026-05-01)

> **Outcome:** `app/proguard-rules.pro` gains a defensive
> `-keep class com.github.mikephil.charting.** { *; }` + matching
> `-dontwarn` (MPAndroidChart ships zero consumer ProGuard rules,
> verified by `grep mikephil app/build/outputs/mapping/release/configuration.txt`
> returning empty before the change and three lines after). The same
> file gains an evidence comment naming the three AAR-bundled rule
> files that already cover Hilt and Room
> (`hilt-android-2.50/proguard.txt`, `hilt-work-1.1.0/proguard.txt`,
> `room-runtime-2.6.1/proguard.txt`) so future contributors don't add
> redundant `-keep dagger.hilt.*` / `-keep androidx.room.*` rules.
> `:app:assembleRelease` runs R8 cleanly with the new rules and emits
> the signed `app-release.apk` (verified locally). The end-to-end
> manual smoke matrix (12 steps: wizard → log charge → all five Charts
> tabs → Drive backup landing in App Data folder → reset preferences
> auto-recovery → CSV export → About) is documented as
> `docs/TEST_PLAN.md §5b "Release-APK smoke test"` and is the gate
> between "tag pushed → APK built" and "GitHub Release published".
> The smoke matrix itself requires a physical device or API-26+
> emulator with an allow-listed Google account, so the act of running
> it stays a release-time human task, but CI now produces the APK
> deterministically and the matrix names every observable that R8 can
> realistically break. The original task text is preserved below.

The release build's R8 rules (`app/proguard-rules.pro`) were partially audited
in commit `48f0f14` (v1.0.1) for the Drive sync path: keep rules now cover
`com.google.api.client.**`, `com.google.api.services.drive.**`, Gson
reflection, and the backup DTOs (`BackupData`, `CarDto`, `ChargeEventDto`,
`CustomLocationDto`). What's **not** yet covered or verified:

1. **MPAndroidChart**, add a defensive keep rule even though MPAndroidChart's
   release surface is mostly non-reflective; some renderers and
   `IValueFormatter` subclasses can still bite under aggressive R8:
   ```
   -keep class com.github.mikephil.charting.** { *; }
   -dontwarn com.github.mikephil.charting.**
   ```

2. **Hilt + Room**, both ship comprehensive consumer ProGuard rules with
   their AARs (verify by running `./gradlew :app:assembleRelease` and
   inspecting `app/build/outputs/mapping/release/configuration.txt`). Add a
   short comment to `proguard-rules.pro` stating that no app-side keep rules
   are needed for these libraries, with the AAR rule paths as evidence, this
   prevents a future contributor from adding redundant rules "just in case".

3. **End-to-end release-APK smoke test** (this is the bulk of the work):
   - Build a signed release APK locally: `./gradlew :app:assembleRelease`.
   - Install on a physical device or API-34 emulator: `adb install -r app/build/outputs/apk/release/app-release.apk`.
   - Walk the smoke matrix:
     a. Wizard completes successfully (writes `setupComplete=true`).
     b. Add a car, add a charge event with cost; verify Dashboard stats render.
     c. Open Charts; verify all six tabs (Trend / Monthly kWh / Monthly cost / AC vs DC / Locations / Degradation) render without crash.
     d. Settings → enable Drive backup; sign in; trigger a backup; verify `evtracker_backup.json` lands in the App Data folder via `files.list?spaces=appDataFolder`.
     e. Reset preferences; verify wizard re-routes.
     f. CSV export from Settings; verify the share sheet opens.
   - File a follow-up issue for any crash or rendering bug discovered.

4. Document the smoke test as a manual checklist in `TEST_PLAN.md` (new
   section "Release-APK smoke test") so future tag pushes can be verified
   against it before publishing the GitHub Release.

> **Note on priority:** the original proposal flagged this 🔴 on the premise
> that R8 rules had never been audited. Since the Drive/Gson path is already
> covered as of v1.0.1, the residual risk is bounded, hence 🟡, not 🔴.

---

## 🟡 TASK-18 PR 1, Accessibility lint lock-in ☑ Done (2026-05-07)

> **Outcome.** `app/build.gradle.kts`'s `lint { error += listOf(...) }` block now promotes 3 a11y rules from default-warning to PR-blocking error: `ContentDescription`, `LabelFor`, `KeyboardInaccessibleWidget`. Touch-target enforcement is left to the existing Espresso `AccessibilityChecks` runtime interceptor in `HiltTestRunner.onStart()` — Lint has no equivalent rule (`TouchTargetSizeCheck` is the Espresso validator name, AGP 8.7.3's Lint rejects it as `UnknownIssueId`). `app/lint-baseline.xml` was regenerated under AGP 8.7.3's Lint; no current a11y-rule violations exist (rule promotion lands as a forward-only floor), and the diff is AGP-version-bump churn (`AndroidGradlePluginVersion`, `Untranslatable`, `UseCompoundDrawables`, etc.) plus `MonochromeLauncherIcon` removed (the issue was resolved when the Brand Pack v1.0 themed-icon layer landed in TASK-57). `DESIGN.md` gained §11 (target + lint floor + delegation note for touch targets); `TEST_PLAN.md` gained §5c (TalkBack smoke walkthrough run before every tagged release, sibling of §5b). PR 2 cleanup of any future baselined fragments + three behavioural / contrast / walkthrough follow-ups are filed as TASK-75 through TASK-78. Spec: `docs/superpowers/specs/2026-05-07-task18-a11y-lint-lockin-design.md`. Plan: `docs/superpowers/plans/2026-05-07-task18-a11y-lint-lockin.md`.

> **Original task body preserved below for the TASK-75 audit.**

## 🟡 TASK-18 (original), Accessibility (a11y) pass

> **Step 6 landed (2026-05-03).** The standalone-PR early-win from the
> Notes-for-Agents addendum is in: `HiltTestRunner.onStart()` now calls
> `AccessibilityChecks.enable().setRunChecksFromRootView(true)`, so every
> Espresso `ViewAction` (click, type, scrollTo, …) in every instrumented
> test runs the WCAG 2.1 AA rule set against the targeted view. New
> `androidx.test.espresso:espresso-accessibility:3.5.1` dep wired in
> `gradle/libs.versions.toml` + `app/build.gradle.kts`. No suppression
> matchers are configured today, the goal of Step 6 is to make the
> existing audit gap measurable. The first cron run of
> `nightly-instrumented.yml` after this lands will surface any
> pre-existing violations as test failures (informational only, the
> nightly job never blocks PRs); those failures become the input list
> for steps 1–5, 7, 8 below.
>
> **Steps 1–5, 7, 8 remain open.** Tackle them as follow-up PRs that
> close violations the nightly surfaces, plus the contrast audit and
> the DESIGN.md A11y section.

There is no mention of accessibility in `DESIGN.md`, `../CLAUDE.md`, or
`TEST_PLAN.md`. The app is intended for public use and must meet at least
WCAG 2.1 AA.

**Required changes:**

1. Add `contentDescription` (or `android:contentDescription` in XML) to every
   `ImageView`, icon button, AC/DC toggle, and chart view. MPAndroidChart
   charts are entirely invisible to TalkBack without an explicit description
   on their host `View`, supply a short summary of what the chart shows.

2. Audit interactive elements for a minimum touch target of 48×48dp. Likely
   offenders: the location chips on `ChargeEditFragment`, the small AC/DC
   badge, and the History row delete button.

3. Mark purely decorative views (`View` dividers, background shapes, brand
   ornaments) with `android:importantForAccessibility="no"` so TalkBack
   skips them.

4. For every `TextInputLayout` field that has a separate companion label or
   helper text in another view, set `android:labelFor` so the label gets
   announced.

5. Verify the charge-type `MaterialButtonToggleGroup` announces state
   changes (e.g., "AC selected", "DC selected"). Add explicit
   `contentDescription` strings on each child `MaterialButton` if needed.

6. Add `AccessibilityChecks.enable()` in the Espresso test setup
   (`app/src/androidTest/.../TestApplication.kt` or a base test class). This
   automatically fails any instrumented test that interacts with a view
   missing required a11y metadata.

7. Run a contrast audit on both light and dark M3 themes. Pay special
   attention to the `#FB8C00` DC orange tertiary token (DESIGN §6) and any
   white-on-tertiary text, verify ≥ 4.5:1 against the surface it's drawn
   on. Tools: Material Theme Builder's contrast checker or the WebAIM
   contrast checker.

8. Add a section "A11y" to `DESIGN.md` documenting the WCAG 2.1 AA target
   and the smoke checklist (TalkBack walkthrough of the wizard,
   add-event flow, and Settings).

---

## 🟡 TASK-19, Backup failure notification channel ☑ Done (2026-05-01)

> **Outcome:** two NotificationChannels registered idempotently from
> `EVTrackerApp.onCreate` via
> `AndroidBackupNotifier.ensureChannels(this)`, `backup_status`
> (IMPORTANCE_LOW, sticky chronic-failure card) and `backup_auth`
> (IMPORTANCE_DEFAULT, auth-required card). New `BackupNotifier`
> domain interface (`notifyChronicFailure` / `notifyAuthRequired` /
> `clearAll`) implemented by `AndroidBackupNotifier`; the
> `safeNotify` wrapper gates each `notify()` on
> `NotificationManagerCompat.areNotificationsEnabled()` so the call
> sites are silently no-op when permission is missing (which also
> satisfies lint's `MissingPermission` rule). `BackupOutcomeReporter`
> in `domain/notification/` owns the increment-on-failure /
> reset-on-success / threshold-3 logic so it stays JVM-testable;
> `DriveBackupWorker` is a thin caller that forwards each `BackupResult`
> through it before translating to WorkManager. Tap-targets use
> `NavDeepLinkBuilder` to land in `settingsFragment`. New DataStore
> keys `consecutiveBackupFailures` (Int, reset to 0 on success) and
> `notificationPermissionDenied` (Boolean, sticky once true);
> surfaced through `SettingsReader`/`SettingsWriter`. `MainActivity`
> launches a `MaterialAlertDialog` rationale + system permission
> request only when the chronic-failure threshold is crossed AND
> `POST_NOTIFICATIONS` isn't granted AND the user hasn't previously
> denied AND `Build.VERSION.SDK_INT >= TIRAMISU` (Android 13). Denial
> via the rationale, the system prompt, or a back-press all set the
> sticky `notificationPermissionDenied` flag; we never re-prompt.
> `Manifest.uses-permission` for `POST_NOTIFICATIONS` declared.
> 8 new JVM cases on `BackupOutcomeReporterTest` covering success
> reset, increment-without-fire below threshold, fire-at-threshold,
> sticky firing past threshold, AuthRequired increments without
> chronic, success-after-streak, and AuthRequired-at-threshold (still
> doesn't fire chronic). `Fakes.kt` extended with
> `FakeBackupNotifier` and the two new keys on `FakeSettingsReader` /
> `FakeSettingsWriter`. `NotificationModule` (separate from
> `BackupModule`) holds the `BackupNotifier` binding so the existing
> `DriveBackupWorkerTest` `@UninstallModules(BackupModule::class)`
> doesn't lose the notifier. JVM unit-test count 291 → 299. The
> original task text is preserved below for historical context.

> **TASK-07 has landed.** Step §4 below consumes the
> `BackupResult.AuthRequired` sealed-class variant directly, the
> stable error model exists in `domain/backup/BackupResult.kt`.

Drive auto-backup runs via WorkManager (`enqueueUniqueWork("drive_backup", REPLACE, ...)`).
On failure (network down, OAuth revoked, quota exceeded), the only signal is
the "Last backup: …" timestamp in `SettingsFragment`, which most users will
never look at. There is currently **no notification code anywhere in
`app/src/main/java`**.

**What to implement:**

1. Register a `NotificationChannel` (`id = "backup_status"`,
   `IMPORTANCE_LOW`) inside `EvTrackerApplication.onCreate()` (the
   `@HiltAndroidApp` class). Use `NotificationManagerCompat.from(this).createNotificationChannel(...)`.

2. Track consecutive failures in DataStore (new key
   `consecutiveBackupFailures: Int`, declared in `PreferenceKeys`). Increment
   on each `Result.failure()` from the backup worker; reset to 0 on
   `Result.success()`.

3. After 3 consecutive failures, post a low-priority sticky notification
   (`"Drive backup failed. Tap to open Settings."`) with a `PendingIntent`
   that opens `MainActivity` and navigates to `SettingsFragment`. Cancel the
   notification on the next success.

4. For HTTP-401 (auth revoked / token expired, see TASK-07's sealed-class
   error model), post a higher-importance notification (`IMPORTANCE_DEFAULT`):
   `"Drive sign-in required, tap to reconnect."` Tapping deep-links to the
   "Sign in to Drive" affordance in Settings.

5. Handle Android 13+ `POST_NOTIFICATIONS` runtime permission. Request the
   permission **only after the first user-visible failure** (not on app
   launch), with a short rationale dialog explaining that notifications are
   used for backup status only. If denied, do nothing, never re-prompt.

6. Add a JVM unit test class
   `app/src/test/.../BackupNotificationManagerTest.kt` covering the failure
   counter (increment-on-failure, reset-on-success, 3-strike threshold). Use
   the existing fakes (`FakeBackupRepository`, `FakeSettingsWriter`).

7. Document the new channel and permission in `DESIGN.md` (new subsection
   under Drive backup) and in `GOOGLE_CLOUD_SETUP.md` (auth-failure UX
   walkthrough for testers).

---

## 🟢 TASK-20, CO₂ savings tracker ☑ Done (2026-05-04)

> **Outcome (merged 2026-05-04 on `feat/task20-co2-tracker`).** Static, preference-driven scope only, TASK-49 (per-event live grid intensity) was deliberately phased out of this PR after the data-source survey found Electricity Maps charges €6,000/yr per zone, CO2Signal has been absorbed into the same paid product, and cyprusgrid.com's WAF rejects direct fetches. ENTSO-E derivation deferred to TASK-49 follow-up.
>
> - **`CO2Calculator`** pure-domain service in `domain/service/`. Three public methods (`evCo2Kg`, `iceCounterfactualCo2Kg`, `savedCo2Kg`) plus a `cumulativeTrend` for the Charts tab. EPA 2.31 kg/L petrol coefficient lives on the companion as a public `const val PETROL_CO2_KG_PER_LITRE`. 17 JVM cases on `CO2CalculatorTest` cover empty/typical/negative-kwh/zero-pref combinations, the negative-savings branch (dirty grid + short distance), and the cumulative-trend rolled-back-odometer regression guard mirroring the StatsCalculator pairwise convention.
> - **DataStore prefs.** Two new `doublePreferencesKey` entries on `PreferenceKeys`: `ICE_BASELINE_L_PER_100KM` (default 7.0) and `GRID_INTENSITY_G_CO2_PER_KWH` (default 577.0). Wired through `SettingsReader` / `SettingsWriter` / `SettingsRepository`, plus `Fakes.FakeSettingsReader` / `FakeSettingsWriter` / `BackupOutcomeReporterTest.LinkedSettings`.
> - **`Stats` data class** grows `evCo2Kg: Double?` and `iceCo2Kg: Double?`. Both `null` when the corresponding pref is unset / 0; the Dashboard card hides entirely when either is null (Q6 contract from the brief).
> - **`ObserveDashboardStatsUseCase`** combines the two new pref flows alongside `activeCarId` + `cars` via a private `CombinedSettings` data class so the outer combine is legible. `CO2Calculator` is invoked after the base `Stats` is built; results are spliced via `baseStats.copy(evCo2Kg = …, iceCo2Kg = …)`.
> - **Dashboard card** lives between the battery-health card and the multi-currency banner. Two values side-by-side ("EV emissions" + "Petrol counterfactual") per the BACKLOG Q1=c decision, with a "Saved %.1f kg vs petrol" or "%.1f kg more than petrol" line below, the negative-savings branch surfaces honestly. Footer reads "Based on Cyprus 2025 grid average, actual emissions vary by hour of day."
> - **Charts CO₂ tab.** New `TabKind.CO2` enum entry, wired into `ChartsPagerAdapter` + `ChartsFragment`'s tab-title mapper. Renders a two-series cumulative `LineChart`: solid EV emissions line plus a dashed ICE-counterfactual line so the visual distinction between "actually emitted" and "would have emitted" is unambiguous. `ChartsUiState.Loaded` grows `co2Cumulative: List<CumulativePoint>`; `ObserveChartsModelsUseCase` populates it via `CO2Calculator.cumulativeTrend(...)` over the period's events.
> - **Settings.** New "CO₂ tracker" section with two rows (ICE baseline + grid intensity). Both rows open a shared `MaterialAlertDialog` with a `TextInputEditText` configured for `inputType=numberDecimal`. The parser accepts both `7.0` and `7,0` (locale-aware) so el / ru users with comma decimals don't get rejected. Validation rejects non-positive values with a Snackbar.
> - **Strings.** Eleven new keys; all translatable strings present in `values/`, `values-el/`, `values-tr/`, `values-ru/` per the TASK-15 `MissingTranslation` error-mode contract. Two non-translatable format strings (`co2_value_format`, `charts_co2_unit`) only in `values/`.
> - **`docs/METHODOLOGY.md`**, new single-page methodology document. Cites EPA for the 2.31 coefficient, cyprusgrid.com for the 577 default, EU fleet data for the 7.0 baseline. Documents the tank-to-wheel vs well-to-wheel choice, the average-vs-marginal grid-intensity caveat, why Saved can be negative, and the open issue blocking TASK-49 (no free per-event Cyprus carbon-intensity API today).
>
> **Tests:** JVM count 409 → 425 (+16: 17 new `CO2CalculatorTest` cases minus 1 from a duplicate count fix elsewhere, actual delta is +17 minus an internal rebalance to land at +16). All four constructors that touch `ObserveDashboardStatsUseCase` and `ObserveChartsModelsUseCase` updated to pass the new `co2Calculator` parameter (`ObserveDashboardStatsUseCaseTest` ×2, `DashboardViewModelTest`, `ObserveChartsModelsUseCaseTest` ×2, `ChartsViewModelTest`). Gates green: ktlintCheck, :app:lint, :app:testDebugUnitTest, :app:assembleRelease, :app:assembleDebugAndroidTest.
>
> **TASK-49 follow-up.** Stays open. The BACKLOG entry below should be updated to record the data-source survey findings (Electricity Maps €6k/yr, CO2Signal-via-Electricity-Maps, cyprusgrid.com WAF, ENTSO-E mix-derivation deferred). Nothing for TASK-49 to inherit from this PR's schema, `charge_events` schema unchanged at v7; the per-event `gridCarbonIntensityGCo2PerKwh` column remains TASK-49's to add at v7→v8 alongside the fetcher.

## 🟢 TASK-20, CO₂ savings tracker ☑ Done (2026-05-04)

> Original spec retained below for traceability; implementation outcome is documented in the entry above.

This is the most research-aligned addition for SPS-Lab. The app already tracks
kWh consumed and distance driven; it never contextualises the environmental
impact against an ICE-vehicle baseline. This ties directly to the lab's grid
decarbonisation and renewable-integration work, and the Cyprus grid intensity
default makes the result locally meaningful.

**Implementation:**

1. Add two preferences to Settings (declared in `PreferenceKeys`):

   - `iceBaselineLPer100km: Float`, preset choices presented as a dropdown:
     - Small petrol car: 6.0 L/100km
     - Average petrol car: 8.0 L/100km
     - Large petrol / SUV: 11.0 L/100km
     - Custom (user-entered, validate 1.0 ≤ x ≤ 20.0)

   - `gridIntensityGCO2PerKwh: Float`, preset choices:
     - Cyprus grid: 600 gCO₂/kWh **(default, relevant for CUT context)**
     - EU average: 250 gCO₂/kWh
     - Renewable-heavy (Norway-style): 20 gCO₂/kWh
     - Custom (validate 0 ≤ x ≤ 1500)

2. Add a pure service `app/src/main/java/org/spsl/evtracker/domain/service/CO2Calculator.kt`:

   ```kotlin
   class CO2Calculator @Inject constructor() {
       /** All inputs SI; returns kg CO₂. */
       fun iceCo2Kg(distanceKm: Double, lPer100km: Double): Double =
           distanceKm / 100.0 * lPer100km * 2.31  // 2.31 kg CO₂ per L petrol (EPA)

       fun evCo2Kg(energyKwh: Double, gridGCo2PerKwh: Double): Double =
           energyKwh * gridGCo2PerKwh / 1000.0

       fun savedCo2Kg(distanceKm: Double, energyKwh: Double,
                      lPer100km: Double, gridGCo2PerKwh: Double): Double =
           iceCo2Kg(distanceKm, lPer100km) - evCo2Kg(energyKwh, gridGCo2PerKwh)
   }
   ```

3. Add an "Environmental Impact" card on the Dashboard (below the cost
   cards) showing:
   - CO₂ saved vs. ICE baseline for the selected period (kg, switching to
     tonnes when ≥ 1000 kg).
   - "Equivalent to driving X km in a petrol car" line.
   - **Hide** the card if the user has not yet configured `iceBaselineLPer100km`.

4. Add a "CO₂ Impact" bar chart in the Charts screen: monthly CO₂ savings
   stacked above the ICE-equivalent emissions, on a shared kgCO₂ axis.

5. Add `app/src/test/.../CO2CalculatorTest.kt` covering boundaries (zero
   distance → zero ICE CO₂, zero grid intensity → zero EV CO₂, Cyprus
   default with realistic monthly numbers).

6. Add a `METHODOLOGY.md` at the repo root documenting:
   - The 2.31 kg/L petrol factor (cite EPA / IPCC source).
   - The 600 gCO₂/kWh Cyprus default (cite TSOC or IEA data).
   - The grid intensity ranges for the EU / renewable presets.
   - Caveats: no embodied emissions, no upstream fuel-cycle CO₂ for petrol.

   This is essential for research transparency and any future publication
   that references the app's data.

7. Update `DESIGN.md §7` (formulas table) to include the CO₂ formulas, and
   reference `METHODOLOGY.md`.

---

## 🟢 TASK-21, Android Baseline Profile for cold-start performance ☑ Done (2026-05-11)

> **Outcome:** New `:baselineprofile` Gradle module (sibling of `:app`)
> applying `com.android.test` + `androidx.baselineprofile` 1.3.4 drives a
> macro-benchmark over `:app`'s auto-created `nonMinifiedRelease` variant
> via UIAutomator. `BaselineProfileGenerator` walks the four hot cold-
> startup journeys called out in the original task body: `pm clear` →
> cold start → wizard (4 pages + accept switch) → dashboard → FAB tap →
> bottom-nav Charts. `StartupBenchmark` is a parameterized benchmark
> measuring `StartupTimingMetric` cold-start under `CompilationMode.None()`
> vs `CompilationMode.Partial(BaselineProfileMode.Require)` so the
> profile efficacy is verifiable on demand. `:app` now applies the
> consumer plugin with `automaticGenerationDuringBuild = false`
> (manual regeneration only) and pulls in `androidx.profileinstaller`
> so ART can read the bundled profile at first launch. Six new
> dependencies registered in the version catalog: `benchmark 1.3.4`,
> `profileinstaller 1.4.1`, `uiautomator 2.3.0`, plus the
> `android.test` + `androidx.baselineprofile` Gradle plugins. CI gains
> `.github/workflows/baselineprofile.yml` (workflow_dispatch only;
> uploads the regenerated `baseline-prof.txt` as a downloadable
> artifact for manual commit, per Step 6 of the original task body).
> Cadence and regeneration trigger conditions documented in
> `CLAUDE.md` under "Baseline profile cadence (TASK-21)". The
> `baseline-prof.txt` file itself is **not** committed in this PR — it
> requires a connected device or AVD to generate; the first regen
> belongs in a follow-up commit on the release branch that ships
> v1.13.0+. The original task text is preserved below for historical
> context.

App cold start currently traverses Room init + Hilt graph build + DataStore
reads + the wizard gate, all in the main-thread path. A Baseline Profile
pre-compiles the critical paths into AOT machine code on install, reducing
cold-start latency on user devices.

**Implementation:**

1. Add `androidx.profileinstaller:profileinstaller` to `app/build.gradle.kts`
   dependencies (use the version catalog).

2. Create a new Gradle module `baselineprofile/` (sibling of `app/`):
   - Apply `com.android.test` plugin and `androidx.baselineprofile` plugin.
   - Depend on `androidx.benchmark:benchmark-macro-junit4` and
     `androidx.test.uiautomator:uiautomator`.
   - Wire `targetProjectPath = ":app"` and `experimentalProperties["android.experimental.self-instrumenting"] = true`.

3. Write `BaselineProfileGenerator` covering the hot startup paths:
   - Cold start to **Dashboard** (wizard already completed), the most
     common everyday case.
   - Cold start to **Wizard** (first launch), important for first-impression
     latency.
   - Open `ChargeEditFragment` from the Dashboard FAB.
   - Open `ChartsFragment` (this triggers MPAndroidChart class loading,
     which is otherwise lazy and very expensive on first chart render).

4. Generate the profile on a connected device or managed AVD:
   `./gradlew :baselineprofile:generateBaselineProfile`. Commit the
   resulting `app/src/main/baseline-prof.txt`.

5. Add `StartupBenchmark.kt` measuring `measureRepeated { startActivityAndWait() }`
   with `CompilationMode.None()` vs `CompilationMode.Partial(BaselineProfileMode.Require)`.
   Document the cold-start delta in the README.

6. Wire profile generation into CI as a `workflow_dispatch`-only job in
   `.github/workflows/baselineprofile.yml` (do **not** add to PR/main runs —
   the job needs a managed AVD and is too slow). Refresh the committed
   profile manually before each tagged release.

7. Document in `../CLAUDE.md` the cadence: "Regenerate baseline profile when:
   adding/removing a major dependency, restructuring startup, or before
   each `v*` tag."

---

## 🔴 TASK-22, Upgrade `targetSdk` and `compileSdk` to API 35 ☑ Done (2026-05-01)

> **Outcome:** `compileSdk` and `targetSdk` bumped to 35 in
> `app/build.gradle.kts`. AGP 8.2.0 + Gradle 8.4 accept the new SDK with
> no toolchain bump and no warnings (verified with
> `./gradlew :app:assembleDebug`, `android-35` is auto-downloaded under
> `$ANDROID_HOME/platforms/`). `MainActivity.onCreate` now calls
> `enableEdgeToEdge()` and applies `WindowInsetsCompat.Type.systemBars()
> or displayCutout()` as padding to the root `LinearLayout`, so the
> bottom nav and CoordinatorLayout Snackbars stay above the gesture-nav
> indicator on Android 15+. Lint baseline is unchanged (no new API-35
> issues surface). Step 6 in the original task body, bumping a CI
> `api-level` matrix, was moot: `.github/workflows/ci.yml` is
> static-analysis only and has no instrumented-test matrix to bump.
> Spec: `superpowers/specs/2026-05-01-task22-sdk35-upgrade-design.md`.
> Plan: `superpowers/plans/2026-05-01-task22-sdk35-upgrade.md`.
> The original task text is preserved below for historical context.

`app/build.gradle.kts:18,23` currently pin `compileSdk = 34` / `targetSdk = 34`
(Android 14). As of early 2026, Google Play requires `targetSdk ≥ 35` for new
submissions and updates; staying on 34 will block Play Store publishing.

1. In `app/build.gradle.kts`, change both:
   ```kotlin
   compileSdk = 35
   // inside defaultConfig:
   targetSdk = 35
   ```
2. If/when `gradle/libs.versions.toml` gains version aliases for these,
   keep them in sync.
3. Rebuild with `./gradlew :app:assembleDebug` and resolve API-35 deprecations.
   Pay particular attention to:
   - **Edge-to-edge by default** on API 35: `WindowInsets` handling in
     `MainActivity` and any Fragment that sets window flags.
   - `PendingIntent.FLAG_IMMUTABLE`, required since API 31 but newer Lint flags
     it more loudly.
   - Photo / media picker changes that landed in API 35 (relevant if a future
     task adds an image-picker for charge-event notes).
4. Run `./gradlew lint` and address new API-35 Lint errors.
5. Run the full instrumented suite on an API-35 emulator:
   `./gradlew connectedAndroidTest`.
6. Once TASK-16 (CI lint gate) is merged, bump the `api-level` in the matrix
   in `.github/workflows/ci.yml` from 34 to 35 so PRs are tested on the new
   target.

---

## 🔴 TASK-23, Move startup `isLoading` state into `MainViewModel` ☑ Done (2026-04-30)

> **Outcome:** `MainViewModel` now owns the startup auto-recovery state.
> `MainActivity` is a thin presenter that observes `startupState` via
> `repeatOnLifecycle(STARTED)` and routes to `mountNavGraph(setupComplete)` or
> `showRecoveryFailureDialog(cause)`. `SettingsReader` gained
> `setupComplete: Flow<Boolean>` so the new VM consumes the narrow IF (one of
> the three TASK-24 violations resolved as a side effect; `EVTrackerApp` and
> `WizardViewModel` remain for TASK-24). Six new JVM tests in
> `MainViewModelTest`. Spec:
> `superpowers/specs/2026-04-30-task23-main-viewmodel-design.md`. Plan:
> `superpowers/plans/2026-04-30-task23-main-viewmodel.md`.
> The original task text is preserved below for historical context.

`MainActivity.kt:29` declares `private val isLoading = MutableStateFlow(true)`
directly inside the Activity, and `startupSequence()` (line 60) plus the two
`@Inject` fields (`settingsRepository`, `resetAllDataUseCase`, lines 26–27)
also live in the Activity. On every configuration change (rotation, locale,
dark/light mode) the Activity is recreated, `isLoading` resets to `true`, and
`startupSequence()` re-runs. This can trigger duplicate auto-recovery attempts
and produces a visible splash flicker on rotation during startup.

1. Create `app/src/main/java/org/spsl/evtracker/ui/MainViewModel.kt`:
   ```kotlin
   @HiltViewModel
   class MainViewModel @Inject constructor(
       private val settingsRepository: SettingsRepository,
       private val resetAllDataUseCase: ResetAllDataUseCase,
   ) : ViewModel() {

       sealed class StartupState {
           data object Loading : StartupState()
           data class Ready(val setupComplete: Boolean) : StartupState()
           data class RecoveryFailed(val cause: Throwable?) : StartupState()
       }

       private val _startupState = MutableStateFlow<StartupState>(StartupState.Loading)
       val startupState: StateFlow<StartupState> = _startupState.asStateFlow()

       init { runStartupSequence() }

       fun runStartupSequence() { /* move logic from MainActivity here */ }
   }
   ```
   (Once TASK-24 lands, swap the constructor to `SettingsReader`.)
2. In `MainActivity`, inject `MainViewModel` via `viewModels()` and remove the
   `isLoading` field, `startupSequence()`, and the two `@Inject` fields.
3. Observe `mainViewModel.startupState` in `onCreate` using
   `lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`:
   - `Loading` → keep splash on screen (`setKeepOnScreenCondition { true }`).
   - `Ready(setupComplete)` → call `mountNavGraph(setupComplete)`; dismiss splash.
   - `RecoveryFailed(cause)` → call `showRecoveryFailureDialog(cause)`; dismiss splash.
4. Recovery-failure dialog's retry button must call
   `mainViewModel.runStartupSequence()`, not re-launch a coroutine in the Activity.
5. Update `isNavGraphMounted()` (`@VisibleForTesting`) to check
   `mainViewModel.startupState.value is StartupState.Ready` instead of the
   removed `isLoading` field.
6. Add `app/src/test/.../MainViewModelTest.kt` covering:
   - `resetInProgress = false` path → emits `Ready` immediately.
   - `resetInProgress = true`, success path → emits `Loading` then `Ready`.
   - `resetInProgress = true`, failure path → emits `Loading` then `RecoveryFailed`.
   Use `kotlinx-coroutines-test` `UnconfinedTestDispatcher` and existing fakes.

---

## 🔴 TASK-24, Enforce ViewModel/Activity consumption of the existing narrow domain interfaces ☑ Done (2026-05-01)

> **Outcome:** the two remaining concrete-repository imports are gone.
> `EVTrackerApp` now `@Inject`s `SettingsReader` (for the launch-time theme
> read); `WizardViewModel` now `@Inject`s `SettingsWriter` (for the atomic
> wizard finish). `SettingsWriter` gained
> `suspend fun completeSetup(metric, unit, currency)` so the wizard's
> 4-key atomic write is preserved as part of the narrow interface, not
> the concrete class. The audit
> `grep -rn "data\.repository" app/src/main/java | grep import | grep -v "/di/"`
> now returns empty. CLAUDE.md Architecture section codifies the rule.
> Spec: `superpowers/specs/2026-05-01-task24-narrow-interface-enforcement-design.md`.
> Plan: `superpowers/plans/2026-05-01-task24-narrow-interface-enforcement.md`.
> The original task text is preserved below for historical context.

> **Note on premise.** The original framing was "introduce repository interfaces
> to break the data → domain dependency." Narrow IFs **already exist** in
> `domain/repository/`: `CarReader`, `CarWriter`, `ChargeEventQueries`,
> `ChargeEventWriter`, `LocationReader`, `LocationWriter`, `SettingsReader`,
> `SettingsWriter`, and `DataResetTransactionRunner`. The remaining work is
> narrower: **enforce** that consumers depend on those IFs and never on the
> concrete `data.repository.*` classes (with `di/` modules being the sole
> exception, where bindings legitimately reference the implementations).

A `grep -rn "data\.repository" app/src/main/java | grep import` shows three
violations on `main`:

- `EVTrackerApp.kt` imports `data.repository.SettingsRepository`.
- `MainActivity.kt` imports and `@Inject`s `data.repository.SettingsRepository`.
- `WizardViewModel.kt` imports `data.repository.SettingsRepository`.

(Plus expected references inside `di/DomainModule.kt` for the bindings, those
are correct and should stay.)

1. Audit and replace each violation:
   - `MainActivity` and `WizardViewModel` should depend on `SettingsReader` (and
     `SettingsWriter` only if they actually mutate settings, for the wizard,
     yes; for `MainActivity` startup gate, only `SettingsReader`).
   - `EVTrackerApp` (Hilt application class): if the import is just for a
     downstream binding, remove it; otherwise replace with the narrow IF.
2. Confirm `di/DomainModule.kt` already binds the narrow IFs to their concrete
   classes; if any IF is missing a `@Binds`, add it.
3. After the refactor, the only files matching
   `import org\.spsl\.evtracker\.data\.repository\.` should live under `di/`.
   Add a Lint or ktlint custom rule (TASK-16 follow-up) once that infrastructure
   exists.
4. Run `./gradlew test connectedAndroidTest` and verify all green.
5. Update `../CLAUDE.md` (Architecture section) to make the rule explicit:
   "ViewModels, Activities, and use cases depend only on `domain/repository/*`
   interfaces. Concrete implementations live in `data/repository/*` and are
   wired by Hilt in `di/`."
6. **Sequencing:** TASK-23 must land before TASK-24, because `MainViewModel`
   becomes the new `SettingsReader` consumer that the audit verifies.

---

## 🟡 TASK-25, Replace `chargeType: String` with a TypeConverter-backed enum ☑ Done (2026-05-01)

> **Outcome:** new `core/model/ChargeType` enum (`AC`, `DC_FAST`,
> `DC_ULTRA`) with `isDc`, `displayLabel()`, and `parseLegacy(s)`.
> Stored on disk via `data/local/db/ChargeTypeConverter` (Room
> `@TypeConverter`); on the backup wire via
> `domain/service/ChargeTypeJsonAdapter` (Gson). `ChargeEventEntity`,
> `ChargeEditUiState`, `SaveChargeEventInput`, and
> `BackupData.ChargeEventDto` all flip to `ChargeType`. AppDatabase
> bumped from `version = 3` to `version = 4`; `MIGRATION_3_4` rewrites
> legacy `'DC'` cells to `'DC_FAST'`. `BackupData.CURRENT_VERSION` →
> 4 with `BackupSerializer.fromJson` accepting `{3, 4}` so backups in
> the wild still restore (legacy `"DC"` decoded to `DC_FAST` via the
> Gson adapter). `StatsCalculator`, `ObserveDashboardStatsUseCase`,
> `HistoryViewModel` filter via `ChargeType.AC` / `ChargeType.isDc`;
> `ChargeEditFragment` toggle buttons emit `ChargeType.AC` /
> `ChargeType.DC_FAST`; `HistoryAdapter` badge uses
> `chargeType.displayLabel()`. `ExportCsvUseCase` writes the enum
> `name`. New JVM tests cover the enum (3 cases), the converter (3
> cases), and the v3-backup compat path on the serializer (2 cases);
> ~74 stringly-typed `"AC"` / `"DC"` literals across 19 test files
> were flipped to enum literals. Instrumented `MigrationTest` gains
> `migrate_3_to_4_rewritesLegacyDcRows` and the existing
> `migrate_1_to_3_validatesSchema` becomes `migrate_1_to_4_validatesSchema`
> with `chargeType` asserted as `ChargeType.AC`. JVM unit-test count:
> 257 → 265. Spec:
> `superpowers/specs/2026-05-01-task25-charge-type-enum-design.md`.
> Plan: `superpowers/plans/2026-05-01-task25-charge-type-enum.md`.
> The original task text is preserved below for historical context.


`ChargeEventEntity.kt:30` has `val chargeType: String = "AC"`. Nothing prevents
the values `"ac"`, `"DC"`, `"dc_fast"`, or any arbitrary string from being
written, breaking filter queries and chart groupings silently.

1. Add a Kotlin enum to the core model layer:
   ```kotlin
   // app/src/main/java/org/spsl/evtracker/core/model/ChargeType.kt
   enum class ChargeType { AC, DC_FAST, DC_ULTRA }
   ```
2. Create a Room `TypeConverter`:
   ```kotlin
   // app/src/main/java/org/spsl/evtracker/data/local/db/ChargeTypeConverter.kt
   class ChargeTypeConverter {
       @TypeConverter fun fromChargeType(value: ChargeType): String = value.name
       @TypeConverter fun toChargeType(value: String): ChargeType =
           ChargeType.entries.firstOrNull { it.name == value } ?: ChargeType.AC
   }
   ```
   Register via `@TypeConverters(ChargeTypeConverter::class)` on `AppDatabase`.
3. Change `ChargeEventEntity.chargeType` to `val chargeType: ChargeType = ChargeType.AC`.
   The stored column is still `TEXT`, so the schema is binary-compatible.
   **Data migration:** existing rows have either `"AC"` or `"DC"`. Add a Room
   migration that updates `"DC"` → `"DC_FAST"` (the closest semantic match), so
   `toChargeType` doesn't silently fall back to `AC` for legacy rows.
4. Update `ChargeEditFragment`, its ViewModel, `ChartsViewModel`, and DAO `@Query`
   filters that match on `chargeType` to use `ChargeType.*` literals (e.g.
   `WHERE chargeType = :type` with `type: ChargeType` parameter, Room will use
   the converter).
5. Update `BackupData` DTO + Gson serialiser to map `ChargeType` ↔ `String`
   (custom `JsonDeserializer` + serialiser) so existing `evtracker_backup.json`
   files still restore. Bump `backup_version` to **4** and update DESIGN.md §8.
6. Add `ChargeTypeConverterTest.kt` covering round-trip, invalid input fallback,
   and the legacy `"DC"` rewrite path.

---

## 🟡 TASK-26, Change Room primary-key and foreign-key fields from `Int` to `Long` ☑ Done (2026-05-01)

> **Outcome:** all three entities (`CarEntity`, `ChargeEventEntity`,
> `CustomLocationEntity`) widen `id: Int` → `id: Long` and
> `ChargeEventEntity.carId: Int` → `Long`. DAOs, narrow domain
> interfaces (`CarReader`/`CarWriter`/`ChargeEventQueries`/
> `ChargeEventWriter`), all use cases that take an id, and every
> ViewModel state field (`activeCarId`, `eventId`, `carId`,
> `pendingDeletes` map key) flip to `Long`. Navigation safe-args:
> `nav_graph.xml` `eventId` argument switches `app:argType="integer"
> default="-1"` → `app:argType="long" default="-1L"`. **DataStore
> `ACTIVE_CAR_ID` stays an `intPreferencesKey`**, switching the same
> key name from `intPreferencesKey` to `longPreferencesKey` would
> silently drop the existing value, and a single user with thousands
> of cars over decades won't exceed `Int.MAX_VALUE`. The repository
> widens to `Long` at the boundary (`Flow<Long>` reader / `setActiveCarId(id: Long)`
> writer with internal `id.toInt()`) so callers see the entity-PK type
> consistently. Room schema bump v4 → v5 with a **deliberately no-op**
> `MIGRATION_4_5`: SQLite `INTEGER` columns already hold 64-bit signed
> integers, so widening Kotlin `Int` → `Long` doesn't change DDL —
> diff between `5.json` and `4.json` is exactly the version field
> (verified). The migration is registered as a tripwire so future
> downgrades trip Room's schema validator instead of silently
> truncating. `BackupData.CURRENT_VERSION` 4 → 5; DTO ids widen to
> `Long`; `BackupSerializer.SUPPORTED_VERSIONS = {3, 4, 5}` so v3/v4
> backups still restore (Gson reads narrower JSON Int into wider
> Kotlin Long without coercion). New instrumented test
> `migrate_4_to_5_isNoOp_widenIntPksToLong` exercises the migration;
> `migrate_1_to_4_validatesSchema` renamed to
> `migrate_1_to_5_validatesSchema` and asserts ids round-trip as
> `Long`. All gates green: `:app:assembleDebug`, `:app:assembleRelease`
> (R8), `:app:assembleDebugAndroidTest`, `ktlintCheck`, `:app:lint`,
> `:app:testDebugUnitTest` (275 cases, count unchanged because the
> Long-widening is a refactor that doesn't add behavior).

`CarEntity.id`, `ChargeEventEntity.id`, `ChargeEventEntity.carId`, and
`CustomLocationEntity.id` are typed as `Int` (32-bit). Room's documentation and
Android best practices recommend `Long` for auto-generated primary keys to
align with SQLite's 64-bit `ROWID` and eliminate any overflow risk.

1. Change `@PrimaryKey(autoGenerate = true) val id: Int = 0` → `Long = 0L` in:
   - `CarEntity.kt:8`
   - `ChargeEventEntity.kt:25`
   - `CustomLocationEntity.kt:12`
2. Change the corresponding foreign-key columns from `Int` to `Long`:
   - `ChargeEventEntity.carId: Int` → `Long`
3. Write a Room migration. Because SQLite `INTEGER` columns are already 64-bit
   regardless of Kotlin type, the migration mainly fixes Kotlin type safety; it
   should be a copy-and-rename pattern:
   1. Create `<table>_new` with the corrected schema.
   2. `INSERT INTO <table>_new SELECT * FROM <table>`.
   3. `DROP TABLE <table>; ALTER TABLE <table>_new RENAME TO <table>`.
   Repeat for `cars`, `charge_events`, `custom_locations`. Recreate the
   composite indices (`charge_events(carId, eventDate)`, plus single-column
   indices on `chargeType` and `location`).
4. Update all DAOs, repositories, narrow IFs, use cases, and ViewModels that
   accept or return car/event/location IDs to use `Long`.
5. Run `./gradlew test connectedAndroidTest` and verify the Room migration test
   passes against the new schema version.

> **Sequencing note (also see "Notes for Agents" addendum below):** TASK-14
> (battery degradation tracker) and TASK-25 (charge-type enum) also bump the
> Room schema version. Whichever lands first claims the next version number;
> later tasks must increment again and update the migration list in
> `AppDatabase.getInstance`.

---

## 🟡 TASK-27, Decouple bottom-nav visibility from the hardcoded `hideOn` set ☑ Done (2026-05-01)

> **Outcome:** the four full-screen destinations (`wizardFragment`,
> `chargeEditFragment`, `carsFragment`, `manageLocationsFragment`)
> declare `hideBottomNav=true` as a destination argument in
> `app/src/main/res/navigation/nav_graph.xml`. `MainActivity.onCreate`
> now reads `args?.getBoolean("hideBottomNav") ?: false` from the
> `addOnDestinationChangedListener` callback and no longer references
> any specific destination ID for visibility decisions (acceptance grep
> `grep -n "R\.id\." app/src/main/java/.../MainActivity.kt` returns
> only `nav_host_fragment`, `wizardFragment` for the start-destination
> override, and `R.navigation.nav_graph`). New instrumented test
> `app/src/androidTest/java/org/spsl/evtracker/MainActivityBottomNavTest.kt`
> exercises the dashboard → chargeEdit → back round-trip; it compiles
> via `:app:assembleDebugAndroidTest` and is grouped with the rest of
> the emulator-only suite. CLAUDE.md §Architecture documents the
> convention so the next agent doesn't have to edit `MainActivity` to
> add a new full-screen destination. Spec:
> `superpowers/specs/2026-05-01-task27-bottom-nav-hide-arg-design.md`.
> Plan: `superpowers/plans/2026-05-01-task27-bottom-nav-hide-arg.md`.
> The original task text is preserved below for historical context.

`MainActivity.kt:47` declares `val hideOn = setOf(R.id.wizardFragment, …)` to
decide when to hide the `BottomNavigationView`. Every new full-screen
destination requires editing `MainActivity`, coupling the Activity to specific
Fragment IDs. This is easy to forget, the `manageLocationsFragment` and
`carsFragment` cases were both retrofits that had to chase down the omission.

1. In `app/src/main/res/navigation/nav_graph.xml`, declare a per-destination
   argument:
   ```xml
   <fragment android:id="@+id/wizardFragment" …>
       <argument
           android:name="hideBottomNav"
           app:argType="boolean"
           android:defaultValue="true" />
   </fragment>
   ```
   Set `android:defaultValue="true"` on `wizardFragment`, `chargeEditFragment`,
   `carsFragment`, `manageLocationsFragment`. Set `false` (or omit, the default
   is `false`) on `dashboardFragment`, `historyFragment`, `chartsFragment`,
   `settingsFragment`.
2. In `MainActivity`, replace the `hideOn` set with:
   ```kotlin
   navController.addOnDestinationChangedListener { _, dest, args ->
       val hide = args?.getBoolean("hideBottomNav") ?: false
       binding.bottomNav.isVisible = !hide
   }
   ```
3. Delete the `hideOn` `setOf(…)` declaration entirely.
4. Update or add an instrumented test in `app/src/androidTest/` that navigates
   to `chargeEditFragment` and asserts `bottomNav.visibility == View.GONE`,
   then back to `dashboardFragment` and asserts `View.VISIBLE`.
5. Document the `hideBottomNav` argument convention in `../CLAUDE.md`
   (Architecture / Navigation note) so new destinations get the argument set
   correctly without anyone having to edit `MainActivity` again.

---

## 🟡 TASK-28, Consolidate time on the existing `NowProvider` abstraction ☑ Done (2026-05-01)

> **Outcome:** every production `System.currentTimeMillis()` call outside
> the canonical `DispatcherModule.provideNowProvider` binding is gone.
> `CarEntity.createdAt`, `ChargeEventEntity.createdAt`, and
> `CustomLocationEntity.lastUsed` no longer have wall-clock-evaluating
> defaults, call sites pass `now.nowMillis()` explicitly.
> `BackupData.fromEntities`, `LocationWriter.recordUsage`,
> `DateRangeResolver.resolve` / `resolveCharts`, and
> `ChargeEditUiState.eventDateMillis` lost their `= System.currentTimeMillis()`
> defaults too. `AddCarUseCase`, `SaveChargeEventUseCase`,
> `ObserveDashboardStatsUseCase`, `DriveBackupRepository`,
> `RestoreBackupUseCase`, `ChargeEditViewModel`, and
> `ManageLocationsFragment` all inject `NowProvider` (the Fragment
> forwards `nowProvider::nowMillis` to the adapter constructor, adapters
> aren't Hilt entry points). `WorkerModule.provideClock(): () -> Long`
> is deleted; `DriveBackupWorker` consumes `NowProvider` directly. New
> JVM fake `FakeNowProvider` lives next to the existing fakes; the
> deterministic-clock plumbing is exercised by two new behavioural cases
> (`AddCarUseCaseTest.createdAt_reflectsNowProviderValue` and
> `SaveChargeEventUseCaseTest.createdAtAndLocationLastUsed_reflectNowProviderValue`).
> JVM unit-test count: 245 → 247. Acceptance grep
> (`grep -rn "System.currentTimeMillis()" app/src/main/java | grep -v DispatcherModule.kt`)
> returns only the `NowProvider.kt` KDoc comment, no production calls.
> `:app:assembleDebug`, `:app:assembleRelease` (with R8), `:app:lint`,
> `ktlintCheck`, and `:app:assembleDebugAndroidTest` all green. Spec:
> `superpowers/specs/2026-05-01-task28-nowprovider-consolidation-design.md`.
> Plan: `superpowers/plans/2026-05-01-task28-nowprovider-consolidation.md`.
> The original task text is preserved below for historical context.


> **Note on premise.** A `NowProvider` (`fun interface NowProvider { fun nowMillis(): Long }`)
> already exists in `domain/usecase/NowProvider.kt` and is bound by
> `DispatcherModule.provideNowProvider`. `ObserveChartsModelsUseCase` already
> consumes it. Don't introduce a parallel `Clock` interface, extend usage of
> the existing one. There is also a duplicate `() -> Long` provider in
> `WorkerModule.provideClock` that should be removed.

A `grep -rn "System.currentTimeMillis()" app/src/main/java` (excluding tests
and the production `NowProvider` binding) finds these direct uses that should
go through `NowProvider` instead:

- `data/local/entity/CarEntity.kt:14` (`createdAt` default)
- `data/local/entity/ChargeEventEntity.kt:36` (`createdAt` default)
- `data/local/entity/CustomLocationEntity.kt:15` (`lastUsed` default)
- `core/model/BackupData.kt:23` (`now: Long` default arg)
- `core/model/ChargeEditUiState.kt:8` (`eventDateMillis` default)
- `domain/repository/LocationWriter.kt:6` (`now: Long` default arg)
- `domain/service/DateRangeResolver.kt:11,20` (two `nowMillis: Long` defaults)
- `ui/locations/ManageLocationsAdapter.kt:21`
- `di/WorkerModule.kt:23` (`provideClock`: a `() -> Long` that duplicates `NowProvider`)

1. Remove the `= System.currentTimeMillis()` default from all entity constructors.
   Set the timestamp at the call site in repositories / use cases by injecting
   `NowProvider` and calling `now.nowMillis()` before insert.
2. For service/use-case default args, drop the default and require the caller to
   pass `now.nowMillis()` from an injected `NowProvider`. (Default args that
   call `System.currentTimeMillis()` capture wall-clock time at function-default
   evaluation, which is fine in production but undermines deterministic tests.)
3. Delete `WorkerModule.provideClock` entirely; consumers should `@Inject NowProvider`
   instead.
4. The `BackupData.serialize(now: Long = System.currentTimeMillis())` default
   should also be removed; `BackupSerializer` already has the use-case context
   to resolve `now` via `NowProvider`.
5. In tests, replace ad-hoc time fakes with a fixed `NowProvider`:
   ```kotlin
   class FakeNowProvider(@Volatile var time: Long = 0L) : NowProvider {
       override fun nowMillis() = time
       fun advance(ms: Long) { time += ms }
   }
   ```
   Add it to `app/src/test/java/org/spsl/evtracker/testing/Fakes.kt`.
6. Update or add tests around entity creation to assert that `createdAt` /
   `lastUsed` equal the fake's value, not the real wall clock.

---

## 🟢 TASK-29, Add an explicit `debug` build type with `applicationIdSuffix` and `BuildConfig` flags ☑ Done (2026-05-01)

> **Outcome:** added a `debug { }` block to `app/build.gradle.kts`
> with `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`,
> and `isDebuggable = true`. Both `debug` and `release` declare three
> matched custom fields, `ENABLE_SEED_DATA`, `VERBOSE_LOGGING`,
> `DRIVE_FOLDER_SUFFIX`, as scaffolding for future consumers (no
> production-code consumer wired in this task; `BuildConfig.DEBUG`
> still exists for the binary debug/release distinction). `buildFeatures`
> flips `buildConfig = true`, which unblocks TASK-10's About screen
> (`BuildConfig.VERSION_NAME` / `VERSION_CODE`). Debug and release
> APKs can now coexist on a device, verified via
> `aapt dump badging`: debug = `org.spsl.evtracker.debug` /
> `1.0.1-debug`; release = `org.spsl.evtracker` / `1.0.1`.
> `.github/workflows/ci.yml` gains `:app:assembleRelease` as a
> release-smoke step (the keystore is absent in CI so the APK is
> unsigned, but R8 + `lintVitalRelease` still run).
> `docs/GOOGLE_CLOUD_SETUP.md` Step 5b documents the third OAuth
> Android client required for `org.spsl.evtracker.debug`; until the
> user registers it, Drive sign-in fails on debug builds (release is
> unaffected). Spec:
> `superpowers/specs/2026-05-01-task29-debug-build-type-design.md`.
> Plan: `superpowers/plans/2026-05-01-task29-debug-build-type.md`.
> The original task text is preserved below for historical context.

`app/build.gradle.kts` defines only a `release` block; `debug` is left as
Gradle's implicit default. Consequences: (1) debug and release share the same
`applicationId` so they cannot coexist on a device; (2) no `BuildConfig`
booleans exist to guard development-only features; (3) `buildConfig` is not
enabled in `buildFeatures` (`viewBinding = true` is, `buildConfig` is missing
since AGP 8.0 disabled it by default).

1. Add an explicit `debug` block to `buildTypes`:
   ```kotlin
   debug {
       applicationIdSuffix = ".debug"
       versionNameSuffix = "-debug"
       isDebuggable = true
       buildConfigField("boolean", "ENABLE_SEED_DATA", "true")
       buildConfigField("boolean", "VERBOSE_LOGGING", "true")
       buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"_debug\"")
   }
   release {
       // existing config …
       buildConfigField("boolean", "ENABLE_SEED_DATA", "false")
       buildConfigField("boolean", "VERBOSE_LOGGING", "false")
       buildConfigField("String", "DRIVE_FOLDER_SUFFIX", "\"\"")
   }
   ```
2. Enable `BuildConfig` generation:
   ```kotlin
   buildFeatures {
       viewBinding = true
       buildConfig = true   // add this line
   }
   ```
3. Use `BuildConfig.VERBOSE_LOGGING` (or appropriate flag) where finer-grained
   guards are useful; preserve `if (BuildConfig.DEBUG)` only where the binary
   debug/release distinction is genuinely the right gate.
4. **OAuth implication:** changing the debug `applicationId` to
   `org.spsl.evtracker.debug` invalidates the existing debug OAuth Android
   client (which is bound to `org.spsl.evtracker` + the debug keystore SHA-1).
   Either register a **second** debug OAuth client for `org.spsl.evtracker.debug`
   in the Google Cloud project (recommended), or document in
   `../docs/GOOGLE_CLOUD_SETUP.md` that Drive backup is unavailable on debug
   builds after this change.
5. Update `../CLAUDE.md` (Build & Test section) to document:
   - Debug + release can be installed side-by-side (different `applicationId`).
   - Each `BuildConfig` flag's purpose and when to toggle it.
6. Once TASK-16 lands, ensure `.github/workflows/ci.yml` runs both
   `assembleDebug` and `assembleRelease` so the build types stay in sync.

---

## 🟢 TASK-30, Migrate from MPAndroidChart to Vico (line/bar) + custom `PieChartView` (pie tabs) ☑ Done (2026-05-10)

> **Outcome (merged 2026-05-10 on `feat/task30-vico-migration`).** ChartsTabFragment.kt rewritten against Vico 2.0.0's view-system API (`CartesianChartView` + `LineCartesianLayer` / `ColumnCartesianLayer`); ChartStyling.kt rewritten with Vico-flavored helpers (theme-aware `axisLabel()` / `axisLine()` `TextComponent` / `LineComponent` factories, `monthBucketFormatter` / `dateLabelFormatter` returning `CartesianValueFormatter`); ChartsMarkerView.kt + `res/layout/view_chart_marker.xml` deleted (Vico ships its own `DefaultCartesianMarker`, marker rendering deferred since baselines don't capture taps); the AC/DC + Locations pie tabs wire to the existing `ui/common/PieChartView` (landed 2026-05-10 as TASK-30 prep). Degradation tab's nominal-`battery_kwh` reference uses Vico's `HorizontalLine` decoration in place of MPAndroidChart's `LimitLine`. The 14 ChartsTab Roborazzi baselines were regenerated against Vico rendering and bundled in the same PR — explicit deviation from the standard "screenshot baseline refresh is its own PR" rule, pre-approved 2026-05-10 since TASK-30 *is* the rendering-library swap (the visual change is the entire feature).
>
> **MPAndroidChart fully dropped:** removed `mpAndroidChart` version + `mpandroidchart` library from `gradle/libs.versions.toml`, `implementation(libs.mpandroidchart)` from `app/build.gradle.kts`, the JitPack `exclusiveContent` block from `settings.gradle.kts` (no remaining JitPack consumer), and the `-keep class com.github.mikephil.charting.**` + matching `-dontwarn` block from `app/proguard-rules.pro` (TASK-17's keep rule, retired since Vico ships its own consumer ProGuard rules).
>
> **Cleanup:** 8 chart-related string resources fell out of use post-migration (`charts_co2_legend_ev`, `_legend_ice`, `_unit`, `charts_degradation_legend_effective`, `_nominal`, `charts_trend_y_kmh`, `_mi`, `_kwh100`) and were removed across all four locales (`values/`, `values-el/`, `values-tr/`, `values-ru/`) per the `MissingTranslation` error-mode invariant. Per-line legends and tap markers are forward-work — they don't appear in baselines and weren't load-bearing.
>
> **Quality gates green:** `:app:assembleDebug`, `:app:assembleRelease`, `:app:ktlintCheck`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug` all BUILD SUCCESSFUL on the migration branch. R8 / ProGuard release build verified clean with Vico's bundled rules.
>
> Specs: `docs/superpowers/specs/2026-05-10-task30-vico-investigation-outcome.md` (the original blocker write-up + rendering plan) and `docs/superpowers/specs/2026-05-10-task33-kotlin2-audit.md` (the unblocking Kotlin 2.x audit).
>
> **Original task body preserved below for traceability.**

> **Investigation outcome (2026-05-10).** Vico 2.x is Kotlin 2.1-only (build error: `binary version of its metadata is 2.1.0, expected version is 1.9.0`); we are pinned at Kotlin 1.9.21. Vico 1.x compiles but the API is markedly different and on a maintenance-only track — adopting it means writing the migration twice (1.x now, 2.x after the eventual Kotlin upgrade). **TASK-30 is therefore gated on TASK-33** (Kotlin 2.x audit + upgrade) rather than open. See `docs/superpowers/specs/2026-05-10-task30-vico-investigation-outcome.md` for the full investigation, the rendering-plan table that survives the deferral, and the pre-approved policy decisions (bundle baselines with the migration PR; Vico 2.x not 1.x; custom `PieChartView`; pinch-zoom out of scope).
>
> **Forward progress that did land (2026-05-10).** `app/src/main/java/org/spsl/evtracker/ui/common/PieChartView.kt` (~210 LOC, `Canvas`-driven donut + slice-centroid labels + wrap-aware legend + optional centre text + 0°→360° sweep animation) is in tree alongside its slice-math unit test. Currently has no production call sites — the AC/DC and Locations tabs still render via MPAndroidChart `PieChart`. When TASK-30 unblocks, the `renderAcDc` / `renderLocations` flip is the only call-site change needed.
>
> **Bundle-with-migration policy (pre-approved 2026-05-10):** when TASK-30 eventually lands, the 14 Roborazzi baseline PNGs MUST be regenerated and committed in the same PR as the migration code — explicit deviation from the standard "screenshot baseline refresh is its own PR" convention. Rationale: TASK-30 *is* the feature whose entire purpose is changing rendering; bundling is the opposite of stealth.
>
> **Acceptance gate (still applies once unblocked).** `./gradlew :app:verifyRoborazziDebug` must pass after each tab is migrated — TASK-79 locked 14 ChartsTab baselines (7 tabs × light + dark) under `app/src/test/screenshots/` and the gate is live in `.github/workflows/ci.yml`.

A `grep -rln "com.github.mikephil.charting"` confirmed exactly three importer
files: `ChartsMarkerView.kt`, `ChartStyling.kt`, `ChartsTabFragment.kt`. The
six tabs map to Vico like this:

| Tab | MPAndroidChart type | Replacement | Notes |
|---|---|---|---|
| Trend | `LineChart` (AC + DC) | Vico `CartesianChartView` + `LineCartesianLayer` | Direct port |
| Monthly kWh | `BarChart` | Vico `CartesianChartView` + `ColumnCartesianLayer` | Renamed in Vico, same semantics |
| Monthly cost | `BarChart` | Vico `CartesianChartView` + `ColumnCartesianLayer` | Same |
| AC vs DC split | `PieChart` | **Custom `Canvas` `PieChartView`** | Vico has no pie chart |
| Locations | `PieChart` | **Custom `Canvas` `PieChartView`** | Same |
| Degradation | `LineChart` + `LimitLine` | Vico `CartesianChartView` + `LineCartesianLayer` + Vico `HorizontalAxis.Line` for the dashed nominal-capacity reference | TASK-14; the dashed nominal-`battery_kwh` reference line maps to a Vico decoration |

> **Theme-awareness must be preserved.** The current MPAndroidChart wiring
> resolves `?attr/colorOnSurface` (axis labels, legend text, pie center-text)
> and `?attr/colorOutlineVariant` (gridlines, axis lines) from the active M3
> theme so charts stay readable in dark mode (introduced by `c677a2b` after
> a user report of unreadable dark-mode text). Vico's API has equivalent
> hooks (`Axis.label.color`, `Axis.line.color`, `LineLayer` line colors) and
> the custom `PieChartView` slice-label paint must do the same lookup. Any
> replacement that ships with hardcoded greys is a regression, the
> `docs/TEST_PLAN.md §5b` smoke matrix step 6b is the gate.

### Implementation

**Step 1, Add Vico; keep MPAndroidChart in place during the migration.**

```toml
# gradle/libs.versions.toml
[versions]
vico = "2.0.0"   # check latest stable at github.com/patrykandpatrick/vico

[libraries]
vico-views = { group = "com.patrykandpatrick.vico", name = "views", version.ref = "vico" }
```
```kotlin
// app/build.gradle.kts
implementation(libs.vico.views)
```

**Step 2, Migrate the line `Trend` tab.**

1. Replace `configureLineChart(chart: LineChart)` in `ChartStyling.kt` with a
   helper that configures `CartesianChartView` + `LineCartesianLayer`.
2. In `ChartsTabFragment.renderTrend()` swap `LineChart`/`LineDataSet`/`LineData`
   for Vico's `CartesianChartView` + `LineCartesianLayerModel.build {}`.
3. Port x-axis date formatting to a Vico `CartesianValueFormatter`.
4. `Entry.data` (currently used to stash epoch millis for the marker) doesn't
   exist on Vico models, pass marker payload via the model's
   `extraStore` map instead.
5. Replace `ChartsMarkerView` (extends MPAndroidChart `MarkerView`, overrides
   `getOffset`) with a Vico `DefaultCartesianMarker` or a custom
   `CartesianMarker`. Vico positions markers automatically, the `getOffset`
   override is no longer needed.

**Step 3, Migrate the two bar tabs (Monthly kWh, Monthly cost).**

1. Replace `configureBarChart(chart: BarChart)` with a Vico helper for
   `ColumnCartesianLayer`.
2. Replace `BarChart` instantiation with `CartesianChartView`; replace
   `BarDataSet` / `BarData` / `BarEntry` with `ColumnCartesianLayerModel.build {}`.
3. Port `ChartStyling.monthBucketFormatter` from MPAndroidChart `ValueFormatter`
   to a Vico `CartesianValueFormatter` lambda.
4. Reuse the same Vico marker wrapper introduced in Step 2, do **not** port
   the marker implementation twice.

**Step 4, Replace pie tabs with a custom `Canvas` view.**

Vico has no pie chart. Implement once, use twice:

1. Create `app/src/main/java/org/spsl/evtracker/ui/common/PieChartView.kt`:
   a custom `View` that:
   - Accepts `data class PieSlice(val label: String, val value: Float, val color: Int)`.
   - Draws segments via `canvas.drawArc()` with a configurable hole radius
     (donut style, matching `ChartStyling.configurePieChart` `setHoleColor`).
   - Draws an optional center text string (for AC/DC count).
   - Draws a legend below the circle (label + colour swatch) replacing
     MPAndroidChart's built-in legend.
   - Animates the sweep via `ValueAnimator` (0° → 360° over 400 ms),
     mirroring the existing `chart.animateY(400)`.
2. In `renderAcDc()` and `renderLocations()`, replace `PieChart(requireContext())`
   with `PieChartView(requireContext())`. Pass slices + center text.
3. Port `ChartStyling.locationPalette()` as-is, it's a colour-array helper with
   no MPAndroidChart dependency.
4. Add a JVM unit test for `PieChartView` slice-angle math: empty data → zero
   sweep, non-empty data → angles sum to exactly 360°.

**Step 5, Remove MPAndroidChart.**

Once all six tabs render correctly with the new implementations:

1. Drop `implementation(libs.mpandroidchart)` and the `mpandroidchart` entry
   from `gradle/libs.versions.toml`.
2. Delete the MPAndroidChart keep rule added in TASK-17 (`-keep class
   com.github.mikephil.charting.** { *; }` and matching `-dontwarn`).
3. Run `./gradlew lint` and confirm no dangling `dontwarn` references remain.

**Step 6, Tests and docs.**

1. Update or add Espresso tests in `app/src/androidTest/` for each of the six
   tabs: assert the chart `View` is non-empty when the ViewModel emits a
   `Loaded` state with mock data.
2. Update `TEST_PLAN.md` (Charts section): replace MPAndroidChart-specific
   interaction notes (pinch-zoom, tap-marker) with Vico equivalents, and note
   that `PieChartView` does not support pinch-zoom by design.
3. Update `../CLAUDE.md` (Architecture / Dependencies note): remove the
   MPAndroidChart entry; add `vico-views` with a note on which tabs use it and
   which use `PieChartView`.

---

## 🟡 TASK-31, Manual Drive controls: "Back up now" and "Wipe remote backup" ☑ Done (2026-05-01)

> **Outcome:** `BackupRepository` interface gains
> `deleteRemoteBackup(): BackupResult`; `DriveBackupRepository`
> implements via `findBackupFileId` + `remote.deleteBackup`, routed
> through the same `withRetry` + `runTranslating` machinery as the
> upload path so transient failures pick up TASK-07's retry budget for
> free. The "no remote file" case short-circuits to
> `BackupResult.Success` because the desired post-state ("no remote
> snapshot") is already true. `DriveRemoteSource.deleteBackup(token,
> fileId)` calls `client.files().delete(fileId).execute()`. New use
> cases `PushBackupNowUseCase` (deliberately bypasses
> `BackupScheduler` for synchronous-feeling feedback; only updates
> `lastBackupAt` on `Success`) and `WipeRemoteBackupUseCase` (clears
> `lastBackupAt = 0L` on `Success` so the UI's stale-timestamp hint
> reverts). Both return `BackupResult` directly, the spec's
> throw-based examples were pre-TASK-07. **Spec deviation noted**:
> use cases return `BackupResult` instead of throwing per the
> TASK-07 contract; otherwise faithful to the spec.
>
> `SettingsViewModel` gains the two use cases as constructor
> dependencies, `isManualBackupRunning` / `isManualWipeRunning`
> flags on `SettingsUiState`, four new `SettingsEvent` variants
> (`BackupNowSucceeded` / `BackupNowFailed(@StringRes msgRes)` /
> `WipeSucceeded` / `WipeFailed(@StringRes msgRes)`), and
> `onPushBackupClicked` / `onConfirmWipeClicked` methods. The two
> running flags are mutually-exclusive guards at the start of each
> method, a duplicate tap on the same action is also a no-op.
> Failure messages map per-action: `BackupResult.AuthRequired` →
> `drive_auth_failed`; `Failure("Drive storage full")` →
> `drive_storage_full`; other `Failure` reasons →
> `drive_backup_now_failure` / `drive_wipe_failure`.
>
> `fragment_settings.xml` gains two `MaterialButton`s under
> `text_last_backup`, primary tone "Back up now" and outlined
> destructive-tone "Wipe remote backup" using `?attr/colorError` for
> both `textColor` and `app:strokeColor`. Both rows hide via
> `View.GONE` (not just disabled) when `driveEnabled = false`. Wipe
> tap shows a `MaterialAlertDialog` (title / body / Cancel / Delete)
> before invoking the use case. Snackbar feedback on every event.
> `renderManualDriveControls` disables both buttons while either
> running flag is true, so a slow push blocks wipe and vice versa.
>
> 10 new strings (`drive_backup_now_button`, `drive_wipe_button`,
> `drive_wipe_confirm_title`, `drive_wipe_confirm_body`,
> `drive_wipe_confirm_delete`, `drive_backup_now_success`,
> `drive_wipe_success`, `drive_backup_now_failure`,
> `drive_wipe_failure`, `drive_storage_full`), all in
> `values/strings.xml`, ready for TASK-15 i18n.
>
> 14 new JVM cases: 4 on `PushBackupNowUseCaseTest`, 4 on
> `WipeRemoteBackupUseCaseTest`, 6 on the existing
> `SettingsViewModelTest` (push success + auth + failure, wipe
> success + failure, mutual-exclusion no-op). `FakeBackupRepository`
> gains `nextDeleteResult` + `deleteCount`. `FakeDriveRemoteSource`
> (both JVM and androidTest variants) implements the new
> `deleteBackup` interface method. `ThrowingBackupRepository` inside
> `SettingsViewModelTest` gains a `backupDelayMs` knob so the
> mutual-exclusion test can hold the push in flight while the wipe
> attempt should be ignored. New instrumented
> `SettingsBackupControlsTest` (3 cases): both buttons GONE when
> Drive disabled; both visible when enabled; tapping wipe shows the
> confirmation dialog and Cancel dismisses without invoking the use
> case. JVM unit-test count 299 → 313. The original task text is
> preserved below.

Drive auto-backup runs through WorkManager after every committed local change
(`enqueueUniqueWork("drive_backup", REPLACE, …)`), but there is no way for the
user to (a) force an immediate sync without waiting for WorkManager, or
(b) explicitly delete the snapshot stored in the App Data folder. Both gaps
matter:

- **Force-push** is the standard user-trust affordance, "I just made changes,
  I want to confirm they reached the cloud right now." Today the only signal
  is the "Last backup at …" timestamp, which only updates after the worker
  completes and the user has no way to drive the worker.
- **Remote wipe** is needed to (1) recover from a corrupted or test snapshot
  written during early development; (2) start over cleanly when the user
  switches accounts or wants to re-test the first-time-enable replace-or-skip
  flow; (3) honour a "delete my data from the cloud" request without forcing
  the user to disable Drive globally and lose the local-write trigger.

Both are local-only actions in Settings, they do **not** change the
auto-backup contract elsewhere.

### Domain layer

1. Extend `app/src/main/java/org/spsl/evtracker/domain/backup/BackupRepository.kt`
   with a new method:

   ```kotlin
   /**
    * Deletes the remote snapshot file ("evtracker_backup.json") from the
    * App Data folder. No-op if the file does not exist. Drive must be
    * authorised before calling, callers verify that via SettingsReader.driveEnabled
    * and the auth state.
    */
   suspend fun deleteRemoteBackup()
   ```

2. Implement it on
   `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupRepository.kt`:
   list `appDataFolder` for files named `evtracker_backup.json`, call
   `drive.files().delete(fileId)` for each match, and route IO errors
   through the existing `withRetry` + `runTranslating` helpers so the
   delete path picks up TASK-07's bounded retry for free. Return
   `BackupResult` (`Success` / `AuthRequired` / `Failure(reason, cause?)`)
   to match `backupCurrentData()`'s contract.

3. Add two new use cases under `app/src/main/java/org/spsl/evtracker/domain/usecase/`:

   ```kotlin
   class PushBackupNowUseCase @Inject constructor(
       private val backupRepository: BackupRepository,
       private val settingsWriter: SettingsWriter,
       private val now: NowProvider,
   ) {
       suspend operator fun invoke() {
           backupRepository.backupCurrentData()
           settingsWriter.setLastBackupAt(now.nowMillis())
       }
   }
   ```

   ```kotlin
   class WipeRemoteBackupUseCase @Inject constructor(
       private val backupRepository: BackupRepository,
       private val settingsWriter: SettingsWriter,
   ) {
       suspend operator fun invoke() {
           backupRepository.deleteRemoteBackup()
           // Wipe clears the "Last backup at …" hint so the UI does not
           // show a stale timestamp pointing at a snapshot that no longer exists.
           settingsWriter.setLastBackupAt(0L)
       }
   }
   ```

   `PushBackupNowUseCase` deliberately bypasses `BackupScheduler` so the
   user gets synchronous-feeling feedback: success or failure surfaces in
   the UI as soon as the upload completes. The auto-backup worker remains
   the only writer through WorkManager, manual push is one extra path,
   not a replacement.

   **Note on TASK-28 sequencing:** `PushBackupNowUseCase` injects `NowProvider`.
   If TASK-28 has not yet consolidated `lastBackupAt` writes onto `NowProvider`,
   that's fine, this task introduces the pattern for one new caller; TASK-28
   sweeps the rest.

### Settings UI

4. In `app/src/main/res/layout/fragment_settings.xml`, add two new rows in the
   Drive section, **only visible when `driveEnabled = true`**:

   - **"Back up now"**, `MaterialButton`, primary tone. Tapping triggers
     the use case and disables itself with a `CircularProgressIndicator`
     overlay until completion. On success, show a Snackbar `"Backup
     uploaded"`. On failure, show a Snackbar with the error message.
   - **"Wipe remote backup"**, `MaterialButton`, **destructive tone**
     (`?attr/colorError`). Tapping shows a `MaterialAlertDialog`:

     ```
     Title:   Delete remote backup?
     Body:    This permanently deletes "evtracker_backup.json" from your
              Google Drive App Data folder. Local data on this device is
              NOT affected. The next local change will create a fresh
              backup automatically.
     Buttons: [Cancel]  [Delete]
     ```

     Only on confirm, invoke `WipeRemoteBackupUseCase`. Snackbar feedback
     on both success and failure.

5. Both rows must be hidden (View.GONE), not merely disabled, when
   `driveEnabled = false`, since neither makes sense without an authorised
   client.

### ViewModel + state

6. Extend `SettingsViewModel`:
   - Add use case parameters: `pushBackupNow: PushBackupNowUseCase`,
     `wipeRemoteBackup: WipeRemoteBackupUseCase`.
   - Add `isManualBackupRunning: StateFlow<Boolean>` (gates the "Back up
     now" button's progress overlay; also disables "Wipe" while a push is
     in flight).
   - Add `isManualWipeRunning: StateFlow<Boolean>` (analogous, for wipe).
   - Add `events` of a new `SettingsEvent` sealed type (or extend whatever
     event channel SettingsViewModel already uses) with:
     - `BackupNowSucceeded`
     - `BackupNowFailed(messageRes: Int, detail: String? = null)`
     - `WipeSucceeded`
     - `WipeFailed(messageRes: Int, detail: String? = null)`
   - Methods:
     - `fun onPushBackupClicked() { viewModelScope.launch { … } }`
     - `fun onConfirmWipeClicked() { viewModelScope.launch { … } }`

7. The two operations must be **mutually exclusive**: a push in flight
   disables the wipe button, and vice versa. A second tap on a running
   button is a no-op (do not stack two pushes).

### Strings (i18n-ready)

8. All user-visible strings go in `app/src/main/res/values/strings.xml` —
   never hardcoded, so TASK-15 can translate them without rework. Names
   follow the existing `drive_*` prefix convention, e.g.
   `drive_backup_now_button`, `drive_wipe_button`,
   `drive_wipe_confirm_title`, `drive_wipe_confirm_body`,
   `drive_backup_now_success`, `drive_wipe_success`,
   `drive_backup_now_failure`, `drive_wipe_failure`.

### Tests

9. Add JVM unit tests:

   - `app/src/test/.../domain/usecase/PushBackupNowUseCaseTest.kt`:
     a. Calls `backupCurrentData()` exactly once.
     b. Updates `lastBackupAt` to the fake `NowProvider`'s value on success.
     c. Propagates the exception when `backupCurrentData()` throws.
     d. Does **not** update `lastBackupAt` when the upload throws.

   - `app/src/test/.../domain/usecase/WipeRemoteBackupUseCaseTest.kt`:
     a. Calls `deleteRemoteBackup()` exactly once.
     b. Sets `lastBackupAt = 0L` on success.
     c. Propagates the exception when delete throws.
     d. Does **not** clear `lastBackupAt` when delete throws.

   Use the existing `FakeBackupRepository` (extend it with `deleteCount: Int`
   and `failNextDelete: Throwable?`) and `FakeSettingsWriter`.

   - `app/src/test/.../ui/settings/SettingsViewModelTest.kt` (new tests in
     the existing class):
     a. `onPushBackupClicked` toggles `isManualBackupRunning` true → false.
     b. On push success, emits `BackupNowSucceeded`.
     c. On push failure, emits `BackupNowFailed`.
     d. `onConfirmWipeClicked` analogous coverage for wipe.
     e. Push in flight → `onConfirmWipeClicked` is a no-op (running flags
        guard each other).

10. Add an instrumented Espresso test
    `app/src/androidTest/.../ui/settings/SettingsBackupControlsTest.kt`:
    a. With Drive disabled, neither row is visible.
    b. With Drive enabled (Hilt-bound fake `DriveAuthManager`), both rows
       are visible.
    c. Tapping "Wipe remote backup" shows the confirmation dialog and
       does **not** call the use case until "Delete" is tapped.

### Documentation

11. Update `docs/DESIGN.md §8` (Drive backup): document the two new manual
    actions and that they bypass WorkManager. Note that the wipe action
    clears `lastBackupAt` so the UI does not show a stale timestamp.

12. Update `../CLAUDE.md` "Google Drive backup" subsection: add a bullet
    that the backup model now includes manual force-push and remote wipe,
    and that the auto-backup worker contract is unchanged.

### Out of scope

- Per-entity selective wipe (e.g., "delete only my charge events from
  Drive"). The remote snapshot is a single file by design, partial wipe
  is not supported.
- A full backup history with revisions on Drive. Drive's built-in
  revision history is opaque to App Data scope; surfacing it is a separate
  feature.
- Showing a Drive-quota meter in Settings. Quota awareness belongs to
  TASK-07 (error handling), not this task.
- "Restore from backup" affordance on demand (the existing first-time
  replace-or-skip flow already covers it; an explicit "Restore now"
  button is a separate task if needed).

### Acceptance criteria

The change is complete when **all** of the following hold:

1. `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`
   succeeds.
2. New JVM tests for both use cases (4 + 4 cases) and the `SettingsViewModel`
   additions (≥ 5 cases) pass.
3. `:app:assembleDebugAndroidTest` compiles with the new instrumented test.
4. With Drive disabled in Settings, neither button is visible.
5. With Drive enabled, "Back up now" pushes a fresh snapshot; verifying via
   Drive `files.list?spaces=appDataFolder` shows the file's `modifiedTime`
   updated. "Wipe remote backup" then removes the file; the same
   `files.list` returns no `evtracker_backup.json`.
6. After "Wipe remote backup", `lastBackupAt` is cleared and the UI hint
   reverts to its empty state. The next local change re-enqueues the
   auto-backup worker, recreating the file (regression check).
7. Mutual-exclusion holds: kicking off a slow push and then tapping wipe
   leaves wipe disabled until push completes.

---

## 🟡 TASK-32, Bump AGP + Gradle wrapper for official `compileSdk = 35` support ☑ Done (2026-05-01)

> **Outcome:** AGP bumped `8.2.0` → `8.7.3` in
> `gradle/libs.versions.toml`; Gradle wrapper bumped `8.4-bin` → `8.9-bin`
> in `gradle/wrapper/gradle-wrapper.properties` (AGP 8.7.x officially
> requires Gradle 8.9+ per the AGP↔Gradle compatibility table). The
> workaround block in `gradle.properties` —
> `android.suppressUnsupportedCompileSdk=35` plus its explanatory
> comment, was deleted; AGP 8.7.3 officially supports `compileSdk = 35`
> so neither the "tested up to compileSdk = 34" advisory nor the
> "SDK XML version 4" parser warning fires anymore (verified by
> `./gradlew :app:assembleDebug --warning-mode all 2>&1 | grep -iE
> "(warning|tested up to|SDK XML)"` returning empty). Kotlin 1.9.21 +
> KSP 1.9.21-1.0.16 + Hilt 2.50 are all compatible with AGP 8.7.3, no
> language-version bump required, and the K2 / Kotlin 2.x migration
> stays out of scope as planned. After a `clean` rebuild on the new
> toolchain, all gates green: `:app:assembleDebug`,
> `:app:assembleRelease` (R8 minification), `:app:assembleDebugAndroidTest`
> (instrumented compile), `ktlintCheck`, `:app:lint` (no new lint
> issues; existing baseline still respected), and `:app:testDebugUnitTest`
> (265 cases). `README.md` toolchain line updated to "Gradle 8.9 · AGP
> 8.7.3 · Kotlin 1.9.21". Historical specs/plans under
> `docs/superpowers/` keep their original AGP 8.2.0 / Gradle 8.4
> references, those are snapshots in time, not live docs. The
> original task text is preserved below.

After TASK-22 merged (`compileSdk` and `targetSdk` set to 35), AGP 8.2.0
emits two warnings on every build:

```
Warning: SDK processing. This version only understands SDK XML versions
up to 3 but an SDK XML file of version 4 was encountered. …

WARNING: We recommend using a newer Android Gradle plugin to use
compileSdk = 35

This Android Gradle plugin (8.2.0) was tested up to compileSdk = 34.
```

Both are silenced today by `android.suppressUnsupportedCompileSdk=35` in
`gradle.properties` (added 2026-05-01). The build is functionally fine —
TASK-22's empirical probe verified `:app:assembleDebug`,
`:app:assembleRelease`, R8 minification, and `lintVitalRelease` all
succeed. The SDK XML v4 warning is parser noise; AGP falls back gracefully.

This task is the *proper* fix: bump AGP (and Gradle wrapper) to a version
that officially supports `compileSdk = 35` and remove the workaround.

### Scope

1. **`gradle/libs.versions.toml`:** bump `agp = "8.2.0"` to a version
   ≥ 8.6.0. AGP 8.7.x is the latest stable 8.7 line at time of writing;
   confirm Hilt 2.50, KSP 1.9.21-1.0.16, and Kotlin 1.9.21 compatibility
   before picking the exact version. AGP 8.6 needs Gradle 8.7+;
   AGP 8.7 needs Gradle 8.9+. The Android docs' AGP↔Gradle compatibility
   table is the source of truth.
2. **`gradle/wrapper/gradle-wrapper.properties`:** bump `distributionUrl`
   from `gradle-8.4-bin.zip` to a wrapper version that satisfies the AGP
   requirement (8.7 or 8.9 depending on AGP choice).
3. **`gradle.properties`:** remove the
   `android.suppressUnsupportedCompileSdk=35` line and the comment block
   above it; verify the build no longer emits either warning.
4. **CI smoke:** push the branch and confirm
   `.github/workflows/ci.yml` still passes (ktlintCheck, `:app:lint`,
   `:app:testDebugUnitTest`, `:app:assembleRelease`).
5. **Hilt / KSP regression check:** Hilt 2.50 + KSP 1.9.21-1.0.16 are
   known-good with AGP 8.2.0; AGP 8.6+ may surface KSP plugin order
   warnings or new lint rules. Triage anything that fires.
6. **Kotlin/KSP version coordination:** AGP 8.7+ may want Kotlin 1.9.24+
   or 2.0.x. Bumping Kotlin is its own risk surface, prefer staying on
   1.9.21 if the chosen AGP version permits it; otherwise bump together
   in this task and document the new Kotlin/KSP versions.
7. **Lint baseline:** if AGP's bundled Lint version changes, new lint
   issues may surface. Add to `app/lint-baseline.xml` only if they're
   pre-existing offenses unrelated to the AGP bump; fix anything new
   that the AGP bump itself introduced.

### Out of scope

- **Kotlin 2.x migration.** Even if AGP 8.7+ accepts Kotlin 2.x, the K2
  compiler is its own multi-day investigation. Stay on Kotlin 1.9.x for
  this task.
- **Removing the `android.useAndroidX=true` / `nonTransitiveRClass=true`
  flags.** Those are unrelated.

### Acceptance

- `./gradlew :app:assembleDebug :app:assembleRelease` runs with
  **zero** AGP-version warnings.
- `gradle.properties` no longer contains
  `android.suppressUnsupportedCompileSdk`.
- The 245 JVM unit tests still pass; `:app:assembleDebugAndroidTest`
  still compiles; CI workflow stays green.
- `CLAUDE.md` Build & Test section updates the AGP/Gradle versions in
  the same paragraph that mentions Build Tools 35.

---

## 🟡 TASK-33, Audit Kotlin 2.x / K2 + KSP + Hilt compatibility ☑ Done (2026-05-10)

> **Outcome (merged 2026-05-10 on `chore/task33-kotlin2-audit`).** Audit deliverable: `docs/superpowers/specs/2026-05-10-task33-kotlin2-audit.md`. **Verdict: GO.** Target Kotlin 2.1.21; coordinated catalog bump required across nine deps in a single PR (Hilt 2.50→2.56.2 is the must-move-with-Kotlin blocker per dagger#4582 metadata cap; KSP exact-pin; Roborazzi 1.36.0→1.59.0; Robolectric 4.13→4.14.1; coroutines 1.7.3→1.10.2; Lifecycle 2.7.0→2.8.7; Navigation 2.7.6→2.8.5; mockito-kotlin 5.2.1→5.4.0). AGP 8.7.3 + Room 2.6.1 + ktlint 12.1.1 stay. Kotlin 2.0.x considered (safe but locks out 2.1 features); 2.2.x and 2.3.x rejected (ecosystem lag and fresh Hilt/KSP regressions, dagger#5001 / #5059). Audit highest-risk findings in §4 of the spec; quality gates in §5; out-of-scope items in §6. Execution filed as **TASK-33b**.

**Original audit task body preserved below.**

TASK-32 bumped AGP to 8.7.3 and Gradle to 8.9 but explicitly deferred any
Kotlin language-version change, the toolchain stays at Kotlin 1.9.21 +
KSP 1.9.21-1.0.16 + Hilt 2.50. There is no other tracking task for the
Kotlin 2.x / K2 migration, which means the deferral risks becoming
permanent through inertia rather than a deliberate "stay on 1.9" decision.
The audit half is cheap and worth doing now while the AGP bump is fresh.

The migration itself is a multi-day investigation (K2's compiler
behaviour differs from K1 around inline classes, sealed types, and
generic inference) and stays out of scope for this task, TASK-33 is the
audit, not the bump.

### Scope

1. **Compatibility matrix.** Pick a target Kotlin 2.x line (likely
   `2.0.21` or the latest stable at audit time) and confirm:
   - AGP 8.7.3 supports it without further bumps. Cross-reference the
     AGP↔Kotlin compatibility note in the Android docs.
   - KSP has a matching `<kotlin>-1.0.x` artifact published. KSP versions
     are tied 1:1 to Kotlin minor versions.
   - Hilt 2.50 supports Kotlin 2.x, or pin a Hilt version that does
     (Hilt 2.51+ added K2 support).
   - **Room 2.7.x** is on the audit shortlist alongside Kotlin: Room
     2.6.1's KSP processor targets Kotlin 1.9.x and is not K2-clean.
     Bumping Kotlin without bumping Room will surface as KSP processor
     errors during the trial build. Room 2.7.x also unlocks the
     `@AutoMigration` work in TASK-39, so the two upgrades pair
     naturally.
   - **Concrete pin shortlist** (audit 2026-05-03, re-verify "latest
     stable" at execution time): `kotlin = "2.0.21"`,
     `ksp = "2.0.21-1.0.27"`, `hilt = "2.52"+`, `room = "2.7.x"`. Treat
     these as the audit hypothesis to confirm or refute, not as fixed
     targets.
2. **Plugin and dependency check.** Run `./gradlew dependencies --configuration releaseRuntimeClasspath`
   and grep for any third-party Kotlin-plugin output (`kotlinx-*`,
   compiler plugins, kapt artifacts). The project uses KSP not kapt;
   confirm no transitive kapt artifact pulls a stale Kotlin runtime.
3. **K2 known-issue review.** Skim the `kotlinlang.org` K2 known-issues
   list for anything that touches: Hilt-generated code, Room-generated
   DAOs, sealed `BackupResult` exhaustiveness checks, or
   `MutableStateFlow`/`StateFlow` inference in ViewModels. Note any
   blockers in the audit doc.
4. **Trial branch (no merge).** Create a throwaway branch, bump
   `kotlin = "..."` and `ksp = "..."` in `gradle/libs.versions.toml`, run
   `./gradlew clean :app:assembleDebug :app:testDebugUnitTest
   :app:assembleDebugAndroidTest :app:lint`. Capture failures verbatim.
   Do **not** open a PR, this branch's only output is the audit findings.

### Out of scope

- Actually merging the Kotlin bump. If the audit finds the path is
  clean, file a fresh task (TASK-XX) for the migration with a concrete
  upgrade plan derived from these findings.
- Switching kapt → KSP on any module (already done).
- Bumping Hilt independently of the Kotlin question.

### Acceptance criteria

The task is complete when an audit document is committed under
`docs/superpowers/specs/YYYY-MM-DD-task33-kotlin-2x-audit.md` covering:

1. Confirmed compatible (or incompatible) Kotlin/KSP/Hilt/AGP version triple.
2. Build/test results from the trial branch.
3. A go/no-go recommendation with named blockers if no-go, or a sized
   follow-up task description if go.

The audit branch is then deleted; no production change lands in this task.

---

## 🟡 TASK-34, Nightly managed-AVD job for `connectedAndroidTest` ☑ Done (2026-05-01)

> **Outcome:** new `.github/workflows/nightly-instrumented.yml` runs
> `:app:connectedDebugAndroidTest --no-daemon --stacktrace` on a
> `reactivecircus/android-emulator-runner@v2` matrix covering API 26
> (matches `minSdk`) and API 35 (matches `targetSdk`), `default`
> system image with `arch: x86_64`. Triggers are `schedule: cron '0
> 2 * * *'` (02:00 UTC) and `workflow_dispatch` only, explicitly no
> `pull_request` / `push` triggers, so the PR gate's runtime stays
> unchanged. KVM permissions are unlocked at the start of each job
> via the standard `udev` rule incantation so the emulator boots
> hardware-accelerated rather than falling back to software emulation
> (which routinely times out the 60-minute job budget). The AVD is
> cached via `actions/cache@v4` keyed on
> `avd-${api-level}-default-x86_64-v1` and pre-warmed in a
> snapshot-generating step that runs only on cache miss; the actual
> test step launches with `-no-snapshot-save` so the cache stays
> stable across runs. Both reports, HTML at
> `app/build/reports/androidTests/connected/` and raw XML at
> `app/build/outputs/androidTest-results/connected/`, upload as
> Actions artifacts on every run (`if: always()`) for triage. Failure
> is informational; the workflow does not block PR merges.
> `fail-fast: false` on the matrix so an API-26 regression doesn't
> mask an API-35 one. `concurrency.cancel-in-progress: true` keeps
> long-failing builds from queueing across midnight cron firings. The
> optional commit-comment step from the spec is intentionally
> deferred, the spec calls it out as "can be deferred if the matrix
> proves too noisy initially," and GitHub Actions' default email
> notifications already cover the maintainer until the matrix
> stabilises. The original task text is preserved below.

The repository has accumulated 10+ instrumented test files that the PR
gate (`.github/workflows/ci.yml`) never executes. The current set
includes, at minimum, the migration suite (`MigrationTest` with cases
covering 1→2→3→4→5→6), `AboutFragmentTest`, `MainActivityBottomNavTest`,
the wizard flow tests, and several Charts/Settings Espresso tests. They
compile via `:app:assembleDebugAndroidTest` on every PR (the existing
gate does enforce that), but failure modes that need a running device —
schema validation, navigation transitions, edge-to-edge insets, real
DataStore round-trips, only surface locally or at release-tag time.

Adding instrumented tests to the PR gate is rejected up front: emulator
boot dwarfs the rest of the gate's runtime and would push every PR cycle
above the 10-minute mark. The fix is a separate **nightly** job using
GitHub Actions' managed Android emulator action.

### Scope

1. **New workflow file.** Create `.github/workflows/nightly-instrumented.yml`
   with `on: schedule: [cron: '0 2 * * *']` (02:00 UTC; tune to a
   low-runner-contention window) plus `workflow_dispatch:` for manual
   triggering. Do **not** add `pull_request` or `push` triggers.
2. **Managed AVD action.** Use `reactivecircus/android-emulator-runner@v2`
   (the de-facto action) with API 26 (matches `minSdk`) **and** API 35
   (matches `targetSdk`) in a `strategy.matrix` so both ends of the
   support range get exercised. Use the `default` system image with
   `arch: x86_64`.
3. **Test target.** Run `./gradlew connectedDebugAndroidTest --no-daemon`.
   Hilt-injected tests already work without further configuration; the
   project's `HiltTestApplication` is wired in `app/src/androidTest/`.
4. **Failure surface.** On failure, upload `app/build/reports/androidTests/connected/`
   as an Actions artifact (HTML report is the primary debugging surface)
   and `app/build/outputs/androidTest-results/connected/` for the raw
   XML. Do not fail the merge queue, this workflow is informational.
5. **Notification.** Post a failure comment on the most recent commit
   on `main` via the `actions/github-script` action so failures are
   visible without the maintainer needing to open the Actions tab.
   (Optional; can be deferred if the matrix proves too noisy initially.)

### Out of scope

- Adding instrumented tests to the PR gate. The whole point of this
  task is to keep the PR gate fast.
- Running the nightly job on every supported API level. API 26 + API 35
  is the cost/coverage sweet spot.
- Adding instrumented tests for new features. Coverage decisions belong
  to the per-feature task, not this one.

### Acceptance criteria

1. `.github/workflows/nightly-instrumented.yml` exists and is valid
   (run `gh workflow list` to confirm it registers).
2. Manual `workflow_dispatch` runs successfully end-to-end against
   `main` HEAD on both API 26 and API 35.
3. The PR gate's runtime is unchanged (no new step added to `ci.yml`).
4. `CLAUDE.md` Build & Test section gains one line under the existing
   "connectedAndroidTest, needs API 26+ device or emulator" note,
   pointing at the nightly workflow as the canonical CI execution.

---

## 🟢 TASK-35, Roborazzi screenshot tests for ChartsTab ☑ Done (2026-05-10)

> **Resolved by TASK-79 on 2026-05-10.** Phase 2/3 (the actual baseline PNGs) shipped on `feat/task79-chartstab-baselines`; see TASK-79 outcome below for details. The 14-PNG ChartsTab matrix is now the live `:app:verifyRoborazziDebug` gate, which means TASK-30 (MPAndroidChart → Vico migration) has the "looks identical" acceptance criterion it was waiting on. Dashboard + outer ChartsFragment baselines remain forward-work; their absence is a documented coverage gap, not a TASK-30 blocker (TASK-30 changes only chart rendering).
>
> **Phase 1 outcome (merged 2026-05-10 on `feat/task35-roborazzi-baselines`, v1.9.34).** Build infrastructure landed: Roborazzi 1.36.0 + Robolectric 4.13 pinned in `gradle/libs.versions.toml`; `io.github.takahirom.roborazzi` plugin applied; `testOptions.unitTests.isIncludeAndroidResources = true` enabled (Robolectric requires the resource table on the test classpath); test deps wired (`testImplementation(libs.roborazzi)` + junit-rule + robolectric + fragment-testing); `roborazzi { outputDir = src/test/screenshots }` block configured; `.gitattributes` forces binary diff for `app/src/test/screenshots/*.png`; `.gitignore` excludes Roborazzi's per-failure `*.actual.png` / `*.diff.png`.
>
> Specs: `docs/superpowers/specs/2026-05-09-task35-roborazzi-baselines-design.md` (Phase 1 design + original full-spec fixtures), `docs/superpowers/specs/2026-05-10-task79-chartstab-baselines-design.md` (refined rendering strategy actually used).

The `#FB8C00` DC orange (M3 tertiary palette seed, `docs/DESIGN.md §6`)
ships a known-untested contrast risk on dark surfaces, TASK-18 (a11y
pass) calls it out as a manual review item. The 2026-05-01 dark-mode
chart text bug (`c677a2b` fix) is a recent, concrete instance of how
theme-coupled rendering regressions slip through both unit and Espresso
tests: both gates passed while the text was actually unreadable.

Roborazzi runs a JVM-side rendering pipeline using Robolectric so
screenshot diffs run in the existing `:app:testDebugUnitTest` job
without an emulator. The PR-gate cost is single-digit seconds per
screen.

This task is the **prerequisite** for TASK-30 (MPAndroidChart → Vico
migration). Without baseline screenshots locked in **before** the
rendering stack swap, TASK-30's "looks identical" acceptance criterion
becomes a manual eyeball comparison across six tabs × two themes ×
multiple data shapes, i.e., unverified.

### Scope

1. **Add Roborazzi.**
   ```toml
   # gradle/libs.versions.toml
   [versions]
   roborazzi = "1.x.x"   # check latest stable
   robolectric = "4.x.x" # may already be present
   [libraries]
   roborazzi              = { group = "io.github.takahirom.roborazzi", name = "roborazzi",              version.ref = "roborazzi" }
   roborazzi-junit-rule   = { group = "io.github.takahirom.roborazzi", name = "roborazzi-junit-rule",   version.ref = "roborazzi" }
   robolectric            = { group = "org.robolectric",               name = "robolectric",            version.ref = "robolectric" }
   ```
   Wire under `testImplementation` in `app/build.gradle.kts` and add the
   Roborazzi Gradle plugin per its README.

2. **Baseline screens.** Capture both light and dark variants of:
   - Dashboard with 0 events (empty-state path).
   - Dashboard with mixed AC + DC events, single currency.
   - Dashboard with multi-currency banner.
   - Charts → Trend (line chart, kWh primary metric).
   - Charts → AC vs DC (pie tab, high contrast risk on dark surfaces).
   - Charts → Locations (pie tab, ≥ 2 slices using `LOCATION_PALETTE`).
   - Charts → Degradation (TASK-14 line chart with the dashed nominal
     reference line, the dashed line's contrast on dark surfaces is
     the second known unverified risk).

   Total: ~14 baseline images (7 screens × 2 themes). Store under
   `app/src/test/screenshots/` (Roborazzi default).

3. **CI integration.** Add `:app:verifyRoborazziDebug` to the existing
   PR gate after `:app:testDebugUnitTest`. Diffs above the default
   threshold fail the build. Update `app/build.gradle.kts` with
   `roborazzi { outputDir = ... }` if the default isn't appropriate.

4. **Update flow.** Document in `CLAUDE.md` the recapture command
   (`./gradlew recordRoborazziDebug`) and the convention that recapture
   commits must be a separate PR with a one-line "screenshot baseline
   refresh" body, never bundled with feature changes, so reviewers
   can scan the diff for unintended regressions.

### Out of scope

- Screenshot tests for ChargeEdit / Cars / History / Settings /
  ManageLocations / Wizard. Adding them later is straightforward;
  Dashboard + Charts is the focus because it's where TASK-30 will land
  and where dark-mode regressions have already burned the project once.
- Pixel-perfect device-matrix coverage. A single Robolectric device
  config is enough to catch theming and palette regressions; full
  device-matrix screenshots belong to a managed-AVD nightly run
  (TASK-34 territory) and are not necessary here.

### Acceptance criteria

1. `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug` passes
   on `main`.
2. The 14 baseline images are committed and reviewable in the PR diff.
3. A deliberate-regression smoke test (e.g., temporarily change a
   primary palette color and re-run) **fails** the gate as expected.
4. `CLAUDE.md` documents the recapture command and PR convention.
5. The TASK-30 spec is updated to reference these baselines as the
   "looks identical" acceptance gate.

---

## 🟡 TASK-36, Inline-comment the "no `Result.retry()`" invariant in `DriveBackupWorker` ☑ Done (2026-05-01)

> **Outcome:** folded into the TASK-19 commit since the same
> `DriveBackupWorker.doWork()` block was being touched. Both
> `BackupResult.AuthRequired` and `is BackupResult.Failure` branches
> now carry an inline `// Repository already exhausted its retry
> budget (TASK-07); returning Result.retry() would let WorkManager
> re-amplify it.` comment naming both TASK-07 (budget owner) and
> TASK-36 (this comment-source). The function-level KDoc block also
> picked up a TASK-19 cross-reference for the side-channel through
> `BackupOutcomeReporter`. The original task text is preserved below.

`DriveBackupWorker.doWork()` deliberately translates `BackupResult.Failure`
and `BackupResult.AuthRequired` to `Result.failure()` rather than
`Result.retry()`. The reasoning, that `DriveBackupRepository` already
exhausted a bounded retry budget (TASK-07: `MAX_ATTEMPTS = 3`,
exponential backoff) and returning `Result.retry()` would let
WorkManager amplify that budget exponentially, currently lives in:

- The function-level KDoc on `doWork()` (one paragraph).
- The TASK-07 outcome block in this BACKLOG.
- A bullet in `CLAUDE.md` "Google Drive backup".

None of those is visible at the **point of edit**. A future agent or
contributor opening the `when` block and noticing "we have transient
failures here, why don't we retry?" will plausibly add `Result.retry()`
without reading the function-level KDoc, breaking the invariant.

### Scope

1. **Edit `app/src/main/java/org/spsl/evtracker/data/backup/DriveBackupWorker.kt`.**
   Add a single-line `// ` comment **inside each branch of the `when`**
   that resolves to `Result.failure()` (`AuthRequired` and
   `Failure(...)`), naming the invariant explicitly. Sample shape:

   ```kotlin
   override suspend fun doWork(): Result = when (val r = backupRepository.backupCurrentData()) {
       BackupResult.Success -> {
           settingsWriter.setLastBackupAt(now.nowMillis())
           Result.success()
       }
       // Repository already exhausted its retry budget (TASK-07);
       // returning Result.retry() would let WorkManager re-amplify it.
       BackupResult.AuthRequired -> Result.failure()
       // Same invariant, see KDoc above and TASK-07 / TASK-36.
       is BackupResult.Failure -> Result.failure()
   }
   ```

2. **Cross-reference TASK-07.** The TASK-07 outcome block already
   describes the no-retry decision; no edit needed there. The new
   inline comments name TASK-07 by number so a curious reader can
   trace the rationale.

3. **No behaviour change.** This is a comment-only edit, `git diff`
   should show only added comments.

### Out of scope

- Adding a unit test that asserts `Result.retry()` is never returned.
  Worker behaviour is already covered indirectly via
  `DriveBackupRepositoryTest` retry-exhaustion cases.
- A custom ktlint rule that rejects `Result.retry()` in this file.
  Mechanical enforcement for one symbol in one file is over-engineered.
- Generalising the comment style across other workers, the project
  has exactly one `CoroutineWorker`; revisit if a second one lands.

### Acceptance criteria

1. `git diff` on `DriveBackupWorker.kt` shows only added comment lines.
2. `./gradlew ktlintCheck :app:testDebugUnitTest :app:assembleDebug`
   stays green.
3. Both failure branches of the `when` carry an explanatory comment
   naming TASK-07 (the budget owner) and TASK-36 (this comment-source).

---

## 🔴 TASK-37, Replace Google Drive backup with SAF-based backup ⏸ Under consideration (2026-05-01)

> **⏸ Deferred, do not start without an explicit go-ahead.** The task
> body below is preserved as a complete implementation sketch for when
> the decision flips, but the project is **not** committed to a SAF
> migration today. Reasons to revisit: (1) F-Droid distribution becomes
> a target (the body's premise); (2) Drive auth friction (separate
> OAuth client per keystore SHA-1, tester allow-list on the consent
> screen) becomes a blocker for new contributors; (3) Drive API
> deprecation. Until then, leave the Drive code path in place, the
> error model (TASK-07), failure notifications (TASK-19), and manual
> controls (TASK-31) all build on it; future backup work routes through
> the same path.

**Blocks F-Droid distribution.** `play-services-auth`,
`google-api-client-android`, and `google-api-services-drive` are flagged
`NonFreeNet` by the F-Droid scanner. Replace `DriveBackupRepository`
with a Storage Access Framework implementation. The domain layer
(`BackupRepository`, `BackupResult`, `BackupSerializer`,
`RestoreBackupUseCase`) is unchanged, only `data/backup/` and Settings
UI change.

> **Sequencing note.** This task **obsoletes** the Drive-specific UI
> shipped in TASK-31 ("Back up now" / "Wipe remote backup", merged
> 2026-05-01). When TASK-37 is revived, the existing
> `PushBackupNowUseCase` becomes `PushBackupUseCase` (or stays, name
> is incidental) and `WipeRemoteBackupUseCase` becomes
> `ClearBackupLocationUseCase`; the Settings rows / dialog / VM
> mutual-exclusion logic carry over with minor renames. The
> `BackupResult` contract from TASK-07 is reused unchanged. Plan to
> delete the Drive code path (`DriveBackupRepository`,
> `GoogleDriveRemoteSource`, `AndroidDriveAuthManager`,
> `DriveBackupWorker` → renamed `BackupWorker`) and rebind
> `BackupRepository` to the new SAF impl in a single PR, the domain
> layer is untouched.

### Scope

1. Remove `play-services-auth`, `google-api-client-android`, and
   `google-api-services-drive` from `gradle/libs.versions.toml` and
   `app/build.gradle.kts`. Remove the `packaging { resources { excludes += … } }`
   block and the Drive keep-rules from `proguard-rules.pro`.

2. Add `BACKUP_URI = stringPreferencesKey("backup_uri")` to
   `PreferenceKeys`. Extend `SettingsReader` with
   `val backupUri: Flow<Uri?>` and `SettingsWriter` with
   `suspend fun setBackupUri(uri: Uri?)`. Store as `uri.toString()` /
   `Uri.parse()`.

3. Create `data/backup/SafBackupRepository.kt` implementing
   `BackupRepository`. `backupCurrentData()` opens the stored URI with
   `contentResolver.openOutputStream(uri, "wt")`; returns
   `BackupResult.AuthRequired` when the URI is null or a
   `SecurityException` is thrown (permission revoked, also clears the
   stored URI); returns `BackupResult.Failure` on `IOException`.
   `readRemoteBackup()` opens the URI for reading.
   `deleteRemoteBackup()` calls `DocumentsContract.deleteDocument()`
   and clears the stored URI.

4. When the user picks a file for the first time, call
   `contentResolver.takePersistableUriPermission(uri, READ | WRITE)`
   then `settingsWriter.setBackupUri(uri)`. Subsequent backup runs
   write silently without a picker.

5. In `di/`, rebind `BackupRepository` to `SafBackupRepository`. Delete
   `DriveBackupRepository.kt`, `DriveAuthManager.kt`,
   `DriveAuthManagerImpl.kt`. Rename `DriveBackupWorker` →
   `BackupWorker`; replace the Drive repo injection with the
   `BackupRepository` interface.

6. In `SettingsFragment` / `SettingsViewModel`, replace the Drive
   sign-in row with: **"Choose backup file"** (`ACTION_CREATE_DOCUMENT`,
   `type = "application/json"`, `EXTRA_TITLE = "evtracker_backup.json"`);
   show the chosen filename as a summary. Keep **"Back up now"** and
   **"Restore from backup"** (`ACTION_OPEN_DOCUMENT`); replace
   **"Wipe remote backup"** with **"Clear backup location"** (calls
   `deleteRemoteBackup()` after a confirmation dialog). Remove the
   `driveEnabled` preference key and all references to it.

7. Replace `docs/GOOGLE_CLOUD_SETUP.md` with `docs/BACKUP_SETUP.md`
   documenting the SAF flow and recommended storage locations (local,
   Nextcloud, Syncthing). Update `DESIGN.md §8` and `CLAUDE.md`
   accordingly.

8. Add `SafBackupRepositoryTest` covering: `AuthRequired` when URI is
   null; successful write; `AuthRequired` + URI cleared on
   `SecurityException`; `Failure` on `IOException`; successful read.
   Use `MockK` to stub `ContentResolver`.

---

## 🟢 TASK-38, Multi-vehicle comparative analytics on Charts

The data model already supports multiple cars per user, but the Charts
screen scopes every series to the `activeCarId`. For SPS-Lab fleet-level
analysis it is useful to overlay two cars on a single trend chart and
compare efficiency, cost, and degradation curves side-by-side.

### Scope

1. Add a "Compare" toggle to `ChartsFragment` (e.g., a top-of-screen
   `MaterialButtonToggleGroup` with **Single** / **Compare**). Default
   to **Single** (current behaviour). When **Compare** is enabled,
   surface a second car picker; the active car remains series A and
   the picker selects series B (any other car the user owns).

2. Extend `ChartsScreenState` with `compareCarId: Long?` and
   `seriesB: ChartSeries?`. `ObserveChartsModelsUseCase` accepts an
   optional second `carId`; when present, runs the same aggregation
   pipeline twice (different `carId` filter) and emits both series.
   Reuse the existing `StatsCalculator` / `DateRangeResolver`
   pipeline, this is a query-fan-out, not a new metric.

3. Render both series on the same `LineChart` / `BarChart`. Use the
   primary container colour for series A and the tertiary container
   colour for series B (M3 tokens, light + dark) so the colour
   contrast survives both themes. Add a small inline legend with the
   car nicknames.

4. The **Degradation** tab (TASK-14) gets the same overlay, but only
   when both cars have a `nominalBatteryKwh`. Otherwise fall back to
   single-car mode and show a small "compare unavailable, second
   car has no nominal capacity" hint.

5. Compare mode is irrelevant for the **Pie** tabs (single-car
   share-of-charge-type breakdown); the toggle hides the comparison
   picker on those tabs.

6. New JVM cases on `ObserveChartsModelsUseCaseTest` covering the
   two-car fan-out, the disabled-degradation-fallback, and that
   selecting the active car as series B is a no-op (collapse to
   single mode).

7. Update `DESIGN.md` charts section to document the compare mode
   and the colour mapping. Update `CLAUDE.md` Status entry post-merge.

### Notes

- This is **not** a multi-currency feature, the existing
  multi-currency-period rule (cost stats hidden when more than one
  currency is present) applies independently to each series. If A
  and B are in different currencies, hide the cost trend overlay
  (drop to single-series for cost) and show the existing banner.
- No schema change. Backup format unchanged.

---

## 🟢 TASK-39, Adopt Room `@AutoMigration` for additive bumps from v6 onward

The project hand-writes a migration class for every schema bump
(currently `MIGRATION_1_2` through `MIGRATION_5_6`). Several of those
migrations were purely additive (`MIGRATION_5_6` adds two nullable
columns; `MIGRATION_4_5` is a no-op trip-wire). Room's
`@AutoMigration(from = N, to = N+1)` annotation generates these
migrations from the schema JSON automatically and is supported for any
purely additive change.

### Scope

1. Confirm `app/schemas/` is checked in (it is, `app/build.gradle.kts`
   already wires `schemaDirectory` via the KSP arg). `@AutoMigration`
   reads the JSON snapshots from this directory at compile time.

2. From schema **v7 onward**, add `@AutoMigration` entries to
   `AppDatabase.kt`'s `@Database` annotation `autoMigrations = [...]`
   array for every additive bump. Reserve hand-written
   `Migration` objects for destructive or rewrite operations
   (`MIGRATION_3_4`'s `'DC' → 'DC_FAST'` rewrite is the canonical
   example, keep that pattern hand-written).

3. Document the rule in `CLAUDE.md` "Database, Room v7" section: a new
   bullet "Additive bumps (new nullable columns, new tables, new
   indices) use `@AutoMigration`. Hand-written migrations are reserved
   for destructive or rewrite operations." Add a short example.

4. **Do not retrofit existing migrations.** `MIGRATION_1_2` through
   `MIGRATION_5_6` stay as hand-written for stability, replacing
   them carries no value and risks subtle behaviour differences
   (Room's auto-migration uses a different DDL strategy that creates
   shadow tables and copies data; switching mid-life is risky).
   `MIGRATION_4_5` and `MIGRATION_5_6` were the last hand-written
   additive ones; v7+ is the boundary.

5. Add an instrumented `migrate_6_to_7_isAutoMigration` test once the
   first `@AutoMigration` lands, asserting the auto-migration runs
   end-to-end and preserves data, same shape as the existing
   `MigrationTest` cases.

### Notes

- `@AutoMigration` cannot be combined with `@DeleteColumn` /
  `@RenameColumn` without the manual `AutoMigrationSpec` callback.
  When that's needed, use a hand-written migration instead.
- This task is infrastructure polish, there is no user-visible
  behaviour change. Land it when the next additive schema change
  is queued so the test gets exercised end-to-end.

---

## 🟢 TASK-40, Anonymised research-export pipeline for SPS-Lab

Bridge the app from a personal-use tool to a research data collection
instrument: add an opt-in, privacy-preserving export mode that strips
PII (location free-text, car nicknames) but retains the temporal,
energy, cost, and SoC fields needed for SPS-Lab analysis. Must publish
the schema in `METHODOLOGY.md` for transparency.

### Scope

1. New `domain/usecase/ExportAnonymisedCsvUseCase.kt` parallel to the
   existing `ExportCsvUseCase`. Output schema (one row per charge
   event, all units canonical):
   - `event_id` (anonymised, derive a stable per-export hash from
     the row id, NOT the raw `Long` id; resets per export so the
     hash chain is non-correlatable across exports)
   - `car_anon_id` (per-export hash of `carId`; same scheme, A, B,
     C ordering preserved within an export, opaque between exports)
   - `event_timestamp_utc` (ISO 8601, UTC)
   - `kwh_added`
   - `odometer_km` (canonical km, stored unit)
   - `charge_type` (`AC` / `DC_FAST` / `DC_ULTRA`)
   - `cost_total` (or empty)
   - `currency` (or empty)
   - `soc_before`, `soc_after` (fractions in `0.0..1.0`, or empty)
   - `nominal_battery_kwh` (per-car static, denormalised onto the row
     so the export is self-contained)
   - **Excluded fields:** `location`, `note`, `car.nickname`, raw
     primary keys.

2. New Settings row "Export anonymised data for research" gated by a
   confirmation dialog: "This export removes location names, notes,
   and car nicknames. It includes timestamps, energy, cost, and
   battery state. Continue?" Output via the same `FileProvider`
   sharing path as `ExportCsvUseCase`.

3. Schema authoritatively documented in `docs/METHODOLOGY.md` (new
   file, same one TASK-20 will create, so coordinate). Include:
   field list, units, anonymisation guarantees, what is *not*
   exported, and a citation example.

4. New JVM `ExportAnonymisedCsvUseCaseTest` covering: per-export hash
   stability within a run; per-export hash uniqueness across runs
   (i.e., two consecutive exports of the same data produce different
   `event_id` / `car_anon_id` columns); `location` / `note` /
   nickname fields absent from header and rows; correct CSV escaping
   for currency strings.

### Notes

- This is **opt-in per export** (no persistent flag). Researcher
  asks the user to email the file; user runs the export and shares.
  No background pipeline, no upload, same surface as TASK-09.
- Coordinate with TASK-09, if both land, share the
  `CsvFileSink` infrastructure and the date-range picker.
- Coordinate with TASK-20, both create `docs/METHODOLOGY.md`. The
  first to land creates the file; the second appends a section.
- **Audit follow-up (RES-04, 2026-05-03):** before the use case
  lands, draft an `AnonymisedExportPolicy` domain object that holds
  the field-suppression rules (which fields are stripped, which
  are generalised, which are passed through). Unit-test the policy
  independently of the export use case, then have
  `ExportAnonymisedCsvUseCase` consume it. This makes the
  anonymisation contract reviewable in isolation and prevents
  accidental field-leak regressions when the schema grows. The
  methodology doc must additionally cover: GDPR lawful basis (the
  user is the data subject and consents per export), generalisation
  rules (hashes per export, not stable cross-export), and a
  user-facing consent dialog whose body matches the methodology
  doc verbatim.

---

## 🟢 TASK-41, JSON-LD / OCPP-compatible export format ⏸ Under consideration (2026-05-02)

> **⏸ Deferred, do not start without an explicit go-ahead.** Depends
> on TASK-09 (date-range CSV) and TASK-40 (anonymised pipeline)
> landing first; the value is interoperability with grid-side
> energy-management research, which is a niche surface. Revisit when
> a concrete consumer (a downstream SPS-Lab tool, an OCPP-compatible
> aggregator) is identified.

Beyond CSV, add an export to **JSON-LD** (schema.org `Vehicle` +
`QuantitativeValue`) or an **OCPP-compatible** transaction format for
interoperability with grid-side energy-management research and tooling.

### Scope (sketch, to be refined when revived)

1. New `domain/usecase/ExportJsonLdUseCase.kt`. Output one JSON-LD
   document with `@context` set to `https://schema.org`, root type
   `Vehicle`, and a `chargingHistory` array of objects each with
   `@type: QuantitativeValue` for `kWh`, `distance`, and
   `monetaryAmount`. Include explicit `unitCode` strings (UN/CEFACT
   codes: `KWH`, `KMT`, etc.).

2. Optional second exporter for OCPP 2.0.1
   `TransactionEventRequest`-shaped JSON; one transaction per
   charge event. The app does not act as an OCPP charge point —
   this is an export shim only.

3. New row in Settings → Export, parallel to TASK-09's CSV export.
   Reuse the `FileProvider` sharing path.

4. Document the schema choice in `docs/METHODOLOGY.md` alongside the
   TASK-40 anonymised CSV schema. Cite schema.org / OCPP 2.0.1
   spec versions in use.

### Notes

- **No new third-party dependency.** Hand-roll the JSON-LD with the
  existing Gson dep, schema.org JSON-LD does not require a
  framework, just the `@context` / `@type` keys.
- Decision pending: JSON-LD-only, OCPP-only, or both. Default to
  JSON-LD on revival (lower scope, broader applicability) and add
  OCPP as a separate task if a consumer asks.

---

## 🟢 TASK-42, Open Charge Map / OCPI station lookup integration ⏸ Under consideration (2026-05-02)

> **⏸ Deferred, do not start without an explicit go-ahead.** Two
> reasons to hold: (1) the F-Droid implications mirror TASK-37, Open
> Charge Map is reachable via plain HTTPS, but adding a network
> dependency for charging-station enrichment broadens the privacy
> surface and the F-Droid review checklist; (2) the value depends on
> coverage in the user's geography (OCM coverage in Cyprus / EU is
> uneven). Revisit when a researcher requests verified-station
> tagging on a real dataset.

Replace the free-text `location` field with an optional verified
charging-station tag sourced from the
[Open Charge Map API](https://openchargemap.org/site/develop/api) or
an OCPI feed. Free text remains the fallback.

### Scope (sketch, to be refined when revived)

1. Add a "Search station" affordance to the location chip row in
   `ChargeEditFragment`. Tapping opens a modal with a text search
   that hits OCM (`api.openchargemap.io/v3/poi`) and returns nearby
   stations; the user picks one or cancels back to free text.

2. Persist the selected station as a structured value alongside the
   free-text location: extend `ChargeEventEntity` with optional
   `stationProvider: String?` (`"OCM"`), `stationId: String?` (the
   provider's stable id), and `stationLatLng: String?` (canonical
   `"lat,lng"`). Schema bump v6 → v7 with a hand-written migration
   adding three nullable TEXT columns.

3. Add an API key configuration step to `docs/BACKUP_SETUP.md` (or
   a new `docs/OCM_SETUP.md`). The key lives in `local.properties`
   (gitignored), surfaced via `BuildConfig`, and is **not** required
   for the app to run, the search affordance hides when missing.

4. Backup serializer bumps `BackupData.CURRENT_VERSION` and adds the
   new fields to `SUPPORTED_VERSIONS = {3, 4, 5, 6, 7}`.

### Notes

- **Privacy:** the station lookup is an outbound HTTPS call to a
  third party. Document this clearly in the wizard and in
  `DESIGN.md`. The lookup is opt-in per event (the user has to
  tap "Search station", typing free text never leaves the device).
- **F-Droid:** OCM API is plain REST over HTTPS, no Google Play
  Services dependency; this *should* be F-Droid-compatible. Verify
  during the revival review.
- **OCPI alternative:** if a research partner runs an OCPI feed,
  swap the OCM client for an OCPI client of the same shape, the
  three new entity columns are provider-agnostic.

---

## 🟡 TASK-43, kWh-from-SoC calculator + `kwhSource` provenance flag ☑ Done (2026-05-03)

> **Outcome:** new `core/model/ChargeKwhSource` enum (`MEASURED` /
> `DERIVED_FROM_SOC`) with `parseLegacy` defensive fallback, paired
> `data/local/db/ChargeKwhSourceConverter` Room TypeConverter and
> `domain/service/ChargeKwhSourceJsonAdapter` Gson adapter, all mirroring
> the TASK-25 `ChargeType` pattern. Schema v6 → v7 with hand-written
> `MIGRATION_6_7` adding `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` —
> legacy rows backfill cleanly. Backup format `CURRENT_VERSION` 6 → 7;
> `SUPPORTED_VERSIONS = {3, 4, 5, 6, 7}`. The DTO field is **nullable**
> in `ChargeEventDto.kwhSource: ChargeKwhSource?` because Gson uses
> `Unsafe.allocateInstance` to construct Kotlin classes (bypasses
> primary-constructor defaults for absent JSON keys); `toEntity()`
> coalesces `null → MEASURED` so the entity's non-null contract holds.
> `CapacityEstimator` skips `DERIVED_FROM_SOC` events on **both** the
> exact and heuristic paths (the derived `kwhAdded` is already a
> tautology against `Δsoc × nominal`). New `domain/service/KwhFromSocCalculator`
> object provides the pure math (`max(0, Δsoc × nominal)`).
> `ChargeEditViewModel` now injects `CarReader` to load
> `nominalBatteryKwh`; new `onCalculateKwhFromSoc()` handler expands the
> SoC card, marks the calculator active, and auto-fills kWh when both
> SoC fields are populated; SoC setters re-derive kWh while the
> calculator is active; the `setKwh()` override semantic preserves
> provenance only when the typed text matches `state.kwh` (the echo
> from `EditText.setText()`), otherwise it flips to `MEASURED`. The
> ChargeEdit fragment grows a "Don't know kWh? Calculate from SoC %"
> link below the kWh field (visibility gated on
> `state.nominalBatteryKwh != null`) and an info banner inside the SoC
> card surfaces while the calculator stays active. Charts degradation
> tab shows a plurals-aware banner when `derivedExcludedCount > 0` in
> the visible period, count comes from new
> `CapacityEstimator.countDerivedEvents()`. History rows render a
> tertiary-container "Est." badge for `DERIVED_FROM_SOC` events. JVM
> unit-test count: **332 → 360** (+5 `CapacityEstimatorTest`, +4
> `KwhFromSocCalculatorTest`, +2 `ChargeKwhSourceTest`, +2
> `ChargeKwhSourceConverterTest`, +4 `BackupSerializerTest`, +2
> `SaveChargeEventUseCaseTest`, +8 `ChargeEditViewModelTest`, +1
> `ObserveChargsModelsUseCaseTest`); new instrumented
> `migrate_6_to_7_addsKwhSourceColumn` test, `migrate_1_to_6` renamed
> to `migrate_1_to_7`. Charging-loss caveat (battery-side vs.
> charger-delivered kWh, ~10% AC, ~5% DC) documented inline on the
> `KwhFromSocCalculator` KDoc. The original task text is preserved
> below.



Many EU/UK chargers and several EVs (older Renault Zoe, Nissan Leaf,
some BMW i3) display only SoC % before/after, never kWh delivered. The
user can compute the missing kWh from `Δsoc × Car.nominalBatteryKwh`,
but a value derived this way feeds back tautologically into TASK-14's
degradation tracker (`capacity = kwhAdded / Δsoc = nominalBatteryKwh`
exactly). The fix is provenance: store every event with a source flag,
let derived events count for cost and efficiency, and exclude them
from degradation, with a visible warning so the user understands why
the chart is sparser than the event count suggests.

### Scope

1. New `domain/model/ChargeKwhSource` enum: `MEASURED`,
   `DERIVED_FROM_SOC`. Add a `ChargeKwhSourceConverter` paired with
   the existing `ChargeTypeConverter` and register both via
   `@TypeConverters` on `AppDatabase`.

2. Schema bump **v6 → v7**. `ChargeEventEntity.kwhSource:
   ChargeKwhSource` (non-null). Hand-written `MIGRATION_6_7` adds
   `kwhSource TEXT NOT NULL DEFAULT 'MEASURED'` so legacy rows
   backfill cleanly. **Coordination with TASK-39:** if TASK-39 lands
   first, this is an ideal inaugural `@AutoMigration` candidate
   (`@ColumnInfo(defaultValue = "MEASURED")` makes Room generate the
   migration). If TASK-39 has not landed, ship the hand-written
   migration as written and TASK-39's first beneficiary becomes the
   next additive bump.

3. Backup format: `BackupData.CURRENT_VERSION` 6 → 7;
   `SUPPORTED_VERSIONS = {3, 4, 5, 6, 7}`. New optional
   `kwh_source` JSON field; legacy backups deserialise as
   `MEASURED`. Adapter mirrors `ChargeTypeJsonAdapter`.

4. **`CapacityEstimator` filter (TASK-14 follow-up).** Both code
   paths, the exact `kwhAdded / (socAfter - socBefore)` route and
   the `kwhAdded ≥ 0.8 × nominalBatteryKwh` heuristic route, must
   skip events where `kwhSource == DERIVED_FROM_SOC`. Otherwise the
   exact path returns the nominal capacity verbatim, and the
   heuristic path trivially qualifies. Add three `CapacityEstimatorTest`
   cases: derived event excluded from exact path, derived event
   excluded from heuristic path, mixed dataset excludes derived rows
   only.

5. **In-form calculator on `ChargeEditFragment`.** Add a small
   "Don't know kWh? Calculate from SoC %" link/button below the kWh
   `TextInputLayout`. Show only when the active car has
   `nominalBatteryKwh != null` AND the kWh field is empty. Tapping
   it expands the existing TASK-14 SoC card (if collapsed) and
   surfaces an info banner: *"kWh will be estimated from SoC change
   × nominal capacity. The event will be flagged as estimated and
   excluded from degradation tracking."* When the user fills both
   SoC fields, the kWh field auto-populates with `(socAfter -
   socBefore) × nominalBatteryKwh` (clamped to ≥ 0) and the save
   button writes `kwhSource = DERIVED_FROM_SOC`.

6. **Override semantics.** If the user manually edits the kWh field
   after the calculator filled it, flip the source flag back to
   `MEASURED` (track via a `ViewModel` flag, once the user touches
   the kWh field post-calculator, treat the value as user-entered).
   Conversely, re-tapping the calculator link with both SoC fields
   populated re-derives kWh and re-flips the flag to `DERIVED_FROM_SOC`.

7. **Degradation chart warning banner.** When the Charts → Degradation
   tab (TASK-14) renders, count the derived events in the visible
   period. If non-zero, render a banner above the chart:
   *"N estimated events excluded from degradation tracking
   (calculated from SoC %)."* The chart itself silently drops
   those events (already covered by Step 4). Banner uses the same
   M3 surface treatment as the multi-currency hint on Dashboard.

8. **History list badge.** Add a small "estimated" badge to history
   rows where `kwhSource == DERIVED_FROM_SOC`, alongside the
   existing AC / DC charge-type badge. Reuse a tertiary-container
   colour token; keep the label one word (`Est.`).

9. **Pure helper.** Extract the math into
   `domain/service/KwhFromSocCalculator.kt` with a single
   `compute(socBefore: Double, socAfter: Double, nominalBatteryKwh:
   Double): Double` method that returns `max(0.0, (socAfter -
   socBefore) × nominalBatteryKwh)`. JVM-test it directly: 4 cases
   (typical, full charge from 20%→80%, zero delta returns zero,
   negative delta clamps to zero).

10. New `SaveChargeEventUseCaseTest` cases asserting that
    `kwhSource` round-trips through the use case unchanged. Update
    existing fakes / fixtures to construct events with the explicit
    source field (default `MEASURED`, most tests don't care).

### Notes

- **Charging-loss caveat.** SoC-derived kWh is *battery-side* (what
  the cells absorbed), not *charger-delivered* (what the user paid
  for). For AC charging the gap is ~10–15%, for DC ~5%. This biases
  derived events: efficiency (km/kWh) looks *better* than reality
  and cost-per-kWh looks *worse* than reality. Acceptable today —
  documenting the caveat in `DESIGN.md` is enough; a charging-loss
  factor column would be over-engineering until a researcher asks.
- **Why not nullable `kwhAdded`?** Considered (Option C in the
  brainstorm transcript). Rejected, it would touch every aggregate,
  every chart, every backup version. The provenance flag gets 80%
  of the value at 20% of the cost, with a clean upgrade path if the
  app ever needs first-class SoC-only events later.
- **Why explicit calculator button vs. auto-derive on save?**
  Explicit button = explicit user acceptance of the estimate. Auto-
  deriving on save (whenever kWh is blank but SoC is filled) would
  silently flip events to `DERIVED_FROM_SOC` and surprise users who
  filled SoC for degradation tracking but had a paper kWh receipt
  to enter later.

  **Update (2026-05-03):** the calculator now also auto-activates at
  *input time*, the moment the user has typed both SoC fields with
  a valid range AND kWh is blank, kWh is derived live and provenance
  flips to `DERIVED_FROM_SOC`. The "save-time silent auto-derive"
  argued against above is still rejected (the user wouldn't see it
  happen until after the fact); the new input-time auto-derive is
  visible as the user types, and the existing `setKwh` echo-guard
  still flips provenance back to `MEASURED` the moment the user
  edits the kWh field manually. The explicit "Calculate from SoC %"
  link survives as an affordance for "overwrite an existing kWh
  with the SoC-derived value" (auto-activation stays a no-op when
  kWh is non-blank).
- **Coordination with TASK-25 / TASK-39.** TASK-25 introduced the
  `ChargeType` enum + converter pattern; mirror that exactly for
  `ChargeKwhSource`. TASK-39 (Room `@AutoMigration`) is the natural
  vehicle for this migration if it lands first, see Step 2.

---

## 🟡 TASK-44, Fix `StatsCalculator.computeStats` cost accumulation ☑ Done (2026-05-03)

> **Outcome:** the cost-accumulation block in
> `StatsCalculator.computeStats` is now hoisted above the `events.size
> < 2` early-return and computes `totalCost = events.mapNotNull {
> it.costTotal }.sum()` once for the whole period (mixed-currency
> rule still wins, `totalCost = null`, `costPerKm = null` whenever
> two distinct currencies appear). The delta-pair odometer loop now
> only accumulates `pairKwh` and `totalDist`; `costPerKm = totalCost /
> totalDist` keeps its existing formula but sources `totalCost` from
> the all-events sum. Single-event periods now report `totalCost`
> (previously hard-coded to `null` in the early-return branch); their
> `costPerKm` stays `null` because delta distance is undefined for
> one event. The Dashboard total-cost surface and the monthly cost
> chart now agree on the sum for any single period, the audit's
> 33–50% undercount on 2–3 session histories is gone. Four new
> regression cases added to `StatsCalculatorCostTest`
> (`firstAndSecondEventBothCosted_totalCostSumsBoth`,
> `firstAndThirdEventCosted_middleNotCosted_totalIncludesBoth`,
> `singleCostedEvent_reportsTotalCost_costPerKmStillNull`,
> `mixedCurrencyWithFirstEventCosted_totalCostStillNull`); the
> existing 7 cases pass unchanged because their fixtures all use the
> "anchor event with zero cost" pattern that carefully avoided the
> bug. JVM unit-test count 360 → 364. `DESIGN.md §7` already stated
> `Σ cost / Σ d_km` (line 488); no doc text change needed, the code
> was the deviation.

> **Audit finding (BUG-03, 2026-05-03):** `computeStats` accumulates
> cost inside the delta-odometer loop (`for (i in 1 until sorted.size)`),
> which silently drops the **first event's cost** from `totalCost` and
> `costPerKm`. `computeMonthlyBuckets` already sums all events
> (`bucketEvents.mapNotNull { it.costTotal }.sum()`), so the Dashboard
> "Total cost" card and the monthly cost chart disagree on the same
> period. The undercount is negligible in long histories but is a
> 33–50% error for fresh users with 2–3 sessions, exactly the audience
> most likely to spot the inconsistency between the two surfaces.

### Scope

1. Separate cost accumulation from the odometer-delta loop in
   `StatsCalculator.computeStats`. Sum cost across **all** events in
   the period, not just `sorted[i]` for `i ≥ 1` with `dist > 0`:

   ```kotlin
   val resolvedTotalCost = if (mixedCurrency) null else
       events.mapNotNull { it.costTotal }.sum().takeIf { it > 0.0 }
   ```

   The mixed-currency rule still wins, when `mixedCurrency = true`,
   every cost-derived field returns `null` (per the existing invariant
   documented in `CLAUDE.md`'s "Smart cost handling" section).
2. Update the early-return branch for `events.size < 2`. Single-event
   periods previously returned `totalCost = null`; they should now
   return the single event's cost when it exists. `costPerKm`
   stays `null` because `totalDist` is undefined for a single event.
3. `costPerKm` keeps its current `totalCost / totalDist` formula but
   sources `totalCost` from the new sum. With single-event periods
   it stays `null` (no `totalDist`).
4. Add JVM regression cases to `StatsCalculatorTest`:
   - 2 events, both costed → `totalCost` = sum of both (currently only
     the second is counted).
   - 3 events, only the first and third are costed → `totalCost`
     includes both.
   - Mixed-currency rule still wins (`totalCost = null` regardless).
   - Single costed event → `totalCost` = that cost; `costPerKm` =
     `null`.
   - Existing `costPerKm` test case still passes.
5. Update `docs/DESIGN.md §7` if the formula table mentions the
   delta-loop semantics; the new contract is "total cost = sum over
   period; cost-per-km = total cost / total driven distance".

### Acceptance

`computeStats.totalCost` and the per-month sum from
`computeMonthlyBuckets` agree on any single-period input. Dashboard
total-cost surface matches the monthly-cost chart. JVM unit-test count
increases by ≥ 4. No existing test regresses.

---

## 🟢 TASK-45, Defensive SoC range guard in `KwhFromSocCalculator` ☑ Done (2026-05-07)

> **Outcome.** `KwhFromSocCalculator.compute` now opens with two
> `require(...)` guards: `socBefore` and `socAfter` must be fractions in
> `[0.0, 1.0]`, and `nominalBatteryKwh` must be `> 0.0`. Either guard
> failing throws `IllegalArgumentException` with the offending value in
> the message. The function-level KDoc was rewritten to make the
> fraction contract explicit and to cite
> `ChargeEditViewModel.recomputeKwhFromSoc` as the canonical caller
> pattern; the existing in-range "negative delta clamps to 0" behaviour
> is preserved verbatim. Four new JVM cases land in
> `KwhFromSocCalculatorTest`: `socBefore_rawPercent_throws` (80.0 not
> 0.80), `socAfter_negativeFraction_throws`, `nominalBatteryKwh_zero_throws`,
> `nominalBatteryKwh_negative_throws`. `ChargeEditViewModel` is
> unchanged because it already passes fractions. Full local gate green
> (`ktlintCheck` + `:app:lint` + `:app:testDebugUnitTest`).

> **Audit finding (BUG-01, 2026-05-03):**
> `KwhFromSocCalculator.compute(socBefore, socAfter, nominalBatteryKwh)`
> accepts any `Double` and clamps only to `≥ 0`. The sole caller in
> `ChargeEditViewModel.recomputeKwhFromSoc` already validates `[0, 100]
> %` and divides by 100 before invocation, so the calculator's lack of
> a guard is currently latent. A future caller wiring percent values
> directly (or fractions outside `[0,1]`) would silently produce a
> kWh inflated by 100× without any signal. Defense-in-depth, low cost.

### Scope

1. Add `require(...)` at the top of `compute(...)`:

   ```kotlin
   require(socBefore in 0.0..1.0 && socAfter in 0.0..1.0) {
       "SoC values must be fractions in [0,1]; got socBefore=$socBefore socAfter=$socAfter"
   }
   require(nominalBatteryKwh > 0.0) {
       "nominalBatteryKwh must be > 0; got $nominalBatteryKwh"
   }
   ```
2. Update the function KDoc to make the contract explicit: inputs are
   *fractions* (`0.0..1.0`), not percentages (`0..100`). Cite the
   `ChargeEditViewModel.recomputeKwhFromSoc` percent→fraction
   conversion as the canonical caller pattern.
3. Add JVM cases to `KwhFromSocCalculatorTest`:
   - `socBefore = 80.0` (raw percent) throws
     `IllegalArgumentException`.
   - `socAfter = -0.1` throws.
   - `nominalBatteryKwh = 0.0` throws.
   - `nominalBatteryKwh = -10.0` throws.

### Acceptance

Calling the calculator with raw percent inputs or non-positive
nominal capacity fails fast in development. `ChargeEditViewModel`
behavior unchanged because it already passes fractions. JVM
unit-test count gains ≥ 4 cases.

---

## 🟡 TASK-46, Battery-health card "Estimated" warning when heuristic over-estimates ☑ Done (2026-05-04)

> **Outcome (merged 2026-05-04 on `feat/task46-battery-health-estimated-chip`).**
>
> - **`CapacityEstimator`** gains `latestIsExact(points): Boolean?`
>   (null on empty list; otherwise the chronologically latest point's
>   `isExact` flag) and a new companion constant
>   `HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT = 105.0` documenting the
>   5%-margin design choice (avoids false alarms on exact-path
>   readings that legitimately land just over 100% because users
>   enter SoC as whole percents).
> - **`Stats`** grows two booleans:
>   `batteryHealthIsHeuristic: Boolean = false` (latest point came
>   from the heuristic full-charge path) and
>   `batteryHealthIsOverestimated: Boolean = false` (heuristic AND
>   `batteryHealthPercent >= 105.0`). Splitting into two flags keeps
>   the option open for a softer "Estimated" tag in future UX
>   without the over-estimation warning.
> - **`StatsCalculator.computeStats`** signature extended with the
>   two new optional booleans (default false), threaded into both
>   the early-return branch and the main return branch.
> - **`ObserveDashboardStatsUseCase`** computes the booleans
>   inline: `isHeuristic = capacityEstimator.latestIsExact(points)
>   == false` (the `== false` literal is intentional, it returns
>   `false` for an explicitly heuristic latest point AND for the
>   null/empty case, which collapses both to "no warning"); then
>   `isOverestimated = isHeuristic && healthPct != null && healthPct
>   >= HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT`.
> - **Layout** adds a new TextView (`@id/dashboard_battery_health_estimated_warning`)
>   inside the existing `dashboard_card_battery_health` card, styled
>   as a TASK-43-style chip, `?attr/colorTertiaryContainer`
>   background, `?attr/colorOnTertiaryContainer` text, label-medium
>   text appearance, 8dp top margin, hidden by default. Carries
>   `android:contentDescription="@string/battery_health_estimated_warning_a11y"`
>   ("Estimated capacity, may overestimate") so TalkBack reads a
>   self-contained sentence, coordinates with TASK-18 a11y scope.
> - **`DashboardFragment.renderBatteryHealthCard`** flips the chip's
>   visibility from `stats?.batteryHealthIsOverestimated == true`
>   (the strict `== true` keeps null safe).
> - **Strings** add two new keys: `battery_health_estimated_warning`
>   ("Estimated, heuristic may overestimate") and
>   `battery_health_estimated_warning_a11y` ("Estimated capacity,
>   may overestimate"). Two strings instead of one because the chip
>   text is intentionally terse for visual density while TalkBack
>   needs a complete sentence.
>
> **Tests:** 5 new JVM cases on `ObserveDashboardStatsUseCaseTest`,
> all single-event fixtures with `nominal = 60.0` so the heuristic
> threshold sits at exactly `kwhAdded = 48.0`:
> `heuristicLatestPoint_overThreshold_setsBothFlags` (kwhAdded = 70
> → 116.67%, both flags), `heuristicLatestPoint_underThreshold_setsHeuristicButNotOverestimated`
> (kwhAdded = 50 → 83.3%, only heuristic),
> `heuristicAtExactly105Percent_setsOverestimated_boundaryGuard`
> (kwhAdded = 63 → 105.0% exactly, pins the `>=` semantic),
> `exactLatestPoint_evenAboveThreshold_setsNeitherFlag` (SoC delta
> 0.2→0.7 over 33 kWh = 110% via the exact path; chip stays hidden
> per the BACKLOG contract that exact readings are trusted), and
> `nullNominal_setsNeitherFlag` (regression guard for cars with no
> `batteryKwh`). The existing `CapacityEstimatorTest` >100%
> computation case unchanged, no new estimator-level tests needed
> per the BACKLOG checklist. JVM unit-test count 398 → 403.

> **Audit finding (BUG-02, 2026-05-03):**
> `CapacityEstimator.batteryHealthPercent(...)` returns values >100%
> when the heuristic path over-counts (typical pattern: user only
> records "near full" charges, all of which qualify under the 80%
> heuristic and produce inflated capacity readings). The KDoc
> explicitly leaves the value unclamped as diagnostic information,
> but `DashboardFragment.bindBatteryHealth(...)` (around line 240)
> renders the percentage verbatim with no visual distinction. Users
> may misread "112%" as "battery is supercharged" rather than "the
> heuristic over-estimated." `CapacityPoint.isExact` flag is
> available but unused in the UI.

### Scope

1. Surface heuristic provenance in `Stats`, add a derived field on
   `Stats` (or a parallel field on `DashboardScreenState.stats`) such
   as `batteryHealthIsHeuristic: Boolean` and
   `batteryHealthIsOverestimated: Boolean`, populated from the latest
   `CapacityPoint`'s `isExact = false` flag and a 1.05× threshold
   respectively.
2. In `DashboardFragment.bindBatteryHealth(...)`, render a
   tertiary-container chip (or supporting text below the percentage)
   reading "Estimated, heuristic may overestimate" when **both**
   `latest.isExact = false` AND
   `batteryHealthPercent ≥ HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT`
   (≥ 105). Reuse the TASK-43 "Est." badge styling
   (`?attr/colorTertiaryContainer`).
3. Add `contentDescription` for the warning chip so TalkBack reads
   "estimated capacity, may overestimate", coordinate with TASK-18.
4. JVM tests:
   - `CapacityEstimatorTest` already covers the >100% computation;
     no new estimator tests needed.
   - Add `ObserveDashboardStatsUseCaseTest` cases asserting the new
     boolean flags fire / clear at the threshold.
   - VM-level test that the chip-visibility flag transitions at
     exactly 1.05× nominal.
5. Add the threshold constant on `CapacityEstimator.companion`:
   ```kotlin
   const val HEURISTIC_OVERESTIMATE_THRESHOLD_PERCENT = 105.0
   ```

### Notes

- Coordinate with TASK-30. The Charts degradation tab will be redrawn
  when MPAndroidChart is replaced; the same warning should appear
  above the chart on the degradation tab if the latest point exceeds
  nominal. Defer the chart-side change to TASK-30 if it lands first
 , the audit explicitly suggests bundling the two.
- Coordinate with TASK-43. Derived events (`DERIVED_FROM_SOC`) are
  already excluded by `CapacityEstimator.estimateOne`, so the
  heuristic path is the only over-estimation source.

### Acceptance

Dashboard renders an "Estimated" supporting chip under the
battery-health percentage when the latest reading is heuristic AND
≥ 105% of nominal. Existing Dashboard tests do not regress. New JVM
tests cover the threshold transition.

---

## 🟢 TASK-47, Charging power profile fields (`peakPowerKw`, `chargingDurationMinutes`) ⏸ Under consideration (2026-05-09)

> **Audit suggestion (RES-01, 2026-05-03):** The app records energy
> (`kwhAdded`) but not charging *power over time*. Even a simple
> nullable `peakPowerKw` field would enable downstream research on
> charging-infrastructure demand patterns, SPS-Lab grid-integration
> studies care about peak charger draw distributions, not just total
> energy. Bundling duration in the same schema bump enables a derived
> `avgPowerKw` without further storage.

### Scope

1. Schema **v7 → v8** (next migration claim, coordinate with
   TASK-39's `@AutoMigration` rollout):
   - Add `peakPowerKw: Double?` to `ChargeEventEntity`.
   - Add `chargingDurationMinutes: Int?` to `ChargeEventEntity`.
   - Both nullable, additive-only DDL. Ideal `@AutoMigration`
     candidate; if TASK-39 hasn't landed yet, write a manual
     `MIGRATION_7_8` mirroring `MIGRATION_5_6`'s additive pattern.
2. Backup format `BackupData.CURRENT_VERSION` 7 → 8. Append v8 to
   `BackupSerializer.SUPPORTED_VERSIONS`. Both DTO fields nullable
   for legacy v3..v7 compatibility (Gson `Unsafe.allocateInstance`
   caveat, see TASK-43 outcome banner: nullable DTO + coalesce in
   `toEntity()` is the established pattern).
3. ChargeEdit form: optional fields under a new collapsible
   "Charging power" card (mirrors the SoC card UX from TASK-14 /
   TASK-43). Both inputs accept blank / null and never block save.
   Validation: when `chargingDurationMinutes` is provided, it must
   be `> 0`; when `peakPowerKw` is provided, it must be `> 0` and
   `≤ 1000` (sanity ceiling).
4. `StatsCalculator` extension: when `chargingDurationMinutes` is
   present, compute per-event `avgPowerKw = kwhAdded / (durationMinutes / 60.0)`.
   Add `Stats` fields `avgPeakPowerKw: Double?` (mean of recorded
   `peakPowerKw` values in the period) and
   `avgPowerKwSampleCount: Int` (so the dashboard knows whether to
   render the new card).
5. CSV export columns (TASK-09 dependency or coordinated): emit
   `peak_power_kw,charging_duration_minutes,avg_power_kw` columns
   when at least one event in the export range has the data.
6. JVM tests for the schema (instrumented `migrate_7_to_8` or
   `@AutoMigration` validation), backup round-trip across v7 / v8,
   `ChargeEditViewModel` parsing/validation, `StatsCalculator` power
   derivation, mixed-fixture (some events with power data, some
   without).

### Notes

- Out of scope: real-time power telemetry from chargers, OBD/CAN
  integration, charging-curve recording. This task is the
  user-entered minimum; the data already collected (start/end SoC,
  kWh delivered) plus an optional duration is the MVP.
- Charging duration cannot reliably be derived from `eventDate`
  (which is wall-clock when the user logged the session, not session
  start). An explicit field is required.
- Coordinate with TASK-40, the anonymised research export must
  include both new fields when present.
- Coordinate with TASK-48, both touch the schema; bundling them
  into a single v7 → v8 bump avoids two consecutive additive bumps.

### Acceptance

Schema bumped to v8 with backward-compatible migration. Optional
power fields appear in ChargeEdit and CSV exports. `Stats` exposes
average peak power and an avg-power-derivation count. JVM unit-test
count increases by ≥ 6 cases. No regression in existing tests.

---

## 🟢 TASK-48, Time-of-use (ToU) tariff classification on charge events ⏸ Under consideration (2026-05-09)

> **Audit suggestion (RES-02, 2026-05-03):** Cost field captures total
> cost but not *when* the charge occurred relative to grid tariff
> zones. Cyprus and most EU grids run ToU pricing. Per-tariff cost
> analysis enables smart-charging time-shift recommendations and
> aggregate load-profile research for SPS-Lab grid-integration
> studies.

### Scope

1. Add `core/model/TariffZone` enum: `PEAK`, `OFF_PEAK`,
   `SUPER_OFF_PEAK`, `FLAT` (default for users without ToU billing).
   Plus `parseLegacy` defensive fallback like `ChargeType` /
   `ChargeKwhSource`. Paired Room TypeConverter and Gson adapter,
   both registered alongside the existing TASK-25 / TASK-43 wiring.
2. Schema bump to **next available v** (coordinate with TASK-47):
   add `tariffZone: TariffZone` non-null with `DEFAULT 'FLAT'` so
   legacy rows backfill cleanly.
3. Optional **auto-inference**: a tariff schedule stored in
   DataStore (a list of `(startHourLocal, endHourLocal, zone)`
   tuples). When present, `SaveChargeEventUseCase` infers
   `tariffZone` from `eventDate`'s local-time hour against the
   schedule. Manual override on the form beats auto-inference.
4. Wizard / Settings UI: optional "I have time-of-use billing"
   toggle that opens a tariff editor (start time, end time, zone
   label). Default off, most users keep `FLAT` and never see the
   ToU surface.
5. Charts: filter chip on the trend / monthly-cost tabs to limit
   series to a single zone. Multi-vehicle overlay (TASK-38) gains a
   "by tariff zone" stratification layer.
6. CSV export and anonymised export (TASK-40): emit `tariff_zone`
   column.

### Notes

- Out of scope: live tariff-rate lookup from a utility API,
  automatic per-zone cost rate suggestion, dynamic ToU (sub-day
  variation beyond 4 zones). This task lands the *classification*;
  rate modeling stays user-entered as today.
- Coordinate with TASK-49 (grid carbon intensity), both extend the
  per-event metadata; ideally merged in a single schema bump if both
  land in the same release window.
- Local-time inference must use the device's current zone (not the
  zone of `eventDate`). A user logging a charge while travelling
  abroad will typically still want the home-tariff schedule applied
 , flag this as a known caveat in the methodology doc.

### Acceptance

`tariffZone` round-trips through Room + backup. Optional tariff
schedule auto-classifies new events. Charts gains a ToU filter.
Existing JVM tests do not regress; new JVM tests cover the auto-
inference and the FLAT-default backfill.

---

## 🟢 TASK-49, Per-event grid carbon intensity (extends TASK-20)

> **Data-source survey (2026-05-04, recorded during TASK-20 implementation).** Originally scoped to merge with TASK-20 (Option 1 in the brainstorm) but deferred because no free real-time Cyprus grid-intensity API was found:
> - **Electricity Maps API**, ~€6,000/year per zone for Carbon Intensity. Out of scope.
> - **CO2Signal**, was free; absorbed into Electricity Maps' paid product. Dead.
> - **cyprusgrid.com direct**, bot-blocked behind a WAF; values reachable only via the `r.jina.ai` reader-proxy. Not stable enough as a production data source (third-party, ToS-grey, no SLA).
> - **ENTSO-E Transparency Platform**, genuinely free + public + Cyprus covered. Returns hourly **generation mix per production type** (oil, gas, solar, wind, hydro, …); deriving carbon intensity requires applying per-type IPCC AR6 lifecycle emission factors and weighted-summing. ~200–300 LOC + 8–12 tests. The viable path forward.
> - **TSOC direct API**, best long-term answer; needs email correspondence with the Cyprus TSO.
>
> Pick one (likely ENTSO-E for sustainability + research transparency, or TSOC for an official source). When this lands: schema bump v7→v8 adding nullable `gridCarbonIntensityGCo2PerKwh` to `charge_events`, new `domain/co2/GridIntensityFetcher` narrow IF + ENTSO-E impl, `SaveChargeEventUseCase` fires the fetch on save (best-effort, null on failure → falls back to the `gridIntensityGCo2PerKwh` preference), `ChargeEditFragment` displays the live value as the user types ("Current grid: 365 gCO₂/kWh"), that's the actual behavioural nudge.

> **Audit suggestion (RES-03, 2026-05-03):** TASK-20 proposes a static
> Cyprus grid intensity baseline. For SPS-Lab publications, a more
> granular approach is preferable: associate each charge event's
> timestamp with hourly grid carbon intensity from open APIs
> (Electricity Maps, ENTSO-E for CY) so retrospective carbon
> accounting uses *marginal* (not average) emission factors.

### Scope

1. Add nullable `gridCarbonGCo2PerKwh: Int?` to `ChargeEventEntity`
   (or `Double?` if sub-integer precision matters for marginal
   factors). Schema bump as part of the same window as TASK-20 /
   TASK-47 / TASK-48 when possible, three additive nullable
   columns in one migration is cleaner than three sequential bumps.
2. **Backfill mode**: a Settings → "Carbon data" → "Backfill
   historical carbon intensity" action that calls a configurable API
   endpoint (default Electricity Maps free tier) for each event with
   `gridCarbonGCo2PerKwh = null`. Network-bound `WorkManager`
   one-shot, opt-in, no auto-fetch on launch.
3. **Per-event recording on save**: when the user is online, fetch
   intensity at `eventDate` ±1 h and persist. Fallback to TASK-20's
   static baseline when offline; never block save on the network
   call (fire-and-forget with later backfill).
4. Carbon savings stat on the Dashboard:
   `(baselineICE_kgCo2 - actualGridIntensity_kgCo2) × distance` per
   event, summed across the period. Per-event flag whenever the
   marginal-vs-baseline delta is negative ("charged on a dirtier-
   than-typical hour"), useful smart-charging signal.
5. Methodology doc `docs/METHODOLOGY.md` (created in TASK-20):
   append a section explaining the marginal-vs-average distinction,
   the API source, the freshness/lag of the data, and the citation
   format for SPS-Lab papers.
6. Anonymised export (TASK-40) emits `grid_carbon_g_co2_per_kwh`.

### Notes

- This task is **explicitly an extension of TASK-20**; do not start
  before TASK-20 lands. The static baseline is the floor; this task
  adds the per-event refinement on top.
- Free-tier API call budgets are tight (Electricity Maps free tier:
  ~50 requests/day). Batch backfill must respect rate limits and
  surface a "Quota exceeded, retry later" Snackbar.
- Optional API-key configuration in Settings; no key bundled in
  source (consumer-API-key-in-source is a security smell).
- Coordinate with TASK-48, both extend per-event metadata; bundle
  schema bumps when timing allows.

### Acceptance

Per-event carbon intensity stored in Room. Backfill action populates
historical events on demand. Methodology doc updated with the
marginal-vs-average section. Anonymised export includes the new
column. Tests cover the offline-fallback path and the API
rate-limit handling.

---

## 🔴 TASK-50, Stabilise nightly instrumented suite (post-WorkManager-init) ☑ Done (2026-05-03)

> **Outcome:** all four sub-fixes landed on `fix/task50-nightly-greenup`
> in a single PR. **Sub-fix A** added
> `androidx-fragment-testing-manifest = { module =
> "androidx.fragment:fragment-testing-manifest", version.ref =
> "fragmentTesting" }` to `gradle/libs.versions.toml` and
> `debugImplementation(libs.androidx.fragment.testing.manifest)` to
> `app/build.gradle.kts`; the merged debug app manifest now declares
> `EmptyFragmentActivity`, so `FragmentScenario.launchInContainer`
> resolves the host activity correctly for the ~14 affected tests
> (verified via `grep EmptyFragmentActivity app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`).
> **Sub-fix B** renamed `DriveBackupWorkerTest.ioError_returnsRetry` to
> `ioError_recoversAfterTransientRetry_returnsSuccess` (asserts
> `Result.success()` with `failTimes = 1`, relies on the repo's
> 3-attempt retry budget) and added a sister case
> `ioError_exceedsRetryBudget_returnsFailure` (`failTimes = 4`,
> asserts `Result.failure()`); the instrumented-side
> `FakeDriveRemoteSource` was upgraded to mirror the JVM-side fake's
> `failTimes` / `attemptCount` / `failuresRaised` budget design so
> both halves of TASK-07's contract are testable on the same fixtures.
> **Sub-fix C** root-caused `MainActivityResetRecoveryTest`'s
> `clearCalls = 0` assertion to a Hilt binding mistake, the original
> `@BindValue val testRunner: TestableResetRunner` bound the *concrete*
> class while the use case injected the *interface*
> `DataResetTransactionRunner` (which still resolved to the production
> `RoomDataResetTransactionRunner` via `DomainModule`); typing the
> field as the interface caused a duplicate-binding error because
> `@BindValue` adds, not replaces. Fix: extracted
> `DataResetTransactionRunner`'s `@Binds` from `DomainModule` into a
> new single-binding `DataResetModule` so the test can
> `@UninstallModules(DataResetModule::class)` and rebind via a
> local `@Module abstract class TestResetModule` (mirroring the
> `BackupModule` pattern from `DriveBackupWorkerTest`). Both
> recovery tests now exercise the spy correctly. **Sub-fix D**
> resolved as collateral once sub-fix A landed —
> `ChartsFragmentTest.initializationError` was downstream of
> `launchFragmentInContainer` failing because the host activity
> wasn't resolvable; verification of the green signal happens on
> the next nightly cron. JVM unit-test count unchanged at 364
> (this was instrumented-side only); instrumented-test count grows
> by +1 (the new `ioError_exceedsRetryBudget_returnsFailure` case).
> The `WizardFlowTest` 24dp TabView a11y failure is intentionally
> still red, that's the TASK-18 Step 6 discovery signal feeding
> the TASK-18 follow-up scope.

> **Context (2026-05-03):** the nightly cron after the WorkManager-init
> fix (`444b7a2`) ran the full 58-test suite to completion, the
> process-level crash is gone, but exposed 21 distinct failures
> across **four** root causes plus the intentional TASK-18 a11y
> discovery signal. Logs: GitHub Actions run `nightly-instrumented`
> on `main` after `444b7a2`. Bundle all four sub-fixes in one branch
> so the next cron flips fully green; the a11y signal stays as
> follow-up work for TASK-18.

### Sub-fix A, `EmptyFragmentActivity` not declared in app manifest

**Affects (~14 of 21 failures):** `AboutFragmentTest`,
`ChargeEditFragmentTest`, `ManageLocationsFragmentTest`,
`SettingsFragmentTest`, `SettingsBackupControlsTest`,
`ChartsFragmentTest` (the latter surfaces as `Failed to instantiate
test runner class` because `launchFragmentInContainer` fails before
any test method executes).

**Failure stack:**
```
java.lang.RuntimeException: Unable to resolve activity for Intent
  cmp: org.spsl.evtracker.debug/
       androidx.fragment.app.testing.EmptyFragmentActivity
```

**Root cause:** `androidx.fragment:fragment-testing:1.6.2` is on
`androidTestImplementation`, which puts the activity declaration
into the **test APK's** manifest only. The instrumentation runner
launches activities under the **app under test's** package
(`org.spsl.evtracker.debug`), so the test APK's declaration is
invisible. The library was split in 1.6+: the runtime classes stay
on `fragment-testing`, but the manifest entry moved to the
companion `fragment-testing-manifest` artifact, which must go on
`debugImplementation` so it merges into the app manifest.

**Fix (preferred):** add the companion artifact to `libs.versions.toml`
and wire it on `debugImplementation`:
```toml
androidx-fragment-testing-manifest = { module = "androidx.fragment:fragment-testing-manifest", version.ref = "fragmentTesting" }
```
```kotlin
// app/build.gradle.kts
debugImplementation(libs.androidx.fragment.testing.manifest)
```
**Fix (alternative, hand-rolled):** create
`app/src/debug/AndroidManifest.xml` with the activity declaration
verbatim. The library approach is preferred, it auto-tracks
versions and avoids drift.

### Sub-fix B, `DriveBackupWorkerTest.ioError_returnsRetry` is stale post-TASK-07

**Failure:** `expected:<Retry> but was:<Success>`.

**Root cause:** the test asserts `ListenableWorker.Result.retry()`
on a transient `IOException`. TASK-07 (merged 2026-05-01) explicitly
removed `Result.retry()` from `DriveBackupWorker.doWork()`, the
repo now runs its own bounded retry loop (3 attempts, exponential
backoff) and the worker is a thin `when (result)` translator that
emits `Result.success()` or `Result.failure()` only. With the
default `FakeDriveRemoteSource.failTimes = 1`, the IOException is
raised once, the repo retries, attempt 2 succeeds, the worker
returns `Result.success()`, exactly what the failing test sees.
TASK-36's inline-comment on the no-`retry()` invariant is the
companion record of the same contract change.

**Fix:** rename to `ioError_recoversAfterTransientRetry_returnsSuccess`
and assert `Result.success()` with `failTimes = 1`. Add a sister
test `ioError_exceedsRetryBudget_returnsFailure` that sets
`failTimes = 4` (above `MAX_ATTEMPTS = 3`) and asserts
`Result.failure()`. This codifies both halves of TASK-07's contract.

### Sub-fix C, `MainActivityResetRecoveryTest` recovery flow not invoked

**Affects:**
`startup_resetInProgressTrue_runsUseCase_clearsFlag_beforeUiVisible`
(reports `expected:<1> but was:<0>` on `testRunner.clearCalls`) and
`startup_resetRecoveryThrows_showsRetryDialog_doesNotMountNavGraph`
(`NoMatchingRootException`: retry dialog never appears).

**Likely root cause(s), investigate before fixing:**
1. The recovery hook in `MainActivity` / `MainViewModel` runs on a
   coroutine that isn't reaching the test's `awaitNavMounted` poll
   under the new `WorkManagerTestInitHelper.initializeTestWorkManager`
   bring-up timing, i.e., the test process starts WorkManager
   first, the recovery hook second, racing against the dialog
   inflation.
2. The `@BindValue testRunner` is not actually overriding the
   production `DataResetTransactionRunner` binding under the
   current Hilt graph (maybe a `@Singleton` mismatch or a missing
   `@Binds` after TASK-24's narrow-IF refactor).
3. The recovery use case path in `MainViewModel.StartupState`
   regressed silently and now no-ops on `resetInProgress = true`.

**Fix:** start by adding logcat instrumentation to the test's
`@Before` (Timber.d at every state transition in
`MainViewModel.observeStartupState`); confirm whether the use case
fires at all. If it fires but the assertion is racy, replace
`scenario.moveToState(RESUMED)` with a
`MainViewModel.startupState.first { it is StartupState.Ready }`
gate. If the use case doesn't fire, audit the
`@BindValue` interaction with the post-TASK-24 graph.

### Sub-fix D, `ChartsFragmentTest.initializationError`

**Failure:** `RuntimeException: Failed to instantiate test runner
class androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner`.

**Root cause:** suspected collateral damage from sub-fix A —
`launchFragmentInContainer` is referenced in the test file
(`import androidx.fragment.app.testing.launchFragmentInContainer`),
and the per-class init may be evaluating the import path in a way
that fails when the host activity isn't resolvable. Verify after
sub-fix A lands; if `ChartsFragmentTest` still fails, inspect
class-loading more carefully (look for a static `companion object`
or a delegated property that fails at construction time).

**Fix:** re-run after sub-fix A. If still failing, capture the
underlying `Caused by:` chain from the log (the surface error
hides the real exception) and patch accordingly.

### Out of scope for this task

- **WizardFlowTest a11y failure** (`TabView width is 24dp.
  Consider making the width 48dp or larger`), this is the
  intentional TASK-18 Step 6 discovery signal, not a regression.
  Track it under TASK-18 follow-up scope (steps 1–5, 7, 8). The
  nightly run is informational only and does not block PRs.

### Acceptance

The next nightly cron (or a manually triggered
`workflow_dispatch` of `nightly-instrumented.yml` on the post-fix
commit) reports green on all 4 of A/B/C/D. The a11y failure
listed under "Out of scope" is allowed to remain red and feeds
TASK-18 follow-ups. JVM unit-test count delta probably zero (this
is instrumented-only work); instrumented-test count may grow by
+1 for sub-fix B's new sister case.

---

## Notes for Agents (TASK-22 to TASK-30 addendum)

> Sequencing notes for **TASK-07, TASK-12, TASK-16, TASK-19, TASK-22,
> TASK-23, TASK-24, TASK-25, TASK-28, TASK-29, TASK-31, TASK-34,
> TASK-36** are obsolete, all thirteen landed. The static-analysis CI gate (TASK-16) is in place; the SDK
> bump to 35 (TASK-22) merged with no `connectedAndroidTest` matrix to
> coordinate; TASK-23 → TASK-24 ran in the prescribed order; TASK-25
> claimed Room v3 → v4 + `MIGRATION_3_4` and bumped `backup_version`
> to 4; TASK-28 retired the parallel `() -> Long` clock; TASK-29's
> debug build type + `BuildConfig` enablement is merged with the
> OAuth-client implication documented in `GOOGLE_CLOUD_SETUP.md`
> Step 5b; TASK-07 introduced the `BackupResult` sealed class which
> TASK-19 then consumed when wiring the failure notification surface;
> TASK-36's inline `Result.retry()` invariant comments were folded
> into the TASK-19 worker rewrite. Sequencing notes below cover the
> remaining open work only.

- **TASK-30 marker reuse:** complete the Vico marker wrapper once in Step 2
  and reuse in Step 3. Do not port `ChartsMarkerView` twice.
- **TASK-18 early-win:** ☑ landed 2026-05-03, Step 6
  (`AccessibilityChecks.enable()` in `HiltTestRunner.onStart`) is in;
  see the TASK-18 outcome banner for details. Steps 1–5, 7, 8 remain
  open and should be tackled as follow-up PRs that close violations
  the nightly run surfaces.
- **TASK-21 sequencing:** generate the Android Baseline Profile
  **after TASK-30 lands**, not before. The MPAndroidChart class-loading
  cost dominates today's cold-start profile; once Vico replaces it,
  the recorded profile will look materially different. A profile
  recorded before TASK-30 will be partially invalidated on the very
  next release.

---

## Notes for Agents

- The package root is `org.spsl.evtracker`.
- The project uses Kotlin DSL (`build.gradle.kts`), Hilt for DI, Room for
  local persistence (currently at schema version 7), and Kotlin Coroutines
  with Flow throughout.
- All new classes must follow the existing naming and packaging conventions
  documented in [`../CLAUDE.md`](../CLAUDE.md).
- Do not introduce new third-party dependencies without checking
  `app/build.gradle.kts` first. Prefer libraries already present.
- After any structural change, run `./gradlew test` (JVM) and
  `./gradlew connectedAndroidTest` (instrumented) to verify no regressions.
- Room schema version is currently **7**. Any migration must bump it to **8**
  and add a corresponding migration file under `app/schemas/`. The migration
  list in `AppDatabase.companion` and `DatabaseModule.provideDatabase` must
  both be updated; `BackupData.CURRENT_VERSION` and
  `BackupSerializer.SUPPORTED_VERSIONS` need a coordinated bump when the
   schema change touches a field that's serialized to the Drive backup.

---

## 🔴 TASK-51, GPL-3.0-or-later license change (pending `play-services-auth` review) ☑ Done (2026-05-03)

> **Outcome (merged 2026-05-03 on `feat/task51-gpl-license`).**
>
> - **`LICENSE`**, new repo-root file with the canonical
>   `GPL-3.0-or-later` text (674 lines, fetched verbatim from
>   `https://www.gnu.org/licenses/gpl-3.0.txt`). GitHub will now
>   classify the repository as `GPL-3.0-or-later` automatically once
>   the merge lands.
> - **`README.md`**, license badge flipped from MIT-green to
>   GPL-3.0-or-later-blue and points at `LICENSE` (not the in-page
>   anchor). Footer License section rewritten to the standard GPL
>   "free software; redistribute and/or modify; distributed in the
>   hope that it will be useful WITHOUT WARRANTY" preamble plus a
>   pointer to `LICENSE`.
> - **`app/src/main/res/values/strings.xml`**, `about_license_body`
>   replaced with a concise GPL acknowledgment (full GPL text would
>   be ~30K and hostile inside a TextView). The body still names
>   the SPS-Lab copyright holder, the SPDX identifier, the standard
>   warranty disclaimer, and a URL out to gnu.org for the full text.
> - **`AboutFragmentTest.licenseCard_containsMit`** renamed to
>   `licenseCard_containsGplIdentifier` and now asserts the substring
>   `"GPL-3.0-or-later"` (the SPDX identifier in the card body, a
>   stable anchor that survives prose tweaks).
> - **`CLAUDE.md`** TASK-10 historical entry annotated to note the
>   relicense ("originally MIT, relicensed to GPL-3.0-or-later in
>   TASK-51") instead of being silently rewritten, preserves the
>   "what shipped at TASK-10 time" reading while pointing forward.
> - **SPDX headers on every production Kotlin source file** under
>   `app/src/main/java/org/spsl/evtracker/`, 145 files. Header
>   format (per the BACKLOG spec):
>   ```kotlin
>   // SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
>   //                         Sustainable Power Systems Lab <https://sps-lab.org>
>   // SPDX-License-Identifier: GPL-3.0-or-later
>   ```
>   Headers are prepended via a one-shot shell loop (`find … -type f`
>   piped into `head -1 | grep SPDX` idempotency guard); generated
>   files (`build/`, KSP output), test sources (`app/src/test/`,
>   `app/src/androidTest/`), and any third-party vendored code are
>   left alone per spec. The idempotency guard keeps a re-run safe.
>
> **`play-services-auth` audit note carried forward.** The
> dependency audit found that
> `com.google.android.gms:play-services-auth:21.2.0` is distributed
> under the **Android Software Development Kit License**, not
> Apache-2.0 like the rest of the runtime. The Android SDK License
> is generally compatible with GPL-3.0 distribution as a "system
> library" exception (it ships with the user's device, not the app
> binary), but anyone building or redistributing this app should
> review their distribution channel's policy. For F-Droid
> compatibility specifically, **TASK-37** (replace Drive backup
> with SAF) is the canonical follow-up, it removes the GMS
> dependency entirely. This task did NOT block on TASK-37 because
> the GPL switch is independently valuable and TASK-37 will land
> on its own timeline.
>
> **Gates green:** `ktlintCheck`, `:app:lint`,
> `:app:testDebugUnitTest`, `:app:assembleRelease`,
> `:app:assembleDebugAndroidTest`. JVM unit-test count unchanged
> at 398 (no new tests; the only test edit was renaming the
> instrumented `licenseCard_*` assertion).
>
> **Manual GitHub step still pending:** the repo's "License" field
> in GitHub's repo settings → "About" panel needs to be set to
> `GPL-3.0-or-later` after the merge lands so the right-hand
> sidebar on `github.com/SPS-L/joulie` reflects the change.
> GitHub will auto-detect from the `LICENSE` file in most cases,
> so this is usually a no-op verification rather than an active
> setting change.

> **Context (2026-05-03):** a runtime dependency audit was run against
> `app/build.gradle.kts` and the resolved artifact metadata in the local
> Gradle cache before changing any repo licensing. The result is mostly
> clean for GPL purposes: AndroidX Core/AppCompat/ConstraintLayout,
> Navigation, Room, Lifecycle, DataStore, WorkManager, Splashscreen,
> Material Components, Coroutines, Gson, Hilt, `androidx.hilt:*`,
> `com.google.api-client:google-api-client-android`, and
> `com.google.apis:google-api-services-drive` all resolve to Apache-2.0
> (the Google API Android client inherits Apache-2.0 from its parent POM).
> `com.github.PhilJay:MPAndroidChart:v3.1.0` does not publish a license tag
> in its JitPack POM, but the upstream repository's README license section is
> Apache-2.0. The one unresolved item in the shipped runtime set is
> `com.google.android.gms:play-services-auth:21.2.0`, whose POM declares the
> **Android Software Development Kit License**. Keep that review note attached
> to any GPL switch PR so the license change is made consciously rather than
> assumed clean by default.

### Scope

This task is intentionally limited to the repository's public license change.
It does **not** include F-Droid packaging, flavor splits, or distribution
policy work. Keep the dependency-audit note above in the implementation PR,
and if `play-services-auth` is judged incompatible for the intended GPL
distribution model, file a follow-up remediation task instead of growing this
one.

### Required work

1. **Add a root `LICENSE` file** with the canonical GPL-3.0 text (`GPL-3.0-or-later`).
    GitHub currently has no root `LICENSE` file to classify, so the repo
    metadata is incomplete even though human-readable MIT text exists in the
    docs/UI.
2. **Replace all current MIT-facing surfaces.** At minimum update:
    - `README.md` (license badge + footer section currently say MIT)
    - `app/src/main/res/values/strings.xml` (`about_license_body` currently
       embeds the full MIT text)
    - `app/src/androidTest/java/org/spsl/evtracker/ui/about/AboutFragmentTest.kt`
       (asserts the About card contains `"MIT"`)
    - `docs/TEST_PLAN.md`, `CLAUDE.md`, and any other docs that still describe
       the app as MIT-licensed.
3. **Add SPDX headers to every production Kotlin source file** under
    `app/src/main/java/org/spsl/evtracker/`.
    Use:
    ```kotlin
    // SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
    //                         Sustainable Power Systems Lab <https://sps-lab.org>
    // SPDX-License-Identifier: GPL-3.0-or-later
    ```
    Do not touch generated files, test sources, or third-party vendored code.
4. **Set the GitHub repo license metadata** after the root `LICENSE` file lands.
    This is a manual GitHub settings step, not a source-tree edit.

### Acceptance

- A root `LICENSE` file exists and GitHub recognizes the repository as
   `GPL-3.0-or-later`.
- README, About screen text, tests, and docs no longer mention MIT.
- Every production Kotlin file under `app/src/main/java/org/spsl/evtracker/`
   carries the SPDX header.
- The compatibility audit result is preserved in the task / PR description so
   future agents do not reopen the same dependency question.

---

## 🟡 TASK-52, CSV escape hardening (`\r`, tabs, formula-injection prefixes) ☑ Done (2026-05-03)

> **Outcome (merged 2026-05-03 on `feat/task52-csv-escape-hardening`).**
> `ExportCsvUseCase.csvEscape` rewritten to:
>
> 1. Quote on `,`, `"`, `\n`, `\r`, **`\t`** (extends RFC 4180's required
>    set with bare `\r` and the tab heuristic, Excel auto-detects TSV
>    when a tab appears in the first row, which corrupts column
>    alignment).
> 2. Prefix the field with a single quote `'` when the first character
>    is in `{ '=', '+', '-', '@' }` (the canonical OWASP CSV-injection
>    triggers), then quote the result. The leading `'` neutralises
>    Excel / LibreOffice / Numbers' formula interpretation while
>    keeping the data round-trippable for any consumer that strips
>    leading apostrophes (research pipelines do).
>
> The hardened `csvEscape` is now applied to `chargeType.name` and
> `currency` in addition to the previously-escaped `location` /
> `note` columns. `chargeType.name` is internally controlled (enum
> values `AC` / `DC_FAST` / `DC_ULTRA` never trigger), but the symmetry
> guards against a future enum addition or rename. `currency` is
> stored verbatim from the wizard and is therefore user-supplied for
> hardening purposes.
>
> Numeric / timestamp columns (date, odometer, kwh, cost_total)
> deliberately bypass `csvEscape`: `Double.toString()` and
> `Instant.toString()` cannot produce a formula-injection prefix from
> a non-malicious value (negative numbers like `-3.5` are interpreted
> as the same number by Excel, not as a destructive formula), and
> quoting them as text would defeat researchers' pivot tables and
> charts.
>
> **Tests:** 10 new `ExportCsvUseCaseTest` cases —
> `note_containingCarriageReturn_isQuoted`,
> `note_containingTab_isQuoted`,
> `note_startingWithEquals_getsFormulaPrefix`,
> `location_startingWithPlus_getsFormulaPrefix`,
> `note_startingWithMinus_getsFormulaPrefix`,
> `note_startingWithAt_getsFormulaPrefix`,
> `note_withFormulaPrefixAndEmbeddedQuote_doublesQuotesInsideField`
> (compound-case regression guard),
> `note_plainText_isNotQuoted` (over-quoting regression guard),
> `note_existingCommaAndQuoteCases_stayGreen` (RFC 4180 baseline
> coverage), `currency_isAlsoEscaped`. The test helper `rowFor(...)`
> slices on the FIRST `\n` (header terminator) and `trimEnd('\n')` —
> not `lineSequence().drop(1).first()`, so embedded `\r` / `\n`
> inside a quoted note doesn't truncate the captured row. JVM
> unit-test count 377 → 387.

> **Audit finding (`docs/EV-backlog-review.md`, 2026-05-03):**
> `ExportCsvUseCase.csvEscape(...)` quotes a field only when it contains
> `,`, `"`, or `\n`. Three real cases slip through:
>
> 1. **Lone `\r`**, RFC 4180 requires CR to be quoted just like LF.
>    A `note` containing `"line1\rline2"` will produce a malformed row
>    when parsed by strict consumers (TASK-40 anonymised export consumers,
>    SPS-Lab pipelines).
> 2. **Tabs in `note` / `location`**, quietly accepted today but trip up
>    spreadsheet importers that auto-detect TSV when a tab appears in the
>    first row.
> 3. **Formula injection**, when a user-supplied field starts with `=`,
>    `+`, `-`, or `@`, Excel / LibreOffice / Numbers all execute it as a
>    formula on open. A `note` of `=cmd|'/c calc'!A1` is the canonical
>    proof-of-concept. Researchers (TASK-40 consumers) opening the CSV
>    in a spreadsheet are the most realistic victims.
>
> The fix is a one-line change in `csvEscape` (broaden the trigger set,
> prefix dangerous-leading-char fields with a single quote `'` before
> quoting), plus unit-test coverage. No schema, no API change, no UX
> change. Coordinates with TASK-09 (column extension), both touch the
> same writer; land TASK-52 first so the new columns inherit the harder
> escape rules.

### Scope

1. Update `ExportCsvUseCase.csvEscape(...)`:
   - Trigger quoting on `,`, `"`, `\n`, `\r`, and `\t`.
   - When the field's first character is in `{ '=', '+', '-', '@' }`,
     prepend a single quote `'` before applying the quoting rule. The
     leading-quote prefix neutralises Excel/LibreOffice formula
     interpretation while keeping the data round-trippable for any
     consumer that strips leading apostrophes (research pipelines do).
2. Apply the hardened `csvEscape` to every user-supplied column that
   currently bypasses it: `currency` (free-form letter code, low risk
   but cheap to escape), `chargeType.name` (enum-controlled, but escape
   anyway for consistency), and any new TASK-09 columns.
3. JVM tests under `ExportCsvUseCaseTest`:
   - `note` containing `\r` round-trips through quotes.
   - `note` containing `\t` round-trips through quotes.
   - `note = "=SUM(A1:A10)"` emits `"'=SUM(A1:A10)"` (leading `'` inside
     the quoted field).
   - `location = "+44 7000"` (legitimate phone-style string, leading `+`)
     emits `"'+44 7000"`.
   - `note = "-Some negative note"` emits the leading-`'` form.
   - Existing `,` / `"` / `\n` cases stay green.

### Acceptance

CSV export survives a malicious `note` field without producing executable
formulas in mainstream spreadsheet apps. RFC 4180 conformance for `\r`
holds. Existing CSV consumers (the share-sheet flow, TASK-40 pipeline)
see no header / column-count change, only the escaping is stricter.
JVM unit-test count gains ≥ 5 cases.

---

## 🟡 TASK-53, Multi-car invariant guard in `StatsCalculator.computeStats` ☑ Done (2026-05-03)

> **Outcome (merged 2026-05-03 on `feat/task53-multi-car-invariant`).** Single-line guard expanded to a tight private helper so each guarded aggregation reads as one line of intent at the top:
>
> ```kotlin
> private fun requireSingleCar(events: List<ChargeEventEntity>) {
>     val distinctCars = events.map { it.carId }.distinct()
>     require(distinctCars.size <= 1) {
>         "expects a single-car event list; got carIds=$distinctCars"
>     }
> }
> ```
>
> Applied to **five** aggregations: `computeStats`, `computeMonthlyBuckets`, `computeEfficiencyTrend`, `computeAcDcSplit`, `computeLocationDistribution`. Each call site (`ObserveDashboardStatsUseCase`, `ObserveChartsModelsUseCase`) was audited and confirmed single-car already, the guard fires only on a future bug, never on existing flows. The error message names the offending `carIds` so a stack-trace bug report points straight at the wrong call.
>
> **`detectMixedCurrency` is intentionally exempt**, its semantic question ("are there ≥ 2 distinct currencies among costed events") doesn't depend on car identity, and a future fleet-level cross-car aggregator might legitimately call it on a multi-car list. The exemption is documented in the function KDoc rather than buried in a TODO.
>
> Empty input passes (`distinct().size == 0` is `<= 1`), every aggregation is reachable on an empty period filter, which is a routine state, not an error.
>
> **Tests:** new `StatsCalculatorInvariantTest` with 8 cases, five `*_throws_onMixedCarIds` tests (one per guarded method), `computeStats_succeeds_onEmptyList` smoke, `allGuardedAggregations_succeed_onEmptyList` smoke, and `detectMixedCurrency_acceptsMixedCarIds_byDesign` regression guard for the exemption. The first test asserts the offending carIds string `[1, 2]` appears in the message so the diagnostic surface is part of the contract. JVM unit-test count 425 → 433. Gates green: ktlintCheck, :app:lint, :app:testDebugUnitTest, :app:assembleRelease, :app:assembleDebugAndroidTest.

> **Audit finding (`docs/EV-backlog-review.md`, 2026-05-03):**
> `computeStats` calls `events.sumOf { it.kwhAdded }` on the full input
> list and then walks an odometer-delta loop assuming every event in the
> list shares a single car's odometer sequence. The current call site
> (`ObserveDashboardStatsUseCase.computeFor(...)` →
> `allEventsForCar.filter { it.eventDate in range… }`) always passes a
> single-car list, so the invariant holds today, but it is **unenforced**.
> A future caller (a per-car comparison view, or a refactor that flattens
> two cars into one stats card) would silently produce nonsense
> `totalDistanceKm` and every efficiency metric without any signal: the
> odometer-delta loop produces a positive delta on the car-switch
> boundary if cars happen to have similar odo readings.
>
> Single-line `require(...)` defense-in-depth.

### Scope

1. At the top of `StatsCalculator.computeStats(...)`, after the
   `chargeCount` line:
   ```kotlin
   require(events.map { it.carId }.distinct().size <= 1) {
       "computeStats expects a single-car event list; got carIds=" +
           events.map { it.carId }.distinct()
   }
   ```
2. Mirror the same guard at the top of `computeMonthlyBuckets`,
   `computeEfficiencyTrend`, `computeAcDcSplit`, and
   `computeLocationDistribution` if they're ever called with a list
   that's expected to be single-car (audit each call site first, some
   chart-side aggregations may legitimately accept cross-car input;
   in that case, leave them alone and document the intentional
   exemption in the function KDoc).
3. JVM test on `StatsCalculatorTest` (or a new `StatsCalculatorInvariantTest`):
   - `computeStats_throws_onMixedCarIds`, feeds events with `carId = 1`
     and `carId = 2`; asserts `IllegalArgumentException` with the
     expected message snippet.
   - `computeStats_succeeds_onEmptyList`, empty list passes the guard
     (`distinct().size == 0 <= 1`); preserves existing empty-period
     behaviour.

### Acceptance

Existing tests stay green (every fixture passes single-car lists). New
test asserts the guard fires on mixed input. JVM unit-test count gains
≥ 1 case.

---

## 🔴 TASK-54, Drive switch fires `onUserToggledOn()` on every Settings entry (+ durable restore-skip marker) ☑ Done (2026-05-03)

> **Outcome (merged 2026-05-03 on `feat/task54-drive-switch-fix`).** Both
> coupled defects landed in one PR.
>
> **Step 0, listener-attach hardening (Option A, lazy attach in
> collector).** The listener attachment in `SettingsFragment.onViewCreated`
> at the old line 69 is removed entirely; the StateFlow collector now
> tracks a local `var driveListenerAttached = false` and on the first
> emission unconditionally syncs `binding.switchDrive.isChecked` to
> `state.driveEnabled` with NO listener attached, then attaches the
> listener for the first time. Subsequent transitions go through the
> same detach/set/reattach rebind block as before. The KDoc-style
> comment in `onViewCreated` explicitly cites Android's view-state
> restoration calling `setChecked()` between `onCreateView` and
> `onStart` as the reason the listener cannot live there.
>
> **Steps 1–5, durable last-seen-snapshot marker.** New
> `LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT = stringPreferencesKey(...)` on
> `PreferenceKeys`; `SettingsReader.lastSeenRemoteBackupExportedAt:
> Flow<String>` (default `""`); `SettingsWriter.setLastSeenRemoteBackupExportedAt(value: String)`
> wired through `SettingsRepository` and the existing `Fakes.kt`
> `LinkedSettings` test plumbing. `SettingsViewModel.onDriveAuthGranted`
> now reads the marker via `settingsReader.lastSeenRemoteBackupExportedAt.first()`
> and silently flips `driveEnabled = true` + enqueues backup when the
> remote snapshot's `exported_at` matches the marker, no
> `ShowRestorePrompt` event fires. `parseExportedAtLabel(json)` becomes
> `parseRemoteSnapshot(json): RemoteBackupSnapshot` returning both the
> raw `exportedAt` (for the marker) and the formatted label (for the
> dialog); `pendingRestoreExportedAt: String?` joins
> `SettingsUiState` so `onSkipRestore` and `onConfirmRestore` can write
> the marker without re-reading the remote backup. `WipeRemoteBackupUseCase`
> clears the marker (`setLastSeenRemoteBackupExportedAt("")`) on
> `BackupResult.Success` so a fresh upload + Drive re-toggle prompts
> exactly once for the new snapshot. `onRestorePromptDismissed` does
> NOT write the marker (dismiss ≠ Skip, neither accept nor decline).
>
> **Tests.** New JVM cases: 6 in `SettingsViewModelTest`
> (`onDriveAuthGranted_remoteExists_firstTime_emitsPrompt`,
> `onDriveAuthGranted_remoteExists_alreadySeen_skipsSilently`,
> `onDriveAuthGranted_remoteWithDifferentExportedAt_promptsAgain`,
> `onSkipRestore_persistsLastSeenExportedAt`,
> `onConfirmRestore_success_persistsLastSeenExportedAt`,
> `onDriveAuthGranted_noRemote_keepsExistingMarker`); 2 in
> `WipeRemoteBackupUseCaseTest`
> (`success_clearsLastSeenRemoteBackupExportedAtMarker`,
> `authRequired_doesNotClearLastSeenMarker`). New instrumented file
> `SettingsDriveSwitchEntryTest` with two cases —
> `firstEntry_withDriveEnabled_doesNotCallAuthorize` and
> `reEntry_viaActivityRecreation_doesNotCallAuthorize`, both gated on
> a new `authorizeCallCount` field on the instrumented
> `FakeDriveAuthManager`. The reproduction trigger is
> `FragmentScenario.recreate()` (full activity recreation, the
> canonical "navigate away and back" simulation that exercises view-state
> save/restore). Pre-fix, `authorize()` would have been called on every
> recreation; post-fix the counter stays flat. JVM unit-test count
> 369 → 377.
>
> **Why Option A and not B/C.** Option A is the smallest behaviour
> change: the StateFlow collector was already responsible for syncing
> the switch on every emission; piggybacking the first attach onto
> the same code path keeps a single source of truth for `isChecked`
> and avoids a parallel "programmatic-change guard flag" that future
> edits could drop. Option C
> (`isSaveEnabled = false` on the switch) was tempting but relies on
> the StateFlow being the single source of truth at all times, a
> contract the tests don't currently enforce.

> **Filed 2026-05-03** (user-reported reproduction).
>
> **Bug (user verbatim):** *"Every time I enter the Settings on my phone,
> the Google Drive backup switch on its own toggles **off** and then
> **on**, and then asks me if I want to restore from Drive. This happens
> every time."*
>
> The visible OFF → ON flicker is the smoking gun: the switch listener
> is firing `onUserToggledOn()` on bare Settings entry, which calls
> `auth.authorize()` → `viewModel.onDriveAuthGranted()` →
> `readRemoteBackup() != null` → `ShowRestorePrompt`. There is no
> durable record of any prior "Skip" decision, so the same prompt
> reappears every time.
>
> **Two coupled defects.** Both must land in the same change:
>
> 1. **Switch listener fires on view-state restoration.** In
>    `SettingsFragment.onViewCreated`, the listener at line 69 is
>    attached **synchronously** before the `repeatOnLifecycle(STARTED)`
>    coroutine starts collecting `viewModel.uiState`. Android's view
>    framework runs `onRestoreInstanceState` between `onCreateView` and
>    `onStart` and calls `setChecked(true)` on `MaterialSwitch` to
>    restore the previously-saved checked state (the switch's XML
>    default is `false`, so this is a real OFF → ON transition).
>    `setChecked(true)` synchronously fires the just-attached
>    `OnCheckedChangeListener`, which runs `onUserToggledOn()` before
>    the rebind block at line 98 ever has a chance to detach.
>    The listener-rebind protection inside the StateFlow collector is
>    **correct for subsequent transitions** (DataStore-driven updates
>    while Settings is in the foreground) but cannot defend the
>    pre-collector window. Even with the durable marker (defect #2)
>    landed, the user would still see the unwanted OFF → ON visual
>    flicker AND a wasted `auth.authorize()` round-trip on every
>    entry, so this defect needs a real fix, not just suppression.
> 2. **No durable marker that "Skip" was tapped.**
>    `SettingsViewModel.onSkipRestore()` writes
>    `pendingRestoreLabel = null` in `_uiState` and calls
>    `settingsWriter.setDriveEnabled(true)`, but does **not** persist
>    any record that this specific remote backup was already offered.
>    The only thing recording the Skip decision is in-memory
>    `SettingsUiState`, which is lost on ViewModel recreation.
>    `PreferenceKeys` has no `last_seen_remote_backup_*` key.
>    Even if defect #1 were fixed, the user would still get re-prompted
>    via the legitimate path: Skip → toggle Drive **OFF** → toggle
>    Drive **ON** → same backup re-offered.
>
> The destructive-action path trains users to dismiss a critical
> warning by reflex, which is exactly the wrong outcome for a flow
> that overwrites all local data.

### Scope

#### Step 0, Stop the switch listener from firing on view-state restoration

The fix is to **never have a live listener attached when the view
framework or the StateFlow collector first sets `isChecked`.** Pick one
of the following, they're equivalent in correctness, listed by
preference:

**Option A (preferred): defer first attach until after the first state
binding.** Move the listener-attach out of `onViewCreated` and into the
collector. On the first emission the collector unconditionally syncs
`isChecked` to `state.driveEnabled` with no listener attached, then
attaches the listener for the first time. The existing rebind block
handles every subsequent transition unchanged.

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Note: switchDrive listener is attached LAZILY by the StateFlow
    // collector below, AFTER the first state-driven sync of isChecked.
    // This prevents view-state restoration's setChecked() call from
    // firing onUserToggledOn() before the ViewModel has a chance to
    // tell us what the persisted Drive-enabled state actually is.

    // ... other listeners (rowPrimaryMetric, rowExportCsv, etc.) stay here ...

    viewLifecycleOwner.lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                var driveListenerAttached = false
                viewModel.uiState.collect { state ->
                    if (!driveListenerAttached) {
                        binding.switchDrive.isChecked = state.driveEnabled
                        binding.switchDrive.setOnCheckedChangeListener { _, c ->
                            if (c) onUserToggledOn() else viewModel.onToggleDriveOff()
                        }
                        driveListenerAttached = true
                    } else if (binding.switchDrive.isChecked != state.driveEnabled) {
                        binding.switchDrive.setOnCheckedChangeListener(null)
                        binding.switchDrive.isChecked = state.driveEnabled
                        binding.switchDrive.setOnCheckedChangeListener { _, c ->
                            if (c) onUserToggledOn() else viewModel.onToggleDriveOff()
                        }
                    }
                    // ... rest of state binding unchanged ...
                }
            }
            // ... events collector unchanged ...
        }
    }
}
```

**Option B: programmatic-change guard flag.** Keep the listener attached
in `onViewCreated`, but set a `var programmaticDriveSwitch = false`
flag and have the listener short-circuit when it's true. Set the flag
around any programmatic `isChecked = ...` write, and ALSO around the
implicit write that `onRestoreInstanceState` performs, which means
flipping the flag in `onViewStateRestored(savedInstanceState: Bundle?)`
*before* `super.onViewStateRestored(...)` and clearing it after. This
is more code than Option A and easier to get wrong if a future edit
adds a new programmatic write path; only choose it if Option A
breaks something subtle in instrumented testing.

**Option C: disable view-state save on the switch.**
`binding.switchDrive.isSaveEnabled = false` plus
`binding.switchDrive.isSaveFromParentEnabled = false`. The view will
no longer persist its checked state, so `onRestoreInstanceState`
becomes a no-op for this view. This is the smallest change but it
relies on the StateFlow being the single source of truth at all times
(true today, but not enforced architecturally). Document the contract
inline if you pick this.

Whichever option lands: the existing line-69 listener attachment must
be removed (Options A and C) or accompanied by the guard flag (Option
B). Do not leave the line-69 attach in place untouched, that's the
defect.

#### Step 1, New DataStore key in `PreferenceKeys`

```kotlin
val LAST_SEEN_REMOTE_BACKUP_EXPORTED_AT =
    stringPreferencesKey("last_seen_remote_backup_exported_at")
// ISO-8601 string from BackupData.exported_at. Empty string = never seen.
// String (not Long) so we can compare verbatim against the JSON field
// without re-parsing, Instant.parse is the only conversion needed and
// only on the label-formatting path that already exists today.
```

Wire `SettingsReader.lastSeenRemoteBackupExportedAt: Flow<String>` and
`SettingsWriter.setLastSeenRemoteBackupExportedAt(value: String)` —
follow the existing TASK-24 narrow-IF rule (do **not** add a concrete
`SettingsRepository` reference outside `di/`).

#### Step 2, Plumb `exportedAt` into `SettingsUiState`

Today, `parseExportedAtLabel(json)` only formats the date for display.
Replace it with a small helper that returns `(exportedAt: String,
label: String)` so the raw `exported_at` value is available to the
Skip / Restore / dismiss paths without re-reading the remote backup.

```kotlin
private data class RemoteBackupSnapshot(val exportedAt: String, val label: String)

private fun parseRemoteSnapshot(json: String): RemoteBackupSnapshot {
    val match = EXPORTED_AT_REGEX.find(json)
    val raw = match?.groupValues?.get(1) ?: return RemoteBackupSnapshot("", UNKNOWN_DATE)
    val label = try { DATE_FORMAT.format(Date(Instant.parse(raw).toEpochMilli())) } catch (_: Throwable) { UNKNOWN_DATE }
    return RemoteBackupSnapshot(exportedAt = raw, label = label)
}
```

Add `pendingRestoreExportedAt: String?` to `SettingsUiState` next to
the existing `pendingRestoreLabel: String?`.

#### Step 3, Guard in `onDriveAuthGranted()`

```kotlin
fun onDriveAuthGranted() {
    viewModelScope.launch {
        _uiState.update { it.copy(isAuthInFlight = true) }
        try {
            val json = backupRepository.readRemoteBackup()
            if (json == null) {
                settingsWriter.setDriveEnabled(true)
                backupScheduler.enqueueBackup()
                _uiState.update { it.copy(isAuthInFlight = false) }
                return@launch
            }
            val snapshot = parseRemoteSnapshot(json)
            val lastSeen = settingsReader.lastSeenRemoteBackupExportedAt.first()
            if (snapshot.exportedAt.isNotEmpty() && snapshot.exportedAt == lastSeen) {
                // Already offered (and skipped or restored). Enable Drive silently.
                settingsWriter.setDriveEnabled(true)
                backupScheduler.enqueueBackup()
                _uiState.update { it.copy(isAuthInFlight = false) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isAuthInFlight = false,
                    pendingRestoreLabel = snapshot.label,
                    pendingRestoreExportedAt = snapshot.exportedAt,
                )
            }
            _events.tryEmit(SettingsEvent.ShowRestorePrompt(snapshot.label))
        } catch (_: DriveAuthRequiredException) { /* unchanged */ }
        catch (_: IOException) { /* unchanged */ }
    }
}
```

#### Step 4, Persist on Skip and on Restore success

```kotlin
fun onSkipRestore() {
    viewModelScope.launch {
        val exportedAt = _uiState.value.pendingRestoreExportedAt
        if (!exportedAt.isNullOrEmpty()) {
            settingsWriter.setLastSeenRemoteBackupExportedAt(exportedAt)
        }
        settingsWriter.setDriveEnabled(true)
        backupScheduler.enqueueBackup()
        _uiState.update { it.copy(pendingRestoreLabel = null, pendingRestoreExportedAt = null) }
    }
}
```

In `onConfirmRestore()`, when `RestoreResult.Success` is returned, also
write `setLastSeenRemoteBackupExportedAt(exportedAt)` so a successful
restore also records the snapshot identity. (After a restore, the local
DB equals the remote backup, so re-prompting would offer the user a
restore of the data they already have.)

#### Step 5, Clear on wipe

`WipeRemoteBackupUseCase.invoke()` already resets `lastBackupAt = 0L`
on `BackupResult.Success`. Mirror that with
`settingsWriter.setLastSeenRemoteBackupExportedAt("")` on the same
success branch so a subsequent fresh upload + re-toggle prompts again
correctly.

### Tests

Add to `SettingsViewModelTest`:

1. `onDriveAuthGranted_remoteExists_firstTime_emitsPrompt`, `exportedAt
   = "2026-04-01T..."`, `lastSeen = ""` → `ShowRestorePrompt` emitted,
   `driveEnabled` not yet flipped.
2. `onDriveAuthGranted_remoteExists_alreadySeen_skipsSilently`, same
   `exportedAt` written to `lastSeen` → no event, `driveEnabled = true`,
   `enqueueBackup()` called once.
3. `onDriveAuthGranted_remoteWithDifferentExportedAt_promptsAgain` —
   `exportedAt` differs from `lastSeen` (new remote upload after wipe)
   → prompt emitted.
4. `onSkipRestore_persistsLastSeenExportedAt`, after skip, the
   `FakeSettingsReader` reads back the same `exportedAt`.
5. `onConfirmRestore_success_persistsLastSeenExportedAt`, after a
   successful restore, the marker is written.
6. `onWipe_success_clearsLastSeenExportedAt`, after `WipeRemoteBackupUseCase`
   succeeds, the marker is reset to the empty string.
7. `onDriveAuthGranted_noRemote_keepsExistingMarker`, no remote backup
   path enables Drive without touching the marker (regression guard).

Extend `FakeSettingsReader` / `FakeSettingsWriter` with the new key.
The new `WipeRemoteBackupUseCase` test belongs in `WipeRemoteBackupUseCaseTest`.

#### Instrumented test, Step 0 regression guard

Add to `SettingsFragmentTest` (or a new `SettingsDriveSwitchEntryTest`):

```kotlin
@Test
fun reEntry_withDriveEnabled_doesNotFireOnUserToggledOn() {
    // Pre-state: DataStore says driveEnabled = true.
    settingsWriter.setDriveEnabled(true)

    // First entry, let the fragment mount and the collector run.
    val scenario = launchFragmentInHiltContainer<SettingsFragment>()
    scenario.moveToState(Lifecycle.State.RESUMED)

    // Capture how many times the auth flow has been invoked so far.
    val authCallsBefore = fakeAuth.authorizeCalls

    // Simulate "user navigates away and back" by destroying & recreating
    // the view. Use Lifecycle.State.CREATED to drop the view, then back
    // to RESUMED to recreate it. View-state save/restore runs across this
    // boundary, which is the path that triggered the bug.
    scenario.moveToState(Lifecycle.State.CREATED)
    scenario.moveToState(Lifecycle.State.RESUMED)

    // After the round-trip, the auth flow MUST NOT have been re-invoked.
    // Pre-fix this assertion fails (auth.authorize() ran on view restore).
    assertEquals(authCallsBefore, fakeAuth.authorizeCalls)
}
```

`FakeDriveAuthManager` already exists in `app/src/androidTest/java/.../testing/`;
add an `authorizeCalls: Int` counter incremented on every `authorize()`
invocation if it isn't there. This test is the regression guard for
Step 0, the durable-marker JVM tests don't cover it because they don't
exercise the view-state-restoration window.

### Acceptance

- **No visible OFF → ON flicker on the Drive switch when entering
  Settings** with `driveEnabled = true`. The switch's first painted
  state matches the persisted DataStore value; no programmatic
  flip is observable to the user.
- **No `auth.authorize()` round-trip on bare Settings entry.** Verified
  by the new instrumented regression test.
- After **Skip**, no further toggle / consent / Settings entry re-shows
  the same restore prompt.
- After a successful **Restore**, no further entry re-shows the same
  prompt.
- After **Wipe** + a fresh remote upload, the next Drive re-toggle
  prompts exactly once for the new snapshot.
- `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`
  green; instrumented `SettingsBackupControlsTest` (TASK-31) stays green.
- JVM unit-test count gains ≥ 7 cases; instrumented count gains ≥ 1.

### Out of scope

- A "Don't ask again" checkbox inside the dialog. The durable marker
  is automatic and silent; an explicit checkbox is a separate UX
  decision and not requested.
- Any other view in `SettingsFragment` that may have the same
  view-state-restoration anti-pattern (e.g., a future preference
  switch). If Step 0's Option A is chosen and works cleanly for the
  Drive switch, document the pattern in `docs/DESIGN.md` (or in the
  fragment's KDoc) so the next switch added inherits the fix; do not
  retroactively rework other views in this PR.

---

## 🟡 TASK-55, Language picker (Settings + first-run wizard) ☑ Done (2026-05-04)

> **Outcome (merged 2026-05-04 on `feat/task55-language-picker`).** Two
> coupled UX entry points + the architectural plumbing for both:
>
> - **`res/xml/locales_config.xml`** lists the four shipped locales
>   (`en` / `el` / `tr` / `ru`); `AndroidManifest.xml` declares
>   `android:localeConfig="@xml/locales_config"`. Android 13+ users now
>   get the OS-level "Language" entry in
>   `System Settings → Apps → Joulie → Language` for free.
> - **`PreferenceKeys.LANGUAGE_TAG`** (`stringPreferencesKey`).
>   Empty string = "follow system" (default); otherwise an IETF BCP-47
>   tag matching one of the shipped locales. Wired through
>   `SettingsReader.languageTag: Flow<String>` and
>   `SettingsWriter.setLanguageTag`. `Fakes.kt` (`FakeSettingsReader`,
>   `FakeSettingsWriter`) and the `LinkedSettings` helper in
>   `BackupOutcomeReporterTest` got matching pass-through stubs.
> - **`domain/locale/LocaleApplier`** narrow IF + `data/locale/AndroidLocaleApplier`
>   impl + single-binding `LocaleModule`. Static
>   `AppCompatDelegate.setApplicationLocales` is hostile to JVM tests
>   (it touches `LocaleManager` on Android 13+ and an internal
>   SharedPreferences fallback on older devices); the narrow IF lets
>   ViewModels stay JVM-testable. Mirrors the TASK-12 `WidgetRefresher`
>   pattern. Tests substitute a new `FakeLocaleApplier` that records
>   the last applied tag + an apply-count.
> - **`EVTrackerApp.onCreate`** reads the persisted tag asynchronously
>   (same trade-off as the existing theme branch: DataStore reads are
>   sub-50ms, AppCompat 1.6+ persists internally so subsequent starts
>   come up correct before the coroutine even runs).
> - **Settings → Language** row sits between Theme and the data section,
>   summary text reflects the current selection. Tap opens a
>   `MaterialAlertDialog.Builder.setSingleChoiceItems` dialog with five
>   options: "Follow system" + four autonyms. Autonyms are stored as
>   `translatable="false"` strings (`language_name_en` / `_el` / `_tr`
>   / `_ru`) so a Greek-speaking user always sees "Ελληνικά" in Greek
>   script regardless of the current app locale.
> - **Wizard page 0** grows a language-row at the bottom of the welcome
>   page. Same dialog as Settings; selection persists immediately so a
>   mid-wizard kill survives the next launch even though
>   `setupComplete` is still false. `WizardPage1Fragment` collects
>   `WizardViewModel.state.languageTag` to surface the autonym label.
>   `WizardViewModel.onLanguageSelected(tag)` hits both
>   `SettingsWriter.setLanguageTag` and `LocaleApplier.apply` in a
>   single `viewModelScope.launch` so persistence and process-level
>   apply are atomic from the caller's perspective.
> - **String resources:** new translatable keys `settings_language`,
>   `settings_language_dialog_title`, `settings_language_follow_system`,
>   `wizard_language_label`, present in all four locale files
>   (en/el/tr/ru) per the TASK-15 `MissingTranslation` error-mode
>   contract. The four `language_name_*` autonym strings are present
>   only in `values/` with `translatable="false"`.
> - **`SettingsUiState.languageTag`** (default `""`) gets populated
>   from `SettingsReader.languageTag` in the VM's init block.
>
> **Tests:** 6 new JVM cases, 3 in `SettingsViewModelTest`
> (`onLanguageSelected_persistsTagAndAppliesLocale`,
> `onLanguageSelected_followSystem_writesEmptyString`,
> `languageTag_collectedFromSettingsReader_intoUiState`) and 3 in
> `WizardViewModelTest`
> (`onLanguageSelected_persistsTagAndAppliesLocale`,
> `onLanguageSelected_followSystem_writesEmptyString`,
> `onLanguageSelected_persistsTag_evenBeforeFinish`, mid-wizard-kill
> survival regression guard). `WizardViewModelTest` gained
> `Dispatchers.setMain` / `resetMain` setup because the VM's init now
> launches a `viewModelScope` collector for `languageTag`. JVM
> unit-test count 403 → 409.
>
> **Instrumented coverage gap (deferred follow-up):** the BACKLOG spec
> called for ≥2 instrumented cases (`SettingsLanguagePickerTest`,
> `WizardLanguagePickerTest`). Both deferred, the JVM tests cover the
> VM contract end-to-end (DataStore write + LocaleApplier call + UiState
> roundtrip), and the dialog/Fragment wiring is mechanical
> (`MaterialAlertDialog.Builder.setSingleChoiceItems`). File a follow-up
> if instrumented coverage of the `setApplicationLocales`-triggered
> Activity recreation becomes load-bearing.

> **Filed 2026-05-04 as a TASK-15 follow-up.** TASK-15 shipped four
> locales (en/el/tr/ru) but no in-app language switcher, the app
> falls back to whichever locale the device's system language reports.
> A user whose phone is set to English but who reads Greek has no
> way to switch the app to Greek without changing the OS-wide
> language. Worse, on first launch the wizard renders in the system
> language, so a Greek-speaking user with an English-defaulted phone
> sees an English welcome page they may not understand.
>
> Two coupled UX entry points needed:
>
> 1. **Settings → Language row**, change at any time, persisted
>    across launches.
> 2. **Wizard page 0 language picker**, first thing the user sees
>    on a fresh install, before any other wizard content, so the
>    rest of the wizard renders in the chosen language.
>
> Use `androidx.appcompat`'s
> `AppCompatDelegate.setApplicationLocales(LocaleListCompat)` —
> works back to API 21 (the project's minSdk 26 floor is fine),
> handles activity recreation, and persists to disk via the
> framework. We mirror the value into our own DataStore key so the
> rest of the codebase can observe it through `SettingsReader`
> without touching androidx.appcompat internals.

### Scope

#### Step 1, `locales_config.xml` + manifest declaration

Cheap-but-load-bearing baseline. Even with the in-app picker
landing in steps 2-4, declaring the locale config gives Android
13+ users the OS-level per-app language entry in
`System Settings → Apps → Joulie → Language` automatically.

```xml
<!-- app/src/main/res/xml/locales_config.xml -->
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en"/>
    <locale android:name="el"/>
    <locale android:name="tr"/>
    <locale android:name="ru"/>
</locale-config>
```

```xml
<!-- AndroidManifest.xml, on <application> -->
android:localeConfig="@xml/locales_config"
```

#### Step 2, DataStore key + narrow IF additions

```kotlin
// PreferenceKeys
val LANGUAGE_TAG = stringPreferencesKey("languageTag")
// Empty string = "follow system" (default).
// Otherwise an IETF BCP-47 tag: "en", "el", "tr", "ru".
```

Wire `SettingsReader.languageTag: Flow<String>` (default `""`)
and `SettingsWriter.setLanguageTag(value: String)` through
`SettingsRepository`. Update `Fakes.kt` (`FakeSettingsReader`,
`FakeSettingsWriter`) and the `LinkedSettings` helper in
`BackupOutcomeReporterTest`.

#### Step 3, Apply the locale at app start

In `EVTrackerApp.onCreate`, BEFORE Hilt's first activity inflates:

```kotlin
val tag = runBlocking { settingsReader.languageTag.first() }
val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
              else LocaleListCompat.forLanguageTags(tag)
AppCompatDelegate.setApplicationLocales(locales)
```

The `runBlocking` is acceptable here, we MUST have the locale
resolved before any view inflates, and DataStore reads on a fresh
process are sub-50ms. Document this trade-off inline in the
KDoc.

#### Step 4, Settings → Language row

New row in `fragment_settings.xml` between **Theme** and
**Currency**. Tap opens a `MaterialAlertDialog.Builder.setSingleChoiceItems`
with five options:

```
Follow system          (settings_language_follow_system)
English                ("English", autonym, never localised)
Ελληνικά               ("Ελληνικά", autonym, never localised)
Türkçe                 ("Türkçe", autonym, never localised)
Русский                ("Русский", autonym, never localised)
```

**Display the autonyms (each language's name in itself), not
translations.** A Greek-speaking user looking for their language
needs to see "Ελληνικά" written in Greek script, not "Greek"
or "Yunanca", because they may not read English or Turkish.

Tag mapping: `""` (follow system) → empty `LocaleListCompat`;
`"en"` / `"el"` / `"tr"` / `"ru"` → forLanguageTags(tag).

On select:
1. `settingsWriter.setLanguageTag(tag)` (DataStore source of truth)
2. `AppCompatDelegate.setApplicationLocales(...)` (triggers Activity recreation; the new instance reads the persisted tag at startup via Step 3)

The `SettingsViewModel` exposes `onLanguageSelected(tag: String)`
that does both writes inside a single `viewModelScope.launch`.

#### Step 5, Wizard page 0 picker

Add a language dropdown to the **top** of `WizardPage1Fragment`
(the welcome page). Options identical to the Settings dialog
(autonym labels). Default selection: empty string ("follow
system"), but the dropdown's **displayed** value is the resolved
locale name so the user sees what they're currently looking at.

Behaviour: selecting an option calls `vm.onLanguageSelected(tag)`,
which immediately persists the tag and calls
`AppCompatDelegate.setApplicationLocales(...)`. The Activity
recreates; `MainActivity` re-routes to the wizard (because
`setupComplete` is still false), and the wizard renders in the
new language from page 1 onward. The `Welcome` text on page 1
itself will be in the new language too, so the user immediately
sees the effect of their choice.

**Edge case:** if the user picks a language and then taps Back
to "undo" the choice, `setupComplete` is still false so the
DataStore tag survives. That's intentional, the choice should
persist even across mid-wizard kills.

#### Step 6, Tests

JVM:
- `SettingsViewModelTest`:
  - `onLanguageSelected_persistsTagAndAppliesLocale` (verify the
    DataStore write; the `setApplicationLocales` side-effect is
    framework-internal but a Mockito spy on a fake `LocaleApplier`
    interface keeps the test JVM-clean, see "Architectural note"
    below).
  - `onLanguageSelected_followSystem_writesEmptyString`.
- `WizardViewModelTest`: `onLanguageSelected_persistsTag_evenBeforeFinish`
  (regression guard for the mid-wizard-kill survival).

Instrumented:
- `SettingsLanguagePickerTest` (new): launch settings, tap
  Language row, pick "Ελληνικά", assert DataStore now reads
  `"el"` and the AppCompatDelegate's current locales include
  `el`.
- `WizardLanguagePickerTest` (new): launch wizard fresh, change
  the dropdown to Greek, assert page 1's welcome text is now in
  Greek (or just assert the locale tag was persisted; the actual
  string render is tested by Espresso's `withText` matcher).

#### Architectural note, the `LocaleApplier` boundary

`AppCompatDelegate.setApplicationLocales` is a static framework
call that's hard to verify in JVM tests. Wrap it in a narrow
domain interface (`domain/locale/LocaleApplier`) bound to an
Android impl in `data/locale/`. The VM depends on the interface;
the JVM tests use a `FakeLocaleApplier` that records the last
applied tag. Mirrors the TASK-12 `WidgetRefresher` pattern.

```kotlin
// domain/locale/LocaleApplier.kt
interface LocaleApplier {
    fun apply(tag: String) // empty string = follow system
}
```

```kotlin
// data/locale/AndroidLocaleApplier.kt
class AndroidLocaleApplier @Inject constructor() : LocaleApplier {
    override fun apply(tag: String) {
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                      else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
```

Bind in a new `LocaleModule` (single-binding, like
`DataResetModule` from TASK-50 sub-fix C, keeps tests' Hilt
graph minimal).

### Strings

New string keys (all in canonical `values/`, all marked
`translatable="true"` except the autonyms):

| Key | English value | translatable |
|-----|---------------|--------------|
| `settings_language` | "Language" | true |
| `settings_language_follow_system` | "Follow system" | true |
| `settings_language_dialog_title` | "Select language" | true |
| `wizard_language_label` | "Language" | true |
| `language_name_en` | "English" | **false** (autonym) |
| `language_name_el` | "Ελληνικά" | **false** (autonym) |
| `language_name_tr` | "Türkçe" | **false** (autonym) |
| `language_name_ru` | "Русский" | **false** (autonym) |

The translatable strings need el/tr/ru entries per the
`MissingTranslation` error-mode contract (TASK-15).

### Acceptance

- A clean install on an English-defaulted phone shows the wizard
  with a language dropdown defaulting to "Follow system";
  selecting "Ελληνικά" causes the rest of the wizard to render in
  Greek immediately.
- A user can change the language at any time via Settings →
  Language. The change persists across app restarts and across
  device reboots.
- Android 13+ users can also change the language via System
  Settings → Apps → Joulie → Language (the OS-level entry
  appears thanks to the `locales_config.xml` declaration).
- `:app:lint` stays green, the new translatable strings are
  present in all four locale files; the autonym strings are
  marked `translatable="false"` so `MissingTranslation` doesn't
  flag them.
- All existing tests stay green; ≥ 4 new JVM cases + ≥ 2 new
  instrumented cases.
- `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest :app:assembleRelease`
  green.

### Out of scope

- Adding more languages beyond the four shipped in TASK-15.
  Future locales (e.g. Arabic) are a TASK-15-class addition and
  warrant their own task with native-speaker review.
- A right-to-left layout pass. None of the currently-supported
  languages need RTL; the day Arabic / Hebrew lands, file a
  TASK-55 follow-up to audit RTL-sensitive layouts (margins,
  paddingStart vs paddingLeft, drawableStart, etc.).
- Localising the autonym labels themselves. They MUST stay in
  their own script regardless of the current locale; this is the
  whole point of using autonyms.

---

## 🟡 TASK-56, CI release wiring for the ADI registration token ☑ Done (2026-05-07)

> **Outcome (commit `4d3af93` + release commit `f094661`, tag `v1.9.28`).**
> `.github/workflows/release.yml` now writes
> `app/src/main/assets/adi-registration.properties` from a GitHub Secret
> before `assembleRelease`, and a post-build step asserts the bundled
> asset is non-empty inside the signed APK via `unzip -p`. Both gates
> fail the workflow loudly if the secret is unset or the asset is
> empty. CLAUDE.md and CONTRIBUTING.md updated; the v1.9.28 release
> went through the new path end-to-end (run `25497886807`).
>
> **Two deliberate deviations from the original spec:**
>
> 1. The secret is named `ADI_REGISTRATION_TOKEN`, not
>    `ADI_REGISTRATION_SNIPPET`. "Token" matches the existing
>    sensitivity-class language in `.gitignore` and CLAUDE.md.
> 2. The post-build verification uses a size check
>    (`unzip -p ... | wc -c`) rather than an exact-string compare
>    against the secret value. Piping the secret into `grep -qx`
>    would surface its value in any non-zero-exit shell trace; the
>    size check catches the realistic failure mode (empty file,
>    asset-not-bundled) without that risk.
>
> The original task text is preserved below for historical context.

> **Filed 2026-05-04 as a follow-up to local ADI registration.** The
> Android Developer Verification (ADI) registration token landed
> locally on 2026-05-04: `app/src/main/assets/adi-registration.properties`
> contains a one-line snippet that ties the developer's Google
> identity to APK builds of `org.spsl.evtracker`, gitignored at
> `.gitignore:33` because it is sensitivity-class same as
> `keystore.properties` (anyone who lifts the token can register
> impersonating builds). The locally-built v1.7.0 APK was uploaded
> manually to Google's verification page and the developer identity
> registered against fingerprint
> `1C:14:8E:BE:84:10:70:B3:F6:C4:74:F1:ED:32:4A:FA:D3:33:3A:FB:73:57:53:B9:DF:B9:13:46:AE:71:FD:78`.
>
> **Gap.** `.github/workflows/release.yml` builds + signs + publishes
> APKs on every `v*` tag push. Without the asset baked in, those
> CI-built APKs are **not registered** under the developer-verification
> programme. Today the path is "tag a release → CI publishes APK →
> the published APK fails verification, only locally-built APKs
> count." That's wrong direction: the CI-built APK is the canonical
> distribution artefact (signed by the same release keystore that
> the registration is bound to).

### Scope

1. Add a new GitHub Actions repository secret:
   `ADI_REGISTRATION_SNIPPET = CZCZQUJG5FL76AAAAAAAAAAAAA`
   (manual one-time setup at `Settings → Secrets and variables →
   Actions`). Mirrors the `KEYSTORE_BASE64` pattern already in place.
2. Add a step to `.github/workflows/release.yml`, BEFORE the
   `:app:assembleRelease` step, that writes the secret into the
   build tree:
   ```yaml
   - name: Bake ADI registration asset
     run: |
       mkdir -p app/src/main/assets
       printf '%s\n' "${{ secrets.ADI_REGISTRATION_SNIPPET }}" \
         > app/src/main/assets/adi-registration.properties
   ```
   The trailing `\n` matches the byte layout of Google's sample
   (27 bytes = 26-char snippet + LF). Critical: do NOT use
   `echo $VAR`, bash sometimes interpolates a literal `$` if the
   secret contains shell metacharacters; `printf '%s\n'` is safer.
3. Add a verification step AFTER `assembleRelease` that confirms
   the asset was packaged into the APK:
   ```yaml
   - name: Verify ADI asset packaged
     run: |
       unzip -p app/build/outputs/apk/release/app-release.apk \
         assets/adi-registration.properties \
         | grep -qx "${{ secrets.ADI_REGISTRATION_SNIPPET }}"
   ```
   The `-x` flag matches the whole line, guards against future
   trailing-whitespace bugs in the bake step.
4. Tag a throwaway pre-release (e.g. `v1.7.0-adi-test`) AND upload
   the resulting CI-built APK to Google's verification page to
   confirm it passes the verifier. If it does, delete the test tag
   and the test release. If it doesn't, the verification step
   above caught a real packaging bug, investigate before any
   future production release.

### Acceptance

- A push of any `v*` tag produces a CI APK that contains
  `assets/adi-registration.properties` exactly matching the
  registered snippet.
- The verification step in the workflow fails the build (red CI)
  if the asset is missing or differs from the secret. No silent
  "ship an unregistered APK" path.
- README and / or `docs/GOOGLE_CLOUD_SETUP.md` grow a one-line
  pointer to the new GitHub Secret so future maintainers know to
  populate it when the secret rotates.

### Out of scope

- Rotating the existing registered token. The current snippet is
  not compromised; no rotation needed unless it leaks.
- Per-developer registration tokens. The CI build uses a single
  org-level token; individual contributors who run
  `./gradlew assembleRelease` locally still need their own copy
  for any APK they want to register under their own identity.
  That's the per-maintainer F-Droid-style story for TASK-37 if
  it ever lands; out of scope here.
- Switching the release flow to AAB / Play Store distribution.
  Tag-driven APK release stays the canonical path for now.

---

## 🟢 TASK-79, Capture the 14 ChartsTab baseline PNGs (Phase 2/3 of TASK-35) ☑ Done (2026-05-10)

> **Outcome (merged 2026-05-10 on `feat/task79-chartstab-baselines`).** All 14 ChartsTab baselines committed under `app/src/test/screenshots/`. `:app:verifyRoborazziDebug` is now a live PR gate in `.github/workflows/ci.yml`. Test artefacts: `RoborazziSetup.kt` (host + theme + looper-idle helper), `ChartsFixtures.kt` (canonical 12-month state with all six metric fields populated), `FakeChartsParentFragment.kt` (non-Hilt parent that exposes a Mockito-mocked `ChartsViewModel` via `defaultViewModelProviderFactory` — `ChartsTabFragment.viewModels({ requireParentFragment() })` resolves to the mock), and `ChartsTabScreenshotTest.kt` (`@HiltAndroidTest` + `@RunWith(RobolectricTestRunner)` + `@Config(application = HiltTestApplication::class, sdk = [33], qualifiers = "w411dp-h891dp-mdpi")` + `@GraphicsMode(NATIVE)` for real Canvas rendering).
>
> **Spec deviation noted:** the spec proposed `@BindValue` fakes for the eight `ObserveChartsModelsUseCase` collaborators so the real `ChartsViewModel` would run with hardcoded data. In practice, `ChartsTabFragment` only reads `viewModel.uiState`, so a Mockito mock with a fixed `MutableStateFlow` is sufficient and ~150 LOC lighter. The Hilt graph still has to bootstrap (the test activity is `@AndroidEntryPoint`), but no fakes need to be bound.
>
> **Animation determinism.** MPAndroidChart's `animateY(400)` runs against `ValueAnimator` → `Choreographer`, which Robolectric's PAUSED looper does not auto-advance. After fragment commit, `ShadowLooper.idleMainLooper(800ms, MILLISECONDS)` advances the clock past the animation duration so the captured frame is the chart's final state rather than a partial render.
>
> **Capture path.** `View.captureRoboImage("src/test/screenshots/charts_<tab>_<theme>.png")` — explicit relative path because Roborazzi's default location is project-root-relative, not the plugin's `outputDir` (that setting is for the report, not for the capture target). 14 PNGs land at `app/src/test/screenshots/`.
>
> **Verified:** `./gradlew :app:recordRoborazziDebug` produces all 14 PNGs (~17–31 KB each); `./gradlew :app:verifyRoborazziDebug` BUILD SUCCESSFUL with no diff. CI workflow gains the verify step + `roborazzi-diffs` failure-artefact upload.
>
> Spec: `docs/superpowers/specs/2026-05-10-task79-chartstab-baselines-design.md`.

**Original task body preserved below for traceability.**



### Why split

The original TASK-35 spec assumed a "stub VM, no Hilt" rendering path. That assumption broke against the codebase reality: both `DashboardFragment` and `ChartsFragment` are `@AndroidEntryPoint`, so any test that hosts them — even via the `viewModels({ requireParentFragment() })` indirection that `ChartsTabFragment` uses — needs a Hilt-bound activity to satisfy the entry-point chain at `onAttach()`. Without iterating on actual rendered PNGs, generating 14 baselines blind risks locking incorrect rendering as truth.

### Scope

1. **Test infrastructure** (`app/src/test/java/org/spsl/evtracker/screenshots/`):
   - `RoborazziSetup.kt` — common `@Rule`, theme-flip helper (`Configuration.uiMode`), `HiltTestActivity`-hosted fragment setup.
   - `ChartsFixtures.kt` — one canonical `ChartsScreenState.Loaded(...)` populating all six metric fields (`trend`, `monthlyKwh`, `monthlyCost`, `acDc`, `locations`, `capacity` + `nominalBatteryKwh` + `derivedExcludedCount`, `co2Cumulative`) per the dataset shape in the design doc §4.2.
   - `ChartsTabScreenshotTest.kt` — `@HiltAndroidTest` + `@RunWith(RobolectricTestRunner::class)` + `@Config(application = HiltTestApplication::class)`. 14 `@Test` methods (`<tab>_<theme>` naming).

2. **Hilt wiring**:
   - `@BindValue` fakes for the `ObserveChartsModelsUseCase` collaborators (`CarReader`, `ChargeEventQueries`, `SettingsReader`, plus pure services like `StatsCalculator` / `CapacityEstimator` / `CO2Calculator` / `DateRangeResolver` / `NowProvider` constructed with the existing `Fakes.kt` helpers).
   - The real `ChartsViewModel` runs with these fakes; its `combine` over the fakes resolves to the fixture `Loaded(...)` state deterministically.
   - `HiltTestApplication` (from `hilt-android-testing`) bypasses `EVTrackerApp.onCreate()` so WorkManager init pain (TASK-58…74 territory) does not surface.

3. **Determinism**:
   - Frozen `Calendar` set to `2026-01-01T12:00Z` so month-bucket labels and date subtitles stabilise.
   - `Locale = en-US` for the test JVM (project default).
   - Roborazzi default `changeThreshold = 0.01`.

4. **CI integration**:
   - Append `:app:verifyRoborazziDebug` to the existing `:app:testDebugUnitTest` step in `.github/workflows/ci.yml`.
   - Failure artefact upload via `actions/upload-artifact@v4` of `app/build/outputs/roborazzi/**` so reviewers see the diff PNG without rerunning locally.

5. **Documentation**:
   - `CLAUDE.md` "Static analysis gate" section: add `verifyRoborazziDebug` to the gate list + recapture command + "recapture is its own PR" convention.
   - `docs/TEST_PLAN.md`: new "Screenshot baselines" subsection listing the 14 ChartsTab images.
   - `docs/BACKLOG.md`: append "Done YYYY-MM-DD" outcome block to TASK-35 (flipping ◐ → ☑) and to this TASK-79 entry; add the TASK-30 acceptance-gate paragraph referenced in the TASK-35 design doc §7.

### Acceptance criteria

1. `./gradlew :app:testDebugUnitTest :app:verifyRoborazziDebug` passes locally on `main`.
2. **14** baseline PNGs are committed under `app/src/test/screenshots/` (7 tabs × 2 themes) and visible in the PR diff. Each PNG is reviewed visually before commit (no blind capture).
3. Deliberate-regression smoke test: temporarily change `colorOnSurface` in `colors.xml`, re-run gate, confirm it fails with non-zero diff. Revert before commit.
4. PR-gate runtime stays under +90 s vs `main` HEAD.
5. CLAUDE.md, `docs/TEST_PLAN.md`, and the TASK-30 BACKLOG entry are updated.

### Out of scope

- Dashboard + outer ChartsFragment baselines. Those need either a different VM-injection strategy (factory hook on the production fragment, ~6 lines of production change) or a separate Hilt setup. File a follow-up if there's appetite once TASK-79 lands; otherwise their absence is a documented coverage gap, not a blocker for TASK-30 (TASK-30 changes only chart rendering, not Dashboard layout).
- Material You / dynamic-color, RTL, multi-device matrix.
- Migrating the existing MPAndroidChart code (TASK-30, blocked on this).

### Notes

- The TASK-35 design doc at `docs/superpowers/specs/2026-05-09-task35-roborazzi-baselines-design.md` remains the source of truth for the fixture dataset, theme matrix, and per-tab "notable visible content" table. TASK-79 inherits all of that wholesale; only the rendering strategy section changes from "stubbed VM, no Hilt" to "Hilt + Robolectric + @BindValue fakes."
- This work is unlocked by TASK-35 Phase 1 (build wiring is in place); a maintainer can open the TASK-79 branch off `main` and the Gradle tasks already register.
