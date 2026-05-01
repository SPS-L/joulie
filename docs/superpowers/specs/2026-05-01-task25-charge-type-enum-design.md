# TASK-25 — `chargeType` String → typed `ChargeType` enum

> **Status:** spec for backlog item TASK-25 (🟡, no hard prerequisites).
> See `docs/BACKLOG.md` for the original task body. Migrates the
> historically-stringly-typed `chargeType` field across the entity, the
> domain layer, the UI state, and the backup wire format to a Kotlin
> `ChargeType` enum, with a Room v3→v4 schema bump and a backup
> `backup_version` 3→4 bump that **stays backwards-compatible** with v3
> backups in the wild.

## Goal

Replace `val chargeType: String` everywhere with the new
`ChargeType` enum:

```kotlin
package org.spsl.evtracker.core.model

enum class ChargeType {
    AC,
    DC_FAST,
    DC_ULTRA;

    /** True for any DC variant. The AC vs DC UI distinction collapses DC_FAST and DC_ULTRA. */
    val isDc: Boolean get() = this != AC

    /** Short label used in the History badge / chart legends ("AC" / "DC"). */
    fun displayLabel(): String = if (this == AC) "AC" else "DC"

    companion object {
        /**
         * Decode a stored / serialized chargeType string.
         * - "AC" → AC
         * - "DC" → DC_FAST  (legacy v3 row alias; preserved for backups + migrated rows)
         * - "DC_FAST" → DC_FAST
         * - "DC_ULTRA" → DC_ULTRA
         * - anything else → AC (defensive default; v3 rows never contained these)
         */
        fun parseLegacy(s: String): ChargeType = when (s) {
            "AC" -> AC
            "DC" -> DC_FAST
            "DC_FAST" -> DC_FAST
            "DC_ULTRA" -> DC_ULTRA
            else -> AC
        }
    }
}
```

The UI exposes only **AC** and **DC** (one toggle button each) — DC button
maps to `DC_FAST`. `DC_ULTRA` is forward-compat: declared today, surfaced
later (charging-network differentiation is out of scope here).

## Non-goals

- Splitting "DC fast" vs "DC ultra" in the UI, charts, or stats. The
  Trend / AcDcSplit / chip-filter logic still buckets by `isDc`.
- Adding charging-network metadata or per-event charger-power fields.
- Migrating the chargeType column type from `TEXT` to anything else.
  TEXT remains; only the values mutate (`'DC'` → `'DC_FAST'`).
- Bringing `error_odometer_must_be_higher` or other non-charge-type
  string resources into scope.

## Production diff

### New files

- `core/model/ChargeType.kt` — the enum above.
- `data/local/db/ChargeTypeConverter.kt`:

  ```kotlin
  class ChargeTypeConverter {
      @TypeConverter fun fromChargeType(value: ChargeType): String = value.name
      @TypeConverter fun toChargeType(value: String): ChargeType = ChargeType.parseLegacy(value)
  }
  ```

- `domain/service/ChargeTypeJsonAdapter.kt` — Gson `JsonSerializer` +
  `JsonDeserializer` reusing `ChargeType.parseLegacy(...)` so the
  backup JSON wire format stays as the string `"AC"` / `"DC_FAST"` /
  `"DC_ULTRA"` (and tolerates legacy `"DC"` from v3 backups).

### Modified files (production)

| File | Change |
|------|--------|
| `core/model/ChargeEditUiState.kt` | `chargeType: String = "AC"` → `chargeType: ChargeType = ChargeType.AC` |
| `core/model/SaveChargeEventInput.kt` | `chargeType: String` → `chargeType: ChargeType` |
| `core/model/BackupData.kt` | `ChargeEventDto.chargeType: String` → `ChargeType`. Bump `CURRENT_VERSION = 3` → `4`. |
| `data/local/entity/ChargeEventEntity.kt` | `chargeType: String = "AC"` → `chargeType: ChargeType = ChargeType.AC`. Index unchanged. |
| `data/local/db/AppDatabase.kt` | `version = 3` → `4`; add `@TypeConverters(ChargeTypeConverter::class)` on the class; add `MIGRATION_3_4` companion. |
| `di/DatabaseModule.kt` | `addMigrations(..., MIGRATION_3_4)` |
| `domain/service/StatsCalculator.kt` | `seriesFor(type: String)` → `seriesFor(predicate: (ChargeEventEntity) -> Boolean)`; `computeAcDcSplit` filters by `isDc`. Comparisons against `"AC"` / `"DC"` removed. |
| `domain/service/BackupSerializer.kt` | Gson builder registers the type adapter; version check loosens to `parsed.backupVersion in setOf(3, 4)` (with the v3 fallback path applied through `parseLegacy`). |
| `domain/usecase/ObserveDashboardStatsUseCase.kt` | Filter by `it.chargeType == ChargeType.AC` / `it.chargeType.isDc`. |
| `domain/usecase/ExportCsvUseCase.kt` | Write `e.chargeType.name` (so the CSV stays `"AC"` / `"DC_FAST"`). |
| `ui/chargeedit/ChargeEditViewModel.kt` | `setChargeType(type: ChargeType)`; the existing `chargeType` field on `ChargeEditUiState` is `ChargeType`. |
| `ui/chargeedit/ChargeEditFragment.kt` | AC button → `ChargeType.AC`, DC button → `ChargeType.DC_FAST`. Render compares `state.chargeType.isDc`. |
| `ui/history/HistoryAdapter.kt` | Badge text = `row.event.chargeType.displayLabel()`. |
| `ui/history/HistoryViewModel.kt` | Filter by `it.chargeType == ChargeType.AC` / `it.chargeType.isDc`. |

### Migration

```kotlin
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The chargeType column type stays TEXT; only the cell value mutates.
        // Existing v3 rows have either "AC" or "DC". Map "DC" → "DC_FAST" so
        // ChargeTypeConverter.toChargeType doesn't have to silently coerce.
        db.execSQL("UPDATE charge_events SET chargeType = 'DC_FAST' WHERE chargeType = 'DC'")
    }
}
```

No DDL changes — Room's schema check on v4 sees the same column type
and indices as v3, plus the converter is a Kotlin-side concern that
doesn't affect the SQL DDL.

## Backup compatibility

- New backups write `backup_version = 4` and serialise `chargeType` as
  `"AC"` / `"DC_FAST"` / `"DC_ULTRA"`.
- Old v3 backups carry `backup_version = 3` and `chargeType ∈ {"AC","DC"}`.
- `BackupSerializer.fromJson` accepts both; `ChargeTypeJsonAdapter`'s
  deserializer routes through `ChargeType.parseLegacy(s)` which handles
  the legacy `"DC"` alias.
- The `BackupVersionMismatch` exception still fires for any other
  version (e.g. a hypothetical v5 backup loaded against this app).

## Tests

### New JVM tests

- `ChargeTypeConverterTest.kt` — three cases:
  1. `roundTrip_acAndDcFast_preserved` — `toChargeType(fromChargeType(x)) == x` for AC / DC_FAST / DC_ULTRA.
  2. `legacyDcString_mapsToDcFast` — `toChargeType("DC") == DC_FAST`.
  3. `unknownString_fallsBackToAc` — `toChargeType("garbage") == AC`.
- `ChargeTypeTest.kt` — three cases:
  1. `parseLegacy_acAndDc_round_trip` — covers all four valid inputs.
  2. `isDc_returns_true_for_dcVariants` — AC false, DC_FAST true, DC_ULTRA true.
  3. `displayLabel_collapsesDcVariants` — AC → "AC", DC_FAST → "DC", DC_ULTRA → "DC".

### Existing tests — mechanical updates

Roughly 19 test files thread `chargeType` literals (74 hits across
`app/src/test` and `app/src/androidTest`). The mechanical edit is:

- `chargeType = "AC"` / `chargeType = "DC"` in `ChargeEventEntity(...)`
  constructor calls → `ChargeType.AC` / `ChargeType.DC_FAST`.
- Equality assertions `assertEquals("DC", state.chargeType)` →
  `assertEquals(ChargeType.DC_FAST, state.chargeType)`.
- Test fixtures with `private fun ev(type: String, ...)` flip the
  parameter type to `ChargeType` and call sites pass enum literals.
- `setChargeType("AC")` / `setChargeType("DC")` →
  `setChargeType(ChargeType.AC)` / `setChargeType(ChargeType.DC_FAST)`.

### Migration test (instrumented)

Add `migrate_3_to_4` to `app/src/androidTest/.../db/MigrationTest.kt`:

1. Build a v3 DB with the existing helper pattern (`buildV3Database`).
2. Insert a row with `chargeType = 'DC'`.
3. Run `MIGRATION_3_4.migrate(db)`.
4. Read back `SELECT chargeType FROM charge_events WHERE id = 1` —
   expect `"DC_FAST"`.
5. Add a `migrate_1_to_4_validatesSchema` (or extend the existing
   `migrate_1_to_3_validatesSchema`) that runs all four migrations
   in order and confirms Room opens the v4 schema cleanly. The
   assertion on `event.chargeType` becomes `ChargeType.AC`.

`openWithRoom()` is updated to register all four migrations.

## Acceptance

- `:app:testDebugUnitTest` green; expected count 257 → ≈ 263 (six new
  cases — three for converter, three for the enum).
- `:app:assembleDebug`, `:app:assembleRelease` (R8), `:app:lint`,
  `ktlintCheck`, `:app:assembleDebugAndroidTest` all green.
- `grep -rn '"AC"\|"DC"' app/src/main/java` returns hits only inside
  `ChargeType.parseLegacy` and the migration's `'DC_FAST' WHERE chargeType = 'DC'`
  string — every other production literal becomes a `ChargeType.*`
  reference.
- `grep -n "backupVersion" app/src/main/java/.../BackupSerializer.kt`
  shows the loosened version check.

## Risks

- **Room schema validation.** The column stays TEXT NOT NULL, indices
  are unchanged. Room's `@TypeConverters` is registered at the database
  class level so `chargeType: ChargeType` round-trips via the converter
  during read/write. The schema dump (`@Database(exportSchema = true)`)
  will publish a v4 entry; we accept the new schema file landing under
  `app/schemas/`.
- **Stat tests that rely on `chargeType` filtering.** All affected
  tests construct events with explicit `chargeType = ChargeType.AC|DC_FAST`
  after the migration; the asserted bucketing (AC vs DC) keeps working
  because we route through `isDc`.
- **CSV consumers.** External consumers of the CSV export who relied
  on `"DC"` (literal) will now see `"DC_FAST"`. Acceptable: the export
  format is debug-grade today (no schema document), and `"DC_FAST"` is
  a strict refinement of `"DC"`.
- **MIGRATION_2_3 unaffected.** The chargeType column was added in
  MIGRATION_1_2 with `DEFAULT 'AC'`; MIGRATION_2_3 doesn't touch it.
  MIGRATION_3_4 only updates string values; no DDL.

## Out-of-scope follow-ups

- DC_ULTRA UI exposure (third toggle button + appropriate stats split).
- Reading-side migration of the CSV export header to indicate the
  enum domain (`"AC"` / `"DC_FAST"` / `"DC_ULTRA"`).
- TASK-26 (PK Int → Long) — independent; whichever lands first claims
  the next migration version slot.
