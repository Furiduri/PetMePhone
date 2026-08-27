package com.gcatcode.petmephone.core.domain.habit

import java.time.DayOfWeek

/**
 * How often a habit is meant to happen, expressed as **the set of weekdays it needs**.
 *
 * Days rather than a rate, because that is what the anchor refusal in #98 actually compares: a
 * daily habit stacked onto something that only happens on Mondays is refused because Tuesday
 * through Sunday have no cue, and only a day set can say which days those are. A rate ("three
 * times a week") cannot answer that question, so it is not the unit here.
 *
 * This is NOT the RRULE recurrence tasks carry (#20/#21). A recurring task fires by calendar; a
 * habit fires by cue, and #98 keeps the two deliberately separate. Nothing here reads or writes an
 * RRULE.
 */
sealed interface HabitFrequency {

    /** The weekdays this habit needs a cue on. Never empty. */
    val occursOn: Set<DayOfWeek>

    /** Every day of the week. */
    data object Daily : HabitFrequency {
        override val occursOn: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }

    /** A chosen subset of weekdays. Only [of] can produce one, so the empty set never exists. */
    class OnDays private constructor(override val occursOn: Set<DayOfWeek>) : HabitFrequency {

        override fun equals(other: Any?): Boolean = other is OnDays && other.occursOn == occursOn

        override fun hashCode(): Int = occursOn.hashCode()

        override fun toString(): String = "OnDays(${occursOn.sorted()})"

        companion object {
            /**
             * Rejects the empty set: a habit that occurs on no day is one that can never be done,
             * and storing it would produce a habit the user experiences as the app forgetting.
             */
            fun of(days: Set<DayOfWeek>): HabitFrequencyResult =
                if (days.isEmpty()) {
                    HabitFrequencyResult.Rejected.NoDays
                } else {
                    HabitFrequencyResult.Valid(OnDays(days.toSet()))
                }
        }
    }
}

/** Outcome of [HabitFrequency.OnDays.of]. */
sealed interface HabitFrequencyResult {
    data class Valid(val frequency: HabitFrequency) : HabitFrequencyResult

    sealed interface Rejected : HabitFrequencyResult {
        data object NoDays : Rejected
    }
}
