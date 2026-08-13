package com.gcatcode.petmephone.feature.overlay.ui

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.balance.ObserveHunger
import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Test-only [ObserveHunger] fixture for suites in this file's package that build
 * [PetOverlayStateHolder] directly but never collect its `hunger` `StateFlow` — Hunger's own
 * reactive plumbing is exercised by `ObserveHungerTest` in `:core:domain`, not here. The
 * underlying `Flow<Int>` never emits, which is safe: `WhileSubscribed(0)` means nothing here
 * actually subscribes unless a test reads `holder.hunger`.
 */
internal fun noOpObserveHunger(): ObserveHunger = ObserveHunger(
    clock = object : AppClock {
        override fun now(): Instant = Instant.EPOCH
        override fun zone(): ZoneId = ZoneId.of("UTC")
    },
    tasks = object : TaskRepository {
        override suspend fun createOneOff(
            title: TaskTitle,
            createdAt: Instant,
            createdDate: LocalDate,
            points: Int,
        ): TaskId = throw UnsupportedOperationException("not used by noOpObserveHunger")

        override suspend fun countManuallyCreatedOn(date: LocalDate): Int = 0
        override suspend fun countRecurringScheduledOn(date: LocalDate): Int = 0
        override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = emptyFlow()
        override fun observeManuallyCreatedOn(date: LocalDate): Flow<Int> = emptyFlow()
        override fun observeRecurringScheduledOn(date: LocalDate): Flow<Int> = emptyFlow()
    },
    config = BalanceConfig(),
)
