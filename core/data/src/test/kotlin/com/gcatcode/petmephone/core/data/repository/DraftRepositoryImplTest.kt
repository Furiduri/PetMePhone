package com.gcatcode.petmephone.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.domain.draft.AuthoringDraft
import com.gcatcode.petmephone.core.domain.draft.DraftKind
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.AnchorsResult
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.BehaviorResult
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.task.MinimumResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #100's draft, against a real database. The claims that matter are survival claims: the draft is
 * still there after everything that destroys an in-memory one, only Cancel removes it, and there is
 * never more than one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DraftRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DraftRepositoryImpl
    private lateinit var habitRepository: HabitRepositoryImpl

    private val started = Instant.parse("2026-08-27T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DraftRepositoryImpl(database.draftDao(), database.habitDao())
        habitRepository = HabitRepositoryImpl(database, database.habitDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun draft(
        kind: DraftKind = DraftKind.HABIT,
        behavior: String = "Read one pa",
        minimum: String = "",
        anchor: Anchor? = null,
        step: Int = 1,
        updatedAt: Instant = started,
    ) = AuthoringDraft(
        kind = kind,
        rawBehavior = behavior,
        rawMinimum = minimum,
        anchor = anchor,
        step = step,
        createdAt = started,
        updatedAt = updatedAt,
    )

    @Test
    fun `no draft exists until one is saved`() = runTest {
        assertNull(repository.current().first())
    }

    @Test
    fun `a half-typed field survives the round trip exactly as typed`() = runTest {
        // The whole reason a draft stores raw strings: "Read one pa" is not a valid Behavior, and
        // storing validated types would make the one thing a draft exists to hold unstorable.
        repository.save(draft(behavior = "Read one pa", step = 2))

        val read = requireNotNull(repository.current().first())

        assertEquals("Read one pa", read.rawBehavior)
        assertEquals(2, read.step)
        assertEquals(DraftKind.HABIT, read.kind)
    }

    @Test
    fun `saving again replaces the pending draft rather than adding a second`() = runTest {
        repository.save(draft(behavior = "first"))
        repository.save(draft(behavior = "second"))

        assertEquals("second", requireNotNull(repository.current().first()).rawBehavior)

        val cursor = database.query("SELECT COUNT(*) FROM AuthoringDraft", arrayOf())
        cursor.use {
            org.junit.Assert.assertTrue(it.moveToFirst())
            assertEquals("one draft at a time is a property of the key, not a rule", 1, it.getInt(0))
        }
    }

    @Test
    fun `discard removes the draft`() = runTest {
        repository.save(draft())

        repository.discard()

        assertNull(repository.current().first())
    }

    @Test
    fun `a day-segment anchor round-trips`() = runTest {
        repository.save(draft(anchor = Anchor.AtDaySegment(DaySegment.EVENING)))

        val read = requireNotNull(repository.current().first())

        assertEquals(DaySegment.EVENING, (read.anchor as Anchor.AtDaySegment).segment)
    }

    @Test
    fun `a clock-time anchor round-trips`() = runTest {
        repository.save(draft(anchor = Anchor.AtClockTime(LocalTime.of(7, 30))))

        val read = requireNotNull(repository.current().first())

        assertEquals(LocalTime.of(7, 30), (read.anchor as Anchor.AtClockTime).time)
    }

    @Test
    fun `an after-habit anchor resolves its frequency from the habit it names`() = runTest {
        val anchorHabit = createRealHabit()

        repository.save(draft(anchor = Anchor.AfterHabit(anchorHabit, HabitFrequency.Daily)))

        val read = requireNotNull(repository.current().first())
        assertEquals(anchorHabit, (read.anchor as Anchor.AfterHabit).habitId)
    }

    @Test
    fun `deleting the referenced habit costs the anchor, never the draft`() = runTest {
        // The deliberate consequence of the draft carrying no foreign key: a CASCADE here would
        // delete half-written words because the user tidied up an unrelated habit.
        val anchorHabit = createRealHabit()
        repository.save(draft(behavior = "Read one pa", anchor = Anchor.AfterHabit(anchorHabit, HabitFrequency.Daily)))

        database.habitDao().delete(anchorHabit.value)

        val read = repository.current().first()
        assertNotNull("the draft must survive the habit it pointed at", read)
        requireNotNull(read)
        assertEquals("the typed words are untouched", "Read one pa", read.rawBehavior)
        assertNull("the cue is dropped, and the user picks again", read.anchor)
    }

    @Test
    fun `an anchor naming a habit that never existed reads back as no anchor`() = runTest {
        repository.save(draft(anchor = Anchor.AfterHabit(HabitId(9999), HabitFrequency.Daily)))

        assertNull(requireNotNull(repository.current().first()).anchor)
    }

    private suspend fun createRealHabit(): HabitId = habitRepository.create(
        behavior = (Behavior.of("Close the laptop for lunch") as BehaviorResult.Valid).behavior,
        minimum = (Minimum.of("Shut the lid") as MinimumResult.Valid).minimum,
        frequency = HabitFrequency.Daily,
        anchors = (Anchors.of(Anchor.AtDaySegment(DaySegment.MORNING)) as AnchorsResult.Valid).anchors,
        identity = null,
        createdAt = started,
        createdDate = LocalDate.of(2026, 8, 27),
    )
}
