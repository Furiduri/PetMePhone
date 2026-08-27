package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: a frequency is a non-empty set of weekdays, never a rate and never an RRULE. */
class HabitFrequencyTest {

    @Test
    fun `Daily occurs on all seven days`() {
        assertEquals(DayOfWeek.entries.toSet(), HabitFrequency.Daily.occursOn)
    }

    @Test
    fun `an empty day set is rejected`() {
        // A habit that occurs on no day can never be done, and storing it produces exactly the
        // silent non-firing #98 warns about.
        assertEquals(HabitFrequencyResult.Rejected.NoDays, HabitFrequency.OnDays.of(emptySet()))
    }

    @Test
    fun `a chosen day set is accepted and preserved`() {
        val result = HabitFrequency.OnDays.of(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

        assertTrue(result is HabitFrequencyResult.Valid)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            (result as HabitFrequencyResult.Valid).frequency.occursOn,
        )
    }

    @Test
    fun `two OnDays over the same days are equal, and differ over different days`() {
        val a = HabitFrequency.OnDays.of(setOf(DayOfWeek.MONDAY))
        val b = HabitFrequency.OnDays.of(setOf(DayOfWeek.MONDAY))
        val c = HabitFrequency.OnDays.of(setOf(DayOfWeek.TUESDAY))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `the day set is copied, so a caller mutating its own set cannot change the frequency`() {
        val caller = mutableSetOf(DayOfWeek.MONDAY)

        val result = HabitFrequency.OnDays.of(caller) as HabitFrequencyResult.Valid
        caller.add(DayOfWeek.SUNDAY)

        assertEquals(setOf(DayOfWeek.MONDAY), result.frequency.occursOn)
    }
}
