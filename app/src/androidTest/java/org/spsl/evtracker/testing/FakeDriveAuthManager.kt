package org.spsl.evtracker.testing

import org.spsl.evtracker.domain.backup.DriveAuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDriveAuthManager @Inject constructor() : DriveAuthManager {
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token")

    /**
     * TASK-54: number of times [authorize] has been called. Used as the
     * regression-test signal for "the Drive switch listener fired on view-state
     * restoration" — pre-fix this counter increments on every
     * `SettingsFragment` re-entry; post-fix it stays at zero on bare entry.
     */
    @Volatile var authorizeCallCount: Int = 0
        private set

    override suspend fun authorize(): DriveAuthManager.AuthResult {
        authorizeCallCount++
        return nextResult
    }

    override suspend fun silentToken(): DriveAuthManager.AuthResult =
        when (val r = nextResult) {
            is DriveAuthManager.AuthResult.NeedsResolution ->
                DriveAuthManager.AuthResult.Failed("consent required")
            else -> r
        }
}
