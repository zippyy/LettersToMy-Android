package com.letters2my.app.domain

/**
 * Canonical Letter lifecycle status. Mirrors iOS `LetterStatus`
 * (Sources/LettersToMy/Models/Letter.swift).
 */
enum class LetterStatus(val raw: String, val title: String) {
    DRAFT("draft", "Draft"),
    SCHEDULED("scheduled", "Scheduled"),
    UNLOCKED("unlocked", "Unlocked");

    companion object {
        fun from(raw: String?): LetterStatus =
            entries.firstOrNull { it.raw == raw } ?: DRAFT
    }
}

/**
 * Pure status calculation, ported from the iOS `Letter` managed object:
 *
 * - isDraft: sealedAt == null
 * - status: draft -> DRAFT; else unlocked(now) ? UNLOCKED : SCHEDULED
 * - isUnlocked uses the Letter's OWN child's birth date for birthdayAge rules.
 */
object LetterStatusCalculator {

    fun isDraft(sealedAtEpochMs: Long?): Boolean = sealedAtEpochMs == null

    fun isUnlocked(
        sealedAtEpochMs: Long?,
        unlockRuleRaw: String?,
        unlockDateEpochMs: Long?,
        unlockAgeYears: Int?,
        lifeEventName: String?,
        manuallyReleasedAtEpochMs: Long?,
        childBirthDateEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (sealedAtEpochMs == null) return false // drafts are never unlocked
        val resolved = UnlockRule.resolveDateMs(
            unlockRuleRaw = unlockRuleRaw,
            unlockDateEpochMs = unlockDateEpochMs,
            unlockAgeYears = unlockAgeYears,
            lifeEventName = lifeEventName,
            manuallyReleasedAtEpochMs = manuallyReleasedAtEpochMs,
            childBirthDateEpochMs = childBirthDateEpochMs
        ) ?: return false
        return resolved <= nowEpochMs
    }

    fun status(
        sealedAtEpochMs: Long?,
        unlockRuleRaw: String?,
        unlockDateEpochMs: Long?,
        unlockAgeYears: Int?,
        lifeEventName: String?,
        manuallyReleasedAtEpochMs: Long?,
        childBirthDateEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): LetterStatus {
        if (isDraft(sealedAtEpochMs)) return LetterStatus.DRAFT
        return if (isUnlocked(
                sealedAtEpochMs, unlockRuleRaw, unlockDateEpochMs, unlockAgeYears,
                lifeEventName, manuallyReleasedAtEpochMs, childBirthDateEpochMs, nowEpochMs
            )
        ) LetterStatus.UNLOCKED else LetterStatus.SCHEDULED
    }
}