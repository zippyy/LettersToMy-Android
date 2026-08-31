package com.letters2my.app.domain

import java.util.UUID

/**
 * Builds `.letterstomy` BackupPayloads from the local store and applies
 * restored payloads back into it. Semantics mirror iOS BackupService +
 * BackupSettingsView.confirmRestore:
 *
 *  - letter_count = number of letters in the payload (manifest.letterCount)
 *  - restore skips existing IDs (duplicate prevention), never tombstones
 *  - attachments are re-imported by their original identifiers
 */
interface BackupDataSource {
    suspend fun allChildren(): List<ChildPayload>
    suspend fun allLetters(): List<LetterPayload>
    suspend fun allAttachments(): List<AttachmentPayload>
    suspend fun allBranches(): List<BranchPayload>
    suspend fun allFolders(): List<FolderPayload>
    suspend fun allMembers(): List<MemberPayload>
    suspend fun allInvitations(): List<InvitationPayload>
}

class BackupService(
    private val dataSource: BackupDataSource,
    private val appVersion: String = "0.1.0"
) {

    data class BackupResult(
        val archiveBytes: ByteArray,
        val payload: BackupPayload,
        val letterCount: Int,
        val attachmentCount: Int,
        val sizeBytes: Long
    )

    /**
     * Build + encrypt a full portable archive from current local state.
     * The archive contains ALL archive records — filters never apply here.
     */
    suspend fun createArchive(passphrase: String): BackupResult {
        val children = dataSource.allChildren()
        val letters = dataSource.allLetters()
        val attachments = dataSource.allAttachments()
        val branches = dataSource.allBranches()
        val folders = dataSource.allFolders()
        val members = dataSource.allMembers()
        val invitations = dataSource.allInvitations()

        val archiveId = UUID.randomUUID().toString()
        val nowMs = System.currentTimeMillis()

        val payload = BackupPayload(
            manifest = BackupManifest(
                formatVersion = LetterstomyArchive.FORMAT_VERSION,
                archiveID = archiveId,
                createdAtEpochMs = nowMs,
                appVersion = appVersion,
                letterCount = letters.size,
                attachmentCount = attachments.size,
                recipientCount = children.size,
                encryptionAlgorithm = "AES-256-GCM"
            ),
            children = children,
            letters = letters,
            attachments = attachments,
            branches = branches,
            folders = folders,
            members = members,
            invitations = invitations
        )

        val bytes = LetterstomyArchive.encrypt(payload, passphrase)
        return BackupResult(
            archiveBytes = bytes,
            payload = payload,
            letterCount = letters.size,
            attachmentCount = attachments.size,
            sizeBytes = bytes.size.toLong()
        )
    }

    /** Decrypt + parse a `.letterstomy` archive (for preview or restore). */
    fun decryptArchive(bytes: ByteArray, passphrase: String): BackupPayload =
        LetterstomyArchive.decrypt(bytes, passphrase)

    /**
     * Restore a payload into the data source. Mirrors iOS confirmRestore:
     * duplicates (by ID) are skipped, everything else is imported preserving
     * original identifiers. Returns (imported, skipped).
     */
    suspend fun applyRestore(payload: BackupPayload, writer: BackupRestoreWriter): RestoreSummary {
        var imported = 0
        var skipped = 0

        val existingChildren = writer.existingChildIds()
        for (child in payload.children) {
            if (child.id in existingChildren) { skipped++; continue }
            writer.upsertChild(child)
            imported++
        }

        val existingLetters = writer.existingLetterIds()
        for (letter in payload.letters) {
            if (letter.id in existingLetters) { skipped++; continue }
            writer.upsertLetter(letter)
            imported++
        }

        val existingAttachments = writer.existingAttachmentIds()
        val letterIds = writer.existingLetterIds()
        for (attachment in payload.attachments) {
            if (attachment.id in existingAttachments) { skipped++; continue }
            // Only import attachments whose letter exists (or was just imported)
            // — mirrors iOS tolerant letterByID lookup.
            if (attachment.letterID !in letterIds && attachment.letterID !in payload.letters.map { it.id }) {
                skipped++
                continue
            }
            writer.upsertAttachment(attachment)
            imported++
        }

        val existingBranches = writer.existingBranchIds()
        for (branch in payload.branches) {
            if (branch.id in existingBranches) { skipped++; continue }
            writer.upsertBranch(branch)
            imported++
        }

        val existingFolders = writer.existingFolderIds()
        for (folder in payload.folders) {
            if (folder.id in existingFolders) { skipped++; continue }
            writer.upsertFolder(folder)
            imported++
        }

        val existingMembers = writer.existingMemberIds()
        for (member in payload.members) {
            if (member.id in existingMembers) { skipped++; continue }
            writer.upsertMember(member)
            imported++
        }

        val existingInvitations = writer.existingInvitationIds()
        for (invitation in payload.invitations) {
            if (invitation.id in existingInvitations) { skipped++; continue }
            writer.upsertInvitation(invitation)
            imported++
        }

        return RestoreSummary(imported = imported, skipped = skipped)
    }
}

data class RestoreSummary(val imported: Int, val skipped: Int)

/** Abstraction over Room DAOs for applyRestore (keeps BackupService pure). */
interface BackupRestoreWriter {
    suspend fun existingChildIds(): Set<String>
    suspend fun existingLetterIds(): Set<String>
    suspend fun existingAttachmentIds(): Set<String>
    suspend fun existingBranchIds(): Set<String>
    suspend fun existingFolderIds(): Set<String>
    suspend fun existingMemberIds(): Set<String>
    suspend fun existingInvitationIds(): Set<String>

    suspend fun upsertChild(child: ChildPayload)
    suspend fun upsertLetter(letter: LetterPayload)
    suspend fun upsertAttachment(attachment: AttachmentPayload)
    suspend fun upsertBranch(branch: BranchPayload)
    suspend fun upsertFolder(folder: FolderPayload)
    suspend fun upsertMember(member: MemberPayload)
    suspend fun upsertInvitation(invitation: InvitationPayload)
}