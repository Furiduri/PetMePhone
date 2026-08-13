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
 * Instrumented-test-only [ObserveHunger] fixture, mirroring the JVM-test one in
 * `src/test/.../ui/NoOpObserveHunger.kt`. These suites build [PetOverlayStateHolder] directly to
 * exercise pet rendering and never collect `hunger`, so the underlying flow never emitting is
 * safe — nothing subscribes.
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
