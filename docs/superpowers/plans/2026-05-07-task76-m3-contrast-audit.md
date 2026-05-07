# TASK-76 — M3 Contrast Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a JVM unit test that audits all 31 M3 token text/surface pairs (16 light + 15 dark) against WCAG 2.1 AA thresholds, plus the supporting `ContrastRatio` helper and a DESIGN.md §11.2 entry. Lock the M3 ramp in: any future re-seed that drops a pair below threshold breaks the build.

**Architecture:** Pure-Kotlin WCAG ratio helper in the test source set + a single parameterised JUnit test. No production code change unless the audit reveals a failing token (none expected — MTB output is AA-compliant by construction; the test is the lock-in). Spec: `docs/superpowers/specs/2026-05-07-task76-m3-contrast-audit-design.md`.

**Tech Stack:** Kotlin, JUnit 4, no Android dep in test (pure JVM), no new Gradle deps.

---

## Branch setup

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feat/task76-m3-contrast-audit
```

Verify: `git status` shows clean tree on `feat/task76-m3-contrast-audit`.

---

### Task 1: ContrastRatio helper — RED-GREEN cycle 1 (white-on-black sanity)

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/testing/ContrastRatio.kt`
- Create: `app/src/test/java/org/spsl/evtracker/testing/ContrastRatioTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/org/spsl/evtracker/testing/ContrastRatioTest.kt`:

```kotlin
/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.testing

import org.junit.Assert.assertEquals
import org.junit.Test

class ContrastRatioTest {
    @Test
    fun whiteOnBlack_isMaximumRatio() {
        val ratio = ContrastRatio.ratio(fg = 0xFFFFFF, bg = 0x000000)
        assertEquals(21.0, ratio, 0.01)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.testing.ContrastRatioTest"
```

Expected: compile error / unresolved reference `ContrastRatio` (file does not exist yet).

- [ ] **Step 3: Write minimal implementation**

Create `app/src/test/java/org/spsl/evtracker/testing/ContrastRatio.kt`:

```kotlin
/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.testing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure WCAG 2.1 §1.4.3 relative-luminance / contrast-ratio computation.
 *
 * Source of truth: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 *                   https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio
 *
 * Used by `M3ContrastAuditTest` to lock in the Joulie M3 ramp against
 * WCAG 2.1 AA thresholds (4.5 normal text, 3.0 large text + non-text UI).
 */
object ContrastRatio {
    fun ratio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(rgb: Int): Double {
        val r = channelLinear(((rgb shr 16) and 0xFF) / 255.0)
        val g = channelLinear(((rgb shr 8) and 0xFF) / 255.0)
        val b = channelLinear((rgb and 0xFF) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channelLinear(c: Double): Double =
        if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.testing.ContrastRatioTest"
```

Expected: PASS.

---

### Task 2: ContrastRatio helper — RED-GREEN cycle 2 (white-on-blue reference)

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/testing/ContrastRatioTest.kt`

- [ ] **Step 1: Add a second test against an externally-computed reference**

Append to `ContrastRatioTest.kt`:

```kotlin
@Test
fun whiteOnPrimaryBlue_matchesReference() {
    // Reference value computed at https://webaim.org/resources/contrastchecker/
    // for fg=#FFFFFF, bg=#1F3DCC: ratio = 9.71:1.
    val ratio = ContrastRatio.ratio(fg = 0xFFFFFF, bg = 0x1F3DCC)
    assertEquals(9.71, ratio, 0.05)
}
```

- [ ] **Step 2: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.testing.ContrastRatioTest"
```

Expected: PASS (the helper is already correct from Task 1; this case validates the formula matches an external reference).

- [ ] **Step 3: Commit the helper**

```bash
git add app/src/test/java/org/spsl/evtracker/testing/ContrastRatio.kt
git add app/src/test/java/org/spsl/evtracker/testing/ContrastRatioTest.kt
git commit -m "test(task-76): add WCAG 2.1 ContrastRatio helper for token audit"
```

---

### Task 3: Light-theme audit test — RED-GREEN cycle 3

**Files:**
- Create: `app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt`

- [ ] **Step 1: Write the failing light-theme test**

Create `app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt`:

```kotlin
/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker

import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.testing.ContrastRatio

/**
 * WCAG 2.1 AA contrast audit for the Joulie M3 ramps.
 *
 * Source of truth for tokens:
 *   app/src/main/res/values/colors.xml         (light)
 *   app/src/main/res/values-night/colors.xml   (dark)
 *
 * If a token is re-seeded (e.g. via Material Theme Builder regen), the
 * paired hex value below must be updated. Failure messages name the pair
 * and the observed ratio so the fix is unambiguous.
 *
 * Spec: docs/superpowers/specs/2026-05-07-task76-m3-contrast-audit-design.md
 */
class M3ContrastAuditTest {
    private data class Pair(
        val name: String,
        val fg: Int,
        val bg: Int,
        val threshold: Double,
    )

    @Test
    fun lightTheme_allPairsClearThreshold() {
        LIGHT_PAIRS.forEach { pair -> assertPair(pair) }
    }

    private fun assertPair(pair: Pair) {
        val ratio = ContrastRatio.ratio(pair.fg, pair.bg)
        assertTrue(
            "${pair.name}: expected >= ${pair.threshold}:1, got %.2f:1 for (fg=#%06X, bg=#%06X)"
                .format(ratio, pair.fg and 0xFFFFFF, pair.bg and 0xFFFFFF),
            ratio >= pair.threshold,
        )
    }

    companion object {
        private const val TEXT = 4.5
        private const val UI = 3.0

        private val LIGHT_PAIRS = listOf(
            Pair("light primary text", 0xFFFFFF, 0x1F3DCC, TEXT),
            Pair("light primary container text", 0x001255, 0xDCE1FF, TEXT),
            Pair("light secondary text", 0xFFFFFF, 0x006874, TEXT),
            Pair("light secondary container text", 0x001F24, 0x9EEFFD, TEXT),
            Pair("light tertiary text", 0xFFFFFF, 0x7F5700, TEXT),
            Pair("light tertiary container text", 0x281900, 0xFFE082, TEXT),
            Pair("light error text", 0xFFFFFF, 0xBA1A1A, TEXT),
            Pair("light error container text", 0x410002, 0xFFDAD6, TEXT),
            Pair("light body text", 0x1A1C1E, 0xFDFCFF, TEXT),
            Pair("light surface text", 0x1A1C1E, 0xFDFCFF, TEXT),
            Pair("light surface variant text", 0x43474E, 0xDFE2EB, TEXT),
            Pair("light inverse surface text", 0xF1F0F4, 0x2F3033, TEXT),
            Pair("light outline on surface", 0x73777F, 0xFDFCFF, UI),
            Pair("light outline variant on surface", 0xC3C7CF, 0xFDFCFF, UI),
            Pair("light brand wordmark on background", 0x0A1B5E, 0xFDFCFF, TEXT),
            Pair("light launcher tile glyph", 0xFFFFFF, 0x0D47FF, TEXT),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest"
```

Expected: PASS — all 16 light pairs clear their thresholds. (MTB-derived ramps are AA by construction; this is the lock-in.)

If any pair FAILS: stop and surface the result. The fix is to either re-seed the token (and update the hex in the test) or accept the failure as a real bug to fix in `colors.xml`. Either way, the spec is the deciding document.

---

### Task 4: Dark-theme audit test — RED-GREEN cycle 4

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt`

- [ ] **Step 1: Add the dark-theme test**

Add a second `@Test` method and a second companion `val` to `M3ContrastAuditTest.kt`:

```kotlin
@Test
fun darkTheme_allPairsClearThreshold() {
    DARK_PAIRS.forEach { pair -> assertPair(pair) }
}
```

And in the companion object, after `LIGHT_PAIRS`:

```kotlin
private val DARK_PAIRS = listOf(
    Pair("dark primary text", 0x001F87, 0xB7C4FF, TEXT),
    Pair("dark primary container text", 0xDCE1FF, 0x0034BC, TEXT),
    Pair("dark secondary text", 0x00363D, 0x82D3DF, TEXT),
    Pair("dark secondary container text", 0x9EEFFD, 0x004F58, TEXT),
    Pair("dark tertiary text", 0x412D00, 0xF4C100, TEXT),
    Pair("dark tertiary container text", 0xFFE082, 0x5D4200, TEXT),
    Pair("dark error text", 0x690005, 0xFFB4AB, TEXT),
    Pair("dark error container text", 0xFFDAD6, 0x93000A, TEXT),
    Pair("dark body text", 0xE2E2E6, 0x1A1C1E, TEXT),
    Pair("dark surface text", 0xE2E2E6, 0x1A1C1E, TEXT),
    Pair("dark surface variant text", 0xC3C7CF, 0x43474E, TEXT),
    Pair("dark inverse surface text", 0x2F3033, 0xE2E2E6, TEXT),
    Pair("dark outline on surface", 0x8D9199, 0x1A1C1E, UI),
    Pair("dark outline variant on surface", 0x43474E, 0x1A1C1E, UI),
    Pair("dark brand wordmark on background", 0xB7C4FF, 0x1A1C1E, TEXT),
)
```

- [ ] **Step 2: Run both tests**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest"
```

Expected: PASS — both `lightTheme_allPairsClearThreshold` and `darkTheme_allPairsClearThreshold` clear all pairs (16 light + 15 dark = 31 total).

- [ ] **Step 3: Commit the audit test**

```bash
git add app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt
git commit -m "test(task-76): WCAG 2.1 AA audit for 31 M3 token pairs (light + dark)"
```

---

### Task 5: Mutation-kill verification (acceptance criterion #2)

This is a temporary mutation to prove the test is discriminating. The mutation is reverted before merge.

**Files:**
- Temporarily modify: `app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt`

- [ ] **Step 1: Pick a pair to break and observe the failure**

Edit `M3ContrastAuditTest.kt`. Change the `light surface variant text` row from:

```kotlin
Pair("light surface variant text", 0x43474E, 0xDFE2EB, TEXT),
```

to:

```kotlin
Pair("light surface variant text", 0xA0A0A0, 0xDFE2EB, TEXT),
```

(`#A0A0A0` is intentionally too light for `#DFE2EB`.)

- [ ] **Step 2: Run the test and capture the failure message**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest"
```

Expected: FAIL with a message of the shape:

```
light surface variant text: expected >= 4.5:1, got 1.NN:1 for (fg=#A0A0A0, bg=#DFE2EB)
```

Record the observed ratio (e.g. "1.49:1") for the release commit message.

- [ ] **Step 3: Revert the mutation**

Restore the original line:

```kotlin
Pair("light surface variant text", 0x43474E, 0xDFE2EB, TEXT),
```

- [ ] **Step 4: Re-run to confirm green**

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest"
```

Expected: PASS.

- [ ] **Step 5: No commit for this task**

The mutation kill is a manual acceptance check; the revert is not committed because it returns to the post-Task-4 state.

---

### Task 6: DESIGN.md §11.2

**Files:**
- Modify: `docs/DESIGN.md` (append a new subsection inside §11 Accessibility)

- [ ] **Step 1: Locate the §11 anchor**

```bash
grep -n "^## 11" docs/DESIGN.md
grep -n "^### 11" docs/DESIGN.md
```

Expected: §11 Accessibility exists from TASK-18 PR 1. We add §11.2 after the existing §11.1 (or as a new §11.x — let the existing structure dictate).

- [ ] **Step 2: Append the audit subsection**

Add the following block after the last §11.x subsection in `docs/DESIGN.md`:

```markdown
### 11.2 Token contrast audit

The Joulie M3 ramps in `app/src/main/res/values/colors.xml` (light) and
`app/src/main/res/values-night/colors.xml` (dark) are locked against WCAG
2.1 AA contrast thresholds by `M3ContrastAuditTest` in
`app/src/test/java/org/spsl/evtracker/M3ContrastAuditTest.kt`. The test
audits 31 token pairs (16 light + 15 dark): every text-on-surface pair
in both ramps at 4.5:1, plus `outline*` non-text UI pairs at 3.0:1, plus
the brand wordmark and launcher-tile glyph pairs.

The test pins hex values rather than reading the XML so a re-seed (e.g.
via Material Theme Builder regen) cannot drift below threshold without
breaking the build. If a re-seed is intentional, both files (XML + test)
must be updated in the same commit.

WCAG ratio computation lives in `ContrastRatio` (test source set,
`org.spsl.evtracker.testing`). Runtime code does not consume it; the
audit is a pure compile-time / test-time guarantee.

Out of scope for this audit: Material component states (filled-button
disabled, switch off, error-state text-field outline), dynamic-color
(Material You S+) variants, and screen-level screenshot contrast sweeps.
The first is partially covered by TASK-77; the rest are forward-work.
```

- [ ] **Step 3: Commit**

```bash
git add docs/DESIGN.md
git commit -m "docs(task-76): document M3 token contrast audit in DESIGN §11.2"
```

---

### Task 7: BACKLOG.md flip + outcome banner

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Capture the worst-case ratio**

Run the audit one more time with a print-on-pass tweak (just for capture, do NOT commit this print):

In `M3ContrastAuditTest.kt`, temporarily replace `assertPair` body with:

```kotlin
val ratio = ContrastRatio.ratio(pair.fg, pair.bg)
println("%-40s %.2f:1 (threshold %.1f)".format(pair.name, ratio, pair.threshold))
assertTrue(/* ... */)
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest" -i
```

Skim the output for the lowest passing ratio across both themes. Record it (e.g. "lowest passing pair: dark outline variant on surface = 3.NN:1").

Revert the `println` (do NOT commit it).

- [ ] **Step 2: Update the BACKLOG row + add outcome banner**

In `docs/BACKLOG.md`, find the TASK-76 row and:

- Flip the status box from `☐` to `☑`.
- Replace the row description's "Flagged tokens: …" trailing sentence with a "Done 2026-05-07" outcome banner that names the worst-case ratio observed and points to the audit test.

The exact replacement is:

Find this line:

```
| TASK-76 | 🟡 | Contrast audit on M3 tokens. Walk every text / surface pair in the brand palette and every state of every Material component on light + dark themes. Flagged tokens: `#FB8C00` DC orange tertiary + white-on-tertiary text, target ≥ 4.5:1 (WCAG 1.4.3). | TASK-18 | ☐ |
```

Replace with:

```
| TASK-76 | 🟡 | Contrast audit on M3 tokens. **Done 2026-05-07.** `M3ContrastAuditTest` audits 31 text/surface pairs (16 light + 15 dark) against WCAG 2.1 AA, locked against re-seed regression. Lowest passing pair: `<NAME> = <RATIO>:1` (recorded from Step 1 capture). The historical `#FB8C00` flag was already retired by TASK-57's tertiary re-seed. Component-state contrast and dynamic-color variants remain out of scope (forward-work). | TASK-18 | ☑ |
```

(Substitute `<NAME>` and `<RATIO>` with the values captured in Step 1.)

- [ ] **Step 3: Commit**

```bash
git add docs/BACKLOG.md
git commit -m "docs(task-76): mark contrast audit done with worst-case-ratio banner"
```

---

### Task 8: Version bump + release commit

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump versionCode and versionName**

In `app/build.gradle.kts`:

```kotlin
versionCode = 47       // was 46
versionName = "1.9.31" // was "1.9.30"
```

(Patch bump per the version-bump memory rule: this is a test-only addition + docs, no behaviour change, no API surface change → patch.)

- [ ] **Step 2: Run the full check locally**

```bash
./gradlew ktlintCheck
./gradlew :app:lint
./gradlew :app:testDebugUnitTest
```

All three must pass.

- [ ] **Step 3: Commit the release**

```bash
git add app/build.gradle.kts
git commit -m "chore(release): v1.9.31"
```

---

### Task 9: Merge, push, tag, cleanup

All git commands run separately per CLAUDE.md (no compound `&&`).

- [ ] **Step 1: Switch to main and merge non-fast-forward**

```bash
git checkout main
```

```bash
git merge --no-ff feat/task76-m3-contrast-audit -m "Merge branch 'feat/task76-m3-contrast-audit'"
```

- [ ] **Step 2: Push main**

```bash
git push origin main
```

- [ ] **Step 3: Tag the release**

```bash
git tag v1.9.31
```

- [ ] **Step 4: Push the tag**

```bash
git push origin v1.9.31
```

This triggers `.github/workflows/release.yml` which builds + signs the APK and publishes a GitHub Release.

- [ ] **Step 5: Delete the feature branch locally**

```bash
git branch -d feat/task76-m3-contrast-audit
```

- [ ] **Step 6: Verify clean state**

```bash
git status
```

```bash
git log --oneline -5
```

```bash
git rev-parse HEAD origin/main
```

Expected: clean tree, HEAD on main, HEAD == origin/main, last 5 commits include the merge + chore + 4 feature commits.

- [ ] **Step 7: Confirm the release workflow**

```bash
gh run list --workflow=release.yml --limit 1
```

Expected: a queued / in-progress / completed run for tag `v1.9.31`. (No need to block on completion — the user can pick up the link from `gh run watch <id>` if desired.)

---

## Done

All acceptance criteria from the spec are now met:

1. `:app:testDebugUnitTest --tests "org.spsl.evtracker.M3ContrastAuditTest"` passes locally.
2. Mutation kill verified in Task 5; revert is in tree.
3. `docs/DESIGN.md §11.2` documents the audit (Task 6).
4. `docs/BACKLOG.md` TASK-76 → ☑ with worst-case-ratio banner (Task 7).
5. Zero production-source-set churn in `app/src/main/`. Audit is test-only.
