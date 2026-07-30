package com.letters2my.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LetterEntity::class,
        AttachmentEntity::class,
        ChildEntity::class,
        BranchEntity::class,
        FolderEntity::class,
        InvitationEntity::class,
        BackupRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LettersDatabase : RoomDatabase() {
    abstract fun letterDao(): LetterDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun childDao(): ChildDao
    abstract fun branchDao(): BranchDao
    abstract fun folderDao(): FolderDao
    abstract fun invitationDao(): InvitationDao
    abstract fun backupDao(): BackupDao

    companion object {
        @Volatile
        private var INSTANCE: LettersDatabase? = null

        fun getInstance(context: Context): LettersDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LettersDatabase::class.java,
                    "letters_to_my.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}