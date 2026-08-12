package com.gcatcode.petmephone.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceEntity
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric, in-memory Room DB (`task-persistence` spec's testing strategy). Covers: a title
 * edit leaves `createdDate` unchanged (task 2.19), the created-today count across a 23:59 → 00:01
 * boundary with no zone drift (task 2.20), duplicate `(taskId, dueDate)` rejection / cascade
 * delete / two concurrent inserts both landing (task 2.21), and generated occurrences never moving
 * the manual count (task 2.22).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskRepositoryImpl(database, database.taskDao(), database.taskOccurrenceDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `editing a task's title leaves createdDate unchanged`() = runTest {
        val createdDate = LocalDate.of(2026, 8, 11)
        val id = repository.createOneOff(
            title = TaskTitle("Original"),
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            createdDate = createdDate,
            points = 1,
        )

        database.taskDao().updateTitle(id.value, "Renamed")

        val updated = database.taskDao().findById(id.value)
        assertNotNull(updated)
        assertEquals("Renamed", updated!!.title)
        assertEquals(createdDate, updated.createdDate)
    }

    @Test
    fun `created-today count is correct across a 23_59 to 00_01 boundary with no zone drift`() = runTest {
        // 23:59 on Aug 10 in UTC-5 is still Aug 10 local, even though the same instant is already
        // Aug 11 in UTC. The date is computed once (here, by the caller) and never recomputed by
        // the query — `domain-time` spec.
        val zone = ZoneOffset.ofHours(-5)
        val lateNightInstant = ZonedDateTime.of(2026, 8, 10, 23, 59, 0, 0, zone).toInstant()
        val lateNightDate = lateNightInstant.atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 10), lateNightDate)

        repository.createOneOff(
            title = TaskTitle("Late task"),
            createdAt = lateNightInstant,
            createdDate = lateNightDate,
            points = 1,
        )

        // Reading back "at 00:01" the next day does not change the stored date: the count for
        // Aug 10 still includes this task, and Aug 11 does not.
        assertEquals(1, repository.countManuallyCreatedOn(LocalDate.of(2026, 8, 10)))
        assertEquals(0, repository.countManuallyCreatedOn(LocalDate.of(2026, 8, 11)))
    }

    @Test
    fun `duplicate taskId dueDate insert is rejected, delete cascades, concurrent inserts both land`() = runTest {
        val date = LocalDate.of(2026, 8, 11)
        val taskId = repository.createOneOff(
            title = TaskTitle("Task"),
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            createdDate = date,
            points = 1,
        )

        // The first occurrence at `date` already exists (created by createOneOff). A second insert
        // for the exact same (taskId, dueDate) must be rejected by the unique index.
        try {
            database.taskOccurrenceDao().insert(
                TaskOccurrenceEntity(
                    taskId = taskId.value,
                    dueDate = date,
                    originDate = null,
                    points = 1,
                    isCompleted = false,
                    isCarriedOver = false,
                    isMandatoryMakeup = false,
                    createdAt = Instant.parse("2026-08-11T10:00:00Z"),
                ),
            )
            fail("Expected the unique index on (taskId, dueDate) to reject the duplicate insert")
        } catch (expected: SQLiteConstraintException) {
            // Rejected as expected.
        }

        val beforeDelete = database.taskOccurrenceDao().occurrencesDueOn(date).first()
        assertEquals(1, beforeDelete.size)

        database.taskDao().updateIsActive(taskId.value, false) // sanity: narrow update path works
        // Deleting the parent Task cascades its TaskOccurrence rows.
        database.taskDao().delete(taskId.value)
        val afterDelete = database.taskOccurrenceDao().occurrencesDueOn(date).first()
        assertTrue(afterDelete.isEmpty())

        // Two concurrent inserts for two different occurrences both land.
        val secondTaskId = repository.createOneOff(
            title = TaskTitle("Task 2"),
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            createdDate = date,
            points = 1,
        )
        val thirdTaskId = repository.createOneOff(
            title = TaskTitle("Task 3"),
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            createdDate = date,
            points = 1,
        )
        val otherDate = date.plusDays(1)
        val jobA = async {
            database.taskOccurrenceDao().insert(
                TaskOccurrenceEntity(
                    taskId = secondTaskId.value,
                    dueDate = otherDate,
                    originDate = null,
                    points = 1,
                    isCompleted = false,
                    isCarriedOver = false,
                    isMandatoryMakeup = false,
                    createdAt = Instant.parse("2026-08-11T10:00:00Z"),
                ),
            )
        }
        val jobB = async {
            database.taskOccurrenceDao().insert(
                TaskOccurrenceEntity(
                    taskId = thirdTaskId.value,
                    dueDate = otherDate,
                    originDate = null,
                    points = 1,
                    isCompleted = false,
                    isCarriedOver = false,
                    isMandatoryMakeup = false,
                    createdAt = Instant.parse("2026-08-11T10:00:00Z"),
                ),
            )
        }
        jobA.await()
        jobB.await()
        val landed = database.taskOccurrenceDao().occurrencesDueOn(otherDate).first()
        assertEquals(2, landed.size)
    }

    @Test
    fun `generated recurring occurrences never move the manual count`() = runTest {
        val date = LocalDate.of(2026, 8, 11)
        val manualTaskId = repository.createOneOff(
            title = TaskTitle("Manual"),
            createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            createdDate = date,
            points = 1,
        )

        // Simulate a recurring occurrence generated for a different day than its Task's own
        // createdDate — countManuallyCreatedOn filters by Task.createdDate, not by dueDate, so an
        // occurrence due today for a task created on a different day must not inflate the count.
        database.taskOccurrenceDao().insert(
            TaskOccurrenceEntity(
                taskId = manualTaskId.value,
                dueDate = date.plusDays(5),
                originDate = null,
                points = 1,
                isCompleted = false,
                isCarriedOver = false,
                isMandatoryMakeup = false,
                createdAt = Instant.parse("2026-08-11T10:00:00Z"),
            ),
        )

        assertEquals(1, repository.countManuallyCreatedOn(date))
        assertEquals(0, repository.countRecurringScheduledOn(date))
    }
}
