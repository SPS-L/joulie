// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.backup

import android.content.IntentSender
import java.io.IOException

/**
 * Resolves an OAuth2 access token for the `drive.appdata` scope.
 *
 * Two entry points:
 * - [authorize] — used by SettingsFragment when the user toggles Drive ON. May return
 *   [AuthResult.NeedsResolution] so the Fragment can launch the consent IntentSender.
 * - [silentToken] — used by DriveBackupRepository (and therefore the Worker). Collapses
 *   NeedsResolution into Failed because the worker has no Activity to resolve from.
 */
interface DriveAuthManager {

    /** Result of an authorization attempt. */
    sealed class AuthResult {
        data class Success(val accessToken: String) : AuthResult()
        data class NeedsResolution(val intentSender: IntentSender) : AuthResult()
        data class Failed(val reason: String, val cause: Throwable? = null) : AuthResult()
    }

    suspend fun authorize(): AuthResult

    /** Like [authorize] but never returns NeedsResolution. NeedsResolution → Failed. */
    suspend fun silentToken(): AuthResult
}

/** Thrown by [DriveAuthManager.silentToken] callers when consent is required or revoked. */
class DriveAuthRequiredException(
    message: String = "Drive consent required or revoked",
    cause: Throwable? = null,
) : IOException(message, cause)
