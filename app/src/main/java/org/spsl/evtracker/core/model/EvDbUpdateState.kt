// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

import androidx.annotation.StringRes

/**
 * UI state for the Settings → "Update EV database" button (TASK-91).
 *
 * Transitions: [Idle] → [Loading] → [Success] | [Failure] → [Idle].
 * The button disables itself while [Loading] is on screen so a
 * duplicate tap can't fire a second concurrent fetch.
 */
sealed class EvDbUpdateState {
    object Idle : EvDbUpdateState()
    object Loading : EvDbUpdateState()
    data class Success(val version: String, val vehicleCount: Int) : EvDbUpdateState()
    data class Failure(@StringRes val reasonRes: Int) : EvDbUpdateState()
}
