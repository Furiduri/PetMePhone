package com.gcatcode.petmephone.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single injected clock/zone port for `:core:domain` (`domain-time` spec). Defined once and
 * shared by every consumer that needs "today" — task creation, Hunger's counting boundary, and
 * later day-close (#34) — so no per-feature duplicate abstraction is ever introduced.
 *
 * [today] is the one conversion from an [Instant] to a [LocalDate]: every consumer that needs
 * "today" goes through this port, never through `LocalDate.now()`, `Clock.systemDefaultZone()`,
 * or an equivalent static call.
 */
interface AppClock {
    fun now(): Instant

    fun zone(): ZoneId

    /**
     * The raw **calendar** date in [zone] — the date a calendar would show, rolling over at
     * midnight.
     *
     * This is NOT the day user activity is credited to. A user whose day starts at 06:00 lives
     * 02:00 as the night before, and crediting it here would split one lived day across two dates.
     * Anything counting what the user did goes through
     * [com.gcatcode.petmephone.core.domain.time.AppDay.at] instead; this stays for the cases where
     * a calendar date is genuinely what is wanted.
     */
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}
