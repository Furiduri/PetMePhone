package com.gcatcode.petmephone.core.data.local.task

import com.gcatcode.petmephone.core.domain.task.Task
import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence
import com.gcatcode.petmephone.core.domain.task.TaskTitle

/**
 * Entity ↔ domain model mapping by plain extension functions, matching how
 * `CharacterRepositoryImpl` already maps: no interface, no reflection, one file a reviewer reads
 * top to bottom (design decision 4).
 */
internal fun TaskEntity.toDomain(): Task = Task(
    id = TaskId(id),
    title = TaskTitle(title),
    rrule = rrule,
    createdAt = createdAt,
    createdDate = createdDate,
    isActive = isActive,
)

internal fun TaskOccurrenceEntity.toDomain(): TaskOccurrence = TaskOccurrence(
    id = id,
    taskId = TaskId(taskId),
    dueDate = dueDate,
    originDate = originDate,
    points = points,
    isCompleted = isCompleted,
    isCarriedOver = isCarriedOver,
    isMandatoryMakeup = isMandatoryMakeup,
    createdAt = createdAt,
)
