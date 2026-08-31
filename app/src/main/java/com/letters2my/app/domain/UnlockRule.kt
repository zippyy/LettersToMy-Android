package com.letters2my.app.domain

import java.util.Calendar
import java.util.TimeZone

/**
 * Unlock rules. Ported from iOS `LetterUnlockSchedule`
 * (Sources/LettersToMyCore/UnlockRule.swift).
 *
 * Raw values MUST match iOS exactly — they cross the wire in
 * `.letterstomy` archives.
 */
enum class UnlockRuleKind(val raw: String) {
    SPECIFIC_DATE("specificDate"),
    BIRTHDAY_AGE("birthdayAge"),
    LIFE_EVENT("lifeEvent");

    companion object {
        fun from(raw: String?): UnlockRuleKind =
            entries.firstOrNull { it.raw == raw } ?: SPECIFIC_DATE
    }
}

object UnlockRule {

    /**
     * Resolve the effective unlock date in epoch millis, or null if the rule
     * is not fully specified.
     *
     * - specificDate: the chosen date
     * - birthdayAge: child birth date + ageInYears (calendar-year arithmetic,
     *   matching Swift `Calendar.date(byAdding: .year)`)
     * - lifeEvent: manuallyReleasedAt (remains null until explicitly released)
     */
    fun resolveDateMs(
        unlockRuleRaw: String?,
        unlockDateEpochMs: Long?,
        unlockAgeYears: Int?,
        lifeEventName: String?,
        manuallyReleasedAtEpochMs: Long?,
        childBirthDateEpochMs: Long?
    ): Long? {
        return when (UnlockRuleKind.from(unlockRuleRaw)) {
            UnlockRuleKind.SPECIFIC_DATE -> unlockDateEpochMs
            UnlockRuleKind.BIRTHDAY_AGE -> {
                val birth = childBirthDateEpochMs ?: return null
                val age = unlockAgeYears ?: return null
                addYears(birth, age)
            }
            UnlockRuleKind.LIFE_EVENT -> manuallyReleasedAtEpochMs
        }
    }

    fun isUnlocked(
        unlockRuleRaw: String?,
        unlockDateEpochMs: Long?,
        unlockAgeYears: Int?,
        lifeEventName: String?,
        manuallyReleasedAtEpochMs: Long?,
        childBirthDateEpochMs: Long?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        val resolved = resolveDateMs(
            unlockRuleRaw, unlockDateEpochMs, unlockAgeYears,
            lifeEventName, manuallyReleasedAtEpochMs, childBirthDateEpochMs
        ) ?: return false
        return resolved <= nowEpochMs
    }

    fun summary(
        unlockRuleRaw: String?,
        unlockDateEpochMs: Long?,
        unlockAgeYears: Int?,
        lifeEventName: String?,
        manuallyReleasedAtEpochMs: Long?,
        childBirthDateEpochMs: Long?
    ): String {
        return when (UnlockRuleKind.from(unlockRuleRaw)) {
            UnlockRuleKind.SPECIFIC_DATE -> {
                val d = unlockDateEpochMs ?: return "Choose an unlock date"
                formatDate(d)
            }
            UnlockRuleKind.BIRTHDAY_AGE -> {
                val age = unlockAgeYears ?: return "Choose a birthday"
                "Age $age"
            }
            UnlockRuleKind.LIFE_EVENT -> {
                if (manuallyReleasedAtEpochMs != null) return "Released"
                if (!lifeEventName.isNullOrEmpty()) lifeEventName else "Future life event"
            }
        }
    }

    private fun addYears(epochMs: Long, years: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMs
            add(Calendar.YEAR, years)
        }
        return cal.timeInMillis
    }

    private fun formatDate(epochMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val month = arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")[cal.get(Calendar.MONTH)]
        return "$month ${cal.get(Calendar.DAY_OF_MONTH)}, ${cal.get(Calendar.YEAR)}"
    }
}