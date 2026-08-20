package com.gcatcode.petmephone.core.domain.balance

import app.cash.turbine.test
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** `config-override-store` spec, decision 6: [ObserveHungerFactory] tracks the config passed. */
class ObserveHungerFactoryTest {

    private val today = LocalDate.of(2026, 8, 12)
    private val zone = ZoneId.of("UTC")
    private val clock = object : AppClock {
        override fun now(): Instant = today.atStartOfDay(zone).toInstant()
        override fun zone(): ZoneId = zone
        override fun today(): LocalDate = today
    }

    private class FakeTaskRepository(private val manuallyCreated: Int) : TaskRepository {
        override suspend fun createOneOff(title: TaskTitle, createdAt: Instant, createdDate: LocalDate, points: Int) =
            throw UnsupportedOperationException("not used")

        override suspend fun countManuallyCreatedOn(date: LocalDate) = manuallyCreated
        override suspend fun countRecurringScheduledOn(date: LocalDate) = 0
        override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = flowOf(emptyList())
        override fun observeManuallyCreatedOn(date: LocalDate): Flow<Int> = flowOf(manuallyCreated)
        override fun observeRecurringScheduledOn(date: LocalDate): Flow<Int> = flowOf(0)
    }

    @Test
    fun `a different dailyTaskGoal changes ObserveHunger's computed ratio`() = runTest {
        val factory = ObserveHungerFactory(clock, FakeTaskRepository(manuallyCreated = 5))

        factory(BalanceConfig(dailyTaskGoal = 10))().test { assertEquals(50, awaitItem()) }
        factory(BalanceConfig(dailyTaskGoal = 5))().test { assertEquals(100, awaitItem()) }
    }
}
