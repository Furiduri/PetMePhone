package com.gcatcode.petmephone.core.domain.task

import java.time.Instant
import java.time.LocalDate

/**
 * A task the user created. [createdDate] is structurally immutable after insert
 * (`task-persistence` spec, design decision 9) — no write path in [TaskRepository] ever updates
 * it, so it is never revisited here as a mutation target.
 */
data class Task(
    val id: TaskId,
    val title: TaskTitle,
    val rrule: String?,
    val createdAt: Instant,
    val createdDate: LocalDate,
    val isActive: Boolean,
)
