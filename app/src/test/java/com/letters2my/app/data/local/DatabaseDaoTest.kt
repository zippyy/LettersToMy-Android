package com.letters2my.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Core Room behavior: CRUD, ordering, relationships, and CASCADE delete
 * behavior (letters → attachments, branches → folders, delivery →
 * delivery attachments). Proves the schema relationships enforce the
 * safe-delete contract at the database layer.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class DatabaseDaoTest {

    private lateinit var db: LettersDatabase
    private lateinit var letterDao: LetterDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var childDao: ChildDao
    private lateinit var branchDao: BranchDao
    private lateinit var folderDao: FolderDao
    private lateinit var memberDao: MemberDao
    private lateinit var recoveryDao: RecoveryContactDao
    private lateinit var deliveryDao: DeliveryDao
    private lateinit var backupDao: BackupDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LettersDatabase::class.java).build()
        letterDao = db.letterDao()
        attachmentDao = db.attachmentDao()
        childDao = db.childDao()
        branchDao = db.branchDao()
        folderDao = db.folderDao()
        memberDao = db.memberDao()
        recoveryDao = db.recoveryContactDao()
        deliveryDao = db.deliveryDao()
        backupDao = db.backupDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Helpers ────────────────────────────────────────────

    private fun child(id: String = "child-1", name: String = "Emma") = ChildEntity(
        id = id, name = name, birthDate = 1526774400000L, createdAt = 1, updatedAt = 1
    )

    private fun letter(
        id: String = "letter-1",
        childId: String? = "child-1",
        title: String = "Hello",
        body: String = "World",
        sealed: Long? = null,
        updated: Long = 100L
    ) = LetterEntity(
        id = id, childId = childId, branchId = null, folderId = null,
        authorMemberId = "member-1", title = title, body = body,
        authorName = "Mom", createdAt = 1, updatedAt = updated,
        sealedAt = sealed, isFavorite = false, isDraft = sealed == null,
        unlockRuleRawValue = "specificDate", unlockDate = null,
        unlockAgeYears = null, lifeEventName = "", manuallyReleasedAt = null
    )

    private fun attachment(id: String, letterId: String) = AttachmentEntity(
        id = id, letterId = letterId, fileName = "$id.png", contentType = "image/png",
        kind = "photo", data = byteArrayOf(1, 2, 3), createdAt = 1
    )

    // ── CRUD ───────────────────────────────────────────────

    @Test
    fun `create child and letter then query them`() = runBlocking {
        childDao.insert(child())
        letterDao.insert(letter())

        assertEquals(1, childDao.getAllOnce().size)
        assertEquals(1, letterDao.getAllOnce().size)
        assertEquals("Emma", childDao.getById("child-1")!!.name)
        assertEquals("Hello", letterDao.getById("letter-1")!!.title)
    }

    @Test
    fun `update letter persists new fields`() = runBlocking {
        letterDao.insert(letter())
        letterDao.update(letter(title = "Updated", updated = 200))

        val l = letterDao.getById("letter-1")!!
        assertEquals("Updated", l.title)
        assertEquals(200L, l.updatedAt)
    }

    @Test
    fun `letter list orders by updated_at desc`() = runBlocking {
        letterDao.insert(letter(id = "old", updated = 100))
        letterDao.insert(letter(id = "new", updated = 300))
        letterDao.insert(letter(id = "mid", updated = 200))

        assertEquals(listOf("new", "mid", "old"), letterDao.getAllOnce().map { it.id })
    }

    // ── Attachment cascade on letter delete ────────────────

    @Test
    fun `deleting a draft letter cascades its attachments`() = runBlocking {
        letterDao.insert(letter(id = "letter-1", sealed = null))
        attachmentDao.insert(attachment("att-1", "letter-1"))
        attachmentDao.insert(attachment("att-2", "letter-1"))

        // Draft delete
        letterDao.delete(letter(id = "letter-1", sealed = null))

        assertNull(letterDao.getById("letter-1"))
        assertEquals(0, attachmentDao.countByLetter("letter-1"))
        assertEquals(0, attachmentDao.getAllOnce().size)
    }

    @Test
    fun `deleting a sealed scheduled letter cascades its attachments`() = runBlocking {
        val sealed = letter(id = "letter-s", sealed = 100L)
        letterDao.insert(sealed)
        attachmentDao.insert(attachment("att-s1", "letter-s"))
        attachmentDao.insert(attachment("att-s2", "letter-s"))

        letterDao.delete(sealed)

        assertEquals(0, attachmentDao.countByLetter("letter-s"))
        assertEquals(0, attachmentDao.getAllOnce().size)
    }

    @Test
    fun `deleting an unlocked letter cascades its attachments`() = runBlocking {
        val unlocked = letter(id = "letter-u", sealed = 100L)
        letterDao.insert(unlocked)
        attachmentDao.insert(attachment("att-u", "letter-u"))

        letterDao.delete(unlocked)

        assertEquals(0, attachmentDao.countByLetter("letter-u"))
        assertEquals(0, attachmentDao.getAllOnce().size)
    }

    @Test
    fun `deleting one letter leaves other letters and their attachments intact`() = runBlocking {
        letterDao.insert(letter(id = "l1", sealed = null))
        letterDao.insert(letter(id = "l2", sealed = 100L))
        attachmentDao.insert(attachment("a1", "l1"))
        attachmentDao.insert(attachment("a2", "l2"))

        letterDao.delete(letter(id = "l1", sealed = null))

        assertEquals(1, letterDao.getAllOnce().size)
        assertEquals(1, attachmentDao.getAllOnce().size)
        assertEquals("l2", attachmentDao.getAllOnce()[0].letterId)
    }

    @Test
    fun `deleteByLetter removes only that letters attachments`() = runBlocking {
        letterDao.insert(letter(id = "l1", sealed = null))
        letterDao.insert(letter(id = "l2", sealed = null))
        attachmentDao.insert(attachment("a1", "l1"))
        attachmentDao.insert(attachment("a2", "l2"))

        attachmentDao.deleteByLetter("l1")

        assertEquals(0, attachmentDao.countByLetter("l1"))
        assertEquals(1, attachmentDao.countByLetter("l2"))
    }

    // ── Branch / folder relationship ───────────────────────

    @Test
    fun `branch delete cascades its folders`() = runBlocking {
        branchDao.insert(
            BranchEntity("b1", "Parents", "parents", true, null, 1)
        )
        folderDao.insert(FolderEntity("f1", "b1", null, "Corner", 1))
        folderDao.insert(FolderEntity("f2", "b1", null, "Other", 1))

        branchDao.delete(BranchEntity("b1", "Parents", "parents", true, null, 1))

        assertEquals(0, folderDao.getAllOnce().size)
    }

    @Test
    fun `folders query scoped by branch`() = runBlocking {
        branchDao.insert(BranchEntity("b1", "A", "parents", true, null, 1))
        branchDao.insert(BranchEntity("b2", "B", "custom", false, null, 2))
        folderDao.insert(FolderEntity("f1", "b1", null, "A-Folder", 1))
        folderDao.insert(FolderEntity("f2", "b2", null, "B-Folder", 2))

        assertEquals(listOf("A-Folder"), folderDao.getByBranch("b1").first().map { it.name })
    }

    // ── Member / recovery / delivery / backup ──────────────

    @Test
    fun `member records insert and query with role`() = runBlocking {
        memberDao.insert(
            MemberEntity("m1", "Mom", "Parent", "owner", "active", true, true, "", "", "",
                1, 1)
        )
        memberDao.insert(
            MemberEntity("m2", "Aunt", "Aunt", "viewer", "active", false, false, "b1", "", "",
                2, 2)
        )
        assertEquals(2, memberDao.getAllOnce().size)
        assertEquals("owner", memberDao.getAllOnce().first { it.id == "m1" }.role)
    }

    @Test
    fun `recovery contacts round trip`() = runBlocking {
        recoveryDao.insert(
            RecoveryContactEntity("r1", "Uncle Bob", "bob@example.com", "555-0100", "Uncle",
                byteArrayOf(9, 9), "notes", 1, 1)
        )
        val contact = recoveryDao.getAllOnce().single()
        assertEquals("Uncle Bob", contact.displayName)
        assertEquals(2, contact.recoveryKeyHash!!.size)
    }

    @Test
    fun `delivery record with attachments and cascade`() = runBlocking {
        deliveryDao.insert(
            DeliveryRecordEntity("d1", "child-1", "letter-1", "Delivery", "body", "Mom",
                100, null, null, null, "delivered", 1)
        )
        deliveryDao.insertAttachment(
            DeliveryAttachmentEntity("da1", "d1", "note.txt", "text/plain", "file",
                byteArrayOf(4, 5), 1)
        )
        deliveryDao.insertAttachment(
            DeliveryAttachmentEntity("da2", "d1", "note2.txt", "text/plain", "file",
                byteArrayOf(6), 1)
        )

        assertEquals(1, deliveryDao.getAllOnce().size)
        assertEquals(2, deliveryDao.attachmentsFor("d1").size)

        // FK CASCADE: deleting the delivery record removes its attachments.
        db.openHelper.writableDatabase.execSQL("DELETE FROM delivery_records WHERE id = 'd1'")
        assertEquals(0, deliveryDao.getAllOnce().size)
        assertEquals(0, deliveryDao.attachmentsFor("d1").size)
    }

    @Test
    fun `backup records round trip`() = runBlocking {
        backupDao.insert(
            BackupRecordEntity("b1", "selfHosted", "completed", 5, 12345, 1, 2)
        )
        val record = backupDao.getAllOnce().single()
        assertEquals(5, record.letterCount)
        assertEquals("completed", record.status)
    }

    // ── Filter query behavior ──────────────────────────────

    @Test
    fun `letters filtered by child returns only that child`() = runBlocking {
        letterDao.insert(letter(id = "l1", childId = "child-1", sealed = null))
        letterDao.insert(letter(id = "l2", childId = "child-2", sealed = null))

        val child1Letters = letterDao.getByChild("child-1").first()
        assertEquals(listOf("l1"), child1Letters.map { it.id })
    }

    @Test
    fun `insert with REPLACE keeps single row per id`() = runBlocking {
        letterDao.insert(letter(id = "dup", title = "First"))
        letterDao.insert(letter(id = "dup", title = "Second"))

        assertEquals(1, letterDao.getAllOnce().size)
        assertEquals("Second", letterDao.getById("dup")!!.title)
    }

    @Test
    fun `deleteByLetter leaves the letter itself`() = runBlocking {
        letterDao.insert(letter(id = "keep", sealed = null))
        attachmentDao.insert(attachment("a", "keep"))

        attachmentDao.deleteByLetter("keep")
        attachmentDao.deleteById("a")

        // letter remains; attachments gone
        assertEquals(1, letterDao.getAllOnce().size)
        assertEquals(0, attachmentDao.getAllOnce().size)
        assertTrue(letterDao.getById("keep") != null)
        assertNull(letterDao.getById("gone"))
    }
}