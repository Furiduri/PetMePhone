package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.config.ConfigField
import com.gcatcode.petmephone.core.domain.config.ConfigGroup
import java.time.LocalTime

/**
 * Where one [DaySegment] ends and the next begins, **chosen by the user**. One person's day starts
 * at 06:00 and another's at 10:00, and a segmentation that disagrees with the user's actual day
 * makes every segment anchor land on the wrong part of it.
 *
 * ## These are wall-clock times, never instants
 *
 * A boundary is a [LocalTime] with no zone attached, and that is load-bearing rather than a
 * simplification. "My day starts at 06:00" means 06:00 wherever the user is standing.
 *
 * Storing it as an instant breaks the traveller: 06:00 under UTC-6 is 12:00Z, and rendering that
 * same instant after moving to UTC+2 gives 14:00 — the user's day would begin in the afternoon
 * because they got on a plane. A wall-clock boundary interpreted in
 * [com.gcatcode.petmephone.core.domain.time.AppClock.zone], which is read live from the device on
 * every call, simply stays 06:00 in both places.
 *
 * The opposite rule holds for anything that *happened*: a completion is a moment, so it is an
 * instant plus the local date it was credited to. Boundaries are wall-clock; events are instants.
 * Both are time, and they are not the same kind of thing.
 *
 * ## The hours before [dayStart] belong to the previous day
 *
 * If the day starts at 06:00, then 02:00 is not this day's morning — it is the tail of the day
 * before, which is how the user experienced it. [segmentAt] reports that with
 * [ResolvedSegment.belongsToPreviousDay].
 *
 * NOTE: this is the habit-facing notion of a day only. It deliberately does NOT change
 * [com.gcatcode.petmephone.core.domain.time.AppClock.today], which is still midnight-based and is
 * what Hunger counts against. Reconciling the two is a cross-cutting decision with its own issue,
 * not something to slip in here.
 *
 * Only [of] can produce an instance, so a non-ascending set of boundaries never exists.
 */
class DaySegmentBoundaries private constructor(
    /** When [DaySegment.MORNING] begins — the user's start of day. */
    val dayStart: LocalTime,
    /** When [DaySegment.AFTERNOON] begins. */
    val afternoonStart: LocalTime,
    /** When [DaySegment.EVENING] begins; it runs until [dayStart] comes round again. */
    val eveningStart: LocalTime,
) {

    /**
     * The segment [time] falls in, and whether it belongs to the day before. [time] is a wall-clock
     * time already expressed in the user's current zone — this type never converts.
     */
    fun segmentAt(time: LocalTime): ResolvedSegment = when {
        time < dayStart -> ResolvedSegment(DaySegment.EVENING, belongsToPreviousDay = true)
        time < afternoonStart -> ResolvedSegment(DaySegment.MORNING, belongsToPreviousDay = false)
        time < eveningStart -> ResolvedSegment(DaySegment.AFTERNOON, belongsToPreviousDay = false)
        else -> ResolvedSegment(DaySegment.EVENING, belongsToPreviousDay = false)
    }

    override fun equals(other: Any?): Boolean =
        other is DaySegmentBoundaries &&
            other.dayStart == dayStart &&
            other.afternoonStart == afternoonStart &&
            other.eveningStart == eveningStart

    override fun hashCode(): Int =
        (dayStart.hashCode() * 31 + afternoonStart.hashCode()) * 31 + eveningStart.hashCode()

    override fun toString(): String = "DaySegmentBoundaries($dayStart, $afternoonStart, $eveningStart)"

    companion object {
        /** This config's override group. User preference, so no staleness notion. */
        val GROUP = ConfigGroup(id = "day_segments", currentVersion = null)

        private const val MINUTES_IN_DAY = 24 * 60

        /** Minutes past midnight at which the user's day begins. Default 06:00. */
        val DAY_START_MINUTES =
            ConfigField.IntField("config_override.day_segments.day_start_minutes", GROUP, 6 * 60, 0, MINUTES_IN_DAY - 1)

        /** Minutes past midnight at which the afternoon begins. Default 12:00. */
        val AFTERNOON_START_MINUTES =
            ConfigField.IntField("config_override.day_segments.afternoon_start_minutes", GROUP, 12 * 60, 0, MINUTES_IN_DAY - 1)

        /** Minutes past midnight at which the evening begins. Default 18:00. */
        val EVENING_START_MINUTES =
            ConfigField.IntField("config_override.day_segments.evening_start_minutes", GROUP, 18 * 60, 0, MINUTES_IN_DAY - 1)

        /** Registry consumed by the tuning panel and the registry-invariant tests. */
        val ALL: List<ConfigField<*>> = listOf(DAY_START_MINUTES, AFTERNOON_START_MINUTES, EVENING_START_MINUTES)

        /** The shipped segmentation, used when no override exists and as the documented fallback. */
        val SHIPPED: DaySegmentBoundaries = DaySegmentBoundaries(
            dayStart = LocalTime.of(6, 0),
            afternoonStart = LocalTime.of(12, 0),
            eveningStart = LocalTime.of(18, 0),
        )

        /**
         * Rejects boundaries that are not strictly ascending. Equal or out-of-order boundaries make
         * at least one segment empty, so an anchor pointing at it could never arrive — the same
         * silent non-firing [AnchorCompatibility] refuses.
         */
        fun of(
            dayStart: LocalTime,
            afternoonStart: LocalTime,
            eveningStart: LocalTime,
        ): DaySegmentBoundariesResult =
            if (dayStart < afternoonStart && afternoonStart < eveningStart) {
                DaySegmentBoundariesResult.Valid(DaySegmentBoundaries(dayStart, afternoonStart, eveningStart))
            } else {
                DaySegmentBoundariesResult.Rejected.NotAscending(dayStart, afternoonStart, eveningStart)
            }

        /** Builds from the three stored minute counts, as the config source resolves them. */
        fun ofMinutes(
            dayStartMinutes: Int,
            afternoonStartMinutes: Int,
            eveningStartMinutes: Int,
        ): DaySegmentBoundariesResult = of(
            dayStart = LocalTime.MIDNIGHT.plusMinutes(dayStartMinutes.toLong()),
            afternoonStart = LocalTime.MIDNIGHT.plusMinutes(afternoonStartMinutes.toLong()),
            eveningStart = LocalTime.MIDNIGHT.plusMinutes(eveningStartMinutes.toLong()),
        )
    }
}

/** Which [DaySegment] a wall-clock time falls in, and whether it is still the previous day's. */
data class ResolvedSegment(
    val segment: DaySegment,
    val belongsToPreviousDay: Boolean,
)

/** Outcome of [DaySegmentBoundaries.of] — measured values, as the other rejections carry. */
sealed interface DaySegmentBoundariesResult {
    data class Valid(val boundaries: DaySegmentBoundaries) : DaySegmentBoundariesResult

    sealed interface Rejected : DaySegmentBoundariesResult {
        /** Carries all three so a surface can point at the one the user moved. */
        data class NotAscending(
            val dayStart: LocalTime,
            val afternoonStart: LocalTime,
            val eveningStart: LocalTime,
        ) : Rejected
    }
}
