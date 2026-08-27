package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: anchor types are ordered by cue strength, and a clock time is never offered first. */
class AnchorTest {

    @Test
    fun `a clock time is available but is never the first offering`() {
        // #98: "Time of day is available as an anchor type but is not the first or default
        // offering." Any surface listing anchor types walks `entries`, so asserting the ordering
        // here is what keeps that requirement out of UI convention.
        assertTrue("a clock time must remain available", AnchorKind.AT_CLOCK_TIME in AnchorKind.entries)
        assertNotEquals(AnchorKind.AT_CLOCK_TIME, AnchorKind.entries.first())
        assertEquals(AnchorKind.AT_CLOCK_TIME, AnchorKind.entries.last())
    }

    @Test
    fun `the strongest cue is offered first`() {
        assertEquals(AnchorKind.AFTER_HABIT, AnchorKind.entries.first())
    }

    @Test
    fun `exactly three anchor types exist, since device-event anchors were dropped`() {
        // #101 stopped firing anchors, which removed the app-detected type from this model. A
        // fourth entry appearing here means that decision was reversed without saying so.
        assertEquals(3, AnchorKind.entries.size)
    }

    @Test
    fun `an after-habit anchor arrives exactly when its anchor habit does`() {
        val mondays = (HabitFrequency.OnDays.of(setOf(DayOfWeek.MONDAY)) as HabitFrequencyResult.Valid).frequency

        val anchor = Anchor.AfterHabit(HabitId(9), mondays)

        assertEquals(setOf(DayOfWeek.MONDAY), anchor.arrivesOn)
    }

    @Test
    fun `day-segment and clock-time anchors arrive every day`() {
        val everyDay = DayOfWeek.entries.toSet()

        assertEquals(everyDay, Anchor.AtDaySegment(DaySegment.MORNING).arrivesOn)
        assertEquals(everyDay, Anchor.AtClockTime(LocalTime.of(6, 0)).arrivesOn)
    }

    @Test
    fun `every anchor variant reports its own kind`() {
        assertEquals(AnchorKind.AFTER_HABIT, Anchor.AfterHabit(HabitId(1), HabitFrequency.Daily).kind)
        assertEquals(AnchorKind.AT_DAY_SEGMENT, Anchor.AtDaySegment(DaySegment.AFTERNOON).kind)
        assertEquals(AnchorKind.AT_CLOCK_TIME, Anchor.AtClockTime(LocalTime.NOON).kind)
    }
}
