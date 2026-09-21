package com.gcatcode.petmephone.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The day the app credits activity to, which is not always the calendar date. */
class AppDayTest {

    private val utc = ZoneId.of("UTC")
    private val sixAm = LocalTime.of(6, 0)

    @Test
    fun `a late-night moment is credited to the day the user is still living`() {
        // The defect this type exists to remove: 02:00 for a user whose day starts at 06:00 is the
        // tail of the night before, and crediting it to the new calendar date splits one lived day
        // across two rows.
        val twoAm = Instant.parse("2026-08-27T02:00:00Z")

        assertEquals(LocalDate.of(2026, 8, 26), AppDay.at(twoAm, utc, sixAm))
    }

    @Test
    fun `the moment the day starts is credited to that day`() {
        val sixAmSharp = Instant.parse("2026-08-27T06:00:00Z")

        assertEquals(LocalDate.of(2026, 8, 27), AppDay.at(sixAmSharp, utc, sixAm))
    }

    @Test
    fun `a midnight day start makes the effective day the calendar date`() {
        val justAfterMidnight = Instant.parse("2026-08-27T00:00:01Z")

        assertEquals(LocalDate.of(2026, 8, 27), AppDay.at(justAfterMidnight, utc, LocalTime.MIDNIGHT))
    }

    @Test
    fun `the rollover is the user's start of day, not midnight`() {
        // Waking a day-scoped flow at midnight would roll the count over six hours early, while
        // the user is still awake and still adding to the day they are living.
        val evening = Instant.parse("2026-08-27T22:00:00Z")

        val rollover = AppDay.nextRolloverAfter(evening, utc, sixAm)

        assertEquals(Instant.parse("2026-08-28T06:00:00Z"), rollover)
    }

    @Test
    fun `the rollover from a late-night moment is that same morning, not the next one`() {
        // 02:00 belongs to the 26th, so the day it is in ends at 06:00 on the 27th — four hours
        // away, not twenty-eight.
        val twoAm = Instant.parse("2026-08-27T02:00:00Z")

        val rollover = AppDay.nextRolloverAfter(twoAm, utc, sixAm)

        assertEquals(Instant.parse("2026-08-27T06:00:00Z"), rollover)
    }

    @Test
    fun `the rollover is always strictly after the moment it is computed from`() {
        val moments = listOf(
            "2026-08-27T05:59:59Z",
            "2026-08-27T06:00:00Z",
            "2026-08-27T23:59:59Z",
            "2026-08-27T00:00:00Z",
        ).map(Instant::parse)

        moments.forEach { moment ->
            val rollover = AppDay.nextRolloverAfter(moment, utc, sixAm)
            assertEquals(
                "a rollover at or before now would spin the day flow",
                true,
                rollover.isAfter(moment),
            )
        }
    }

    @Test
    fun `the start of day does not move when the user changes time zone`() {
        // The traveller. The same instant is a different local time in each zone, so it lands in a
        // different day — correctly, because the user really did travel. What must NOT happen is
        // the boundary itself shifting: 06:00 local opens the day in both places.
        val mexicoCity = ZoneId.of("UTC-6")
        val berlin = ZoneId.of("UTC+2")
        val moment = Instant.parse("2026-08-27T05:00:00Z")

        // 23:00 on the 26th in Mexico City — before that day's 06:00 start, so still the 26th.
        assertEquals(LocalDate.of(2026, 8, 26), AppDay.at(moment, mexicoCity, sixAm))
        // 07:00 on the 27th in Berlin — after 06:00, so the 27th.
        assertEquals(LocalDate.of(2026, 8, 27), AppDay.at(moment, berlin, sixAm))
    }

    @Test
    fun `crossing zones can make a single day shorter than 24 hours`() {
        // Stated as a test rather than left to be discovered: a day's length is not a constant
        // anywhere in this app, and whatever counts against a day counts against a shorter one.
        val mexicoCity = ZoneId.of("UTC-6")
        val berlin = ZoneId.of("UTC+2")
        val morningInMexico = Instant.parse("2026-08-27T12:00:00Z") // 06:00 local, day starts

        val endedInBerlin = AppDay.nextRolloverAfter(morningInMexico, berlin, sixAm)
        val hoursLived = java.time.Duration.between(morningInMexico, endedInBerlin).toHours()

        assertEquals("the traveller's day is eight hours shorter", 16L, hoursLived)
    }
}
