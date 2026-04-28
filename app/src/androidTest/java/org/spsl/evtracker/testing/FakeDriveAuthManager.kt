package org.spsl.evtracker.testing

import org.spsl.evtracker.domain.backup.DriveAuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDriveAuthManager @Inject constructor() : DriveAuthManager {
    var nextResult: DriveAuthManager.AuthResult = DriveAuthManager.AuthResult.Success("fake-token")

    override suspend fun authorize(): DriveAuthManager.AuthResult = nextResult

    override suspend fun silentToken(): DriveAuthManager.AuthResult =
        when (val r = nextResult) {
            is DriveAuthManager.AuthResult.NeedsResolution ->
                DriveAuthManager.AuthResult.Failed("consent required")
            else -> r
        }
}
