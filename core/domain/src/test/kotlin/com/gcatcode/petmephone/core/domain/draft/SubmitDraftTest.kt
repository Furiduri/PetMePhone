package com.gcatcode.petmephone.core.domain.draft

import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.CreateHabit
import com.gcatcode.petmephone.core.domain.habit.CreateHabitResult
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.core.domain.habit.Habit
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.habit.HabitRepository
import com.gcatcode.petmephone.core.domain.habit.Identity
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #100: submitting turns the draft into a habit, and the draft only goes once that succeeded. */
class SubmitDraftTest {

    private val now = Instant.parse("2026-08-27T10:00:00Z")

    private class FakeClock : AppClock {
        override fun now(): Instant = Instant.parse("2026-08-27T10:00:00Z")
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private class FakeDrafts : DraftRepository {
        val state = MutableStateFlow<AuthoringDraft?>(null)
        var discarded = false
        override fun current(): Flow<AuthoringDraft?> = state
        override suspend fun save(draft: AuthoringDraft) {
            state.value = draft
        }

        override suspend fun discard() {
            discarded = true
            state.value = null
        }
    }

    private class FakeHabits(var throwOnCreate: Exception? = null) : HabitRepository {
        var created = 0
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
            created++
            return HabitId(created.toLong())
        }

        override suspend fun habitById(id: HabitId): Habit? = null
    }

    private fun draft(
        kind: DraftKind = DraftKind.HABIT,
        behavior: String = "Read one page",
        minimum: String = "Open the book",
        anchor: Anchor? = Anchor.AtDaySegment(DaySegment.MORNING),
    ) = AuthoringDraft(
        kind = kind,
        rawBehavior = behavior,
        rawMinimum = minimum,
        anchor = anchor,
        step = 2,
        createdAt = now,
        updatedAt = now,
    )

    private fun submitDraft(drafts: FakeDrafts, habits: FakeHabits) = SubmitDraft(
        drafts = drafts,
        createHabit = CreateHabit(FakeClock(), habits, LocalTime.of(6, 0)),
    )

    @Test
    fun `a complete habit draft becomes a habit and the draft is cleared`() = runTest {
        val drafts = FakeDrafts()
        val habits = FakeHabits()

        val result = submitDraft(drafts, habits)(draft())

        assertEquals(SubmitDraftResult.Created(HabitId(1)), result)
        assertEquals(1, habits.created)
        assertTrue("a submitted draft has served its purpose", drafts.discarded)
    }

    @Test
    fun `a failed write leaves the draft exactly where it was`() = runTest {
        // A form that eats what you typed when something goes wrong is worse than one that never
        // saved it.
        val drafts = FakeDrafts()
        val habits = FakeHabits(throwOnCreate = IllegalStateException("simulated"))

        val result = submitDraft(drafts, habits)(draft())

        assertTrue(result is SubmitDraftResult.Rejected.Refused)
        assertFalse("the user's words must survive a failed insert", drafts.discarded)
    }

    @Test
    fun `a draft missing its cue names the step to go back to`() = runTest {
        val drafts = FakeDrafts()

        val result = submitDraft(drafts, FakeHabits())(draft(anchor = null))

        assertEquals(SubmitDraftResult.Rejected.Incomplete(AuthoringStep.ANCHOR), result)
        assertFalse(drafts.discarded)
    }

    @Test
    fun `a draft missing its behavior names that step`() = runTest {
        val result = submitDraft(FakeDrafts(), FakeHabits())(draft(behavior = "  "))

        assertEquals(SubmitDraftResult.Rejected.Incomplete(AuthoringStep.BEHAVIOR), result)
    }

    @Test
    fun `a draft missing its minimum names that step`() = runTest {
        val result = submitDraft(FakeDrafts(), FakeHabits())(draft(minimum = ""))

        assertEquals(SubmitDraftResult.Rejected.Incomplete(AuthoringStep.MINIMUM), result)
    }

    @Test
    fun `a task draft is refused rather than silently losing its minimum`() = runTest {
        // Task has a title and no minimum column. Submitting would drop the field #98 makes
        // mandatory; this refusal is what keeps that visible until Task carries one.
        val drafts = FakeDrafts()
        val habits = FakeHabits()

        val result = submitDraft(drafts, habits)(draft(kind = DraftKind.TASK))

        assertEquals(SubmitDraftResult.Rejected.TaskMinimumNotPersistable, result)
        assertEquals("nothing may be written for a task draft yet", 0, habits.created)
        assertFalse(drafts.discarded)
    }

    @Test
    fun `the domain's own refusal is carried through rather than flattened`() = runTest {
        // An over-length behavior must arrive as CreateHabit's measured rejection, so the form can
        // say what was wrong instead of "could not save".
        val result = submitDraft(FakeDrafts(), FakeHabits())(draft(behavior = "a".repeat(201)))

        val refused = result as SubmitDraftResult.Rejected.Refused
        assertNotNull(refused.reason as CreateHabitResult.Rejected.BehaviorTooLong)
    }
}
