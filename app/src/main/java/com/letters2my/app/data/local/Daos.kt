package com.letters2my.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {
    @Query("SELECT * FROM letters ORDER BY updated_at DESC")
    fun getAll(): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id = :id")
    suspend fun getById(id: String): LetterEntity?

    @Query("SELECT * FROM letters ORDER BY updated_at DESC")
    suspend fun getAllOnce(): List<LetterEntity>

    @Query("SELECT * FROM letters WHERE child_id = :childId ORDER BY updated_at DESC")
    fun getByChild(childId: String): Flow<List<LetterEntity>>

    @Query("SELECT * FROM letters WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<LetterEntity>

    @Query("SELECT COUNT(*) FROM letters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(letter: LetterEntity)

    @Update
    suspend fun update(letter: LetterEntity)

    @Delete
    suspend fun delete(letter: LetterEntity)

    @Query("DELETE FROM letters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM letters WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE letter_id = :letterId ORDER BY created_at ASC")
    suspend fun getByLetter(letterId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    fun getAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun getAllOnce(): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachments WHERE letter_id = :letterId")
    suspend fun countByLetter(letterId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE letter_id = :letterId")
    suspend fun deleteByLetter(letterId: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY created_at ASC")
    fun getAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<ChildEntity>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getById(id: String): ChildEntity?

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

    @Query("SELECT * FROM branches ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<BranchEntity>

    @Query("SELECT COUNT(*) FROM branches")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(branch: BranchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<BranchEntity>)

    @Update
    suspend fun update(branch: BranchEntity)

    @Delete
    suspend fun delete(branch: BranchEntity)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE branch_id = :branchId ORDER BY created_at ASC")
    fun getByBranch(branchId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY created_at ASC")
    fun getAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations ORDER BY created_at DESC")
    fun getAll(): Flow<List<InvitationEntity>>

    @Query("SELECT * FROM invitations ORDER BY created_at DESC")
    suspend fun getAllOnce(): List<InvitationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invitation: InvitationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invitations: List<InvitationEntity>)

    @Update
    suspend fun update(invitation: InvitationEntity)

    @Delete
    suspend fun delete(invitation: InvitationEntity)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY created_at ASC")
    fun getAll(): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<MemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<MemberEntity>)

    @Update
    suspend fun update(member: MemberEntity)

    @Delete
    suspend fun delete(member: MemberEntity)
}

@Dao
interface RecoveryContactDao {
    @Query("SELECT * FROM recovery_contacts ORDER BY created_at ASC")
    fun getAll(): Flow<List<RecoveryContactEntity>>

    @Query("SELECT * FROM recovery_contacts ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<RecoveryContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: RecoveryContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<RecoveryContactEntity>)

    @Delete
    suspend fun delete(contact: RecoveryContactEntity)
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM delivery_records ORDER BY delivered_at DESC")
    fun getAll(): Flow<List<DeliveryRecordEntity>>

    @Query("SELECT * FROM delivery_records ORDER BY delivered_at DESC")
    suspend fun getAllOnce(): List<DeliveryRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DeliveryRecordEntity)

    @Update
    suspend fun update(record: DeliveryRecordEntity)

    @Query("SELECT * FROM delivery_attachments WHERE delivery_id = :deliveryId")
    suspend fun attachmentsFor(deliveryId: String): List<DeliveryAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: DeliveryAttachmentEntity)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_records ORDER BY created_at DESC")
    fun getAll(): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records ORDER BY created_at DESC")
    suspend fun getAllOnce(): List<BackupRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecordEntity)

    @Delete
    suspend fun delete(record: BackupRecordEntity)
}