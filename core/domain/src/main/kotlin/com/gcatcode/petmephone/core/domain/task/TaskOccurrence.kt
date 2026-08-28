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
    /**
     * How it was completed, or `null` while [isCompleted] is false — absence here means "not done
     * yet", never a defaulted [CompletionKind.FULL]. Recording a kind for something nobody has done
     * would be a claim the app has not earned.
     *
     * No scoring path reads this. See [CompletionKind] for why.
     */
    val completionKind: CompletionKind?,
    val isCarriedOver: Boolean,
    val isMandatoryMakeup: Boolean,
    val createdAt: Instant,
)
