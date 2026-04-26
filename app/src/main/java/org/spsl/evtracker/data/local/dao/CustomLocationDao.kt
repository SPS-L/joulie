package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

@Dao
abstract class CustomLocationDao {

    @Query("SELECT * FROM custom_locations ORDER BY useCount DESC, lastUsed DESC LIMIT 5")
    abstract fun observeTop5(): Flow<List<CustomLocationEntity>>

    @Query("SELECT * FROM custom_locations ORDER BY useCount DESC, lastUsed DESC")
    abstract fun observeAll(): Flow<List<CustomLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfMissing(location: CustomLocationEntity): Long

    @Query("UPDATE custom_locations SET useCount = useCount + 1, lastUsed = :now WHERE label = :label")
    abstract suspend fun incrementUseCount(label: String, now: Long)

    @Delete
    abstract suspend fun delete(location: CustomLocationEntity)

    @Transaction
    open suspend fun recordUsage(label: String, now: Long) {
        val rowId = insertIfMissing(
            CustomLocationEntity(label = label, useCount = 1, lastUsed = now)
        )
        if (rowId == -1L) {
            // Insert was IGNORED because the label already exists — bump the counter.
            incrementUseCount(label, now)
        }
    }
}
