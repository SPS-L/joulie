package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_locations",
    indices = [Index(value = ["label"], unique = true)]
)
data class CustomLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)
