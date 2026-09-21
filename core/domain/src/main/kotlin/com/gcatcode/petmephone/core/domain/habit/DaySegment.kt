package com.gcatcode.petmephone.core.domain.habit

/**
 * A coarse part of the day, used by [Anchor.AtDaySegment].
 *
 * Coarse on purpose. "After lunch" is a real cue and a clock time is not the same thing — pinning
 * it to 13:00 makes the habit late at 13:20 for no reason the user recognises. A segment arrives
 * when the user judges it has.
 *
 * The *names* are fixed; **where each one begins is the user's choice** and lives in
 * [DaySegmentBoundaries], because one person's day starts at 06:00 and another's at 10:00. A
 * segmentation that disagrees with the user's actual day puts every segment anchor on the wrong
 * part of it.
 */
enum class DaySegment {
    MORNING,
    AFTERNOON,
    EVENING,
}
