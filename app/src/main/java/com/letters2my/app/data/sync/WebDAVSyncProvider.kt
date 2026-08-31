package com.letters2my.app.data.sync

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection

/**
 * WebDAV/Nextcloud provider for PORTABLE .letterstomy archives.
 * Raw SQLite snapshots removed — archives stored as
 * `letters_to_my/backups/<name>.letterstomy` under the base URL.
 */
class WebDAVSyncProvider(
    private val baseURL: String,
    private val username: String? = null,
    private val password: String? = null
) : CloudSyncProvider {

    override val name = "WebDAV"
    override val supportsPortableArchives: Boolean = true

    companion object {
        private const val TAG = "WebDAVSync"
        private const val BACKUP_DIR = "letters_to_my/backups"
    }

    private val client = OkHttpClient()

    private fun fileUrl(name: String): String {
        val base = baseURL.trimEnd('/')
        return "$base/$BACKUP_DIR/$name.letterstomy"
    }

    override suspend fun pushArchive(archive: ByteArray, name: String, letterCount: Int): Unit =
        withContext(Dispatchers.IO) {
            val url = fileUrl(name)
            val body = archive.toRequestBody("application/octet-stream".toMediaType())
            client.newCall(authRequest(Request.Builder().url(url).put(body)).build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("WebDAV upload failed: ${resp.code} ${resp.body?.string()}")
                }
                Log.d(TAG, "Pushed archive $url")
            }
        }

    override suspend fun listArchives(): List<String> = withContext(Dispatchers.IO) {
        val base = baseURL.trimEnd('/')
        val propfind = """
            <?xml version="1.0"?>
            <d:propfind xmlns:d="DAV:">
                <d:prop><d:displayname/></d:prop>
            </d:propfind>
        """.trimIndent()
        val propBody = propfind.toRequestBody("application/xml".toMediaType())
        val req = authRequest(Request.Builder().url("$base/$BACKUP_DIR/").method("PROPFIND", propBody)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val xml = resp.body?.string() ?: return@withContext emptyList()
            // <d:href>.../backups/NAME.letterstomy</d:href>
            val pattern = Regex("<d:href>([^<]+)</d:href>")
            pattern.findAll(xml).mapNotNull { m ->
                val href = m.groupValues[1].trim()
                val name = href.substringAfterLast('/').removeSuffix(".letterstomy")
                name.takeIf { it.isNotEmpty() && href.endsWith(".letterstomy") }
            }.distinct().toList()
        }
    }

    override suspend fun pullArchive(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val getReq = authRequest(Request.Builder().url(fileUrl(name)).get()).build()
        client.newCall(getReq).execute().use { resp ->
            if (resp.code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (!resp.isSuccessful) throw IllegalStateException("WebDAV download failed: ${resp.code}")
            resp.body?.bytes()
        }
    }

    override suspend fun deleteArchive(name: String) = withContext(Dispatchers.IO) {
        val delReq = authRequest(Request.Builder().url(fileUrl(name)).delete()).build()
        client.newCall(delReq).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != HttpURLConnection.HTTP_NOT_FOUND) {
                throw IllegalStateException("WebDAV delete failed: ${resp.code}")
            }
        }
    }

    override suspend fun pushSnapshot(platform: String, data: ByteArray) {
        throw UnsupportedOperationException("Raw device snapshots are not supported on WebDAV.")
    }

    override suspend fun pullSnapshot(platform: String): ByteArray? =
        throw UnsupportedOperationException("Raw device snapshots are not supported on WebDAV.")

    private fun authRequest(builder: Request.Builder): Request.Builder {
        if (username != null && password != null) {
            val creds = Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            builder.header("Authorization", "Basic $creds")
        }
        return builder
    }
}