package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveBack] must be total over **every** [QuickMenuContent] case, per design decision 7:
 * `Instructions -> ShowTaskInput`, `TaskInput -> ShowDashboard`, `Dashboard -> CloseCard`. There
 * is deliberately no case for the keyboard level — a back press only reaches this function when
 * the IME did not consume it.
 *
 * Totality is asserted structurally as well as case by case: a fourth content added tomorrow
 * fails `resolveBack is total over every QuickMenuContent case` rather than silently inheriting
 * whichever branch a `when` happened to fall through to.
 */
class ResolveBackTest {

    @Test
    fun `Instructions resolves to ShowTaskInput - unwinds one level, window stays open`() {
        // Fails if resolveBack skipped a level straight to the dashboard, or closed the card.
        val result = resolveBack(QuickMenuContent.Instructions)

        assertEquals(BackOutcome.ShowTaskInput, result)
    }

    @Test
    fun `resolveBack is total over every QuickMenuContent case`() {
        // The expectation is itself an exhaustive `when` over the sealed interface, with no else
        // branch: adding a fourth content stops this file compiling until its back outcome is
        // spelled out here, which is a louder failure than a runtime assertion could be. No
        // reflection is used — this module carries no kotlin-reflect dependency.
        val allContents: List<QuickMenuContent> = listOf(
            QuickMenuContent.Dashboard,
            QuickMenuContent.TaskInput,
            QuickMenuContent.Instructions,
        )

        allContents.forEach { content ->
            val expected: BackOutcome = when (content) {
                QuickMenuContent.Instructions -> BackOutcome.ShowTaskInput
                QuickMenuContent.TaskInput -> BackOutcome.ShowDashboard
                QuickMenuContent.Dashboard -> BackOutcome.CloseCard
            }
            assertEquals("wrong back outcome for $content", expected, resolveBack(content))
        }
    }

    @Test
    fun `TaskInput resolves to ShowDashboard - unwinds one level, window stays open`() {
        // Fails if resolveBack closed the card instead of unwinding to the dashboard.
        val result = resolveBack(QuickMenuContent.TaskInput)

        assertEquals(BackOutcome.ShowDashboard, result)
    }

    @Test
    fun `Dashboard resolves to CloseCard - the last level, dismisses the window`() {
        // Fails if resolveBack tried to unwind further instead of closing the card.
        val result = resolveBack(QuickMenuContent.Dashboard)

        assertEquals(BackOutcome.CloseCard, result)
    }
}
