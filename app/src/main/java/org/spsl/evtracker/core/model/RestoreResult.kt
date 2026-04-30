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
