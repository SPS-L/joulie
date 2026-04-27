package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CarEntity

@Dao
interface CarDao {

    @Query("SELECT * FROM cars ORDER BY name")
    fun observeAll(): Flow<List<CarEntity>>

    @Query("SELECT * FROM cars WHERE id = :id")
    suspend fun getById(id: Int): CarEntity?

    // Default OnConflictStrategy.ABORT (no `onConflict` parameter). NOT REPLACE:
    // a stale-id insert on cars would DELETE the existing row and cascade-delete
    // every charge_event for that car (charge_events FK is ON DELETE CASCADE).
    // Edits go through @Update; mismatches throw SQLiteConstraintException to
    // surface the bug rather than silently wipe data.
    @Insert
    suspend fun insert(car: CarEntity): Long

    @Update
    suspend fun update(car: CarEntity)

    @Query("UPDATE cars SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM cars WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Delete
    suspend fun delete(car: CarEntity)

    @Query("DELETE FROM cars")
    suspend fun deleteAll()
}
