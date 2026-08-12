package com.gcatcode.petmephone.core.data.local.task

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.gcatcode.petmephone.core.data.local.AppDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `hunger-metric` spec, design decision 4: the two new `Flow<Int>` counts backing [ObserveHunger]
 * emit on insert (Room's own query invalidation, no polling) and never move for a `dueDate`-only
 * write — mirrors [com.gcatcode.petmephone.core.data.repository.TaskRepositoryImplTest]'s existing
 * "generated recurring occurrences never move the manual count" scenario, but at the DAO/Flow
 * layer rather than the suspend-count layer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskDaoObserveCountsTest {

    private lateinit var database: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var taskOccurrenceDao: TaskOccurrenceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = database.taskDao()
        taskOccurrenceDao = database.taskOccurrenceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeManuallyCreatedOn emits the current count and re-emits on insert`() = runTest {
        val date = LocalDate.of(2026, 8, 12)

        taskDao.observeManuallyCreatedOn(date).test {
            // Failing input this guards: a suspend-only count read once at collection start (no
            // Room Flow/invalidation wiring) would never deliver the second item below.
            assertEquals(0, awaitItem())

            insertTask(title = "Task 1", createdDate = date)

            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `observeManuallyCreatedOn does not move when a generated occurrence is scheduled for a different date`() =
        runTest {
            val date = LocalDate.of(2026, 8, 12)
            val otherDate = date.plusDays(5)
            val manualTaskId = insertTask(title = "Manual", createdDate = date)

            taskDao.observeManuallyCreatedOn(date).test {
                assertEquals(1, awaitItem())

                // A recurring occurrence generated for `otherDate`, still belonging to a Task whose
                // own createdDate is `date`. Counting on `dueDate` instead of `createdDate` would
                // wrongly move `date`'s count here (or leave it alone by accident) — the assertion
                // below on `otherDate` separately proves createdDate, not dueDate, is what's read.
                taskOccurrenceDao.insert(
                    TaskOccurrenceEntity(
                        taskId = manualTaskId,
                        dueDate = otherDate,
                        originDate = null,
                        points = 1,
                        isCompleted = false,
                        isCarriedOver = false,
                        isMandatoryMakeup = false,
                        createdAt = Instant.parse("2026-08-12T10:00:00Z"),
                    ),
                )

                expectNoEvents()
            }

            // Failing input this guards: a query counting TaskOccurrence rows by dueDate (instead
            // of Task rows by createdDate) would report 1 here instead of 0 — no Task was created
            // on `otherDate`.
            assertEquals(0, taskDao.observeManuallyCreatedOn(otherDate).first())
        }

    private suspend fun insertTask(title: String, createdDate: LocalDate): Long =
        taskDao.insert(
            TaskEntity(
                title = title,
                rrule = null,
                createdAt = createdDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdDate = createdDate,
                isActive = true,
            ),
        )
}
