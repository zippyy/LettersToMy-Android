package com.letters2my.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Typed SelfHostedSync API v1 client, derived from the Go server
 * (main.go) and the current Swift SelfHostedAPI.swift. One canonical
 * base-URL normalizer and one auth/error pipeline.
 *
 * Contract highlights (API v1):
 *  - Auth: `Authorization: Bearer <token>` on every endpoint.
 *  - Errors: `{"error":{"code":"...","message":"..."}}` envelope.
 *  - Backups are opaque encrypted archives; letter_count is a metadata hint.
 *
 * Never reduces a failure to null/false: every call throws [ApiException]
 * with the structured code/message so UI can explain what happened.
 */
class SelfHostedApiClient(
    baseUrl: String,
    private val tokenProvider: () -> String
) {
    /** Canonical base URL normalizer: no trailing slash, https by default. */
    val baseUrl: String = normalizeBaseUrl(baseUrl)

    private val jsonMedia = "application/json".toMediaType()
    private val octetMedia = "application/octet-stream".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    // ── DTOs ──────────────────────────────────────────────

    data class Status(
        val service: String,
        val apiVersion: Int,
        val serverVersion: String,
        val capabilities: List<String>,
        val syncs: List<SyncMeta>,
        val attachments: List<AttachmentMeta>,
        val recoveries: List<BackupMeta>,
        val branches: Int,
        val folders: Int,
        val members: Int,
        val invitations: Int,
        val uptimeSeconds: Long
    ) {
        val hasBackupCapability: Boolean get() = "backups" in capabilities
        val hasAttachmentCapability: Boolean get() = "attachments" in capabilities
        val hasCollaborationCapability: Boolean get() = "collaboration" in capabilities

        companion object {
            fun parse(o: JSONObject): Status = Status(
                service = o.optString("service", ""),
                apiVersion = o.optInt("api_version", 0),
                serverVersion = o.optString("server_version", ""),
                capabilities = o.optJSONArray("capabilities")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList(),
                syncs = o.optJSONArray("syncs")?.let {
                    (0 until it.length()).map { i -> SyncMeta.parse(it.getJSONObject(i)) }
                } ?: emptyList(),
                attachments = o.optJSONArray("attachments")?.let {
                    (0 until it.length()).map { i -> AttachmentMeta.parse(it.getJSONObject(i)) }
                } ?: emptyList(),
                recoveries = o.optJSONArray("recoveries")?.let {
                    (0 until it.length()).map { i -> BackupMeta.parse(it.getJSONObject(i)) }
                } ?: emptyList(),
                branches = o.optInt("branches", 0),
                folders = o.optInt("folders", 0),
                members = o.optInt("members", 0),
                invitations = o.optInt("invitations", 0),
                uptimeSeconds = o.optLong("uptime_seconds", 0L)
            )
        }
    }

    data class BackupMeta(
        val id: String,
        val timestamp: Long,
        val size: Long,
        val letterCount: Int
    ) {
        companion object {
            fun parse(o: JSONObject): BackupMeta = BackupMeta(
                id = o.optString("id", ""),
                timestamp = o.optLong("timestamp", 0L),
                size = o.optLong("size", 0L),
                letterCount = o.optInt("letter_count", 0)
            )
        }
    }

    /** Result of a backup push. */
    data class BackupPushResult(
        val id: String,
        val timestamp: Long,
        val size: Long,
        val letterCount: Int,
        val sha256: String
    )

    data class AttachmentMeta(
        val id: String,
        val contentType: String,
        val size: Long
    ) {
        companion object {
            fun parse(o: JSONObject): AttachmentMeta = AttachmentMeta(
                id = o.optString("id", ""),
                contentType = o.optString("content_type", "application/octet-stream"),
                size = o.optLong("size", 0L)
            )
        }
    }

    data class SyncMeta(
        val platform: String,
        val timestamp: Long,
        val size: Long,
        val kind: String
    ) {
        companion object {
            fun parse(o: JSONObject): SyncMeta = SyncMeta(
                platform = o.optString("platform", ""),
                timestamp = o.optLong("timestamp", 0L),
                size = o.optLong("size", 0L),
                kind = o.optString("kind", "device-snapshot")
            )
        }
    }

    data class MemberInfo(
        val id: String,
        val name: String,
        val role: String,
        val since: Long
    )

    data class InvitationInfo(
        val code: String,
        val createdBy: String,
        val role: String,
        val branchIds: List<String>,
        val folderIds: List<String>,
        val expires: Long
    )

    data class BranchInfo(
        val id: String,
        val name: String,
        val kind: String,
        val isSeeded: Boolean,
        val memberIds: List<String>,
        val createdAt: Long
    )

    data class FolderInfo(
        val id: String,
        val branchId: String,
        val parentId: String?,
        val name: String,
        val memberIds: List<String>,
        val createdAt: Long
    )

    /** Structured error from the canonical envelope. */
    class ApiException(
        val httpStatus: Int,
        val code: String,
        override val message: String
    ) : Exception("[$httpStatus/$code] $message")

    // ── Status ─────────────────────────────────────────────

    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/status"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val body = resp.body?.string() ?: throw ApiException(resp.code, "empty_response", "Empty status response")
        Status.parse(JSONObject(body))
    }

    // ── Backups ────────────────────────────────────────────

    /** PUT /backup/push?id=&letter_count=  (opaque encrypted archive) */
    suspend fun pushBackup(
        id: String,
        letterCount: Int,
        archive: ByteArray
    ): BackupPushResult = withContext(Dispatchers.IO) {
        val url = "$baseUrl/backup/push?id=${urlEncode(id)}&letter_count=$letterCount"
        val req = Request.Builder().url(url)
            .put(archive.toRequestBody(octetMedia))
            .header("Authorization", bearer())
            .build()
        val resp = execute(req)
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val o = JSONObject(resp.body?.string() ?: "{}")
        BackupPushResult(
            id = o.optString("id", id),
            timestamp = o.optLong("timestamp", 0L),
            size = o.optLong("size", 0L),
            letterCount = o.optInt("letter_count", letterCount),
            sha256 = o.optString("sha256", "")
        )
    }

    /** GET /backup/list */
    suspend fun listBackups(): List<BackupMeta> = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/backup/list"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val arr = JSONArray(resp.body?.string() ?: "[]")
        (0 until arr.length()).map { BackupMeta.parse(arr.getJSONObject(it)) }
    }

    /** GET /backup/pull/:id  -> raw encrypted archive bytes */
    suspend fun pullBackup(id: String): ByteArray = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/backup/pull/${urlEncode(id)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        resp.body?.bytes() ?: throw ApiException(resp.code, "empty_response", "Empty backup body")
    }

    /** DELETE /backup/:id  -> 204 */
    suspend fun deleteBackup(id: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/backup/${urlEncode(id)}"))
        if (!resp.isSuccessful && resp.code != 404) throw apiError(resp.code, resp.body?.string())
    }

    // ── Attachments (server-side objects, distinct from archive-internal) ──

    /** PUT /attachment/upload?id= */
    suspend fun uploadAttachment(id: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val url = "$baseUrl/attachment/upload?id=${urlEncode(id)}"
        val resp = execute(
            Request.Builder().url(url).put(data.toRequestBody(octetMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** GET /attachment/list */
    suspend fun listAttachments(): List<AttachmentMeta> = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/attachment/list"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val arr = JSONArray(resp.body?.string() ?: "[]")
        (0 until arr.length()).map { AttachmentMeta.parse(arr.getJSONObject(it)) }
    }

    /** GET /attachment/download/:id */
    suspend fun downloadAttachment(id: String): ByteArray = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/attachment/download/${urlEncode(id)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        resp.body?.bytes() ?: throw ApiException(resp.code, "empty_response", "Empty attachment body")
    }

    /** DELETE /attachment/:id */
    suspend fun deleteAttachment(id: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/attachment/${urlEncode(id)}"))
        if (!resp.isSuccessful && resp.code != 404) throw apiError(resp.code, resp.body?.string())
    }

    // ── Device snapshots (platform-specific, NOT logical sync) ──

    /** PUT /sync/push/{platform} — raw device snapshot storage only. */
    suspend fun pushSnapshot(platform: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val resp = execute(
            Request.Builder().url("$baseUrl/sync/push/$platform").put(data.toRequestBody(octetMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** GET /sync/pull/{platform} — retrieve a platform's raw snapshot. */
    suspend fun pullSnapshot(platform: String): ByteArray? = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/sync/pull/$platform"))
        if (resp.code == 404) null
        else if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        else resp.body?.bytes()
    }

    // ── Invitations ────────────────────────────────────────

    /** POST /invite  {created_by, role} -> {code} */
    suspend fun createInvite(createdBy: String, role: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("created_by", createdBy).put("role", role).toString()
            .toRequestBody(jsonMedia)
        val resp = execute(
            Request.Builder().url("$baseUrl/invite").post(body)
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        JSONObject(resp.body?.string() ?: "{}").optString("code", "")
    }

    /** GET /invite/:code */
    suspend fun lookupInvite(code: String): InvitationInfo = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/invite/${urlEncode(code)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        parseInvitation(JSONObject(resp.body?.string() ?: "{}"))
    }

    /** POST /invite/:code  {member_id, member_name} -> {role} */
    suspend fun acceptInvite(code: String, memberId: String, memberName: String): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("member_id", memberId)
                .put("member_name", memberName)
                .toString().toRequestBody(jsonMedia)
            val resp = execute(
                Request.Builder().url("$baseUrl/invite/${urlEncode(code)}").post(body)
                    .header("Authorization", bearer()).build()
            )
            if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
            JSONObject(resp.body?.string() ?: "{}").optString("role", "viewer")
        }

    /** DELETE /invite/:code */
    suspend fun revokeInvite(code: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/invite/${urlEncode(code)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    // ── Members ────────────────────────────────────────────

    /** GET /members */
    suspend fun listMembers(): List<MemberInfo> = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/members"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val arr = JSONArray(resp.body?.string() ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            MemberInfo(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                role = o.optString("role", "viewer"),
                since = o.optLong("since", 0L)
            )
        }
    }

    /** PUT /members  {id, name, role} */
    suspend fun updateMemberRole(id: String, name: String, role: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("id", id).put("name", name).put("role", role).toString()
            .toRequestBody(jsonMedia)
        val resp = execute(
            Request.Builder().url("$baseUrl/members").put(body)
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** DELETE /members?id=  (last owner -> 409) */
    suspend fun removeMember(id: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/members?id=${urlEncode(id)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    // ── Branches ───────────────────────────────────────────

    /** GET /branches */
    suspend fun listBranches(): List<BranchInfo> = withContext(Dispatchers.IO) {
        val resp = execute(request("GET", "$baseUrl/branches"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val arr = JSONArray(resp.body?.string() ?: "[]")
        (0 until arr.length()).map { parseBranch(arr.getJSONObject(it)) }
    }

    /** POST /branches */
    suspend fun createBranch(branch: BranchInfo) = withContext(Dispatchers.IO) {
        val resp = execute(
            Request.Builder().url("$baseUrl/branches")
                .post(branchJson(branch).toString().toRequestBody(jsonMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** PUT /branches/:id */
    suspend fun updateBranch(branch: BranchInfo) = withContext(Dispatchers.IO) {
        val resp = execute(
            Request.Builder().url("$baseUrl/branches/${urlEncode(branch.id)}")
                .put(branchJson(branch).toString().toRequestBody(jsonMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** DELETE /branches/:id */
    suspend fun deleteBranch(id: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/branches/${urlEncode(id)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    // ── Folders ────────────────────────────────────────────

    /** GET /folders?branch_id= */
    suspend fun listFolders(branchId: String? = null): List<FolderInfo> = withContext(Dispatchers.IO) {
        val url = if (branchId != null) "$baseUrl/folders?branch_id=${urlEncode(branchId)}" else "$baseUrl/folders"
        val resp = execute(request("GET", url))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
        val arr = JSONArray(resp.body?.string() ?: "[]")
        (0 until arr.length()).map { parseFolder(arr.getJSONObject(it)) }
    }

    /** POST /folders */
    suspend fun createFolder(folder: FolderInfo) = withContext(Dispatchers.IO) {
        val resp = execute(
            Request.Builder().url("$baseUrl/folders")
                .post(folderJson(folder).toString().toRequestBody(jsonMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** PUT /folders/:id */
    suspend fun updateFolder(folder: FolderInfo) = withContext(Dispatchers.IO) {
        val resp = execute(
            Request.Builder().url("$baseUrl/folders/${urlEncode(folder.id)}")
                .put(folderJson(folder).toString().toRequestBody(jsonMedia))
                .header("Authorization", bearer()).build()
        )
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    /** DELETE /folders/:id */
    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        val resp = execute(request("DELETE", "$baseUrl/folders/${urlEncode(id)}"))
        if (!resp.isSuccessful) throw apiError(resp.code, resp.body?.string())
    }

    // ── Internals ──────────────────────────────────────────

    private fun bearer(): String = "Bearer ${tokenProvider()}"

    private fun request(method: String, url: String): Request {
        val builder = Request.Builder().url(url)
        builder.header("Authorization", bearer())
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
        }
        return builder.build()
    }

    private fun execute(request: Request): okhttp3.Response =
        client.newCall(request).execute()

    private fun apiError(status: Int, rawBody: String?): ApiException {
        if (rawBody.isNullOrBlank()) {
            return ApiException(status, "http_$status", "Server returned HTTP $status")
        }
        return try {
            val o = JSONObject(rawBody)
            val err = o.optJSONObject("error")
            if (err != null) {
                ApiException(status, err.optString("code", "unknown"), err.optString("message", rawBody))
            } else {
                ApiException(status, "http_$status", rawBody.take(300))
            }
        } catch (_: Exception) {
            ApiException(status, "http_$status", rawBody.take(300))
        }
    }

    private fun parseInvitation(o: JSONObject): InvitationInfo = InvitationInfo(
        code = o.optString("code", ""),
        createdBy = o.optString("created_by", ""),
        role = o.optString("role", "viewer"),
        branchIds = o.optJSONArray("branch_ids")?.let {
            (0 until it.length()).map { i -> it.getString(i) }
        } ?: emptyList(),
        folderIds = o.optJSONArray("folder_ids")?.let {
            (0 until it.length()).map { i -> it.getString(i) }
        } ?: emptyList(),
        expires = o.optLong("expires", 0L)
    )

    private fun parseBranch(o: JSONObject): BranchInfo = BranchInfo(
        id = o.optString("id", ""),
        name = o.optString("name", ""),
        kind = o.optString("kind", "custom"),
        isSeeded = o.optBoolean("is_seeded", false),
        memberIds = o.optJSONArray("member_ids")?.let {
            (0 until it.length()).map { i -> it.getString(i) }
        } ?: emptyList(),
        createdAt = o.optLong("created_at", 0L)
    )

    private fun parseFolder(o: JSONObject): FolderInfo = FolderInfo(
        id = o.optString("id", ""),
        branchId = o.optString("branch_id", ""),
        parentId = o.optString("parent_id", "").ifEmpty { null },
        name = o.optString("name", ""),
        memberIds = o.optJSONArray("member_ids")?.let {
            (0 until it.length()).map { i -> it.getString(i) }
        } ?: emptyList(),
        createdAt = o.optLong("created_at", 0L)
    )

    private fun branchJson(b: BranchInfo): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("name", b.name)
        put("kind", b.kind)
        put("is_seeded", b.isSeeded)
        put("member_ids", JSONArray(b.memberIds))
    }

    private fun folderJson(f: FolderInfo): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("branch_id", f.branchId)
        f.parentId?.let { put("parent_id", it) }
        put("name", f.name)
        put("member_ids", JSONArray(f.memberIds))
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    companion object {
        /**
         * Canonical base URL normalization shared by all clients:
         * strip trailing slashes, reject non-http(s) schemes.
         */
        fun normalizeBaseUrl(raw: String): String {
            var url = raw.trim()
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            while (url.endsWith("/")) url = url.dropLast(1)
            return url
        }
    }
}