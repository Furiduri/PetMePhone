package com.gcatcode.petmephone.core.domain.task

import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `config-override-store` spec, decision 6: [CreateOneOffTaskFactory] tracks the config passed. */
class CreateOneOffTaskFactoryTest {

    private val fixedToday = LocalDate.of(2026, 8, 11)
    private val fixedNow = fixedToday.atStartOfDay(ZoneId.of("UTC")).toInstant()
    private val clock = object : AppClock {
        override fun now(): Instant = fixedNow
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today(): LocalDate = fixedToday
    }

    private class FakeTaskRepository : TaskRepository {
        val createdPoints = mutableListOf<Int>()
        var nextId = 1L

        override suspend fun createOneOff(title: TaskTitle, createdAt: Instant, createdDate: LocalDate, points: Int): TaskId {
            createdPoints += points
            return TaskId(nextId++)
        }

        override suspend fun countManuallyCreatedOn(date: LocalDate) = createdPoints.size
        override suspend fun countRecurringScheduledOn(date: LocalDate) = 0
        override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = flowOf(emptyList())
        override fun observeManuallyCreatedOn(date: LocalDate): Flow<Int> = flowOf(createdPoints.size)
        override fun observeRecurringScheduledOn(date: LocalDate): Flow<Int> = flowOf(0)
    }

    @Test
    fun `a different standardTaskPoints changes the points written for a created task`() = runTest {
        val repository = FakeTaskRepository()
        val factory = CreateOneOffTaskFactory(clock, repository)

        factory(BalanceConfig(standardTaskPoints = 5))("Feed the cat")

        assertEquals(listOf(5), repository.createdPoints)
    }

    @Test
    fun `a different dailyTaskGoal changes whether the cap is reported as reached`() = runTest {
        val factory = CreateOneOffTaskFactory(clock, FakeTaskRepository())

        val result = factory(BalanceConfig(dailyTaskGoal = 1))("Feed the cat")

        assertTrue((result as CreateTaskResult.Created).hungerCapReached)
    }
}
