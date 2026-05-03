// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_locations",
    indices = [Index(value = ["label"], unique = true)],
)
data class CustomLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val useCount: Int = 1,
    val lastUsed: Long,
)
