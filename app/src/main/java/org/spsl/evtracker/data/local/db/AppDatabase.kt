package org.spsl.evtracker.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 3,
    exportSchema = true,
)
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
    }
}
