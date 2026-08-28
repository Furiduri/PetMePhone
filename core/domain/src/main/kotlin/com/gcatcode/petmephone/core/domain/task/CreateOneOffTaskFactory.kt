package com.gcatcode.petmephone.core.domain.task

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.habit.DaySegmentBoundaries
import com.gcatcode.petmephone.core.domain.time.AppClock

/**
 * Builds a [CreateOneOffTask] against [BalanceConfig] and [DaySegmentBoundaries] snapshots,
 * without changing [CreateOneOffTask]'s own constructor (design decision 6).
 */
class CreateOneOffTaskFactory(
    private val clock: AppClock,
    private val tasks: TaskRepository,
) {
    operator fun invoke(
        config: BalanceConfig,
        boundaries: DaySegmentBoundaries,
    ): CreateOneOffTask = CreateOneOffTask(clock, tasks, config, boundaries.dayStart)
}
