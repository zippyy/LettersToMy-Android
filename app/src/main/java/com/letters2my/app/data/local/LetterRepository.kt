package com.letters2my.app.data.local

import com.letters2my.app.domain.AttachmentPayload
import com.letters2my.app.domain.BackupDataSource
import com.letters2my.app.domain.BackupRestoreWriter
import com.letters2my.app.domain.BranchPayload
import com.letters2my.app.domain.ChildPayload
import com.letters2my.app.domain.FolderPayload
import com.letters2my.app.domain.InvitationPayload
import com.letters2my.app.domain.LetterPayload
import com.letters2my.app.domain.MemberPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Room-backed implementations of the domain data-source contracts.
 * All archive reads/writes go through these so the domain layer stays
 * pure and testable.
 */
class LetterRepository(private val db: LettersDatabase) : BackupDataSource, BackupRestoreWriter {

    // ── Reads (BackupDataSource) ────────────────────────────

    override suspend fun allChildren(): List<ChildPayload> = withContext(Dispatchers.IO) {
        db.childDao().getAllOnce().map {
            ChildPayload(id = it.id, name = it.name, birthDateEpochMs = it.birthDate)
        }
    }

    override suspend fun allLetters(): List<LetterPayload> = withContext(Dispatchers.IO) {
        db.letterDao().getAllOnce().map { it.toPayload() }
    }

    override suspend fun allAttachments(): List<AttachmentPayload> = withContext(Dispatchers.IO) {
        db.attachmentDao().getAllOnce().map {
            AttachmentPayload(
                id = it.id,
                letterID = it.letterId,
                fileName = it.fileName,
                contentTypeIdentifier = it.contentType,
                kindRawValue = it.kind,
                createdAtEpochMs = it.createdAt,
                data = it.data
            )
        }
    }

    override suspend fun allBranches(): List<BranchPayload> = withContext(Dispatchers.IO) {
        db.branchDao().getAllOnce().map {
            BranchPayload(id = it.id, name = it.name, kindRawValue = it.kind, parentBranchID = it.parentBranchId)
        }
    }

    override suspend fun allFolders(): List<FolderPayload> = withContext(Dispatchers.IO) {
        db.folderDao().getAllOnce().map {
            FolderPayload(id = it.id, branchID = it.branchId, parentFolderID = it.parentFolderId, name = it.name)
        }
    }

    override suspend fun allMembers(): List<MemberPayload> = withContext(Dispatchers.IO) {
        db.memberDao().getAllOnce().map {
            MemberPayload(
                id = it.id,
                displayName = it.displayName,
                relationship = it.relationship,
                roleRawValue = it.role,
                statusRawValue = it.status,
                canInviteOthers = it.canInviteOthers
            )
        }
    }

    override suspend fun allInvitations(): List<InvitationPayload> = withContext(Dispatchers.IO) {
        db.invitationDao().getAllOnce().map {
            InvitationPayload(
                id = it.id,
                inviteeDisplayName = it.inviteeDisplayName,
                inviteeAddress = it.inviteeAddress,
                relationship = it.relationship,
                roleRawValue = it.role,
                statusRawValue = it.status
            )
        }
    }

    // ── Restore writer (BackupRestoreWriter) ────────────────

    override suspend fun existingChildIds(): Set<String> =
        db.childDao().getAllOnce().map { it.id }.toSet()

    override suspend fun existingLetterIds(): Set<String> =
        getAllLetterEntities().map { it.id }.toSet()

    override suspend fun existingAttachmentIds(): Set<String> =
        getAllAttachmentEntities().map { it.id }.toSet()

    override suspend fun existingBranchIds(): Set<String> =
        db.branchDao().getAllOnce().map { it.id }.toSet()

    override suspend fun existingFolderIds(): Set<String> =
        db.folderDao().getAllOnce().map { it.id }.toSet()

    override suspend fun existingMemberIds(): Set<String> =
        db.memberDao().getAllOnce().map { it.id }.toSet()

    override suspend fun existingInvitationIds(): Set<String> =
        db.invitationDao().getAllOnce().map { it.id }.toSet()

    override suspend fun upsertChild(child: ChildPayload) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.childDao().insert(
            ChildEntity(
                id = child.id,
                name = child.name,
                birthDate = child.birthDateEpochMs,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun upsertLetter(letter: LetterPayload) = withContext(Dispatchers.IO) {
        db.letterDao().insert(letter.toEntity())
    }

    override suspend fun upsertAttachment(attachment: AttachmentPayload) = withContext(Dispatchers.IO) {
        db.attachmentDao().insert(attachment.toEntity())
    }

    override suspend fun upsertBranch(branch: BranchPayload) = withContext(Dispatchers.IO) {
        db.branchDao().insert(
            BranchEntity(
                id = branch.id,
                name = branch.name,
                kind = branch.kindRawValue,
                isSeeded = branch.kindRawValue != "custom",
                parentBranchId = branch.parentBranchID,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsertFolder(folder: FolderPayload) = withContext(Dispatchers.IO) {
        db.folderDao().insert(
            FolderEntity(
                id = folder.id,
                branchId = folder.branchID,
                parentFolderId = folder.parentFolderID,
                name = folder.name,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsertMember(member: MemberPayload) = withContext(Dispatchers.IO) {
        db.memberDao().insert(
            MemberEntity(
                id = member.id,
                displayName = member.displayName,
                relationship = member.relationship,
                role = member.roleRawValue,
                status = member.statusRawValue,
                canInviteOthers = member.canInviteOthers,
                scopeArchiveWide = true,
                scopeBranchIds = "",
                scopeFolderIds = "",
                scopeRecipientIds = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun upsertInvitation(invitation: InvitationPayload) = withContext(Dispatchers.IO) {
        db.invitationDao().insert(
            InvitationEntity(
                id = invitation.id,
                inviteeDisplayName = invitation.inviteeDisplayName,
                inviteeAddress = invitation.inviteeAddress,
                relationship = invitation.relationship,
                role = invitation.roleRawValue,
                scopeArchiveWide = false,
                scopeBranchIds = "",
                scopeFolderIds = "",
                scopeRecipientIds = "",
                intendedRecipientId = null,
                canInviteOthers = false,
                status = invitation.statusRawValue,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // ── Letter mutations (permission-aware deletion) ────────

    fun observeLetters(): Flow<List<LetterEntity>> = db.letterDao().getAll()

    suspend fun letterById(id: String): LetterEntity? = db.letterDao().getById(id)

    /** Delete a letter AND cascade its attachments (matches iOS cascade). */
    suspend fun deleteLetterWithCascade(letter: LetterEntity): Result<Unit> {
        return runCatching {
            withContext(Dispatchers.IO) {
                db.attachmentDao().deleteByLetter(letter.id)
                db.letterDao().delete(letter)
            }
        }
    }

    /**
     * Permission-aware delete. Mirrors iOS: deletion requires
     * deleteOwnContent/deleteAnyContent and the acting member's scope must
     * cover the letter. Returns structured failure instead of silent false.
     */
    suspend fun deleteLetterWithPermission(
        letter: LetterEntity,
        hasPermission: Boolean
    ): Result<Unit> {
        if (!hasPermission) {
            return Result.failure(
                IllegalStateException("You don't have permission to delete this letter.")
            )
        }
        return deleteLetterWithCascade(letter)
    }

    suspend fun saveLetter(entity: LetterEntity): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { db.letterDao().insert(entity) }
    }

    // ── Helpers ─────────────────────────────────────────────

    private suspend fun getAllLetterEntities(): List<LetterEntity> =
        withContext(Dispatchers.IO) { db.letterDao().getAllOnce() }

    private suspend fun getAllAttachmentEntities(): List<AttachmentEntity> =
        withContext(Dispatchers.IO) { db.attachmentDao().getAllOnce() }
}

// ── Entity <-> Payload mappers ─────────────────────────────

fun LetterPayload.toEntity(): LetterEntity = LetterEntity(
    id = id,
    childId = childID,
    branchId = branchID,
    folderId = folderID,
    authorMemberId = authorMemberID,
    title = title,
    body = body,
    authorName = authorName,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
    sealedAt = sealedAtEpochMs,
    isFavorite = isFavorite,
    isDraft = sealedAtEpochMs == null,
    unlockRuleRawValue = unlockRuleRawValue,
    unlockDate = unlockDateEpochMs,
    unlockAgeYears = unlockAgeYearsValue,
    lifeEventName = lifeEventName,
    manuallyReleasedAt = manuallyReleasedAtEpochMs
)

fun LetterEntity.toPayload(): LetterPayload = LetterPayload(
    id = id,
    childID = childId,
    branchID = branchId,
    folderID = folderId,
    authorMemberID = authorMemberId,
    title = title,
    body = body,
    authorName = authorName,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
    sealedAtEpochMs = sealedAt,
    isFavorite = isFavorite,
    unlockRuleRawValue = unlockRuleRawValue,
    unlockDateEpochMs = unlockDate,
    unlockAgeYearsValue = unlockAgeYears,
    lifeEventName = lifeEventName,
    manuallyReleasedAtEpochMs = manuallyReleasedAt
)

fun AttachmentPayload.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    letterId = letterID,
    fileName = fileName,
    contentType = contentTypeIdentifier,
    kind = kindRawValue,
    data = data,
    createdAt = createdAtEpochMs
)