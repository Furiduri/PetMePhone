package com.gcatcode.petmephone.core.domain.task

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.time.AppDay
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.LocalTime

/**
 * Creates a one-off task and its due-today occurrence (`task-creation` spec). `clock.now()` and
 * [AppDay] are the only "today" in the write path — never `LocalDate.now()` or a direct `Clock`
 * call (`domain-time` spec). The day is the user's, not the calendar's: a task created at 02:00
 * counts toward the day they are still living.
 *
 * MUST be invoked from an application- or service-scoped [kotlinx.coroutines.CoroutineScope],
 * never `rememberCoroutineScope()`: a card dismissed the instant after submit must not lose the
 * write.
 */
class CreateOneOffTask(
    private val clock: AppClock,
    private val tasks: TaskRepository,
    private val config: BalanceConfig,
    /**
     * The user's start of day. A task created at 02:00 belongs to the day the user is still
     * living, not to the calendar date that just began — see [AppDay].
     */
    private val dayStart: LocalTime,
) {
    suspend operator fun invoke(rawBehavior: String, rawMinimum: String): CreateTaskResult {
        val behavior = when (val result = Behavior.of(rawBehavior)) {
            is BehaviorResult.Rejected.Blank -> return CreateTaskResult.Rejected.BlankBehavior
            is BehaviorResult.Rejected.TooLong ->
                return CreateTaskResult.Rejected.BehaviorTooLong(result.length, result.maxLength)
            is BehaviorResult.Valid -> result.behavior
        }

        // Required on a task exactly as on a habit (#98). Validated before anything is written, so
        // a task never exists without the field its row rendering depends on.
        val minimum = when (val result = Minimum.of(rawMinimum)) {
            is MinimumResult.Rejected.Blank -> return CreateTaskResult.Rejected.BlankMinimum
            is MinimumResult.Rejected.TooLong ->
                return CreateTaskResult.Rejected.MinimumTooLong(result.length, result.maxLength)
            is MinimumResult.Valid -> result.minimum
        }

        val now = clock.now()
        val today = AppDay.at(now, clock.zone(), dayStart)

        return try {
            val id = tasks.createOneOff(
                behavior = behavior,
                minimum = minimum,
                createdAt = now,
                createdDate = today,
                points = config.standardTaskPoints,
            )
            val hungerCapReached = tasks.countManuallyCreatedOn(today) >= config.dailyTaskGoal
            CreateTaskResult.Created(id = id, hungerCapReached = hungerCapReached)
        } catch (_: Exception) {
            CreateTaskResult.Rejected.PersistenceFailure
        }
    }
}
