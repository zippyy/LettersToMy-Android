package com.letters2my.app.data.local

import androidx.room.*

// ──────────────────────────────────────────────
// Letter
// ──────────────────────────────────────────────

@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "child_id") val childId: String?,
    @ColumnInfo(name = "branch_id") val branchId: String?,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sealed_at") val sealedAt: Long?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_draft") val isDraft: Boolean,
    @ColumnInfo(name = "unlock_rule") val unlockRule: String,
    @ColumnInfo(name = "unlock_date") val unlockDate: Long?,
    @ColumnInfo(name = "unlock_age_years") val unlockAgeYears: Int?,
    @ColumnInfo(name = "life_event_name") val lifeEventName: String,
    @ColumnInfo(name = "manually_released_at") val manuallyReleasedAt: Long?
)

// ──────────────────────────────────────────────
// Attachment
// ──────────────────────────────────────────────

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = LetterEntity::class,
        parentColumns = ["id"],
        childColumns = ["letter_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "letter_id") val letterId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "kind") val kind: String, // photo, video, audio, file
    @ColumnInfo(name = "data") val data: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ──────────────────────────────────────────────
// Child Profile
// ──────────────────────────────────────────────

@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "birth_date") val birthDate: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

// ──────────────────────────────────────────────
// Family Branch (family side)
// ──────────────────────────────────────────────

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "kind") val kind: String, // parents, maternal, paternal, chosenFamily, custom
    @ColumnInfo(name = "is_seeded") val isSeeded: Boolean,
    @ColumnInfo(name = "parent_branch_id") val parentBranchId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ──────────────────────────────────────────────
// Archive Folder
// ──────────────────────────────────────────────

@Entity(
    tableName = "folders",
    foreignKeys = [ForeignKey(
        entity = BranchEntity::class,
        parentColumns = ["id"],
        childColumns = ["branch_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "branch_id") val branchId: String,
    @ColumnInfo(name = "parent_folder_id") val parentFolderId: String?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ──────────────────────────────────────────────
// Collaboration Invitation
// ──────────────────────────────────────────────

@Entity(tableName = "invitations")
data class InvitationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "invitee_display_name") val inviteeDisplayName: String,
    @ColumnInfo(name = "invitee_address") val inviteeAddress: String,
    @ColumnInfo(name = "relationship") val relationship: String,
    @ColumnInfo(name = "role") val role: String, // parentAdmin, organizer, contributor, viewer, recipient
    @ColumnInfo(name = "scope_archive_wide") val scopeArchiveWide: Boolean,
    @ColumnInfo(name = "scope_branch_ids") val scopeBranchIds: String, // comma-separated
    @ColumnInfo(name = "scope_folder_ids") val scopeFolderIds: String,
    @ColumnInfo(name = "scope_recipient_ids") val scopeRecipientIds: String,
    @ColumnInfo(name = "intended_recipient_id") val intendedRecipientId: String?,
    @ColumnInfo(name = "can_invite_others") val canInviteOthers: Boolean,
    @ColumnInfo(name = "status") val status: String, // pending, sent, accepted, declined, revoked, failed
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// ──────────────────────────────────────────────
// Backup Record
// ──────────────────────────────────────────────

@Entity(tableName = "backup_records")
data class BackupRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "destination") val destination: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "letter_count") val letterCount: Int,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?
)