package com.gcatcode.petmephone.core.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [resolveBack] must be total over both [QuickMenuContent] cases, per design decision 7:
 * `TaskInput -> ShowDashboard`, `Dashboard -> CloseCard`. There is deliberately no third case for
 * the keyboard level — a back press only reaches this function when the IME did not consume it.
 */
class ResolveBackTest {

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
