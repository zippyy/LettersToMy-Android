package com.letters2my.app.data.sync

import com.letters2my.app.domain.AndroidToIosArchiveProofTest
import com.letters2my.app.domain.LetterstomyArchive
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.util.UUID

/**
 * LIVE SelfHostedSync API v1 end-to-end test against a real server.
 *
 * Uses the PRODUCTION SelfHostedApiClient (pure JVM: okhttp + org.json,
 * no android.* imports) and the PRODUCTION LetterstomyArchive codec.
 * Not a mock — real HTTP, real disk, real token auth.
 *
 * Requires a running server:
 *   PORT=8080 DATA_DIR=/tmp/ltm-server-test/data \
 *   API_KEYS_FILE=/tmp/ltm-server-test/api_keys.txt ./server
 *   (api_keys.txt: `android-e2e:test-token-123`)
 *
 * If the server is unreachable the suite is SKIPPED (assumeTrue), so CI
 * without a server stays green.
 */
class SelfHostedSyncE2ETest {

    private val baseUrl = System.getProperty("ltm.e2e.baseUrl", "http://localhost:8080")
    private val token = System.getProperty("ltm.e2e.token", "test-token-123")

    private fun client(activeToken: String = token) = SelfHostedApiClient(baseUrl) { activeToken }

    @Before
    fun serverMustBeUp() {
        val reachable = try {
            val c = client()
            runBlocking { c.status() }
            true
        } catch (ex: Exception) {
            System.err.println("E2E server check failed: ${ex::class.java.name}: ${ex.message}")
            false
        }
        assumeTrue("SelfHostedSync server not reachable at $baseUrl — skipping live E2E", reachable)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    // ── Status ─────────────────────────────────────────────

    @Test
    fun `status returns typed api v1 capabilities`() = runBlocking {
        val status = client().status()
        assertEquals("LettersToMy-SelfHostedSync", status.service)
        assertEquals(1, status.apiVersion)
        assertTrue(status.capabilities.contains("backups"))
        assertTrue(status.capabilities.contains("attachments"))
        assertTrue(status.capabilities.contains("collaboration"))
        assertTrue(status.hasBackupCapability)
        assertTrue(status.hasAttachmentCapability)
        assertTrue(status.hasCollaborationCapability)
    }

    // ── Authentication / structured errors ─────────────────

    @Test
    fun `missing token yields structured 401`() = runBlocking {
        val e = try {
            client("").status()
            null
        } catch (ex: SelfHostedApiClient.ApiException) {
            ex
        }
        assertNotNull(e)
        assertEquals(401, e!!.httpStatus)
        assertEquals("unauthorized", e.code)
    }

    @Test
    fun `invalid token yields structured 401`() = runBlocking {
        val e = try {
            client("wrong-token").status()
            null
        } catch (ex: SelfHostedApiClient.ApiException) {
            ex
        }
        assertNotNull(e)
        assertEquals(401, e!!.httpStatus)
        assertEquals("unauthorized", e.code)
    }

    // ── Backup lifecycle (production archive codec) ────────

    @Test
    fun `backup push list pull delete round trip with letter_count and sha256`() = runBlocking {
        val api = client()

        // Build a REAL archive with the production codec (5 letters, 3 attachments).
        val payload = AndroidToIosArchiveProofTest().buildMirrorPayload()
        val archive = LetterstomyArchive.encrypt(payload, "e2e-passphrase")
        val id = "e2e-${UUID.randomUUID()}"

        // Push
        val pushed = api.pushBackup(id, letterCount = 5, archive = archive)
        assertEquals(id, pushed.id)
        assertEquals(5, pushed.letterCount)
        assertEquals(sha256(archive), pushed.sha256)

        // List — metadata + letter_count correct
        val listed = api.listBackups().first { it.id == id }
        assertEquals(5, listed.letterCount)
        assertEquals(archive.size.toLong(), listed.size)

        // Pull — byte identity with what we uploaded
        val pulled = api.pullBackup(id)
        assertArrayEquals(archive, pulled)

        // Decrypt what the server returned with the production codec — full restore sanity
        val restored = LetterstomyArchive.decrypt(pulled, "e2e-passphrase")
        assertEquals(5, restored.letters.size)
        assertEquals(2, restored.children.size)

        // Wrong passphrase must NOT decrypt
        val wrong = try {
            LetterstomyArchive.decrypt(pulled, "wrong-passphrase")
            null
        } catch (_: Exception) {
            "rejected"
        }
        assertEquals("rejected", wrong)

        // Delete — list confirms gone
        api.deleteBackup(id)
        assertTrue(api.listBackups().none { it.id == id })
    }

    // ── Attachments (server-side objects) ──────────────────

    @Test
    fun `attachment upload list download delete round trip`() = runBlocking {
        val api = client()
        val id = "e2e-att-${UUID.randomUUID()}"
        val data = ByteArray(4096) { (it % 251).toByte() }

        api.uploadAttachment(id, data)
        val meta = api.listAttachments().first { it.id == id }
        assertEquals(data.size.toLong(), meta.size)

        val downloaded = api.downloadAttachment(id)
        assertArrayEquals(data, downloaded)

        api.deleteAttachment(id)
        assertTrue(api.listAttachments().none { it.id == id })
    }

    // ── Device snapshots (platform-specific, raw-bytes storage) ──

    @Test
    fun `snapshot push pull round trip and missing platform is null`() = runBlocking {
        val api = client()
        val bytes = "android-snapshot-bytes-12345".toByteArray()

        // Server validates platform to ios|android|web, and stores ONE
        // snapshot per platform (PUT replaces), so "android" is safe to
        // round trip even across runs. "web" is never written by this
        // suite, so it must resolve to null (404 -> null).
        assertNull(api.pullSnapshot("web"))

        api.pushSnapshot("android", bytes)
        assertArrayEquals(bytes, api.pullSnapshot("android"))
    }

    // ── Collaboration: invites, members, branches, folders ──

    @Test
    fun `collaboration endpoints decode typed DTOs`() = runBlocking {
        val api = client()

        // Invite 1 — created, looked up, then REVOKED while unused
        // (server consumes an invite on accept, so revoke must target an
        // unconsumed invite — the client's 404 handling is the typed path).
        val revokedCode = api.createInvite(createdBy = "e2e-owner", role = "viewer")
        assertTrue(revokedCode.isNotBlank())
        val inviteBefore = api.lookupInvite(revokedCode)
        assertEquals("viewer", inviteBefore.role)
        assertEquals("e2e-owner", inviteBefore.createdBy)
        api.revokeInvite(revokedCode)
        val revokedLookup = try {
            api.lookupInvite(revokedCode)
            null
        } catch (ex: SelfHostedApiClient.ApiException) {
            assertEquals(404, ex.httpStatus)
            "gone"
        }
        assertEquals("gone", revokedLookup)

        // Invite 2 — accepted; server consumes it and adds the member.
        val code = api.createInvite(createdBy = "e2e-owner", role = "viewer")
        val memberId = "60000000-0000-0000-0000-0000000000e2"
        val grantedRole = api.acceptInvite(code, memberId, "Aunt Carol")
        assertEquals("viewer", grantedRole)
        val members = api.listMembers()
        val carol = members.first { it.id == memberId }
        assertEquals("Aunt Carol", carol.name)
        assertEquals("viewer", carol.role)

        // Role update
        api.updateMemberRole(memberId, "Aunt Carol", role = "contributor")
        assertEquals("contributor", api.listMembers().first { it.id == memberId }.role)

        // Branch CRUD
        val branchId = "40000000-0000-0000-0000-0000000000e2"
        api.createBranch(
            SelfHostedApiClient.BranchInfo(
                id = branchId, name = "E2E Branch", kind = "custom",
                isSeeded = false, memberIds = listOf(), createdAt = 0L
            )
        )
        val branch = api.listBranches().first { it.id == branchId }
        assertEquals("E2E Branch", branch.name)
        assertEquals("custom", branch.kind)

        // Folder CRUD under that branch
        val folderId = "50000000-0000-0000-0000-0000000000e2"
        api.createFolder(
            SelfHostedApiClient.FolderInfo(
                id = folderId, branchId = branchId, parentId = null,
                name = "E2E Folder", memberIds = listOf(), createdAt = 0L
            )
        )
        assertEquals("E2E Folder", api.listFolders(branchId).first { it.id == folderId }.name)
        api.updateFolder(
            SelfHostedApiClient.FolderInfo(
                id = folderId, branchId = branchId, parentId = null,
                name = "E2E Folder Renamed", memberIds = listOf(), createdAt = 0L
            )
        )
        assertEquals("E2E Folder Renamed", api.listFolders().first { it.id == folderId }.name)

        // Cleanup: folder, branch, member. (The accepted invite was already
        // consumed by the server on accept.)
        api.deleteFolder(folderId)
        api.deleteBranch(branchId)
        api.removeMember(memberId)

        assertTrue(api.listFolders().none { it.id == folderId })
        assertTrue(api.listBranches().none { it.id == branchId })
        assertTrue(api.listMembers().none { it.id == memberId })
    }

    // ── Base URL normalization (unit-level) ────────────────

    @Test
    fun `normalizeBaseUrl canonicalizes`() {
        assertEquals("https://example.com", SelfHostedApiClient.normalizeBaseUrl("example.com"))
        assertEquals("http://host:8080", SelfHostedApiClient.normalizeBaseUrl("http://host:8080/"))
        assertEquals("https://a/b", SelfHostedApiClient.normalizeBaseUrl("https://a/b///"))
        assertEquals("", SelfHostedApiClient.normalizeBaseUrl("  "))
    }
}