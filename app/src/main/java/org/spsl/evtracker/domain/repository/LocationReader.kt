// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.spsl.evtracker.data.local.entity.CustomLocationEntity

interface LocationReader {
    fun observeTop5(): Flow<List<CustomLocationEntity>>
    fun observeAll(): Flow<List<CustomLocationEntity>>
}
