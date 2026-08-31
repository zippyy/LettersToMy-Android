package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (Included in CrossPlatformArchiveTest.) Android-side archive production
 * helpers: build a representative payload matching what BackupService
 * produces from a real store, then prove the payload JSON round-trips
 * through the Swift-compatible codec.
 */
class ArchiveProductionTest {

    private fun samplePayload(): BackupPayload {
        val now = 1_700_000_000_000L
        return BackupPayload(
            manifest = BackupManifest(
                formatVersion = 1,
                archiveID = "80000000-0000-0000-0000-0000000000aa",
                createdAtEpochMs = now,
                appVersion = "0.1.0",
                letterCount = 3,
                attachmentCount = 2,
                recipientCount = 1,
                encryptionAlgorithm = "AES-256-GCM"
            ),
            children = listOf(
                ChildPayload("10000000-0000-0000-0000-000000000001", "Emma", 1526774400000L)
            ),
            letters = listOf(
                LetterPayload(
                    id = "20000000-0000-0000-0000-000000000001",
                    childID = "10000000-0000-0000-0000-000000000001",
                    branchID = null, folderID = null, authorMemberID = null,
                    title = "Draft", body = "pending", authorName = "Mom",
                    createdAtEpochMs = now, updatedAtEpochMs = now,
                    sealedAtEpochMs = null, isFavorite = false,
                    unlockRuleRawValue = "specificDate", unlockDateEpochMs = null,
                    unlockAgeYearsValue = null, lifeEventName = "",
                    manuallyReleasedAtEpochMs = null
                ),
                LetterPayload(
                    id = "20000000-0000-0000-0000-000000000002",
                    childID = "10000000-0000-0000-0000-000000000001",
                    branchID = null, folderID = null, authorMemberID = null,
                    title = "18th", body = "sealed", authorName = "Dad",
                    createdAtEpochMs = now, updatedAtEpochMs = now,
                    sealedAtEpochMs = now, isFavorite = true,
                    unlockRuleRawValue = "birthdayAge", unlockDateEpochMs = null,
                    unlockAgeYearsValue = 18, lifeEventName = "",
                    manuallyReleasedAtEpochMs = null
                ),
                LetterPayload(
                    id = "20000000-0000-0000-0000-000000000003",
                    childID = null, branchID = null, folderID = null,
                    authorMemberID = null,
                    title = "Graduation", body = "released", authorName = "Mom",
                    createdAtEpochMs = now, updatedAtEpochMs = now,
                    sealedAtEpochMs = now, isFavorite = false,
                    unlockRuleRawValue = "lifeEvent", unlockDateEpochMs = null,
                    unlockAgeYearsValue = null, lifeEventName = "Graduation",
                    manuallyReleasedAtEpochMs = now
                )
            ),
            attachments = listOf(
                AttachmentPayload(
                    id = "30000000-0000-0000-0000-000000000001",
                    letterID = "20000000-0000-0000-0000-000000000001",
                    fileName = "pic.png", contentTypeIdentifier = "public.png",
                    kindRawValue = "photo", createdAtEpochMs = now,
                    data = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
                ),
                AttachmentPayload(
                    id = "30000000-0000-0000-0000-000000000002",
                    letterID = "20000000-0000-0000-0000-000000000002",
                    fileName = "note.pdf", contentTypeIdentifier = "public.pdf",
                    kindRawValue = "file", createdAtEpochMs = now,
                    data = "%PDF-1.4\n".toByteArray()
                )
            ),
            branches = listOf(
                BranchPayload("40000000-0000-0000-0000-000000000001", "Parents", "parents", null)
            ),
            folders = listOf(
                FolderPayload("50000000-0000-0000-0000-000000000001", "40000000-0000-0000-0000-000000000001", null, "Emma Corner")
            ),
            members = listOf(
                MemberPayload("60000000-0000-0000-0000-000000000001", "Mom", "Parent", "owner", "active", true)
            ),
            invitations = listOf(
                InvitationPayload("70000000-0000-0000-0000-000000000001", "Aunt Carol", "carol@example.com", "Aunt", "viewer", "pending")
            )
        )
    }

    @Test
    fun `payload JSON round-trips through the Swift-compatible codec`() {
        val payload = samplePayload()
        val encrypted = LetterstomyArchive.encrypt(payload, "roundtrip-pass")
        val decoded = LetterstomyArchive.decrypt(encrypted, "roundtrip-pass")

        assertEquals(payload.manifest.archiveID, decoded.manifest.archiveID)
        assertEquals(payload.children.size, decoded.children.size)
        assertEquals(payload.letters.size, decoded.letters.size)
        assertEquals(payload.attachments.size, decoded.attachments.size)
        assertEquals(payload.branches.size, decoded.branches.size)
        assertEquals(payload.folders.size, decoded.folders.size)
        assertEquals(payload.members.size, decoded.members.size)
        assertEquals(payload.invitations.size, decoded.invitations.size)

        // Letter fields survive exactly.
        val orig = payload.letters.first { it.id == "20000000-0000-0000-0000-000000000002" }
        val round = decoded.letters.first { it.id == orig.id }
        assertEquals(orig.unlockRuleRawValue, round.unlockRuleRawValue)
        assertEquals(orig.unlockAgeYearsValue, round.unlockAgeYearsValue)
        assertEquals(orig.sealedAtEpochMs, round.sealedAtEpochMs)
        assertEquals(orig.isFavorite, round.isFavorite)

        // Attachment bytes are byte-identical.
        val a1 = decoded.attachments.first { it.id == "30000000-0000-0000-0000-000000000001" }
        assertTrue(a1.data.contentEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
    }

    @Test
    fun `nulls are omitted and defaults present in emitted JSON`() {
        val payload = samplePayload()
        val json = payload.toJson().toString()

        // Swift format: no null values emitted, non-optional defaults present.
        assertTrue(!json.contains(":null"))
        assertTrue(json.contains("\"isFavorite\":false"))
        assertTrue(json.contains("\"encryptionAlgorithm\":\"AES-256-GCM\""))
        // reference-date seconds are used for dates.
        assertTrue(json.contains("\"createdAt\":"))
    }
}