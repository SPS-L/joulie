package org.spsl.evtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.ChargeEventEntity

@Dao
interface ChargeEventDao {

    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    fun observeForCar(carId: Long): Flow<List<ChargeEventEntity>>

    @Query(
        "SELECT * FROM charge_events " +
            "WHERE carId = :carId AND eventDate BETWEEN :from AND :to " +
            "ORDER BY eventDate ASC",
    )
    suspend fun getInRange(carId: Long, from: Long, to: Long): List<ChargeEventEntity>

    @Query("SELECT * FROM charge_events WHERE carId = :carId ORDER BY eventDate ASC")
    suspend fun getAllForCarSorted(carId: Long): List<ChargeEventEntity>

    @Query("SELECT * FROM charge_events WHERE id = :id")
    suspend fun getById(id: Long): ChargeEventEntity?

    @Insert
    suspend fun insert(event: ChargeEventEntity): Long

    @Update
    suspend fun update(event: ChargeEventEntity)

    @Delete
    suspend fun delete(event: ChargeEventEntity)

    @Query("DELETE FROM charge_events WHERE carId = :carId")
    suspend fun deleteForCar(carId: Long)

    @Query("DELETE FROM charge_events")
    suspend fun deleteAll()
}
