package com.gcatcode.petmephone.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.data.local.habit.HabitDao
import com.gcatcode.petmephone.core.domain.habit.Anchor
import com.gcatcode.petmephone.core.domain.habit.Anchors
import com.gcatcode.petmephone.core.domain.habit.AnchorsResult
import com.gcatcode.petmephone.core.domain.habit.DaySegment
import com.gcatcode.petmephone.core.domain.habit.HabitFrequency
import com.gcatcode.petmephone.core.domain.habit.HabitFrequencyResult
import com.gcatcode.petmephone.core.domain.habit.HabitId
import com.gcatcode.petmephone.core.domain.habit.Identity
import com.gcatcode.petmephone.core.domain.habit.IdentityResult
import com.gcatcode.petmephone.core.domain.task.Behavior
import com.gcatcode.petmephone.core.domain.task.BehaviorResult
import com.gcatcode.petmephone.core.domain.task.Minimum
import com.gcatcode.petmephone.core.domain.task.MinimumResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
 * #128 against a real SQLite database: a habit survives the round trip field for field, anchors keep
 * their structure and their referential integrity, and nothing partially rebuilt is ever handed
 * back as a habit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HabitRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var habitDao: HabitDao
    private lateinit var repository: HabitRepositoryImpl

    private val createdAt = Instant.parse("2026-08-27T10:00:00Z")
    private val createdDate = LocalDate.of(2026, 8, 27)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        habitDao = database.habitDao()
        repository = HabitRepositoryImpl(database, habitDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun behavior(raw: String) = (Behavior.of(raw) as BehaviorResult.Valid).behavior
    private fun minimum(raw: String) = (Minimum.of(raw) as MinimumResult.Valid).minimum
    private fun identity(raw: String) = (Identity.of(raw) as IdentityResult.Valid).identity
    private fun anchorsOf(vararg anchor: Anchor) =
        (Anchors.of(anchor.toList()) as AnchorsResult.Valid).anchors

    private fun onDays(vararg days: DayOfWeek) =
        (HabitFrequency.OnDays.of(days.toSet()) as HabitFrequencyResult.Valid).frequency

    private suspend fun createHabit(
        behaviorText: String = "Read one page of Atomic Habits",
        minimumText: String = "Open the book",
        frequency: HabitFrequency = HabitFrequency.Daily,
        anchors: Anchors = anchorsOf(Anchor.AtDaySegment(DaySegment.MORNING)),
        identityText: String? = "I am someone who reads",
    ): HabitId = repository.create(
        behavior = behavior(behaviorText),
        minimum = minimum(minimumText),
        frequency = frequency,
        anchors = anchors,
        identity = identityText?.let(::identity),
        createdAt = createdAt,
        createdDate = createdDate,
    )

    @Test
    fun `a habit survives the round trip field for field`() = runTest {
        val id = createHabit()

        val read = repository.habitById(id)

        assertNotNull(read)
        requireNotNull(read)
        assertEquals(id, read.id)
        assertEquals("Read one page of Atomic Habits", read.behavior.value)
        assertEquals("Open the book", read.minimum.value)
        assertEquals("I am someone who reads", read.identity?.value)
        assertEquals(HabitFrequency.Daily, read.frequency)
        assertEquals(createdAt, read.createdAt)
        assertEquals(createdDate, read.createdDate)
        assertEquals(true, read.isActive)
    }

    @Test
    fun `a skipped identity reads back as absent, not as an empty line`() = runTest {
        val id = createHabit(identityText = null)

        assertNull(repository.habitById(id)?.identity)
    }

    @Test
    fun `a specific-days frequency round-trips as exactly those days`() = runTest {
        val id = createHabit(frequency = onDays(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))

        val read = requireNotNull(repository.habitById(id))

        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), read.frequency.occursOn)
    }

    @Test
    fun `a seven-day frequency reads back as Daily rather than a seven-element set`() = runTest {
        val id = createHabit(frequency = onDays(*DayOfWeek.entries.toTypedArray()))

        assertEquals(HabitFrequency.Daily, requireNotNull(repository.habitById(id)).frequency)
    }

    @Test
    fun `every anchor variant round-trips with its own data`() = runTest {
        val anchorHabit = createHabit(behaviorText = "Close the laptop for lunch")
        val id = createHabit(
            anchors = anchorsOf(
                Anchor.AfterHabit(anchorHabit, HabitFrequency.Daily),
                Anchor.AtDaySegment(DaySegment.EVENING),
                Anchor.AtClockTime(LocalTime.of(7, 30)),
            ),
        )

        val read = requireNotNull(repository.habitById(id))

        val anchors = read.anchors.values
        assertEquals(3, anchors.size)
        assertEquals(anchorHabit, (anchors[0] as Anchor.AfterHabit).habitId)
        assertEquals(DaySegment.EVENING, (anchors[1] as Anchor.AtDaySegment).segment)
        assertEquals(LocalTime.of(7, 30), (anchors[2] as Anchor.AtClockTime).time)
    }

    @Test
    fun `the anchor order the user chose survives the round trip`() = runTest {
        val id = createHabit(
            anchors = anchorsOf(
                Anchor.AtClockTime(LocalTime.of(21, 0)),
                Anchor.AtDaySegment(DaySegment.MORNING),
            ),
        )

        val read = requireNotNull(repository.habitById(id))

        // Never re-sorted by cue strength: AT_CLOCK_TIME is the weakest kind and still comes first,
        // because that is the order the user picked.
        assertEquals(LocalTime.of(21, 0), (read.anchors.values[0] as Anchor.AtClockTime).time)
        assertEquals(DaySegment.MORNING, (read.anchors.values[1] as Anchor.AtDaySegment).segment)
    }

    @Test
    fun `an after-habit anchor derives its frequency from the habit it points at`() = runTest {
        // The frequency is NOT stored a second time on the anchor row. Two copies would be free to
        // drift, which is exactly the duplicated-default defect the balance config had.
        val mondayOnly = createHabit(
            behaviorText = "Weekly review",
            frequency = onDays(DayOfWeek.MONDAY),
        )
        val id = createHabit(
            frequency = onDays(DayOfWeek.MONDAY),
            anchors = anchorsOf(Anchor.AfterHabit(mondayOnly, HabitFrequency.Daily)),
        )

        val read = requireNotNull(repository.habitById(id))

        val anchor = read.anchors.values.single() as Anchor.AfterHabit
        assertEquals(
            "the stored anchor must report the referenced habit's real days, not what it was constructed with",
            setOf(DayOfWeek.MONDAY),
            anchor.habitFrequency.occursOn,
        )
    }

    @Test
    fun `deleting the anchor habit leaves no dangling reference`() = runTest {
        val anchorHabit = createHabit(behaviorText = "Close the laptop for lunch")
        val stacked = createHabit(anchors = anchorsOf(Anchor.AfterHabit(anchorHabit, HabitFrequency.Daily)))

        habitDao.delete(anchorHabit.value)

        // The cue cascaded away with its target, so the stacked habit no longer has one. It is
        // surfaced as unreadable rather than handed back with an invented cue.
        assertEquals(0, habitDao.anchors(stacked.value).size)
        assertNull("a habit with no cue must not be rebuilt", repository.habitById(stacked))
    }

    @Test
    fun `deleting a habit removes its own days and anchors`() = runTest {
        val id = createHabit(frequency = onDays(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

        habitDao.delete(id.value)

        assertEquals(0, habitDao.days(id.value).size)
        assertEquals(0, habitDao.anchors(id.value).size)
        assertNull(repository.habitById(id))
    }

    @Test
    fun `an unknown id reads back as null`() = runTest {
        assertNull(repository.habitById(HabitId(4242)))
    }

    @Test
    fun `the anchor reference is a real foreign key, stored as a column of its own`() = runTest {
        val anchorHabit = createHabit(behaviorText = "Close the laptop for lunch")
        val id = createHabit(anchors = anchorsOf(Anchor.AfterHabit(anchorHabit, HabitFrequency.Daily)))

        // Read the raw row: the reference must be a queryable column, not a field buried in a blob.
        val cursor = database.query(
            "SELECT anchorHabitId, kind FROM HabitAnchor WHERE habitId = ?",
            arrayOf<Any>(id.value),
        )
        cursor.use {
            org.junit.Assert.assertTrue(it.moveToFirst())
            assertEquals(anchorHabit.value, it.getLong(0))
            assertEquals("AFTER_HABIT", it.getString(1))
        }
    }
}
