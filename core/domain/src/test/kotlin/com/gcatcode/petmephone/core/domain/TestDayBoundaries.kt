package com.gcatcode.petmephone.core.domain

import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundaries
import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundariesResult
import java.time.LocalTime

/**
 * A day that starts at midnight, so the effective day equals the calendar date.
 *
 * Tests written before the day boundary became the user's use this deliberately: they pin counting
 * and creation behaviour, not where a day begins, and a midnight start leaves their fixed clocks
 * meaning exactly what they meant when the assertions were written. The boundary's own behaviour is
 * covered by its own tests instead of being smuggled into every unrelated one.
 */
val CALENDAR_DAY: LocalTime = LocalTime.MIDNIGHT

/** [CALENDAR_DAY] as a full set of boundaries, for the factories that take the whole snapshot. */
val CALENDAR_BOUNDARIES: DaySegmentBoundaries =
    (
        DaySegmentBoundaries.of(
            dayStart = CALENDAR_DAY,
            afternoonStart = LocalTime.of(12, 0),
            eveningStart = LocalTime.of(18, 0),
        ) as DaySegmentBoundariesResult.Valid
        ).boundaries
