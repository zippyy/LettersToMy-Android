package com.letters2my.app.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Dropbox provider for PORTABLE .letterstomy archives.
 * Raw SQLite snapshots removed — archives stored as
 * `/letters_to_my/backups/<name>.letterstomy`.
 */
class DropboxSyncProvider(
    private val accessToken: String
) : CloudSyncProvider {

    override val name = "Dropbox"
    override val supportsPortableArchives: Boolean = true

    companion object {
        private const val TAG = "DropboxSync"
        private const val BACKUP_DIR = "/letters_to_my/backups"
    }

    private val client = OkHttpClient()

    private fun pathFor(name: String) = "$BACKUP_DIR/$name.letterstomy"

    override suspend fun pushArchive(archive: ByteArray, name: String, letterCount: Int): Unit =
        withContext(Dispatchers.IO) {
            val body = archive.toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .header("Authorization", "Bearer $accessToken")
                .header("Dropbox-API-Arg", """{"path":"${pathFor(name)}","mode":"overwrite","mute":true}""")
                .header("Content-Type", "application/octet-stream")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Dropbox upload failed: ${resp.code} ${resp.body?.string()}")
                }
                Log.d(TAG, "Pushed archive ${pathFor(name)}")
            }
        }

    override suspend fun listArchives(): List<String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("path", BACKUP_DIR).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val entries = json.optJSONArray("entries") ?: return@withContext emptyList()
            (0 until entries.length()).mapNotNull { i ->
                val e = entries.getJSONObject(i)
                val path = e.optString("path_display", "")
                if (path.endsWith(".letterstomy")) {
                    path.substringAfterLast('/').removeSuffix(".letterstomy")
                } else null
            }
        }
    }

    override suspend fun pullArchive(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", """{"path":"${pathFor(name)}"}""")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 409) return@withContext null
            if (!resp.isSuccessful) throw IllegalStateException("Dropbox download failed: ${resp.code}")
            resp.body?.bytes()
        }
    }

    override suspend fun deleteArchive(name: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("path", pathFor(name)).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/delete_v2")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 409) {
                throw IllegalStateException("Dropbox delete failed: ${resp.code}")
            }
        }
    }

    override suspend fun pushSnapshot(platform: String, data: ByteArray) {
        throw UnsupportedOperationException("Raw device snapshots are not supported on Dropbox.")
    }

    override suspend fun pullSnapshot(platform: String): ByteArray? =
        throw UnsupportedOperationException("Raw device snapshots are not supported on Dropbox.")
}