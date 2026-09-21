package com.gcatcode.petmephone.feature.overlay.quickmenu

import com.gcatcode.petmephone.core.domain.draft.AuthoringDraft
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.draft.DraftRepository
import com.gcatcode.petmephone.core.domain.draft.SubmitDraft
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.CreateHabit
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.core.domain.habit.Habit
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.habit.HabitRepository
import com.gcatcode.petmephone.core.domain.habit.Identity
import com.gcatcode.petmephone.core.domain.balance.BalanceConfig
import com.gcatcode.petmephone.core.domain.task.CreateOneOffTask
import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #100's wiring: every edit reaches the persisted draft, and only Cancel throws it away. */
class AuthoringFormControllerTest {

    private class TickingClock : AppClock {
        var current: Instant = Instant.parse("2026-08-27T10:00:00Z")
        override fun now(): Instant {
            current = current.plusSeconds(1)
            return current
        }

        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private class FakeDrafts : DraftRepository {
        val state = MutableStateFlow<AuthoringDraft?>(null)
        var saves = 0
        var discarded = false
        override fun current(): Flow<AuthoringDraft?> = state
        override suspend fun save(draft: AuthoringDraft) {
            saves++
            state.value = draft
        }

        override suspend fun discard() {
            discarded = true
            state.value = null
        }
    }

    private class FakeHabits : HabitRepository {
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
            created++
            return HabitId(created.toLong())
        }

        override suspend fun habitById(id: HabitId): Habit? = null
    }

    /** The habit path is what these exercise; the task path has its own tests in SubmitDraftTest. */
    private class FakeTasks : TaskRepository {
        override suspend fun createOneOff(
            behavior: Behavior,
            minimum: Minimum,
            createdAt: Instant,
            createdDate: LocalDate,
            points: Int,
        ): TaskId = TaskId(1)

        override suspend fun countManuallyCreatedOn(date: LocalDate) = 0
        override suspend fun countRecurringScheduledOn(date: LocalDate) = 0
        override fun observeManuallyCreatedOn(date: LocalDate) = MutableStateFlow(0)
        override fun observeRecurringScheduledOn(date: LocalDate) = MutableStateFlow(0)
        override fun occurrencesDueOn(date: LocalDate) = MutableStateFlow(emptyList<TaskOccurrence>())
    }

    private fun controller(
        drafts: FakeDrafts,
        habits: FakeHabits = FakeHabits(),
        clock: TickingClock = TickingClock(),
    ) = AuthoringFormController(
        drafts = drafts,
        submitDraft = SubmitDraft(
            drafts = drafts,
            createHabit = CreateHabit(clock, habits, LocalTime.of(6, 0)),
            createOneOffTask = CreateOneOffTask(clock, FakeTasks(), BalanceConfig(), LocalTime.of(6, 0)),
        ),
        clock = clock,
    )

    @Test
    fun `starting a draft writes it immediately, at step zero and empty`() = runTest {
        val drafts = FakeDrafts()

        controller(drafts).start(DraftKind.HABIT)

        val draft = requireNotNull(drafts.state.first())
        assertEquals(DraftKind.HABIT, draft.kind)
        assertEquals(0, draft.step)
        assertEquals("", draft.rawBehavior)
    }

    @Test
    fun `every keystroke is written, not batched until advance`() = runTest {
        // A batched write loses the current step's text to the screen turning off mid-sentence.
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)
        val savesAfterStart = drafts.saves

        controller.edit(requireNotNull(drafts.state.first()), stepIndex = 0, value = "R")
        controller.edit(requireNotNull(drafts.state.first()), stepIndex = 0, value = "Re")
        controller.edit(requireNotNull(drafts.state.first()), stepIndex = 0, value = "Rea")

        assertEquals(savesAfterStart + 3, drafts.saves)
        assertEquals("Rea", requireNotNull(drafts.state.first()).rawBehavior)
    }

    @Test
    fun `each step writes its own field`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)

        controller.edit(requireNotNull(drafts.state.first()), 0, "Read one page")
        controller.edit(requireNotNull(drafts.state.first()), 1, "Open the book")

        val draft = requireNotNull(drafts.state.first())
        assertEquals("Read one page", draft.rawBehavior)
        assertEquals("Open the book", draft.rawMinimum)
    }

    @Test
    fun `text arriving for the cue step is ignored rather than written into a field`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)
        val before = drafts.saves

        controller.edit(requireNotNull(drafts.state.first()), stepIndex = 2, value = "nonsense")

        assertEquals("no write may happen for a step that holds no text", before, drafts.saves)
    }

    @Test
    fun `advancing moves the stored step, so a resume returns where it left off`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)

        val outcome = controller.advance(requireNotNull(drafts.state.first()), stepIndex = 0)

        assertEquals(AdvanceOutcome.ShowStep(1), outcome)
        assertEquals(1, requireNotNull(drafts.state.first()).step)
    }

    @Test
    fun `advancing past the last step submits and the draft is gone`() = runTest {
        val drafts = FakeDrafts()
        val habits = FakeHabits()
        val controller = controller(drafts, habits)
        controller.start(DraftKind.HABIT)
        controller.edit(requireNotNull(drafts.state.first()), 0, "Read one page")
        controller.edit(requireNotNull(drafts.state.first()), 1, "Open the book")
        controller.chooseSegment(requireNotNull(drafts.state.first()), DaySegment.MORNING)

        val outcome = controller.advance(requireNotNull(drafts.state.first()), stepIndex = 2)

        assertEquals(AdvanceOutcome.Submitted, outcome)
        assertEquals(1, habits.created)
        assertNull(drafts.state.first())
    }

    @Test
    fun `choosing a cue stores a day-segment anchor`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)

        controller.chooseSegment(requireNotNull(drafts.state.first()), DaySegment.EVENING)

        val anchor = requireNotNull(drafts.state.first()).anchor
        assertEquals(DaySegment.EVENING, (anchor as Anchor.AtDaySegment).segment)
    }

    @Test
    fun `cancel is the only thing that discards`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)
        controller.edit(requireNotNull(drafts.state.first()), 0, "Read one page")

        assertTrue("editing must never discard", !drafts.discarded)
        controller.cancel()

        assertTrue(drafts.discarded)
        assertNull(drafts.state.first())
    }

    @Test
    fun `an incomplete draft is refused without losing what was typed`() = runTest {
        val drafts = FakeDrafts()
        val controller = controller(drafts)
        controller.start(DraftKind.HABIT)
        controller.edit(requireNotNull(drafts.state.first()), 0, "Read one page")
        controller.edit(requireNotNull(drafts.state.first()), 1, "Open the book")

        // Submitting from the last step without having chosen a cue.
        val outcome = controller.advance(requireNotNull(drafts.state.first()), stepIndex = 2)

        assertTrue(outcome is AdvanceOutcome.Refused)
        assertNotNull("a refusal must not cost the user their words", drafts.state.first())
        assertEquals("Read one page", requireNotNull(drafts.state.first()).rawBehavior)
    }

    @Test
    fun `a stale draft stops being offered but is still there`() = runTest {
        val drafts = FakeDrafts()
        val clock = TickingClock()
        val controller = controller(drafts, clock = clock)
        controller.start(DraftKind.HABIT)
        controller.edit(requireNotNull(drafts.state.first()), 0, "Read one page")

        clock.current = clock.current.plusSeconds(60L * 60L * 48L)

        assertNull("a two-day-old thought stops interrupting", controller.offerable(36).first())
        assertNotNull("but it is never deleted on a timer", drafts.state.first())
    }
}
