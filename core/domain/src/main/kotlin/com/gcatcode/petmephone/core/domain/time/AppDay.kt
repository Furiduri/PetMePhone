package com.gcatcode.petmephone.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * **The day the app credits activity to**, which is not always the calendar date.
 *
 * A user whose day starts at 06:00 experiences 02:00 as the tail of the night before. Crediting a
 * task they finished then to the new calendar date splits one lived day across two rows: the late
 * hours inflate a day the user has not started, and the day they actually worked ends short. They
 * see it as the app resetting while they are still awake.
 *
 * So every consumer that credits *user activity* to a day goes through [at] rather than
 * [AppClock.today], which remains the raw calendar date and is the right answer only when a
 * calendar date is genuinely what is wanted.
 *
 * ## The rollover is wall-clock, like the boundary that drives it
 *
 * `dayStart` is a zone-less [LocalTime] compared against the local time in the user's *current*
 * zone, read live from [AppClock.zone]. That is what keeps "my day starts at six" true after a
 * flight: the boundary is 06:00 in both zones rather than a fixed instant that renders as some
 * other hour once the user lands.
 *
 * A consequence worth stating rather than discovering: crossing zones makes a single day shorter
 * or longer than 24 hours. Flying UTC-6 to UTC+2 removes eight hours from that day, and whatever
 * counts against the day counts against a shorter one. That is correct — the user really did live
 * a shorter day — but it means a day's length is not a constant anywhere in this app.
 *
 * This type takes `dayStart` as a parameter rather than reading it, so `:core:domain`'s time port
 * stays independent of where the boundary is configured.
 */
object AppDay {

    /** The day [instant] is credited to, given the user's [dayStart] in [zone]. */
    fun at(instant: Instant, zone: ZoneId, dayStart: LocalTime): LocalDate {
        val local = instant.atZone(zone)
        return if (local.toLocalTime() < dayStart) {
            local.toLocalDate().minusDays(1)
        } else {
            local.toLocalDate()
        }
    }

    /**
     * The next instant at which [at] would return a different day — the user's rollover, not
     * midnight. Used to wake a day-scoped flow exactly when the day it is showing ends.
     *
     * Resolved through [java.time.ZonedDateTime], so a rollover that a DST change skips or repeats
     * lands on the zone's own answer instead of an hour this app invented.
     */
    fun nextRolloverAfter(instant: Instant, zone: ZoneId, dayStart: LocalTime): Instant {
        val currentDay = at(instant, zone, dayStart)
        return currentDay.plusDays(1).atTime(dayStart).atZone(zone).toInstant()
    }
}
