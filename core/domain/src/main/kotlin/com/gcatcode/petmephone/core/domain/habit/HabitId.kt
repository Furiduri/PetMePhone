package com.gcatcode.petmephone.core.domain.habit

/**
 * Identity of a habit row, wrapping its Room-generated `Long` primary key — the same shape as
 * [com.gcatcode.petmephone.core.domain.task.TaskId], and deliberately a distinct type from it so a
 * task id can never be passed where a habit id is meant.
 *
 * It exists in this change ahead of the habit itself because
 * [Anchor.AfterHabit] is a reference to another habit, and an anchor cannot be modelled without
 * naming what it points at.
 */
@JvmInline
value class HabitId(val value: Long)
