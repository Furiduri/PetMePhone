package com.gcatcode.petmephone.core.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: [Behavior.of] trims, rejects blank/whitespace-only, and caps length. */
class BehaviorTest {

    @Test
    fun `blank raw behavior is rejected`() {
        val result = Behavior.of("")

        assertEquals(BehaviorResult.Rejected.Blank, result)
    }

    @Test
    fun `whitespace-only raw behavior is rejected`() {
        val result = Behavior.of("   \t  \n ")

        assertEquals(BehaviorResult.Rejected.Blank, result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed and the behavior is accepted`() {
        val result = Behavior.of("  Read one page of Atomic Habits  ")

        assertTrue(result is BehaviorResult.Valid)
        assertEquals("Read one page of Atomic Habits", (result as BehaviorResult.Valid).behavior.value)
    }

    @Test
    fun `a behavior of exactly 200 characters is accepted`() {
        val result = Behavior.of("a".repeat(200))

        assertTrue(result is BehaviorResult.Valid)
        assertEquals(200, (result as BehaviorResult.Valid).behavior.value.length)
    }

    @Test
    fun `a behavior of exactly 201 characters is rejected as too long`() {
        val result = Behavior.of("a".repeat(201))

        assertEquals(BehaviorResult.Rejected.TooLong(length = 201, maxLength = 200), result)
    }

    @Test
    fun `length is measured after trimming, so padding never pushes a valid behavior over the cap`() {
        val result = Behavior.of("  " + "a".repeat(200) + "  ")

        assertTrue("padding must not make an at-cap behavior too long", result is BehaviorResult.Valid)
    }

    @Test
    fun `a vague behavior is accepted, because concreteness is taught rather than validated`() {
        // #98 wants concrete behaviors, but no rule separates "Read" from "Read one page" without
        // parsing natural language. A validator that guessed would reject honest input, so the
        // authoring flow teaches this with an example (#100) and the type stays out of it.
        val result = Behavior.of("Read")

        assertTrue(result is BehaviorResult.Valid)
    }
}
