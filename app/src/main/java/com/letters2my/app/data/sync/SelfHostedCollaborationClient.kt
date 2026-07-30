package com.letters2my.app.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

/**
 * Self-hosted server client for cross-platform collaboration.
 * Handles invitations, member roles, and cross-device member sync.
 */
class SelfHostedCollaborationClient(
    private val serverURL: String,
    private val apiToken: String
) {
    private val json = "application/json".toMediaType()
    private val client = OkHttpClient()

    // ── Invitations ──────────────────────────

    /**
     * Create an invitation and return the code.
     * The code is shared with the invitee (via message, email, etc).
     */
    suspend fun createInvite(createdBy: String, role: String): String? = withContext(Dispatchers.IO) {
        val body = RequestBody.create(json, """{"created_by":"$createdBy","role":"$role"}""")
        val req = auth(Request.Builder().url("$serverURL/invite").post(body)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val obj = JSONObject(resp.body?.string() ?: "{}")
            obj.optString("code", null)
        }
    }

    /** Look up an invitation by code (doesn't consume it). */
    suspend fun lookupInvite(code: String): JSONObject? = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/invite/$code").get()).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            JSONObject(resp.body?.string() ?: "{}")
        }
    }

    /**
     * Accept an invitation. Returns the assigned role.
     */
    suspend fun acceptInvite(code: String, memberID: String, memberName: String): String? = withContext(Dispatchers.IO) {
        val body = RequestBody.create(json, """{"member_id":"$memberID","member_name":"$memberName"}""")
        val req = auth(Request.Builder().url("$serverURL/invite/$code").post(body)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val obj = JSONObject(resp.body?.string() ?: "{}")
            obj.optString("role", null)
        }
    }

    /** Revoke an invitation. */
    suspend fun revokeInvite(code: String): Boolean = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/invite/$code").delete()).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    // ── Members ──────────────────────────────

    /** List all members (cross-platform). */
    suspend fun listMembers(): List<MemberInfo> = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/members").get()).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = org.json.JSONArray(resp.body?.string() ?: "[]")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MemberInfo(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    role = obj.optString("role", "viewer"),
                    since = obj.optLong("since", 0L)
                )
            }
        }
    }

    /** Update a member's role. */
    suspend fun updateRole(memberID: String, name: String, role: String): Boolean = withContext(Dispatchers.IO) {
        val body = RequestBody.create(json, """{"id":"$memberID","name":"$name","role":"$role"}""")
        val req = auth(Request.Builder().url("$serverURL/members").put(body)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    /** Remove a member. */
    suspend fun removeMember(memberID: String): Boolean = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/members?id=$memberID").delete()).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    // ── Branches ────────────────────────────

    suspend fun listBranches(): List<BranchInfo> = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/branches").get()).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = org.json.JSONArray(resp.body?.string() ?: "[]")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val memberArr = obj.optJSONArray("member_ids")
                val memberIDs = if (memberArr != null)
                    (0 until memberArr.length()).map { memberArr.getString(it) } else emptyList()
                BranchInfo(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    kind = obj.optString("kind", "custom"),
                    isSeeded = obj.optBoolean("is_seeded", false),
                    memberIDs = memberIDs,
                    createdAt = obj.optLong("created_at", 0L)
                )
            }
        }
    }

    suspend fun createBranch(branch: BranchInfo): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", branch.id)
            put("name", branch.name)
            put("kind", branch.kind)
            put("is_seeded", branch.isSeeded)
            put("member_ids", org.json.JSONArray(branch.memberIDs))
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val req = auth(Request.Builder().url("$serverURL/branches").post(body)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun updateBranch(branch: BranchInfo): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", branch.id)
            put("name", branch.name)
            put("kind", branch.kind)
            put("is_seeded", branch.isSeeded)
            put("member_ids", org.json.JSONArray(branch.memberIDs))
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val req = auth(Request.Builder().url("$serverURL/branches/${branch.id}").put(body)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun deleteBranch(id: String): Boolean = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/branches/$id").delete()).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    // ── Folders ──────────────────────────────

    suspend fun listFolders(branchID: String? = null): List<FolderInfo> = withContext(Dispatchers.IO) {
        val url = if (branchID != null) "$serverURL/folders?branch_id=$branchID" else "$serverURL/folders"
        val req = auth(Request.Builder().url(url).get()).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = org.json.JSONArray(resp.body?.string() ?: "[]")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val memberArr = obj.optJSONArray("member_ids")
                val memberIDs = if (memberArr != null)
                    (0 until memberArr.length()).map { memberArr.getString(it) } else emptyList()
                FolderInfo(
                    id = obj.optString("id", ""),
                    branchID = obj.optString("branch_id", ""),
                    parentID = obj.optString("parent_id", "").ifEmpty { null },
                    name = obj.optString("name", ""),
                    memberIDs = memberIDs,
                    createdAt = obj.optLong("created_at", 0L)
                )
            }
        }
    }

    suspend fun createFolder(folder: FolderInfo): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", folder.id)
            put("branch_id", folder.branchID)
            folder.parentID?.let { put("parent_id", it) }
            put("name", folder.name)
            put("member_ids", org.json.JSONArray(folder.memberIDs))
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val req = auth(Request.Builder().url("$serverURL/folders").post(body)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun updateFolder(folder: FolderInfo): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", folder.id)
            put("branch_id", folder.branchID)
            folder.parentID?.let { put("parent_id", it) }
            put("name", folder.name)
            put("member_ids", org.json.JSONArray(folder.memberIDs))
        }
        val body = RequestBody.create("application/json".toMediaType(), json.toString())
        val req = auth(Request.Builder().url("$serverURL/folders/${folder.id}").put(body)).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun deleteFolder(id: String): Boolean = withContext(Dispatchers.IO) {
        val req = auth(Request.Builder().url("$serverURL/folders/$id").delete()).build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun auth(builder: Request.Builder): Request.Builder {
        return builder.header("Authorization", "Bearer $apiToken")
    }
}

data class MemberInfo(
    val id: String,
    val name: String,
    val role: String,
    val since: Long
)

data class BranchInfo(
    val id: String,
    val name: String,
    val kind: String,
    val isSeeded: Boolean,
    val memberIDs: List<String>,
    val createdAt: Long
)

data class FolderInfo(
    val id: String,
    val branchID: String,
    val parentID: String?,
    val name: String,
    val memberIDs: List<String>,
    val createdAt: Long
)