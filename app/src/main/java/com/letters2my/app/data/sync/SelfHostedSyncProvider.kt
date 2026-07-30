package com.letters2my.app.data.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType

/**
 * Syncs with a self-hosted LettersToMy sync server
 * (https://github.com/zippyy/LettersToMy-SelfHostedSync).
 *
 * Run: docker compose up -d
 * Then point the app at your server's URL.
 */
class SelfHostedSyncProvider(
    private val serverURL: String,
    private val apiToken: String,
    override val name: String = "Self-Hosted"
) : CloudSyncProvider {

    companion object {
        private const val TAG = "SelfHostedSync"
    }

    private val client = OkHttpClient()

    override suspend fun pushDatabase(data: ByteArray, timestamp: Long) = withContext(Dispatchers.IO) {
        val body = RequestBody.create("application/octet-stream".toMediaType(), data)
        val request = auth(Request.Builder()
            .url("$serverURL/sync/push/android")
            .put(body))
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) Log.d(TAG, "Pushed database: ${data.size} bytes")
            else Log.e(TAG, "Push failed: ${resp.code}")
        }
        Unit
    }

    override suspend fun pullDatabase(): CloudSyncResult? = withContext(Dispatchers.IO) {
        val request = auth(Request.Builder()
            .url("$serverURL/sync/pull/ios")
            .get())
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val data = resp.body?.bytes() ?: return@withContext null
            CloudSyncResult(data, System.currentTimeMillis(), name)
        }
    }

    /**
     * Upload a single attachment to the server.
     */
    suspend fun uploadAttachment(id: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val body = RequestBody.create("application/octet-stream".toMediaType(), data)
        val request = auth(Request.Builder()
            .url("$serverURL/attachment/upload?id=${id}")
            .put(body))
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) Log.d(TAG, "Uploaded attachment: $id")
            else Log.e(TAG, "Attachment upload failed: ${resp.code}")
        }
        Unit
    }

    /**
     * Push a backup archive to the server.
     */
    suspend fun pushBackup(id: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val body = RequestBody.create("application/octet-stream".toMediaType(), data)
        val request = auth(Request.Builder()
            .url("$serverURL/backup/push?id=${id}")
            .put(body))
            .build()

        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) Log.d(TAG, "Backup pushed: $id")
            else Log.e(TAG, "Backup push failed: ${resp.code}")
        }
        Unit
    }

    private fun auth(builder: Request.Builder): Request.Builder {
        return builder.header("Authorization", "Bearer $apiToken")
    }
}