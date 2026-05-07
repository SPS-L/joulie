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

Four files. No new code, no new packages, no new tests, no new modules.

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

Regenerated in-place via `./gradlew :app:updateLintBaseline` after the rule promotion. The 1114-line baseline gains entries for whatever a11y violations the 4 new rules surface today. Existing entries for the legacy rules (`HardcodedText`, `UnusedResources`, `Overdraw`, etc.) that *still violate* are preserved verbatim; entries whose underlying issue has been fixed since the baseline was last regenerated are dropped because `:app:updateLintBaseline` only writes entries that still fire.

The expected diff shape:
- A block of **new** entries for the 4 promoted rules (`ContentDescription`, `TouchTargetSizeCheck`, `LabelFor`, `KeyboardInaccessibleWidget`).
- Possibly some **removed** legacy entries that no longer fire. These are not regressions — they are CLAUDE.md's "append-only-by-omission" rule operating as intended.

The PR description must enumerate every removed legacy entry by ID + line so reviewers can confirm none of the drops are surprises (e.g. an issue silenced by an unrelated refactor we missed).

This procedure is consistent with CLAUDE.md's rule "Regenerate the baseline only when retiring a rule (`./gradlew :app:updateLintBaseline`); do not regenerate to 'clean up', the baseline is append-only-by-omission." We are *adding* new rules, not retiring; the regeneration is mandatory because the new rules introduce violations the baseline does not yet know about.

### `docs/DESIGN.md`

New top-level section `## 11. Accessibility (a11y)`, appended after `## 10. Localisation ( )` at the end of the file (DESIGN.md is 777 lines today; this becomes the final section). The section documents the *contract*, not the procedure — release-gating manual steps live in TEST_PLAN.md (next file). Two subsections:

1. **Target** — WCAG 2.1 AA. One-paragraph statement that the app is intended for public use and must clear the AA bar; AAA is aspirational, not gated. Two-sentence note on what conformance does and does not cover (rendering, interaction, contrast in scope; cognitive accessibility, internationalised TalkBack vocabularies out of scope).
2. **Lint floor** — names the 4 promoted rules, points at `app/lint-baseline.xml` as the registry of known debt, and references CLAUDE.md's "append-only-by-omission" rule so future cleanup PRs that retire baseline entries follow the same pattern as the rest of the project. Closes with a single-line pointer at TEST_PLAN.md §5c (below) for the release smoke walkthrough.

Word budget for the whole section: ~150 words. No mockups, no tables beyond the rule list.

### `docs/TEST_PLAN.md`

New section `## 5c. Accessibility smoke walkthrough (run before every tagged release)`, inserted between the existing `## 5b. Release-APK smoke test` and `## 6.` so the release-smoke procedures stay single-sourced and adjacent. The 5b matrix already gates every tagged release on R8, Drive, charts, and dark-mode rendering; 5c adds three TalkBack walkthroughs gated on the same release event:

| # | Step | Pass criterion |
|---|------|----------------|
| 1 | Fresh install. Enable TalkBack (Settings → Accessibility → TalkBack). Cold-launch the app from the launcher. Walk through wizard pages 1 → 4 by swiping right with two fingers. | Every interactive control on every page is announced (page title, primary metric chips on page 1, distance-unit chips, currency dropdown on page 2, language dropdown if present, page-3 disclaimer text + Accept switch + Finish button on page 4). The Finish button announces its disabled state until the disclaimer switch is toggled. |
| 2 | Dashboard → FAB ("Log charge") → ChargeEdit → save the add-event golden path with TalkBack on. | FAB announces "Log charge". Inside ChargeEdit every text field announces its label (date, odometer km, kWh, cost, currency, charge type AC / DC, location, note). Save button announces success state. |
| 3 | Settings → walk through Drive toggle, language picker, About row, Reset preferences. | Each row reads its full label + state ("Drive backup, on" / "Drive backup, off"). The Reset confirmation dialog reads its body text and both buttons. |

Optional during development: re-running these against `scripts/run-instrumented.sh` is not equivalent (Espresso `AccessibilityChecks` does not exercise screen-reader announcements), but is useful for catching the *types* of violations that would otherwise surface during the manual walkthrough.

## Verification

The legacy 4 rules in the lint block (`HardcodedText`, `MissingTranslation`, `TypographyDashes`, `UnusedResources`) already make `:app:lint` exit zero on the current tree. A "build green after the change" check therefore does not discriminate the lock-in: a PR that forgets to add the 4 new IDs to `error += listOf(...)` would also be green. The discriminating check is a deliberate fault injection that proves each new rule is wired up. Promote it to acceptance.

### Acceptance for PR 1 (every item must hold)

1. **Baseline-absorbed build**: `./gradlew :app:lint` exits zero on the head of the feat branch after `:app:updateLintBaseline` has run once. Confirms current violations are absorbed.
2. **Rule wiring proof — `ContentDescription`**: revert any single existing `contentDescription` attribute on a layout (e.g. drop `android:contentDescription="@string/fab_log_charge"` from `fragment_dashboard.xml:399`). Run `./gradlew :app:lint`. The build must exit non-zero with `ContentDescription` firing on the offending element. Restore the attribute.
3. **Rule wiring proof — `LabelFor`**: drop the `android:labelFor` attribute from a label that currently has one (use `git grep "android:labelFor"` to find a candidate). Run `./gradlew :app:lint`. Build must exit non-zero with `LabelFor` firing. Restore.
4. **Rule wiring proof — `TouchTargetSizeCheck`**: shrink any clickable view's `minWidth` / `minHeight` (or the parent constraint) below 48dp on a debug-only branch. Run `./gradlew :app:lint`. Build must exit non-zero with `TouchTargetSizeCheck` firing. Restore.
5. **Rule wiring proof — `KeyboardInaccessibleWidget`**: add `android:focusable="false"` to any view that has an `OnClickListener` (most clickable Material components). Run `./gradlew :app:lint`. Build must exit non-zero with `KeyboardInaccessibleWidget` firing. Restore.
6. **Baseline diff shape**: `git diff app/lint-baseline.xml` shows the new-rule entries added; any *removed* legacy entries are enumerated by `id` + line in the PR description with a one-sentence note on why each one stopped firing. No surprise drops.
7. **CI gate clean**: the existing `.github/workflows/ci.yml` static-analysis job runs `:app:lint`; the workflow stays green on the merge commit. No workflow change.
8. **Documentation**: DESIGN.md §11 is appended; TEST_PLAN.md §5c is inserted. Markdown files lint clean against the existing repo conventions (4-space indent, no em-dash in shipped strings is a strings-locale rule, not a docs rule).

The 4 fault-injection steps (2 → 5) can be combined into a single throwaway commit on the feat branch that touches one element per rule; the commit is reverted before merge. Total verification time on a hot Gradle daemon: ~5 minutes for the four `:app:lint` invocations.

## Failure modes

- **Reviewer skips fault-injection steps.** Acceptance items 2–5 are the only checks that prove each rule is actually wired into `:app:lint`'s error set. If they are skipped, an off-by-one in `error += listOf(...)` (e.g. typo `ContentDesciption`) ships silently green and the lock-in is illusory until the first cleanup PR notices nothing is failing. Mitigation: items 2–5 are mandatory, not optional, and the verification commit is small enough to leave on the branch as evidence (squashed at merge).
- **Baseline regeneration drops fixed entries that were silenced for a reason.** Rare, but possible: a baselined `HardcodedText` entry on a string that has since been moved to `strings.xml` will drop. Item 6 of the acceptance list (PR description enumerates every drop) is the mitigation.
- **A new a11y rule version (Lint version bump) reclassifies issues.** Out of scope for this PR; if it happens we treat it as a new TASK at the time.
- **Translation drift.** `MissingTranslation` is already in error mode; this PR does not change that. New `contentDescription` strings added during PR 2 cleanup will need translations into el / tr / ru per the existing CI gate.

## Release shape

Standard repo workflow per CLAUDE.md:

1. `git checkout -b feat/task18-a11y-lint-lockin`.
2. Edit `app/build.gradle.kts` (the `lint { error += listOf(...) }` block — add the 4 new rule IDs).
3. Run `./gradlew :app:updateLintBaseline` once. Inspect the resulting `git diff app/lint-baseline.xml` to enumerate any *removed* legacy entries; capture them for the PR description per acceptance item 6.
4. Append `## 11. Accessibility (a11y)` to `docs/DESIGN.md`.
5. Insert `## 5c. Accessibility smoke walkthrough` into `docs/TEST_PLAN.md` between the existing §5b and §6.
6. Run the four fault-injection acceptance checks (items 2-5) on a throwaway commit; revert before squashing.
7. Commit `feat(task-18): promote a11y lint rules to error + regenerate baseline + DESIGN.md §11 + TEST_PLAN.md §5c`.
8. Bump `versionCode` 45 → 46 and `versionName` 1.9.29 → 1.9.30 in `app/build.gradle.kts`. Patch bump (z): build-config + docs only, no user-visible behaviour change.
9. Commit `chore(release): v1.9.30`.
10. `git checkout main` → `git merge --no-ff feat/task18-a11y-lint-lockin` → push → `git tag v1.9.30 <release-commit>` → push tag → `git branch -d feat/task18-a11y-lint-lockin`. Each git command separate.
11. The release workflow takes care of APK assembly and GitHub Release publication end-to-end.

## Forward work to file

After PR 1 lands, file these as separate backlog rows so they show up alongside other open tasks:

- **TASK-18 PR 2 — a11y baseline cleanup on priority fragments.** Walk the baselined `ContentDescription` / `TouchTargetSizeCheck` / `LabelFor` / `KeyboardInaccessibleWidget` violations on the seven priority fragments (Wizard, ChargeEdit, Dashboard, Charts, History, Cars, Settings), fix them, drop their entries from `lint-baseline.xml`. About / ManageLocations follow in their own PR if non-trivial. Concrete violation count is known after PR 1.
- **TASK-18 follow-up — contrast audit on M3 tokens.** Walk every text / surface pair in the brand palette + every state of every Material component on light + dark themes. Flagged tokens from the original BACKLOG TASK-18 body: `#FB8C00` DC orange tertiary + white-on-tertiary text, target ≥ 4.5:1.
- **TASK-18 follow-up — `MaterialButtonToggleGroup` state-change announcements.** Custom `AccessibilityDelegate` on each `MaterialButton` so toggle changes announce as "AC selected" / "DC selected" rather than a click sound.
- **TASK-18 follow-up — TalkBack walkthrough notes.** A second pair of eyes runs through the Smoke checklist on a real Pixel device and files anything the lint / Espresso paths miss.
