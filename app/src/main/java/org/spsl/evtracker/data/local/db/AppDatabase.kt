package org.spsl.evtracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.spsl.evtracker.data.local.dao.CarDao
import org.spsl.evtracker.data.local.dao.ChargeEventDao
import org.spsl.evtracker.data.local.dao.CustomLocationDao
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Database(
    entities = [
        CarEntity::class,
        ChargeEventEntity::class,
        CustomLocationEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(ChargeTypeConverter::class, ChargeKwhSourceConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun carDao(): CarDao
    abstract fun chargeEventDao(): ChargeEventDao
    abstract fun customLocationDao(): CustomLocationDao

    companion object {

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_events " +
                        "ADD COLUMN chargeType TEXT NOT NULL DEFAULT 'AC'",
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema changes to charge_events.
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costTotal REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN costPerKwh REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN currency TEXT")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN location TEXT")
                db.execSQL(
                    "ALTER TABLE charge_events " +
                        "ADD COLUMN note TEXT NOT NULL DEFAULT ''",
                )

                // New custom_locations table.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS custom_locations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "useCount INTEGER NOT NULL DEFAULT 1, " +
                        "lastUsed INTEGER NOT NULL" +
                        ")",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_locations_label " +
                        "ON custom_locations(label)",
                )

                // Indices on charge_events. IF NOT EXISTS keeps each statement
                // idempotent regardless of which version's createAllTables produced
                // the existing table.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_carId_eventDate " +
                        "ON charge_events(carId, eventDate)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_chargeType " +
                        "ON charge_events(chargeType)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charge_events_location " +
                        "ON charge_events(location)",
                )
            }
        }

        /**
         * TASK-25: rewrite legacy `'DC'` chargeType cells to `'DC_FAST'` so the
         * Room TypeConverter (and the [org.spsl.evtracker.core.model.ChargeType]
         * enum it produces) sees only canonical values for fresh reads. The
         * column type stays TEXT NOT NULL — only the cell values mutate, so
         * Room's v4 schema check passes without schema-DDL changes.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE charge_events SET chargeType = 'DC_FAST' WHERE chargeType = 'DC'",
                )
            }
        }

        /**
         * TASK-26: widen entity primary keys (and foreign keys) from Kotlin
         * `Int` to `Long`. SQLite already stores `INTEGER` columns as up to
         * 8 bytes, so the on-disk schema is unchanged — Room's affinity for
         * these columns stays `INTEGER` and the column DDL is identical.
         * The migration is therefore a deliberate no-op: bumping to version
         * 5 acts as a tripwire for the entity-side type change so future
         * downgrades (or stale DBs) trip Room's schema validator instead of
         * silently truncating Long values to Int.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: SQLite INTEGER columns already hold 64-bit signed
                // integers; widening Kotlin Int → Long doesn't change DDL.
            }
        }

        /**
         * TASK-14: add optional `socBefore` and `socAfter` REAL columns to
         * `charge_events` so the user can record state-of-charge data per
         * event. Both columns are nullable and default to NULL — events
         * persisted before this migration leave both fields blank.
         * `CapacityEstimator` consumes the fields when both are present
         * (else falls back to the heuristic `kwh_added` proxy on full charges).
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charge_events ADD COLUMN socBefore REAL")
                db.execSQL("ALTER TABLE charge_events ADD COLUMN socAfter REAL")
            }
        }

        /**
         * TASK-43: add a `kwhSource` provenance column to `charge_events`.
         * `MEASURED` events come from the charger or the user; they remain
         * eligible for the TASK-14 capacity-degradation tracker.
         * `DERIVED_FROM_SOC` events are produced by the in-form calculator
         * and are skipped by `CapacityEstimator` because the math is
         * tautological. The column is `NOT NULL DEFAULT 'MEASURED'` so legacy
         * rows backfill cleanly without a separate UPDATE pass.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE charge_events " +
                        "ADD COLUMN kwhSource TEXT NOT NULL DEFAULT 'MEASURED'",
                )
            }
        }
    }
}
