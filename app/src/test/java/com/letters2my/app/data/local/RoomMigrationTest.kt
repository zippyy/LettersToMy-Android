package com.letters2my.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room schema v1 → v2 migration proof.
 *
 * A REAL v1 database is created from the exported v1 schema, populated with
 * representative data, migrated with the production MIGRATION_1_2, validated
 * against the exported v2 schema by MigrationTestHelper, then reopened via
 * Room and queried through the actual DAOs.
 *
 * This is the mandatory non-destructive migration proof — not a static SQL
 * review.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class RoomMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LettersDatabase::class.java
    )

    private val dbName = "migration-test.db"

    private val childId = "10000000-0000-0000-0000-000000000001"
    private val letterId = "20000000-0000-0000-0000-000000000001"
    private val attachmentId = "30000000-0000-0000-0000-000000000001"
    private val branchId = "40000000-0000-0000-0000-000000000001"
    private val folderId = "50000000-0000-0000-0000-000000000001"
    private val backupId = "60000000-0000-0000-0000-000000000001"

    /** Create the version-1 database, populate representative v1 rows, and close it. */
    private fun seedV1Database() {
        val db = helper.createDatabase(dbName, 1)
        // Children
        db.execSQL(
            "INSERT INTO children (id, name, birth_date, created_at, updated_at) " +
                "VALUES ('$childId', 'Emma', 1526774400000, 1526774400000, 1526774400000)"
        )
        db.execSQL(
            "INSERT INTO children (id, name, birth_date, created_at, updated_at) " +
                "VALUES ('10000000-0000-0000-0000-000000000002', 'Noah', 1604275200000, 1604275200000, 1604275200000)"
        )
        // Letters (v1 has NO author_member_id column)
        db.execSQL(
            "INSERT INTO letters (id, child_id, branch_id, folder_id, title, body, author_name, " +
                "created_at, updated_at, sealed_at, is_favorite, is_draft, unlock_rule, unlock_date, " +
                "unlock_age_years, life_event_name, manually_released_at) " +
                "VALUES ('$letterId', '$childId', '$branchId', '$folderId', 'Draft for Emma', 'body', 'Mom', " +
                "1526774400000, 1526774400000, NULL, 0, 1, 'specificDate', NULL, NULL, '', NULL)"
        )
        db.execSQL(
            "INSERT INTO letters (id, child_id, branch_id, folder_id, title, body, author_name, " +
                "created_at, updated_at, sealed_at, is_favorite, is_draft, unlock_rule, unlock_date, " +
                "unlock_age_years, life_event_name, manually_released_at) " +
                "VALUES ('20000000-0000-0000-0000-000000000002', NULL, NULL, NULL, 'Sealed', 'secret', 'Dad', " +
                "1526774400000, 1526774400000, 1526774400000, 1, 0, 'specificDate', 2031696000000, NULL, '', NULL)"
        )
        // Attachments (FK cascade to letters)
        db.execSQL(
            "INSERT INTO attachments (id, letter_id, file_name, content_type, kind, data, created_at) " +
                "VALUES ('$attachmentId', '$letterId', 'photo.png', 'image/png', 'photo', X'89504E470D0A1A0A', 1526774400000)"
        )
        // Branches / folders
        db.execSQL(
            "INSERT INTO branches (id, name, kind, is_seeded, parent_branch_id, created_at) " +
                "VALUES ('$branchId', 'Parents', 'parents', 1, NULL, 1526774400000)"
        )
        db.execSQL(
            "INSERT INTO folders (id, branch_id, parent_folder_id, name, created_at) " +
                "VALUES ('$folderId', '$branchId', NULL, 'Emma Corner', 1526774400000)"
        )
        // Invitation
        db.execSQL(
            "INSERT INTO invitations (id, invitee_display_name, invitee_address, relationship, role, " +
                "scope_archive_wide, scope_branch_ids, scope_folder_ids, scope_recipient_ids, " +
                "intended_recipient_id, can_invite_others, status, created_at) " +
                "VALUES ('70000000-0000-0000-0000-000000000001', 'Aunt Carol', 'carol@example.com', 'Aunt', 'viewer', " +
                "0, '', '', '', NULL, 0, 'pending', 1526774400000)"
        )
        // Backup record
        db.execSQL(
            "INSERT INTO backup_records (id, destination, status, letter_count, size_bytes, created_at, completed_at) " +
                "VALUES ('$backupId', 'selfHosted', 'completed', 2, 1234, 1526774400000, 1526774400000)"
        )
        db.close()
    }

    @Test
    fun `migration 1 to 2 preserves data and adds new schema`() {
        seedV1Database()

        // Run the production migration + validate against the exported v2
        // schema, then query the MIGRATED handle returned by the helper.
        val db = helper.runMigrationsAndValidate(dbName, 2, true, LettersDatabase.MIGRATION_1_2)

        // ── Original children remain ──
        val children = db.query("SELECT * FROM children ORDER BY created_at ASC")
        children.moveToFirst()
        assertEquals(2, children.count)
        assertEquals("Emma", children.getString(children.getColumnIndexOrThrow("name")))
        children.close()

        // ── Original letters remain, incl. the draft's nulls ──
        val letters = db.query("SELECT * FROM letters ORDER BY created_at ASC")
        letters.moveToFirst()
        assertEquals(2, letters.count)
        assertEquals("Draft for Emma", letters.getString(letters.getColumnIndexOrThrow("title")))
        letters.close()

        // ── Original attachments remain ──
        val attachments = db.query("SELECT * FROM attachments")
        assertEquals(1, attachments.count)
        attachments.close()

        // ── New column exists on letters with NULL default ──
        val letterCols = db.query("PRAGMA table_info(letters)")
        var hasAuthorMemberId = false
        while (letterCols.moveToNext()) {
            if (letterCols.getString(1) == "author_member_id") hasAuthorMemberId = true
        }
        letterCols.close()
        assertTrue("letters must gain author_member_id", hasAuthorMemberId)

        val migratedLetter = db.query(
            "SELECT author_member_id FROM letters WHERE id = '$letterId'"
        )
        migratedLetter.moveToFirst()
        assertNull("existing letter author_member_id must be NULL", migratedLetter.getString(0))
        migratedLetter.close()

        // ── New tables exist ──
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('members','recovery_contacts','delivery_records','delivery_attachments')"
        )
        assertEquals(4, tables.count)
        tables.close()

        // ── New tables accept inserts (schema is usable, FKs enforced) ──
        db.execSQL(
            "INSERT INTO members (id, display_name, relationship, role, status, can_invite_others, " +
                "scope_archive_wide, scope_branch_ids, scope_folder_ids, scope_recipient_ids, created_at, updated_at) " +
                "VALUES ('80000000-0000-0000-0000-000000000001', 'Mom', 'Parent', 'owner', 'active', 1, " +
                "1, '', '', '', 1526774400000, 1526774400000)"
        )
        db.execSQL(
            "INSERT INTO delivery_records (id, recipient_id, original_letter_id, title, body, author_name, " +
                "delivered_at, read_at, reply_body, replied_at, state, created_at) " +
                "VALUES ('90000000-0000-0000-0000-000000000001', '$childId', '$letterId', 'Delivery', 'body', 'Mom', " +
                "1526774400000, NULL, NULL, NULL, 'delivered', 1526774400000)"
        )
        db.execSQL(
            "INSERT INTO delivery_attachments (id, delivery_id, file_name, content_type, kind, data, created_at) " +
                "VALUES ('a0000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001', " +
                "'note.txt', 'text/plain', 'file', X'68656C6C6F', 1526774400000)"
        )

        db.close()

        // ── App can REOPEN the migrated database and query through DAOs ──
        val context = ApplicationProvider.getApplicationContext<Context>()
        val reopened = Room.databaseBuilder(context, LettersDatabase::class.java, dbName)
            .addMigrations(LettersDatabase.MIGRATION_1_2)
            .build()

        val kids = runBlocking { reopened.childDao().getAllOnce() }
        assertEquals(2, kids.size)

        val lettersAfter = runBlocking { reopened.letterDao().getAllOnce() }
        assertEquals(2, lettersAfter.size)
        assertEquals(0, lettersAfter.count { it.authorMemberId != null }) // NULL preserved

        val members = runBlocking { reopened.memberDao().getAllOnce() }
        assertEquals(1, members.size)
        assertEquals("owner", members[0].role)

        val deliveries = runBlocking { reopened.deliveryDao().getAllOnce() }
        assertEquals(1, deliveries.size)
        assertEquals(1, runBlocking { reopened.deliveryDao().attachmentsFor(deliveries[0].id) }.size)

        reopened.close()
    }

    @Test
    fun `no destructive reset - backup record survives migration`() {
        seedV1Database()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, LettersDatabase.MIGRATION_1_2)

        val backups = db.query("SELECT * FROM backup_records WHERE id = '$backupId'")
        assertEquals(1, backups.count)
        backups.moveToFirst()
        assertEquals("selfHosted", backups.getString(backups.getColumnIndexOrThrow("destination")))
        backups.close()
        db.close()
    }
}