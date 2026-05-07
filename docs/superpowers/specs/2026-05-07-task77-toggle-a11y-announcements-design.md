# TASK-77 — Toggle-Group State-Change Announcements (Design Spec)

**Filed:** 2026-05-07
**Backlog row:** TASK-77, 🟢, depends on TASK-18 (done)
**Goal:** When a `MaterialButtonToggleGroup` button gets checked, fire a TalkBack announcement of the form `"AC selected"` so blind users hear the new state instead of just a generic click sound. Apply to all three toggle groups in the app.

## 1. Background

`MaterialButtonToggleGroup` manages check-state across `MaterialButton` children. The visual feedback (button "presses in") is sighted-only. Material's children do set `isCheckable = true` so a TalkBack-focused button reads as "AC, double-tap to activate", but **on the moment the user actually taps to switch selection, no spoken feedback fires** — only the system click sound. Blind users have to swipe back over the button to discover whether the tap actually changed selection.

The fix: on each check transition, call `View.announceForAccessibility(text)` with a localised template like `"%1$s selected"`. TalkBack speaks the announcement immediately.

## 2. Scope

**In scope (this PR).** Three `MaterialButtonToggleGroup` instances in `app/src/main/res/layout/`:

| Layout | Group ID | Buttons | Fragment binding code |
|--------|----------|---------|-----------------------|
| `fragment_charge_edit.xml` | `charge_edit_type_group` | AC / DC | `ChargeEditFragment.kt` |
| `fragment_charge_edit.xml` | `charge_edit_cost_mode_group` | Total / Per-kWh | `ChargeEditFragment.kt` |
| `fragment_wizard_page2.xml` | `wizard_page2_unit_group` | km / miles | `WizardPage2Fragment.kt` |

**Out of scope.**

- Other a11y improvements on these screens (TASK-75 is the catch-all).
- Real-device TalkBack walkthrough notes (TASK-78 is forward-work).
- Component-state contrast for these toggle buttons (TASK-76 covered M3 token contrast; component-state contrast is forward-work).

## 3. Architecture

| Unit | Responsibility | Path |
|------|----------------|------|
| `announceCheckedStateOnChange` | Kotlin extension on `MaterialButtonToggleGroup`. Calls `addOnButtonCheckedListener`; on `isChecked == true`, looks up the now-checked `MaterialButton` and calls `group.announceForAccessibility(context.getString(templateRes, button.text))`. | `app/src/main/java/org/spsl/evtracker/ui/common/ToggleGroupA11y.kt` |
| `R.string.a11y_toggle_selected` | Localised template `"%1$s selected"`. Translated into all four shipped locales (en / el / tr / ru). `MissingTranslation` is in error mode so any omission breaks the build. | `app/src/main/res/values{,-el,-tr,-ru}/strings.xml` |
| Three call sites | One line each in `ChargeEditFragment` (×2) and `WizardPage2Fragment` (×1). Wired in the `onViewCreated` block alongside the existing `addOnButtonCheckedListener` for application logic. | existing fragments |
| TEST_PLAN.md §5c | Append three rows to the TalkBack smoke walkthrough — one per toggle group — so any regression is caught at release time. | `docs/TEST_PLAN.md` |

The helper is intentionally tiny (single function, ~10 lines including the no-op guards) and lives in `ui/common/` next to existing presentation helpers (`MoneyFormat.kt`, `DateFormat.kt`, `PeriodLabels.kt`). It does not depend on `domain/` or `data/`.

## 4. Implementation

```kotlin
// app/src/main/java/org/spsl/evtracker/ui/common/ToggleGroupA11y.kt
package org.spsl.evtracker.ui.common

import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

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

The extension fires the announcement only on the **check** transition (the listener also fires on the corresponding **uncheck** of the previously-selected button — we ignore that since `singleSelection="true"` makes the new check fully describe the new state).

Each call site:

```kotlin
binding.chargeEditTypeGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
binding.chargeEditCostModeGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
binding.wizardPage2UnitGroup.announceCheckedStateOnChange(R.string.a11y_toggle_selected)
```

Does **not** disturb the existing `addOnButtonCheckedListener` blocks that drive application logic — `MaterialButtonToggleGroup` supports multiple listeners.

## 5. Localisation

| Locale | Template |
|--------|----------|
| en (`values/`) | `%1$s selected` |
| el (`values-el/`) | `Επιλέχθηκε %1$s` (verb-first; matches the language's preference for placing the new state before the noun) |
| tr (`values-tr/`) | `%1$s seçildi` (noun-first + suffix verb) |
| ru (`values-ru/`) | `Выбрано: %1$s` (passive-neutral; colon separates the announcement from the value) |

These are first-pass machine-translated drafts. A native review pass for el/tr/ru can land later without breaking the build (the drafts are functional). Native review is a forward-work follow-up if any locale shows up as poor in real-device testing (TASK-78).

## 6. Testing

**No automated tests for the helper.** Justification:

- The helper is an extension on an Android view; pure-JVM testing requires Robolectric, which is not in this codebase's tooling chain.
- The internal logic (`if (!isChecked) return; getString; announce`) is too small a surface for an instrumented Espresso test to be worth its run-time cost — instrumented tests already average 30 s of overhead each.
- Verifying that `announceForAccessibility(...)` actually fires under instrumentation requires intercepting the `AccessibilityManager`, which is fragile and easily breaks with TalkBack engine updates.
- The fix's risk surface is one extra listener call per toggle change; a regression manifests immediately as "TalkBack stays silent on tap", which the existing TEST_PLAN §5c walkthrough catches at release-gate time.

**TEST_PLAN.md §5c additions.** Three new rows (one per toggle group) appended to the TalkBack smoke walkthrough, each in the same format as the existing rows: action → expected announcement.

| Step | Action | Expected TalkBack output |
|------|--------|--------------------------|
| 5c.4 | On ChargeEdit, double-tap the **DC** chip in the AC/DC toggle | `"DC selected"` |
| 5c.5 | On ChargeEdit, expand cost section, double-tap **Per kWh** | `"Per kWh selected"` |
| 5c.6 | On wizard page 2 (Reset preferences → re-enter), double-tap **miles** | `"miles selected"` (or the localised equivalent) |

## 7. Acceptance criteria

1. **CI gate green** — `./gradlew ktlintCheck :app:lint :app:testDebugUnitTest` passes locally and in CI. The new string clears `MissingTranslation` (error mode) by virtue of being present in all four locale files.
2. **All three call sites wired** — `grep -n "announceCheckedStateOnChange" app/src/main/java/...` returns exactly three hits, in `ChargeEditFragment.kt` and `WizardPage2Fragment.kt`.
3. **TEST_PLAN.md §5c contains three new rows** matching the table in §6.
4. **`docs/BACKLOG.md`** flips TASK-77 to ☑ with a one-line outcome banner.
5. **Manual TalkBack verification on the AVD** before merge — agent runs at least one toggle (`charge_edit_type_group`) under TalkBack and confirms the announcement fires. If TalkBack isn't available on the AVD image (it's an opt-in service), the agent documents that and notes that the deferred verification is by the user before tagging `v1.9.32`. (We opt-in to deferred user verification when the AVD lacks TalkBack to avoid blocking on environment.)

## 8. Risks + mitigations

| Risk | Mitigation |
|------|------------|
| `announceForAccessibility` is deprecated since API 36 in favour of `AccessibilityManager.interrupt()` + `AccessibilityEvent.TYPE_ANNOUNCEMENT`. | Min SDK is 26, target 35. The deprecation does not remove the API; it just suggests preferring the lower-level path. The helper is a one-liner that can be migrated later if Google ever marks it `@RequiresApi` or removes the public method. Compile target is 35, so the deprecation does not break the build. |
| Translation drift for el/tr/ru if the source string changes. | `MissingTranslation` is in error mode. Any change to the source string forces a build break until all three locales are updated. |
| Multiple listeners on the same toggle group could conflict. | `addOnButtonCheckedListener` is additive; Material's source code iterates the listener list. The new listener is purely a side-effect (no return value, no state mutation), so order does not matter. |
| The announcement template may sound awkward in some locales (no native review). | Documented as forward-work in §5. The drafts are functional and consistent. A native review pass can land in a separate small PR. |

## 9. Out of scope (filed if relevant)

- Native-language review pass for el/tr/ru announcement templates (if real-device TalkBack feedback flags it).
- Custom `roleDescription` per toggle (e.g. "AC charge type") — `info.roleDescription` would let TalkBack announce "AC charge type, button" on focus, but adds template complexity and is a different problem from the on-tap announcement TASK-77 targets.
- A full pass over every `MaterialButton` checkable in the app (the three toggle groups are the complete inventory in current `app/src/main/res/layout/`; future toggles should call the helper as a convention, no enforcement currently).
