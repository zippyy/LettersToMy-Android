package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Letter lifecycle status — ported semantics MUST match iOS
 * (Sources/LettersToMy/Models/Letter.swift + UnlockRule.swift).
 */
class LetterStatusCalculatorTest {

    // Fixed reference "now" so tests are deterministic.
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val future = now + 86_400_000L * 365 * 5  // +5y
    private val past = now - 86_400_000L * 30          // -30d

    // ── Draft ─────────────────────────────────────────────
    @Test
    fun `unsealed letter is DRAFT`() {
        assertEquals(
            LetterStatus.DRAFT,
            LetterStatusCalculator.status(
                sealedAtEpochMs = null, unlockRuleRaw = "specificDate",
                unlockDateEpochMs = future, unlockAgeYears = null,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    // ── Specific date ─────────────────────────────────────
    @Test
    fun `sealed future specific date is SCHEDULED`() {
        assertEquals(
            LetterStatus.SCHEDULED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "specificDate",
                unlockDateEpochMs = future, unlockAgeYears = null,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    @Test
    fun `sealed reached specific date is UNLOCKED`() {
        assertEquals(
            LetterStatus.UNLOCKED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "specificDate",
                unlockDateEpochMs = past, unlockAgeYears = null,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    @Test
    fun `date exactly now is UNLOCKED`() {
        assertEquals(
            LetterStatus.UNLOCKED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "specificDate",
                unlockDateEpochMs = now, unlockAgeYears = null,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    // ── Birthday age ──────────────────────────────────────
    @Test
    fun `birthday age in future is SCHEDULED`() {
        // Child born 10 years before "now" -> age rule 5 already reached;
        // use a child born 1 year ago with age 5 -> SCHEDULED.
        val birthOneYearAgo = now - 86_400_000L * 365
        assertEquals(
            LetterStatus.SCHEDULED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "birthdayAge",
                unlockDateEpochMs = null, unlockAgeYears = 5,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = birthOneYearAgo, nowEpochMs = now
            )
        )
    }

    @Test
    fun `birthday age reached is UNLOCKED`() {
        // Child born 6 years ago -> age 5 reached.
        val birthSixYearsAgo = now - 86_400_000L * 365 * 6
        assertEquals(
            LetterStatus.UNLOCKED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "birthdayAge",
                unlockDateEpochMs = null, unlockAgeYears = 5,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = birthSixYearsAgo, nowEpochMs = now
            )
        )
    }

    @Test
    fun `birthday age with missing birth date stays SCHEDULED`() {
        // iOS: resolveDateMs returns nil (cannot compute the target date),
        // so a sealed letter is never unlocked -> SCHEDULED. Deterministic.
        assertEquals(
            LetterStatus.SCHEDULED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "birthdayAge",
                unlockDateEpochMs = null, unlockAgeYears = 5,
                lifeEventName = "", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
        // UnlockRule.resolveDateMs is null and isUnlocked is false.
        assertNull(
            UnlockRule.resolveDateMs("birthdayAge", null, 5, "", null, null)
        )
        assertFalse(
            UnlockRule.isUnlocked("birthdayAge", null, 5, "", null, null, now)
        )
    }

    // ── Life event ────────────────────────────────────────
    @Test
    fun `life event not released stays SCHEDULED`() {
        assertEquals(
            LetterStatus.SCHEDULED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "lifeEvent",
                unlockDateEpochMs = null, unlockAgeYears = null,
                lifeEventName = "Graduation", manuallyReleasedAtEpochMs = null,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    @Test
    fun `life event manually released is UNLOCKED`() {
        assertEquals(
            LetterStatus.UNLOCKED,
            LetterStatusCalculator.status(
                sealedAtEpochMs = past, unlockRuleRaw = "lifeEvent",
                unlockDateEpochMs = null, unlockAgeYears = null,
                lifeEventName = "Graduation",
                manuallyReleasedAtEpochMs = past,
                childBirthDateEpochMs = null, nowEpochMs = now
            )
        )
    }

    // ── UnlockRule.resolveDateMs ──────────────────────────
    @Test
    fun `resolveDateMs specificDate is passthrough`() {
        assertEquals(future, UnlockRule.resolveDateMs("specificDate", future, null, "", null, null))
    }

    @Test
    fun `resolveDateMs birthdayAge adds calendar years`() {
        // 2020-01-01 + 5 years = 2025-01-01 (calendar-year arithmetic, NOT
        // 365*5 days — matches Swift Calendar.date(byAdding: .year)).
        val birth = 1577836800000L // 2020-01-01T00:00:00Z
        assertEquals(1735689600000L, UnlockRule.resolveDateMs("birthdayAge", null, 5, "", null, birth))
    }

    @Test
    fun `resolveDateMs lifeEvent returns release timestamp`() {
        assertEquals(past, UnlockRule.resolveDateMs("lifeEvent", null, null, "Wedding", past, null))
        assertNull(UnlockRule.resolveDateMs("lifeEvent", null, null, "Wedding", null, null))
    }

    @Test
    fun `unknown raw rule resolves as specificDate`() {
        // Unknown raw values must degrade safely, not crash.
        assertEquals(future, UnlockRule.resolveDateMs("banana", future, null, "", null, null))
    }

    @Test
    fun `summary text for each rule`() {
        assertEquals("Age 5", UnlockRule.summary("birthdayAge", null, 5, "", null, null))
        assertEquals("Graduation", UnlockRule.summary("lifeEvent", null, null, "Graduation", null, null))
        assertEquals("Released", UnlockRule.summary("lifeEvent", null, null, "", past, null))
        assertEquals("Choose an unlock date", UnlockRule.summary("specificDate", null, null, "", null, null))
    }
}