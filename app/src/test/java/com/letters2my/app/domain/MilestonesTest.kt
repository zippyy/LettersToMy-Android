package com.letters2my.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Milestone Quick Start templates — must match iOS EXACTLY
 * (Sources/LettersToMy/Views/LetterEditorView.swift `MilestoneTemplate.all`).
 * This is a parity contract: the Android catalog may not drift.
 */
class MilestonesTest {

    @Test
    fun `exactly 8 templates`() {
        assertEquals(8, Milestones.all.size)
    }

    @Test
    fun `titles match iOS in order`() {
        assertEquals(
            listOf(
                "Your First Birthday",
                "Starting School",
                "Your 10th Birthday",
                "Sweet Sixteen",
                "Graduation Day",
                "Your Wedding Day",
                "Becoming a Parent",
                "A Letter for When You Need It"
            ),
            Milestones.all.map { it.title }
        )
    }

    @Test
    fun `unlock kinds and ages match iOS`() {
        // 4 birthday-age milestones with ages 1, 5, 10, 16
        val birthday = Milestones.all.take(4)
        birthday.forEach { assertEquals(UnlockRuleKind.BIRTHDAY_AGE, it.unlockKind) }
        assertEquals(listOf(1, 5, 10, 16), birthday.map { it.unlockAge })

        // 4 life-event milestones
        val events = Milestones.all.drop(4)
        events.forEach { assertEquals(UnlockRuleKind.LIFE_EVENT, it.unlockKind) }
        events.forEach { assertNull(it.unlockAge) }
    }

    @Test
    fun `life event names match iOS`() {
        assertEquals(
            listOf("Graduation", "Wedding", "Becoming a parent", "Encouragement"),
            Milestones.all.drop(4).map { it.lifeEventName }
        )
    }

    @Test
    fun `body text contractually significant phrases match iOS`() {
        val byTitle = Milestones.all.associateBy { it.title }
        assertEquals("Dear little one,\n\nHappy first birthday! You've grown so much this year…\n\n",
            byTitle.getValue("Your First Birthday").body)
        assertEquals("Today you start school. I remember when…\n\n",
            byTitle.getValue("Starting School").body)
        assertEquals("Double digits! You're growing up so fast…\n\n",
            byTitle.getValue("Your 10th Birthday").body)
        assertEquals("Sixteen years old. I am so proud of the person you're becoming…\n\n",
            byTitle.getValue("Sweet Sixteen").body)
        assertEquals("Today you graduate. All those years of hard work…\n\n",
            byTitle.getValue("Graduation Day").body)
        assertEquals("On this beautiful day, as you start this new chapter…\n\n",
            byTitle.getValue("Your Wedding Day").body)
        assertEquals("Now you understand. The moment you held your own child…\n\n",
            byTitle.getValue("Becoming a Parent").body)
        assertEquals("If you're reading this, you might be having a hard day…\n\n",
            byTitle.getValue("A Letter for When You Need It").body)
    }
}