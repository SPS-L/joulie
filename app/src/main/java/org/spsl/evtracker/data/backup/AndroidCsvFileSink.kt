package org.spsl.evtracker.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import org.spsl.evtracker.domain.backup.CsvFileSink

@Singleton
class AndroidCsvFileSink @Inject constructor(
    @ApplicationContext private val context: Context
) : CsvFileSink {
    override suspend fun write(carName: String, body: (Writer) -> Unit): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val safeName = carName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "evtracker_${safeName}_$timestamp.csv")
        file.bufferedWriter().use(body)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
