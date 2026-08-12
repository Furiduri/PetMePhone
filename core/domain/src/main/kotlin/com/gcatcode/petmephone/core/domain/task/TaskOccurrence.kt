package com.gcatcode.petmephone.core.domain.task

import java.time.Instant
import java.time.LocalDate

/**
 * A single scheduled/due instance of a [Task]. Carry-over and mandatory-makeup generation are out
 * of scope for this slice (#23) — those flags exist on the shape now so the schema does not need a
 * migration when that behaviour lands.
 */
data class TaskOccurrence(
    val id: Long,
    val taskId: TaskId,
    val dueDate: LocalDate,
    val originDate: LocalDate?,
    val points: Int,
    val isCompleted: Boolean,
    val isCarriedOver: Boolean,
    val isMandatoryMakeup: Boolean,
    val createdAt: Instant,
)
