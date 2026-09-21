package com.gcatcode.petmephone.core.domain.habit

import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #98: nothing partially valid reaches the repository, and a habit always has a workable cue. */
class CreateHabitTest {

    private val zone = ZoneId.of("UTC")
    private val sixAm = LocalTime.of(6, 0)

    private class FakeClock(private val instant: Instant, private val zoneId: ZoneId) : AppClock {
        override fun now(): Instant = instant
        override fun zone(): ZoneId = zoneId
    }

    private class FakeHabitRepository(var throwOnCreate: Exception? = null) : HabitRepository {
        var created: MutableList<Triple<Behavior, Minimum, Identity?>> = mutableListOf()
        var createdDates: MutableList<LocalDate> = mutableListOf()
        var createdAnchors: MutableList<Anchors> = mutableListOf()

        override suspend fun create(
            behavior: Behavior,
            minimum: Minimum,
            frequency: HabitFrequency,
            anchors: Anchors,
            identity: Identity?,
            createdAt: Instant,
            createdDate: LocalDate,
        ): HabitId {
            throwOnCreate?.let { throw it }
            created += Triple(behavior, minimum, identity)
            createdDates += createdDate
            createdAnchors += anchors
            return HabitId(created.size.toLong())
        }

        /** Not exercised here: this test is about the write path's refusals. */
        override suspend fun habitById(id: HabitId): Habit? = null
    }

    private fun onDays(vararg days: DayOfWeek): HabitFrequency =
        (HabitFrequency.OnDays.of(days.toSet()) as HabitFrequencyResult.Valid).frequency

    private fun useCase(
        repository: FakeHabitRepository,
        at: String = "2026-08-27T10:00:00Z",
    ) = CreateHabit(FakeClock(Instant.parse(at), zone), repository, sixAm)

    private val morningAnchor = Anchor.AtDaySegment(DaySegment.MORNING)

    @Test
    fun `a complete habit is created`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)(
            rawBehavior = "Read one page of Atomic Habits",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor),
            rawIdentity = "I am someone who reads",
        )

        assertEquals(CreateHabitResult.Created(HabitId(1)), result)
        assertEquals("Read one page of Atomic Habits", repository.created.single().first.value)
        assertEquals("Open the book", repository.created.single().second.value)
        assertEquals("I am someone who reads", repository.created.single().third?.value)
    }

    @Test
    fun `a habit with no anchor is refused`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = emptyList(),
        )

        assertEquals(CreateHabitResult.Rejected.NoAnchors, result)
        assertTrue("nothing partially valid may reach the repository", repository.created.isEmpty())
    }

    @Test
    fun `a daily habit stacked onto a Monday-only habit is refused, naming the missing days`() = runTest {
        // #98's second-most-important case, now at the level a user actually hits it.
        val repository = FakeHabitRepository()
        val mondayOnly = Anchor.AfterHabit(HabitId(42), onDays(DayOfWeek.MONDAY))

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(mondayOnly),
        )

        val rejection = result as CreateHabitResult.Rejected.IncompatibleAnchor
        assertEquals(0, rejection.anchorIndex)
        assertEquals(DayOfWeek.entries.toSet() - DayOfWeek.MONDAY, rejection.missingDays)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a second anchor that cannot fire is refused even when the first one is fine`() = runTest {
        // A habit with one good cue and one dead cue still goes silent on those days, so checking
        // only the first anchor would ship exactly the defect the refusal exists to prevent.
        val repository = FakeHabitRepository()
        val mondayOnly = Anchor.AfterHabit(HabitId(42), onDays(DayOfWeek.MONDAY))

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor, mondayOnly),
        )

        assertEquals(1, (result as CreateHabitResult.Rejected.IncompatibleAnchor).anchorIndex)
    }

    @Test
    fun `a blank behavior is refused`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)("   ", "Open the book", HabitFrequency.Daily, listOf(morningAnchor))

        assertEquals(CreateHabitResult.Rejected.BlankBehavior, result)
    }

    @Test
    fun `a blank minimum is refused, because an optional minimum is an empty minimum`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)("Read one page", "  ", HabitFrequency.Daily, listOf(morningAnchor))

        assertEquals(CreateHabitResult.Rejected.BlankMinimum, result)
    }

    @Test
    fun `a skipped identity is stored as absent, never as a blank one`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor),
            rawIdentity = "   ",
        )

        assertTrue(result is CreateHabitResult.Created)
        assertNull("blank input must not become an empty identity line", repository.created.single().third)
    }

    @Test
    fun `an over-length identity is refused with the measured length`() = runTest {
        val repository = FakeHabitRepository()

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor),
            rawIdentity = "a".repeat(281),
        )

        assertEquals(CreateHabitResult.Rejected.IdentityTooLong(281, 280), result)
    }

    @Test
    fun `a habit created at 2am belongs to the day the user is still living`() = runTest {
        val repository = FakeHabitRepository()

        useCase(repository, at = "2026-08-27T02:00:00Z")(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor),
        )

        assertEquals(LocalDate.of(2026, 8, 26), repository.createdDates.single())
    }

    @Test
    fun `a persistence failure returns a typed result rather than throwing`() = runTest {
        val repository = FakeHabitRepository(throwOnCreate = IllegalStateException("simulated"))

        val result = useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(morningAnchor),
        )

        assertEquals(CreateHabitResult.Rejected.PersistenceFailure, result)
    }

    @Test
    fun `the anchors reach the repository in the order the user chose`() = runTest {
        val repository = FakeHabitRepository()
        val evening = Anchor.AtDaySegment(DaySegment.EVENING)

        useCase(repository)(
            rawBehavior = "Read one page",
            rawMinimum = "Open the book",
            frequency = HabitFrequency.Daily,
            anchors = listOf(evening, morningAnchor),
        )

        assertEquals(listOf(evening, morningAnchor), repository.createdAnchors.single().values)
    }
}
