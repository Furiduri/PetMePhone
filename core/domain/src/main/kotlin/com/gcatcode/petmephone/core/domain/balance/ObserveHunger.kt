package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.time.AppClock
import com.gcatcode.petmephone.core.domain.time.AppDay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Hunger's reactive plumbing (`hunger-metric` spec, design decision 4). Hunger has no `Flow`
 * producer of its own in the domain layer before this class — [calculateHunger] is pure and takes
 * plain counts. This composes the day boundary and [TaskRepository]'s two Flow counts around it.
 *
 * Never caches a count: every emission recomputes [calculateHunger] straight from the two
 * repository flows for the current day. `@Provides`-only in `DataModule`, not `@Inject`-annotated
 * on the class itself, so `:core:domain` gains no `javax.inject` dependency (mirrors
 * [com.gcatcode.petmephone.core.domain.task.CreateOneOffTask]).
 */
class ObserveHunger(
    private val clock: AppClock,
    private val tasks: TaskRepository,
    private val config: BalanceConfig,
    /**
     * The user's start of day. Hunger counts what the user did, so it counts against the day they
     * lived rather than the calendar date — see [AppDay].
     */
    private val dayStart: LocalTime,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Int> =
        todayFlow(clock, dayStart).flatMapLatest { date ->
            combine(
                tasks.observeManuallyCreatedOn(date),
                tasks.observeRecurringScheduledOn(date),
            ) { manuallyCreated, recurringScheduled ->
                calculateHunger(manuallyCreated, recurringScheduled, config)
            }
        }
}

/**
 * Emits the current [AppDay] immediately, then suspends until that day's rollover and re-emits —
 * so a consumer collecting across a day boundary sees the new date without polling. If the process
 * is dozing, the re-emission fires late; bounded because this is only ever collected while the
 * quick-menu card window is attached, and the card is (re)opened by a live tap that re-reads the
 * day on subscription (design.md decision 4's named failure mode).
 *
 * The wake-up is the user's rollover, NOT midnight. Waking at midnight for a day that ends at
 * 06:00 would roll the count over six hours early, while the user is still awake and still adding
 * to the day they are living.
 */
private fun todayFlow(clock: AppClock, dayStart: LocalTime): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.now()
        emit(AppDay.at(now, clock.zone(), dayStart))
        val rollover = AppDay.nextRolloverAfter(now, clock.zone(), dayStart)
        val delayMillis = Duration.between(clock.now(), rollover).toMillis().coerceAtLeast(0)
        delay(delayMillis)
    }
}
