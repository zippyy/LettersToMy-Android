package com.letters2my.app.domain

/**
 * Canonical collaboration roles and permissions, ported from iOS
 * CollaborationRole / CollaborationPermission
 * (Sources/LettersToMyCore/Collaboration.swift). Raw values match iOS and
 * the SelfHostedSync server — never invent new role raw values.
 */
enum class CollaborationRole(val raw: String, val title: String) {
    OWNER("owner", "Owner"),
    PARENT_ADMIN("parentAdmin", "Parent / Admin"),
    ORGANIZER("organizer", "Family Organizer"),
    CONTRIBUTOR("contributor", "Contributor"),
    VIEWER("viewer", "Viewer"),
    RECIPIENT("recipient", "Recipient");

    companion object {
        fun from(raw: String?): CollaborationRole =
            entries.firstOrNull { it.raw == raw } ?: VIEWER
    }
}

enum class CollaborationPermission(val raw: String) {
    VIEW_CONTENT("viewContent"),
    VIEW_SEALED_CONTENT("viewSealedContent"),
    CREATE_CONTENT("createContent"),
    EDIT_OWN_CONTENT("editOwnContent"),
    EDIT_ANY_CONTENT("editAnyContent"),
    DELETE_OWN_CONTENT("deleteOwnContent"),
    DELETE_ANY_CONTENT("deleteAnyContent"),
    MANAGE_FOLDERS("manageFolders"),
    INVITE_CONTRIBUTORS("inviteContributors"),
    MANAGE_MEMBERS("manageMembers"),
    MANAGE_PERMISSIONS("managePermissions"),
    INVITE_RECIPIENTS("inviteRecipients"),
    MANAGE_RECIPIENTS("manageRecipients"),
    RELEASE_LIFE_EVENT_LETTERS("releaseLifeEventLetters"),
    EXPORT_ARCHIVE("exportArchive"),
    REPLY_AS_RECIPIENT("replyAsRecipient"),
    TRANSFER_OWNERSHIP("transferOwnership")
}

/**
 * Default permission set per role, matching iOS
 * `CollaborationRole.defaultPermissions`.
 */
object RolePermissions {

    /** All permission raw values. */
    val ALL: Set<String> = CollaborationPermission.entries.map { it.raw }.toSet()

    fun permissionsFor(roleRaw: String?): Set<String> {
        return when (CollaborationRole.from(roleRaw)) {
            CollaborationRole.OWNER -> ALL
            CollaborationRole.PARENT_ADMIN ->
                ALL - CollaborationPermission.TRANSFER_OWNERSHIP.raw
            CollaborationRole.ORGANIZER -> setOf(
                CollaborationPermission.VIEW_CONTENT.raw,
                CollaborationPermission.VIEW_SEALED_CONTENT.raw,
                CollaborationPermission.CREATE_CONTENT.raw,
                CollaborationPermission.EDIT_OWN_CONTENT.raw,
                CollaborationPermission.EDIT_ANY_CONTENT.raw,
                CollaborationPermission.DELETE_OWN_CONTENT.raw,
                CollaborationPermission.DELETE_ANY_CONTENT.raw,
                CollaborationPermission.MANAGE_FOLDERS.raw,
                CollaborationPermission.INVITE_CONTRIBUTORS.raw,
                CollaborationPermission.RELEASE_LIFE_EVENT_LETTERS.raw
            )
            CollaborationRole.CONTRIBUTOR -> setOf(
                CollaborationPermission.VIEW_CONTENT.raw,
                CollaborationPermission.CREATE_CONTENT.raw,
                CollaborationPermission.EDIT_OWN_CONTENT.raw,
                CollaborationPermission.DELETE_OWN_CONTENT.raw
            )
            CollaborationRole.VIEWER -> setOf(
                CollaborationPermission.VIEW_CONTENT.raw
            )
            CollaborationRole.RECIPIENT -> setOf(
                CollaborationPermission.VIEW_CONTENT.raw,
                CollaborationPermission.REPLY_AS_RECIPIENT.raw
            )
        }
    }

    fun allows(roleRaw: String?, permission: CollaborationPermission): Boolean =
        permission.raw in permissionsFor(roleRaw)
}

/**
 * Collaboration scope: archive-wide, branch set, folder set, recipient set.
 * Mirrors iOS `CollaborationScope`.
 */
data class CollaborationScope(
    val archiveWide: Boolean = false,
    val branchIds: Set<String> = emptySet(),
    val folderIds: Set<String> = emptySet(),
    val recipientIds: Set<String> = emptySet()
) {
    /** Does this scope cover a letter's context? (best-effort, matches iOS semantics) */
    fun covers(
        branchID: String?,
        folderID: String?,
        recipientID: String?
    ): Boolean {
        if (archiveWide) return true
        if (branchID != null && branchID in branchIds) return true
        if (folderID != null && folderID in folderIds) return true
        if (recipientID != null && recipientID in recipientIds) return true
        return false
    }

    companion object {
        val archiveWide = CollaborationScope(archiveWide = true)
    }
}

/**
 * Permission policy evaluation for a single letter operation.
 * Mirrors iOS `CollaborationPolicy.allows(role:permission:scope:context:)`.
 */
object CollaborationPolicy {

    /**
     * @param roleRaw          the acting member's role raw value
     * @param permission       the operation to check
     * @param scope            the acting member's scope
     * @param branchID         letter's branch
     * @param folderID         letter's folder
     * @param recipientID      letter's recipient (child) id
     * @param authorMemberID   letter's author (for editOwn/deleteOwn checks)
     * @param actingMemberID   the acting member's id
     * @param isSealed         letter is sealed (not draft and not unlocked)
     * @param isUnlocked       letter is unlocked
     */
    fun allows(
        roleRaw: String?,
        permission: CollaborationPermission,
        scope: CollaborationScope = CollaborationScope.archiveWide,
        branchID: String? = null,
        folderID: String? = null,
        recipientID: String? = null,
        authorMemberID: String? = null,
        actingMemberID: String? = null,
        isSealed: Boolean = false,
        isUnlocked: Boolean = false
    ): Boolean {
        val role = CollaborationRole.from(roleRaw)

        // Scope must cover the target before any permission applies
        // (archive-wide scope covers everything).
        if (!scope.covers(branchID, folderID, recipientID)) return false

        // Sealed content visibility is restricted to roles that can see it.
        if (isSealed && permission == CollaborationPermission.VIEW_CONTENT) {
            return RolePermissions.allows(roleRaw, CollaborationPermission.VIEW_SEALED_CONTENT)
        }

        // Own-content variants require the letter to be authored by the actor.
        when (permission) {
            CollaborationPermission.EDIT_OWN_CONTENT,
            CollaborationPermission.DELETE_OWN_CONTENT -> {
                if (authorMemberID.isNullOrEmpty() || authorMemberID != actingMemberID) return false
                return RolePermissions.allows(roleRaw, permission)
            }
            else -> return RolePermissions.allows(roleRaw, permission)
        }
    }
}