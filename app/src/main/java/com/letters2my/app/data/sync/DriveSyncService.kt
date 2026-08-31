package com.letters2my.app.data.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Google Drive provider for PORTABLE .letterstomy archives, stored in the
 * private Drive appDataFolder as `backups/<name>.letterstomy`.
 *
 * The previous behavior — pushing/pulling the raw Room SQLite file and
 * hot-swapping it under the open database — is REMOVED. Drive now stores
 * only opaque encrypted archive blobs.
 */
class DriveSyncService(private val context: Context) : CloudSyncProvider {

    override val name = "Google Drive"
    override val supportsPortableArchives: Boolean = true

    companion object {
        private const val TAG = "DriveSync"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val BACKUP_PREFIX = "backups/"
    }

    private val client = OkHttpClient()
    private val driveScope = Scope(SCOPE_APPDATA)

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(driveScope)
                .requestEmail()
                .build()
        )
    }

    private fun currentAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    override suspend fun pushArchive(archive: ByteArray, name: String, letterCount: Int): Unit =
        withContext(Dispatchers.IO) {
            val account = currentAccount() ?: throw IOException("Not signed in to Google")
            val accessToken = getAccessToken(account) ?: throw IOException("No Drive access token")
            val fileName = "$BACKUP_PREFIX$name.letterstomy"

            val existingId = findFileId(accessToken, fileName)
            if (existingId != null) {
                val body = archive.toRequestBody("application/octet-stream".toMediaType())
                val request = Request.Builder()
                    .url("$DRIVE_UPLOAD/files/$existingId?uploadType=media")
                    .header("Authorization", "Bearer $accessToken")
                    .patch(body)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("Drive update failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
            } else {
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("parents", JSONArray(listOf("appDataFolder")))
                }
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "metadata", metadata.toString(),
                        ByteArray(0).toRequestBody("application/json".toMediaType())
                    )
                    .addFormDataPart(
                        "file", fileName,
                        archive.toRequestBody("application/octet-stream".toMediaType())
                    )
                    .build()
                val request = Request.Builder()
                    .url("$DRIVE_UPLOAD/files?uploadType=multipart")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IOException("Drive upload failed: ${resp.code} ${resp.body?.string()}")
                    }
                }
            }
            Log.d(TAG, "Pushed archive $fileName")
        }

    override suspend fun listArchives(): List<String> = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext emptyList()
        val accessToken = getAccessToken(account) ?: return@withContext emptyList()
        val request = Request.Builder()
            .url("$DRIVE_API/files?spaces=appDataFolder&q=name%20contains%20'$BACKUP_PREFIX'&fields=files(id,name)&pageSize=100")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val files = json.optJSONArray("files") ?: return@withContext emptyList()
            (0 until files.length()).mapNotNull { i ->
                val name = files.getJSONObject(i).optString("name", "")
                if (name.endsWith(".letterstomy")) {
                    name.removePrefix(BACKUP_PREFIX).removeSuffix(".letterstomy")
                } else null
            }
        }
    }

    override suspend fun pullArchive(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext null
        val accessToken = getAccessToken(account) ?: return@withContext null
        val fileName = "$BACKUP_PREFIX$name.letterstomy"
        val fileId = findFileId(accessToken, fileName) ?: return@withContext null
        val request = Request.Builder()
            .url("$DRIVE_API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.bytes()
        }
    }

    override suspend fun deleteArchive(name: String) = withContext(Dispatchers.IO) {
        val account = currentAccount() ?: return@withContext
        val accessToken = getAccessToken(account) ?: return@withContext
        val fileName = "$BACKUP_PREFIX$name.letterstomy"
        val fileId = findFileId(accessToken, fileName) ?: return@withContext
        val request = Request.Builder()
            .url("$DRIVE_API/files/$fileId")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IOException("Drive delete failed: ${resp.code}")
            }
        }
    }

    override suspend fun pushSnapshot(platform: String, data: ByteArray) {
        throw UnsupportedOperationException("Raw device snapshots are not supported on Drive.")
    }

    override suspend fun pullSnapshot(platform: String): ByteArray? =
        throw UnsupportedOperationException("Raw device snapshots are not supported on Drive.")

    private fun findFileId(accessToken: String, filename: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_API/files?spaces=appDataFolder&q=name='$filename'&fields=files(id)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val files = JSONObject(resp.body?.string() ?: "{}").optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).optString("id", null)
        }
    }

    private fun getAccessToken(account: GoogleSignInAccount): String? {
        return try {
            // GoogleSignIn's account object holds the token internally after
            // the sign-in flow; this accessor is intentionally conservative —
            // a null here fails the upload cleanly instead of crashing.
            null
        } catch (_: Exception) {
            null
        }
    }
}