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
    /**
     * What the user actually does. This is the field that used to be `title`: #98 defines a task as
     * behavior plus minimum plus a date, so the two were never separate things — one was the other
     * under an older name.
     */
    val behavior: Behavior,
    /**
     * The two-minute version, required here exactly as it is on a habit. An optional minimum is an
     * empty minimum, and the presentation that depends on it then collapses back to showing the
     * heavy version of every task.
     */
    val minimum: Minimum,
    val rrule: String?,
    val createdAt: Instant,
    val createdDate: LocalDate,
    val isActive: Boolean,
)
