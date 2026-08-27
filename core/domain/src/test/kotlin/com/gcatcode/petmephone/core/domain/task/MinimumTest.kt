package com.gcatcode.petmephone.core.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: [Minimum.of] trims, rejects blank/whitespace-only, and caps length. */
class MinimumTest {

    @Test
    fun `blank raw minimum is rejected`() {
        val result = Minimum.of("")

        assertEquals(MinimumResult.Rejected.Blank, result)
    }

    @Test
    fun `whitespace-only raw minimum is rejected`() {
        val result = Minimum.of("   \t  \n ")

        assertEquals(MinimumResult.Rejected.Blank, result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed and the minimum is accepted`() {
        val result = Minimum.of("  Put three dishes in the sink  ")

        assertTrue(result is MinimumResult.Valid)
        assertEquals("Put three dishes in the sink", (result as MinimumResult.Valid).minimum.value)
    }

    @Test
    fun `a minimum of exactly 200 characters is accepted`() {
        val result = Minimum.of("a".repeat(200))

        assertTrue(result is MinimumResult.Valid)
        assertEquals(200, (result as MinimumResult.Valid).minimum.value.length)
    }

    @Test
    fun `a minimum of exactly 201 characters is rejected as too long`() {
        val result = Minimum.of("a".repeat(201))

        assertEquals(MinimumResult.Rejected.TooLong(length = 201, maxLength = 200), result)
    }

    @Test
    fun `length is measured after trimming, so padding never pushes a valid minimum over the cap`() {
        val result = Minimum.of("  " + "a".repeat(200) + "  ")

        assertTrue("padding must not make an at-cap minimum too long", result is MinimumResult.Valid)
    }

    @Test
    fun `the cap matches Behavior's, since a minimum is a behavior that is merely smaller`() {
        assertEquals(Behavior.MAX_LENGTH, Minimum.MAX_LENGTH)
    }
}
