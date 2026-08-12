package com.gcatcode.petmephone.core.data.repository

import androidx.room.withTransaction
import com.gcatcode.petmephone.core.data.local.AppDatabase
import com.gcatcode.petmephone.core.data.local.task.TaskDao
import com.gcatcode.petmephone.core.data.local.task.TaskEntity
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceDao
import com.gcatcode.petmephone.core.data.local.task.TaskOccurrenceEntity
import com.gcatcode.petmephone.core.data.local.task.toDomain
import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskRepository
import com.gcatcode.petmephone.core.domain.task.TaskTitle
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TaskRepository] (`task-persistence` spec). [createOneOff] inserts the [TaskEntity]
 * and its first [TaskOccurrenceEntity] inside one transaction (design's data-flow block), so a
 * task never exists without its due-today occurrence.
 *
 * [countRecurringScheduledOn] returns `0` until recurring-occurrence generation lands in a later
 * slice — deliberate per design decision 8.
 */
class TaskRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val taskOccurrenceDao: TaskOccurrenceDao,
) : TaskRepository {

    override suspend fun createOneOff(
        title: TaskTitle,
        createdAt: Instant,
        createdDate: LocalDate,
        points: Int,
    ): TaskId = database.withTransaction {
        val taskId = taskDao.insert(
            TaskEntity(
                title = title.value,
                rrule = null,
                createdAt = createdAt,
                createdDate = createdDate,
                isActive = true,
            ),
        )
        taskOccurrenceDao.insert(
            TaskOccurrenceEntity(
                taskId = taskId,
                dueDate = createdDate,
                originDate = null,
                points = points,
                isCompleted = false,
                isCarriedOver = false,
                isMandatoryMakeup = false,
                createdAt = createdAt,
            ),
        )
        TaskId(taskId)
    }

    override suspend fun countManuallyCreatedOn(date: LocalDate): Int =
        taskDao.countCreatedOn(date)

    override suspend fun countRecurringScheduledOn(date: LocalDate): Int = 0

    override fun occurrencesDueOn(date: LocalDate): Flow<List<TaskOccurrence>> =
        taskOccurrenceDao.occurrencesDueOn(date).map { entities -> entities.map { it.toDomain() } }
}
