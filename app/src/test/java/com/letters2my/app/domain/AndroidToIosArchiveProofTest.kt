package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android → iOS reverse archive proof (production codec only).
 *
 * Builds a payload mirroring the iOS fixture's structure (2 children,
 * 5 letters across every lifecycle state and unlock kind, 3 attachments
 * with distinctive bytes, branches, folder, member, invitation), encrypts
 * it with the PRODUCTION LetterstomyArchive (AES-256-GCM, SHA-256 key,
 * nonce||ciphertext||tag), and writes the archive to a well-known path
 * consumed by the Swift harness:
 *
 *   app/build/proof/android-archive.letterstomy
 *
 * The Swift side (separate throwaway package, path-dependency on the real
 * LettersToMyCore) decrypts with BackupService.decryptPayload() and
 * verifies every field. The values hardcoded here MUST match the hardcoded
 * expectations in that harness (ltm-android-proof/Sources/LTMAndroidProof).
 */
class AndroidToIosArchiveProofTest {

    companion object {
        const val PASSPHRASE = "Fixture-Passphrase-42"
        const val ARCHIVE_ID = "80000000-0000-0000-0000-0000000000aa"
        const val CHILD_1 = "10000000-0000-0000-0000-000000000001" // Emma
        const val CHILD_2 = "10000000-0000-0000-0000-000000000002" // Noah
        const val MEMBER_1 = "60000000-0000-0000-0000-000000000001" // Mom
        const val BRANCH_1 = "40000000-0000-0000-0000-000000000001" // Parents
        const val BRANCH_2 = "40000000-0000-0000-0000-000000000002" // Maternal
        const val FOLDER_1 = "50000000-0000-0000-0000-000000000001"
        const val T0 = 1_526_774_400_000L // 2018-05-19T16:00:00Z
    }

    private val photoBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val audioBytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val pdfBytes = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".toByteArray()

    fun buildMirrorPayload(): BackupPayload = BackupPayload(
        manifest = BackupManifest(
            formatVersion = 1,
            archiveID = ARCHIVE_ID,
            createdAtEpochMs = T0,
            appVersion = "0.1.0",
            letterCount = 5,
            attachmentCount = 3,
            recipientCount = 1,
            encryptionAlgorithm = "AES-256-GCM"
        ),
        children = listOf(
            ChildPayload(CHILD_1, "Emma", T0),
            ChildPayload(CHILD_2, "Noah", 1_604_275_200_000L)
        ),
        letters = listOf(
            // 1. Draft — unsealed, specificDate rule.
            LetterPayload(
                id = "20000000-0000-0000-0000-000000000001", childID = CHILD_1,
                branchID = BRANCH_1, folderID = null, authorMemberID = MEMBER_1,
                title = "Draft for Emma", body = "Prewritten draft body.", authorName = "Mom",
                createdAtEpochMs = T0, updatedAtEpochMs = T0 + 86_400_000,
                sealedAtEpochMs = null, isFavorite = false,
                unlockRuleRawValue = "specificDate", unlockDateEpochMs = null,
                unlockAgeYearsValue = null, lifeEventName = "",
                manuallyReleasedAtEpochMs = null
            ),
            // 2. Scheduled — sealed, future specific date.
            LetterPayload(
                id = "20000000-0000-0000-0000-000000000002", childID = CHILD_1,
                branchID = BRANCH_1, folderID = FOLDER_1, authorMemberID = MEMBER_1,
                title = "Sweet Sixteen", body = "Sealed body — sixteen.", authorName = "Mom",
                createdAtEpochMs = T0 + 900_000, updatedAtEpochMs = T0 + 900_000,
                sealedAtEpochMs = T0 + 900_000, isFavorite = true,
                unlockRuleRawValue = "specificDate", unlockDateEpochMs = 1_053_388_800_000L,
                unlockAgeYearsValue = null, lifeEventName = "",
                manuallyReleasedAtEpochMs = null
            ),
            // 3. Scheduled — birthday age (5) not yet reached.
            LetterPayload(
                id = "20000000-0000-0000-0000-000000000003", childID = CHILD_2,
                branchID = BRANCH_2, folderID = null, authorMemberID = MEMBER_1,
                title = "Starting School", body = "Age five letter.", authorName = "Dad",
                createdAtEpochMs = T0 + 1_800_000, updatedAtEpochMs = T0 + 1_800_000,
                sealedAtEpochMs = T0 + 1_800_000, isFavorite = false,
                unlockRuleRawValue = "birthdayAge", unlockDateEpochMs = null,
                unlockAgeYearsValue = 5, lifeEventName = "",
                manuallyReleasedAtEpochMs = null
            ),
            // 4. Unlocked — life event, manually released.
            LetterPayload(
                id = "20000000-0000-0000-0000-000000000004", childID = CHILD_1,
                branchID = null, folderID = null, authorMemberID = MEMBER_1,
                title = "Graduation", body = "Released by hand.", authorName = "Mom",
                createdAtEpochMs = T0 + 2_700_000, updatedAtEpochMs = T0 + 2_700_000,
                sealedAtEpochMs = T0 + 2_700_000, isFavorite = false,
                unlockRuleRawValue = "lifeEvent", unlockDateEpochMs = null,
                unlockAgeYearsValue = null, lifeEventName = "Graduation",
                manuallyReleasedAtEpochMs = T0 + 3_600_000
            ),
            // 5. Unlocked — sealed, past specific date.
            LetterPayload(
                id = "20000000-0000-0000-0000-000000000005", childID = CHILD_2,
                branchID = null, folderID = FOLDER_1, authorMemberID = null,
                title = "First Tooth", body = "Past date, open.", authorName = "Mom",
                createdAtEpochMs = T0 + 3_000_000, updatedAtEpochMs = T0 + 3_000_000,
                sealedAtEpochMs = T0 + 3_000_000, isFavorite = false,
                unlockRuleRawValue = "specificDate", unlockDateEpochMs = T0 + 3_600_000,
                unlockAgeYearsValue = null, lifeEventName = "",
                manuallyReleasedAtEpochMs = null
            )
        ),
        attachments = listOf(
            AttachmentPayload(
                id = "30000000-0000-0000-0000-000000000001",
                letterID = "20000000-0000-0000-0000-000000000002",
                fileName = "pic.png", contentTypeIdentifier = "public.png",
                kindRawValue = "photo", createdAtEpochMs = T0 + 900_000,
                data = photoBytes
            ),
            AttachmentPayload(
                id = "30000000-0000-0000-0000-000000000002",
                letterID = "20000000-0000-0000-0000-000000000003",
                fileName = "voice.m4a", contentTypeIdentifier = "public.mpeg-4-audio",
                kindRawValue = "audio", createdAtEpochMs = T0 + 1_800_000,
                data = audioBytes
            ),
            AttachmentPayload(
                id = "30000000-0000-0000-0000-000000000003",
                letterID = "20000000-0000-0000-0000-000000000001",
                fileName = "note.pdf", contentTypeIdentifier = "public.pdf",
                kindRawValue = "file", createdAtEpochMs = T0,
                data = pdfBytes
            )
        ),
        branches = listOf(
            BranchPayload(BRANCH_1, "Parents", "parents", null),
            BranchPayload(BRANCH_2, "Maternal Family", "maternal", null)
        ),
        folders = listOf(
            FolderPayload(FOLDER_1, BRANCH_1, null, "Emma's Corner")
        ),
        members = listOf(
            MemberPayload(MEMBER_1, "Mom", "Parent", "owner", "active", true)
        ),
        invitations = listOf(
            InvitationPayload("70000000-0000-0000-0000-000000000001", "Aunt Carol", "carol@example.com", "Aunt", "viewer", "pending")
        )
    )

    @Test
    fun `android production codec writes an archive the Swift decoder reads`() {
        val payload = buildMirrorPayload()
        val archive = LetterstomyArchive.encrypt(payload, PASSPHRASE)

        // Sanity: production round trip in-process must also pass.
        val decoded = LetterstomyArchive.decrypt(archive, PASSPHRASE)
        assertEquals(5, decoded.letters.size)
        assertEquals(3, decoded.attachments.size)
        assertTrue(decoded.attachments[0].data.contentEquals(photoBytes))

        // Write the archive for the Swift harness.
        val out = File("build/proof/android-archive.letterstomy")
        out.parentFile.mkdirs()
        out.writeBytes(archive)
        println("WROTE ${out.absolutePath} (${archive.size} bytes)")
    }
}