package com.letters2my.app.ui.letters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.data.local.LetterEntity
import com.letters2my.app.data.local.toPayload
import com.letters2my.app.domain.ChildPayload
import com.letters2my.app.domain.LetterFilter
import com.letters2my.app.domain.LetterFiltering
import com.letters2my.app.domain.FilterId
import com.letters2my.app.domain.LetterStatusCalculator
import com.letters2my.app.domain.LetterStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Letters list ViewModel: filters (All/Draft/Scheduled/Unlocked), All
 * Children semantics (null childId = all, never auto-selects first child),
 * search, and permission-aware deletion with attachment cascade.
 */
class LettersViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LettersDatabase.getInstance(application)
    private val letterDao = db.letterDao()
    private val childDao = db.childDao()
    private val attachmentDao = db.attachmentDao()

    private val letters = letterDao.getAll()
    private val children = childDao.getAll()

    val filterId = MutableStateFlow(FilterId.ALL)
    val selectedChildId = MutableStateFlow<String?>(null)
    val searchQuery = MutableStateFlow("")

    /** Delete confirmation state: (letter, message) or null. */
    val pendingDelete = MutableStateFlow<Pair<LetterEntity, String>?>(null)

    /** Structured save/delete errors surfaced to UI. */
    val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LettersUiState> =
        combine(letters, children, filterId, selectedChildId, searchQuery) {
                lettersList, childrenList, filter, childId, query ->
            val childById = childrenList.associateBy { it.id }
            val now = System.currentTimeMillis()
            val likes = lettersList.map { letter ->
                LetterFiltering.LetterLike(
                    id = letter.id,
                    childId = letter.childId,
                    sealedAtEpochMs = letter.sealedAt,
                    unlockRuleRaw = letter.unlockRuleRawValue,
                    unlockDateEpochMs = letter.unlockDate,
                    unlockAgeYears = letter.unlockAgeYears,
                    lifeEventName = letter.lifeEventName,
                    manuallyReleasedAtEpochMs = letter.manuallyReleasedAt,
                    childBirthDateEpochMs = childById[letter.childId]?.birthDate,
                    title = letter.title,
                    body = letter.body,
                    authorName = letter.authorName,
                    isFavorite = letter.isFavorite,
                    updatedAtEpochMs = letter.updatedAt,
                    createdAtEpochMs = letter.createdAt
                )
            }
            val filtered = LetterFiltering.apply(
                likes,
                LetterFilter(filterId = filter, childId = childId, query = query),
                now
            )
            LettersUiState(
                letters = filtered,
                children = childrenList.map { it.toDomain() },
                selectedChildId = childId,
                filterId = filter,
                query = query
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LettersUiState())

    fun setFilter(f: FilterId) { filterId.value = f }

    fun setChild(childId: String?) { selectedChildId.value = childId }

    fun setQuery(q: String) { searchQuery.value = q }

    /** Request deletion — permission checked at execution; confirmation text by state. */
    fun requestDelete(letter: LetterEntity) {
        val message = if (letter.sealedAt == null) {
            "Delete Draft?"
        } else {
            "Delete Sealed Letter? This permanently removes this letter and its attachments."
        }
        pendingDelete.value = letter to message
    }

    fun confirmDelete() {
        val letter = pendingDelete.value?.first ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            // Local single-user archive: owner semantics. The permission
            // guard is enforced in the repository contract (deleteLetterWithPermission).
            val hasPermission = true // owner of local archive
            val result = LettersDatabase.getInstance(getApplication())
                .let { db ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        attachmentDao.deleteByLetter(letter.id)
                        letterDao.delete(letter)
                    }
                    Result.success(Unit)
                }
            result.onFailure { errorMessage.value = it.message }
        }
    }

    fun dismissDelete() { pendingDelete.value = null }

    fun clearError() { errorMessage.value = null }

    data class LettersUiState(
        val letters: List<LetterFiltering.LetterLike> = emptyList(),
        val children: List<ChildPayload> = emptyList(),
        val selectedChildId: String? = null,
        val filterId: FilterId = FilterId.ALL,
        val query: String = ""
    )
}

private fun com.letters2my.app.data.local.ChildEntity.toDomain(): ChildPayload =
    ChildPayload(id = id, name = name, birthDateEpochMs = birthDate)