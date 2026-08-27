package com.gcatcode.petmephone.core.domain.habit

/**
 * A coarse part of the day, used by [Anchor.AtDaySegment].
 *
 * Coarse on purpose. "After lunch" is a real cue and a clock time is not the same thing — pinning
 * it to 13:00 makes the habit late at 13:20 for no reason the user recognises. A segment arrives
 * when the user judges it has.
 *
 * The segmentation is minimal here because #96's guided walk is what actually walks a user through
 * the day and is the natural owner of how finely it is cut. This type exists so an anchor can name
 * a segment at all; refining the set is that issue's call, and adding a constant is cheap.
 */
enum class DaySegment {
    MORNING,
    AFTERNOON,
    EVENING,
}
