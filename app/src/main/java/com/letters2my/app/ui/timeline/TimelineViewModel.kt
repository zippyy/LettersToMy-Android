package com.letters2my.app.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.letters2my.app.data.local.LettersDatabase
import com.letters2my.app.domain.LetterFiltering
import com.letters2my.app.domain.LetterStatus
import com.letters2my.app.domain.UnlockRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Timeline: sealed/non-draft letters in unlock order. Supports All
 * recipients/children (null = all) and explicit child filter — never
 * auto-selects the first child. Per-letter status resolution uses the
 * letter's OWN child's birth date.
 */
class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LettersDatabase.getInstance(application)

    val selectedChildId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TimelineUiState> =
        combine(db.letterDao().getAll(), db.childDao().getAll(), selectedChildId) { letters, children, childId ->
            val childById = children.associateBy { it.id }
            val now = System.currentTimeMillis()

            val entries = letters
                .filter { it.sealedAt != null } // Timeline shows sealed/non-draft only
                .filter { childId == null || it.childId == childId }
                .mapNotNull { letter ->
                    val birth = childById[letter.childId]?.birthDate
                    val status = LetterFiltering.statusOf(
                        LetterFiltering.LetterLike(
                            id = letter.id,
                            childId = letter.childId,
                            sealedAtEpochMs = letter.sealedAt,
                            unlockRuleRaw = letter.unlockRuleRawValue,
                            unlockDateEpochMs = letter.unlockDate,
                            unlockAgeYears = letter.unlockAgeYears,
                            lifeEventName = letter.lifeEventName,
                            manuallyReleasedAtEpochMs = letter.manuallyReleasedAt,
                            childBirthDateEpochMs = birth,
                            title = letter.title,
                            body = letter.body,
                            authorName = letter.authorName
                        ),
                        now
                    )
                    val unlockMs = UnlockRule.resolveDateMs(
                        unlockRuleRaw = letter.unlockRuleRawValue,
                        unlockDateEpochMs = letter.unlockDate,
                        unlockAgeYears = letter.unlockAgeYears,
                        lifeEventName = letter.lifeEventName,
                        manuallyReleasedAtEpochMs = letter.manuallyReleasedAt,
                        childBirthDateEpochMs = birth
                    ) ?: return@mapNotNull null
                    TimelineEntry(
                        letterId = letter.id,
                        title = letter.title.ifEmpty { "Untitled" },
                        childId = letter.childId,
                        childName = childById[letter.childId]?.name ?: "Unknown child",
                        authorName = letter.authorName,
                        unlockAt = unlockMs,
                        status = status
                    )
                }
                .sortedBy { it.unlockAt } // unlock order

            TimelineUiState(
                entries = entries,
                children = children.map { it.id to it.name },
                selectedChildId = childId
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun setChild(childId: String?) { selectedChildId.value = childId }
}

data class TimelineEntry(
    val letterId: String,
    val title: String,
    val childId: String?,
    val childName: String,
    val authorName: String,
    val unlockAt: Long,
    val status: LetterStatus
)

data class TimelineUiState(
    val entries: List<TimelineEntry> = emptyList(),
    val children: List<Pair<String, String>> = emptyList(),
    val selectedChildId: String? = null
)