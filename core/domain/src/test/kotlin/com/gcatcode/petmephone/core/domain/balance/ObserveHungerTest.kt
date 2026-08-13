package com.gcatcode.petmephone.core.domain.balance

import app.cash.turbine.test
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `hunger-metric` spec, design decision 4: [ObserveHunger] against a fake [AppClock]/[TaskRepository],
 * exercised only through its public `invoke(): Flow<Int>`. Uses `runTest` virtual time to advance
 * across the day boundary rather than asserting by inspection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveHungerTest {

    private val zone = ZoneId.of("UTC")

    /** A clock whose [now] advances with virtual time and whose [today] is derived from it, so
     * `runTest`'s `advanceTimeBy` genuinely crosses a local midnight rather than being asserted by
     * inspection. */
    private class VirtualClock(startInstant: Instant, private val zoneId: ZoneId) : AppClock {
        var current: Instant = startInstant
        override fun now(): Instant = current
        override fun zone(): ZoneId = zoneId
    }

    private class FakeTaskRepository(
        manuallyCreated: Map<LocalDate, Int> = emptyMap(),
        recurringScheduled: Map<LocalDate, Int> = emptyMap(),
    ) : TaskRepository {
        private val manualFlows = mutableMapOf<LocalDate, MutableStateFlow<Int>>()
        private val recurringFlows = mutableMapOf<LocalDate, MutableStateFlow<Int>>()

        init {
            manuallyCreated.forEach { (date, count) -> manualFlow(date).value = count }
            recurringScheduled.forEach { (date, count) -> recurringFlow(date).value = count }
        }

        private fun manualFlow(date: LocalDate) = manualFlows.getOrPut(date) { MutableStateFlow(0) }
        private fun recurringFlow(date: LocalDate) = recurringFlows.getOrPut(date) { MutableStateFlow(0) }

        fun setManuallyCreated(date: LocalDate, count: Int) {
            manualFlow(date).value = count
        }

        override suspend fun createOneOff(
            title: TaskTitle,
            createdAt: Instant,
            createdDate: LocalDate,
            points: Int,
        ) = throw UnsupportedOperationException("not used by ObserveHungerTest")

        override suspend fun countManuallyCreatedOn(date: LocalDate): Int = manualFlow(date).value

        override suspend fun countRecurringScheduledOn(date: LocalDate): Int = recurringFlow(date).value

        override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> = flowOf(emptyList())

        override fun observeManuallyCreatedOn(date: LocalDate): Flow<Int> = manualFlow(date)

        override fun observeRecurringScheduledOn(date: LocalDate): Flow<Int> = recurringFlow(date)
    }

    @Test
    fun `emits the initial hunger percentage on first collection`() = runTest {
        val today = LocalDate.of(2026, 8, 12)
        val clock = VirtualClock(today.atStartOfDay(zone).toInstant(), zone)
        val repository = FakeTaskRepository(manuallyCreated = mapOf(today to 3))
        val config = BalanceConfig(dailyTaskGoal = 10)
        val observeHunger = ObserveHunger(clock, repository, config)

        observeHunger().test {
            // Failing input this guards: an implementation that never reads the repository at all
            // (e.g. a stub returning a constant) would emit anything other than 30.
            assertEquals(30, awaitItem())
        }
    }

    @Test
    fun `re-emits when the manually-created count changes`() = runTest {
        val today = LocalDate.of(2026, 8, 12)
        val clock = VirtualClock(today.atStartOfDay(zone).toInstant(), zone)
        val repository = FakeTaskRepository(manuallyCreated = mapOf(today to 2))
        val config = BalanceConfig(dailyTaskGoal = 10)
        val observeHunger = ObserveHunger(clock, repository, config)

        observeHunger().test {
            assertEquals(20, awaitItem())

            repository.setManuallyCreated(today, 5)

            // Failing input this guards: a one-shot read (e.g. via `first()` snapshotted at
            // subscription) would never emit this second value at all.
            assertEquals(50, awaitItem())
        }
    }

    @Test
    fun `re-emits across a local midnight boundary using virtual time`() = runTest {
        val day1 = LocalDate.of(2026, 8, 12)
        val day2 = day1.plusDays(1)
        val startOfDay1 = day1.atStartOfDay(zone).toInstant()
        val clock = VirtualClock(startOfDay1, zone)
        val repository = FakeTaskRepository(
            manuallyCreated = mapOf(day1 to 4, day2 to 1),
        )
        val config = BalanceConfig(dailyTaskGoal = 10)
        val observeHunger = ObserveHunger(clock, repository, config)

        observeHunger().test {
            assertEquals(40, awaitItem())

            // Advance virtual time past local midnight into day2, moving the clock's `now()` with
            // it so `today()` genuinely changes rather than being reassigned directly.
            val millisToMidnight = Duration.between(clock.now(), day2.atStartOfDay(zone).toInstant()).toMillis()
            clock.current = day2.atStartOfDay(zone).toInstant()
            testScheduler.advanceTimeBy(millisToMidnight + 1)
            testScheduler.runCurrent()

            // Failing input this guards: a `flatMapLatest` keyed on a value read once at
            // subscription (rather than a recurring `todayFlow` that re-emits `clock.today()`)
            // would stay on day1's count (40) forever instead of switching to day2's (10).
            assertEquals(10, awaitItem())
        }
    }

    @Test
    fun `generated recurring occurrences never move the count reported for manual tasks alone`() = runTest {
        val today = LocalDate.of(2026, 8, 12)
        val clock = VirtualClock(today.atStartOfDay(zone).toInstant(), zone)
        // 3 manual tasks, 9 recurring occurrences scheduled today. With the default config
        // (recurringHungerRatio = 3, recurringHungerCap = 4), the recurring term contributes
        // min(9 / 3, 4) = 3 points, so total points = 3 + 3 = 6, i.e. 60% of a goal of 10.
        val repository = FakeTaskRepository(
            manuallyCreated = mapOf(today to 3),
            recurringScheduled = mapOf(today to 9),
        )
        val config = BalanceConfig(dailyTaskGoal = 10)
        val observeHunger = ObserveHunger(clock, repository, config)

        observeHunger().test {
            // Failing input this guards: an implementation that reads only the manual count and
            // ignores the recurring flow (or vice versa) would emit 30 or 90 here instead of 60 —
            // proving both flows are actually combined, not just one of them wired through.
            assertEquals(60, awaitItem())
        }
    }
}
