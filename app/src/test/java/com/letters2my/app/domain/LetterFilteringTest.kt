package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Letter list filtering — the critical invariant: `childId == null` means
 * ALL CHILDREN and must NEVER silently become the first child.
 */
class LetterFilteringTest {

    private val now = 1_700_000_000_000L
    private val future = now + 86_400_000L * 365 * 5
    private val past = now - 86_400_000L * 30

    private val emma = "child-emma"
    private val noah = "child-noah"

    private fun letter(
        id: String,
        childId: String?,
        title: String = "",
        body: String = "",
        author: String = "",
        sealed: Long? = null,
        rule: String = "specificDate",
        unlockDate: Long? = null,
        unlockAgeYears: Int? = null,
        updated: Long = 0L
    ) = LetterFiltering.LetterLike(
        id = id, childId = childId,
        sealedAtEpochMs = sealed, unlockRuleRaw = rule,
        unlockDateEpochMs = unlockDate, unlockAgeYears = unlockAgeYears,
        lifeEventName = "", manuallyReleasedAtEpochMs = null,
        childBirthDateEpochMs = null,
        title = title, body = body, authorName = author,
        updatedAtEpochMs = updated, createdAtEpochMs = updated
    )

    @Test
    fun `ALL returns every letter`() {
        val letters = listOf(
            letter("d", emma, sealed = null, updated = 1),
            letter("s", emma, sealed = past, rule = "specificDate", unlockDate = future, updated = 2),
            letter("u", noah, sealed = past, rule = "specificDate", unlockDate = past, updated = 3)
        )
        val all = LetterFiltering.apply(letters, LetterFilter(FilterId.ALL), now)
        assertEquals(3, all.size)
    }

    @Test
    fun `DRAFT filter returns only drafts`() {
        val letters = listOf(
            letter("d", emma, sealed = null, updated = 1),
            letter("s", emma, sealed = past, rule = "specificDate", unlockDate = future, updated = 2),
            letter("u", noah, sealed = past, rule = "specificDate", unlockDate = past, updated = 3)
        )
        val drafts = LetterFiltering.apply(letters, LetterFilter(FilterId.DRAFT), now)
        assertEquals(listOf("d"), drafts.map { it.id })
    }

    @Test
    fun `SCHEDULED filter returns only scheduled`() {
        val letters = listOf(
            letter("d", emma, sealed = null, updated = 1),
            letter("s", emma, sealed = past, rule = "specificDate", unlockDate = future, updated = 2),
            letter("u", noah, sealed = past, rule = "specificDate", unlockDate = past, updated = 3),
            letter("b", noah, sealed = past, rule = "birthdayAge", unlockAgeYears = 5,
                // no birth date -> cannot resolve -> SCHEDULED
                updated = 4)
        )
        // note: letter() helper has no birth date param; override:
        val b = letters.last().copy(childBirthDateEpochMs = null)
        val scheduled = LetterFiltering.apply(
            letters.map { if (it.id == "b") b else it },
            LetterFilter(FilterId.SCHEDULED), now
        )
        assertEquals(listOf("s", "b"), scheduled.map { it.id })
    }

    @Test
    fun `UNLOCKED filter returns only unlocked`() {
        val letters = listOf(
            letter("d", emma, sealed = null, updated = 1),
            letter("s", emma, sealed = past, rule = "specificDate", unlockDate = future, updated = 2),
            letter("u", noah, sealed = past, rule = "specificDate", unlockDate = past, updated = 3)
        )
        val unlocked = LetterFiltering.apply(letters, LetterFilter(FilterId.UNLOCKED), now)
        assertEquals(listOf("u"), unlocked.map { it.id })
    }

    @Test
    fun `null childId means ALL CHILDREN - never the first child`() {
        // Emma is "first" (list order); Noah's letters must still show when
        // childId == null.
        val letters = listOf(
            letter("emma-letter", emma, title = "To Emma", updated = 5),
            letter("noah-letter", noah, title = "To Noah", updated = 6)
        )
        val all = LetterFiltering.apply(letters, LetterFilter(FilterId.ALL, childId = null), now)
        assertEquals(2, all.size)
        assertTrue(all.map { it.id }.containsAll(listOf("emma-letter", "noah-letter")))
    }

    @Test
    fun `explicit child restricts to that child only`() {
        val letters = listOf(
            letter("emma-letter", emma, updated = 5),
            letter("noah-letter", noah, updated = 6)
        )
        val onlyNoah = LetterFiltering.apply(letters, LetterFilter(FilterId.ALL, childId = noah), now)
        assertEquals(listOf("noah-letter"), onlyNoah.map { it.id })
    }

    @Test
    fun `search matches title`() {
        val letters = listOf(
            letter("1", emma, title = "Graduation Day", updated = 2),
            letter("2", emma, title = "Camping Trip", updated = 1)
        )
        assertEquals(listOf("1"), LetterFiltering.apply(letters, LetterFilter(query = "gradu"), now).map { it.id })
    }

    @Test
    fun `search matches body case-insensitively`() {
        val letters = listOf(
            letter("1", emma, body = "I love you so much", updated = 2),
            letter("2", emma, body = "Remember the beach", updated = 1)
        )
        assertEquals(listOf("1"), LetterFiltering.apply(letters, LetterFilter(query = "LOVE"), now).map { it.id })
    }

    @Test
    fun `search matches author`() {
        val letters = listOf(
            letter("1", emma, author = "Dad", updated = 2),
            letter("2", emma, author = "Mom", updated = 1)
        )
        assertEquals(listOf("1"), LetterFiltering.apply(letters, LetterFilter(query = "dad"), now).map { it.id })
    }

    @Test
    fun `combination of status plus child plus search`() {
        val letters = listOf(
            // Noah, unlocked, titled "Beach Day" — should match.
            letter("u-noah", noah, title = "Beach Day", sealed = past,
                rule = "specificDate", unlockDate = past, updated = 3),
            // Noah, scheduled, titled "Beach Day" — status mismatch.
            letter("s-noah", noah, title = "Beach Day", sealed = past,
                rule = "specificDate", unlockDate = future, updated = 2),
            // Emma, unlocked, titled "Beach Day" — child mismatch.
            letter("u-emma", emma, title = "Beach Day", sealed = past,
                rule = "specificDate", unlockDate = past, updated = 1),
            // Noah, unlocked, different title — search mismatch.
            letter("u-other", noah, title = "Library", sealed = past,
                rule = "specificDate", unlockDate = past, updated = 4)
        )
        val result = LetterFiltering.apply(
            letters,
            LetterFilter(filterId = FilterId.UNLOCKED, childId = noah, query = "beach"),
            now
        )
        assertEquals(listOf("u-noah"), result.map { it.id })
    }

    @Test
    fun `sortByUpdated sorts most recent first`() {
        val letters = listOf(
            letter("old", emma, updated = 100),
            letter("new", noah, updated = 300),
            letter("mid", emma, updated = 200)
        )
        assertEquals(listOf("new", "mid", "old"), LetterFiltering.sortByUpdated(letters).map { it.id })
    }

    @Test
    fun `matchesLetter with blank query does not filter by search`() {
        val l = letter("1", emma, title = "x", updated = 1)
        assertTrue(LetterFiltering.matchesLetter(l, LetterFilter(FilterId.ALL, query = "   "), now))
        assertTrue(LetterFiltering.matchesLetter(l, LetterFilter(FilterId.ALL, query = ""), now))
    }

    @Test
    fun `statusOf uses letters own child birth date`() {
        // Birthday-age letter for Emma (born 10y ago, age 5 -> unlocked) —
        // must NOT inherit Noah's (different) birth date.
        val emmaBirth = now - 86_400_000L * 365 * 10
        val l = letter("b", emma, sealed = past, rule = "birthdayAge", unlockAgeYears = 5)
            .copy(childBirthDateEpochMs = emmaBirth)
        assertEquals(LetterStatus.UNLOCKED, LetterFiltering.statusOf(l, now))
        // Same letter WITHOUT a resolvable birth date -> SCHEDULED (safe).
        assertEquals(LetterStatus.SCHEDULED, LetterFiltering.statusOf(l.copy(childBirthDateEpochMs = null), now))
    }

    @Test
    fun `draft is never considered unlocked`() {
        val l = letter("d", emma, sealed = null, rule = "specificDate", unlockDate = past, updated = 1)
        assertFalse(LetterFiltering.statusOf(l, now) == LetterStatus.UNLOCKED)
    }
}