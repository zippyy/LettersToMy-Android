package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Collaboration role/permission matrix — must match iOS EXACTLY
 * (Sources/LettersToMyCore/Collaboration.swift).
 *
 * 6 canonical roles × 17 permissions, plus scope behavior
 * (archive / branch / folder / recipient).
 */
class CollaborationPolicyTest {

    private val memberA = "member-a"
    private val memberB = "member-b"

    private fun letterPerms(role: String?): Set<String> = RolePermissions.permissionsFor(role)

    // ── Role base permission sets (exact match vs iOS) ─────

    @Test
    fun `owner has ALL 17 permissions`() {
        assertEquals(17, CollaborationPermission.entries.size)
        assertEquals(CollaborationPermission.entries.map { it.raw }.toSet(), letterPerms("owner"))
    }

    @Test
    fun `parentAdmin is ALL minus transferOwnership`() {
        val expected = CollaborationPermission.entries.map { it.raw }.toSet() -
            CollaborationPermission.TRANSFER_OWNERSHIP.raw
        assertEquals(expected, letterPerms("parentAdmin"))
        assertFalse(RolePermissions.allows("parentAdmin", CollaborationPermission.TRANSFER_OWNERSHIP))
    }

    @Test
    fun `organizer permission set matches iOS exactly`() {
        assertEquals(
            setOf(
                "viewContent", "viewSealedContent", "createContent",
                "editOwnContent", "editAnyContent", "deleteOwnContent",
                "deleteAnyContent", "manageFolders", "inviteContributors",
                "releaseLifeEventLetters"
            ),
            letterPerms("organizer")
        )
        assertFalse(RolePermissions.allows("organizer", CollaborationPermission.MANAGE_MEMBERS))
        assertFalse(RolePermissions.allows("organizer", CollaborationPermission.EXPORT_ARCHIVE))
    }

    @Test
    fun `contributor permission set matches iOS exactly`() {
        assertEquals(
            setOf("viewContent", "createContent", "editOwnContent", "deleteOwnContent"),
            letterPerms("contributor")
        )
        assertFalse(RolePermissions.allows("contributor", CollaborationPermission.EDIT_ANY_CONTENT))
        assertFalse(RolePermissions.allows("contributor", CollaborationPermission.VIEW_SEALED_CONTENT))
    }

    @Test
    fun `viewer has only viewContent`() {
        assertEquals(setOf("viewContent"), letterPerms("viewer"))
        assertFalse(RolePermissions.allows("viewer", CollaborationPermission.CREATE_CONTENT))
    }

    @Test
    fun `recipient has viewContent and replyAsRecipient`() {
        assertEquals(
            setOf("viewContent", "replyAsRecipient"),
            letterPerms("recipient")
        )
    }

    @Test
    fun `unknown role degrades to viewer`() {
        assertEquals(setOf("viewContent"), letterPerms("banana"))
    }

    // ── CollaborationPolicy.allows with scope ──────────────

    @Test
    fun `scope must cover target else DENIED even for owner`() {
        // Owner with a branch-scoped scope cannot act on another branch.
        val scoped = CollaborationScope(branchIds = setOf("branch-x"))
        val allows = CollaborationPolicy.allows(
            roleRaw = "owner",
            permission = CollaborationPermission.CREATE_CONTENT,
            scope = scoped,
            branchID = "branch-y"
        )
        assertFalse(allows)
    }

    @Test
    fun `archive-wide scope covers everything`() {
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "owner",
                permission = CollaborationPermission.DELETE_ANY_CONTENT,
                scope = CollaborationScope.archiveWide,
                branchID = "branch-y", folderID = "folder-z"
            )
        )
    }

    @Test
    fun `branch scope covers matching branch`() {
        val scoped = CollaborationScope(branchIds = setOf("branch-x"))
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "organizer",
                permission = CollaborationPermission.EDIT_ANY_CONTENT,
                scope = scoped, branchID = "branch-x"
            )
        )
    }

    @Test
    fun `folder scope covers matching folder`() {
        val scoped = CollaborationScope(folderIds = setOf("folder-f"))
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "organizer",
                permission = CollaborationPermission.MANAGE_FOLDERS,
                scope = scoped, folderID = "folder-f"
            )
        )
    }

    @Test
    fun `recipient scope covers matching recipient`() {
        val scoped = CollaborationScope(recipientIds = setOf("child-1"))
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "viewer",
                permission = CollaborationPermission.VIEW_CONTENT,
                scope = scoped, recipientID = "child-1"
            )
        )
    }

    // ── Sealed-content visibility ──────────────────────────

    @Test
    fun `viewContent on sealed letter requires viewSealedContent`() {
        // contributor has viewContent but NOT viewSealedContent:
        // viewing a SEALED letter must be denied.
        assertFalse(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.VIEW_CONTENT,
                scope = CollaborationScope.archiveWide,
                isSealed = true
            )
        )
        // organizer/viewer-with-sealed CAN view.
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "organizer",
                permission = CollaborationPermission.VIEW_CONTENT,
                scope = CollaborationScope.archiveWide,
                isSealed = true
            )
        )
        // Unsealed content visible to any viewContent role.
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.VIEW_CONTENT,
                scope = CollaborationScope.archiveWide,
                isSealed = false
            )
        )
    }

    // ── Own-content variants ───────────────────────────────

    @Test
    fun `editOwnContent requires matching author`() {
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.EDIT_OWN_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberA, actingMemberID = memberA
            )
        )
        assertFalse(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.EDIT_OWN_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberB, actingMemberID = memberA // not their own
            )
        )
        // organizer has editAnyContent so own-content check also passes.
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "organizer",
                permission = CollaborationPermission.EDIT_ANY_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberB, actingMemberID = memberA
            )
        )
    }

    @Test
    fun `deleteOwnContent requires matching author`() {
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.DELETE_OWN_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberA, actingMemberID = memberA
            )
        )
        assertFalse(
            CollaborationPolicy.allows(
                roleRaw = "contributor",
                permission = CollaborationPermission.DELETE_OWN_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberA, actingMemberID = memberB
            )
        )
    }

    @Test
    fun `owner can delete any content`() {
        assertTrue(
            CollaborationPolicy.allows(
                roleRaw = "owner",
                permission = CollaborationPermission.DELETE_ANY_CONTENT,
                scope = CollaborationScope.archiveWide,
                authorMemberID = memberB, actingMemberID = memberA
            )
        )
    }

    @Test
    fun `viewer cannot create edit delete invite or manage`() {
        val viewer = "viewer"
        listOf(
            CollaborationPermission.CREATE_CONTENT,
            CollaborationPermission.EDIT_OWN_CONTENT,
            CollaborationPermission.DELETE_OWN_CONTENT,
            CollaborationPermission.INVITE_CONTRIBUTORS,
            CollaborationPermission.MANAGE_MEMBERS,
            CollaborationPermission.MANAGE_PERMISSIONS,
            CollaborationPermission.MANAGE_FOLDERS,
            CollaborationPermission.RELEASE_LIFE_EVENT_LETTERS,
            CollaborationPermission.EXPORT_ARCHIVE
        ).forEach { perm ->
            assertFalse("viewer must NOT $perm", RolePermissions.allows(viewer, perm))
        }
    }

    @Test
    fun `contribute cannot invite or manage members`() {
        listOf(
            CollaborationPermission.INVITE_CONTRIBUTORS,
            CollaborationPermission.MANAGE_MEMBERS,
            CollaborationPermission.MANAGE_PERMISSIONS,
            CollaborationPermission.RELEASE_LIFE_EVENT_LETTERS,
            CollaborationPermission.TRANSFER_OWNERSHIP
        ).forEach { perm ->
            assertFalse("contributor must NOT $perm", RolePermissions.allows("contributor", perm))
        }
    }

    // ── Full 6×17 matrix sanity ────────────────────────────

    @Test
    fun `complete permission matrix - every role every permission is deterministic`() {
        val roles = listOf("owner", "parentAdmin", "organizer", "contributor", "viewer", "recipient", null)
        val perms = CollaborationPermission.entries
        // Policy evaluation must be deterministic and never crash across the
        // full matrix. (Policy != RolePermissions.for for own-content/ownership
        // variants — policy adds the author==actor guard — so assert the
        // individual semantic guarantees instead of raw equality.)
        for (role in roles) {
            val base = RolePermissions.permissionsFor(role)
            for (perm in perms) {
                // Own-content variants carry an authorship guard: pass a
                // matched author/actor so policy resolves to the base set.
                val author = if (perm == CollaborationPermission.EDIT_OWN_CONTENT ||
                    perm == CollaborationPermission.DELETE_OWN_CONTENT
                ) "actor-id" else null
                val viaPolicy = CollaborationPolicy.allows(
                    roleRaw = role,
                    permission = perm,
                    scope = CollaborationScope.archiveWide,
                    authorMemberID = author,
                    actingMemberID = author,
                    isSealed = false, isUnlocked = false
                )
                // archive-wide scope + matched authorship (or no ownership
                // constraint): policy must agree with the base permission set.
                assertEquals("policy==perms for role=$role perm=$perm", perm.raw in base, viaPolicy)
            }
        }
    }
}