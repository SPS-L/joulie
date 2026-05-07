# TASK-18 PR 1, a11y lint lock-in — design

**Status:** approved 2026-05-07  
**Backlog item:** TASK-18 (🟡 Accessibility a11y pass)  
**Scope of this spec:** PR 1 only — the lock-in mechanism. PR 2 (cleanup of baselined fragments) is filed separately once PR 1 lands and the baseline content is known.

## Background

The original TASK-18 entry (BACKLOG.md line 1210) framed the work as eight steps; Step 6 (`AccessibilityChecks.enable()` in `HiltTestRunner.onStart()`) landed in commit `8417420`. The framing assumed that wiring Step 6 would surface a list of failing tests as the input to Steps 1–5, 7, 8.

That assumption no longer holds. The most recent nightly run (`25475926490`, 2026-05-07) is green: the TASK-58 / TASK-59 / TASK-60..74 cohort silently cleared the a11y violations Espresso surfaces (`MaterialToolbar` nav-back `contentDescription`, `Wizard` `TabLayout` indicator dots). The remaining work is genuinely subjective — coverage of widgets Espresso does not exercise, contrast on tokens, manual TalkBack walkthroughs — and is not gated by a failing test.

The risk this spec addresses: today's `:app:lint` block promotes only `HardcodedText`, `MissingTranslation`, `TypographyDashes`, `UnusedResources` to error; a11y rules are at default-warning severity and silently rot on every PR. A fix-now / re-rot-later cycle with no permanent floor is a poor use of effort.

## Goal

Promote the four core Android Lint a11y rules from default-warning to error so future drift cannot land silently, and document the WCAG 2.1 AA target + manual smoke checklist in `DESIGN.md` so the project's a11y intent is visible.

Out of scope (deferred to follow-up tasks): contrast audit on the M3 tokens, `MaterialButtonToggleGroup` state-change announcements via custom `AccessibilityDelegate`, full TalkBack walkthrough notes from a real device, and the cleanup of whatever the baseline absorbs in PR 1.

## Files touched

Three files only. No new code, no new packages, no new tests, no new modules.

### `app/build.gradle.kts`

Extend the existing `lint { error += listOf(...) }` block. The current block is at the `lint { ... }` level inside `android { }`:

```kotlin
lint {
    abortOnError = true
    checkReleaseBuilds = true
    warningsAsErrors = false
    baseline = file("lint-baseline.xml")
    error +=
        listOf(
            "HardcodedText",
            "MissingTranslation",
            "TypographyDashes",
            "UnusedResources",
            "ContentDescription",
            "TouchTargetSizeCheck",
            "LabelFor",
            "KeyboardInaccessibleWidget",
        )
}
```

The 4 new entries:

| Rule | What it catches |
|------|-----------------|
| `ContentDescription` | `ImageView` / `ImageButton` / icon-only widgets without `android:contentDescription`. Decorative views must explicitly opt out via `android:contentDescription="@null"` or `android:importantForAccessibility="no"`. |
| `TouchTargetSizeCheck` | Clickable / focusable views smaller than 48 × 48dp. WCAG 2.5.5. |
| `LabelFor` | `EditText` / `TextInputEditText` whose label lives in a separate `TextView` that lacks `android:labelFor="@+id/..."`. TalkBack drops the label otherwise. |
| `KeyboardInaccessibleWidget` | View with an `OnClickListener` but `android:focusable="false"`, hidden from D-pad / keyboard / switch-access users. |

The 4 adjacent rules considered and rejected for this PR: `ClickableViewAccessibility` (custom views; we have very few), `RedundantDescription` (cosmetic), `ImageContrastCheck` (often noisy), and the RTL family (`RtlHardcoded` / `RtlSymmetry` / etc.) (separate concern). Each can be promoted later as its own one-line PR.

### `app/lint-baseline.xml`

Regenerated in-place via `./gradlew :app:updateLintBaseline` after the rule promotion. The current 1114-line baseline grows by however many a11y violations the 4 new rules surface today; existing `HardcodedText` / `UnusedResources` / `Overdraw` / etc. entries that *still violate* are preserved. Entries whose underlying issue has been fixed since the baseline was last regenerated are dropped — that is correct behaviour and matches CLAUDE.md's "append-only-by-omission" rule, but the resulting diff is therefore **mostly** additive rather than purely additive. The PR description must call out any non-additive lines so reviewers can confirm none of the dropped entries are surprises.

This procedure is consistent with CLAUDE.md's rule "Regenerate the baseline only when retiring a rule (`./gradlew :app:updateLintBaseline`); do not regenerate to 'clean up', the baseline is append-only-by-omission." We are *adding* new rules, not retiring; the regeneration is mandatory because the new rules introduce violations the baseline does not yet know about.

### `docs/DESIGN.md`

New top-level section `## 11. Accessibility (a11y)`, appended after `## 10. Localisation ( )` at the end of the file (DESIGN.md is 777 lines today; this becomes the final section). Three subsections:

1. **Target** — WCAG 2.1 AA. One-paragraph statement that the app is intended for public use and must clear the AA bar; AAA is aspirational, not gated.
2. **Lint floor** — names the 4 promoted rules, points at the baseline as known debt, points at the "append-only-by-omission" rule from CLAUDE.md so the cleanup PRs that retire baseline entries follow the same pattern as the rest of the project.
3. **Smoke checklist** — three TalkBack walkthroughs that must pass before each release: (a) Wizard pages 1 → 4 on a fresh install, (b) Dashboard → FAB → ChargeEdit → save (the add-event golden path), (c) Settings → Drive enable/disable, language picker, reset all data. Includes pointer to `scripts/run-instrumented.sh` as the loop for catching regressions during development.

Word budget for the whole section: ~250 words. No mockups, no tables beyond the rule list.

## Verification

- **Local gate**: `./gradlew :app:lint` must succeed after the rule promotion + baseline regeneration. If it does not, the baseline regeneration was incomplete or a non-baselined rule still fires.
- **Sanity check**: on a throwaway branch, deliberately remove a `contentDescription` from any `ImageView` that currently has one (e.g. `fragment_dashboard.xml:399` — the FAB). Confirm `./gradlew :app:lint` exits non-zero with `ContentDescription` firing on the offending element. Revert.
- **CI**: the existing `.github/workflows/ci.yml` static-analysis job already runs `:app:lint`. No workflow change.
- **Acceptance for PR 1**: build green; `git diff app/lint-baseline.xml` is purely additive (existing entries preserved); DESIGN.md renders cleanly on `sps-l.github.io/joulie/` after merge.

## Failure modes

- **Baseline regeneration drops fixed entries.** Already covered in the `app/lint-baseline.xml` section above. Mentioned here so the failure-mode list is complete: this is benign expected behaviour, not a failure mode of the design.
- **A new a11y rule version (Lint version bump) reclassifies issues.** Out of scope for this PR; if it happens we treat it as a new TASK at the time.
- **Translation drift.** `MissingTranslation` is already in error mode; this PR does not change that. New `contentDescription` strings added during PR 2 cleanup will need translations into el / tr / ru per the existing CI gate.

## Release shape

Standard repo workflow per CLAUDE.md:

1. `git checkout -b feat/task18-a11y-lint-lockin`
2. Edit the three files; run `./gradlew :app:updateLintBaseline` once.
3. Commit `feat(task-18): promote a11y lint rules to error + regenerate baseline + DESIGN.md`.
4. Bump `versionCode` 45 → 46 and `versionName` 1.9.29 → 1.9.30 in `app/build.gradle.kts`. Patch bump (z): build-config + docs only, no user-visible behaviour change.
5. Commit `chore(release): v1.9.30`.
6. `git checkout main` → `git merge --no-ff feat/task18-a11y-lint-lockin` → push → `git tag v1.9.30` → push tag → `git branch -d feat/task18-a11y-lint-lockin`. Each git command separate.
7. The release workflow takes care of the rest end-to-end.

## Forward work to file

After PR 1 lands, file these as separate backlog rows so they show up alongside other open tasks:

- **TASK-18 PR 2 — a11y baseline cleanup on priority fragments.** Walk the baselined `ContentDescription` / `TouchTargetSizeCheck` / `LabelFor` / `KeyboardInaccessibleWidget` violations on the seven priority fragments (Wizard, ChargeEdit, Dashboard, Charts, History, Cars, Settings), fix them, drop their entries from `lint-baseline.xml`. About / ManageLocations follow in their own PR if non-trivial. Concrete violation count is known after PR 1.
- **TASK-18 follow-up — contrast audit on M3 tokens.** Walk every text / surface pair in the brand palette + every state of every Material component on light + dark themes. Flagged tokens from the original BACKLOG TASK-18 body: `#FB8C00` DC orange tertiary + white-on-tertiary text, target ≥ 4.5:1.
- **TASK-18 follow-up — `MaterialButtonToggleGroup` state-change announcements.** Custom `AccessibilityDelegate` on each `MaterialButton` so toggle changes announce as "AC selected" / "DC selected" rather than a click sound.
- **TASK-18 follow-up — TalkBack walkthrough notes.** A second pair of eyes runs through the Smoke checklist on a real Pixel device and files anything the lint / Espresso paths miss.
