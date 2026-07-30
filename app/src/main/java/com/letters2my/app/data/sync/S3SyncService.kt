package com.letters2my.app.data.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cross-platform sync via any S3-compatible object storage
 * (AWS S3, Backblaze B2, Cloudflare R2, MinIO, etc).
 *
 * Both platforms push timestamped database snapshots to
 * the same bucket. On launch, each downloads the latest
 * snapshot from the OTHER platform if it's newer.
 */
class S3SyncService(
    private val endpoint: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
    private val region: String = "us-east-1"
) {
    companion object {
        private const val TAG = "S3Sync"
    }

    private val client = OkHttpClient()

    /** Push the local database to S3 with a platform prefix. */
    suspend fun pushDatabase(dbFile: java.io.File, platform: String) = withContext(Dispatchers.IO) {
        val key = "sync/${platform}-letters.db"
        val data = dbFile.readBytes()
        val sha256 = sha256Hex(data)

        val request = signedRequest("PUT", key, data, sha256)
        client.newCall(request).execute().use { resp ->
            if (resp.isSuccessful) {
                Log.d(TAG, "Pushed $platform database to S3 ($sha256)")
            } else {
                Log.e(TAG, "Push failed: ${resp.code} ${resp.body?.string()}")
            }
        }
    }

    /** Pull the latest database from another platform. Returns data if newer. */
    suspend fun pullDatabase(platform: String, localLastModified: Long): ByteArray? = withContext(Dispatchers.IO) {
        val key = "sync/${platform}-letters.db"

        // HEAD to check last modified
        val headReq = signedRequest("HEAD", key, ByteArray(0), "")
        val headResp = client.newCall(headReq).execute()
        if (!headResp.isSuccessful) {
            headResp.close()
            return@withContext null
        }
        val remoteModified = headResp.header("Last-Modified")?.let { iso ->
            try { java.time.Instant.parse(iso).toEpochMilli() } catch (_: Exception) { 0L }
        } ?: 0L
        headResp.close()

        if (remoteModified <= localLastModified) {
            Log.d(TAG, "$platform database is not newer — skipping pull")
            return@withContext null
        }

        // GET the data
        val getReq = signedRequest("GET", key, ByteArray(0), "")
        client.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val data = resp.body?.bytes() ?: return@withContext null
            Log.d(TAG, "Pulled $platform database: ${data.size} bytes")
            data
        }
    }

    /** List all platform snapshots in the sync folder. */
    suspend fun listPlatforms(): List<String> = withContext(Dispatchers.IO) {
        val request = signedRequest("GET", "", ByteArray(0), "")
        request.url.newBuilder().addQueryParameter("prefix", "sync/").addQueryParameter("delimiter", "/")
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val xml = resp.body?.string() ?: return@withContext emptyList()
            // Parse CommonPrefixes from S3 XML
            val pattern = Regex("<Prefix>sync/([^<]+)-letters.db</Prefix>")
            pattern.findAll(xml).map { it.groupValues[1] }.toList()
        }
    }

    // MARK: - AWS Signature V4

    private fun signedRequest(method: String, path: String, body: ByteArray, sha256: String): Request {
        val fullPath = if (path.isEmpty()) "" else "/$path"
        val url = "$endpoint/$bucket$fullPath"
        val now = java.time.Instant.now()
        val dateStamp = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
            .withZone(java.time.ZoneOffset.UTC).format(now)
        val amzDate = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC).format(now)

        val contentSha256 = if (sha256.isEmpty()) "UNSIGNED-PAYLOAD" else sha256
        val host = java.net.URL(url).host

        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = "$method\n$fullPath\n\nhost:$host\nx-amz-content-sha256:$contentSha256\nx-amz-date:$amzDate\n\n$signedHeaders\n$contentSha256"

        val scope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray())}"

        val signingKey = hmacSHA256("aws4_request",
            hmacSHA256("s3",
                hmacSHA256(region,
                    hmacSHA256(dateStamp, "AWS4$secretKey".toByteArray()))))

        val signature = hmacSHA256Hex(stringToSign.toByteArray(), signingKey)
        val authHeader = "AWS4-HMAC-SHA256 Credential=$accessKey/$scope,SignedHeaders=$signedHeaders,Signature=$signature"

        val bodyPart = if (body.isNotEmpty()) RequestBody.create("application/octet-stream".toMediaType(), body) else null

        return Request.Builder()
            .url(url)
            .method(method, bodyPart)
            .addHeader("Authorization", authHeader)
            .addHeader("X-Amz-Date", amzDate)
            .addHeader("X-Amz-Content-SHA256", contentSha256)
            .build()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(string: String): String = sha256Hex(string.toByteArray())

    private fun hmacSHA256(data: String, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    private fun hmacSHA256Hex(data: ByteArray, key: ByteArray): String {
        val result = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(key, "HmacSHA256"))
        }.doFinal(data)
        return result.joinToString("") { "%02x".format(it) }
    }
}