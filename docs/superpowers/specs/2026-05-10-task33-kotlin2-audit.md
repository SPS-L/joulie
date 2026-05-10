# TASK-33 — Kotlin 2.x audit

**Date:** 2026-05-10
**Backlog entry:** `docs/BACKLOG.md` TASK-33 (audit-only scope of this PR)
**Verdict:** **GO** — Kotlin 2.1.21 is the right target. Bundle-style coordinated dep bump (single PR) is mandatory; isolated `kotlin = 2.1.x` bump fails at compile time on Hilt's `kotlinx-metadata-jvm` cap.
**Unblocks:** TASK-30 (MPAndroidChart → Vico migration; Vico 2.x is Kotlin 2.1+).
**Follow-up filed:** **TASK-33b** — execute the coordinated upgrade specced below.

## 1. Why this audit exists

TASK-30 (chart-library migration) hit a hard block during build wiring on 2026-05-10: Vico 2.x is published with Kotlin 2.1 metadata, our project pins `kotlin = "1.9.21"`, and the compiler rejects the binary mismatch. See `2026-05-10-task30-vico-investigation-outcome.md` for the original blocker write-up.

Going to Kotlin 2.x lifts that block and unlocks the broader 2024–2026 ecosystem (newer Lifecycle, Navigation, coroutines, Robolectric, Roborazzi). This document is the audit deliverable: a recommendation on whether to upgrade, what to target, and what else has to move with the kotlin pin.

## 2. Target stack

```
kotlin         1.9.21        -> 2.1.21
ksp            1.9.21-1.0.16 -> 2.1.21-1.0.x   (exact-Kotlin-match, latest matching micro)
hilt           2.50          -> 2.56.2
coroutines     1.7.3         -> 1.10.2
robolectric    4.13          -> 4.14.1
roborazzi      1.36.0        -> 1.59.0
lifecycle      2.7.0         -> 2.8.7
navigation     2.7.6         -> 2.8.5
mockito-kotlin 5.2.1         -> 5.4.0
```

`agp = "8.7.3"` (already supports Kotlin 2.1), `room = "2.6.1"`, `ktlint = "12.1.1"`, `gson`, `core-ktx`, `material`, `workmanager` stay as-is.

**Why Kotlin 2.1.21 (not 2.0.x or 2.2.x or 2.3.x):**

| Candidate | Reason for / against |
|---|---|
| 2.0.21 | Safe, conservative. Works. But locks us out of features and bug-fixes shipped in 2.1, and we'd want to bump again within 12 months. |
| **2.1.21** ✓ | Sweet spot. Every blocked dep has a mature release at this Kotlin (Hilt 2.56, Roborazzi 1.59, Robolectric 4.14, coroutines 1.10.2). AGP 8.7.3 satisfies its 8.6 minimum. |
| 2.2.x | Hilt 2.56+ unshaded `kotlinx-metadata-jvm` only landed late; AGP 8.10 required. Ecosystem still catching up. |
| 2.3.x | **Avoid.** Fresh round of Hilt incompatibilities (dagger#5001, #5059); same metadata-cap pattern that bit us at 2.50→2.1. |

## 3. Per-dep status

| Dep | Current | Target | Status | Notes |
|---|---|---|---|---|
| kotlin | 1.9.21 | **2.1.21** | bump | K2 frontend has been default since 2.0; project has no kapt usage so K2 surface is small. |
| ksp | 1.9.21-1.0.16 | **2.1.21-1.0.x** | bump (exact-match) | KSP1 (the version Hilt + Room use) requires `kotlin == ksp.kotlinPart`. Catalog typo here = `KSP plugin loaded does not match Kotlin compiler version` preflight failure. |
| agp | 8.7.3 | keep (or 8.8/8.9) | OK | Kotlin 2.1's AGP minimum is 8.6. AGP 8.10 only required for Kotlin 2.2+. |
| hilt | 2.50 | **2.56.2** | **blocker** | 2.50 caps `kotlinx-metadata-jvm` at 2.0.0. K2.1 metadata = 2.1.0 → fails: `Provided Metadata instance has version 2.1.0, while maximum supported version is 2.0.0` (dagger#4582). 2.51 added Kotlin 2.0 support; 2.56 is the explicit Kotlin 2.1.10 / KSP 2.1.10-1.0.31 release. KSP-not-kapt is already correct in our build. |
| room | 2.6.1 | keep (or 2.7.x) | OK | Works with Kotlin 2.x via KSP. 2.7.x adds KMP support — not needed for this app. |
| coroutines | 1.7.3 | **1.10.2** | bump | 1.7.3 predates K2 entirely. Companion versions: 1.9.0 ↔ K 2.0, 1.10.0 ↔ K 2.1, 1.11.0 ↔ K 2.2.20. |
| mockito | 5.10.0 | 5.14.x+ | OK (hygiene bump) | Bytecode-level; no K2 incompat. |
| mockito-kotlin | 5.2.1 | **5.4.0** | bump | 5.4.0 (Jul 2024) added value-class support, bumped Mockito to 5.12. |
| robolectric | 4.13 | **4.14.1** (or 4.15) | bump | 4.13 predates K2 internal Kotlin. 4.14 internally bumped to Kotlin 2.0.21; 4.15 to 2.2.0. |
| roborazzi | 1.36.0 | **1.59.0** | bump | 1.36.0 was compiled against Kotlin 1.9.x. Under a 2.x consumer the compiler emits metadata-mismatch warnings and (on stricter inline-function paths) `IrLinkageError`. 1.59+ aligns with Kotlin 2.0.21+. |
| gson | 2.10.1 | 2.11.0 | OK | Pure-Java. |
| lifecycle | 2.7.0 | **2.8.7** | bump | 2.8 line targets Kotlin 2.0+. ViewModel → AutoCloseable signature change is source-compatible. |
| navigation | 2.7.6 | **2.8.5** | bump | 2.8+ requires KGP 2.0+. |
| ktlint plugin | 12.1.1 | keep (or 12.1.2) | OK | Plugin tests against K2; no strict need to bump. |
| core-ktx | 1.12.0 | 1.13.1 | OK | Compiler-insensitive. |
| material | 1.11.0 | 1.12.0 | OK | Compiler-insensitive. |
| workmanager | 2.9.0 | 2.9.1 | OK | Compiler-insensitive. |

## 4. Highest-risk blockers (in execution order)

1. **Hilt 2.50 + Kotlin 2.1 → hard fail** at compile. Bump `hilt` to 2.56.2 in the same commit as the Kotlin bump. KSP-vs-kapt processor migration is N/A — already on KSP per `CLAUDE.md` "Build & Test".
2. **KSP exact-version pin** — KSP1's preflight check fails with `KSP plugin loaded does not match Kotlin compiler version` if the catalog drifts (e.g. `kotlin = "2.1.21"` paired with `ksp = "2.1.20-1.0.x"`). Verify the Kotlin micro version matches the KSP prefix exactly.
3. **Roborazzi 1.36.0** — 14 ChartsTab baselines are tracked under `app/src/test/screenshots/`. Roborazzi internal Kotlin shifts may produce sub-pixel rendering differences. Mitigation: keep the existing `changeThreshold = 0.01`. If verify fails on the bump, recapture in a separate `screenshot baseline refresh` PR per the standard convention; if the diffs are <2% on every baseline, prefer threshold bump to 0.02 over recapture.
4. **TASK-39 auto-migration test sensitivity** — KSP2 occasionally regenerates Room schemas with subtle column-ordering differences. The `MigrationTest` suite (with the bundled `app/schemas/AppDatabase/<v>.json` assets) is the gate for that. Run `:app:connectedDebugAndroidTest` end-to-end once locally; failures here are recoverable by re-exporting schemas, but they need to surface before merge.
5. **Coroutines 1.7.3 → 1.10.2** — large jump (three minor versions). The `kotlinx-coroutines-test` API used by JVM tests changed slightly between 1.7 and 1.8 (StandardTestDispatcher / advanceTimeBy); the project already uses these (per CLAUDE.md "ViewModel + event pattern"), so no source change should be needed, but tests are the canary.

## 5. Quality gates that must remain green

- `:app:assembleDebug` + `:app:assembleRelease` — clean build, no metadata mismatches.
- `:app:ktlintCheck` — Kotlin official style, may need ktlint plugin bump if rules drift.
- `:app:lintDebug` — no new violations against `app/lint-baseline.xml`.
- `:app:testDebugUnitTest` — all existing JVM tests pass, including the 5 new `PieChartViewSliceMathTest` cases (TASK-30 prep).
- `:app:verifyRoborazziDebug` — 14 baselines unchanged, OR baseline-refresh PR landed alongside per §4 risk #3.
- `:app:connectedAndroidTest` (nightly suite) — `MigrationTest`, instrumented Espresso suite, all green. Per §4 risk #4 this is the primary canary for KSP2 schema regeneration.

## 6. What this audit does NOT cover

- The execution itself — that's TASK-33b. The recommended catalog-edit / branch / commit / merge / version-bump plan lives in the TASK-33b spec.
- Bumping AGP / Java target above 17 — out of scope; today's 8.7.3 / JDK 17 satisfies the K 2.1 minimum.
- Kotlin 2.2.x or 2.3.x adoption — explicitly punted to a future audit when the ecosystem matures (§2 candidate-version table above).
- Compose adoption — TASK-08 closed (Compose not justified for one dialog). Re-evaluating Compose post-K2 is a separate decision that doesn't block TASK-33b.

## 7. Verdict

**GO**. The 1.9.21 → 2.1.21 jump is mature: every blocking dep has a stable companion release, and the most disruptive single failure mode (Hilt 2.50's metadata cap) has a clean fix in Hilt 2.56.2. The biggest remaining unknown is whether the Roborazzi 1.36.0 → 1.59.0 internal-Kotlin shift produces baseline-level pixel differences; mitigation strategy is documented above.

The execution PR (TASK-33b) should:

1. Make the catalog edit per §2 in one commit.
2. Verify locally end-to-end (§5 gates) before pushing.
3. If Roborazzi diffs surface, either threshold-bump (small drift) or open a separate baseline-refresh PR per the standard convention (large drift).
4. Bump the app version **minor** (y) — significant build-config rewrite, even though no user-visible feature ships. v1.9.x → v1.10.0.
5. Once green on `main`, TASK-30 unblocks: open it as a Vico 2.x migration PR with the rendering plan in `2026-05-10-task30-vico-investigation-outcome.md`.

## Sources

- [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- [KSP releases](https://github.com/google/ksp/releases)
- [Dagger releases](https://github.com/google/dagger/releases) — see issues [#4582](https://github.com/google/dagger/issues/4582), [#5001](https://github.com/google/dagger/issues/5001), [#5059](https://github.com/google/dagger/issues/5059) for Hilt + Kotlin metadata-cap regressions.
- [AGP × Kotlin compatibility](https://developer.android.com/build/kotlin-support)
- [Robolectric releases](https://github.com/robolectric/robolectric/releases)
- [Roborazzi releases](https://github.com/takahirom/roborazzi/releases)
- [kotlinx.coroutines releases](https://github.com/Kotlin/kotlinx.coroutines/releases)
- [mockito-kotlin 5.4.0 release notes](https://github.com/mockito/mockito-kotlin/releases/tag/5.4.0)
- [androidx Lifecycle release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [androidx Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation)
