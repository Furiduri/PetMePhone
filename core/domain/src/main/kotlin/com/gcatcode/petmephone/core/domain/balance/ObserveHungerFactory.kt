package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.time.AppClock

/**
 * Builds an [ObserveHunger] against a given [BalanceConfig] snapshot, without changing
 * [ObserveHunger]'s own constructor (design decision 6): `configSource.config.flatMapLatest { factory(it)() }`.
 */
class ObserveHungerFactory(
    private val clock: AppClock,
    private val tasks: TaskRepository,
) {
    operator fun invoke(config: BalanceConfig): ObserveHunger = ObserveHunger(clock, tasks, config)
}
