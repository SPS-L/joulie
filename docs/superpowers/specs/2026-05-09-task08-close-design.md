# TASK-08 closure — design

**Date:** 2026-05-09
**Outcome:** Close TASK-08 (☒) without porting `CarEditDialog` to Compose.
**Scope:** Documentation-only change to `docs/BACKLOG.md`. No source, build, or schema edits.

## Why close

TASK-08's own preamble framed Step 1 as a gate: "if the team prefers staying on Views, close this task." All four reasonable triggers for adopting Compose come up empty:

1. **No Compose-first screens are planned.** The active backlog (TASK-21, 30, 33, 35, 38, 40, 49, 75, 78) describes performance, charts, tooling, and accessibility work — none of it Compose-shaped.
2. **TASK-30 (chart migration) explicitly chose a custom `Canvas` `PieChartView`, not Compose Canvas.** The original "TASK-30 benefits from Compose" hypothesis listed in TASK-08 has been superseded by the TASK-30 spec itself.
3. **The widget effort (DESIGN.md §708) deliberately avoided Glance/Compose runtime** — confirming a project-wide stance against pulling Compose in for narrow surfaces.
4. **`CarEditDialog` is 56 lines** wrapping `MaterialAlertDialogBuilder` over a single `DialogEditCarBinding`. The dependency cost (Compose BOM + UI + Material3 + activity-compose, plus `buildFeatures.compose = true` and a `composeOptions` block) far outweighs the line-count saving.

Pulling Compose in for one dialog is the textbook YAGNI anti-pattern.

## Reactivation trigger

Re-open TASK-08 if **either** of the following lands:

- A genuinely Compose-first new screen (not a port) gets prioritised on the backlog.
- A decision is taken to phase out ViewBinding project-wide.

Until then, `CarEditDialog.kt` and `R.layout.dialog_edit_car` stay as-is.

## Diffs

### `docs/BACKLOG.md` — overview table row 18

```diff
-| TASK-08 | 🟢 | Replace `CarEditDialog` with a Compose `AlertDialog` (requires adding Compose) |  | ☐ |
+| TASK-08 |  | ~~Replace `CarEditDialog` with a Compose `AlertDialog`~~, **closed, scope vs value** |  | ☒ |
```

(Mirrors the existing closed-task convention used by TASK-03, TASK-05, TASK-13.)

### `docs/BACKLOG.md` — heading + body at line 328

Replace the existing heading + "Premise correction" block + Step 1/2/3 subsections with a single closure note:

```markdown
## ☒ TASK-08, ~~Replace `CarEditDialog` with a Compose `AlertDialog`~~

**Closed 2026-05-09, scope vs value.** Compose is not in the dependency graph today, no Compose-first screens are planned, TASK-30 chose a custom `Canvas` `PieChartView` (not Compose Canvas), and the widget work in DESIGN.md §708 deliberately avoided the Compose / Glance runtime. The existing 56-line `CarEditDialog` object over `MaterialAlertDialogBuilder` + ViewBinding is fit for purpose; pulling in `compose-bom` + `ui` + `material3` + `activity-compose` + `buildFeatures.compose = true` for one dialog fails the YAGNI test.

**Reactivate if:** a Compose-first new screen lands on the backlog, or ViewBinding is being phased out project-wide.
```

## Branch + commit + merge plan

Per CLAUDE.md (single-merge, `--no-ff`, no compound git commands):

1. `git switch -c chore/task08-close` (off `main`)
2. Apply the two edits above to `docs/BACKLOG.md`
3. Stage spec + BACKLOG: `git add docs/BACKLOG.md docs/superpowers/specs/2026-05-09-task08-close-design.md`
4. `git commit -m "chore(task-08): close — Compose adoption fails YAGNI"`
5. `git switch main`
6. `git merge --no-ff chore/task08-close`
7. `git push origin main`
8. `git branch -d chore/task08-close`

## Version bump

**None.** Per the docs-only exemption recorded 2026-05-09 in the version-bump feedback memory: merges that touch only `docs/` (no source, no schema, no Gradle/CI, no shipped strings) are exempt from the `versionCode` / `versionName` bump and the `v*` tag. This merge qualifies — only `docs/BACKLOG.md` and the new spec file change.

## Out of scope

- No source code changes.
- No deletion of `CarEditDialog.kt` or `R.layout.dialog_edit_car`.
- No new tests (no behaviour change to test).
- No documentation rewrites elsewhere (`docs/DESIGN.md`, `CLAUDE.md`) — TASK-08 wasn't referenced from either.

## Acceptance

- `docs/BACKLOG.md` overview table row 18 shows ☒.
- `## ☒ TASK-08, ~~…~~` heading and closure note replace the original three-step body.
- `docs/superpowers/specs/2026-05-09-task08-close-design.md` exists and is committed.
- Merge to `main` is `--no-ff`, branch deleted, no version bump, no tag.
- `git log --oneline -1` on `main` shows the merge commit.
