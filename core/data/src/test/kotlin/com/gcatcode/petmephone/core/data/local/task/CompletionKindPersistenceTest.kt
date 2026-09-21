package com.gcatcode.petmephone.core.data.local.task

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.domain.task.CompletionKind
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #98's completion kind, exercised against real rows in a real SQLite database rather than a fake.
 * The claims worth proving here are storage claims: that the column round-trips, that absence stays
 * absent, and above all that recording a minimum completion leaves the score untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompletionKindPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var occurrenceDao: TaskOccurrenceDao

    private val today = LocalDate.of(2026, 8, 27)
    private val createdAt = Instant.parse("2026-08-27T10:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        occurrenceDao = database.taskOccurrenceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertTask(title: String): Long = taskDao.insert(
        TaskEntity(
            title = title,
            rrule = null,
            createdAt = createdAt,
            createdDate = today,
            isActive = true,
        ),
    )

    private suspend fun insertOccurrence(taskId: Long, points: Int = 1): Long =
        occurrenceDao.insert(
            TaskOccurrenceEntity(
                taskId = taskId,
                dueDate = today,
                originDate = null,
                points = points,
                isCompleted = false,
                isCarriedOver = false,
                isMandatoryMakeup = false,
                createdAt = createdAt,
            ),
        )

    private suspend fun occurrences(): List<TaskOccurrenceEntity> =
        occurrenceDao.occurrencesDueOn(today).first()

    @Test
    fun `a fresh occurrence has no completion kind, because nobody has done it`() = runTest {
        insertOccurrence(insertTask("Read one page"))

        val stored = occurrences().single()

        assertFalse(stored.isCompleted)
        assertNull("an uncompleted occurrence must not carry a kind", stored.completionKind)
    }

    @Test
    fun `a minimum completion round-trips through the database`() = runTest {
        val id = insertOccurrence(insertTask("Clean the kitchen"))

        occurrenceDao.markCompleted(id, CompletionKind.MINIMUM)

        val stored = occurrences().single()
        assertTrue(stored.isCompleted)
        assertEquals(CompletionKind.MINIMUM, stored.completionKind)
    }

    @Test
    fun `a full completion round-trips through the database`() = runTest {
        val id = insertOccurrence(insertTask("Clean the kitchen"))

        occurrenceDao.markCompleted(id, CompletionKind.FULL)

        assertEquals(CompletionKind.FULL, occurrences().single().completionKind)
    }

    @Test
    fun `a minimum completion is worth exactly what a full one is`() = runTest {
        // The criterion #98 cares about most, at the layer where it can be broken silently: if the
        // completion write ever touches `points`, the minimum stops being usable on the day it was
        // designed for.
        val minimumId = insertOccurrence(insertTask("Clean the kitchen"), points = 1)
        val fullId = insertOccurrence(insertTask("Read one page"), points = 1)

        occurrenceDao.markCompleted(minimumId, CompletionKind.MINIMUM)
        occurrenceDao.markCompleted(fullId, CompletionKind.FULL)

        val stored = occurrences().associateBy { it.id }
        assertEquals(
            "a minimum completion must not be scored lower than a full one",
            stored.getValue(fullId).points,
            stored.getValue(minimumId).points,
        )
        // ...and the record of HOW they were done still differs, which is the whole point of
        // storing it: a week made entirely of minimums is a week that survived.
        assertEquals(CompletionKind.MINIMUM, stored.getValue(minimumId).completionKind)
        assertEquals(CompletionKind.FULL, stored.getValue(fullId).completionKind)
    }

    @Test
    fun `a day of all minimums and a day of all full completions carry equal total points`() = runTest {
        // #98's shape: equal counts, different kinds, identical score. Asserted over totals, since
        // that is what any future metric will sum.
        val minimums = (1..3).map { insertOccurrence(insertTask("minimum $it"), points = 1) }
        val fulls = (1..3).map { insertOccurrence(insertTask("full $it"), points = 1) }

        minimums.forEach { occurrenceDao.markCompleted(it, CompletionKind.MINIMUM) }
        fulls.forEach { occurrenceDao.markCompleted(it, CompletionKind.FULL) }

        val stored = occurrences().associateBy { it.id }
        val minimumTotal = minimums.sumOf { stored.getValue(it).points }
        val fullTotal = fulls.sumOf { stored.getValue(it).points }

        assertEquals(fullTotal, minimumTotal)
        assertEquals(3, minimums.count { stored.getValue(it).completionKind == CompletionKind.MINIMUM })
        assertEquals(3, fulls.count { stored.getValue(it).completionKind == CompletionKind.FULL })
    }

    @Test
    fun `the kind is stored as its name, so reordering the enum cannot relabel stored rows`() = runTest {
        val id = insertOccurrence(insertTask("Read one page"))
        occurrenceDao.markCompleted(id, CompletionKind.MINIMUM)

        val cursor = database.query("SELECT completionKind FROM TaskOccurrence WHERE id = ?", arrayOf<Any>(id))
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(
                "an ordinal would silently re-label every row the day a constant is inserted",
                "MINIMUM",
                it.getString(0),
            )
        }
    }

    @Test
    fun `completing one occurrence leaves the others untouched`() = runTest {
        val completed = insertOccurrence(insertTask("Read one page"))
        val untouched = insertOccurrence(insertTask("Water the plants"))

        occurrenceDao.markCompleted(completed, CompletionKind.MINIMUM)

        val stored = occurrences().associateBy { it.id }
        assertFalse(stored.getValue(untouched).isCompleted)
        assertNull(stored.getValue(untouched).completionKind)
    }
}
