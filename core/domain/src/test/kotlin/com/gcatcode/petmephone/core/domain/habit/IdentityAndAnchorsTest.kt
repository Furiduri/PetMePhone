package com.gcatcode.petmephone.core.domain.habit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: identity is optional but never blank, and a habit's cue list is never empty. */
class IdentityAndAnchorsTest {

    private val morning = Anchor.AtDaySegment(DaySegment.MORNING)

    @Test
    fun `an unanswered identity is absent rather than rejected`() {
        // Skipping the question is allowed, so it is not an error — but it must not become an
        // empty Identity either, which would render as a blank line at completion and read as the
        // app having lost the answer.
        assertEquals(IdentityResult.Absent, Identity.ofOptional(null))
        assertEquals(IdentityResult.Absent, Identity.ofOptional("   "))
    }

    @Test
    fun `a typed identity is still validated`() {
        val result = Identity.ofOptional("  I am someone who reads  ")

        assertEquals("I am someone who reads", (result as IdentityResult.Valid).identity.value)
    }

    @Test
    fun `Identity of refuses blank, so no blank instance can exist`() {
        assertEquals(IdentityResult.Rejected.Blank, Identity.of("   "))
    }

    @Test
    fun `an identity of exactly 280 characters is accepted and 281 is refused`() {
        assertTrue(Identity.of("a".repeat(280)) is IdentityResult.Valid)
        assertEquals(IdentityResult.Rejected.TooLong(281, 280), Identity.of("a".repeat(281)))
    }

    @Test
    fun `an identity may be longer than a behavior, because it is a sentence`() {
        assertTrue(Identity.MAX_LENGTH > com.gcatcode.petmephone.core.domain.task.Behavior.MAX_LENGTH)
    }

    @Test
    fun `an empty anchor list is refused`() {
        assertEquals(AnchorsResult.Rejected.Empty, Anchors.of(emptyList()))
    }

    @Test
    fun `a single anchor is accepted`() {
        val result = Anchors.of(morning)

        assertEquals(1, (result as AnchorsResult.Valid).anchors.size)
    }

    @Test
    fun `the user's order is preserved, never re-sorted by cue strength`() {
        // AnchorKind's strength order is for OFFERING types, not for ranking cues the user has
        // already committed to.
        val evening = Anchor.AtDaySegment(DaySegment.EVENING)
        val afterHabit = Anchor.AfterHabit(HabitId(1), HabitFrequency.Daily)

        val result = Anchors.of(listOf(evening, afterHabit)) as AnchorsResult.Valid

        assertEquals(listOf(evening, afterHabit), result.anchors.values)
    }

    @Test
    fun `the list is copied, so a caller emptying its own list cannot empty the anchors`() {
        val caller = mutableListOf(morning)

        val result = Anchors.of(caller) as AnchorsResult.Valid
        caller.clear()

        assertEquals(1, result.anchors.size)
    }

    @Test
    fun `two Anchors over the same cues in the same order are equal`() {
        val a = Anchors.of(listOf(morning)) as AnchorsResult.Valid
        val b = Anchors.of(listOf(morning)) as AnchorsResult.Valid
        val c = Anchors.of(listOf(Anchor.AtDaySegment(DaySegment.EVENING))) as AnchorsResult.Valid

        assertEquals(a.anchors, b.anchors)
        assertEquals(a.anchors.hashCode(), b.anchors.hashCode())
        assertNotEquals(a.anchors, c.anchors)
    }
}
