package com.letters2my.app.ui.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.AttachmentEntity
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.LetterEntity
import com.letters2my.app.domain.Milestones
import com.letters2my.app.domain.MilestoneTemplate
import com.letters2my.app.domain.UnlockRuleKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Letter editor state + save logic. Save safety: a failed save keeps the
 * editor open, preserves input, surfaces the error, and never creates a
 * duplicate entity on retry (single stable ID per editing session).
 */
class LetterEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LettersDatabase.getInstance(application)
    private val letterDao = db.letterDao()
    private val attachmentDao = db.attachmentDao()
    private val childDao = db.childDao()
    private val branchDao = db.branchDao()
    private val folderDao = db.folderDao()

    /** Stable ID for this editing session — never regenerated on retry. */
    private var sessionLetterId: String? = null
    private var loadedLetter: LetterEntity? = null

    val children = childDao.getAll()
    val branches = branchDao.getAll()
    val folders = folderDao.getAll()

    // Editor fields
    val title = MutableStateFlow("")
    val body = MutableStateFlow("")
    val authorName = MutableStateFlow("")
    val childId = MutableStateFlow<String?>(null)
    val branchId = MutableStateFlow<String?>(null)
    val folderId = MutableStateFlow<String?>(null)
    val unlockKind = MutableStateFlow(UnlockRuleKind.SPECIFIC_DATE)
    val unlockDate = MutableStateFlow<Long?>(null)
    val unlockAgeYears = MutableStateFlow<Int?>(null)
    val lifeEventName = MutableStateFlow("")
    val isFavorite = MutableStateFlow(false)
    val selectedMilestone = MutableStateFlow<MilestoneTemplate?>(null)

    /** Pending attachment payloads (not yet persisted). */
    val pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())

    val isSaving = MutableStateFlow(false)
    val saveError = MutableStateFlow<String?>(null)

    fun load(letterId: String?) {
        if (letterId == null) {
            // New letter
            sessionLetterId = UUID.randomUUID().toString()
            return
        }
        viewModelScope.launch {
            val entity = letterDao.getById(letterId) ?: return@launch
            loadedLetter = entity
            sessionLetterId = entity.id
            title.value = entity.title
            body.value = entity.body
            authorName.value = entity.authorName
            childId.value = entity.childId
            branchId.value = entity.branchId
            folderId.value = entity.folderId
            unlockKind.value = UnlockRuleKind.from(entity.unlockRuleRawValue)
            unlockDate.value = entity.unlockDate
            unlockAgeYears.value = entity.unlockAgeYears
            lifeEventName.value = entity.lifeEventName
            isFavorite.value = entity.isFavorite
        }
    }

    fun applyMilestone(milestone: MilestoneTemplate) {
        title.value = milestone.title
        body.value = milestone.body
        unlockKind.value = milestone.unlockKind
        if (milestone.unlockKind == UnlockRuleKind.BIRTHDAY_AGE) {
            unlockAgeYears.value = milestone.unlockAge ?: 5
        } else if (milestone.unlockKind == UnlockRuleKind.LIFE_EVENT) {
            lifeEventName.value = milestone.lifeEventName ?: ""
        }
        selectedMilestone.value = null
    }

    fun addPendingAttachment(attachment: PendingAttachment) {
        pendingAttachments.value = pendingAttachments.value + attachment
    }

    fun removePendingAttachment(id: String) {
        pendingAttachments.value = pendingAttachments.value.filterNot { it.id == id }
    }

    /**
     * Save (draft or sealed). FAILURE POLICY:
     *  - keep the editor open and preserve all input
     *  - surface a meaningful error (never silently swallowed)
     *  - do NOT create a duplicate on retry (sessionLetterId is stable)
     *  - no analytics success is reported on failure
     */
    fun save(seal: Boolean, onSaved: () -> Unit) {
        val id = sessionLetterId ?: return
        if (title.value.isBlank() && body.value.isBlank()) {
            saveError.value = "Add a title or some words before saving."
            return
        }
        viewModelScope.launch {
            isSaving.value = true
            saveError.value = null
            try {
                val now = System.currentTimeMillis()
                val existing = loadedLetter
                val sealedAt = when {
                    seal -> existing?.sealedAt ?: now
                    else -> null // editing a draft keeps it a draft
                }
                val entity = LetterEntity(
                    id = id,
                    childId = childId.value,
                    branchId = branchId.value,
                    folderId = if (branchId.value == null) null else folderId.value,
                    authorMemberId = existing?.authorMemberId,
                    title = title.value.trim(),
                    body = body.value,
                    authorName = authorName.value.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    sealedAt = sealedAt,
                    isFavorite = isFavorite.value,
                    isDraft = sealedAt == null,
                    unlockRuleRawValue = unlockKind.value.raw,
                    unlockDate = unlockDate.value,
                    unlockAgeYears = unlockAgeYears.value,
                    lifeEventName = lifeEventName.value,
                    manuallyReleasedAt = existing?.manuallyReleasedAt
                )
                val ok = withContext(Dispatchers.IO) {
                    runCatching { letterDao.insert(entity) }.isSuccess
                }
                if (!ok) {
                    saveError.value = "Could not save this letter. Your text is safe — please try again."
                    return@launch
                }
                // Persist pending attachments (cascade on letter delete).
                pendingAttachments.value.forEach { att ->
                    runCatching {
                        attachmentDao.insert(
                            AttachmentEntity(
                                id = att.id,
                                letterId = id,
                                fileName = att.fileName,
                                contentType = att.contentType,
                                kind = att.kind,
                                data = att.data,
                                createdAt = now
                            )
                        )
                    }.onFailure {
                        saveError.value = "Saved, but one attachment failed: ${it.message}"
                    }
                }
                pendingAttachments.value = emptyList()
                loadedLetter = entity
                onSaved()
            } catch (e: Exception) {
                saveError.value = "Save failed: ${e.message}"
            } finally {
                isSaving.value = false
            }
        }
    }

    fun clearError() { saveError.value = null }

    /** Whether the loaded entity is still a draft (sealedAt == null). */
    fun isDraft(): Boolean = loadedLetter?.sealedAt == null

    companion object {
        val milestoneTemplates = Milestones.all
    }
}

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val contentType: String,
    val kind: String,
    val data: ByteArray
)