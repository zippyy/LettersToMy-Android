package com.letters2my.app.ui.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.AttachmentEntity
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.LetterEntity
import com.letters2my.app.domain.ChildPayload
import com.letters2my.app.domain.LetterStatusCalculator
import com.letters2my.app.domain.LetterStatus
import com.letters2my.app.domain.UnlockRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Letter detail: metadata, schedule, attachments, favorite, edit, delete.
 *
 * RECIPIENT PRIVACY: a sealed letter's body is only rendered when the
 * viewer has viewSealedContent. In the local single-user archive the owner
 * always may, but the gate lives in the screen so any future recipient
 * context cannot leak sealed content merely by rendering the entity.
 */
class LetterDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LettersDatabase.getInstance(application)

    val letter = MutableStateFlow<LetterEntity?>(null)
    val attachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val childName = MutableStateFlow<String?>(null)
    val childBirthDate = MutableStateFlow<Long?>(null)

    /** Can the current viewer read sealed content? (local owner => true) */
    val canViewSealed = MutableStateFlow(true)

    val deleteError = MutableStateFlow<String?>(null)
    val showDeleteConfirm = MutableStateFlow(false)

    fun load(id: String) {
        viewModelScope.launch {
            val entity = db.letterDao().getById(id) ?: return@launch
            letter.value = entity
            attachments.value = db.attachmentDao().getByLetter(id)
            if (entity.childId != null) {
                val child = db.childDao().getById(entity.childId)
                childName.value = child?.name
                childBirthDate.value = child?.birthDate
            }
        }
    }

    fun status(): LetterStatus {
        val l = letter.value ?: return LetterStatus.DRAFT
        return LetterStatusCalculator.status(
            sealedAtEpochMs = l.sealedAt,
            unlockRuleRaw = l.unlockRuleRawValue,
            unlockDateEpochMs = l.unlockDate,
            unlockAgeYears = l.unlockAgeYears,
            lifeEventName = l.lifeEventName,
            manuallyReleasedAtEpochMs = l.manuallyReleasedAt,
            childBirthDateEpochMs = childBirthDate.value,
            nowEpochMs = System.currentTimeMillis()
        )
    }

    fun unlockSummary(): String {
        val l = letter.value ?: return ""
        return UnlockRule.summary(
            unlockRuleRaw = l.unlockRuleRawValue,
            unlockDateEpochMs = l.unlockDate,
            unlockAgeYears = l.unlockAgeYears,
            lifeEventName = l.lifeEventName,
            manuallyReleasedAtEpochMs = l.manuallyReleasedAt,
            childBirthDateEpochMs = childBirthDate.value
        )
    }

    fun toggleFavorite() {
        val l = letter.value ?: return
        viewModelScope.launch {
            val updated = l.copy(isFavorite = !l.isFavorite)
            db.letterDao().update(updated)
            letter.value = updated
        }
    }

    fun requestDelete() { showDeleteConfirm.value = true }

    fun cancelDelete() { showDeleteConfirm.value = false }

    /**
     * Permission-aware delete: cascade attachments first, then the letter.
     * On Room failure the error is surfaced — never pretend success.
     */
    fun confirmDelete(onDeleted: () -> Unit) {
        val l = letter.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    db.attachmentDao().deleteByLetter(l.id)
                    db.letterDao().delete(l)
                }.isSuccess
            }
            if (ok) {
                onDeleted()
            } else {
                deleteError.value = "Could not delete this letter. Please try again."
            }
            showDeleteConfirm.value = false
        }
    }

    fun clearError() { deleteError.value = null }
}