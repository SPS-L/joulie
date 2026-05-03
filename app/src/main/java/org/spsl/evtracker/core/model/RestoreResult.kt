// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

sealed class RestoreResult {
    object NoRemoteBackup : RestoreResult()
    data class VersionMismatch(val actualVersion: Int) : RestoreResult()
    data class Success(
        val carCount: Int,
        val eventCount: Int,
        val locationCount: Int,
    ) : RestoreResult()
}
