# TASK-11 Implementation Plan — Odometer Regression UX

**Goal:** Pre-fill the odometer in Create mode, show inline regression
errors as the user types, and gate the Save button on those errors.
Edit mode also enforces an upper bound against the chronologically-next
event when present.

**Architecture:** State changes flow through `ChargeEditUiState` →
`ChargeEditViewModel.setOdometer` → `ChargeEditFragment.render`. No new
classes; one new sorted-events fetch in `init` (Create + Edit) caches
`previousOdometerKm` and `nextOdometerKm` on state.

**Tech Stack:** Kotlin 1.9.21, Hilt, MaterialTextInputLayout, JUnit 4.

---

## Task 1 — Add new fields to `ChargeEditUiState`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/core/model/ChargeEditUiState.kt`

- [ ] Add `previousOdometerKm: Double? = null`,
  `nextOdometerKm: Double? = null`,
  `odometerBelowPrevious: Boolean = false`,
  `odometerAboveNext: Boolean = false` to the data class.

(No tests for the data class directly — its behaviour is exercised
through ViewModel tests.)

## Task 2 — Failing tests for prefill + inline regression (TDD red)

**Files:**
- Modify: `app/src/test/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModelTest.kt`

- [ ] Add the 10 new test cases listed in the spec (see "Tests" section).
- [ ] Run `:app:testDebugUnitTest --tests
  "org.spsl.evtracker.ui.chargeedit.ChargeEditViewModelTest"`. Expected:
  10 failures (current state has neither prefill, neighbour fields, nor
  flag updates).

## Task 3 — Implement Create-mode prefill + neighbour fetch

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModel.kt`

- [ ] In the `init` block's Create-mode branch (where `rawId == -1` AND
  the `event == null` fallback), call
  `chargeEventQueries.getAllForCarSorted(activeCarId)` once.
- [ ] Compute `prevKm = sorted.lastOrNull()?.odometerKm`.
- [ ] Compute `prefilled = prevKm?.let { (it + 1.0).toDisplayUnit(unit).toString() } ?: ""`.
  Add a private helper:

  ```kotlin
  private fun Double.toDisplayUnit(unit: String): Double =
      if (unit == "miles") UnitConverter.kmToMiles(this) else this
  ```

- [ ] Set `odometer = prefilled, previousOdometerKm = prevKm` in the
  Create-mode `ChargeEditUiState(...)` constructor.
- [ ] Re-run the prefill tests — Tasks 2's cases #1, #2, #3 should now pass.

## Task 4 — Implement Edit-mode neighbour wiring

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModel.kt`

- [ ] In the Edit-mode branch, fetch
  `val sorted = chargeEventQueries.getAllForCarSorted(event.carId)`.
- [ ] Locate `idx = sorted.indexOfFirst { it.id == event.id }`.
- [ ] Set `previousOdometerKm = sorted.getOrNull(idx - 1)?.odometerKm`,
  `nextOdometerKm = sorted.getOrNull(idx + 1)?.odometerKm`.
- [ ] Compute initial regression flags from `event.odometerKm`:
  - `odometerBelowPrevious = previousOdometerKm != null && event.odometerKm <= previousOdometerKm!!`
  - `odometerAboveNext = nextOdometerKm != null && event.odometerKm >= nextOdometerKm!!`
- [ ] Re-run tests — cases #4, #5, #6 should now pass.

## Task 5 — Re-evaluate flags on every `setOdometer`

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditViewModel.kt`

- [ ] Replace the body of `setOdometer(text: String)` with the recompute
  block from the spec ("setOdometer" subsection). Both flags reset to
  `false` on blank/unparseable input.
- [ ] Re-run tests — cases #7, #8, #9, #10 should now pass. Total green.

## Task 6 — Add the new error strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Insert (next to the existing odometer strings):

  ```xml
  <string name="error_odometer_must_be_greater_than">Must be greater than last entry (%1$s %2$s)</string>
  <string name="error_odometer_must_be_less_than">Must be less than next entry (%1$s %2$s)</string>
  ```

## Task 7 — Render inline error + gate Save in Fragment

**Files:**
- Modify: `app/src/main/java/org/spsl/evtracker/ui/chargeedit/ChargeEditFragment.kt`

- [ ] Replace `binding.chargeEditOdometerLayout.error = state.odometerError?.let { getString(it) }`
  with the three-branch `when` from the spec.
- [ ] Replace `binding.chargeEditSave.isEnabled = !state.saving` with
  `!state.saving && !state.odometerBelowPrevious && !state.odometerAboveNext`.
- [ ] Add the two private helpers `formatOdometer` and `unitLabel` to the
  Fragment file.

## Task 8 — Build + test gate

- [ ] `:app:testDebugUnitTest` — green; total ≥ 257.
- [ ] `:app:assembleDebug` — green.
- [ ] `:app:assembleRelease` — green (R8 still passes).
- [ ] `ktlintCheck` — green; run `ktlintFormat` if needed.
- [ ] `:app:lint` — no new offences vs baseline.
- [ ] `:app:assembleDebugAndroidTest` — compiles.

## Task 9 — Update docs + commit

- [ ] `docs/BACKLOG.md`: flip TASK-11 to ☑ Done with an outcome block
  matching the TASK-27/TASK-28 pattern.
- [ ] `CLAUDE.md`: append a short TASK-11 note in the Status paragraph;
  bump `JVM unit-test count: 247` → `257` in Status + Build & Test
  sections.
- [ ] `README.md`: bump `~247` → `~257` in the Tests block.
- [ ] Commit on feat branch (single commit), merge `--no-ff` to main,
  push, delete branch.
