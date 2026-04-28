package org.spsl.evtracker.data.backup

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.spsl.evtracker.domain.backup.DriveRemoteSource

@Singleton
class GoogleDriveRemoteSource @Inject constructor() : DriveRemoteSource {

    private fun client(accessToken: String): Drive {
        val initializer = HttpRequestInitializer { req ->
            req.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory(), initializer)
            .setApplicationName("EV Tracker")
            .build()
    }

    override suspend fun findBackupFileId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val list = client(accessToken).files().list()
            .setSpaces("appDataFolder")
            .setQ("name='$BACKUP_FILE_NAME' and trashed=false")
            .setFields("files(id)")
            .execute()
        list.files?.firstOrNull()?.id
    }

    override suspend fun createBackup(accessToken: String, jsonBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val metadata = File().apply {
                name = BACKUP_FILE_NAME
                parents = listOf("appDataFolder")
            }
            val content = ByteArrayContent("application/json", jsonBytes)
            client(accessToken).files().create(metadata, content)
                .setFields("id")
                .execute().id
        }

    override suspend fun updateBackup(accessToken: String, fileId: String, jsonBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val content = ByteArrayContent("application/json", jsonBytes)
            client(accessToken).files().update(fileId, null, content).execute()
        }
    }

    override suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            client(accessToken).files().get(fileId).executeMediaAsInputStream().use { it.readBytes() }
        }

    companion object {
        const val BACKUP_FILE_NAME = "evtracker_backup.json"
    }
}
