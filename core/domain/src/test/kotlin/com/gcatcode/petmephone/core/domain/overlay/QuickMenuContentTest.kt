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
            QuickMenuContent.StepForm(stepIndex = 0),
            QuickMenuContent.StepForm(stepIndex = 2),
            QuickMenuContent.StepHelp(stepIndex = 1),
        )

        allContents.forEach { content ->
            val expected: BackOutcome = when (content) {
                QuickMenuContent.Instructions -> BackOutcome.ShowTaskInput
                QuickMenuContent.TaskInput -> BackOutcome.ShowDashboard
                QuickMenuContent.Dashboard -> BackOutcome.CloseCard
                is QuickMenuContent.StepHelp -> BackOutcome.ShowStep(content.stepIndex)
                is QuickMenuContent.StepForm ->
                    if (content.stepIndex > 0) {
                        BackOutcome.ShowStep(content.stepIndex - 1)
                    } else {
                        BackOutcome.ShowDashboard
                    }
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

    @Test
    fun `back inside the form walks the steps and never discards`() {
        // Back is safe to press: only Cancel discards a draft (#100). Reaching the first step's
        // back leaves the form for the dashboard rather than unwinding into nothing.
        assertEquals(BackOutcome.ShowStep(1), resolveBack(QuickMenuContent.StepForm(stepIndex = 2)))
        assertEquals(BackOutcome.ShowDashboard, resolveBack(QuickMenuContent.StepForm(stepIndex = 0)))
    }

    @Test
    fun `a step's help returns to that step, not to the start of the form`() {
        assertEquals(BackOutcome.ShowStep(2), resolveBack(QuickMenuContent.StepHelp(stepIndex = 2)))
    }
}
