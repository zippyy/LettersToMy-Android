package com.letters2my.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {
    @Query("SELECT * FROM letters ORDER BY updated_at DESC")
    fun getAll(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id = :id")
    suspend fun getById(id: String): LetterEntity?

    @Query("SELECT * FROM letters WHERE child_id = :childId ORDER BY updated_at DESC")
    fun getByChild(childId: String): Flow<List<LetterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(letter: LetterEntity)

    @Update
    suspend fun update(letter: LetterEntity)

    @Delete
    suspend fun delete(letter: LetterEntity)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE letter_id = :letterId")
    suspend fun getByLetter(letterId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)
}

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY created_at ASC")
    fun getAll(): Flow<List<ChildEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(child: ChildEntity)

    @Update
    suspend fun update(child: ChildEntity)

    @Delete
    suspend fun delete(child: ChildEntity)
}

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY created_at ASC")
    fun getAll(): Flow<List<BranchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(branch: BranchEntity)

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun count(): Int

    @Delete
    suspend fun delete(branch: BranchEntity)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE branch_id = :branchId ORDER BY created_at ASC")
    fun getByBranch(branchId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY created_at ASC")
    fun getAll(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations ORDER BY created_at DESC")
    fun getAll(): Flow<List<InvitationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invitation: InvitationEntity)

    @Update
    suspend fun update(invitation: InvitationEntity)

    @Delete
    suspend fun delete(invitation: InvitationEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_records ORDER BY created_at DESC")
    fun getAll(): Flow<List<BackupRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecordEntity)

    @Delete
    suspend fun delete(record: BackupRecordEntity)
}