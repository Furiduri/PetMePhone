package com.gcatcode.petmephone.core.domain.task

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.time.AppClock

/**
 * Creates a one-off task and its due-today occurrence (`task-creation` spec). `clock.now()` /
 * `clock.today()` are the only "today" in the write path — never `LocalDate.now()` or a direct
 * `Clock` call (`domain-time` spec).
 *
 * MUST be invoked from an application- or service-scoped [kotlinx.coroutines.CoroutineScope],
 * never `rememberCoroutineScope()`: a card dismissed the instant after submit must not lose the
 * write.
 */
class CreateOneOffTask(
    private val clock: AppClock,
    private val tasks: TaskRepository,
    private val config: BalanceConfig,
) {
    suspend operator fun invoke(rawTitle: String): CreateTaskResult {
        val titleResult = TaskTitle.of(rawTitle)
        val title = when (titleResult) {
            is TaskTitleResult.Rejected.Blank -> return CreateTaskResult.Rejected.BlankTitle
            is TaskTitleResult.Rejected.TooLong ->
                return CreateTaskResult.Rejected.TitleTooLong(
                    length = titleResult.length,
                    maxLength = titleResult.maxLength,
                )
            is TaskTitleResult.Valid -> titleResult.title
        }

        val now = clock.now()
        val today = clock.today()

        return try {
            val id = tasks.createOneOff(
                title = title,
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
