package com.letters2my.app.domain

/**
 * Letter list filters, matching iOS: All Letters, Draft, Scheduled, Unlocked,
 * All Children (null child), explicit child, search.
 *
 * FilterId semantics:
 *  - ALL: every non-deleted letter
 *  - DRAFT / SCHEDULED / UNLOCKED: status-based
 *  - CHILD: requires childId (an explicit child); null childId means All Children
 *  - SEARCH: title/body/author substring, case-insensitive
 */
enum class FilterId(val raw: String) {
    ALL("all"),
    DRAFT("draft"),
    SCHEDULED("scheduled"),
    UNLOCKED("unlocked"),
    CHILD("child"),
    SEARCH("search");

    companion object {
        fun from(raw: String?): FilterId = entries.firstOrNull { it.raw == raw } ?: ALL
    }
}

data class LetterFilter(
    val filterId: FilterId = FilterId.ALL,
    val childId: String? = null,   // null = All Children
    val query: String = ""         // search term
)

/**
 * Pure, testable filtering logic. A filter determines what the UI displays;
 * it must NEVER determine what goes into a full encrypted backup.
 */
object LetterFiltering {

    data class LetterLike(
        val id: String,
        val childId: String?,
        val sealedAtEpochMs: Long?,
        val unlockRuleRaw: String?,
        val unlockDateEpochMs: Long?,
        val unlockAgeYears: Int?,
        val lifeEventName: String?,
        val manuallyReleasedAtEpochMs: Long?,
        val childBirthDateEpochMs: Long?,
        val title: String,
        val body: String,
        val authorName: String,
        val isFavorite: Boolean = false,
        val updatedAtEpochMs: Long = 0L,
        val createdAtEpochMs: Long = 0L
    )

    /**
     * Resolve a letter's status against its OWN child's birth date and the
     * reference "now". Never substitutes the first child.
     */
    fun statusOf(letter: LetterLike, nowEpochMs: Long = System.currentTimeMillis()): LetterStatus =
        LetterStatusCalculator.status(
            sealedAtEpochMs = letter.sealedAtEpochMs,
            unlockRuleRaw = letter.unlockRuleRaw,
            unlockDateEpochMs = letter.unlockDateEpochMs,
            unlockAgeYears = letter.unlockAgeYears,
            lifeEventName = letter.lifeEventName,
            manuallyReleasedAtEpochMs = letter.manuallyReleasedAtEpochMs,
            childBirthDateEpochMs = letter.childBirthDateEpochMs,
            nowEpochMs = nowEpochMs
        )

    /**
     * All Children semantics: when childId is null, every child's letters
     * are shown. An explicit childId restricts to that child only.
     */
    fun matchesLetter(
        letter: LetterLike,
        filter: LetterFilter,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        // Status-based filters
        when (filter.filterId) {
            FilterId.DRAFT -> {
                if (statusOf(letter, nowEpochMs) != LetterStatus.DRAFT) return false
            }
            FilterId.SCHEDULED -> {
                if (statusOf(letter, nowEpochMs) != LetterStatus.SCHEDULED) return false
            }
            FilterId.UNLOCKED -> {
                if (statusOf(letter, nowEpochMs) != LetterStatus.UNLOCKED) return false
            }
            else -> {}
        }

        // Child filter: explicit child restricts; All Children does not.
        filter.childId?.let { requested ->
            if (letter.childId != requested) return false
        }

        // Search
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim().lowercase()
            val haystack = listOf(letter.title, letter.body, letter.authorName)
                .joinToString(" ").lowercase()
            if (!haystack.contains(q)) return false
        }

        return true
    }

    fun apply(
        letters: List<LetterLike>,
        filter: LetterFilter,
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<LetterLike> = letters.filter { matchesLetter(it, filter, nowEpochMs) }

    /** Sort: updatedAt descending (most recent first), like the iOS list. */
    fun sortByUpdated(letters: List<LetterLike>): List<LetterLike> =
        letters.sortedByDescending { it.updatedAtEpochMs }
}