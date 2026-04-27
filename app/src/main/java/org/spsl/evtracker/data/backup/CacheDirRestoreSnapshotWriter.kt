package org.spsl.evtracker.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter

@Singleton
class CacheDirRestoreSnapshotWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : RestoreSnapshotWriter {
    override fun write(json: String) {
        File(context.cacheDir, "last_overwritten_backup.json").writeText(json)
    }
}
