package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: an anchor whose frequency cannot support the habit's is refused, with a reason. */
class AnchorCompatibilityTest {

    private fun onDays(vararg days: DayOfWeek): HabitFrequency =
        (HabitFrequency.OnDays.of(days.toSet()) as HabitFrequencyResult.Valid).frequency

    @Test
    fun `a daily habit stacked onto a Monday-only habit is refused`() {
        // #98's second-most-important case. The authoring mistake produces a habit that silently
        // cannot fire, which the user experiences as the app forgetting.
        val anchor = Anchor.AfterHabit(HabitId(7), onDays(DayOfWeek.MONDAY))

        val result = AnchorCompatibility.check(HabitFrequency.Daily, anchor)

        val rejection = result as AnchorCompatibilityResult.Rejected.MissingDays
        assertEquals(setOf(DayOfWeek.MONDAY), rejection.anchorArrivesOn)
        assertEquals(DayOfWeek.entries.toSet() - DayOfWeek.MONDAY, rejection.missingDays)
    }

    @Test
    fun `the rejection names every missing day, not just that it failed`() {
        val anchor = Anchor.AfterHabit(HabitId(1), onDays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))

        val result = AnchorCompatibility.check(onDays(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), anchor)

        val rejection = result as AnchorCompatibilityResult.Rejected.MissingDays
        assertEquals(setOf(DayOfWeek.FRIDAY), rejection.missingDays)
    }

    @Test
    fun `an anchor arriving on exactly the habit's days is compatible`() {
        val anchor = Anchor.AfterHabit(HabitId(2), onDays(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))

        val result = AnchorCompatibility.check(onDays(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), anchor)

        assertEquals(AnchorCompatibilityResult.Compatible, result)
    }

    @Test
    fun `an anchor arriving more often than the habit needs is compatible`() {
        // The extra days simply go unused. Only missing days are a defect.
        val anchor = Anchor.AfterHabit(HabitId(3), HabitFrequency.Daily)

        val result = AnchorCompatibility.check(onDays(DayOfWeek.SATURDAY), anchor)

        assertEquals(AnchorCompatibilityResult.Compatible, result)
    }

    @Test
    fun `a day segment supports a daily habit, because every day has one`() {
        val result = AnchorCompatibility.check(HabitFrequency.Daily, Anchor.AtDaySegment(DaySegment.MORNING))

        assertEquals(AnchorCompatibilityResult.Compatible, result)
    }

    @Test
    fun `a clock time supports a daily habit, because a clock arrives every day`() {
        val result = AnchorCompatibility.check(HabitFrequency.Daily, Anchor.AtClockTime(LocalTime.of(7, 30)))

        assertEquals(AnchorCompatibilityResult.Compatible, result)
    }

    @Test
    fun `the check is driven by arrivesOn, so no anchor type is special-cased`() {
        // Guards the design rather than a case: a future anchor type with a narrower arrival is
        // covered the day it is added, without anyone remembering to extend a `when`.
        val everyAnchor = listOf(
            Anchor.AfterHabit(HabitId(4), HabitFrequency.Daily),
            Anchor.AtDaySegment(DaySegment.EVENING),
            Anchor.AtClockTime(LocalTime.NOON),
        )

        everyAnchor.forEach { anchor ->
            val expected = if (anchor.arrivesOn.containsAll(HabitFrequency.Daily.occursOn)) {
                AnchorCompatibilityResult.Compatible
            } else {
                null
            }
            val actual = AnchorCompatibility.check(HabitFrequency.Daily, anchor)
            if (expected != null) {
                assertEquals("${anchor.kind} should be compatible", expected, actual)
            } else {
                assertTrue("${anchor.kind} should be refused", actual is AnchorCompatibilityResult.Rejected)
            }
        }
    }
}
