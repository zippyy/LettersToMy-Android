package com.letters2my.app.domain

/**
 * Milestone Quick Start templates — ported EXACTLY from iOS
 * (Sources/LettersToMy/Views/LetterEditorView.swift `MilestoneTemplate`).
 * Do not invent a separate Android catalog.
 */
data class MilestoneTemplate(
    val title: String,
    val body: String,
    val unlockKind: UnlockRuleKind,
    val unlockAge: Int?,
    val lifeEventName: String?
)

object Milestones {
    val all: List<MilestoneTemplate> = listOf(
        MilestoneTemplate(
            title = "Your First Birthday",
            body = "Dear little one,\n\nHappy first birthday! You've grown so much this year…\n\n",
            unlockKind = UnlockRuleKind.BIRTHDAY_AGE,
            unlockAge = 1,
            lifeEventName = null
        ),
        MilestoneTemplate(
            title = "Starting School",
            body = "Today you start school. I remember when…\n\n",
            unlockKind = UnlockRuleKind.BIRTHDAY_AGE,
            unlockAge = 5,
            lifeEventName = null
        ),
        MilestoneTemplate(
            title = "Your 10th Birthday",
            body = "Double digits! You're growing up so fast…\n\n",
            unlockKind = UnlockRuleKind.BIRTHDAY_AGE,
            unlockAge = 10,
            lifeEventName = null
        ),
        MilestoneTemplate(
            title = "Sweet Sixteen",
            body = "Sixteen years old. I am so proud of the person you're becoming…\n\n",
            unlockKind = UnlockRuleKind.BIRTHDAY_AGE,
            unlockAge = 16,
            lifeEventName = null
        ),
        MilestoneTemplate(
            title = "Graduation Day",
            body = "Today you graduate. All those years of hard work…\n\n",
            unlockKind = UnlockRuleKind.LIFE_EVENT,
            unlockAge = null,
            lifeEventName = "Graduation"
        ),
        MilestoneTemplate(
            title = "Your Wedding Day",
            body = "On this beautiful day, as you start this new chapter…\n\n",
            unlockKind = UnlockRuleKind.LIFE_EVENT,
            unlockAge = null,
            lifeEventName = "Wedding"
        ),
        MilestoneTemplate(
            title = "Becoming a Parent",
            body = "Now you understand. The moment you held your own child…\n\n",
            unlockKind = UnlockRuleKind.LIFE_EVENT,
            unlockAge = null,
            lifeEventName = "Becoming a parent"
        ),
        MilestoneTemplate(
            title = "A Letter for When You Need It",
            body = "If you're reading this, you might be having a hard day…\n\n",
            unlockKind = UnlockRuleKind.LIFE_EVENT,
            unlockAge = null,
            lifeEventName = "Encouragement"
        )
    )
}