package org.spsl.evtracker.testing

import org.spsl.evtracker.domain.backup.DriveAuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDriveAuthManager @Inject constructor() : DriveAuthManager {
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token")

    /**
     * Number of times [authorize] has been called. Used as the
     * regression-test signal for "the Drive switch listener fired on
     * view-state restoration" — bare `SettingsFragment` entry must leave
     * this counter at zero.
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
