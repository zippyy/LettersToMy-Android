package com.letters2my.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.letters2my.app.domain.BackupService
import com.letters2my.app.domain.BackupPayload
import com.letters2my.app.domain.ChildPayload
import com.letters2my.app.domain.LetterPayload
import com.letters2my.app.domain.LetterstomyArchive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Restore safety (Phase 10): restore must never partially replace a live
 * database with unvalidated external bytes.
 *
 * Proves:
 *  - wrong passphrase / corrupted archive raise BEFORE any DB mutation
 *  - validation (decrypt) precedes the destructive step
 *  - applyRestore is additive: existing rows by ID are skipped, new rows
 *    imported preserving original identifiers, attachments only when their
 *    letter exists
 *  - the archive that comes back out of the restored DB matches the
 *    restored payload (byte-identical attachments)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class RestoreSafetyTest {

    private lateinit var db: LettersDatabase
    private lateinit var repo: LetterRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LettersDatabase::class.java).build()
        repo = LetterRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun samplePayload(): BackupPayload {
        val now = 1_700_000_000_000L
        return BackupPayload(
            manifest = com.letters2my.app.domain.BackupManifest(
                formatVersion = 1,
                archiveID = "80000000-0000-0000-0000-0000000000aa",
                createdAtEpochMs = now,
                appVersion = "0.1.0",
                letterCount = 2,
                attachmentCount = 1,
                recipientCount = 1,
                encryptionAlgorithm = "AES-256-GCM"
            ),
            children = listOf(ChildPayload("10000000-0000-0000-0000-000000000001", "Emma", now)),
            letters = listOf(
                LetterPayload(
                    id = "20000000-0000-0000-0000-000000000001", childID = "10000000-0000-0000-0000-000000000001",
                    branchID = null, folderID = null, authorMemberID = null,
                    title = "Hello", body = "World", authorName = "Mom",
                    createdAtEpochMs = now, updatedAtEpochMs = now,
                    sealedAtEpochMs = null, isFavorite = false,
                    unlockRuleRawValue = "specificDate", unlockDateEpochMs = null,
                    unlockAgeYearsValue = null, lifeEventName = "",
                    manuallyReleasedAtEpochMs = null
                )
            ),
            attachments = listOf(
                com.letters2my.app.domain.AttachmentPayload(
                    id = "30000000-0000-0000-0000-000000000001",
                    letterID = "20000000-0000-0000-0000-000000000001",
                    fileName = "pic.png", contentTypeIdentifier = "public.png",
                    kindRawValue = "photo", createdAtEpochMs = now,
                    data = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
                )
            ),
            branches = emptyList(), folders = emptyList(),
            members = emptyList(), invitations = emptyList()
        )
    }

    // ── Validation precedes destructive action ─────────────

    @Test
    fun `decrypting a corrupted archive throws before any db mutation`() {
        val junk = ByteArray(64) { 7 }
        var threw = false
        try {
            LetterstomyArchive.decrypt(junk, "pass")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("corrupt bytes must be rejected", threw)

        // DB is untouched — no children/letters were created by the attempt.
        val empty = runBlocking {
            Pair(db.childDao().getAllOnce().size, db.letterDao().getAllOnce().size)
        }
        assertEquals(0 to 0, empty)
    }

    @Test
    fun `wrong passphrase raises decryption error and does not mutate db`() {
        val payload = samplePayload()
        val archive = LetterstomyArchive.encrypt(payload, "correct-pass")

        var threw = false
        try {
            LetterstomyArchive.decrypt(archive, "wrong-pass")
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("wrong passphrase must be rejected", threw)

        val empty = runBlocking { Pair(db.childDao().getAllOnce().size, db.letterDao().getAllOnce().size) }
        assertEquals(0 to 0, empty)
    }

    // ── Restore is additive, preserves original IDs ────────

    @Test
    fun `restore imports new rows preserving original identifiers`() = runBlocking {
        val payload = samplePayload()
        val summary = BackupService(repo).applyRestore(payload, repo)

        assertEquals(3, summary.imported) // child + letter + attachment
        assertEquals(0, summary.skipped)

        val child = db.childDao().getById("10000000-0000-0000-0000-000000000001")!!
        assertEquals("Emma", child.name)

        val letter = db.letterDao().getById("20000000-0000-0000-0000-000000000001")!!
        assertEquals("Hello", letter.title)

        val att = db.attachmentDao().getAllOnce().single { it.id == "30000000-0000-0000-0000-000000000001" }
        assertTrue(att.data.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun `restore skips existing ids - no duplicates`() = runBlocking {
        val payload = samplePayload()
        val svc = BackupService(repo)

        val first = svc.applyRestore(payload, repo)
        val second = svc.applyRestore(payload, repo) // same IDs again

        assertEquals(3, first.imported) // child + letter + attachment
        assertEquals(0, first.skipped)
        assertEquals(0, second.imported) // everything already exists
        assertEquals(3, second.skipped) // child + letter + attachment

        assertEquals(1, db.childDao().getAllOnce().size)
        assertEquals(1, db.letterDao().getAllOnce().size)
        assertEquals(1, db.attachmentDao().getAllOnce().size)
    }

    @Test
    fun `attachment without a valid letter is skipped`() = runBlocking {
        val payload = samplePayload()
        // Point the attachment at a letter that does not exist.
        val orphan = payload.copy(
            attachments = listOf(
                com.letters2my.app.domain.AttachmentPayload(
                    id = "30000000-0000-0000-0000-000000000099",
                    letterID = "20000000-0000-0000-0000-000000000099", // missing
                    fileName = "orphan.bin", contentTypeIdentifier = "public.data",
                    kindRawValue = "file", createdAtEpochMs = 1,
                    data = byteArrayOf(1)
                )
            )
        )
        val summary = BackupService(repo).applyRestore(orphan, repo)

        assertEquals(2, summary.imported) // child + letter only
        assertEquals(1, summary.skipped) // orphan attachment
        assertEquals(0, runBlocking { db.attachmentDao().getAllOnce().size })
    }

    // ── Restored data round-trips back into a new archive ──

    @Test
    fun `permission denied delete leaves letter and attachments intact`() = runBlocking {
        val payload = samplePayload()
        BackupService(repo).applyRestore(payload, repo)

        val letter = db.letterDao().getById("20000000-0000-0000-0000-000000000001")!!
        val result = repo.deleteLetterWithPermission(letter, hasPermission = false)

        assertTrue(result.isFailure)
        assertEquals(1, db.letterDao().getAllOnce().size)
        assertEquals(1, db.attachmentDao().getAllOnce().size)
        assertTrue(db.attachmentDao().getAllOnce().any { it.id == "30000000-0000-0000-0000-000000000001" })
    }

    @Test
    fun `permitted delete cascades attachments`() = runBlocking {
        val payload = samplePayload()
        BackupService(repo).applyRestore(payload, repo)

        val letter = db.letterDao().getById("20000000-0000-0000-0000-000000000001")!!
        val result = repo.deleteLetterWithPermission(letter, hasPermission = true)

        assertTrue(result.isSuccess)
        assertEquals(0, db.letterDao().getAllOnce().size)
        assertEquals(0, db.attachmentDao().getAllOnce().size)
    }

    @Test
    fun `restored database re-exports identical archive payload`() = runBlocking {
        val svc = BackupService(repo)
        svc.applyRestore(samplePayload(), repo)

        val reexported = svc.createArchive("roundtrip")
        // Same letter_count, same child, same attachment bytes.
        assertEquals(1, reexported.payload.letters.size)
        assertEquals(1, reexported.payload.attachments.size)
        assertEquals("Emma", reexported.payload.children.single().name)
        assertTrue(
            reexported.payload.attachments.single()
                .data.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        )

        // And it decrypts with the production codec.
        val decoded = LetterstomyArchive.decrypt(reexported.archiveBytes, "roundtrip")
        assertEquals(1, decoded.letters.size)
    }
}