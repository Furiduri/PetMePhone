package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `resolveBack` unwinds exactly one level per press and never skips one (design decision 7).
 *
 * The single-field task input and its instructions page were cases here until the authoring form
 * replaced them. Their rows are gone rather than kept as history, because an exhaustive `when` that
 * still named them would keep the dead cases alive in the type.
 */
class ResolveBackTest {

    @Test
    fun `resolveBack is total over every QuickMenuContent case`() {
        // The expectation is itself an exhaustive `when` over the sealed interface, with no else
        // branch: adding a content stops this file compiling until its back outcome is spelled out
        // here, which is a louder failure than a runtime assertion could be. No reflection is used —
        // this module carries no kotlin-reflect dependency.
        val allContents: List<QuickMenuContent> = listOf(
            QuickMenuContent.Dashboard,
            QuickMenuContent.StepForm(stepIndex = 0),
            QuickMenuContent.StepForm(stepIndex = 2),
            QuickMenuContent.StepHelp(stepIndex = 1),
        )

        allContents.forEach { content ->
            val expected: BackOutcome = when (content) {
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
    fun `Dashboard resolves to CloseCard - the last level, dismisses the window`() {
        // Fails if resolveBack tried to unwind further instead of closing the card.
        assertEquals(BackOutcome.CloseCard, resolveBack(QuickMenuContent.Dashboard))
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
