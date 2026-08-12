package com.gcatcode.petmephone.core.domain.task

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Port for task persistence (`task-persistence` spec). Implementation and its Room-backed
 * DAOs/entities live in `:core:data`; the binding is `@Binds` in `BindingsModule`, per the
 * existing `PetProfileRepository` precedent.
 */
interface TaskRepository {
    /**
     * [points] is the caller's `BalanceConfig.standardTaskPoints` (design's data-flow block): the
     * repository never reads balance values itself, only carries the number the domain layer
     * already resolved, so `points = config.standardTaskPoints` is never a literal here.
     */
    suspend fun createOneOff(
        title: TaskTitle,
        createdAt: Instant,
        createdDate: LocalDate,
        points: Int,
    ): TaskId

    suspend fun countManuallyCreatedOn(date: LocalDate): Int

    suspend fun countRecurringScheduledOn(date: LocalDate): Int

    fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>>
}
