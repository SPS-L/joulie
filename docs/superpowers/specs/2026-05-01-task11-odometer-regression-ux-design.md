# TASK-11 — Odometer Regression UX Improvement

> **Status:** spec for backlog item TASK-11 (🟡, no hard prerequisites).
> See `docs/BACKLOG.md` for the original task body. The current behaviour
> shows a generic on-save error for any odometer regression and does not
> pre-fill the field; this spec replaces both with an inline-validation
> experience tied to the actual previous (and, in edit mode, next) entry.

## Goal

When the user opens the charge-edit screen, give them an odometer field
that is already useful and visibly self-validating:

1. **Pre-fill** the odometer in **Create** mode with the active car's
   previous-event odometer + 1 (in km storage; rendered in the user's
   display unit). No pre-fill in **Edit** mode — the field already loads
   the existing event's value.
2. **Inline error** (via `TextInputLayout.error`, updated on every
   `setOdometer`) whenever the typed value would regress past the previous
   recorded entry, with text:
   `Must be greater than last entry (<prev value> <unit>)`.
3. **Save button gated** on the inline-error state: disabled while a
   regression message is showing.
4. **Edit mode** also enforces an upper bound when editing a non-latest
   event: the value must remain less than the chronologically-next event's
   odometer. Inline message:
   `Must be less than next entry (<next value> <unit>)`.

## Non-goals

- Re-architecting `ChargeEditViewModel` / `ChargeEditUiState`. We add
  state fields and one new `setOdometer` branch; we do not introduce a
  separate validator object or move logic into `domain/`.
- Re-running the use case's odometer-increasing check. The use case still
  guards on save (defence in depth) — TASK-11 is purely the UX layer.
- Internationalising the unit suffix differently than today. The unit is
  already `"km"` / `"mi"` per `state.distanceUnit`; we surface the same
  value verbatim.
- Adding an `error_odometer_must_be_higher` removal. The string stays
  alive as a fallback for the use case's `OdometerNotIncreasing` result
  (which can still fire on race conditions where two events share an
  `eventDate`).

## State diff (`core/model/ChargeEditUiState.kt`)

Three new fields, all optional / defaulted:

```kotlin
data class ChargeEditUiState(
    ...existing fields...,

    /** Previous event's odometer in km (chronologically before this event,
     *  or the latest entry in Create mode). Null when no prior event exists
     *  for the active car. */
    val previousOdometerKm: Double? = null,

    /** Next event's odometer in km. Set only in Edit mode when the event
     *  being edited is not the chronologically-latest one. */
    val nextOdometerKm: Double? = null,

    /** True when the parsed odometer text (in km) is ≤ previousOdometerKm.
     *  Drives the inline TextInputLayout error and the Save button gate. */
    val odometerBelowPrevious: Boolean = false,

    /** True when the parsed odometer text (in km) is ≥ nextOdometerKm
     *  (Edit-mode upper bound). */
    val odometerAboveNext: Boolean = false,
)
```

## ViewModel diff (`ui/chargeedit/ChargeEditViewModel.kt`)

### Init (Create mode)

After loading `activeCarId`, fetch the sorted event history once:

```kotlin
val sorted = chargeEventQueries.getAllForCarSorted(activeCarId)
val prevKm = sorted.lastOrNull()?.odometerKm
val prefilled = prevKm?.let { (it + 1.0).toDisplay(unit).format() } ?: ""
```

Where:

- `Double.toDisplay(unit: String)` returns `kmToMiles(this)` for `"miles"`,
  else `this`.
- `Double.format()` matches the existing edit-mode pattern (`.toString()`
  yields e.g. `"12346.0"`). Don't introduce locale-aware formatting here
  — `.toDoubleOrNull()` round-trips reliably with this output and stays
  consistent with how Edit mode renders the existing value.

The Create-mode `_uiState.value = ChargeEditUiState(...)` gains:

```kotlin
odometer = prefilled,
previousOdometerKm = prevKm,
odometerBelowPrevious = false,   // prev + 1 is by construction strictly greater
```

### Init (Edit mode)

When `event != null`, pull the sorted list once and locate the event's
chronological neighbours:

```kotlin
val sorted = chargeEventQueries.getAllForCarSorted(event.carId)
val idx = sorted.indexOfFirst { it.id == event.id }
val prevKm = sorted.getOrNull(idx - 1)?.odometerKm
val nextKm = sorted.getOrNull(idx + 1)?.odometerKm
```

The Edit-mode `_uiState.value = ChargeEditUiState(...)` gains:

```kotlin
previousOdometerKm = prevKm,
nextOdometerKm = nextKm,
odometerBelowPrevious = prevKm != null && event.odometerKm <= prevKm,
odometerAboveNext = nextKm != null && event.odometerKm >= nextKm,
```

(Both flags are typically `false` for a clean dataset; the edit form
re-checks if the user types a different value.)

### `setOdometer(text: String)`

Re-evaluate the two flags every time:

```kotlin
fun setOdometer(text: String) = _uiState.update { st ->
    val km = text.trim().toDoubleOrNull()?.let { typed ->
        if (st.distanceUnit == "miles") UnitConverter.milesToKm(typed) else typed
    }
    st.copy(
        odometer = text,
        odometerError = null,
        odometerBelowPrevious = km != null && st.previousOdometerKm != null &&
            km <= st.previousOdometerKm,
        odometerAboveNext = km != null && st.nextOdometerKm != null &&
            km >= st.nextOdometerKm,
    )
}
```

Empty / unparseable input clears both flags (the existing required-on-save
check still catches that path).

### `save()`

No change to the validation order. The Fragment's gate on
`odometerBelowPrevious || odometerAboveNext` prevents `save()` from being
clickable while inline errors are showing, but the use case's
`OdometerNotIncreasing` branch is left intact for the same-`eventDate`
edge case.

## Fragment diff (`ui/chargeedit/ChargeEditFragment.kt`)

`render(state)` learns to format the inline message and gate the button:

```kotlin
val errorText = when {
    state.odometerError != null -> getString(state.odometerError)
    state.odometerBelowPrevious -> getString(
        R.string.error_odometer_must_be_greater_than,
        formatOdometer(state.previousOdometerKm!!, state.distanceUnit),
        unitLabel(state.distanceUnit),
    )
    state.odometerAboveNext -> getString(
        R.string.error_odometer_must_be_less_than,
        formatOdometer(state.nextOdometerKm!!, state.distanceUnit),
        unitLabel(state.distanceUnit),
    )
    else -> null
}
binding.chargeEditOdometerLayout.error = errorText
binding.chargeEditSave.isEnabled =
    !state.saving && !state.odometerBelowPrevious && !state.odometerAboveNext
```

`formatOdometer` and `unitLabel` are private file-level helpers in
`ChargeEditFragment.kt` (no need for a new common util):

```kotlin
private fun formatOdometer(km: Double, unit: String): String {
    val display = if (unit == "miles") UnitConverter.kmToMiles(km) else km
    return "%.1f".format(display)
}
private fun unitLabel(unit: String): String =
    if (unit == "miles") "mi" else "km"
```

## Strings (`res/values/strings.xml`)

Two new parameterised strings — keep `error_odometer_must_be_higher` for
the use-case fallback:

```xml
<string name="error_odometer_must_be_greater_than">Must be greater than last entry (%1$s %2$s)</string>
<string name="error_odometer_must_be_less_than">Must be less than next entry (%1$s %2$s)</string>
```

## Tests (`app/src/test/java/.../ChargeEditViewModelTest.kt`)

TDD-first: every behaviour gets a failing test before the production
diff lands. New cases (≈ 10):

1. `createMode_noPreviousEvent_doesNotPrefillOdometer` — gateway has no
   events; `state.odometer` is `""` and `state.previousOdometerKm` is null.
2. `createMode_withPreviousEvent_kmUnit_prefillsPrevPlusOne` — single
   seeded event at 12345 km; `state.odometer == "12346.0"` and
   `state.previousOdometerKm == 12345.0`.
3. `createMode_withPreviousEvent_milesUnit_prefillsConverted` —
   `distanceUnitInit = "miles"`; `state.odometer` is the miles value of
   `12346.0 km`.
4. `editMode_doesNotChangePrefill_butLoadsNeighbours` — three seeded
   events; editing the middle one populates both `previousOdometerKm` and
   `nextOdometerKm` from the actual neighbours.
5. `editMode_firstEvent_hasNoPrevious` — editing the chronologically-first
   event leaves `previousOdometerKm` null.
6. `editMode_lastEvent_hasNoNext` — editing the chronologically-last event
   leaves `nextOdometerKm` null.
7. `setOdometer_belowPrevious_setsBelowPreviousFlag` — typed value ≤
   `previousOdometerKm` flips the flag true.
8. `setOdometer_abovePrevious_clearsBelowPreviousFlag` — typed value >
   previous flips the flag false.
9. `setOdometer_aboveNext_inEditMode_setsAboveNextFlag` — typed value ≥
   `nextOdometerKm` flips the flag true (Edit mode only).
10. `setOdometer_blank_clearsBothRegressionFlags` — empty string =
    no regression assertion (Save still blocked by the existing
    required-on-save path).

The existing `save_useCaseReturnsOdoNotIncreasing_setsError` test stays —
it now exercises the use-case fallback (same-`eventDate` race), which
still flips the field's `odometerError`.

## Acceptance

- 10 new JVM test cases pass; total count rises from 247 → 257.
- `:app:assembleDebug`, `:app:assembleRelease` (R8), `:app:lint`,
  `ktlintCheck`, `:app:assembleDebugAndroidTest` all green.
- Manual smoke (verifiable on real device, not in CI):
  - Create flow on a car with prior history — odometer pre-filled.
  - Type a regressing value — inline error appears, Save disables.
  - Type a valid value — error clears, Save re-enables.
  - Edit a middle-of-history event with a too-large value — upper-bound
    inline error.

## Risks

- **Edit-mode `getAllForCarSorted` is called twice in `init`.** Once
  already (no — it's actually a NEW call we add). The DAO is `suspend`
  and reads from a `MutableStateFlow` in tests, so a second collection
  is cheap. In production the Room query runs once and the result is
  small.
- **Locale-formatted output.** Using `"%.1f".format(...)` follows the
  default locale, so a German user sees `12345,0`. That's acceptable —
  matches the formatting the field already accepts via
  `inputType="numberDecimal"` (which respects locale separators).
- **Pre-fill side effect on `setOdometer`.** When the Fragment binds the
  prefilled value back to the EditText, `doAfterTextChanged` re-fires
  `setOdometer` with the same string. The new logic is idempotent —
  re-evaluation produces the same flags — so this is benign.
