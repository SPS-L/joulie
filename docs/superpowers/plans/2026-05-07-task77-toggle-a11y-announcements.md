# TASK-77 — Toggle-Group A11y Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `View.announceForAccessibility(...)` onto every `MaterialButtonToggleGroup` in the app so blind users hear "AC selected" / "DC selected" instead of just a click sound. One Kotlin extension helper, one localised string template (en + el + tr + ru), three call sites, three new TEST_PLAN.md §5c walkthrough rows.

**Architecture:** New extension on `MaterialButtonToggleGroup` in `ui/common/`, called from each of the three fragment `onViewCreated` blocks alongside the existing application-logic listeners. No changes to domain or data layers. Spec: `docs/superpowers/specs/2026-05-07-task77-toggle-a11y-announcements-design.md`.

**Tech Stack:** Kotlin, Material 3, no new Gradle deps, no new tests.

---

## Branch setup

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feat/task77-toggle-a11y-announcements
```

Verify: `git status` shows clean tree on `feat/task77-toggle-a11y-announcements`.

---

### Task 1: Helper extension

**Files:**
- Create: `app/src/main/java/org/spsl/evtracker/ui/common/ToggleGroupA11y.kt`

- [ ] **Step 1: Create the helper file**

```kotlin
/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.spsl.evtracker.ui.common

import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Fire a TalkBack announcement on each check transition of a
 * `MaterialButtonToggleGroup`, using `templateRes` (a format string with
 * one `%1$s` placeholder for the now-checked button's text).
 *
 * Solves the WCAG 4.1.3 "status messages" gap on toggle groups: by
 * default `MaterialButtonToggleGroup` only emits the system click sound
 * on selection change, leaving blind users without spoken feedback for
 * the new state. The helper does not interfere with application-logic
 * listeners — `addOnButtonCheckedListener` is additive.
 */
fun MaterialButtonToggleGroup.announceCheckedStateOnChange(
    @StringRes templateRes: Int,
) {
    addOnButtonCheckedListener { group, checkedId, isChecked ->
        if (!isChecked) return@addOnButtonCheckedListener
        val button = group.findViewById<MaterialButton>(checkedId) ?: return@addOnButtonCheckedListener
        group.announceForAccessibility(group.context.getString(templateRes, button.text))
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. The reference `R.string.a11y_toggle_selected` does not appear in the helper itself — the call sites pass the resource ID — so the helper compiles before the string resource exists.

---

### Task 2: Localised string resource (4 locales)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-el/strings.xml`
- Modify: `app/src/main/res/values-tr/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Add the English template**

Append (or insert near other a11y/wizard strings) to `app/src/main/res/values/strings.xml`:

```xml
<string name="a11y_toggle_selected">%1$s selected</string>
```

- [ ] **Step 2: Add the Greek template**

Append to `app/src/main/res/values-el/strings.xml`:

```xml
<string name="a11y_toggle_selected">Επιλέχθηκε %1$s</string>
```

- [ ] **Step 3: Add the Turkish template**

Append to `app/src/main/res/values-tr/strings.xml`:

```xml
<string name="a11y_toggle_selected">%1$s seçildi</string>
```

- [ ] **Step 4: Add the Russian template**

Append to `app/src/main/res/values-ru/strings.xml`:

```xml
<string name="a11y_toggle_selected">Выбрано: %1$s</string>
```

- [ ] **Step 5: Verify lint passes (`MissingTranslation` is in error mode)**

```bash
./gradlew :app:lint
```

Expected: BUILD SUCCESSFUL. Any locale missing the entry would fail with `MissingTranslation: ...a11y_toggle_selected...`.

---

### Task 3: Wire ChargeEdit AC/DC toggle

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditFragment.kt`

- [ ] **Step 1: Add the import + call**

Find the `onViewCreated` (or wherever `binding.chargeEditTypeGroup` is first touched). Add the import:

```kotlin
import org.spsl.evtracker.ui.common.announceCheckedStateOnChange
```

Add this single line near the top of `onViewCreated`, ideally right after the binding is set up and before the application-logic listeners:

```kotlin
binding.chargeEditTypeGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
```

- [ ] **Step 2: Wire the cost-mode toggle the same way**

Append the second call right next to the first:

```kotlin
binding.chargeEditCostModeGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

---

### Task 4: Wire wizard page 2 km/miles toggle

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt`

- [ ] **Step 1: Add the import + call**

Add the import:

```kotlin
import org.spsl.evtracker.ui.common.announceCheckedStateOnChange
```

In `onViewCreated`, before the existing `binding.wizardPage2UnitGroup.addOnButtonCheckedListener { ... }` block (line 55 today), insert:

```kotlin
binding.wizardPage2UnitGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Acceptance check — exactly 3 call sites**

```bash
grep -rn "announceCheckedStateOnChange" app/src/main/java
```

Expected: exactly 3 hits across 2 files (`ChargeEditFragment.kt` ×2, `WizardPage2Fragment.kt` ×1).

---

### Task 5: TEST_PLAN.md §5c walkthrough rows

**Files:**
- Modify: `docs/TEST_PLAN.md`

- [ ] **Step 1: Append three rows to the §5c table**

Find the §5c table (line 619 today, three rows ending at "Settings → walk through ..."). Append three new rows directly under row #3:

```markdown
| 4 | On ChargeEdit, double-tap the **DC** chip in the AC/DC toggle. | TalkBack announces `"DC selected"` (or the localised template result). |
| 5 | On ChargeEdit, expand the cost section and double-tap **Per kWh**. | TalkBack announces `"Per kWh selected"`. |
| 6 | On wizard page 2 (re-enter via Settings → Reset preferences), double-tap **miles**. | TalkBack announces `"miles selected"`. |
```

- [ ] **Step 2: Verify TEST_PLAN parses (no other changes)**

```bash
grep -c "^|" docs/TEST_PLAN.md
```

Sanity-check that the table grew by exactly 3 rows.

---

### Task 6: BACKLOG.md flip

**Files:**
- Modify: `docs/BACKLOG.md`

- [ ] **Step 1: Replace the TASK-77 row**

Find:

```
| TASK-77 | 🟢 | `MaterialButtonToggleGroup` state-change announcements. Custom `AccessibilityDelegate` on each `MaterialButton` so toggle changes announce as "AC selected" / "DC selected" rather than a click sound. Affects ChargeEdit charge-type toggle. | TASK-18 | ☐ |
```

Replace with:

```
| TASK-77 | 🟢 | `MaterialButtonToggleGroup` state-change announcements. **Done 2026-05-07** in `feat/task77-toggle-a11y-announcements` (v1.9.32). All three toggle groups in the app (ChargeEdit AC/DC, ChargeEdit cost-mode, Wizard km/miles) now fire `announceForAccessibility("<label> selected")` on each check transition via the `announceCheckedStateOnChange` extension in `ui/common/ToggleGroupA11y.kt`. Localised template `a11y_toggle_selected` shipped in en/el/tr/ru. TEST_PLAN.md §5c grew by three release-gate walkthrough rows. Native review of el/tr/ru phrasing remains forward-work. | TASK-18 | ☑ |
```

- [ ] **Step 2: Verify the backlog still parses**

```bash
grep "TASK-77" docs/BACKLOG.md | head -1
```

Should show the new ☑ row.

---

### Task 7: Manual TalkBack smoke (acceptance criterion #5)

This step is a best-effort verification on the AVD. If TalkBack is not available on the AVD image, the agent records that and defers to the user-run release-smoke walkthrough at tag-push time.

- [ ] **Step 1: Build the debug APK**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Check whether TalkBack is reachable on the AVD**

```bash
scripts/run-instrumented.sh stop  # only if a stale emulator is running
adb shell pm list packages | grep "com.google.android.marvin.talkback" || echo "TalkBack NOT installed on AVD"
```

- [ ] **Step 3: If TalkBack is present, install + launch the app, enable TalkBack, exercise one toggle**

If `TalkBack NOT installed`, skip to Step 4 — the AVD image lacks the closed-source service. Document the deferred verification in the release commit.

If TalkBack is installed:
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`
- Launch the app, navigate to ChargeEdit, double-tap DC.
- Capture TalkBack's spoken text via `adb logcat -d -s TalkBack:V` and confirm "DC selected" appears.

- [ ] **Step 4: Document the result in the release commit**

If verified locally: include "TalkBack smoke verified locally on AVD" in the release-commit body.
If deferred: include "TalkBack smoke deferred to release-tag user verification per TEST_PLAN.md §5c rows 4-6".

---

### Task 8: Version bump + full CI gate

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Bump versionCode + versionName**

In `app/build.gradle.kts`:

```kotlin
versionCode = 48       // was 47
versionName = "1.9.32" // was "1.9.31"
```

- [ ] **Step 2: Run all three CI gates**

```bash
./gradlew ktlintCheck
./gradlew :app:lint
./gradlew :app:testDebugUnitTest
```

All three must pass. Lint must specifically pass `MissingTranslation` for `a11y_toggle_selected`.

- [ ] **Step 3: Commit feature changes**

```bash
git add app/src/main/java/org/spsl/evtracker/ui/common/ToggleGroupA11y.kt
git add app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditFragment.kt
git add app/src/main/java/org/spsl/evtracker/ui/wizard/WizardPage2Fragment.kt
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-el/strings.xml
git add app/src/main/res/values-tr/strings.xml
git add app/src/main/res/values-ru/strings.xml
git add docs/TEST_PLAN.md
git add docs/BACKLOG.md
```

```bash
git commit -m "$(cat <<'EOF'
feat(task-77): TalkBack announcements on MaterialButtonToggleGroup state changes

`announceCheckedStateOnChange` extension wires each toggle group to
`announceForAccessibility("<label> selected")` on each check transition.
Applied to all three toggle groups in the app:
- ChargeEdit AC/DC charge type
- ChargeEdit Total/Per-kWh cost mode
- Wizard page 2 km/miles distance unit

Localised template `a11y_toggle_selected` ships in en/el/tr/ru
(MissingTranslation gate). TEST_PLAN.md §5c grew three release-gate
walkthrough rows for manual TalkBack smoke verification.
EOF
)"
```

- [ ] **Step 4: Commit version bump**

```bash
git add app/build.gradle.kts
```

```bash
git commit -m "chore(release): v1.9.32"
```

---

### Task 9: Merge, push, tag, cleanup

All git commands run separately per CLAUDE.md (no compound `&&`).

- [ ] **Step 1: Switch to main and merge non-fast-forward**

```bash
git checkout main
```

```bash
git merge --no-ff feat/task77-toggle-a11y-announcements -m "Merge branch 'feat/task77-toggle-a11y-announcements'"
```

- [ ] **Step 2: Stage + commit the spec + plan on main (TASK-76 precedent)**

```bash
git add docs/superpowers/specs/2026-05-07-task77-toggle-a11y-announcements-design.md
git add docs/superpowers/plans/2026-05-07-task77-toggle-a11y-announcements.md
```

```bash
git commit -m "docs(task-77): file spec + plan for toggle a11y announcements"
```

- [ ] **Step 3: Push main**

```bash
git push origin main
```

- [ ] **Step 4: Tag the release**

```bash
git tag v1.9.32
```

- [ ] **Step 5: Push the tag**

```bash
git push origin v1.9.32
```

This triggers `.github/workflows/release.yml`.

- [ ] **Step 6: Delete the feature branch locally**

```bash
git branch -d feat/task77-toggle-a11y-announcements
```

- [ ] **Step 7: Verify clean state**

```bash
git status
```

```bash
git log --oneline -6
```

```bash
git rev-parse HEAD origin/main
```

Expected: clean tree (sandbox-bound `??` entries OK), HEAD on main, HEAD == origin/main, last 6 commits include merge + chore + spec/plan + feature.

- [ ] **Step 8: Confirm the release workflow**

```bash
gh run list --workflow=release.yml --limit 1
```

Expected: a queued / in-progress / completed run for tag `v1.9.32`.

---

## Done

Acceptance criteria from the spec are met:

1. CI gate green (Task 8 Step 2).
2. Three call sites (Task 4 Step 3 grep).
3. TEST_PLAN.md §5c grew by three rows (Task 5).
4. BACKLOG flipped (Task 6).
5. TalkBack smoke either verified locally or explicitly deferred (Task 7).
