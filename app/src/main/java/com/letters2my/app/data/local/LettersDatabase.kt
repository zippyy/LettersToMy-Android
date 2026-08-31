package com.letters2my.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LetterEntity::class,
        AttachmentEntity::class,
        ChildEntity::class,
        BranchEntity::class,
        FolderEntity::class,
        InvitationEntity::class,
        BackupRecordEntity::class,
        MemberEntity::class,
        RecoveryContactEntity::class,
        DeliveryRecordEntity::class,
        DeliveryAttachmentEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class LettersDatabase : RoomDatabase() {
    abstract fun letterDao(): LetterDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun childDao(): ChildDao
    abstract fun branchDao(): BranchDao
    abstract fun folderDao(): FolderDao
    abstract fun invitationDao(): InvitationDao
    abstract fun backupDao(): BackupDao
    abstract fun memberDao(): MemberDao
    abstract fun recoveryContactDao(): RecoveryContactDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {

        /**
         * Schema v1 → v2 migration (NON-DESTRUCTIVE):
         *  - letters gains `author_member_id` (nullable) — ALTER only.
         *  - attachments / folders / children / branches / invitations /
         *    backup_records are unchanged.
         *  - new tables: members, recovery_contacts, delivery_records,
         *    delivery_attachments (with CASCADE FK to delivery_records).
         *
         * Room validates this migration against the exported schemas in
         * app/schemas/. Never use fallbackToDestructiveMigration() for this.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing letters keep their data; new nullable column.
                db.execSQL("ALTER TABLE `letters` ADD COLUMN `author_member_id` TEXT DEFAULT NULL")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `members` (" +
                        "`id` TEXT NOT NULL, " +
                        "`display_name` TEXT NOT NULL, " +
                        "`relationship` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`can_invite_others` INTEGER NOT NULL, " +
                        "`scope_archive_wide` INTEGER NOT NULL, " +
                        "`scope_branch_ids` TEXT NOT NULL, " +
                        "`scope_folder_ids` TEXT NOT NULL, " +
                        "`scope_recipient_ids` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recovery_contacts` (" +
                        "`id` TEXT NOT NULL, " +
                        "`display_name` TEXT NOT NULL, " +
                        "`email_address` TEXT NOT NULL, " +
                        "`phone_number` TEXT, " +
                        "`relationship` TEXT NOT NULL, " +
                        "`recovery_key_hash` BLOB, " +
                        "`notes` TEXT, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `delivery_records` (" +
                        "`id` TEXT NOT NULL, " +
                        "`recipient_id` TEXT NOT NULL, " +
                        "`original_letter_id` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`author_name` TEXT NOT NULL, " +
                        "`delivered_at` INTEGER NOT NULL, " +
                        "`read_at` INTEGER, " +
                        "`reply_body` TEXT, " +
                        "`replied_at` INTEGER, " +
                        "`state` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `delivery_attachments` (" +
                        "`id` TEXT NOT NULL, " +
                        "`delivery_id` TEXT NOT NULL, " +
                        "`file_name` TEXT NOT NULL, " +
                        "`content_type` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`data` BLOB NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`delivery_id`) REFERENCES `delivery_records`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        @Volatile
        private var INSTANCE: LettersDatabase? = null

        fun getInstance(context: Context): LettersDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LettersDatabase::class.java,
                    "letters_to_my.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Deliberately NO fallbackToDestructiveMigration(): schema
                    // changes must preserve user data (see MIGRATION_1_2).
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}