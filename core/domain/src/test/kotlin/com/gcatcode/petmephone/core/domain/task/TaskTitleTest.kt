package com.gcatcode.petmephone.core.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `task-creation` spec: [TaskTitle.of] trims, rejects blank/whitespace-only, and caps length. */
class TaskTitleTest {

    @Test
    fun `blank raw title is rejected`() {
        val result = TaskTitle.of("")

        assertEquals(TaskTitleResult.Rejected.Blank, result)
    }

    @Test
    fun `whitespace-only raw title is rejected`() {
        val result = TaskTitle.of("   \t  \n ")

        assertEquals(TaskTitleResult.Rejected.Blank, result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed and the title is accepted`() {
        val result = TaskTitle.of("  Feed the cat  ")

        assertTrue(result is TaskTitleResult.Valid)
        assertEquals("Feed the cat", (result as TaskTitleResult.Valid).title.value)
    }

    @Test
    fun `a title of exactly 199 characters is accepted`() {
        val raw = "a".repeat(199)

        val result = TaskTitle.of(raw)

        assertTrue(result is TaskTitleResult.Valid)
        assertEquals(199, (result as TaskTitleResult.Valid).title.value.length)
    }

    @Test
    fun `a title of exactly 200 characters is accepted`() {
        val raw = "a".repeat(200)

        val result = TaskTitle.of(raw)

        assertTrue(result is TaskTitleResult.Valid)
        assertEquals(200, (result as TaskTitleResult.Valid).title.value.length)
    }

    @Test
    fun `a title of exactly 201 characters is rejected as too long`() {
        val raw = "a".repeat(201)

        val result = TaskTitle.of(raw)

        assertEquals(TaskTitleResult.Rejected.TooLong(length = 201, maxLength = 200), result)
    }
}
