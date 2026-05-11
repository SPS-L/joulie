// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.domain.repository.EvModelReader
import org.spsl.evtracker.domain.repository.UpdateResult
import javax.inject.Inject

/**
 * Thin wrapper around [EvModelReader.updateFromRemote] (TASK-91) so the
 * `SettingsViewModel` depends on a use case, not on the narrow
 * repository interface directly. The use case adds nothing on top of
 * the repository call today — its job is to be a stable seam if/when
 * we add side-effects (analytics, retry policy, throttling).
 */
class UpdateEvDatabaseUseCase @Inject constructor(
    private val evModelReader: EvModelReader,
) {
    suspend operator fun invoke(): UpdateResult = evModelReader.updateFromRemote()
}
