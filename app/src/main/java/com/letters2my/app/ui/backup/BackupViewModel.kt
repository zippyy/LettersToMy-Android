package com.letters2my.app.ui.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.LettersApplication
import com.letters2my.app.data.local.BackupRecordEntity
import com.letters2my.app.data.local.LetterRepository
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.toEntity
import com.letters2my.app.data.sync.SelfHostedSyncProvider
import com.letters2my.app.domain.BackupService
import com.letters2my.app.domain.RestoreSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Backup / restore flow, mirroring iOS BackupService semantics:
 *
 *  - Portable encrypted `.letterstomy` archives (AES-256-GCM, SHA-256 key).
 *  - Push/list/pull/delete against the SelfHostedSync API v1 server.
 *  - letter_count derived from the archive manifest (current letter count).
 *  - Restore skips existing IDs (duplicate prevention) — an OLD backup may
 *    legitimately contain previously-deleted letters (that is the point of
 *    a historical backup); no tombstones are invented.
 *  - Failed ops surface typed errors; no silent null/false.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LettersApplication
    private val db = LettersDatabase.getInstance(application)
    private val repo = LetterRepository(db)
    private val backupService = BackupService(repo, appVersion = "0.1.0")

    val isBusy = MutableStateFlow(false)
    val statusMessage = MutableStateFlow<String?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    /** Remote backup metadata (id, timestamp, size, letter_count). */
    val remoteBackups = MutableStateFlow<List<RemoteBackup>>(emptyList())

    /** Local history. */
    val localHistory = MutableStateFlow<List<BackupRecordEntity>>(emptyList())

    val passphrasePrompt = MutableStateFlow(false)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)

    private fun provider(): SelfHostedSyncProvider? =
        app.selfHostedProvider()

    /** Refresh remote listing + local history. */
    suspend fun refresh() {
        val provider = provider()
        if (provider != null) {
            try {
                val metas = provider.apiClient.listBackups()
                remoteBackups.value = metas.map {
                    RemoteBackup(id = it.id, timestamp = it.timestamp, size = it.size, letterCount = it.letterCount)
                }
            } catch (e: Exception) {
                errorMessage.value = "Could not list remote backups: ${e.message?.take(100)}"
            }
        }
        localHistory.value = db.backupDao().getAllOnce()
    }

    /** Create a full encrypted archive and upload to the self-hosted server. */
    fun createAndPush(passphrase: String) {
        if (passphrase.isBlank()) {
            errorMessage.value = "A passphrase is required."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val provider = provider()
                    ?: run { errorMessage.value = "Self-hosted server not configured."; return@launch }

                val created = withContext(Dispatchers.Default) {
                    backupService.createArchive(passphrase)
                }
                val archiveId = created.payload.manifest.archiveID
                val letterCount = created.letterCount

                // Upload: opaque encrypted archive + letter_count metadata hint.
                val result = provider.apiClient.pushBackup(
                    id = archiveId,
                    letterCount = letterCount,
                    archive = created.archiveBytes
                )
                if (result.sha256.isBlank()) {
                    errorMessage.value = "Server did not return a SHA-256 confirmation."
                    return@launch
                }

                // Persist local history record.
                db.backupDao().insert(
                    BackupRecordEntity(
                        id = UUID.randomUUID().toString(),
                        destination = "selfHosted",
                        status = "completed",
                        letterCount = letterCount,
                        sizeBytes = created.sizeBytes,
                        createdAt = created.payload.manifest.createdAtEpochMs,
                        completedAt = System.currentTimeMillis()
                    )
                )
                statusMessage.value =
                    "Backup uploaded: $letterCount letters, ${created.sizeBytes} bytes (sha256 ${result.sha256.take(8)}…)"
                refresh()
            } catch (e: Exception) {
                errorMessage.value = "Backup failed: ${e.message?.take(160)}"
            } finally {
                isBusy.value = false
                passphrasePrompt.value = false
            }
        }
    }

    /** Download + decrypt a remote backup (preview before restore). */
    fun previewRemote(id: String, passphrase: String) {
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val provider = provider() ?: return@launch
                val bytes = provider.apiClient.pullBackup(id)
                val payload = withContext(Dispatchers.Default) {
                    backupService.decryptArchive(bytes, passphrase)
                }
                restorePreview.value = RestorePreview(
                    archiveId = payload.manifest.archiveID,
                    createdAt = payload.manifest.createdAtEpochMs,
                    letterCount = payload.letters.size,
                    attachmentCount = payload.attachments.size,
                    childCount = payload.children.size,
                    branchCount = payload.branches.size,
                    folderCount = payload.folders.size,
                    memberCount = payload.members.size,
                    payload = payload
                )
            } catch (e: Exception) {
                errorMessage.value = "Could not download/decrypt backup: ${e.message?.take(160)}"
            } finally {
                isBusy.value = false
            }
        }
    }

    /** Apply a decrypted payload to the local store (duplicate-skip). */
    fun applyRestore() {
        val preview = restorePreview.value ?: return
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val summary: RestoreSummary = backupService.applyRestore(preview.payload, repo)
                statusMessage.value =
                    "Restored ${summary.imported} records, skipped ${summary.skipped} duplicates."
                restorePreview.value = null
                passphrasePrompt.value = false
            } catch (e: Exception) {
                errorMessage.value = "Restore failed: ${e.message?.take(160)}"
            } finally {
                isBusy.value = false
            }
        }
    }

    /** Delete a remote backup (server keeps others untouched). */
    fun deleteRemote(id: String) {
        viewModelScope.launch {
            isBusy.value = true
            errorMessage.value = null
            try {
                val provider = provider() ?: return@launch
                provider.apiClient.deleteBackup(id)
                statusMessage.value = "Backup $id deleted."
                refresh()
            } catch (e: Exception) {
                errorMessage.value = "Delete failed: ${e.message?.take(100)}"
            } finally {
                isBusy.value = false
            }
        }
    }

    fun clearStatus() { statusMessage.value = null }
    fun clearError() { errorMessage.value = null }
    fun dismissRestorePreview() { restorePreview.value = null }
}

data class RemoteBackup(val id: String, val timestamp: Long, val size: Long, val letterCount: Int)

data class RestorePreview(
    val archiveId: String,
    val createdAt: Long,
    val letterCount: Int,
    val attachmentCount: Int,
    val childCount: Int,
    val branchCount: Int,
    val folderCount: Int,
    val memberCount: Int,
    val payload: com.letters2my.app.domain.BackupPayload
)