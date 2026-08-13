package com.gcatcode.petmephone.core.domain.balance

import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Duration
import java.time.LocalDate
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
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Int> =
        todayFlow(clock).flatMapLatest { date ->
            combine(
                tasks.observeManuallyCreatedOn(date),
                tasks.observeRecurringScheduledOn(date),
            ) { manuallyCreated, recurringScheduled ->
                calculateHunger(manuallyCreated, recurringScheduled, config)
            }
        }
}

/**
 * Emits [AppClock.today] immediately, then suspends until the next local midnight and re-emits —
 * so a consumer collecting across a day boundary sees the new date without polling. If the process
 * is dozing, the re-emission fires late; bounded because this is only ever collected while the
 * quick-menu card window is attached, and the card is (re)opened by a live tap that re-reads
 * [AppClock.today] on subscription (design.md decision 4's named failure mode).
 */
private fun todayFlow(clock: AppClock): Flow<LocalDate> = flow {
    while (true) {
        val today = clock.today()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(clock.zone()).toInstant()
        val delayMillis = Duration.between(clock.now(), nextMidnight).toMillis().coerceAtLeast(0)
        delay(delayMillis)
    }
}
