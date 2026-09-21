package com.gcatcode.petmephone.core.data.local.task

import com.gcatcode.petmephone.core.domain.task.TaskId
import com.gcatcode.petmephone.core.domain.task.TaskOccurrence

/**
 * Entity ↔ domain model mapping by plain extension functions, matching how
 * `CharacterRepositoryImpl` already maps: no interface, no reflection, one file a reviewer reads
 * top to bottom (design decision 4).
 */
internal fun TaskOccurrenceEntity.toDomain(): TaskOccurrence = TaskOccurrence(
    id = id,
    taskId = TaskId(taskId),
    dueDate = dueDate,
    originDate = originDate,
    points = points,
    isCompleted = isCompleted,
    completionKind = completionKind,
    isCarriedOver = isCarriedOver,
    isMandatoryMakeup = isMandatoryMakeup,
    createdAt = createdAt,
)
