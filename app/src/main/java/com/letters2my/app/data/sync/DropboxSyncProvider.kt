package com.letters2my.app.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

/**
 * Syncs via Dropbox API. Requires a Dropbox access token
 * (obtained via OAuth PKCE flow — can be added from Settings).
 */
class DropboxSyncProvider(
    private val accessToken: String
) : CloudSyncProvider {
    override val name = "Dropbox"

    companion object {
        private const val TAG = "DropboxSync"
        private const val SYNC_PATH = "/letters_to_my_sync/database.db"
    }

    private val client = OkHttpClient()

    override suspend fun pushDatabase(data: ByteArray, timestamp: Long) = withContext(Dispatchers.IO) {
        val body = RequestBody.create("application/octet-stream".toMediaType(), data)

        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", """{"path":"$SYNC_PATH","mode":"overwrite","mute":true}""")
            .header("Content-Type", "application/octet-stream")
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                Log.d(TAG, "Pushed database: ${data.size} bytes")
            } else {
                Log.e(TAG, "Push failed: ${resp.code} ${resp.body?.string()}")
            }
        }
        Unit
    }

    override suspend fun pullDatabase(): CloudSyncResult? = withContext(Dispatchers.IO) {
        // Get metadata first
        val metaReq = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/get_metadata")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .post(
                RequestBody.create(
                    "application/json".toMediaType(),
                    """{"path":"$SYNC_PATH"}"""
                )
            )
            .build()

        val metaResp = client.newCall(metaReq).execute()
        if (!metaResp.isSuccessful) {
            metaResp.close()
            return@withContext null
        }
        val meta = JSONObject(metaResp.body?.string() ?: "{}")
        metaResp.close()
        val serverModified = meta.optString("server_modified", "").let { iso ->
            try { java.time.Instant.parse(iso).toEpochMilli() } catch (_: Exception) { 0L }
        }

        // Download
        val dlReq = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", """{"path":"$SYNC_PATH"}""")
            .get()
            .build()

        client.newCall(dlReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val data = resp.body?.bytes() ?: return@withContext null
            Log.d(TAG, "Pulled database: ${data.size} bytes")
            CloudSyncResult(data, serverModified, name)
        }
    }
}