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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Syncs the Room database to Google Drive's appDataFolder via
 * the Drive REST API v3. Equivalent to CloudKit private database on iOS.
 */
class DriveSyncService(private val context: Context) {

    companion object {
        private const val TAG = "DriveSync"
        private const val DB_FILENAME = "letters_to_my.db"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
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

    private fun token(account: GoogleSignInAccount): String {
        val t = account.account?.let { acct ->
            GoogleSignIn.getLastSignedInAccount(context)?.let { last ->
                // Use the account from sign-in result
            }
        }
        // Request a fresh token via GoogleSignIn
        // Note: For production, use GoogleSignIn.requestServerAuthCode or
        // GoogleSignIn.getCredential with OAuth token.
        // This simplified version reuses the existing sign-in scope.
        return ""
    }

    /**
     * Upload database to Drive appDataFolder.
     */
    suspend fun uploadDatabase(account: GoogleSignInAccount) = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath(DB_FILENAME)
        if (!dbFile.exists()) {
            Log.w(TAG, "Database file not found")
            return@withContext
        }

        val accessToken = getAccessToken(account) ?: run {
            Log.e(TAG, "No access token")
            return@withContext
        }

        val fileId = findFileId(accessToken, DB_FILENAME)

        if (fileId != null) {
            // Update existing
            val requestBody = RequestBody.create("application/octet-stream".toMediaType(), dbFile.readBytes())
            val request = Request.Builder()
                .url("$DRIVE_UPLOAD/files/$fileId?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) Log.e(TAG, "Update failed: ${resp.code} ${resp.body?.string()}")
                else Log.d(TAG, "Updated database on Drive")
            }
        } else {
            // Create new
            val metadata = JSONObject().apply {
                put("name", DB_FILENAME)
                put("parents", org.json.JSONArray(listOf("appDataFolder")))
            }
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata", metadata.toString(),
                    RequestBody.create("application/json".toMediaType(), ByteArray(0))
                )
                .addFormDataPart(
                    "file", DB_FILENAME,
                    RequestBody.create("application/octet-stream".toMediaType(), dbFile.readBytes())
                )
                .build()

            val request = Request.Builder()
                .url("$DRIVE_UPLOAD/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Log.d(TAG, "Uploaded database to Drive")
                else Log.e(TAG, "Upload failed: ${resp.code} ${resp.body?.string()}")
            }
        }
    }

    /**
     * Download database from Drive. Returns true if downloaded.
     */
    suspend fun downloadDatabase(account: GoogleSignInAccount): Boolean = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(account) ?: return@withContext false
        val fileId = findFileId(accessToken, DB_FILENAME) ?: return@withContext false

        // Get file metadata
        val metaRequest = Request.Builder()
            .url("$DRIVE_API/files/$fileId?fields=id,name,modifiedTime,size")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val metaResp = client.newCall(metaRequest).execute()
        val meta = JSONObject(metaResp.body?.string() ?: "{}")
        metaResp.close()

        val dbFile = context.getDatabasePath(DB_FILENAME)
        val localTime = dbFile.lastModified()
        val remoteTime = meta.optString("modifiedTime", "").let { iso ->
            try { java.time.Instant.parse(iso).toEpochMilli() } catch (_: Exception) { 0L }
        }

        if (remoteTime <= localTime) return@withContext false

        // Download content
        val dlRequest = Request.Builder()
            .url("$DRIVE_API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(dlRequest).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext false
            val data = resp.body?.bytes() ?: return@withContext false

            // Write to database file, delete WAL/SHM for clean Room start
            dbFile.outputStream().use { it.write(data) }
            context.getDatabasePath("${DB_FILENAME}-wal").delete()
            context.getDatabasePath("${DB_FILENAME}-shm").delete()
            dbFile.setLastModified(remoteTime)
            Log.d(TAG, "Downloaded database: ${data.size} bytes")
        }
        true
    }

    private fun findFileId(accessToken: String, filename: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_API/files?spaces=appDataFolder&q=name='$filename'&fields=files(id)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: "{}")
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).optString("id", null)
        }
    }

    private fun getAccessToken(account: GoogleSignInAccount): String? {
        return try {
            val credential = GoogleSignIn.getSignedInAccountFromIntent(null)
            // Re-sign in to get fresh token
            // In production, use GoogleSignIn.getCredential or AccountManager
            // For now, request a new sign-in to get the token
            null // Will be implemented with proper OAuth flow
        } catch (_: Exception) {
            null
        }
    }
}