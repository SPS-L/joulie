# TASK-75 closure + open-task audit (design)

**Date:** 2026-05-10
**Outcome:** Close TASK-75 (☒) and document an empirical audit of every open / partial / under-consideration backlog task, confirming the rest are accurately recorded.
**Scope:** Documentation-only change to `docs/BACKLOG.md`. No source, build, or schema edits.

## 1. Why close TASK-75

TASK-75's stated scope was: *"Walk the baselined `ContentDescription` / `LabelFor` / `KeyboardInaccessibleWidget` violations on the seven priority fragments, fix them, drop their entries from `app/lint-baseline.xml`."*

Empirical check on `main` HEAD (2026-05-10):

1. **Baseline grep:** `grep -c 'id="ContentDescription"\\|id="LabelFor"\\|id="KeyboardInaccessibleWidget"' app/lint-baseline.xml` → **0 matches**.
2. **Lint run:** `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :app:lintDebug` → **BUILD SUCCESSFUL in 1m 1s**, exit 0. No unbaselined violations either.
3. **Rule mode:** `app/build.gradle.kts` already promotes all three rules to `error` mode (TASK-18 PR 1 outcome). New violations break the build, so debt cannot silently accrue.
4. **Touch-target violations** (the only remaining a11y class) are explicitly out-of-scope for TASK-75 per its own preamble — they are surfaced by the Espresso `AccessibilityChecks` interceptor and tracked separately (covered partly by TASK-78 real-device walkthroughs).

The premise — "there are baselined a11y violations to clean up" — is empirically false. Following the same pattern as TASK-08's closure (2026-05-09): mark ☒ with a short rationale, document the reactivation trigger, no source-code change.

**Reactivation trigger:** if `ContentDescription`, `LabelFor`, or `KeyboardInaccessibleWidget` entries ever land in `app/lint-baseline.xml` again (e.g., a future bulk regeneration absorbs new debt), reopen TASK-75 to clean them.

## 2. Audit of remaining backlog items

Each open / partial / under-consideration task was checked against the codebase to confirm its premise still holds. Method: targeted grep for the artefacts the task would produce if completed.

### Open ☐

| Task | Premise check | Result |
|---|---|---|
| TASK-21 | `find . -name "baseline-prof.txt" -o -name "BaselineProfileGenerator*"` | empty → premise holds |
| TASK-30 | `grep -i "vico\|patrykandpatrick" libs.versions.toml app/build.gradle.kts` | empty → premise holds, gated on TASK-79 |
| TASK-33 | `grep "kotlin = " libs.versions.toml` → `kotlin = "1.9.21"` | still 1.x → premise holds |
| TASK-38 | `grep -rn "comparisonCarId\|secondaryCarId\|compareCarIds\|overlayCar" app/src/main` | empty → premise holds |
| TASK-40 | `grep -rn "anonymis\|researchExport\|ResearchExport" app/src/main` | empty → premise holds |
| TASK-49 | `grep -n "gridCarbonIntensityGCo2PerKwh\|perEventCarbon" ChargeEventEntity.kt` | empty → premise holds |
| TASK-78 | needs physical Pixel + low-end device | cannot auto-verify, premise presumed |
| TASK-79 | filed today; no `app/src/test/java/.../screenshots/` directory yet | premise holds |

### Partial ◐

| Task | Status | Result |
|---|---|---|
| TASK-35 | Phase 1 (build wiring) merged 2026-05-10 in v1.9.34; Phase 2/3 (PNGs) deferred to TASK-79 | status correct |

### Under consideration ⏸

| Task | Premise check | Result |
|---|---|---|
| TASK-37 | `grep -rn "SafBackup\|StorageAccessFramework\|documentfile" app/src/main` | empty → premise holds |
| TASK-41 | `grep -rn "JsonLd\|@context\|OCPP\|chargingSession" app/src/main` | empty → premise holds |
| TASK-42 | `grep -rn "OpenChargeMap\|OCPI\|OcpiClient" app/src/main` | empty → premise holds |
| TASK-47 | `grep -n "peakPowerKw\|chargingDurationMinutes" ChargeEventEntity.kt` | empty → premise holds |
| TASK-48 | `grep -n "tariffPeriod\|TariffWindow\|offPeak\|peakHours" ChargeEventEntity.kt` | empty → premise holds |

### Closed in this audit

| Task | Reason |
|---|---|
| TASK-75 | Premise empirically false (see §1 above). |

## 3. Diffs

### `docs/BACKLOG.md` — overview table row 85 (TASK-75)

```diff
-| TASK-75 | 🟡 | Accessibility (a11y) cleanup of priority fragments. ... | TASK-18 | ☐ |
+| TASK-75 |  | ~~A11y cleanup of priority fragments~~, **closed 2026-05-10, premise no longer holds** — verified zero baselined `ContentDescription` / `LabelFor` / `KeyboardInaccessibleWidget` violations and `:app:lintDebug` BUILD SUCCESSFUL with all three rules in error mode, so no debt to clean and no debt can accrue without breaking the build. | TASK-18 | ☒ |
```

(Mirrors the conventions used by TASK-03 / TASK-05 / TASK-08 / TASK-13 closures.)

No body-section edit needed — TASK-75 never had a dedicated body section in the BACKLOG, only the overview row.

## 4. Branch + commit + merge plan

Per CLAUDE.md (single-merge, `--no-ff`, no compound git commands):

1. `git switch -c chore/task75-close-and-audit` (off `main`)
2. Apply the overview-row edit
3. Stage spec + BACKLOG: `git add docs/BACKLOG.md docs/superpowers/specs/2026-05-10-task75-close-and-audit-design.md`
4. `git commit -m "chore(task-75): close — premise empirically empty, plus open-task audit"`
5. `git switch main`
6. `git merge --no-ff chore/task75-close-and-audit`
7. `git push origin main`
8. `git branch -d chore/task75-close-and-audit`

## 5. Version bump

**None.** Per the docs-only exemption recorded 2026-05-09 in the version-bump feedback memory. This merge touches only `docs/BACKLOG.md` and the new spec file. No source, no schema, no Gradle, no CI, no shipped strings.

## 6. Acceptance

- `docs/BACKLOG.md` overview table row 85 shows ☒ for TASK-75 with the closure rationale visible.
- The audit table in §2 is recorded in this spec doc for traceability — future maintainers can re-run the same greps to confirm whether the listed tasks are still pending.
- `docs/superpowers/specs/2026-05-10-task75-close-and-audit-design.md` exists and is committed.
- Merge to `main` is `--no-ff`, branch deleted, no version bump, no tag.
