// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.db

import androidx.room.AutoMigration
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
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
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
         * Rewrite legacy `'DC'` chargeType cells to `'DC_FAST'` so the
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
         * Widen entity primary keys (and foreign keys) from Kotlin
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

        // v5 → v6 (add socBefore + socAfter REAL columns) and
        // v6 → v7 (add kwhSource TEXT NOT NULL DEFAULT 'MEASURED') run via
        // @AutoMigration entries on the @Database annotation. Room's KSP
        // synthesises the migration SQL from the exported schemas in
        // app/schemas/ at compile time. Equivalent to the hand-written
        // ALTER TABLE ADD COLUMN they replaced. See TASK-39.
    }
}
