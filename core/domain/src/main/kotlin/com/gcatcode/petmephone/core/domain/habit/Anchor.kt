package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The cue that makes a habit a habit (#98). A habit requires at least one; a task has none, and
 * that is the whole difference between them.
 *
 * An anchor is NOT recurrence. A recurring task fires by calendar, whereas a habit fires by
 * something that already happens — which is the stronger cue, because a time of day arrives whether
 * or not the user is in a position to act, while "after I close my laptop for lunch" arrives
 * already in context.
 *
 * **Nothing here fires.** #101 dropped anchor firing entirely — it removed app-open detection as
 * invasive and moved the pet's animation from cue to reward — so no device-event variant exists and
 * this type is stored, read and validated but never listened for. The field keeps nearly all its
 * value anyway: the implementation-intention research locates the benefit in having made a specific
 * plan for when and where, not in an app reminding you of it afterwards.
 */
sealed interface Anchor {

    /** This anchor's type, whose declaration order is the strength order — see [AnchorKind]. */
    val kind: AnchorKind

    /**
     * The weekdays on which this anchor can arrive. Compared against a habit's
     * [HabitFrequency.occursOn] by [AnchorCompatibility].
     */
    val arrivesOn: Set<DayOfWeek>

    /**
     * After another habit completes — the strongest cue, because it is an existing behavior rather
     * than a moment on a clock.
     *
     * It carries the anchor habit's own [HabitFrequency] because that is what bounds when this cue
     * can arrive: stacking onto a Monday-only habit yields a Monday-only cue.
     */
    data class AfterHabit(
        val habitId: HabitId,
        val habitFrequency: HabitFrequency,
    ) : Anchor {
        override val kind: AnchorKind = AnchorKind.AFTER_HABIT
        override val arrivesOn: Set<DayOfWeek> = habitFrequency.occursOn
    }

    /** At a coarse part of the day. Arrives every day, since every day has a morning. */
    data class AtDaySegment(val segment: DaySegment) : Anchor {
        override val kind: AnchorKind = AnchorKind.AT_DAY_SEGMENT
        override val arrivesOn: Set<DayOfWeek> = EVERY_DAY
    }

    /** At a clock time. Permitted, but the weakest cue — see [AnchorKind]. */
    data class AtClockTime(val time: LocalTime) : Anchor {
        override val kind: AnchorKind = AnchorKind.AT_CLOCK_TIME
        override val arrivesOn: Set<DayOfWeek> = EVERY_DAY
    }

    companion object {
        private val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

/**
 * Anchor types **in descending order of cue strength**. The declaration order is load-bearing, not
 * cosmetic: #98 requires that a clock time be available but never the first or default offering, so
 * any surface that lists anchor types offers them in [entries] order and gets that ordering for
 * free rather than re-deciding it.
 *
 * [AT_CLOCK_TIME] is last because a clock arrives whether or not the user is in a position to act,
 * while an existing behavior arrives already in context.
 */
enum class AnchorKind {
    AFTER_HABIT,
    AT_DAY_SEGMENT,
    AT_CLOCK_TIME,
}
