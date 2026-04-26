package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(car: CarEntity): Long

    @Update
    suspend fun update(car: CarEntity)

    @Delete
    suspend fun delete(car: CarEntity)
}
