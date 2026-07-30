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