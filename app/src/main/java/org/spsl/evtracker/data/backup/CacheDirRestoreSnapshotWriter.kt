// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.spsl.evtracker.domain.backup.RestoreSnapshotWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheDirRestoreSnapshotWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestoreSnapshotWriter {
    override fun write(json: String) {
        File(context.cacheDir, "last_overwritten_backup.json").writeText(json)
    }
}
