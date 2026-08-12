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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `task-creation` spec: [CreateOneOffTask] against a fake [TaskRepository] and a fixed [AppClock],
 * exercised through its public `invoke` API only — behaviour, not shape.
 */
class CreateOneOffTaskTest {

    private val fixedToday = LocalDate.of(2026, 8, 11)
    private val fixedNow = fixedToday.atStartOfDay(ZoneId.of("UTC")).toInstant()

    private val clock = object : AppClock {
        override fun now(): Instant = fixedNow
        override fun zone(): ZoneId = ZoneId.of("UTC")
        override fun today(): LocalDate = fixedToday
    }

    private class FakeTaskRepository : TaskRepository {
        data class CreatedRecord(
            val title: TaskTitle,
            val createdAt: Instant,
            val createdDate: LocalDate,
            val points: Int,
        )

        val createdRecords = mutableListOf<CreatedRecord>()
        var nextId = 1L
        var throwOnCreate: Exception? = null

        override suspend fun createOneOff(
            title: TaskTitle,
            createdAt: Instant,
            createdDate: LocalDate,
            points: Int,
        ): TaskId {
            throwOnCreate?.let { throw it }
            createdRecords += CreatedRecord(title, createdAt, createdDate, points)
            return TaskId(nextId++)
        }

        override suspend fun countManuallyCreatedOn(date: LocalDate): Int =
            createdRecords.count { it.createdDate == date }

        override suspend fun countRecurringScheduledOn(date: LocalDate): Int = 0

        override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = flowOf(emptyList())
    }

    @Test
    fun `a valid title writes a task created and due today`() = runTest {
        val repository = FakeTaskRepository()
        val config = BalanceConfig(standardTaskPoints = 5)
        val useCase = CreateOneOffTask(clock, repository, config)

        val result = useCase("Feed the cat")

        assertTrue(result is CreateTaskResult.Created)
        assertEquals(1, repository.createdRecords.size)
        val record = repository.createdRecords.single()
        assertEquals("Feed the cat", record.title.value)
        assertEquals(fixedToday, record.createdDate)
        assertEquals(fixedNow, record.createdAt)
        assertEquals(5, record.points)
    }

    @Test
    fun `duplicate titles both succeed`() = runTest {
        val repository = FakeTaskRepository()
        val useCase = CreateOneOffTask(clock, repository, BalanceConfig())

        val first = useCase("Feed the cat")
        val second = useCase("Feed the cat")

        assertTrue(first is CreateTaskResult.Created)
        assertTrue(second is CreateTaskResult.Created)
        assertEquals(2, repository.createdRecords.size)
    }

    @Test
    fun `blank title is rejected without touching the repository`() = runTest {
        val repository = FakeTaskRepository()
        val useCase = CreateOneOffTask(clock, repository, BalanceConfig())

        val result = useCase("   ")

        assertEquals(CreateTaskResult.Rejected.BlankTitle, result)
        assertTrue(repository.createdRecords.isEmpty())
    }

    @Test
    fun `over-length title is rejected with the measured length`() = runTest {
        val repository = FakeTaskRepository()
        val useCase = CreateOneOffTask(clock, repository, BalanceConfig())

        val result = useCase("a".repeat(201))

        assertEquals(
            CreateTaskResult.Rejected.TitleTooLong(length = 201, maxLength = 200),
            result,
        )
        assertTrue(repository.createdRecords.isEmpty())
    }

    @Test
    fun `the eleventh task of the day under a goal of ten still creates and reports the cap as reached`() =
        runTest {
            val repository = FakeTaskRepository()
            val config = BalanceConfig(dailyTaskGoal = 10)
            val useCase = CreateOneOffTask(clock, repository, config)
            repeat(10) { index -> useCase("Task $index") }

            val eleventh = useCase("Task 11")

            assertTrue(eleventh is CreateTaskResult.Created)
            assertEquals(11, repository.createdRecords.size)
            assertTrue((eleventh as CreateTaskResult.Created).hungerCapReached)
        }

    @Test
    fun `a task created below the daily goal does not report the cap as reached`() = runTest {
        val repository = FakeTaskRepository()
        val config = BalanceConfig(dailyTaskGoal = 10)
        val useCase = CreateOneOffTask(clock, repository, config)

        val result = useCase("Feed the cat")

        assertTrue(result is CreateTaskResult.Created)
        assertFalse((result as CreateTaskResult.Created).hungerCapReached)
    }

    @Test
    fun `a simulated persistence failure returns a typed failure result instead of throwing`() = runTest {
        val repository = FakeTaskRepository().apply {
            throwOnCreate = IllegalStateException("simulated database failure")
        }
        val useCase = CreateOneOffTask(clock, repository, BalanceConfig())

        val result = useCase("Feed the cat")

        assertEquals(CreateTaskResult.Rejected.PersistenceFailure, result)
    }
}
