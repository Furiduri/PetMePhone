package com.gcatcode.petmephone.core.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #100: the overlay collects only what is required, and the step arithmetic never guesses. */
class AuthoringFlowTest {

    @Test
    fun `a task stops at the minimum`() {
        assertEquals(
            listOf(AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM),
            AuthoringFlow.stepsFor(DraftKind.TASK),
        )
    }

    @Test
    fun `a habit also needs a cue, because without one it is not a habit`() {
        assertEquals(
            listOf(AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM, AuthoringStep.ANCHOR),
            AuthoringFlow.stepsFor(DraftKind.HABIT),
        )
    }

    @Test
    fun `identity and scheduling are not collected on the overlay`() {
        // Building a five-step wizard into a 280dp card is how this becomes unusable; the full app
        // exists for the longer conversation.
        val everyStep = DraftKind.entries.flatMap(AuthoringFlow::stepsFor).toSet()

        assertEquals(setOf(AuthoringStep.BEHAVIOR, AuthoringStep.MINIMUM, AuthoringStep.ANCHOR), everyStep)
    }

    @Test
    fun `an out-of-range index is refused rather than clamped to the first step`() {
        // A stored step 3 on a task draft is a state nobody can render. Quietly showing step 1
        // would drop the user somewhere they did not leave off.
        assertNull(AuthoringFlow.stepAt(DraftKind.TASK, 2))
        assertNull(AuthoringFlow.stepAt(DraftKind.HABIT, 3))
        assertNull(AuthoringFlow.stepAt(DraftKind.HABIT, -1))
    }

    @Test
    fun `each index names its own step`() {
        assertEquals(AuthoringStep.BEHAVIOR, AuthoringFlow.stepAt(DraftKind.HABIT, 0))
        assertEquals(AuthoringStep.MINIMUM, AuthoringFlow.stepAt(DraftKind.HABIT, 1))
        assertEquals(AuthoringStep.ANCHOR, AuthoringFlow.stepAt(DraftKind.HABIT, 2))
    }

    @Test
    fun `the last step is where submit replaces next`() {
        assertTrue(AuthoringFlow.isLastStep(DraftKind.TASK, 1))
        assertFalse(AuthoringFlow.isLastStep(DraftKind.TASK, 0))
        assertTrue(AuthoringFlow.isLastStep(DraftKind.HABIT, 2))
        assertFalse(AuthoringFlow.isLastStep(DraftKind.HABIT, 1))
    }

    @Test
    fun `nextIndex returns null at the end, so nobody re-derives the off-by-one`() {
        assertEquals(1, AuthoringFlow.nextIndex(DraftKind.TASK, 0))
        assertNull(AuthoringFlow.nextIndex(DraftKind.TASK, 1))
        assertEquals(2, AuthoringFlow.nextIndex(DraftKind.HABIT, 1))
        assertNull(AuthoringFlow.nextIndex(DraftKind.HABIT, 2))
    }

    @Test
    fun `previousIndex returns null at the first step, where back leaves the form`() {
        assertNull(AuthoringFlow.previousIndex(0))
        assertEquals(0, AuthoringFlow.previousIndex(1))
    }

    @Test
    fun `nextIndex agrees with isLastStep for every reachable index`() {
        // Two statements of the same boundary; they must not be able to disagree.
        DraftKind.entries.forEach { kind ->
            AuthoringFlow.stepsFor(kind).indices.forEach { index ->
                assertEquals(
                    "kind=$kind index=$index",
                    AuthoringFlow.isLastStep(kind, index),
                    AuthoringFlow.nextIndex(kind, index) == null,
                )
            }
        }
    }
}
