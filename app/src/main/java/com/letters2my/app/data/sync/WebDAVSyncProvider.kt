package com.letters2my.app.data.sync

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.net.HttpURLConnection

/**
 * Syncs via WebDAV or Nextcloud (Nextcloud IS WebDAV at /remote.php/dav/files/).
 */
class WebDAVSyncProvider(
    private val baseURL: String,
    private val username: String? = null,
    private val password: String? = null,
    override val name: String = "WebDAV"
) : CloudSyncProvider {

    companion object {
        private const val TAG = "WebDAVSync"
        private const val SYNC_FILE = "letters_to_my_sync.db"
    }

    private val client = OkHttpClient()

    override suspend fun pushDatabase(data: ByteArray, timestamp: Long) = withContext(Dispatchers.IO) {
        val url = "$baseURL/$SYNC_FILE"
        val body = RequestBody.create("application/octet-stream".toMediaType(), data)

        client.newCall(authRequest(Request.Builder().url(url).put(body)).build()).execute().use { resp ->
            if (resp.isSuccessful) Log.d(TAG, "Pushed to $name: ${data.size} bytes")
            else Log.e(TAG, "Push failed: ${resp.code}")
        }
    }

    override suspend fun pullDatabase(): CloudSyncResult? = withContext(Dispatchers.IO) {
        val url = "$baseURL/$SYNC_FILE"

        // PROPfind to get last modified
        val propfind = """
            <?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:">
                <d:prop><d:getlastmodified/></d:prop>
            </d:propfind>
        """.trimIndent()
        val propBody = RequestBody.create("application/xml".toMediaType(), propfind)

        val propReq = authRequest(Request.Builder().url(url).method("PROPFIND", propBody)).build()
        val propResp = client.newCall(propReq).execute()
        val remoteTime = if (propResp.isSuccessful) {
            parseLastModified(propResp.body?.string() ?: "")
        } else 0L
        propResp.close()

        // GET
        val getReq = authRequest(Request.Builder().url(url).get()).build()
        client.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful || resp.code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            val data = resp.body?.bytes() ?: return@withContext null
            Log.d(TAG, "Pulled from $name: ${data.size} bytes")
            CloudSyncResult(data, remoteTime, name)
        }
    }

    private fun authRequest(builder: Request.Builder): Request.Builder {
        // Using 'auth' header to avoid naming conflicts with 'Authorization'
        if (username != null && password != null) {
            val creds = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            builder.header("Authorization", "Basic $creds")
        }
        return builder
    }

    private fun parseLastModified(xml: String): Long {
        val pattern = Regex("<d:getlastmodified>([^<]+)</d:getlastmodified>")
        val match = pattern.find(xml) ?: return 0L
        return try {
            val iso = match.groupValues[1]
            java.time.ZonedDateTime.parse(iso, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }
    }
}