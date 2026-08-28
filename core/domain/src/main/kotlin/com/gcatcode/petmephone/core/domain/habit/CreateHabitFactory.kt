package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.time.AppClock

/**
 * Builds a [CreateHabit] against a given [DaySegmentBoundaries] snapshot, without changing
 * [CreateHabit]'s own constructor — the same shape as
 * [com.gcatcode.petmephone.core.domain.task.CreateOneOffTaskFactory].
 */
class CreateHabitFactory(
    private val clock: AppClock,
    private val habits: HabitRepository,
) {
    operator fun invoke(boundaries: DaySegmentBoundaries): CreateHabit =
        CreateHabit(clock, habits, boundaries.dayStart)
}
