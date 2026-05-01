package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val batteryKwh: Double? = null,
    val createdAt: Long,
)
