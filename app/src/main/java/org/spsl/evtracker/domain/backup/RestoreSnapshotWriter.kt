package org.spsl.evtracker.domain.backup

/**
 * Persists a JSON snapshot of pre-restore local state to a deterministic location.
 * Production: [org.spsl.evtracker.data.backup.CacheDirRestoreSnapshotWriter] writes to
 * cacheDir/last_overwritten_backup.json. Tests: capture the JSON in memory.
 */
interface RestoreSnapshotWriter {
    fun write(json: String)
}
