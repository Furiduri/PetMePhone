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

    /**
     * Reactive counterpart to [countManuallyCreatedOn] (`hunger-metric` spec, design decision 4):
     * emits the current count and re-emits on every write that could change it, backed by Room's
     * own query invalidation. Never caches or snapshots the count itself — the flow is the source
     * of truth for [ObserveHunger][com.gcatcode.petmephone.core.domain.balance.ObserveHunger].
     */
    fun observeManuallyCreatedOn(date: LocalDate): Flow<Int>

    /**
     * Reactive counterpart to [countRecurringScheduledOn]. Returns `0` until recurring-occurrence
     * generation lands in a later slice (design decision 8), matching [countRecurringScheduledOn].
     */
    fun observeRecurringScheduledOn(date: LocalDate): Flow<Int>
}
