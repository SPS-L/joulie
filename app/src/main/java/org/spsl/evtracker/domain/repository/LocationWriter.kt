// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationWriter {
    suspend fun recordUsage(label: String, now: Long)
    suspend fun delete(location: CustomLocationEntity)

    /** Global reset. */
    suspend fun deleteAll()
}
